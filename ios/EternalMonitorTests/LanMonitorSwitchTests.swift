import XCTest
@testable import EternalMonitor

final class LanMonitorSwitchTests: XCTestCase {
    func testNewWirelessEpochReauthenticatesTheMonitorBeforeForwardingConfigOrInk() throws {
        let queue = DispatchQueue(label: "lan-monitor-refresh-test")
        let first = expectation(description: "DISPLAY1 authenticated")
        let refreshing = expectation(description: "Media and ink paused for monitor refresh")
        let newHello = expectation(description: "New monitor identity requested")
        let second = expectation(description: "DISPLAY2 authenticated")
        let issuedToken = Data(17...32)
        var hellos: [Hello2] = [], sessions: [ControlChannel.SessionInfo] = [], ink: [Data] = []
        var forwardedEpochs: [UInt32] = []
        var inkAtRefresh = 0
        var channel: ControlChannel!
        channel = ControlChannel(queue: queue) { bytes in
            if case .hello2(let hello) = Wire.parseControl(bytes)?.message {
                guard hellos.last?.clientNonce != hello.clientNonce else { return }
                hellos.append(hello)
                if hellos.count == 1 {
                    channel.handleControl(Self.ack(hello, session: 41, epoch: 3, name: "PC · DISPLAY1", token: issuedToken))
                } else if hellos.count == 2 {
                    newHello.fulfill()
                }
            } else if bytes.prefix(4) == Data("DI02".utf8) {
                ink.append(bytes)
            }
        }
        channel.onSessionEstablished = { info in
            sessions.append(info)
            if sessions.count == 1 { first.fulfill() } else { second.fulfill() }
        }
        channel.onSessionRefreshing = { inkAtRefresh = ink.count; refreshing.fulfill() }
        channel.onHeartbeat = { forwardedEpochs.append($0.streamConfig.streamEpoch) }
        channel.onStreamConfig = { forwardedEpochs.append($0.streamEpoch) }
        channel.onInkFailure = { XCTFail($0) }
        defer { channel.stop(); queue.sync { channel = nil } }
        channel.startHandshake(listenPort: 50000, identity: Self.identity)
        wait(for: [first], timeout: 3)
        channel.sendInk([.clear], epoch: 3)
        queue.sync {
            channel.handleControl(Self.heartbeat(session: 41, epoch: 2))
            XCTAssertTrue(forwardedEpochs.isEmpty, "An old stream must not replace the current board config")
            XCTAssertEqual(hellos.count, 1)
            channel.handleControl(Self.heartbeat(session: 41, epoch: 4))
        }
        wait(for: [refreshing, newHello], timeout: 3)
        XCTAssertEqual(channel.currentSessionId, 0)
        channel.sendInk([.undo], epoch: 3)
        channel.sendInk([.redo], epoch: 4)
        let refreshHello = queue.sync { () -> Hello2 in
            XCTAssertGreaterThanOrEqual(inkAtRefresh, 1)
            XCTAssertEqual(ink.count, inkAtRefresh, "No old pending ink or new-board edit can escape while the identity is unknown")
            XCTAssertTrue(forwardedEpochs.isEmpty, "New-epoch config waits for an authenticated monitor label")
            return hellos[1]
        }
        XCTAssertEqual(refreshHello.authToken, issuedToken, "Use the token issued by the successful ACK")
        XCTAssertEqual(refreshHello.pairingCode, 0)
        XCTAssertEqual(refreshHello.listenPort, 50000)
        XCTAssertEqual(refreshHello.deviceId, Self.identity.deviceId)
        XCTAssertNotEqual(refreshHello.clientNonce, queue.sync { hellos[0].clientNonce })
        queue.sync {
            channel.handleControl(Self.ack(refreshHello, session: 42, epoch: 4, name: "PC · DISPLAY2", token: issuedToken))
        }
        wait(for: [second], timeout: 3)
        channel.sendInk([.clear], epoch: 4)
        let lastInk = try XCTUnwrap(queue.sync { ink.last })
        let body = try XCTUnwrap(JSONSerialization.jsonObject(with: lastInk.dropFirst(4)) as? [String: Any])
        XCTAssertEqual(body["session"] as? Int, 42)
        XCTAssertEqual(body["epoch"] as? Int, 4)
        XCTAssertEqual(body["seq"] as? Int, 1)
        queue.sync {
            XCTAssertEqual(sessions.map(\.hostName), ["PC · DISPLAY1", "PC · DISPLAY2"])
            channel.handleControl(Self.heartbeat(session: 42, epoch: 3))
            XCTAssertTrue(forwardedEpochs.isEmpty)
            channel.handleControl(Self.heartbeat(session: 42, epoch: 4))
            XCTAssertEqual(forwardedEpochs, [4])
            XCTAssertEqual(hellos.count, 2)
        }
    }

    func testUnansweredWirelessMonitorRefreshFailsWithAReconnectMessage() {
        let queue = DispatchQueue(label: "lan-monitor-refresh-timeout-test")
        let first = expectation(description: "Initial authenticated session")
        let failed = expectation(description: "Refresh has a bounded timeout")
        var acceptedInitial = false
        var message = ""
        var channel: ControlChannel!
        channel = ControlChannel(queue: queue) { bytes in
            guard !acceptedInitial, case .hello2(let hello) = Wire.parseControl(bytes)?.message else { return }
            acceptedInitial = true
            channel.handleControl(Self.ack(hello, session: 41, epoch: 3, name: "DISPLAY1", token: Data(17...32)))
        }
        channel.onSessionEstablished = { _ in first.fulfill() }
        channel.onInkFailure = { message = $0; failed.fulfill() }
        defer { channel.stop(); queue.sync { channel = nil } }
        channel.startHandshake(listenPort: 50000, identity: Self.identity)
        wait(for: [first], timeout: 3)
        queue.sync { channel.handleControl(Self.heartbeat(session: 41, epoch: 4)) }
        wait(for: [failed], timeout: 4)
        XCTAssertTrue(queue.sync { message.contains("다시 연결") })
        XCTAssertEqual(channel.currentSessionId, 0)
    }

    func testUsbEpochUpdatesKeepTheExistingSessionAndDoNotStartWirelessRefresh() {
        let queue = DispatchQueue(label: "usb-epoch-unchanged-test")
        let first = expectation(description: "USB session authenticated")
        var hellos = 0
        var forwarded: [UInt32] = []
        var channel: ControlChannel!
        channel = ControlChannel(queue: queue) { bytes in
            guard case .hello2(let hello) = Wire.parseControl(bytes)?.message else { return }
            hellos += 1
            channel.handleControl(Self.ack(hello, session: 41, epoch: 3, name: "DISPLAY1", token: Data(17...32)))
        }
        channel.onSessionEstablished = { _ in first.fulfill() }
        channel.onSessionRefreshing = { XCTFail("USB must keep its existing reconnect behavior") }
        channel.onHeartbeat = { forwarded.append($0.streamConfig.streamEpoch) }
        defer { channel.stop(); queue.sync { channel = nil } }
        var usbIdentity = Self.identity
        usbIdentity.featureCaps = 0
        channel.startHandshake(listenPort: 9877, identity: usbIdentity)
        wait(for: [first], timeout: 3)
        queue.sync { channel.handleControl(Self.heartbeat(session: 41, epoch: 4)) }
        XCTAssertEqual(channel.currentSessionId, 41)
        queue.sync {
            XCTAssertEqual(hellos, 1)
            XCTAssertEqual(forwarded, [4])
        }
    }

    private static var identity: ControlChannel.ClientIdentity {
        ControlChannel.ClientIdentity(deviceName: "Test iPad", screenPxW: 640, screenPxH: 360,
            screenPtW: 640, screenPtH: 360, refreshHz: 60, featureCaps: Hello2.featureReliableInk,
            deviceId: 123, authToken: Data(1...16))
    }

    private static func ack(_ hello: Hello2, session: UInt32, epoch: UInt32, name: String, token: Data) -> Data {
        Wire.encodeControl(sessionId: 0, msgSeq: 1,
            message: .helloAck(HelloAck(status: .ok, acceptedVersion: 2, clientNonce: hello.clientNonce,
                sessionId: session, heartbeatIntervalMs: 1000, reportIntervalMs: 500,
                livenessTimeoutMs: 3000, streamConfig: StreamConfig(streamEpoch: epoch),
                hostName: name, authToken: token, hostCaps: 0xF000)))
    }

    private static func heartbeat(session: UInt32, epoch: UInt32) -> Data {
        Wire.encodeControl(sessionId: session, msgSeq: 2,
            message: .heartbeat(Heartbeat(hostTimeUs: 1, streamConfig: StreamConfig(streamEpoch: epoch))))
    }
}
