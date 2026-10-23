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
        if(w<16||h<16||distance(raw.get(0),raw.get(raw.size()-1))>Math.max(10,diagonal*.16)) return null;
        List<InkDocument.Point> samples=resample(raw,length,64);
        double error=0; boolean[] corners=new boolean[4];
        for(InkDocument.Point p:samples) {
            error+=Math.min(Math.min(Math.abs(p.x-left),Math.abs(p.x-right)),Math.min(Math.abs(p.y-top),Math.abs(p.y-bottom)));
            float[] xs={left,right,right,left},ys={top,top,bottom,bottom};
            for(int i=0;i<4;i++) if(Math.hypot(p.x-xs[i],p.y-ys[i])<Math.min(w,h)*.22) corners[i]=true;
        }
        if(error/samples.size()<diagonal*.025&&corners[0]&&corners[1]&&corners[2]&&corners[3]
                &&length>1.6*(w+h)&&length<2.6*(w+h)) {
            return new InkShape("rectangle",new InkDocument.Point(left,top,.5f),new InkDocument.Point(right,bottom,.5f));
        }
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
