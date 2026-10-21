import Foundation

/// Ordered ink delivery over the existing UDP socket. All calls, including
/// callbacks and tick(), belong to the owning ControlChannel's serial queue.
/// Video never waits for ink acknowledgements, and healthy ink sends immediately.
final class ReliableInkSender {
    private struct Pending {
        let sequence: UInt64
        let bytes: Data
        let enqueuedUs: UInt64
    }
    private let nowUs: () -> UInt64
    private let sendPacket: (Data) -> Void
    private let failed: (String) -> Void
    private var binding: (UInt32, UInt32) = (0, 0)
    private var sequence: UInt64 = 0
    private var pending: [Pending] = []
    private var hasFailed = false

    init(nowUs: @escaping () -> UInt64, send: @escaping (Data) -> Void,
         failed: @escaping (String) -> Void) {
        self.nowUs = nowUs
        sendPacket = send
        self.failed = failed
    }

    func start(session: UInt32, epoch: UInt32) {
        guard binding != (session, epoch) else { return }
        if binding.0 == session, binding.1 != 0, Int32(bitPattern: epoch &- binding.1) <= 0 { return }
        stop()
        binding = (session, epoch)
    }

    func accepts(session: UInt32, epoch: UInt32) -> Bool {
        !hasFailed && binding == (session, epoch) && session != 0 && epoch != 0
    }

    func stop() {
        binding = (0, 0)
        sequence = 0
        pending.removeAll(keepingCapacity: true)
        hasFailed = false
    }

    func send(_ commands: [DeskInkCommand]) {
        guard binding.0 != 0, binding.1 != 0, !hasFailed else { return }
        guard !expired() else { fail(); return }
        let encoder = JSONEncoder()
        for command in commands {
            guard pending.count < 256, sequence < UInt64.max,
                  let body = try? encoder.encode(DeskInkEnvelope(session: binding.0, epoch: binding.1,
                      seq: sequence + 1, command: command)), body.count <= 1024 else { fail(); return }
            sequence += 1
            var packet = Data("DI02".utf8)
            packet.append(body)
            pending.append(Pending(sequence: sequence, bytes: packet, enqueuedUs: nowUs()))
            sendPacket(packet)
        }
    }

    func acknowledge(_ bytes: Data) {
        guard !hasFailed, binding.0 != 0, bytes.count == 20,
              bytes.prefix(4) == Data("DA02".utf8) else { return }
        guard !expired() else { fail(); return }
        let values = bytes.withUnsafeBytes { buffer in
            (UInt32(littleEndian: buffer.loadUnaligned(fromByteOffset: 4, as: UInt32.self)),
             UInt32(littleEndian: buffer.loadUnaligned(fromByteOffset: 8, as: UInt32.self)),
             UInt64(littleEndian: buffer.loadUnaligned(fromByteOffset: 12, as: UInt64.self)))
        }
        guard binding == (values.0, values.1), values.2 <= sequence else { return }
        pending.removeAll { $0.sequence <= values.2 }
    }

    func tick() {
        guard !hasFailed else { return }
        guard !expired() else { fail(); return }
        for packet in pending.prefix(16) { sendPacket(packet.bytes) }
    }

    private func expired() -> Bool {
        guard let oldest = pending.first else { return false }
        let now = nowUs()
        return now >= oldest.enqueuedUs && now - oldest.enqueuedUs >= 250_000
    }

    private func fail() {
        guard !hasFailed else { return }
        hasFailed = true
        pending.removeAll(keepingCapacity: true)
        failed("무선 필기 응답이 지연되어 연결을 중단했습니다. PC에 반영되지 않은 필기가 있을 수 있습니다. Wi-Fi 상태를 확인하고 다시 연결하거나 USB를 사용하세요.")
    }
}
