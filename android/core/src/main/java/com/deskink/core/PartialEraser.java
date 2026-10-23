package com.deskink.core;

import java.util.*;

/** Clips polylines against the swept eraser, keeping disconnected contours. */
final class PartialEraser {
    static final class Cut {
        final List<InkDocument.Point> points=new ArrayList<>();
        final Set<Integer> breaks=new TreeSet<>();
        boolean changed;
    }
    static Cut cut(InkDocument.Stroke stroke,InkDocument.Point from,InkDocument.Point to,float size) {
        double radius=(double)(size+stroke.width/2);
        Cut out=new Cut(); List<Integer> ends=new ArrayList<>(stroke.breaks); ends.add(stroke.points.size());
        int start=0;
        for(int end:ends) {
            if(end-start==1) {
                InkDocument.Point p=stroke.points.get(start);
                if(interval(p,p,from,to,radius)!=null) out.changed=true;
                else { if(!out.points.isEmpty()) out.breaks.add(out.points.size()); out.points.add(p); }
            }
            boolean join=false;
            for(int i=start+1;i<end;i++) {
                InkDocument.Point a=stroke.points.get(i-1),b=stroke.points.get(i);
                double[] cut=interval(a,b,from,to,radius);
                List<double[]> visible=new ArrayList<>();
                if(cut!=null&&cut[1]-cut[0]>1e-9) {
                    out.changed=true;
                    if(cut[0]>1e-9) visible.add(new double[]{0,cut[0]});
                    if(cut[1]<1-1e-9) visible.add(new double[]{cut[1],1});
                } else visible.add(new double[]{0,1});
                if(visible.isEmpty()) join=false;
                for(double[] span:visible) {
                    InkDocument.Point p=interpolate(a,b,span[0]),q=interpolate(a,b,span[1]);
                    if(!join||span[0]>0) { if(!out.points.isEmpty()) out.breaks.add(out.points.size()); out.points.add(p); }
                    InkDocument.Point last=out.points.get(out.points.size()-1);
                    if(last.x!=q.x||last.y!=q.y||last.pressure!=q.pressure) out.points.add(q);
                    join=span[1]==1;
                }
            }
            start=end;
        }
        return out.changed?out:null;
    }
    private static InkDocument.Point interpolate(InkDocument.Point a,InkDocument.Point b,double t) {
        if(t==0) return a; if(t==1) return b;
        return new InkDocument.Point((float)(a.x+((double)b.x-a.x)*t),(float)(a.y+((double)b.y-a.y)*t),(float)(a.pressure+((double)b.pressure-a.pressure)*t));
    }
    private static double[] interval(InkDocument.Point a,InkDocument.Point b,InkDocument.Point from,InkDocument.Point to,double r) {
        double ax=a.x,ay=a.y,dx=(double)b.x-ax,dy=(double)b.y-ay,fx=from.x,fy=from.y,tx=to.x,ty=to.y;
        if(Math.min(ax,ax+dx)>Math.max(fx,tx)+r||Math.max(ax,ax+dx)<Math.min(fx,tx)-r||Math.min(ay,ay+dy)>Math.max(fy,ty)+r||Math.max(ay,ay+dy)<Math.min(fy,ty)-r) return null;
        List<double[]> ranges=new ArrayList<>(); double length2=dx*dx+dy*dy;
        for(double[] center:new double[][]{{fx,fy},{tx,ty}}) {
            double x=ax-center[0],y=ay-center[1];
            if(length2<1e-18) { if(x*x+y*y<=r*r) ranges.add(new double[]{0,1}); }
            else {
                double dot=x*dx+y*dy,discriminant=dot*dot-length2*(x*x+y*y-r*r);
                if(discriminant>=0) { double root=Math.sqrt(discriminant),lo=Math.max(0,(-dot-root)/length2),hi=Math.min(1,(-dot+root)/length2); if(lo<=hi) ranges.add(new double[]{lo,hi}); }
            }
        }
        double length=Math.hypot(tx-fx,ty-fy);
        if(length>1e-9) {
            double ux=(tx-fx)/length,uy=(ty-fy)/length,lo=0,hi=1;
            for(double[] slab:new double[][]{{(ax-fx)*ux+(ay-fy)*uy,dx*ux+dy*uy,0,length},{-(ax-fx)*uy+(ay-fy)*ux,-dx*uy+dy*ux,-r,r}}) {
                double p=slab[0],d=slab[1];
                if(Math.abs(d)<1e-12) { if(p<slab[2]||p>slab[3]) hi=-1; }
                else { double t1=(slab[2]-p)/d,t2=(slab[3]-p)/d; lo=Math.max(lo,Math.min(t1,t2)); hi=Math.min(hi,Math.max(t1,t2)); }
            }
            if(lo<=hi) ranges.add(new double[]{lo,hi});
        }
        if(ranges.isEmpty()) return null;
        double lo=1,hi=0; for(double[] span:ranges) { lo=Math.min(lo,span[0]); hi=Math.max(hi,span[1]); }
        return new double[]{lo,hi};
    }
}
