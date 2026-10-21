import Combine
import Network
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
        "playPCaudio", "lastHost", "lastPort", "lastLANPCID", "lastLANAuthToken"]
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

    init() throws {
        let parameters = NWParameters.udp
        parameters.requiredLocalEndpoint = .hostPort(host: .ipv4(.loopback), port: .any)
        listener = try NWListener(using: parameters)
    }

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
            if let bytes, case .hello2(let hello) = Wire.parseControl(bytes)?.message,
               self.seenNonces.insert(hello.clientNonce).inserted {
                self.onHello?(hello)
            }
            self.receive(connection)
        }
    }

    func stop() {
        listener.cancel()
        queue.sync {
            for connection in connections { connection.cancel() }
            connections.removeAll()
        }
    }
}
