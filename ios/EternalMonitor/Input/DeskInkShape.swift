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

    /// Recognition runs only after the stationary hold, never per Pencil sample.
    static func recognize(_ raw: [DeskInkPoint]) -> DeskInkShape? {
        guard raw.count >= 2, let first=raw.first, let last=raw.last else { return nil }
        var left=first.x, top=first.y, right=first.x, bottom=first.y, length: CGFloat=0
        for (index,p) in raw.enumerated() {
            left=min(left,p.x); top=min(top,p.y); right=max(right,p.x); bottom=max(bottom,p.y)
            if index>0 { length += distance(raw[index-1],p) }
        }
        let w=right-left, h=bottom-top, diagonal=hypot(w,h), chord=distance(first,last)
        if chord>=24, length<chord*1.15 {
            let dx=last.x-first.x, dy=last.y-first.y
            var deviation: CGFloat=0
            for point in raw {
                let cross=dx*(first.y-point.y)-(first.x-point.x)*dy
                deviation=max(deviation,abs(cross)/chord)
            }
            if deviation<max(3,chord*0.035) { return .init(kind:.line,a:first,b:last) }
        }
        guard raw.count>=5, w>=16, h>=16 else { return nil }
        let samples=resample(raw,length:length)
        // A slightly unfinished fourth side is still a box. Round shapes retain
        // the stricter closure rule so open handwriting is not corrected.
        if chord<=max(10,min(w,h)*0.35), matchesRectangle(samples,length:length) {
            return .init(kind:.rectangle,a:.init(x:left,y:top,pressure:0.5),b:.init(x:right,y:bottom,pressure:0.5))
        }
        guard chord<=max(10,diagonal*0.16) else { return nil }
        let cx=(left+right)/2, cy=(top+bottom)/2
        var radialError: CGFloat=0, maxError: CGFloat=0, turn: CGFloat=0, travel: CGFloat=0, previous: CGFloat=0
        for (index,p) in samples.enumerated() {
            let x=(p.x-cx)/(w/2), y=(p.y-cy)/(h/2), error=abs(hypot(x,y)-1), angle=atan2(y,x)
            radialError += error; maxError=max(maxError,error)
            if index>0 {
                var delta=angle-previous
                while delta > .pi { delta -= 2 * .pi }; while delta < -.pi { delta += 2 * .pi }
                turn += delta; travel += abs(delta)
            }
            previous=angle
        }
        guard radialError/CGFloat(samples.count)<0.10, maxError<0.24, abs(turn)>5.2, travel<7.5 else { return nil }
        if abs(w-h)/max(w,h)<0.08 {
            let radius=min(w,h)/2; left=cx-radius; right=cx+radius; top=cy-radius; bottom=cy+radius
        }
        return .init(kind:.ellipse,a:.init(x:left,y:top,pressure:0.5),b:.init(x:right,y:bottom,pressure:0.5))
    }
    /// Test the box's own axes before trying an ellipse. Snapping still returns
    /// an upright rectangle inside the original bounds, as the wire format expects.
    private static func matchesRectangle(_ samples: [DeskInkPoint],length: CGFloat) -> Bool {
        for step in 0...36 {
            let degrees: CGFloat=step==0 ? 0 : CGFloat((step+1)/2)*2.5*(step%2==0 ? -1 : 1)
            let angle=degrees * .pi/180, c=cos(angle), s=sin(angle)
            let rotated=samples.map { CGPoint(x:$0.x*c+$0.y*s,y: -$0.x*s+$0.y*c) }
            guard let first=rotated.first else { return false }
            var left=first.x, right=first.x, top=first.y, bottom=first.y
            for p in rotated { left=min(left,p.x); right=max(right,p.x); top=min(top,p.y); bottom=max(bottom,p.y) }
            let w=right-left, h=bottom-top
            guard w>=16, h>=16, length>1.6*(w+h), length<2.6*(w+h) else { continue }
            let corners=[(left,top),(right,top),(right,bottom),(left,bottom)]
            var error: CGFloat=0, visited=Array(repeating:false,count:4)
            for p in rotated {
                error += min(abs(p.x-left),abs(p.x-right),abs(p.y-top),abs(p.y-bottom))
                for (index,corner) in corners.enumerated() where hypot(p.x-corner.0,p.y-corner.1)<min(w,h)*0.22 { visited[index]=true }
            }
            if error/CGFloat(rotated.count)<hypot(w,h)*0.025, visited.allSatisfy({ $0 }) { return true }
        }
        return false
    }
    private static func distance(_ a: DeskInkPoint,_ b: DeskInkPoint) -> CGFloat { hypot(a.x-b.x,a.y-b.y) }
    private static func resample(_ raw: [DeskInkPoint],length: CGFloat) -> [DeskInkPoint] {
        var segment=1, before: CGFloat=0, result: [DeskInkPoint]=[]
        for index in 0..<64 {
            let target=length*CGFloat(index)/63
            var span=distance(raw[segment-1],raw[segment])
            while segment<raw.count-1, before+span<target { before += span; segment += 1; span=distance(raw[segment-1],raw[segment]) }
            let a=raw[segment-1], b=raw[segment], t=span>0 ? (target-before)/span : 0
            result.append(.init(x:a.x+(b.x-a.x)*t,y:a.y+(b.y-a.y)*t,pressure:0.5))
        }
        return result
    }
}
