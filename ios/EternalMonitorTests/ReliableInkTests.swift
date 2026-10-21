import XCTest
@testable import EternalMonitor

final class ReliableInkTests: XCTestCase {
    func testLostInkIsSentImmediatelyThenRetriedUntilTheHostAcknowledgesIt() throws {
        var now: UInt64 = 1_000_000
        var packets: [Data] = []
        let sender = ReliableInkSender(nowUs: { now }, send: { packets.append($0) }, failed: { XCTFail($0) })
        sender.start(session: 42, epoch: 3)
        sender.send([.clear])
        XCTAssertEqual(packets.count, 1, "A healthy link must not wait for the retry timer")
        XCTAssertEqual(packets[0].prefix(4), Data("DI02".utf8))
        let envelope = try XCTUnwrap(JSONSerialization.jsonObject(with: packets[0].dropFirst(4)) as? [String: Any])
        XCTAssertEqual(envelope["session"] as? Int, 42)
        XCTAssertEqual(envelope["epoch"] as? Int, 3)
        XCTAssertEqual(envelope["seq"] as? Int, 1)
        now += 20_000
        sender.tick()
        XCTAssertEqual(packets.count, 2)
        XCTAssertEqual(packets[0], packets[1], "Retries preserve the exact command and sequence")
        // Independent literal: DA02, session 42, epoch 3, cumulative sequence 1.
        sender.acknowledge(Data([68,65,48,50,42,0,0,0,3,0,0,0,1,0,0,0,0,0,0,0]))
        now += 20_000
        sender.tick()
        XCTAssertEqual(packets.count, 2, "Acknowledged edits must not be sent again")
    }

    func testMissingAcknowledgementStopsInsteadOfReplayingOldInkAfterAStall() {
        var now: UInt64 = 1_000_000
        var packets: [Data] = [], failures: [String] = []
        let sender = ReliableInkSender(nowUs: { now }, send: { packets.append($0) }, failed: { failures.append($0) })
        sender.start(session: 42, epoch: 3)
        sender.send([.clear])
        now += 250_000
        sender.tick()
        sender.send([.undo])
        sender.tick()
        XCTAssertEqual(packets.count, 1, "Expired commands and subsequent edits must not leak into the old session")
        XCTAssertEqual(failures.count, 1, "One interruption must produce one actionable error")
        sender.start(session: 43, epoch: 3)
        sender.send([.redo])
        XCTAssertEqual(packets.count, 2, "A new authenticated session can draw again")
    }

    func testPendingWorkIsBoundedAndOneRetryTickCannotFloodTheVideoSocket() {
        var packets: [Data] = [], failures: [String] = []
        let sender = ReliableInkSender(nowUs: { 1_000_000 }, send: { packets.append($0) }, failed: { failures.append($0) })
        sender.start(session: 42, epoch: 3)
        sender.send(Array(repeating: .clear, count: 256))
        XCTAssertEqual(packets.count, 256)
        sender.tick()
        XCTAssertEqual(packets.count, 272, "Each retry is capped at sixteen old commands")
        sender.send([.undo])
        XCTAssertEqual(failures.count, 1)
        sender.tick()
        XCTAssertEqual(packets.count, 272, "Overflow must stop the session, not grow a stale queue")
    }

    func testForeignFutureAndMalformedAcknowledgementsCannotDiscardInk() {
        var packets: [Data] = []
        let sender = ReliableInkSender(nowUs: { 1_000_000 }, send: { packets.append($0) }, failed: { XCTFail($0) })
        sender.start(session: 42, epoch: 3)
        sender.send([.clear])
        let valid: [UInt8] = [68,65,48,50,42,0,0,0,3,0,0,0,1,0,0,0,0,0,0,0]
        let invalidFields: [(Int, UInt8)] = [(4,43), (8,4), (12,2)]
        for (offset, value) in invalidFields {
            var invalid = valid
            invalid[offset] = value
            sender.acknowledge(Data(invalid))
        }
        sender.acknowledge(Data(valid.dropLast()))
        sender.acknowledge(Data(valid + [0]))
        sender.tick()
        XCTAssertEqual(packets.count, 2)
        sender.acknowledge(Data(valid))
        sender.tick()
        XCTAssertEqual(packets.count, 2)
    }

    func testLateAcknowledgementCannotReviveAnExpiredSession() {
        var now: UInt64 = 1_000_000
        var packets: [Data] = [], failures: [String] = []
        let sender = ReliableInkSender(nowUs: { now }, send: { packets.append($0) }, failed: { failures.append($0) })
        sender.start(session: 42, epoch: 3)
        sender.send([.clear])
        now += 250_000
        sender.acknowledge(Data([68,65,48,50,42,0,0,0,3,0,0,0,1,0,0,0,0,0,0,0]))
        sender.start(session: 42, epoch: 3)
        sender.send([.undo])
        XCTAssertEqual(failures.count, 1)
        XCTAssertEqual(packets.count, 1)
    }

    func testStreamSwitchAndStopDiscardUnacknowledgedInk() throws {
        var packets: [Data] = []
        let sender = ReliableInkSender(nowUs: { 1_000_000 }, send: { packets.append($0) }, failed: { XCTFail($0) })
        sender.start(session: 42, epoch: 3)
        sender.send([.clear])
        sender.start(session: 42, epoch: 4)
        sender.tick()
        XCTAssertEqual(packets.count, 1)
        sender.send([.undo])
        let body = try XCTUnwrap(JSONSerialization.jsonObject(with: packets[1].dropFirst(4)) as? [String: Any])
        XCTAssertEqual(body["epoch"] as? Int, 4)
        XCTAssertEqual(body["seq"] as? Int, 1)
        sender.start(session: 42, epoch: 3)
        XCTAssertFalse(sender.accepts(session: 42, epoch: 3), "Late old heartbeats cannot restore the old board")
        XCTAssertTrue(sender.accepts(session: 42, epoch: 4))
        sender.stop()
        sender.tick()
        sender.send([.clear])
        XCTAssertEqual(packets.count, 2)
    }

    func testOnlyNegotiatedWirelessInkUsesTheAcknowledgedProtocol() throws {
        try exerciseChannel(wireless: false, hostCaps: 0xF000, expectedPrefix: "DI01")
        try exerciseChannel(wireless: true, hostCaps: 0xF000, expectedPrefix: "DI02")
        try exerciseChannel(wireless: true, hostCaps: 0x7000, expectedPrefix: nil)
    }

    private func exerciseChannel(wireless: Bool, hostCaps: UInt16, expectedPrefix: String?) throws {
        let queue = DispatchQueue(label: "reliable-ink-contract")
        let ready = expectation(description: "Authenticated session")
        var emitted: [Data] = []
        var channel: ControlChannel!
        channel = ControlChannel(queue: queue) { bytes in
            if case .hello2(let hello) = Wire.parseControl(bytes)?.message {
                channel.handleControl(Wire.encodeControl(sessionId: 0, msgSeq: 1,
                    message: .helloAck(HelloAck(status: .ok, acceptedVersion: 2,
                        clientNonce: hello.clientNonce, sessionId: 42, heartbeatIntervalMs: 1000,
                        reportIntervalMs: 500, livenessTimeoutMs: 3000,
                        streamConfig: StreamConfig(streamEpoch: 3), hostName: "Test PC", hostCaps: hostCaps))))
            } else if bytes.prefix(2) == Data("DI".utf8) {
                emitted.append(bytes)
            }
        }
        channel.onSessionEstablished = { _ in ready.fulfill() }
        channel.onInkFailure = { XCTFail($0) }
        defer { channel.stop(); queue.sync { channel = nil } }
        channel.startHandshake(listenPort: 50000, identity: ControlChannel.ClientIdentity(
            deviceName: "Test iPad", screenPxW: 640, screenPxH: 360, screenPtW: 640, screenPtH: 360,
            refreshHz: 60, featureCaps: wireless ? Hello2.featureReliableInk : 0))
        wait(for: [ready], timeout: 3)
        channel.sendInk([.clear], epoch: 3)
        let sent = queue.sync { emitted }
        if let expectedPrefix {
            XCTAssertEqual(try XCTUnwrap(sent.first).prefix(4), Data(expectedPrefix.utf8))
        } else {
            XCTAssertTrue(sent.isEmpty, "An old host must not receive unreliable wireless ink")
        }
        // The real incoming datagram adapter routes ink ACKs before legacy wire classification.
        let datagrams = MediaDatagrams()
        var acknowledgement: Data?
        datagrams.onInkAcknowledgement = { acknowledgement = $0; channel.handleInkAcknowledgement($0) }
        let ack = Data([68,65,48,50,42,0,0,0,3,0,0,0,1,0,0,0,0,0,0,0])
        queue.sync { datagrams.handle(ack) }
        XCTAssertEqual(acknowledgement, ack)
    }
}
