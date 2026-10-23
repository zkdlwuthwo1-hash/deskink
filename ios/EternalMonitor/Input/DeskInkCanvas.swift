import UIKit

/// UIKit owns the hot input path; SwiftUI observes tool choices and history only.
final class InkCanvasView: UIView, UIPencilInteractionDelegate {
    var send: ([DeskInkCommand]) -> Void = { _ in }
    var onPencilActive: (Bool) -> Void = { _ in }
    weak var tools: DeskInkTools?
    private(set) var document = DeskInkDocument()
    private var videoSize = CGSize.zero
    private var enabled = false
    private var connectionKey = ""
    private var activeTouch: UITouch?
    private var activeID: UInt64?
    private var activeTool: DeskInkTool?
    private var lastTimestamp: TimeInterval = 0
    private var lastPointerAt: TimeInterval = 0
    private let inkLayer = CALayer()
    private let pointerLayer = CAShapeLayer()
    private let selectionLayer = CAShapeLayer()
    private let handlesLayer = CAShapeLayer()
    private var selection: DeskInkSelection?
    private var holdTimer: Timer?
    private var holdAnchor: DeskInkPoint?
    private var shapeFrozen = false
    private var expiryTimer: Timer?
    private final class RenderedStroke {
        let layer = CAShapeLayer()
        var path = CGMutablePath()
        var count = 0
        var geometryRevision = 0
    }
    private var rendered: [UInt64: RenderedStroke] = [:]

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear; isOpaque = false; isMultipleTouchEnabled = true
        layer.addSublayer(inkLayer); layer.addSublayer(pointerLayer)
        layer.addSublayer(selectionLayer); layer.addSublayer(handlesLayer)
        selectionLayer.strokeColor=UIColor.systemBlue.cgColor; selectionLayer.fillColor=UIColor.clear.cgColor
        handlesLayer.strokeColor=UIColor.systemBlue.cgColor; handlesLayer.fillColor=UIColor.white.cgColor
        let pencil = UIPencilInteraction(); pencil.delegate = self; addInteraction(pencil)
        let hover = UIHoverGestureRecognizer(target: self, action: #selector(hovered(_:)))
        hover.allowedTouchTypes = [NSNumber(value: UITouch.TouchType.pencil.rawValue)]
        addGestureRecognizer(hover)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unsupported") }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        expiryTimer?.invalidate(); expiryTimer = nil
        if window != nil {
            let timer = Timer(timeInterval: 0.05, repeats: true) { [weak self] _ in
                guard let self else { return }
                if self.document.advanceTime(Self.nowMs) { self.synchronizeLayers() }
            }
            RunLoop.main.add(timer, forMode: .common); expiryTimer = timer
        } else { finish() }
    }
    private static var nowMs: UInt64 { UInt64(ProcessInfo.processInfo.systemUptime * 1000) }

    func useDocument(_ document: DeskInkDocument) {
        guard self.document !== document else { return }
        finish(sendEnd: false)
        self.document.suspend()
        self.document = document
        resetSelection()
        // Remove old layers even if two restored boards contain the same ID.
        for stroke in rendered.values { stroke.layer.removeFromSuperlayer() }
        rendered.removeAll()
        synchronizeLayers()
        updateControls()
    }

    func configure(videoSize: CGSize, enabled: Bool, connectionKey: String) {
        if self.connectionKey != connectionKey || self.videoSize != videoSize {
            finish(sendEnd: false)
            self.connectionKey = connectionKey; self.videoSize = videoSize
            resetSelection()
            setNeedsLayout()
        }
        if self.enabled && !enabled { finish() }
        self.enabled = enabled
    }
    private var imageRect: CGRect {
        guard videoSize.width > 0, videoSize.height > 0 else { return .zero }
        let scale = min(bounds.width / videoSize.width, bounds.height / videoSize.height)
        let size = CGSize(width: videoSize.width * scale, height: videoSize.height * scale)
        return CGRect(x: (bounds.width-size.width)/2, y: (bounds.height-size.height)/2, width: size.width, height: size.height)
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        let rect = imageRect
        guard rect.width > 0 else { return }
        CATransaction.begin(); CATransaction.setDisableActions(true)
        for canvasLayer in [inkLayer, pointerLayer, selectionLayer, handlesLayer] {
            canvasLayer.anchorPoint = .zero
            canvasLayer.bounds = CGRect(origin: .zero, size: videoSize)
            canvasLayer.position = rect.origin
            canvasLayer.setAffineTransform(CGAffineTransform(scaleX: rect.width/videoSize.width, y: rect.height/videoSize.height))
        }
        CATransaction.commit()
        synchronizeSelection()
    }
    private var inputScale: CGFloat { videoSize.width>0 ? max(0.001,imageRect.width/videoSize.width):1 }
    private func resetSelection() {
        selection=DeskInkSelection(document:document,size:videoSize) { [weak self] command,transmit in self?.submit([command],transmit:transmit) }
    }
    func clearSelection() { selection?.clear(); synchronizeSelection() }
    private func reading(location: CGPoint, pressure: CGFloat) -> DeskInkPoint? {
        let rect = imageRect
        guard rect.width > 0, rect.height > 0 else { return nil }
        return DeskInkPoint(x: min(videoSize.width, max(0, (location.x-rect.minX)*videoSize.width/rect.width)),
                            y: min(videoSize.height, max(0, (location.y-rect.minY)*videoSize.height/rect.height)), pressure: min(1,max(0,pressure)))
    }
    private func reading(_ touch: UITouch) -> DeskInkPoint? {
        reading(location: touch.preciseLocation(in: self), pressure: touch.maximumPossibleForce > 0 ? touch.force/touch.maximumPossibleForce : 0.5)
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard enabled, activeTouch == nil, let tools,
              let touch = touches.first(where: { $0.type == .pencil }) ?? (tools.fingerDrawing ? touches.first(where: { $0.type == .direct }) : nil) else { return }
        if touch.type == .direct, (event?.allTouches?.filter { $0.type == .direct && $0.phase != .ended }.count ?? 0) > 1 { return }
        if touch.type == .pencil { onPencilActive(true) }
        let pressure = touch.maximumPossibleForce > 0 ? touch.force/touch.maximumPossibleForce : 0.5
        guard beginStroke(at: touch.preciseLocation(in: self), pressure: pressure) else {
            if touch.type == .pencil { onPencilActive(false) }
            return
        }
        activeTouch = touch; lastTimestamp = touch.timestamp
    }

    /// UIKit has already converted the Pencil location through the scroll view's
    /// zoom transform. This is also the input seam used by viewport tests.
    @discardableResult
    func beginStroke(at location: CGPoint, pressure: CGFloat) -> Bool {
        guard enabled, activeID == nil, let tools, imageRect.contains(location),
              let point = reading(location: location, pressure: pressure) else { return false }
        if !document.visible { submit([.setVisible(true)]); updateControls() }
        let id = UInt64.random(in: 1...UInt64.max)
        activeID = id; activeTool = tools.tool
        shapeFrozen=false
        var commands: [DeskInkCommand] = []
        switch tools.tool {
        case .lasso:
            guard tools.editingEnabled else { activeID=nil; activeTool=nil; return false }
            selection?.begin(point,scale:inputScale,id:id); synchronizeSelection(); return true
        case .eraser: commands.append(.eraseBegin(id: id, point: point, radius: tools.eraserSize.radius))
        case .laser: break
        case .vanishing: commands.append(.transientBegin(id: id, brush: tools.brush, point: point, lifetimeMs: 2000))
        case .pen, .highlighter: commands.append(.begin(id: id, brush: tools.brush, point: point))
        }
        commands.append(pointerCommand(point)); submit(commands)
        scheduleCorrection(at:point)
        return true
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = activeTouch, touches.contains(touch), activeID != nil else { return }
        var commands: [DeskInkCommand] = []; var lastPoint: DeskInkPoint?
        for sample in event?.coalescedTouches(for: touch) ?? [touch] {
            guard sample.timestamp > lastTimestamp, let point = reading(sample) else { continue }
            lastTimestamp = sample.timestamp; lastPoint = point
            appendMovement(point,to:&commands)
        }
        if activeTool != .lasso, let point = lastPoint, ProcessInfo.processInfo.systemUptime-lastPointerAt >= 1.0/60 {
            commands.append(pointerCommand(point)); lastPointerAt = ProcessInfo.processInfo.systemUptime
        }
        submit(commands)
    }
    /// The same movement path is used by coalesced UIKit samples and input tests.
    func moveStroke(to location: CGPoint,pressure: CGFloat) {
        guard activeID != nil, let point=reading(location:location,pressure:pressure) else { return }
        var commands: [DeskInkCommand]=[]
        appendMovement(point,to:&commands); submit(commands)
    }
    private func appendMovement(_ point: DeskInkPoint,to commands: inout [DeskInkCommand]) {
        guard let id=activeID else { return }
        switch activeTool {
        case .lasso: selection?.move(point,now:Self.nowMs); synchronizeSelection()
        case .eraser: commands.append(.eraseTo(id:id,point:point))
        case .laser,.none: break
        default:
            guard !shapeFrozen else { return }
            commands.append(.append(id:id,point:point))
            if let anchor=holdAnchor, hypot(point.x-anchor.x,point.y-anchor.y)<=3/inputScale { return }
            scheduleCorrection(at:point)
        }
    }
    private func scheduleCorrection(at point: DeskInkPoint) {
        holdTimer?.invalidate(); holdTimer=nil; holdAnchor=point
        guard tools?.editingEnabled==true, tools?.shapeCorrection==true, activeTool == .pen || activeTool == .highlighter else { return }
        let timer=Timer(timeInterval:1,repeats:false) { [weak self] _ in
            guard let self, let id=self.activeID, self.tools?.editingEnabled==true, self.tools?.shapeCorrection==true,
                  let stroke=self.document.strokes.first(where:{ $0.id==id }), let shape=DeskInkShape.recognize(stroke.points) else { return }
            self.shapeFrozen=true
            self.submit([.shape(id:id,kind:shape.kind,a:shape.a,b:shape.b)])
        }
        RunLoop.main.add(timer,forMode:.common); holdTimer=timer
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = activeTouch, touches.contains(touch) else { return }
        touchesMoved(touches, with: event); finish()
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = activeTouch, touches.contains(touch) else { return }; finish()
    }
    func finish(sendEnd: Bool = true) {
        holdTimer?.invalidate(); holdTimer=nil; holdAnchor=nil
        guard let id = activeID else { return }
        let wasErasing = activeTool == .eraser
        if activeTool == .lasso { selection?.end(transmit:sendEnd); synchronizeSelection() }
        let command: DeskInkCommand? = activeTool == .laser || activeTool == .lasso ? nil : activeTool == .eraser ? .eraseEnd(id: id) : .end(id: id)
        activeTouch = nil; activeID = nil; activeTool = nil
        if let command { submit([command], transmit: sendEnd) }
        if wasErasing {
            if sendEnd {
                tools?.eraserGestureDidEnd()
                submit([pointerCommand(document.pointer?.point)])
            } else {
                // A connection binding can finish an interrupted gesture inside
                // updateUIView; defer the observable tool selection in that case.
                DispatchQueue.main.async { [weak tools] in tools?.eraserGestureDidEnd() }
            }
        }
        onPencilActive(false)
        updateControls()
    }
    func perform(_ command: DeskInkCommand) {
        guard enabled else { return }; finish(); clearSelection(); submit([command]); updateControls()
    }
    private func submit(_ commands: [DeskInkCommand], transmit: Bool = true) {
        guard !commands.isEmpty else { return }
        document.advanceTime(Self.nowMs)
        let accepted = commands.filter { document.apply($0) }
        synchronizeLayers()
        if transmit, !accepted.isEmpty { send(accepted) }
    }
    private func updateControls() {
        let undo = document.canUndo, redo = document.canRedo, visible = document.visible
        // A binding change can finish a stroke inside updateUIView.
        DispatchQueue.main.async { [weak self] in
            self?.tools?.canUndo = undo; self?.tools?.canRedo = redo; self?.tools?.visible = visible
        }
    }
    private func pointerCommand(_ point: DeskInkPoint?) -> DeskInkCommand {
        let tool = activeTool ?? tools?.tool ?? .pen
        return .pointer(point: point, color: tool == .eraser ? [235,240,250,255] : tools?.color.rgba ?? [255,80,40,255],
                        radius: tool == .eraser ? (tools?.eraserSize.radius ?? 12) : 7, laser: tool == .laser || tool == .eraser)
    }
    @objc private func hovered(_ gesture: UIHoverGestureRecognizer) {
        guard enabled, activeTouch == nil else { return }
        if gesture.state == .ended || gesture.state == .cancelled { submit([pointerCommand(nil)]); return }
        let location = gesture.location(in: self)
        guard imageRect.contains(location), let point = reading(location: location, pressure: 0) else { return }
        submit([pointerCommand(point)])
    }
    func pencilInteractionDidTap(_ interaction: UIPencilInteraction) { tools?.toggleEraser() }
    @available(iOS 17.5, *)
    func pencilInteraction(_ interaction: UIPencilInteraction, didReceiveTap tap: UIPencilInteraction.Tap) { tools?.toggleEraser() }

    private func synchronizeLayers() {
        CATransaction.begin(); CATransaction.setDisableActions(true)
        let strokes = document.renderStrokes, ids = Set(strokes.map(\.id))
        for id in Array(rendered.keys) where !ids.contains(id) { rendered.removeValue(forKey: id)?.layer.removeFromSuperlayer() }
        for (index, stroke) in strokes.enumerated() {
            let entry: RenderedStroke
            if let existing = rendered[stroke.id] { entry = existing }
            else {
                entry = RenderedStroke(); rendered[stroke.id] = entry
                entry.layer.strokeColor = stroke.brush.uiColor.cgColor; entry.layer.fillColor = UIColor.clear.cgColor
                entry.layer.lineWidth = stroke.brush.width; entry.layer.lineCap = .round; entry.layer.lineJoin = .round
                inkLayer.insertSublayer(entry.layer, at: UInt32(index))
            }
            if entry.geometryRevision != stroke.geometryRevision {
                entry.path = CGMutablePath(); entry.count = 0; entry.geometryRevision = stroke.geometryRevision
            }
            if entry.count < stroke.points.count {
                for point in stroke.points.dropFirst(entry.count) {
                    if entry.count == 0 {
                        entry.path.move(to: CGPoint(x:point.x,y:point.y)); entry.path.addLine(to: CGPoint(x:point.x+0.001,y:point.y))
                    } else { entry.path.addLine(to: CGPoint(x:point.x,y:point.y)) }
                    entry.count += 1
                }
                entry.layer.path = entry.path
            }
        }
        if let pointer = document.pointer {
            let path = CGMutablePath(), x = pointer.point.x, y = pointer.point.y
            if pointer.laser { path.addEllipse(in: CGRect(x:x-pointer.radius,y:y-pointer.radius,width:pointer.radius*2,height:pointer.radius*2)) }
            else { path.move(to: CGPoint(x:x,y:y)); path.addLine(to: CGPoint(x:x+7,y:y-20)); path.addLine(to: CGPoint(x:x+15,y:y-12)); path.closeSubpath() }
            pointerLayer.path = path
            pointerLayer.fillColor = DeskInkBrush(color:pointer.color).uiColor.withAlphaComponent(0.92).cgColor
            pointerLayer.strokeColor = UIColor.white.cgColor; pointerLayer.lineWidth = 2
        } else { pointerLayer.path = nil }
        CATransaction.commit()
        synchronizeSelection()
    }
    private func synchronizeSelection() {
        CATransaction.begin(); CATransaction.setDisableActions(true)
        defer { CATransaction.commit() }
        selectionLayer.path=nil; handlesLayer.path=nil
        guard document.visible, tools?.tool == .lasso, tools?.editingEnabled==true, let selection else { return }
        let path=CGMutablePath(), handles=CGMutablePath(), radius=5/inputScale
        if let bounds=selection.bounds {
            path.addRect(bounds)
            for handle in DeskInkSelection.Handle.allCases {
                let p=handle.position(in:bounds)
                handles.addEllipse(in:CGRect(x:p.x-radius,y:p.y-radius,width:radius*2,height:radius*2))
            }
        }
        for (index,p) in selection.lasso.enumerated() {
            if index==0 { path.move(to:CGPoint(x:p.x,y:p.y)) } else { path.addLine(to:CGPoint(x:p.x,y:p.y)) }
        }
        selectionLayer.lineWidth=1.5/inputScale; selectionLayer.lineDashPattern=[NSNumber(value:Double(6/inputScale)),NSNumber(value:Double(4/inputScale))]
        handlesLayer.lineWidth=1.5/inputScale; selectionLayer.path=path; handlesLayer.path=handles
    }
}
