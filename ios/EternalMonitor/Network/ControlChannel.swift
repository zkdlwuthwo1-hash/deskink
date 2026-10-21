import Foundation
import os

/// The client side of the protocol-v2 control plane, sharing the media socket.
/// Owns the HELLO2 handshake (retry until acked), periodic receiver reports
/// (which double as the client's liveness signal), keyframe requests, and BYE.
///
/// Threading: protocol state lives on `queue`; the small ingress budget is
/// locked so callers cannot create unbounded work before reaching that queue.
/// Inbound datagrams are handed over
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
    /// LAN monitor changes need a new authenticated host label before drawing resumes.
    var onSessionRefreshing: (() -> Void)?
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

    private struct InkIngress {
        var reliable = false
        var generation: UInt64 = 0
        var session: UInt32 = 0
        var epoch: UInt32 = 0
        var outstanding = 0
        var failed = false
    }
    private enum InkAdmission {
        case legacy
        case discard
        case accepted(UInt64)
        case overflow(UInt64)
    }
    private let inkIngress = OSAllocatedUnfairLock(initialState: InkIngress())

    private var sessionId: UInt32 = 0
    private var inkSequence: UInt64 = 0
    private var inkBinding: (UInt32, UInt32) = (0, 0)
    private var reliableInk: ReliableInkSender?
    private var inkRetryTimer: DispatchSourceTimer?
    private var usesReliableInk = false
    private var authenticatedEpoch: UInt32 = 0
    private var authenticatedToken = Data()
    private var refreshingSession = false
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

    /// The injected monotonic clock must be thread-safe: LAN ingress reads it
    /// on the caller's thread, and retries/ACK handling read it on the link queue.
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
        resetInkIngress(reliable: identity.featureCaps & Hello2.featureReliableInk != 0)
        queue.async { [self] in
            stopReliableInk()
            usesReliableInk = identity.featureCaps & Hello2.featureReliableInk != 0
            authenticatedEpoch = 0
            authenticatedToken = Data()
            refreshingSession = false
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
            if refreshingSession {
                refreshingSession = false
                clientNonce = 0
                onInkFailure?("전환한 모니터의 연결을 확인하지 못했습니다. Windows DeskInk와 네트워크를 확인한 뒤 다시 연결하세요.")
                return
            }
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
            guard acceptsStreamConfig(heartbeat.streamConfig) else { return }
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
            guard acceptsStreamConfig(config) else { return }
            onStreamConfig?(config)
        default:
            break
        }
    }

    /// A UDP pipeline restart can select another monitor without changing the
    /// transport session. Only HELLO_ACK carries its authenticated monitor label.
    private func acceptsStreamConfig(_ config: StreamConfig) -> Bool {
        guard usesReliableInk, hostCaps & HelloAck.hostCapReliableInk != 0 else { return true }
        guard config.streamEpoch != 0 else { return false }
        if config.streamEpoch == authenticatedEpoch { return true }
        guard authenticatedEpoch != 0, Int32(bitPattern: config.streamEpoch &- authenticatedEpoch) > 0 else { return false }
        stopReliableInk()
        reportTimer?.cancel()
        reportTimer = nil
        pingTimer?.cancel()
        pingTimer = nil
        sessionId = 0
        hostCaps = 0
        refreshingSession = true
        remainingAuthTokens = []
        lastKeyframeRequestUs = nil
        onSessionRefreshing?()
        retryHello(token: authenticatedToken, code: 0)
        return false
    }

    private func handleAck(_ ack: HelloAck) {
        guard clientNonce != 0, ack.clientNonce == clientNonce else {
            onDiagnostic?("Ignored HELLO_ACK for a stale nonce")
            return
        }
        helloTimer?.cancel()
        helloTimer = nil

        guard ack.status == .ok else {
            if refreshingSession {
                refreshingSession = false
                clientNonce = 0
                onInkFailure?("전환한 모니터에 연결하지 못했습니다. Windows DeskInk의 연결 상태를 확인한 뒤 다시 연결하세요.")
                return
            }
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
        authenticatedEpoch = ack.streamConfig.streamEpoch
        authenticatedToken = ack.authToken
        refreshingSession = false
        if usesReliableInk, hostCaps & HelloAck.hostCapReliableInk != 0 {
            let generation = resetInkIngress(reliable: true, session: sessionId, epoch: authenticatedEpoch)
            let delivery = ReliableInkSender(nowUs: nowUs, send: { [weak self] packet in
                // Stop/overflow may arrive from the pen thread while a coalesced
                // batch is already running. Recheck before each actual send.
                guard let self, self.isCurrentInkIngress(generation) else { return }
                self.send(packet)
            }, failed: { [weak self] message in
                self?.reportInkFailure(generation: generation, message: message)
            })
            delivery.start(session: sessionId, epoch: ack.streamConfig.streamEpoch)
            reliableInk = delivery
            let timer = DispatchSource.makeTimerSource(queue: queue)
            timer.schedule(deadline: .now() + .milliseconds(20), repeating: .milliseconds(20), leeway: .milliseconds(1))
            timer.setEventHandler { [weak self, weak delivery] in
                guard self?.isCurrentInkIngress(generation) == true else { return }
                delivery?.tick()
            }
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
        // Invalidate admissions immediately, even if the link queue is stalled.
        resetInkIngress()
        queue.async { [self] in
            stopReliableInk()
            usesReliableInk = false
            authenticatedEpoch = 0
            authenticatedToken = Data()
            refreshingSession = false
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
        let generation = inkIngress.withLock { $0.generation }
        let released = reliableInk?.acknowledge(bytes) ?? 0
        inkIngress.withLock { state in
            guard state.generation == generation, !state.failed else { return }
            state.outstanding -= min(state.outstanding, released)
        }
    }

    private func stopReliableInk() {
        resetInkIngress()
        inkRetryTimer?.cancel()
        inkRetryTimer = nil
        reliableInk?.stop()
        reliableInk = nil
    }

    @discardableResult
    private func resetInkIngress(reliable: Bool? = nil, session: UInt32 = 0, epoch: UInt32 = 0) -> UInt64 {
        inkIngress.withLock { state in
            state.generation &+= 1
            if let reliable { state.reliable = reliable }
            state.session = session
            state.epoch = epoch
            state.outstanding = 0
            state.failed = false
            return state.generation
        }
    }

    private func isCurrentInkIngress(_ generation: UInt64) -> Bool {
        inkIngress.withLock { $0.generation == generation && $0.session != 0 && !$0.failed }
    }

    /// Always called on the link queue, once per failed admission generation.
    private func reportInkFailure(generation: UInt64, message: String) {
        guard inkIngress.withLock({ $0.reliable && $0.generation == generation }) else { return }
        stopReliableInk()
        onInkFailure?(message)
    }

    func sendInk(_ commands: [DeskInkCommand], epoch: UInt32) {
        guard !commands.isEmpty else { return }
        let submittedUs = nowUs()
        let admission: InkAdmission = inkIngress.withLock { state in
            guard state.reliable else { return .legacy }
            guard state.session != 0, state.epoch == epoch, !state.failed else { return .discard }
            guard commands.count <= ReliableInkSender.maxPending - state.outstanding else {
                state.failed = true
                return .overflow(state.generation)
            }
            state.outstanding += commands.count
            return .accepted(state.generation)
        }
        switch admission {
        case .discard:
            return
        case .overflow(let generation):
            queue.async { [self] in
                reportInkFailure(generation: generation, message: ReliableInkSender.failureMessage)
            }
            return
        case .accepted(let generation):
            queue.async { [self] in
                guard isCurrentInkIngress(generation),
                      reliableInk?.accepts(session: sessionId, epoch: epoch) == true else { return }
                reliableInk?.send(commands, enqueuedUs: submittedUs)
            }
            return
        case .legacy:
            break
        }
        queue.async { [self] in
            guard !usesReliableInk, sessionId != 0, hostCaps & 0x1000 != 0 else { return }
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
