import SwiftUI
import UIKit

/// Wire commands are value types. Coalesced samples cross to the connection's
/// serial queue as a batch; UIKit drawing never waits for video or a PC reply.
struct DeskInkPoint: Encodable {
    let x: CGFloat
    let y: CGFloat
    let pressure: CGFloat
}

struct DeskInkBrush: Encodable {
    var color: [UInt8] = [255, 80, 40, 255]
    var width: CGFloat = 4

    var uiColor: UIColor {
        UIColor(red: CGFloat(color[0]) / 255, green: CGFloat(color[1]) / 255,
                blue: CGFloat(color[2]) / 255, alpha: CGFloat(color[3]) / 255)
    }
}

enum DeskInkCommand: Encodable {
    case begin(id: UInt64, brush: DeskInkBrush, point: DeskInkPoint)
    case append(id: UInt64, point: DeskInkPoint)
    case end(id: UInt64)

    private enum CodingKeys: String, CodingKey { case type, id, brush, point }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case let .begin(id, brush, point):
            try container.encode("begin", forKey: .type)
            try container.encode(id, forKey: .id)
            try container.encode(brush, forKey: .brush)
            try container.encode(point, forKey: .point)
        case let .append(id, point):
            try container.encode("append", forKey: .type)
            try container.encode(id, forKey: .id)
            try container.encode(point, forKey: .point)
        case let .end(id):
            try container.encode("end", forKey: .type)
            try container.encode(id, forKey: .id)
        }
    }
}

struct DeskInkEnvelope: Encodable {
    let version = 1
    let session: UInt32
    let epoch: UInt32
    let seq: UInt64
    let command: DeskInkCommand
}

struct DeskInkCanvas: UIViewRepresentable {
    let videoSize: CGSize
    let enabled: Bool
    let connectionKey: String
    let send: ([DeskInkCommand]) -> Void

    func makeUIView(context: Context) -> InkCanvasView { InkCanvasView() }
    func updateUIView(_ view: InkCanvasView, context: Context) {
        view.send = send
        view.configure(videoSize: videoSize, enabled: enabled, connectionKey: connectionKey)
    }
}

/// Fast input/rendering stays inside this UIKit view. SwiftUI does not observe
/// every Pencil point. The video is a separate Metal layer beneath the canvas.
final class InkCanvasView: UIView {
    var send: ([DeskInkCommand]) -> Void = { _ in }
    private var videoSize = CGSize.zero
    private var enabled = false
    private var connectionKey = ""
    private var activeTouch: UITouch?
    private var activeID: UInt64?
    private var activePath: CGMutablePath?
    private var activeLayer: CAShapeLayer?
    private var lastTimestamp: TimeInterval = 0
    private let inkLayer = CALayer()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false
        isMultipleTouchEnabled = true
        layer.addSublayer(inkLayer)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unsupported") }

    func configure(videoSize: CGSize, enabled: Bool, connectionKey: String) {
        if self.connectionKey != connectionKey || self.videoSize != videoSize {
            // The send closure already belongs to the new binding. Never send
            // the previous connection's final command into that new session.
            finish(sendEnd: false)
            self.connectionKey = connectionKey
            self.videoSize = videoSize
            setNeedsLayout()
        }
        if self.enabled && !enabled { finish() }
        self.enabled = enabled
    }

    private var imageRect: CGRect {
        guard videoSize.width > 0, videoSize.height > 0 else { return .zero }
        let scale = min(bounds.width / videoSize.width, bounds.height / videoSize.height)
        let size = CGSize(width: videoSize.width * scale, height: videoSize.height * scale)
        return CGRect(x: (bounds.width - size.width) / 2, y: (bounds.height - size.height) / 2,
                      width: size.width, height: size.height)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let rect = imageRect
        guard rect.width > 0 else { return }
        CATransaction.begin(); CATransaction.setDisableActions(true)
        inkLayer.anchorPoint = .zero
        inkLayer.bounds = CGRect(origin: .zero, size: videoSize)
        inkLayer.position = rect.origin
        inkLayer.setAffineTransform(CGAffineTransform(scaleX: rect.width / videoSize.width, y: rect.height / videoSize.height))
        CATransaction.commit()
    }

    private func reading(_ touch: UITouch) -> DeskInkPoint? {
        let rect = imageRect
        guard rect.width > 0, rect.height > 0 else { return nil }
        let p = touch.preciseLocation(in: self)
        let pressure = touch.maximumPossibleForce > 0 ? touch.force / touch.maximumPossibleForce : 0.5
        return DeskInkPoint(x: min(videoSize.width, max(0, (p.x - rect.minX) * videoSize.width / rect.width)),
                            y: min(videoSize.height, max(0, (p.y - rect.minY) * videoSize.height / rect.height)),
                            pressure: min(1, max(0, pressure)))
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard enabled, activeTouch == nil,
              let touch = touches.first(where: { $0.type == .pencil }) ?? touches.first,
              imageRect.contains(touch.preciseLocation(in: self)), let point = reading(touch) else { return }
        let id = UInt64.random(in: 1...UInt64.max)
        activeTouch = touch; activeID = id; lastTimestamp = touch.timestamp
        let path = CGMutablePath()
        path.move(to: CGPoint(x: point.x, y: point.y))
        // A zero-length round-capped segment makes a tap visible on both sides.
        path.addLine(to: CGPoint(x: point.x + 0.001, y: point.y))
        let line = CAShapeLayer()
        let brush = DeskInkBrush()
        line.strokeColor = brush.uiColor.cgColor
        line.fillColor = UIColor.clear.cgColor
        line.lineWidth = brush.width; line.lineCap = .round; line.lineJoin = .round
        line.path = path
        inkLayer.addSublayer(line)
        activePath = path; activeLayer = line
        send([.begin(id: id, brush: brush, point: point)])
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = activeTouch, touches.contains(touch), let id = activeID, let path = activePath else { return }
        var commands: [DeskInkCommand] = []
        for sample in event?.coalescedTouches(for: touch) ?? [touch] {
            guard sample.timestamp > lastTimestamp, let point = reading(sample) else { continue }
            lastTimestamp = sample.timestamp
            path.addLine(to: CGPoint(x: point.x, y: point.y))
            commands.append(.append(id: id, point: point))
        }
        CATransaction.begin(); CATransaction.setDisableActions(true)
        activeLayer?.path = path
        CATransaction.commit()
        if !commands.isEmpty { send(commands) }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = activeTouch, touches.contains(touch) else { return }
        touchesMoved(touches, with: event)
        finish()
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = activeTouch, touches.contains(touch) else { return }
        finish()
    }
    private func finish(sendEnd: Bool = true) {
        if sendEnd, let id = activeID { send([.end(id: id)]) }
        activeTouch = nil; activeID = nil; activePath = nil; activeLayer = nil
    }
}
