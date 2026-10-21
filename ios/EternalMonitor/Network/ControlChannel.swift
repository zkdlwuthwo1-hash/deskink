import Foundation
import os

/// The client side of the protocol-v2 control plane, sharing the media socket.
/// Owns the HELLO2 handshake (retry until acked), periodic receiver reports
/// (which double as the client's liveness signal), keyframe requests, and BYE.
///
/// Threading: all state lives on `queue`; inbound datagrams are handed over
/// via `handleControl`, callbacks fire on `queue` — the ConnectionManager hops
/// to the MainActor itself.
final class ControlChannel {
    struct SessionInfo {
        let sessionId: UInt32
        let hostName: String
        let heartbeatIntervalMs: UInt16
        let reportIntervalMs: UInt16
        let livenessTimeoutMs: UInt16
        let streamConfig: StreamConfig
        let hostCaps: UInt16
        let authToken: Data
        let presentedAuthToken: Data
        let pcID: Data
    }

    var onSessionEstablished: ((SessionInfo) -> Void)?
    var onRejected: ((HelloStatus) -> Void)?
    /// HELLO2 went unanswered for the whole retry budget.
    var onHandshakeTimeout: (() -> Void)?
    var onHelloAttempt: ((Int, Int) -> Void)?
    var onHeartbeat: ((Heartbeat) -> Void)?
    /// Out-of-band stream-parameter change (bitrate, codec, resolution).
    var onStreamConfig: ((StreamConfig) -> Void)?
    var onBye: ((ByeReason) -> Void)?
    var onDiagnostic: ((String) -> Void)?
    var onInkFailure: ((String) -> Void)?
    /// Snapshot provider for periodic receiver reports.
    var reportProvider: (() -> ReceiverReport)?

    private let queue: DispatchQueue
    private let send: (Data) -> Void

    private var sessionId: UInt32 = 0
    private var inkSequence: UInt64 = 0
    private var inkBinding: (UInt32, UInt32) = (0, 0)
    private var reliableInk: ReliableInkSender?
    private var inkRetryTimer: DispatchSourceTimer?
    private var usesReliableInk = false
    private var hostCaps: UInt16 = 0
    private var msgSeq: UInt32 = 0
    private var clientNonce: UInt32 = 0
    private var helloBytes = Data()
    private var helloTemplate: Hello2?
    private var remainingAuthTokens: [Data] = []
    private var helloAttempts = 0
    private var helloTimer: DispatchSourceTimer?
    private var reportTimer: DispatchSourceTimer?
    private var pingTimer: DispatchSourceTimer?
    private var burstPingsRemaining = 0
    private var estimator = ClockEstimator()

    /// Client clock (µs) of the last host heartbeat; 0 = none yet. Read by the
    /// ConnectionManager's watchdog.
    let lastHeartbeatAtUs = OSAllocatedUnfairLock<UInt64>(initialState: 0)
    /// Latest clock-sync results for latency math and the HUD.
    let clockSnapshot = OSAllocatedUnfairLock<ClockSnapshot>(initialState: ClockSnapshot())

    struct ClockSnapshot {
        var offsetUs: Int64?
        var rttUs: UInt64?
    }

    /// Monotonic client clock in microseconds (same domain as CACurrentMediaTime).
    static func clientNowUs() -> UInt64 {
        clock_gettime_nsec_np(CLOCK_UPTIME_RAW) / 1000
    }

    private static let maxHelloAttempts = 8
    private static let helloRetryMs = 250
    private static let pingIntervalMs = 2000
    private static let pingBurstCount = 5
    private static let pingBurstSpacingMs = 200
    /// The host grants one keyframe per 500 ms and ignores the rest. A WiFi
    /// stall can damage many frames at once; ask once for all of them.
    static let keyframeRequestSpacingUs: UInt64 = 500_000

    private let nowUs: () -> UInt64
    private var lastKeyframeRequestUs: UInt64?

    init(queue: DispatchQueue, nowUs: @escaping () -> UInt64 = ControlChannel.clientNowUs,
         send: @escaping (Data) -> Void) {
        self.queue = queue
        self.nowUs = nowUs
        self.send = send
    }

    var currentSessionId: UInt32 {
        queue.sync { sessionId }
    }

    // MARK: - Handshake

    struct ClientIdentity {
        var deviceName: String
        var screenPxW: UInt16
        var screenPxH: UInt16
        var screenPtW: UInt16
        var screenPtH: UInt16
        var refreshHz: UInt8
        var decoderCaps: UInt16 = Hello2.capDecodeH264
        var featureCaps: UInt16 = 0
        var deviceId: UInt64 = 0
        var preferredFPS: UInt8 = 0
        var authToken = Data(repeating: 0, count: 16)
        var pairingCode: UInt32 = 0
        var alternativeAuthTokens: [Data] = []
    }

    /// Begin the HELLO2 retry loop. `listenPort` is the ephemeral local port
    /// the media stream should target.
    func startHandshake(listenPort: UInt16, identity: ClientIdentity) {
        queue.async { [self] in
            stopReliableInk()
            usesReliableInk = identity.featureCaps & Hello2.featureReliableInk != 0
            sessionId = 0
            hostCaps = 0
            msgSeq = 0
            helloAttempts = 0
            remainingAuthTokens = Array(identity.alternativeAuthTokens.filter {
                $0.count == 16 && $0.contains(where: { $0 != 0 }) && $0 != identity.authToken
            }.prefix(1))
            clientNonce = UInt32.random(in: 1...UInt32.max)
            let hello = Hello2(
                clientNonce: clientNonce,
                listenPort: listenPort,
                decoderCaps: identity.decoderCaps,
                featureCaps: identity.featureCaps,
                screenPxW: identity.screenPxW,
                screenPxH: identity.screenPxH,
                screenPtW: identity.screenPtW,
                screenPtH: identity.screenPtH,
                refreshHz: identity.refreshHz,
                deviceName: identity.deviceName,
                deviceId: identity.deviceId,
                preferredFPS: identity.preferredFPS,
                authToken: identity.authToken,
                pairingCode: identity.pairingCode
            )
            helloTemplate = hello
            helloBytes = Wire.encodeControl(sessionId: 0, msgSeq: 1, message: .hello2(hello))

            startHelloTimer()
        }
    }

    /// Retry on the same socket with a fresh nonce. A rejected attempt's
    /// delayed ACK cannot dismiss or accept the next code submission.
    func retryPairing(code: UInt32) {
        queue.async { [self] in
            remainingAuthTokens = []
            retryHello(token: Data(repeating: 0, count: 16), code: code)
        }
    }

    /// Called only on queue, including automatic attempts for the other PC.
    private func retryHello(token: Data, code: UInt32) {
        guard sessionId == 0, var hello = helloTemplate else { return }
        clientNonce = UInt32.random(in: 1...UInt32.max)
        hello.clientNonce = clientNonce
        hello.authToken = token
        hello.pairingCode = code
        helloTemplate = hello
        helloAttempts = 0
        helloBytes = Wire.encodeControl(sessionId: 0, msgSeq: 1, message: .hello2(hello))
        startHelloTimer()
    }

    private func startHelloTimer() {
        let timer = DispatchSource.makeTimerSource(queue: queue)
        timer.schedule(deadline: .now(), repeating: .milliseconds(Self.helloRetryMs))
        timer.setEventHandler { [weak self] in self?.helloTick() }
        helloTimer?.cancel()
        helloTimer = timer
        timer.resume()
    }

    private func helloTick() {
        guard sessionId == 0 else {
            helloTimer?.cancel()
            helloTimer = nil
            return
        }
        helloAttempts += 1
        if helloAttempts > Self.maxHelloAttempts {
            helloTimer?.cancel()
            helloTimer = nil
            onDiagnostic?("HELLO2 unanswered after \(Self.maxHelloAttempts) attempts")
            onHandshakeTimeout?()
            return
        }
        onHelloAttempt?(helloAttempts, Self.maxHelloAttempts)
        send(helloBytes)
    }

    // MARK: - Inbound

    /// Called on `queue` with a datagram already classified as control.
    func handleControl(_ datagram: Data) {
        guard let (header, message) = Wire.parseControl(datagram) else {
            onDiagnostic?("Dropped malformed control datagram (\(datagram.count) bytes)")
            return
        }

        switch message {
        case .helloAck(let ack):
            handleAck(ack)
        case .heartbeat(let heartbeat):
            guard header.sessionId == sessionId, sessionId != 0 else { return }
            reliableInk?.start(session: sessionId, epoch: heartbeat.streamConfig.streamEpoch)
            lastHeartbeatAtUs.withLock { $0 = Self.clientNowUs() }
            onHeartbeat?(heartbeat)
        case .bye(let reason):
            guard header.sessionId == sessionId, sessionId != 0 else { return }
            onBye?(reason)
        case .pong(let pong):
            guard header.sessionId == sessionId, sessionId != 0 else { return }
            let t4 = Self.clientNowUs()
            estimator.addExchange(t1: pong.t1Us, t2: pong.t2Us, t3: pong.t3Us, t4: t4)
            clockSnapshot.withLock {
                $0 = ClockSnapshot(offsetUs: estimator.offsetUs, rttUs: estimator.rttUs)
            }
        case .streamConfig(let config):
            // The host sends this the moment the stream changes (an ABR step,
            // a codec switch) so the UI doesn't wait up to a heartbeat to
            // catch up. Dropping it made the host's immediate notify dead
            // traffic.
            guard header.sessionId == sessionId, sessionId != 0 else { return }
            reliableInk?.start(session: sessionId, epoch: config.streamEpoch)
            onStreamConfig?(config)
        default:
            break
        }
    }

    private func handleAck(_ ack: HelloAck) {
        guard clientNonce != 0, ack.clientNonce == clientNonce else {
            onDiagnostic?("Ignored HELLO_ACK for a stale nonce")
            return
        }
        helloTimer?.cancel()
        helloTimer = nil

        guard ack.status == .ok else {
            // Known credentials remain valid even while new code attempts are
            // rate limited. Exhaust the two saved PCs before showing a prompt.
            if (ack.status == .unauthorized || ack.status == .rateLimited),
               helloTemplate?.pairingCode == 0, !remainingAuthTokens.isEmpty {
                retryHello(token: remainingAuthTokens.removeFirst(), code: 0)
                return
            }
            clientNonce = 0
            onRejected?(ack.status)
            return
        }
        guard sessionId == 0 else { return } // duplicate ack retransmit

        sessionId = ack.sessionId
        hostCaps = ack.hostCaps
        if usesReliableInk, hostCaps & HelloAck.hostCapReliableInk != 0 {
            let delivery = ReliableInkSender(nowUs: nowUs, send: send, failed: { [weak self] message in
                self?.onInkFailure?(message)
            })
            delivery.start(session: sessionId, epoch: ack.streamConfig.streamEpoch)
            reliableInk = delivery
            let timer = DispatchSource.makeTimerSource(queue: queue)
            timer.schedule(deadline: .now() + .milliseconds(20), repeating: .milliseconds(20), leeway: .milliseconds(1))
            timer.setEventHandler { [weak delivery] in delivery?.tick() }
            inkRetryTimer = timer
            timer.resume()
        }
        onDiagnostic?("Session \(ack.sessionId) established with \(ack.hostName)")
        lastHeartbeatAtUs.withLock { $0 = Self.clientNowUs() }
        startReportTimer(intervalMs: max(ack.reportIntervalMs, 100))
        startPinging()
        onSessionEstablished?(SessionInfo(
            sessionId: ack.sessionId,
            hostName: ack.hostName,
            heartbeatIntervalMs: ack.heartbeatIntervalMs,
            reportIntervalMs: ack.reportIntervalMs,
            livenessTimeoutMs: ack.livenessTimeoutMs,
            streamConfig: ack.streamConfig,
            hostCaps: ack.hostCaps,
            authToken: ack.authToken,
            presentedAuthToken: helloTemplate?.authToken ?? Data(),
            pcID: ack.pcID
        ))
    }

    // MARK: - Outbound

    func sendNack(_ nack: Nack) {
        queue.async { [self] in
            guard sessionId != 0, hostCaps & HelloAck.hostCapNack != 0, nack.isValid else { return }
            sendMessage(.nack(nack))
        }
    }

    private func startReportTimer(intervalMs: UInt16) {
        let timer = DispatchSource.makeTimerSource(queue: queue)
        timer.schedule(
            deadline: .now() + .milliseconds(Int(intervalMs)),
            repeating: .milliseconds(Int(intervalMs))
        )
        timer.setEventHandler { [weak self] in
            guard let self, self.sessionId != 0 else { return }
            let report = self.reportProvider?() ?? ReceiverReport()
            self.sendMessage(.receiverReport(report))
        }
        reportTimer?.cancel()
        reportTimer = timer
        timer.resume()
    }

    func sendKeyframeRequest(streamEpoch: UInt32, lastCompleteSeq: UInt32, reason: KeyframeReason) {
        queue.async { [self] in
            guard sessionId != 0 else { return }
            let now = nowUs()
            if let last = lastKeyframeRequestUs, now &- last < Self.keyframeRequestSpacingUs { return }
            lastKeyframeRequestUs = now
            onDiagnostic?("Requesting keyframe (\(reason))")
            sendMessage(.keyframeRequest(KeyframeRequest(
                streamEpoch: streamEpoch,
                lastCompleteSeq: lastCompleteSeq,
                reason: reason
            )))
        }
    }

    /// Fire-and-forget goodbye, sent a few times for loss tolerance.
    /// Say goodbye so the host stops streaming at once instead of waiting out
    /// its liveness timeout.
    ///
    /// The first copy goes out SYNCHRONOUSLY. Callers tear the socket down on
    /// the very next line, so anything merely enqueued here was still sitting
    /// on the queue when the connection was cancelled — the host saw no BYE,
    /// kept sending into a dead peer for the full three seconds, held the
    /// virtual display up for the same three seconds, and answered an
    /// immediate reconnect with "busy".
    func sendBye(_ reason: ByeReason) {
        queue.sync { [self] in
            guard sessionId != 0 else { return }
            sendMessage(.bye(reason))
        }
        // Two more for loss tolerance; these are best-effort and become no-ops
        // once the socket is gone.
        for delay in [50, 100] {
            queue.asyncAfter(deadline: .now() + .milliseconds(delay)) { [weak self] in
                guard let self, self.sessionId != 0 else { return }
                self.sendMessage(.bye(reason))
            }
        }
    }

    private func startPinging() {
        burstPingsRemaining = Self.pingBurstCount
        let timer = DispatchSource.makeTimerSource(queue: queue)
        timer.schedule(deadline: .now(), repeating: .milliseconds(Self.pingBurstSpacingMs))
        timer.setEventHandler { [weak self] in self?.pingTick() }
        pingTimer?.cancel()
        pingTimer = timer
        timer.resume()
    }

    private func pingTick() {
        guard sessionId != 0 else { return }
        sendMessage(.ping(WirePing(t1Us: Self.clientNowUs())))
        if burstPingsRemaining > 0 {
            burstPingsRemaining -= 1
            if burstPingsRemaining == 0 {
                // Burst done: settle into the steady cadence.
                pingTimer?.schedule(
                    deadline: .now() + .milliseconds(Self.pingIntervalMs),
                    repeating: .milliseconds(Self.pingIntervalMs)
                )
            }
        }
    }

    func stop() {
        queue.async { [self] in
            stopReliableInk()
            usesReliableInk = false
            helloTimer?.cancel()
            helloTimer = nil
            reportTimer?.cancel()
            reportTimer = nil
            pingTimer?.cancel()
            pingTimer = nil
            helloTemplate = nil
            remainingAuthTokens = []
            helloBytes = Data()
            clientNonce = 0
            sessionId = 0
            hostCaps = 0
            estimator.reset()
            lastHeartbeatAtUs.withLock { $0 = 0 }
            clockSnapshot.withLock { $0 = ClockSnapshot() }
        }
    }

    /// Fire-and-forget input relay event. Hot path (up to ~240 Hz while
    /// dragging) — no logging, silently dropped before the session is up.
    func sendInput(_ event: WireInputEvent) {
        sendInputs([event])
    }

    /// Keep a coalesced Pencil sample batch in order through one queue hop.
    func sendInputs(_ events: [WireInputEvent]) {
        queue.async { [self] in
            guard sessionId != 0 else { return }
            for event in events {
                if event.inputVer == 2 && self.hostCaps & HelloAck.hostCapPen == 0 { continue }
                guard event.kind < 4 || hostCaps & HelloAck.hostCapKeyboard != 0 else { continue }
                sendMessage(.inputEvent(event))
            }
        }
    }

    private func sendMessage(_ message: ControlMessage) {
        msgSeq &+= 1
        send(Wire.encodeControl(sessionId: sessionId, msgSeq: msgSeq, message: message))
    }

    /// Called on the same serial link queue as handleControl(_:).
    func handleInkAcknowledgement(_ bytes: Data) {
        reliableInk?.acknowledge(bytes)
    }

    private func stopReliableInk() {
        inkRetryTimer?.cancel()
        inkRetryTimer = nil
        reliableInk?.stop()
        reliableInk = nil
    }

    func sendInk(_ commands: [DeskInkCommand], epoch: UInt32) {
        queue.async { [self] in
            guard sessionId != 0, hostCaps & 0x1000 != 0 else { return }
            if usesReliableInk {
                // A delayed UI sample from the previous stream cannot reset
                // the sender to a retired epoch. Config messages own its binding.
                guard reliableInk?.accepts(session: sessionId, epoch: epoch) == true else { return }
                reliableInk?.send(commands)
                return
            }
            if inkBinding != (sessionId, epoch) {
                inkBinding = (sessionId, epoch)
                inkSequence = 0
            }
            let encoder = JSONEncoder()
            for command in commands {
                guard inkSequence < UInt64.max,
                      let body = try? encoder.encode(DeskInkEnvelope(session: sessionId, epoch: epoch, seq: inkSequence + 1, command: command)),
                      body.count <= 1024 else { return }
                inkSequence += 1
                var packet = Data("DI01".utf8)
                packet.append(body)
                send(packet)
            }
        }
    }
}
