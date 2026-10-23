import Foundation
import CoreGraphics

struct DeskInkShape {
    enum Kind: String, Codable { case line, rectangle, ellipse }
    let kind: Kind
    let a: DeskInkPoint
    let b: DeskInkPoint
    var points: [DeskInkPoint] {
        switch kind {
        case .line: return [a,b]
        case .rectangle:
            return [a, .init(x:b.x,y:a.y,pressure:a.pressure), b, .init(x:a.x,y:b.y,pressure:a.pressure), a]
        case .ellipse:
            let cx=(a.x+b.x)/2, cy=(a.y+b.y)/2, rx=(b.x-a.x)/2, ry=(b.y-a.y)/2
            var points=(0..<64).map { i -> DeskInkPoint in
                let angle=CGFloat(i)*2 * .pi/64
                return .init(x:cx+rx*cos(angle),y:cy+ry*sin(angle),pressure:a.pressure)
            }
            points.append(points[0]); return points
        }
    }
}
