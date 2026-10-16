import Foundation
import CoreGraphics
import UIKit

struct DeskInkPoint: Codable, Equatable {
    let x: CGFloat
    let y: CGFloat
    let pressure: CGFloat
}

struct DeskInkBrush: Codable, Equatable {
    var color: [UInt8] = [255, 80, 40, 255]
    var width: CGFloat = 4
    var uiColor: UIColor {
        UIColor(red: CGFloat(color[0]) / 255, green: CGFloat(color[1]) / 255,
                blue: CGFloat(color[2]) / 255, alpha: CGFloat(color[3]) / 255)
    }
}

enum DeskInkCommand: Codable {
    case begin(id: UInt64, brush: DeskInkBrush, point: DeskInkPoint)
    case append(id: UInt64, point: DeskInkPoint)
    case end(id: UInt64)
    case eraseBegin(id: UInt64, point: DeskInkPoint, radius: CGFloat)
    case eraseTo(id: UInt64, point: DeskInkPoint)
    case eraseEnd(id: UInt64)
    case undo, redo, clear
    case setVisible(Bool)
    case transientBegin(id: UInt64, brush: DeskInkBrush, point: DeskInkPoint, lifetimeMs: UInt32)
    case pointer(point: DeskInkPoint?, color: [UInt8], radius: CGFloat, laser: Bool)

    private enum Keys: String, CodingKey {
        case type, id, brush, point, radius, visible, color, laser
        case lifetimeMs = "lifetime_ms"
    }
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: Keys.self)
        switch self {
        case let .begin(id, brush, point):
            try c.encode("begin", forKey: .type); try c.encode(id, forKey: .id)
            try c.encode(brush, forKey: .brush); try c.encode(point, forKey: .point)
        case let .append(id, point):
            try c.encode("append", forKey: .type); try c.encode(id, forKey: .id); try c.encode(point, forKey: .point)
        case let .end(id):
            try c.encode("end", forKey: .type); try c.encode(id, forKey: .id)
        case let .eraseBegin(id, point, radius):
            try c.encode("erase_begin", forKey: .type); try c.encode(id, forKey: .id)
            try c.encode(point, forKey: .point); try c.encode(radius, forKey: .radius)
        case let .eraseTo(id, point):
            try c.encode("erase_to", forKey: .type); try c.encode(id, forKey: .id); try c.encode(point, forKey: .point)
        case let .eraseEnd(id):
            try c.encode("erase_end", forKey: .type); try c.encode(id, forKey: .id)
        case .undo: try c.encode("undo", forKey: .type)
        case .redo: try c.encode("redo", forKey: .type)
        case .clear: try c.encode("clear", forKey: .type)
        case let .setVisible(visible):
            try c.encode("set_visible", forKey: .type); try c.encode(visible, forKey: .visible)
        case let .transientBegin(id, brush, point, lifetimeMs):
            try c.encode("transient_begin", forKey: .type); try c.encode(id, forKey: .id)
            try c.encode(brush, forKey: .brush); try c.encode(point, forKey: .point); try c.encode(lifetimeMs, forKey: .lifetimeMs)
        case let .pointer(point, color, radius, laser):
            try c.encode("pointer", forKey: .type); try c.encode(point, forKey: .point)
            try c.encode(color, forKey: .color); try c.encode(radius, forKey: .radius); try c.encode(laser, forKey: .laser)
        }
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Keys.self)
        switch try c.decode(String.self, forKey: .type) {
        case "begin": self = .begin(id: try c.decode(UInt64.self, forKey: .id), brush: try c.decode(DeskInkBrush.self, forKey: .brush), point: try c.decode(DeskInkPoint.self, forKey: .point))
        case "append": self = .append(id: try c.decode(UInt64.self, forKey: .id), point: try c.decode(DeskInkPoint.self, forKey: .point))
        case "end": self = .end(id: try c.decode(UInt64.self, forKey: .id))
        case "erase_begin": self = .eraseBegin(id: try c.decode(UInt64.self, forKey: .id), point: try c.decode(DeskInkPoint.self, forKey: .point), radius: try c.decode(CGFloat.self, forKey: .radius))
        case "erase_to": self = .eraseTo(id: try c.decode(UInt64.self, forKey: .id), point: try c.decode(DeskInkPoint.self, forKey: .point))
        case "erase_end": self = .eraseEnd(id: try c.decode(UInt64.self, forKey: .id))
        case "undo": self = .undo
        case "redo": self = .redo
        case "clear": self = .clear
        case "set_visible": self = .setVisible(try c.decode(Bool.self, forKey: .visible))
        case "transient_begin": self = .transientBegin(id: try c.decode(UInt64.self, forKey: .id), brush: try c.decode(DeskInkBrush.self, forKey: .brush), point: try c.decode(DeskInkPoint.self, forKey: .point), lifetimeMs: try c.decode(UInt32.self, forKey: .lifetimeMs))
        case "pointer": self = .pointer(point: try c.decodeIfPresent(DeskInkPoint.self, forKey: .point), color: try c.decode([UInt8].self, forKey: .color), radius: try c.decode(CGFloat.self, forKey: .radius), laser: try c.decode(Bool.self, forKey: .laser))
        default: throw DecodingError.dataCorruptedError(forKey: .type, in: c, debugDescription: "Unknown ink command")
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

struct DeskInkStroke {
    let id: UInt64
    let brush: DeskInkBrush
    var points: [DeskInkPoint]
    var finished = false
    var lifetimeMs: UInt32? = nil
    var expiresAt: UInt64 = 0
}

struct DeskInkPointer {
    let point: DeskInkPoint
    let color: [UInt8]
    let radius: CGFloat
    let laser: Bool
    let expiresAt: UInt64
}

/// Main-view-owned drawing state. Point updates never publish SwiftUI changes.
/// The command contract and gesture history match the Windows ink session.
final class DeskInkDocument {
    private(set) var strokes: [DeskInkStroke] = []
    private(set) var temporary: [DeskInkStroke] = []
    private(set) var pointer: DeskInkPointer?
    private(set) var visible = true
    private var nowMs: UInt64 = 0
    private struct Edit { var remove: [UInt64] = []; var insert: [(Int, DeskInkStroke)] = [] }
    private var undoStack: [Edit] = []
    private var redoStack: [Edit] = []
    private struct Erase { let id: UInt64; var last: DeskInkPoint; let radius: CGFloat; var removed: [(Int, DeskInkStroke)] }
    private var eraser: Erase?
    var canUndo: Bool { !undoStack.isEmpty }
    var canRedo: Bool { !redoStack.isEmpty }
    var renderStrokes: [DeskInkStroke] { visible ? strokes + temporary : [] }

    @discardableResult
    func apply(_ command: DeskInkCommand) -> Bool {
        switch command {
        case let .begin(id, brush, point):
            guard !strokes.contains(where: { $0.id == id }), !temporary.contains(where: { $0.id == id }) else { return false }
            strokes.append(.init(id: id, brush: brush, points: [point])); redoStack.removeAll()
        case let .append(id, point):
            if let index = strokes.firstIndex(where: { $0.id == id && !$0.finished }) { strokes[index].points.append(point) }
            else if let index = temporary.firstIndex(where: { $0.id == id && !$0.finished }) { temporary[index].points.append(point) }
            else { return false }
        case let .end(id):
            if let index = strokes.firstIndex(where: { $0.id == id && !$0.finished }) {
                strokes[index].finished = true; remember(Edit(remove: [id]))
            } else if let index = temporary.firstIndex(where: { $0.id == id && !$0.finished }) {
                temporary[index].finished = true
                temporary[index].expiresAt = nowMs + UInt64(temporary[index].lifetimeMs ?? 2000)
            } else { return false }
        case let .eraseBegin(id, point, radius):
            guard eraser == nil else { return false }
            eraser = Erase(id: id, last: point, radius: radius, removed: erase(from: point, to: point, radius: radius))
        case let .eraseTo(id, point):
            guard var gesture = eraser, gesture.id == id else { return false }
            gesture.removed += erase(from: gesture.last, to: point, radius: gesture.radius)
            gesture.last = point; eraser = gesture
        case let .eraseEnd(id):
            guard let gesture = eraser, gesture.id == id else { return false }
            if !gesture.removed.isEmpty { remember(Edit(insert: gesture.removed.reversed())) }
            eraser = nil
        case .undo:
            guard eraser == nil, !strokes.contains(where: { !$0.finished }) else { return false }
            if let edit = undoStack.popLast() { redoStack.append(applyEdit(edit)) }
        case .redo:
            guard eraser == nil, !strokes.contains(where: { !$0.finished }) else { return false }
            if let edit = redoStack.popLast() { undoStack.append(applyEdit(edit)) }
        case .clear:
            guard eraser == nil, !strokes.contains(where: { !$0.finished }) else { return false }
            if !strokes.isEmpty { remember(Edit(insert: Array(strokes.enumerated()))) }
            strokes.removeAll(); temporary.removeAll(); pointer = nil
        case let .setVisible(visible): self.visible = visible
        case let .transientBegin(id, brush, point, lifetimeMs):
            guard !strokes.contains(where: { $0.id == id }), !temporary.contains(where: { $0.id == id }) else { return false }
            temporary.append(.init(id: id, brush: brush, points: [point], lifetimeMs: lifetimeMs, expiresAt: nowMs + UInt64(lifetimeMs)))
        case let .pointer(point, color, radius, laser):
            pointer = point.map { DeskInkPointer(point: $0, color: color, radius: radius, laser: laser, expiresAt: nowMs + 750) }
        }
        return true
    }

    @discardableResult
    func advanceTime(_ milliseconds: UInt64) -> Bool {
        nowMs = max(nowMs, milliseconds)
        let before = temporary.count
        temporary.removeAll { $0.finished && $0.expiresAt <= nowMs }
        let expired = pointer.map { $0.expiresAt <= nowMs } ?? false
        if expired { pointer = nil }
        return expired || before != temporary.count
    }

    private func remember(_ edit: Edit) {
        redoStack.removeAll(); undoStack.append(edit)
        if undoStack.count > 100 { undoStack.removeFirst() }
    }
    private func applyEdit(_ edit: Edit) -> Edit {
        var inverse = Edit()
        for id in edit.remove {
            if let index = strokes.firstIndex(where: { $0.id == id }) { inverse.insert.append((index, strokes.remove(at: index))) }
        }
        inverse.insert.reverse()
        for (index, stroke) in edit.insert {
            inverse.remove.append(stroke.id); strokes.insert(stroke, at: min(index, strokes.count))
        }
        return inverse
    }
    private func erase(from: DeskInkPoint, to: DeskInkPoint, radius: CGFloat) -> [(Int, DeskInkStroke)] {
        var removed: [(Int, DeskInkStroke)] = []; var index = 0
        while index < strokes.count {
            let stroke = strokes[index], limit = pow(radius + strokes[index].brush.width / 2, 2)
            let hit: Bool
            if stroke.points.count == 1 { hit = Self.pointDistance(stroke.points[0], from, to) <= limit }
            else { hit = zip(stroke.points, stroke.points.dropFirst()).contains { Self.segmentDistance(from, to, $0.0, $0.1) <= limit } }
            if hit { removed.append((index, strokes.remove(at: index))) } else { index += 1 }
        }
        return removed
    }
    private static func pointDistance(_ p: DeskInkPoint, _ a: DeskInkPoint, _ b: DeskInkPoint) -> CGFloat {
        let dx = b.x-a.x, dy = b.y-a.y, length = dx*dx+dy*dy
        let t = length > 0 ? min(1, max(0, ((p.x-a.x)*dx+(p.y-a.y)*dy)/length)) : 0
        return pow(p.x-a.x-t*dx, 2) + pow(p.y-a.y-t*dy, 2)
    }
    private static func segmentDistance(_ a: DeskInkPoint, _ b: DeskInkPoint, _ c: DeskInkPoint, _ d: DeskInkPoint) -> CGFloat {
        let rx = b.x-a.x, ry = b.y-a.y, sx = d.x-c.x, sy = d.y-c.y, cross = rx*sy-ry*sx
        if abs(cross) > CGFloat(Float.ulpOfOne) {
            let t = ((c.x-a.x)*sy-(c.y-a.y)*sx)/cross, u = ((c.x-a.x)*ry-(c.y-a.y)*rx)/cross
            if (0...1).contains(t) && (0...1).contains(u) { return 0 }
        }
        return min(pointDistance(a,c,d),pointDistance(b,c,d),pointDistance(c,a,b),pointDistance(d,a,b))
    }
}
