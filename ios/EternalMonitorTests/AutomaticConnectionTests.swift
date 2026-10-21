import Combine
import Network
import SwiftUI
import XCTest
@testable import EternalMonitor

/// Exercise app connection lifecycle against actual loopback peers. No media,
/// control-channel or connection-manager collaborators are replaced by mocks.
final class AutomaticConnectionTests: XCTestCase {
    @MainActor func testLaunchWithoutUSBConnectsToThePreviouslyPairedLANAddress() async throws {
        let peer = try AutomaticLANPeer()
        let ready = expectation(description: "Saved PC is listening")
        peer.onReady = { ready.fulfill() }
        peer.start()
        defer { peer.stop() }
        await fulfillment(of: [ready], timeout: 3)
        let port = try XCTUnwrap(peer.port)
        let settings = saveSettings()
        defer { restoreSettings(settings) }
        UserDefaults.standard.set(true, forKey: "allowUSB")
        UserDefaults.standard.set(true, forKey: "autoReconnect")
        UserDefaults.standard.set(false, forKey: "playPCaudio")
        UserDefaults.standard.set("127.0.0.1", forKey: "lastHost")
        UserDefaults.standard.set(Int(port), forKey: "lastPort")
        UserDefaults.standard.removeObject(forKey: "lastLANPCID")
        let address = PairingStore.address(host: "127.0.0.1", port: port)
        let token = Data(1...16)
        let pairings = PairingStore(storage: AutomaticPairingStorage())
        try pairings.save(token: token, hostName: "Remembered PC", address: address)
        let hello = expectation(description: "App automatically contacts the remembered paired PC")
        peer.onHello = { message in
            XCTAssertEqual(message.authToken, token)
            XCTAssertEqual(message.pairingCode, 0, "Automatic connection must not bypass pairing")
            hello.fulfill()
        }
        let manager = ConnectionManager(pairingStore: pairings, usbPort: 0)
        defer { manager.cancel() }

        // This is the same entry point used when the app launches.
        manager.refreshUSBAvailability()

        await fulfillment(of: [hello], timeout: 5)
        XCTAssertEqual(manager.state, .connecting)
        XCTAssertEqual(manager.transportMode, "WiFi")
    }

    @MainActor func testUSBWinsOverLANAndUnpluggingReturnsToTheSameAuthenticatedPC() async throws {
        let settings = saveSettings()
        defer { restoreSettings(settings) }
        let token = Data(1...16), pcID = Data(65...80)
        let peer = try AutomaticLANPeer(token: token, pcID: pcID)
        let port = try await start(peer)
        defer { peer.stop() }
        let pairings = try prepare(port: port, token: token)
        try pairings.save(token: token, hostName: "PC", address: "USB", pcID: pcID)
        let manager = ConnectionManager(pairingStore: pairings, usbPort: 0)
        defer { manager.cancel() }
        manager.connect(host: "127.0.0.1", port: port)
        try await waitFor(manager, transport: "WiFi")
        XCTAssertEqual(manager.videoSize, CGSize(width: 64, height: 64))
        let usb = try AutomaticUSBPeer(port: await listeningPort(manager), token: token, pcID: pcID)
        defer { usb.stop() }
        usb.start()
        try await waitFor(manager, transport: "USB")
        XCTAssertEqual(manager.hostInfo?.pcID, pcID)

        usb.stop()

        try await waitFor(manager, transport: "WiFi")
        XCTAssertEqual(manager.hostInfo?.pcID, pcID)
        XCTAssertNil(manager.pairingPrompt)
        XCTAssertEqual(manager.videoSize, CGSize(width: 64, height: 64))
    }

    @MainActor func testUnpluggingAnotherUSBPCDoesNotReconnectThePreviousLANPC() async throws {
        let settings = saveSettings()
        defer { restoreSettings(settings) }
        let token = Data(1...16), pcID = Data(65...80)
        let peer = try AutomaticLANPeer(token: token, pcID: pcID)
        let port = try await start(peer)
        defer { peer.stop() }
        let pairings = try prepare(port: port, token: token)
        let otherToken = Data(17...32), otherID = Data(81...96)
        try pairings.save(token: otherToken, hostName: "Other PC", address: "USB", pcID: otherID)
        let manager = ConnectionManager(pairingStore: pairings, usbPort: 0)
        defer { manager.cancel() }
        manager.connect(host: "127.0.0.1", port: port)
        try await waitFor(manager, transport: "WiFi")
        let usb = try AutomaticUSBPeer(port: await listeningPort(manager), token: otherToken, pcID: otherID)
        defer { usb.stop() }
        usb.start()
        try await waitFor(manager, transport: "USB")
        let unexpected = expectation(description: "A different PC must not receive an automatic LAN connection")
        unexpected.isInverted = true
        peer.observeNewHellos { _ in unexpected.fulfill() }

        usb.stop()

        await fulfillment(of: [unexpected], timeout: 2.5)
        XCTAssertEqual(manager.state, .disconnected)
        XCTAssertEqual(manager.reconnectAttempt, 0)
    }

    @MainActor func testCancelStopsPendingAutomaticConnectionsAcrossForegrounding() async throws {
        let settings = saveSettings()
        defer { restoreSettings(settings) }
        let peer = try AutomaticLANPeer()
        let port = try await start(peer)
        defer { peer.stop() }
        let pairings = try prepare(port: port, token: Data(1...16))
        let unexpected = expectation(description: "X prevents a queued automatic connection")
        unexpected.isInverted = true
        peer.observeNewHellos { _ in unexpected.fulfill() }
        let manager = ConnectionManager(pairingStore: pairings, usbPort: 0)
        defer { manager.cancel() }
        manager.refreshUSBAvailability()

        manager.cancel()
        manager.handleAppBackgrounded()
        manager.handleAppForegrounded()

        await fulfillment(of: [unexpected], timeout: 2)
        XCTAssertEqual(manager.state, .disconnected)
        XCTAssertNil(manager.usbListeningPort)
        XCTAssertEqual(manager.usbStatus, "USB: disconnected")
    }

    @MainActor func testAnAttachedUSBTunnelIsPreferredBeforeSavedLANStarts() async throws {
        let settings = saveSettings()
        defer { restoreSettings(settings) }
        let token = Data(1...16), pcID = Data(65...80)
        let peer = try AutomaticLANPeer()
        let port = try await start(peer)
        defer { peer.stop() }
        let pairings = try prepare(port: port, token: token)
        try pairings.save(token: token, hostName: "PC", address: "USB", pcID: pcID)
        let unexpected = expectation(description: "USB arrival cancels the deferred LAN connection")
        unexpected.isInverted = true
        peer.observeNewHellos { _ in unexpected.fulfill() }
        let manager = ConnectionManager(pairingStore: pairings, usbPort: 0)
        defer { manager.cancel() }
        manager.refreshUSBAvailability()
        let usb = try AutomaticUSBPeer(port: await listeningPort(manager), token: token, pcID: pcID)
        defer { usb.stop() }
        usb.start()

        try await waitFor(manager, transport: "USB")

        await fulfillment(of: [unexpected], timeout: 1.5)
        XCTAssertEqual(manager.hostInfo?.pcID, pcID)
    }

    @MainActor func testRememberedAddressWithoutAPairingIsNotContactedAutomatically() async throws {
        let settings = saveSettings()
        defer { restoreSettings(settings) }
        let peer = try AutomaticLANPeer()
        let port = try await start(peer)
        defer { peer.stop() }
        let pairings = try prepare(port: port, token: Data(1...16))
        try pairings.forgetAll()
        let unexpected = expectation(description: "No auto-discovery or automatic trust of an unpaired address")
        unexpected.isInverted = true
        peer.observeNewHellos { _ in unexpected.fulfill() }
        let manager = ConnectionManager(pairingStore: pairings, usbPort: 0)
        defer { manager.cancel() }
        manager.refreshUSBAvailability()
        await fulfillment(of: [unexpected], timeout: 1.5)
        XCTAssertEqual(manager.state, .disconnected)
        XCTAssertNil(manager.pairingPrompt)
    }

    @MainActor func testAutomaticLANRejectsAChangedPCIdentityBeforeDisplayingItsBoard() async throws {
        let settings = saveSettings()
        defer { restoreSettings(settings) }
        let token = Data(1...16)
        let peer = try AutomaticLANPeer(token: token, pcID: Data(81...96))
        let port = try await start(peer)
        defer { peer.stop() }
        let pairings = try prepare(port: port, token: token)
        UserDefaults.standard.set(Data(65...80), forKey: "lastLANPCID")
        let manager = ConnectionManager(pairingStore: pairings, usbPort: 0)
        defer { manager.cancel() }
        let rejected = expectation(description: "Changed PC identity stops automatic selection")
        let observation = manager.$connectionError.compactMap { $0 }.filter { $0.contains("다른 PC") }
            .first().sink { _ in rejected.fulfill() }
        defer { observation.cancel() }
        manager.refreshUSBAvailability()
        await fulfillment(of: [rejected], timeout: 5)
        XCTAssertEqual(manager.state, .disconnected)
        XCTAssertNil(manager.hostInfo)
        XCTAssertEqual(manager.videoSize, .zero)
    }

    @MainActor func testAnIncompatibleRememberedPCStopsRetriesAndKeepsTheUpdateExplanation() async throws {
        let settings = saveSettings()
        defer { restoreSettings(settings) }
        let token = Data(1...16)
        let peer = try AutomaticLANPeer(token: token, pcID: Data(65...80), rejection: .versionUnsupported)
        let port = try await start(peer)
        defer { peer.stop() }
        let pairings = try prepare(port: port, token: token)
        let manager = ConnectionManager(pairingStore: pairings, usbPort: 0)
        defer { manager.cancel() }
        let rejected = expectation(description: "The user sees how to update both DeskInk apps")
        let observation = manager.$connectionError.compactMap { $0 }.filter { $0.contains("IPA") }
            .first().sink { _ in rejected.fulfill() }
        defer { observation.cancel() }
        manager.refreshUSBAvailability()
        await fulfillment(of: [rejected], timeout: 5)
        let explanation = manager.connectionError
        let unexpected = expectation(description: "A terminal version error does not retry automatically")
        unexpected.isInverted = true
        peer.observeNewHellos { _ in unexpected.fulfill() }
        await fulfillment(of: [unexpected], timeout: 2.5)
        XCTAssertEqual(manager.connectionError, explanation)
        XCTAssertEqual(manager.state, .disconnected)
        XCTAssertEqual(manager.reconnectAttempt, 0)
    }

    @MainActor func testBackgroundingAManualUnansweredPCDoesNotSelectAnOlderRememberedPC() async throws {
        let settings = saveSettings()
        defer { restoreSettings(settings) }
        let oldPC = try AutomaticLANPeer()
        let oldPort = try await start(oldPC)
        defer { oldPC.stop() }
        let selectedPC = try AutomaticLANPeer()
        let selectedPort = try await start(selectedPC)
        defer { selectedPC.stop() }
        let pairings = try prepare(port: oldPort, token: Data(1...16))
        let selected = expectation(description: "Only the manually selected PC receives HELLO")
        selectedPC.observeNewHellos { _ in selected.fulfill() }
        let unexpected = expectation(description: "App resume must not switch to an older saved PC")
        unexpected.isInverted = true
        oldPC.observeNewHellos { _ in unexpected.fulfill() }
        let manager = ConnectionManager(pairingStore: pairings, usbPort: 0)
        defer { manager.cancel() }
        manager.connect(host: "127.0.0.1", port: selectedPort)
        await fulfillment(of: [selected], timeout: 3)
        XCTAssertNil(manager.hostInfo, "The selected PC has not authenticated or displayed any picture")

        manager.handleAppBackgrounded()
        manager.handleAppForegrounded()

        await fulfillment(of: [unexpected], timeout: 2.5)
        XCTAssertNil(manager.hostInfo)
    }

    @MainActor func testTheRealWirelessDisplayAllowsPencilInkToReachThePC() async throws {
        let settings = saveSettings()
        defer { restoreSettings(settings) }
        let token = Data(1...16)
        let peer = try AutomaticLANPeer(token: token, pcID: Data(65...80))
        let port = try await start(peer)
        defer { peer.stop() }
        let pairings = try prepare(port: port, token: token)
        let manager = ConnectionManager(pairingStore: pairings, usbPort: 0)
        defer { manager.cancel() }
        manager.connect(host: "127.0.0.1", port: port)
        try await waitFor(manager, transport: "WiFi")
        let received = expectation(description: "Pencil start crossed the real LAN socket")
        peer.observeInk { received.fulfill() }
        let host = UIHostingController(rootView: DeskInkDisplay().environmentObject(manager))
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 1180, height: 820))
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true; window.rootViewController = nil }
        host.view.frame = window.bounds
        host.view.layoutIfNeeded()
        try await Task.sleep(for: .milliseconds(100))
        host.view.layoutIfNeeded()
        func canvas(in view: UIView) -> InkCanvasView? {
            if let found = view as? InkCanvasView { return found }
            return view.subviews.lazy.compactMap { canvas(in: $0) }.first
        }
        let surface = try XCTUnwrap(canvas(in: host.view))

        XCTAssertTrue(surface.beginStroke(at: CGPoint(x: surface.bounds.midX, y: surface.bounds.midY), pressure: 0.5),
            "The actual display must enable Pencil input for reliable LAN, just as it does for USB")
        surface.finish()

        await fulfillment(of: [received], timeout: 2)
        XCTAssertEqual(surface.document.strokes.count, 1)
    }

    @MainActor private func start(_ peer: AutomaticLANPeer) async throws -> UInt16 {
        let ready = expectation(description: "Remote LAN socket bound")
        peer.onReady = { ready.fulfill() }
        peer.start()
        await fulfillment(of: [ready], timeout: 3)
        return try XCTUnwrap(peer.port)
    }

    @MainActor private func prepare(port: UInt16, token: Data) throws -> PairingStore {
        let defaults = UserDefaults.standard
        defaults.set(true, forKey: "allowUSB")
        defaults.set(true, forKey: "autoReconnect")
        defaults.set(true, forKey: "autoResumeOnForeground")
        defaults.set(false, forKey: "playPCaudio")
        defaults.set("127.0.0.1", forKey: "lastHost")
        defaults.set(Int(port), forKey: "lastPort")
        defaults.removeObject(forKey: "lastLANPCID")
        let pairings = PairingStore(storage: AutomaticPairingStorage())
        try pairings.save(token: token, hostName: "PC", address: PairingStore.address(host: "127.0.0.1", port: port))
        return pairings
    }

    @MainActor private func listeningPort(_ manager: ConnectionManager) async throws -> UInt16 {
        let ready = expectation(description: "USB loopback listener bound")
        let observation = manager.$usbListeningPort.compactMap { $0 }.first().sink { _ in ready.fulfill() }
        defer { observation.cancel() }
        await fulfillment(of: [ready], timeout: 3)
        return try XCTUnwrap(manager.usbListeningPort)
    }

    @MainActor private func waitFor(_ manager: ConnectionManager, transport: String) async throws {
        let connected = expectation(description: "First \(transport) picture decoded")
        let observation = manager.$state.combineLatest(manager.$transportMode)
            .filter { $0.0 == .connected && $0.1 == transport }.first().sink { _ in connected.fulfill() }
        defer { observation.cancel() }
        await fulfillment(of: [connected], timeout: 8)
        XCTAssertEqual(manager.state, .connected)
        XCTAssertEqual(manager.transportMode, transport)
    }

    @MainActor private func saveSettings() -> [String: Any] {
        Dictionary(uniqueKeysWithValues: Self.settingsKeys.compactMap { key in
            UserDefaults.standard.object(forKey: key).map { (key, $0) }
        })
    }

    @MainActor private func restoreSettings(_ saved: [String: Any]) {
        for key in Self.settingsKeys {
            if let value = saved[key] { UserDefaults.standard.set(value, forKey: key) }
            else { UserDefaults.standard.removeObject(forKey: key) }
        }
    }

    private static let settingsKeys = ["allowUSB", "autoReconnect", "autoResumeOnForeground",
        "playPCaudio", "lastHost", "lastPort", "lastLANPCID", "recentConnections"]
}

/// Keychain is an external system seam; unsigned simulator apps have no
/// Keychain entitlement. The real PairingStore still handles all credentials.
private final class AutomaticPairingStorage: PairingStorage {
    private var values: [String: Data] = [:]
    func records() throws -> [PairingRecord] {
        values.map { PairingRecord(account: $0.key, token: $0.value) }
    }
    func save(account: String, token: Data) throws { values[account] = token }
    func remove(account: String?) throws {
        if let account { values.removeValue(forKey: account) }
        else { values.removeAll() }
    }
}

/// The remote PC lives on the other side of a real UDP socket, so HELLO
/// registration and address/port selection travel through UDPReceiver.
private final class AutomaticLANPeer {
    var onReady: (() -> Void)?
    var onHello: ((Hello2) -> Void)?
    var port: UInt16? { listener.port?.rawValue }
    private let listener: NWListener
    private let queue = DispatchQueue(label: "test.automatic.lan-peer")
    private var connections: [NWConnection] = []
    private var seenNonces: Set<UInt32> = []
    private let stream: AutomaticStreamPeer?
    private var onInk: (() -> Void)?
    private var observedInk = false

    init(token: Data? = nil, pcID: Data = Data(), rejection: HelloStatus? = nil) throws {
        stream = try token.map { try AutomaticStreamPeer(token: $0, pcID: pcID, rejection: rejection) }
        let parameters = NWParameters.udp
        parameters.requiredLocalEndpoint = .hostPort(host: .ipv4(.loopback), port: .any)
        listener = try NWListener(using: parameters)
    }

    func observeNewHellos(_ callback: @escaping (Hello2) -> Void) { queue.sync { onHello = callback } }
    func observeInk(_ callback: @escaping () -> Void) { queue.sync { onInk = callback } }

    func start() {
        listener.stateUpdateHandler = { [weak self] state in
            if case .ready = state { self?.onReady?() }
        }
        listener.newConnectionHandler = { [weak self] connection in
            guard let self else { connection.cancel(); return }
            self.connections.append(connection)
            connection.start(queue: self.queue)
            self.receive(connection)
        }
        listener.start(queue: queue)
    }

    private func receive(_ connection: NWConnection) {
        connection.receiveMessage { [weak self, weak connection] bytes, _, _, error in
            guard let self, let connection, error == nil else { return }
            if let bytes {
                if case .hello2(let hello) = Wire.parseControl(bytes)?.message,
                   self.seenNonces.insert(hello.clientNonce).inserted {
                    self.onHello?(hello)
                    self.stream?.hello(hello, queue: self.queue, send: { packet in
                        connection.send(content: packet, completion: .contentProcessed { _ in })
                    })
                } else if bytes.prefix(4) == Data("DI02".utf8) {
                    if !self.observedInk { self.observedInk = true; self.onInk?() }
                    if let object = try? JSONSerialization.jsonObject(with: bytes.dropFirst(4)) as? [String: Any],
                       let session = object["session"] as? UInt32,
                       let epoch = object["epoch"] as? UInt32,
                       let sequence = object["seq"] as? UInt64 {
                        var ack = Data("DA02".utf8)
                        for value in [session, epoch] { var le = value.littleEndian; withUnsafeBytes(of: &le) { ack.append(contentsOf: $0) } }
                        var le = sequence.littleEndian
                        withUnsafeBytes(of: &le) { ack.append(contentsOf: $0) }
                        connection.send(content: ack, completion: .contentProcessed { _ in })
                    }
                }
            }
            self.receive(connection)
        }
    }

    func stop() {
        listener.cancel()
        queue.sync {
            stream?.stop()
            for connection in connections { connection.cancel() }
            connections.removeAll()
        }
    }
}

/// A Windows USB tunnel is represented by the real TCP preamble and framed
/// datagrams. Nothing calls ConnectionManager's private callbacks directly.
private final class AutomaticUSBPeer {
    private let connection: NWConnection
    private let queue = DispatchQueue(label: "test.automatic.usb-peer")
    private let stream: AutomaticStreamPeer
    private var framer = EMLinkFramer(expectsPreamble: false)

    init(port: UInt16, token: Data, pcID: Data) throws {
        connection = NWConnection(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: port)!, using: .tcp)
        stream = try AutomaticStreamPeer(token: token, pcID: pcID)
    }

    func start() {
        connection.stateUpdateHandler = { [weak self] state in
            guard let self, case .ready = state else { return }
            self.connection.send(content: EMLinkFramer.preamble, completion: .contentProcessed { _ in })
            self.receive()
        }
        connection.start(queue: queue)
    }

    private func receive() {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 16384) { [weak self] bytes, _, complete, error in
            guard let self, error == nil, !complete else { return }
            if let bytes, let packets = try? self.framer.append(bytes) {
                for packet in packets {
                    if case .hello2(let hello) = Wire.parseControl(packet)?.message {
                        self.stream.hello(hello, queue: self.queue, send: { [weak self] datagram in
                            guard let framed = try? EMLinkFramer.encode(datagram) else { return }
                            self?.connection.send(content: framed, completion: .contentProcessed { _ in })
                        })
                    }
                }
            }
            self.receive()
        }
    }

    func stop() {
        queue.sync { stream.stop(); connection.stateUpdateHandler = nil; connection.cancel() }
    }
}

/// A tiny protocol-speaking PC sends a real H.264 IDR and heartbeats. It accepts
/// only its paired credential, so tests also cross the ordinary authentication.
private final class AutomaticStreamPeer {
    private let token: Data
    private let pcID: Data
    private let rejection: HelloStatus?
    private let frame: Data
    private var timer: DispatchSourceTimer?
    private var frameSequence: UInt32 = 0
    private var session: UInt32 = 100

    init(token: Data, pcID: Data, rejection: HelloStatus? = nil) throws {
        self.token = token
        self.pcID = pcID
        self.rejection = rejection
        let url = try XCTUnwrap(Bundle(for: AutomaticConnectionTests.self).url(
            forResource: "single_idr_64x64", withExtension: "h264", subdirectory: "Fixtures"))
        frame = try Data(contentsOf: url)
    }

    func hello(_ hello: Hello2, queue: DispatchQueue, send: @escaping (Data) -> Void) {
        let accepted = hello.authToken == token && rejection == nil
        var ack = HelloAck(status: rejection ?? (accepted ? .ok : .unauthorized), acceptedVersion: 2,
            clientNonce: hello.clientNonce, sessionId: accepted ? session : 0,
            heartbeatIntervalMs: 500, reportIntervalMs: 500, livenessTimeoutMs: 3000,
            streamConfig: StreamConfig(streamEpoch: 1), hostName: "PC · DISPLAY1", authToken: accepted ? token : Data(repeating: 0, count: 16),
            hostCaps: 0xF000)
        ack.pcID = pcID
        send(Wire.encodeControl(sessionId: 0, msgSeq: 1, message: .helloAck(ack)))
        guard accepted else { return }
        timer?.cancel()
        frameSequence = 0
        let activeSession = session
        session += 1
        let timer = DispatchSource.makeTimerSource(queue: queue)
        timer.schedule(deadline: .now() + .milliseconds(100), repeating: .milliseconds(100))
        timer.setEventHandler { [weak self] in
            guard let self else { return }
            self.frameSequence += 1
            send(Wire.encodeControl(sessionId: activeSession, msgSeq: self.frameSequence + 1,
                message: .heartbeat(Heartbeat(hostTimeUs: ControlChannel.clientNowUs(), streamConfig: StreamConfig(streamEpoch: 1)))))
            let fragments = (self.frame.count + MediaHeader.maxPayload - 1) / MediaHeader.maxPayload
            for index in 0..<fragments {
                let start = index * MediaHeader.maxPayload
                let payload = self.frame.subdata(in: start..<min(start + MediaHeader.maxPayload, self.frame.count))
                send(MediaHeader(sessionId: activeSession, streamEpoch: 1, frameSeq: self.frameSequence,
                    fragIndex: UInt16(index), fragCount: UInt16(fragments), isKeyframe: true,
                    captureTimestampUs: ControlChannel.clientNowUs(), payloadLen: UInt16(payload.count)).encode(payload: payload))
            }
        }
        self.timer = timer
        timer.resume()
    }

    func stop() { timer?.cancel(); timer = nil }
}
