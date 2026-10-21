import XCTest
import os
@testable import EternalMonitor

final class LanInkIngressTests: XCTestCase {
    func testWirelessInkThatWaitedInTheDispatchQueueExpiresBeforeItIsSent() {
        let connection = ConnectedInkChannel(test: self)
        defer { connection.stop() }
        connection.queue.suspend()
        connection.channel.sendInk([.clear], epoch: 3)
        connection.advance(250_000)
        connection.queue.resume()
        let result = connection.snapshot()
        XCTAssertTrue(result.packets.isEmpty, "Do not send edits that already exceeded the total age limit")
        XCTAssertEqual(result.failures.count, 1)
    }

    func testIngressOverflowRejectsTheBacklogBeforeItCanReplay() {
        let connection = ConnectedInkChannel(test: self)
        defer { connection.stop() }
        connection.queue.suspend()
        for _ in 0..<257 { connection.channel.sendInk([.clear], epoch: 3) }
        connection.queue.resume()
        let result = connection.snapshot()
        XCTAssertTrue(result.packets.isEmpty, "Admission must be bounded before the suspended queue drains")
        XCTAssertEqual(result.failures.count, 1)
    }

    func testOneBudgetCoversBothQueuedAndAlreadySentUnacknowledgedInk() throws {
        let connection = ConnectedInkChannel(test: self)
        defer { connection.stop() }
        connection.channel.sendInk(Array(repeating: .clear, count: 255), epoch: 3)
        _ = connection.snapshot()
        connection.queue.suspend()
        connection.channel.sendInk([.undo, .redo], epoch: 3)
        connection.queue.resume()
        let result = connection.snapshot()
        let sequences = try result.packets.map { packet -> Int in
            let body = try XCTUnwrap(JSONSerialization.jsonObject(with: packet.dropFirst(4)) as? [String: Any])
            return try XCTUnwrap(body["seq"] as? Int)
        }
        XCTAssertEqual(sequences.max(), 255, "A batch beyond the shared limit must not partially escape")
        XCTAssertEqual(result.failures.count, 1)
    }

    func testAcknowledgementsReleaseCapacityForLaterInk() {
        let connection = ConnectedInkChannel(test: self)
        defer { connection.stop() }
        connection.channel.sendInk(Array(repeating: .clear, count: 256), epoch: 3)
        connection.queue.sync {
            connection.channel.handleInkAcknowledgement(Self.ack(sequence: 256))
        }
        connection.channel.sendInk([.clear], epoch: 3)
        let result = connection.snapshot()
        XCTAssertTrue(result.failures.isEmpty)
        XCTAssertGreaterThanOrEqual(result.packets.count, 257)
    }

    func testQueueWaitingTimeStillCountsAfterTheFirstSend() {
        let connection = ConnectedInkChannel(test: self)
        defer { connection.stop() }
        connection.queue.suspend()
        connection.channel.sendInk([.clear], epoch: 3)
        connection.advance(200_000)
        connection.queue.resume()
        XCTAssertFalse(connection.snapshot().packets.isEmpty)
        connection.advance(60_000)
        connection.queue.sync {
            connection.channel.handleInkAcknowledgement(Self.ack(sequence: 1))
        }
        XCTAssertEqual(connection.snapshot().failures.count, 1,
            "200 ms in Dispatch plus 60 ms awaiting ACK exceeds the total deadline")
    }

    func testStopInvalidatesQueuedInkWithoutFailingTheNextSession() throws {
        let connection = ConnectedInkChannel(test: self)
        defer { connection.stop() }
        connection.queue.suspend()
        connection.channel.sendInk([.clear], epoch: 3)
        connection.channel.stop()
        connection.advance(300_000)
        connection.queue.resume()
        XCTAssertTrue(connection.snapshot().packets.isEmpty)
        XCTAssertTrue(connection.snapshot().failures.isEmpty)
        connection.connect(test: self)
        connection.channel.sendInk([.clear], epoch: 3)
        let result = connection.snapshot()
        let packet = try XCTUnwrap(result.packets.last)
        let body = try XCTUnwrap(JSONSerialization.jsonObject(with: packet.dropFirst(4)) as? [String: Any])
        XCTAssertEqual(body["session"] as? Int, 42)
        XCTAssertEqual(body["seq"] as? Int, 1)
        XCTAssertTrue(result.failures.isEmpty)
    }

    func testUsbRetainsItsExistingQueuedDeliveryBehavior() {
        let connection = ConnectedInkChannel(test: self, wireless: false)
        defer { connection.stop() }
        connection.queue.suspend()
        connection.channel.sendInk([.clear], epoch: 3)
        connection.advance(1_000_000)
        connection.queue.resume()
        let result = connection.snapshot()
        XCTAssertEqual(result.packets.first?.prefix(4), Data("DI01".utf8))
        XCTAssertTrue(result.failures.isEmpty)
    }

    private static func ack(sequence: UInt64) -> Data {
        var bytes = Data([68,65,48,50,41,0,0,0,3,0,0,0])
        for shift in stride(from: 0, to: 64, by: 8) {
            bytes.append(UInt8(truncatingIfNeeded: sequence >> shift))
        }
        return bytes
    }
}

private final class ConnectedInkChannel {
    let queue = DispatchQueue(label: "blocked-lan-ink-ingress")
    let clock = OSAllocatedUnfairLock<UInt64>(initialState: 1_000_000)
    let wireless: Bool
    var channel: ControlChannel!
    private var packets: [Data] = []
    private var failures: [String] = []
    private var session: UInt32 = 40

    init(test: XCTestCase, wireless: Bool = true) {
        self.wireless = wireless
        channel = ControlChannel(queue: queue, nowUs: { [clock] in clock.withLock { $0 } }) { [weak self] bytes in
            guard let self else { return }
            if case .hello2(let hello) = Wire.parseControl(bytes)?.message {
                self.session += 1
                self.channel.handleControl(Wire.encodeControl(sessionId: 0, msgSeq: 1,
                    message: .helloAck(HelloAck(status: .ok, acceptedVersion: 2, clientNonce: hello.clientNonce,
                        sessionId: self.session, heartbeatIntervalMs: 1000, reportIntervalMs: 500,
                        livenessTimeoutMs: 3000, streamConfig: StreamConfig(streamEpoch: 3),
                        hostName: "Test PC", authToken: Data(17...32), hostCaps: 0xF000))))
            } else if bytes.prefix(2) == Data("DI".utf8) {
                self.packets.append(bytes)
            }
        }
        channel.onInkFailure = { [weak self] in self?.failures.append($0) }
        connect(test: test)
    }

    func connect(test: XCTestCase) {
        let ready = test.expectation(description: "Authenticated ink channel")
        queue.sync { channel.onSessionEstablished = { _ in ready.fulfill() } }
        channel.startHandshake(listenPort: 50000, identity: ControlChannel.ClientIdentity(
            deviceName: "Test tablet", screenPxW: 640, screenPxH: 360, screenPtW: 640, screenPtH: 360,
            refreshHz: 60, featureCaps: wireless ? Hello2.featureReliableInk : 0, deviceId: 123))
        test.wait(for: [ready], timeout: 3)
    }

    func advance(_ micros: UInt64) { clock.withLock { $0 += micros } }

    func snapshot() -> (packets: [Data], failures: [String]) {
        queue.sync { (packets, failures) }
    }

    func stop() {
        channel.stop()
        queue.sync { }
    }
}
