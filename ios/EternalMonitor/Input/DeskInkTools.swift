import SwiftUI
import Observation

enum DeskInkTool: String, CaseIterable, Identifiable {
    case pen, highlighter, eraser, laser, vanishing, lasso
    var id: String { rawValue }
    var title: String {
        switch self { case .pen: return "펜"; case .highlighter: return "형광펜"; case .eraser: return "지우개"; case .laser: return "레이저"; case .vanishing: return "사라지는 펜"; case .lasso: return "올가미" }
    }
    var icon: String {
        switch self { case .pen: return "pencil.tip"; case .highlighter: return "highlighter"; case .eraser: return "eraser"; case .laser: return "scope"; case .vanishing: return "pencil.tip.crop.circle.badge.minus"; case .lasso: return "lasso" }
    }
}

enum DeskInkColor: String, CaseIterable, Identifiable {
    case orange, red, yellow, green, blue, white, black, teal, purple, pink, gray, brown
    var id: String { rawValue }
    var title: String {
        switch self { case .orange: return "주황"; case .red: return "빨강"; case .yellow: return "노랑"; case .green: return "초록"; case .blue: return "파랑"; case .white: return "흰색"; case .black: return "검정"
        case .teal: return "청록"; case .purple: return "보라"; case .pink: return "분홍"; case .gray: return "회색"; case .brown: return "갈색" }
    }
    var rgba: [UInt8] {
        switch self {
        case .orange: return [255,80,40,255]; case .red: return [245,45,65,255]
        case .yellow: return [255,210,35,255]; case .green: return [25,190,100,255]
        case .blue: return [35,125,255,255]; case .white: return [255,255,255,255]; case .black: return [25,30,40,255]
        case .teal: return [20,190,200,255]; case .purple: return [155,90,235,255]; case .pink: return [245,120,180,255]
        case .gray: return [145,150,160,255]; case .brown: return [155,100,60,255]
        }
    }
    var color: Color { Color(uiColor: DeskInkBrush(color: rgba).uiColor) }
}

enum DeskInkEraserSize: String, CaseIterable, Identifiable {
    case small, medium, large
    var id: String { rawValue }
    var title: String { switch self { case .small: return "작게"; case .medium: return "보통"; case .large: return "크게" } }
    var radius: CGFloat { switch self { case .small: return 12; case .medium: return 24; case .large: return 48 } }
}

@MainActor @Observable
final class DeskInkTools {
    var tool: DeskInkTool = .pen
    var color: DeskInkColor = .orange
    var width: CGFloat = 4
    var editingEnabled = false {
        didSet {
            if !editingEnabled {
                canvas?.finish(sendEnd:false); canvas?.clearSelection()
                if tool == .lasso { tool = .pen }
            }
        }
    }
    var shapeCorrection: Bool {
        didSet { defaults.set(shapeCorrection, forKey:"deskink.shape-correction") }
    }
    var eraserSize: DeskInkEraserSize {
        didSet { defaults.set(eraserSize.rawValue, forKey: "deskink.eraser-size") }
    }
    @ObservationIgnored private let defaults: UserDefaults
    var fingerDrawing = false
    var visible = true
    var canUndo = false
    var canRedo = false
    @ObservationIgnored weak var canvas: InkCanvasView?
    @ObservationIgnored weak var viewport: DeskInkViewportView?
    @ObservationIgnored private var previousTool: DeskInkTool = .pen

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        eraserSize = DeskInkEraserSize(rawValue: defaults.string(forKey: "deskink.eraser-size") ?? "") ?? .small
        shapeCorrection = defaults.object(forKey:"deskink.shape-correction") as? Bool ?? true
    }

    var brush: DeskInkBrush {
        var rgba = color.rgba
        if tool == .highlighter { rgba[3] = 80 }
        return DeskInkBrush(color: rgba, width: tool == .highlighter ? width * 5 : width)
    }
    func select(_ tool: DeskInkTool) {
        guard tool != .lasso || editingEnabled else { return }
        canvas?.finish()
        canvas?.clearSelection()
        if tool == .eraser, self.tool != .eraser { previousTool = self.tool }
        self.tool = tool
    }
    func toggleEraser() {
        if tool == .eraser { select(previousTool) }
        else { select(.eraser) }
    }
    func eraserGestureDidEnd() {
        if tool == .eraser { tool = previousTool }
    }
    func perform(_ command: DeskInkCommand) { canvas?.perform(command) }
    func resetZoom() { viewport?.resetZoom() }
}

struct DeskInkToolbar: View {
    @Bindable var tools: DeskInkTools

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) {
                ForEach(DeskInkTool.allCases) { tool in
                    Button { tools.select(tool) } label: { item(tool.title, tool.icon) }
                        .foregroundStyle(tools.tool == tool ? Color.blue : Color.primary)
                        .background(tools.tool == tool ? Color.blue.opacity(0.14) : .clear, in: RoundedRectangle(cornerRadius: 10))
                        .accessibilityLabel(tool.title)
                        .accessibilityAddTraits(tools.tool == tool ? .isSelected : [])
                        .disabled(tool == .lasso && !tools.editingEnabled)
                }
                Button { tools.shapeCorrection.toggle() } label: { item("도형 보정", "square.on.circle") }
                    .foregroundStyle(tools.shapeCorrection && tools.editingEnabled ? Color.blue : Color.secondary)
                    .background(tools.shapeCorrection && tools.editingEnabled ? Color.blue.opacity(0.14) : .clear, in: RoundedRectangle(cornerRadius:10))
                    .disabled(!tools.editingEnabled)
                    .accessibilityLabel(tools.editingEnabled ? "도형 보정 \(tools.shapeCorrection ? "켜짐" : "꺼짐")" : "도형 편집은 Windows DeskInk 업데이트가 필요합니다")
                    .accessibilityAddTraits(tools.shapeCorrection ? .isSelected : [])
                Divider().frame(height: 32)
                Menu {
                    Picker("색상", selection: $tools.color) {
                        ForEach(DeskInkColor.allCases) { color in Text(color.title).tag(color) }
                    }
                } label: {
                    VStack(spacing: 4) {
                        Circle().fill(tools.color.color).overlay(Circle().stroke(.gray, lineWidth: 1)).frame(width: 23, height: 23)
                        Text("색상").font(.system(size: 10))
                    }.frame(width: 46, height: 50)
                }.accessibilityLabel("색상: \(tools.color.title)")
                Menu {
                    Picker("펜 굵기", selection: $tools.width) {
                        Text("가는 선").tag(CGFloat(2)); Text("보통").tag(CGFloat(4)); Text("굵은 선").tag(CGFloat(8))
                    }
                    Picker("지우개 크기", selection: $tools.eraserSize) {
                        ForEach(DeskInkEraserSize.allCases) { size in Text(size.title).tag(size) }
                    }
                    Toggle("손가락으로 쓰기", isOn: $tools.fingerDrawing)
                } label: { item("굵기", "lineweight") }.accessibilityLabel("펜 굵기, 지우개 크기와 손가락 필기 설정")
                Divider().frame(height: 32)
                Button { tools.perform(.undo) } label: { item("취소", "arrow.uturn.backward") }.disabled(!tools.canUndo).accessibilityLabel("실행 취소")
                Button { tools.perform(.redo) } label: { item("다시", "arrow.uturn.forward") }.disabled(!tools.canRedo).accessibilityLabel("다시 실행")
                Button { tools.perform(.setVisible(!tools.visible)) } label: { item(tools.visible ? "숨기기" : "보이기", tools.visible ? "eye" : "eye.slash") }.accessibilityLabel(tools.visible ? "필기 숨기기" : "필기 보이기")
                Button { tools.perform(.clear) } label: { item("지우기", "trash") }.accessibilityLabel("전체 지우기, 실행 취소로 복원 가능")
            }.padding(8)
        }
        .buttonStyle(.plain)
        .fixedSize(horizontal: false, vertical: true)
        .frame(maxWidth: 820)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18))
        .shadow(color: .black.opacity(0.16), radius: 12, y: 3)
    }
    private func item(_ title: String, _ symbol: String) -> some View {
        VStack(spacing: 4) { Image(systemName: symbol).font(.system(size: 21)); Text(title).font(.system(size: 10)).lineLimit(1) }
            .frame(width: title == "사라지는 펜" ? 65 : 46, height: 50).contentShape(Rectangle())
    }
}
