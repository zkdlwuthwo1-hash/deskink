import SwiftUI
import UIKit

/// One native zoom surface for both video and local ink. SwiftUI's toolbar stays
/// outside this view, and zoom/pan never sends commands to the Windows host.
struct DeskInkViewport: UIViewRepresentable {
    let frameSlot: FrameSlot
    let videoSize: CGSize
    let enabled: Bool
    let connectionKey: String
    let tools: DeskInkTools
    let send: ([DeskInkCommand]) -> Void
    var document: DeskInkDocument? = nil

    func makeCoordinator() -> MetalView.Coordinator { MetalView.Coordinator() }
    func makeUIView(context: Context) -> DeskInkViewportView {
        let video = MetalView.makeRenderingView(frameSlot: frameSlot, coordinator: context.coordinator)
        return DeskInkViewportView(videoView: video)
    }
    func updateUIView(_ view: DeskInkViewportView, context: Context) {
        view.configure(videoSize: videoSize, enabled: enabled, connectionKey: connectionKey, tools: tools, send: send)
        if let document { view.canvas.useDocument(document) }
    }
    static func dismantleUIView(_ view: DeskInkViewportView, coordinator: MetalView.Coordinator) {
        view.canvas.finish()
        coordinator.detach()
    }
}

final class DeskInkViewportView: UIScrollView, UIScrollViewDelegate {
    let canvas = InkCanvasView()
    private let content = UIView()
    private let videoView: UIView
    private var layoutSize = CGSize.zero
    private var videoSize = CGSize.zero
    private var connectionKey = ""

    init(videoView: UIView) {
        self.videoView = videoView
        super.init(frame: .zero)
        delegate = self
        minimumZoomScale = 1; maximumZoomScale = 4
        bounces = false; bouncesZoom = false
        showsHorizontalScrollIndicator = false; showsVerticalScrollIndicator = false
        contentInsetAdjustmentBehavior = .never
        delaysContentTouches = false
        decelerationRate = .fast
        backgroundColor = .black
        accessibilityIdentifier = "deskink.viewport"
        accessibilityLabel = "발표 화면, 두 손가락으로 확대와 이동"
        accessibilityValue = "100%"
        addSubview(content)
        videoView.isUserInteractionEnabled = false
        content.addSubview(videoView); content.addSubview(canvas)
        let fingers = [NSNumber(value: UITouch.TouchType.direct.rawValue)]
        panGestureRecognizer.minimumNumberOfTouches = 2
        panGestureRecognizer.maximumNumberOfTouches = 2
        panGestureRecognizer.allowedTouchTypes = fingers
        pinchGestureRecognizer?.allowedTouchTypes = fingers
        canvas.onPencilActive = { [weak self] active in
            guard let self else { return }
            if active { self.setContentOffset(self.contentOffset, animated: false) }
            self.panGestureRecognizer.isEnabled = !active
            self.pinchGestureRecognizer?.isEnabled = !active
        }
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unsupported") }

    func configure(videoSize: CGSize, enabled: Bool, connectionKey: String, tools: DeskInkTools,
                   send: @escaping ([DeskInkCommand]) -> Void) {
        canvas.send = send; canvas.tools = tools
        tools.canvas = canvas; tools.viewport = self
        canvas.configure(videoSize: videoSize, enabled: enabled, connectionKey: connectionKey)
        if self.videoSize != videoSize || self.connectionKey != connectionKey {
            self.videoSize = videoSize; self.connectionKey = connectionKey
            resetZoom()
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.width > 0, bounds.height > 0, layoutSize != bounds.size else { return }
        layoutSize = bounds.size
        canvas.finish()
        setZoomScale(1, animated: false)
        content.frame = CGRect(origin: .zero, size: layoutSize)
        videoView.frame = content.bounds; canvas.frame = content.bounds
        contentSize = layoutSize
        setContentOffset(.zero, animated: false)
        canvas.setNeedsLayout(); canvas.layoutIfNeeded()
    }

    func viewForZooming(in scrollView: UIScrollView) -> UIView? { content }
    func scrollViewDidZoom(_ scrollView: UIScrollView) {
        accessibilityValue = "\(Int((zoomScale * 100).rounded()))%"
    }
    func resetZoom() {
        canvas.finish()
        setZoomScale(1, animated: false)
        setContentOffset(.zero, animated: false)
    }
}
