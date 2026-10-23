import Foundation

/// Clip against a swept circular eraser, preserving disconnected stroke contours.
enum DeskInkPartialEraser {
    static func cut(_ stroke: DeskInkStroke, from: DeskInkPoint, to: DeskInkPoint, radius: CGFloat) -> (points:[DeskInkPoint],breaks:Set<Int>)? {
        let radius=Double(Float(radius+stroke.brush.width/2))
        var points:[DeskInkPoint]=[], breaks=Set<Int>(), changed=false, start=0
        for end in stroke.breaks.sorted()+[stroke.points.count] {
            if end-start==1 {
                let p=stroke.points[start]
                if interval(p,p,from,to,radius) != nil { changed=true }
                else { if !points.isEmpty { breaks.insert(points.count) }; points.append(p) }
            }
            var join=false
            if end-start>=2 { for i in (start+1)..<end {
                let a=stroke.points[i-1],b=stroke.points[i]
                var visible:[(Double,Double)]=[(0,1)]
                if let cut=interval(a,b,from,to,radius), cut.1-cut.0>1e-9 {
                    changed=true; visible=[]
                    if cut.0>1e-9 { visible.append((0,cut.0)) }
                    if cut.1<1-1e-9 { visible.append((cut.1,1)) }
                }
                if visible.isEmpty { join=false }
                for (lo,hi) in visible {
                    let p=interpolate(a,b,lo),q=interpolate(a,b,hi)
                    if !join || lo>0 { if !points.isEmpty { breaks.insert(points.count) }; points.append(p) }
                    if points.last != q { points.append(q) }
                    join=hi==1
                }
            } }
            start=end
        }
        return changed ? (points,breaks):nil
    }
    private static func interpolate(_ a:DeskInkPoint,_ b:DeskInkPoint,_ t:Double)->DeskInkPoint {
        if t==0 { return a }; if t==1 { return b }
        return .init(x:CGFloat(Float(Double(a.x)+(Double(b.x)-Double(a.x))*t)),
                     y:CGFloat(Float(Double(a.y)+(Double(b.y)-Double(a.y))*t)),
                     pressure:CGFloat(Float(Double(a.pressure)+(Double(b.pressure)-Double(a.pressure))*t)))
    }
    private static func interval(_ a:DeskInkPoint,_ b:DeskInkPoint,_ from:DeskInkPoint,_ to:DeskInkPoint,_ r:Double)->(Double,Double)? {
        let ax=Double(a.x),ay=Double(a.y),dx=Double(b.x)-ax,dy=Double(b.y)-ay
        let fx=Double(from.x),fy=Double(from.y),tx=Double(to.x),ty=Double(to.y)
        if min(ax,ax+dx)>max(fx,tx)+r || max(ax,ax+dx)<min(fx,tx)-r || min(ay,ay+dy)>max(fy,ty)+r || max(ay,ay+dy)<min(fy,ty)-r { return nil }
        var ranges:[(Double,Double)]=[]
        let length2=dx*dx+dy*dy
        for (cx,cy) in [(fx,fy),(tx,ty)] {
            let x=ax-cx,y=ay-cy
            if length2<1e-18 { if x*x+y*y<=r*r { ranges.append((0,1)) } }
            else {
                let dot=x*dx+y*dy,discriminant=(x*dx+y*dy)*(x*dx+y*dy)-length2*(x*x+y*y-r*r)
                if discriminant>=0 {
                    let root=sqrt(discriminant),lo=max(0,(-dot-root)/length2),hi=min(1,(-dot+root)/length2)
                    if lo<=hi { ranges.append((lo,hi)) }
                }
            }
        }
        let length=hypot(tx-fx,ty-fy)
        if length>1e-9 {
            let ux=(tx-fx)/length,uy=(ty-fy)/length
            var lo=0.0,hi=1.0
            let slabs:[(Double,Double,Double,Double)]=[((ax-fx)*ux+(ay-fy)*uy,dx*ux+dy*uy,0,length), (-(ax-fx)*uy+(ay-fy)*ux,-dx*uy+dy*ux,-r,r)]
            for (p,d,lower,upper) in slabs {
                if abs(d)<1e-12 { if p<lower || p>upper { hi = -1 } }
                else { let t1=(lower-p)/d,t2=(upper-p)/d; lo=max(lo,min(t1,t2)); hi=min(hi,max(t1,t2)) }
            }
            if lo<=hi { ranges.append((lo,hi)) }
        }
        guard !ranges.isEmpty else { return nil }
        return (ranges.map(\.0).min()!,ranges.map(\.1).max()!)
    }
}
