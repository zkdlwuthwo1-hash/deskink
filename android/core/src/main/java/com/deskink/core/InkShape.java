package com.deskink.core;

import java.util.*;
import org.json.JSONObject;

/** Compact shape geometry; expanded identically on Windows and each tablet. */
public final class InkShape {
    public final String kind;
    public final InkDocument.Point a,b;
    public InkShape(String kind,InkDocument.Point a,InkDocument.Point b) { this.kind=kind; this.a=a; this.b=b; }
    public JSONObject command(long id) { return Ink.object("type","shape","id",id,"kind",kind,"a",Ink.point(a.x,a.y,a.pressure),"b",Ink.point(b.x,b.y,b.pressure)); }
    public List<InkDocument.Point> points() {
        List<InkDocument.Point> result=new ArrayList<>();
        if(kind.equals("line")) { result.add(a); result.add(b); }
        else if(kind.equals("rectangle")) {
            result.add(a); result.add(new InkDocument.Point(b.x,a.y,a.pressure)); result.add(b);
            result.add(new InkDocument.Point(a.x,b.y,a.pressure)); result.add(a);
        } else if(kind.equals("ellipse")) {
            float cx=(a.x+b.x)/2,cy=(a.y+b.y)/2,rx=(b.x-a.x)/2,ry=(b.y-a.y)/2;
            for(int i=0;i<64;i++) { double angle=i*Math.PI*2/64; result.add(new InkDocument.Point(cx+rx*(float)Math.cos(angle),cy+ry*(float)Math.sin(angle),a.pressure)); }
            result.add(result.get(0));
        }
        return result;
    }

    /** Run once after the hold deadline, never on the high-frequency input path. */
    public static InkShape recognize(List<InkDocument.Point> raw) {
        if(raw.size()<2) return null;
        float left=Float.POSITIVE_INFINITY,top=left,right=Float.NEGATIVE_INFINITY,bottom=right;
        double length=0;
        for(int i=0;i<raw.size();i++) {
            InkDocument.Point p=raw.get(i); left=Math.min(left,p.x); top=Math.min(top,p.y); right=Math.max(right,p.x); bottom=Math.max(bottom,p.y);
            if(i>0) length+=distance(raw.get(i-1),p);
        }
        float w=right-left,h=bottom-top; double diagonal=Math.hypot(w,h);
        InkDocument.Point first=raw.get(0),last=raw.get(raw.size()-1);
        double chord=distance(first,last);
        if(chord>=24&&length<chord*1.15) {
            double maximum=0;
            for(InkDocument.Point p:raw) maximum=Math.max(maximum,Math.abs((last.x-first.x)*(first.y-p.y)-(first.x-p.x)*(last.y-first.y))/chord);
            if(maximum<Math.max(3,chord*.035)) return new InkShape("line",first,last);
        }
        if(raw.size()<5) return null;
        if(w<16||h<16) return null;
        List<InkDocument.Point> samples=resample(raw,length,64);
        // Four sides are enough evidence for a box even when the final side
        // stops a little short. Keep the stricter closure rule for round shapes.
        if(chord<=Math.max(10,Math.max(diagonal*.16,Math.min(w,h)*.35))&&matchesRectangle(samples,length)) {
            return new InkShape("rectangle",new InkDocument.Point(left,top,.5f),new InkDocument.Point(right,bottom,.5f));
        }
        if(chord>Math.max(10,diagonal*.16)) return null;
        double radialError=0,maxError=0,turn=0,travel=0,previous=0;
        float cx=(left+right)/2,cy=(top+bottom)/2;
        for(int i=0;i<samples.size();i++) {
            InkDocument.Point p=samples.get(i); double x=(p.x-cx)/(w/2),y=(p.y-cy)/(h/2);
            double e=Math.abs(Math.hypot(x,y)-1); radialError+=e; maxError=Math.max(maxError,e);
            double angle=Math.atan2(y,x);
            if(i>0) { double delta=angle-previous; while(delta>Math.PI) delta-=2*Math.PI; while(delta< -Math.PI) delta+=2*Math.PI; turn+=delta; travel+=Math.abs(delta); }
            previous=angle;
        }
        if(radialError/samples.size()<.10&&maxError<.24&&Math.abs(turn)>5.2&&travel<7.5) {
            if(Math.abs(w-h)/Math.max(w,h)<.08) { float r=Math.min(w,h)/2; left=cx-r; right=cx+r; top=cy-r; bottom=cy+r; }
            return new InkShape("ellipse",new InkDocument.Point(left,top,.5f),new InkDocument.Point(right,bottom,.5f));
        }
        return null;
    }
    /** Compare against the box's own axes before trying an ellipse. A small
     * hand-drawn tilt must not make four straight sides look like an oval.
     * Snapping still produces an upright rectangle inside the original bounds. */
    private static boolean matchesRectangle(List<InkDocument.Point> samples,double length) {
        for(int step=0;step<=36;step++) {
            double angle=Math.toRadians(step==0?0:((step+1)/2)*2.5*(step%2==0?-1:1));
            double cos=Math.cos(angle),sin=Math.sin(angle);
            double left=Double.POSITIVE_INFINITY,top=left,right=Double.NEGATIVE_INFINITY,bottom=right;
            double[] xs=new double[samples.size()],ys=new double[samples.size()];
            for(int i=0;i<samples.size();i++) {
                InkDocument.Point p=samples.get(i);
                xs[i]=p.x*cos+p.y*sin; ys[i]=-p.x*sin+p.y*cos;
                left=Math.min(left,xs[i]); right=Math.max(right,xs[i]); top=Math.min(top,ys[i]); bottom=Math.max(bottom,ys[i]);
            }
            double w=right-left,h=bottom-top;
            if(w<16||h<16||length<=1.6*(w+h)||length>=2.6*(w+h)) continue;
            double error=0; boolean[] corners=new boolean[4];
            double[] cx={left,right,right,left},cy={top,top,bottom,bottom};
            for(int i=0;i<xs.length;i++) {
                error+=Math.min(Math.min(Math.abs(xs[i]-left),Math.abs(xs[i]-right)),Math.min(Math.abs(ys[i]-top),Math.abs(ys[i]-bottom)));
                for(int corner=0;corner<4;corner++) if(Math.hypot(xs[i]-cx[corner],ys[i]-cy[corner])<Math.min(w,h)*.22) corners[corner]=true;
            }
            if(error/xs.length<Math.hypot(w,h)*.025&&corners[0]&&corners[1]&&corners[2]&&corners[3]) return true;
        }
        return false;
    }
    private static double distance(InkDocument.Point a,InkDocument.Point b) { return Math.hypot(a.x-b.x,a.y-b.y); }
    private static List<InkDocument.Point> resample(List<InkDocument.Point> raw,double length,int count) {
        List<InkDocument.Point> points=new ArrayList<>(); int segment=1; double before=0;
        for(int i=0;i<count;i++) {
            double target=length*i/(count-1),span=distance(raw.get(segment-1),raw.get(segment));
            while(segment<raw.size()-1&&before+span<target) { before+=span; segment++; span=distance(raw.get(segment-1),raw.get(segment)); }
            InkDocument.Point a=raw.get(segment-1),b=raw.get(segment); float t=(float)(span>0?(target-before)/span:0);
            points.add(new InkDocument.Point(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,.5f));
        }
        return points;
    }
}
