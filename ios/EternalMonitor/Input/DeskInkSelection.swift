import Foundation
import CoreGraphics

/// Owns selection gestures; the document and Windows receive the same compact edit.
final class DeskInkSelection {
    enum Handle: CaseIterable {
        case topLeft, top, topRight, right, bottomRight, bottom, bottomLeft, left
        var xSide: CGFloat { switch self { case .topLeft,.bottomLeft,.left: return -1; case .topRight,.bottomRight,.right: return 1; default: return 0 } }
        var ySide: CGFloat { switch self { case .topLeft,.top,.topRight: return -1; case .bottomLeft,.bottom,.bottomRight: return 1; default: return 0 } }
        var isCorner: Bool { xSide != 0 && ySide != 0 }
        func position(in b: CGRect) -> CGPoint { .init(x:xSide<0 ? b.minX : xSide>0 ? b.maxX : b.midX, y:ySide<0 ? b.minY : ySide>0 ? b.maxY : b.midY) }
    }
    private enum Gesture { case none, lasso, move, resize(Handle) }
    private let document: DeskInkDocument
    private let size: CGSize
    private let emit: (DeskInkCommand,Bool) -> Void
    private var selected=Set<UInt64>()
    private(set) var lasso: [DeskInkPoint]=[]
    private var gesture=Gesture.none
    private var original=CGRect.zero
    private var start=DeskInkPoint(x:0,y:0,pressure:0), last=DeskInkPoint(x:0,y:0,pressure:0)
    private var scale: CGFloat=1
    private var id: UInt64=0, lastSent: UInt64=0
    private var editing=false

    init(document: DeskInkDocument,size: CGSize,emit: @escaping (DeskInkCommand,Bool)->Void) {
        self.document=document; self.size=size; self.emit=emit
    }
    var bounds: CGRect? {
        selected.formIntersection(Set(document.strokes.map(\.id)))
        let points=document.strokes.filter { selected.contains($0.id) }.flatMap(\.points)
        guard let first=points.first else { return nil }
        var l=first.x,t=first.y,r=first.x,b=first.y
        for p in points { l=min(l,p.x); t=min(t,p.y); r=max(r,p.x); b=max(b,p.y) }
        if r-l<1 { l=Self.clamp(l-0.5,0,size.width-1); r=l+1 }
        if b-t<1 { t=Self.clamp(t-0.5,0,size.height-1); b=t+1 }
        return CGRect(x:l,y:t,width:r-l,height:b-t)
    }
    func clear(transmit: Bool=true) { end(transmit:transmit); selected.removeAll(); lasso.removeAll() }
    func begin(_ point: DeskInkPoint,scale: CGFloat,id: UInt64) {
        end(); self.start=point; last=point; self.scale=scale; self.id=id; gesture = .none
        if let bounds {
            original=bounds
            if let handle=Handle.allCases.first(where: { hypot(point.x-$0.position(in:bounds).x,point.y-$0.position(in:bounds).y)<=12/scale }) { gesture = .resize(handle) }
            else if Self.contains(bounds,point) { gesture = .move }
        }
        if case .none=gesture {
            selected.removeAll()
            if let stroke=document.strokes.reversed().first(where: { $0.finished && Self.hit($0,point,tolerance:8/scale) }) {
                selected.insert(stroke.id); original=bounds ?? .zero; gesture = .move
            }
        }
        if case .none=gesture { gesture = .lasso; lasso.append(point) }
    }
    func move(_ point: DeskInkPoint,now: UInt64) {
        last=point
        if case .lasso=gesture {
            if let previous=lasso.last, hypot(point.x-previous.x,point.y-previous.y)>2/scale { lasso.append(point) }
            return
        }
        if case .none=gesture { return }
        guard hypot(point.x-start.x,point.y-start.y)>2/scale else { return }
        if !editing {
            editing=true; emit(.editBegin(id:id),true)
            let ids=selected.sorted()
            for offset in stride(from:0,to:ids.count,by:32) { emit(.editAdd(id:id,ids:Array(ids[offset..<min(offset+32,ids.count)])),true) }
        }
        if now>=lastSent+16 { preview(); lastSent=now }
    }
    func end(transmit: Bool=true) {
        if editing {
            if transmit { preview() }
            emit(.editEnd(id:id),transmit); editing=false
        } else if case .lasso=gesture, lasso.count>=3 {
            for stroke in document.strokes where stroke.finished && selected.count<4096 && stroke.points.allSatisfy({ Self.inside($0,polygon:lasso) }) { selected.insert(stroke.id) }
        }
        gesture = .none; lasso.removeAll()
    }
    private func preview() {
        let l=original.minX,t=original.minY,r=original.maxX,b=original.maxY,w=original.width,h=original.height
        var sx: CGFloat=1,sy: CGFloat=1,tx: CGFloat=0,ty: CGFloat=0
        switch gesture {
        case .move:
            tx=Self.clamp(last.x-start.x,-l,size.width-r); ty=Self.clamp(last.y-start.y,-t,size.height-b)
        case let .resize(handle):
            let ax=handle.xSide<0 ? r:l, ay=handle.ySide<0 ? b:t
            let maxX=min(1000,(handle.xSide<0 ? ax:size.width-ax)/w), maxY=min(1000,(handle.ySide<0 ? ay:size.height-ay)/h)
            if handle.xSide != 0 { sx=Self.clamp(handle.xSide*(last.x-ax)/w,min(4/w,maxX),maxX) }
            if handle.ySide != 0 { sy=Self.clamp(handle.ySide*(last.y-ay)/h,min(4/h,maxY),maxY) }
            if handle.isCorner { let ratio=min(max(sx,sy),min(maxX,maxY)); sx=ratio; sy=ratio }
            sx=Self.clamp(sx,0.001,1000); sy=Self.clamp(sy,0.001,1000)
            tx=Self.clamp(ax*(1-sx),-l*sx,size.width-r*sx); ty=Self.clamp(ay*(1-sy),-t*sy,size.height-b*sy)
        default: return
        }
        let x=Self.wireAxis(scale:sx,translation:tx,lower:l,upper:r,limit:size.width)
        let y=Self.wireAxis(scale:sy,translation:ty,lower:t,upper:b,limit:size.height)
        emit(.editTransform(id:id,sx:x.0,sy:y.0,tx:x.1,ty:y.1),true)
    }
    /// Windows evaluates coordinates as Float. Leave room for cancellation at
    /// the edges instead of sending a mathematically valid but negative f32 point.
    private static func wireAxis(scale: CGFloat,translation: CGFloat,lower: CGFloat,upper: CGFloat,limit: CGFloat) -> (CGFloat,CGFloat) {
        var factor=Float(scale)
        let l=Float(lower),r=Float(upper),edge=Float(limit)
        while r*factor-l*factor>edge { factor=factor.nextDown }
        let low = -l*factor, high=edge-r*factor
        let magnitude=max(abs(l*factor),abs(r*factor))
        let padding=min(max(0,(high-low)/2),max(0.001,magnitude.ulp*4))
        let offset=max(low+padding,min(high-padding,Float(translation)))
        return (CGFloat(factor),CGFloat(offset))
    }
    private static func clamp(_ value: CGFloat,_ lower: CGFloat,_ upper: CGFloat) -> CGFloat { lower>upper ? 0:max(lower,min(upper,value)) }
    private static func contains(_ b: CGRect,_ p: DeskInkPoint) -> Bool { p.x>=b.minX && p.x<=b.maxX && p.y>=b.minY && p.y<=b.maxY }
    private static func hit(_ stroke: DeskInkStroke,_ p: DeskInkPoint,tolerance: CGFloat) -> Bool {
        if let shape=stroke.shape, shape.kind != .line, inside(p,polygon:stroke.points) { return true }
        if stroke.points.count==1, let first=stroke.points.first { return hypot(p.x-first.x,p.y-first.y)<=tolerance+stroke.brush.width/2 }
        return zip(stroke.points,stroke.points.dropFirst()).contains { distance(p,$0.0,$0.1)<=tolerance+stroke.brush.width/2 }
    }
    private static func distance(_ p: DeskInkPoint,_ a: DeskInkPoint,_ b: DeskInkPoint) -> CGFloat {
        let dx=b.x-a.x,dy=b.y-a.y,len=dx*dx+dy*dy
        let t=len>0 ? clamp(((p.x-a.x)*dx+(p.y-a.y)*dy)/len,0,1):0
        return hypot(p.x-a.x-t*dx,p.y-a.y-t*dy)
    }
    private static func inside(_ p: DeskInkPoint,polygon: [DeskInkPoint]) -> Bool {
        guard polygon.count>=3 else { return false }
        var result=false, previous=polygon.count-1
        for index in polygon.indices {
            let a=polygon[previous],b=polygon[index]
            if distance(p,a,b)<0.5 { return true }
            if (a.y>p.y) != (b.y>p.y), p.x<(b.x-a.x)*(p.y-a.y)/(b.y-a.y)+a.x { result.toggle() }
            previous=index
        }
        return result
    }
}
