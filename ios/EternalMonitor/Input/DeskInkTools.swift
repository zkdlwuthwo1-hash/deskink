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

enum DeskInkEraserMode: String, CaseIterable, Identifiable {
    case stroke, partial
    var id: String { rawValue }
    var title: String { self == .stroke ? "획 전체 지우기":"닿은 부분만 지우기" }
    var icon: String { self == .stroke ? "line.diagonal":"eraser" }
}

@MainActor @Observable
final class DeskInkTools {
    var tool: DeskInkTool = .pen
    var color: DeskInkColor = .orange
    var width: CGFloat = 4
    var erasingEnabled = false
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
    var eraserMode: DeskInkEraserMode {
        didSet { defaults.set(eraserMode.rawValue, forKey:"deskink.eraser-mode") }
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
        eraserMode = DeskInkEraserMode(rawValue: defaults.string(forKey:"deskink.eraser-mode") ?? "") ?? .stroke
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
    @State private var popover: ToolPopover?
    private enum ToolPopover { case eraser, colors }

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) {
                ForEach(DeskInkTool.allCases) { tool in
                    if tool == .eraser {
                        toolButton(tool)
                            .popover(isPresented: presenting(.eraser), arrowEdge: .bottom) {
                                DeskInkEraserPicker(tools: tools)
                                    .presentationCompactAdaptation(.popover)
                            }
                    } else {
                        toolButton(tool)
                    }
                }
                Button { tools.shapeCorrection.toggle() } label: { item("도형 보정", "square.on.circle") }
                    .foregroundStyle(tools.shapeCorrection && tools.editingEnabled ? Color.blue : Color.primary)
                    .background(tools.shapeCorrection && tools.editingEnabled ? Color.blue.opacity(0.14) : .clear, in: RoundedRectangle(cornerRadius:10))
                    .disabled(!tools.editingEnabled)
                    .accessibilityLabel("도형 보정")
                    .accessibilityValue(tools.editingEnabled ? (tools.shapeCorrection ? "켜짐" : "꺼짐") : "Windows 업데이트 필요")
                    .accessibilityHint("켜면 도형을 한 획으로 그린 뒤 펜을 떼지 않고 0.5초 멈추면 보정합니다")
                    .accessibilityAddTraits(tools.shapeCorrection && tools.editingEnabled ? .isSelected : [])
                    .accessibilityIdentifier("deskink.shapeCorrection")
                Divider().frame(height: 32)
                Button { popover = .colors } label: {
                    VStack(spacing: 4) {
                        Circle().fill(tools.color.color).overlay(Circle().stroke(.gray, lineWidth: 1)).frame(width: 23, height: 23)
                        Text("색상").font(.system(size: 10))
                    }.frame(width: 46, height: 50)
                }
                .accessibilityLabel("색상: \(tools.color.title)")
                .accessibilityIdentifier("deskink.colors")
                .popover(isPresented: presenting(.colors), arrowEdge: .bottom) {
                    DeskInkColorPicker(tools: tools)
                        .presentationCompactAdaptation(.popover)
                }
                Menu {
                    Picker("펜 굵기", selection: $tools.width) {
                        Text("가는 선").tag(CGFloat(2)); Text("보통").tag(CGFloat(4)); Text("굵은 선").tag(CGFloat(8))
                    }
                } label: { item("굵기", "lineweight") }.accessibilityLabel("펜 굵기")
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
    private func presenting(_ panel: ToolPopover) -> Binding<Bool> {
        Binding(get: { popover == panel }, set: { visible in
            if visible { popover = panel } else if popover == panel { popover = nil }
        })
    }
    private func toolButton(_ tool: DeskInkTool) -> some View {
        Button {
            tools.select(tool)
            if tool == .eraser { popover = .eraser }
        } label: { item(tool.title, tool.icon) }
            .foregroundStyle(tools.tool == tool ? Color.blue : Color.primary)
            .background(tools.tool == tool ? Color.blue.opacity(0.14) : .clear, in: RoundedRectangle(cornerRadius: 10))
            .accessibilityLabel(tool.title)
            .accessibilityValue(tool == .eraser ? tools.eraserSize.title : "")
            .accessibilityAddTraits(tools.tool == tool ? .isSelected : [])
            .disabled(tool == .lasso && !tools.editingEnabled)
    }
    private func item(_ title: String, _ symbol: String) -> some View {
        VStack(spacing: 4) { Image(systemName: symbol).symbolRenderingMode(.monochrome).font(.system(size: 21)); Text(title).font(.system(size: 10)).lineLimit(1) }
            .frame(width: title == "사라지는 펜" ? 65 : 46, height: 50).contentShape(Rectangle())
    }
}

private struct DeskInkEraserPicker: View {
    @Bindable var tools: DeskInkTools
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("지우는 방식").font(.headline)
            ForEach(DeskInkEraserMode.allCases) { mode in
                Button { tools.eraserMode=mode } label: {
                    HStack {
                        Label(mode.title,systemImage:mode.icon)
                        Spacer()
                        Image(systemName:"checkmark").opacity(tools.eraserMode==mode ? 1:0)
                    }.padding(12)
                        .frame(minHeight:44)
                        .background(tools.eraserMode==mode ? Color.blue.opacity(0.14):Color.clear,in:RoundedRectangle(cornerRadius:10))
                        .contentShape(Rectangle())
                }
                .disabled(mode == .partial && !tools.erasingEnabled)
                .foregroundStyle(tools.eraserMode==mode ? Color.blue:Color.primary)
                .accessibilityIdentifier("deskink.eraser.\(mode.rawValue)")
                .accessibilityAddTraits(tools.eraserMode==mode ? .isSelected:[])
            }
            if !tools.erasingEnabled {
                Text("부분 지우기·선택 삭제는 Windows DeskInk 0.6.4 이상이 필요합니다.").font(.caption).foregroundStyle(.secondary).fixedSize(horizontal:false,vertical:true)
            }
            Text("지우개 크기").font(.headline)
            HStack(spacing: 8) {
                ForEach(DeskInkEraserSize.allCases) { size in
                    Button {
                        tools.eraserSize = size
                        dismiss()
                    } label: {
                        VStack(spacing: 8) {
                            Circle().fill(Color.blue.opacity(0.14))
                                .overlay(Circle().stroke(Color.blue, lineWidth: 1.5))
                                .frame(width: size.radius / 2 + 10, height: size.radius / 2 + 10)
                                .frame(width: 48, height: 42)
                            Text(size.title).font(.subheadline.weight(.medium))
                        }
                        .frame(width: 76, height: 82)
                        .background(tools.eraserSize == size ? Color.blue.opacity(0.14) : Color.clear,
                                    in: RoundedRectangle(cornerRadius: 12))
                        .contentShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .foregroundStyle(tools.eraserSize == size ? Color.blue : Color.primary)
                    .accessibilityLabel("지우개 크기: \(size.title)")
                    .accessibilityAddTraits(tools.eraserSize == size ? .isSelected : [])
                }
            }
        }
        .padding(18)
        .buttonStyle(.plain)
    }
}

private struct DeskInkColorPicker: View {
    @Bindable var tools: DeskInkTools
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("색상").font(.headline)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 6) {
                ForEach(DeskInkColor.allCases) { color in
                    Button {
                        tools.color = color
                        dismiss()
                    } label: {
                        HStack(spacing: 9) {
                            Circle().fill(color.color)
                                .overlay(Circle().stroke(Color.gray, lineWidth: 1))
                                .frame(width: 24, height: 24)
                            Text(color.title).font(.subheadline)
                            Spacer(minLength: 0)
                            Image(systemName: "checkmark").font(.caption.weight(.bold))
                                .foregroundStyle(Color.blue)
                                .opacity(tools.color == color ? 1 : 0)
                        }
                        .padding(.horizontal, 10)
                        .frame(height: 44)
                        .background(tools.color == color ? Color.blue.opacity(0.12) : Color.clear,
                                    in: RoundedRectangle(cornerRadius: 10))
                        .contentShape(RoundedRectangle(cornerRadius: 10))
                    }
                    .foregroundStyle(Color.primary)
                    .accessibilityLabel(color.title)
                    .accessibilityAddTraits(tools.color == color ? .isSelected : [])
                }
            }
        }
        .frame(width: 280)
        .padding(18)
        .buttonStyle(.plain)
    }
}
