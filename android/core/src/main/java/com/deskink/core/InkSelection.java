package com.deskink.core;

import java.util.*;
import java.util.function.Consumer;
import org.json.*;

/** Lasso selection and a single undoable move/resize gesture in desktop coordinates. */
public final class InkSelection {
    private final InkDocument document;
    private final Consumer<JSONObject> emit;
    private final float width,height;
    private final Set<Long> selected=new LinkedHashSet<>();
    private final List<InkDocument.Point> lasso=new ArrayList<>();
    private float[] original;
    private InkDocument.Point start,last;
    private int mode=-1; // 0..7 handles clockwise, 8 move, 9 lasso
    private long id,lastSent;
    private float scale;
    private boolean editing;
    public InkSelection(InkDocument document,float width,float height,Consumer<JSONObject> emit) { this.document=document; this.width=width; this.height=height; this.emit=emit; }
    public List<InkDocument.Point> lasso() { return Collections.unmodifiableList(lasso); }
    public float[] bounds() {
        selected.removeIf(id -> document.strokes().stream().noneMatch(s -> s.id==id));
        if(selected.isEmpty()) return null;
        float l=Float.POSITIVE_INFINITY,t=l,r=Float.NEGATIVE_INFINITY,b=r;
        for(InkDocument.Stroke s:document.strokes()) if(selected.contains(s.id)) for(InkDocument.Point p:s.points) { l=Math.min(l,p.x); t=Math.min(t,p.y); r=Math.max(r,p.x); b=Math.max(b,p.y); }
        if(r-l<1) { l-=.5f; r+=.5f; } if(b-t<1) { t-=.5f; b+=.5f; }
        return new float[]{l,t,r,b};
    }
    public static float[][] handles(float[] b) {
        float cx=(b[0]+b[2])/2,cy=(b[1]+b[3])/2;
        return new float[][]{{b[0],b[1]},{cx,b[1]},{b[2],b[1]},{b[2],cy},{b[2],b[3]},{cx,b[3]},{b[0],b[3]},{b[0],cy}};
    }
    public void clear() { end(); selected.clear(); lasso.clear(); }
    public void begin(InkDocument.Point p,float scale,long id) {
        end(); this.start=this.last=p; this.scale=scale; this.id=id; original=bounds(); mode=-1;
        if(original!=null) {
            float[][] handles=handles(original);
            for(int i=0;i<8;i++) if(Math.hypot(p.x-handles[i][0],p.y-handles[i][1])<=12/scale) { mode=i; break; }
            if(mode<0&&insideBounds(p,original)) mode=8;
        }
        if(mode<0) {
            selected.clear();
            List<InkDocument.Stroke> strokes=document.strokes();
            for(int i=strokes.size()-1;i>=0;i--) {
                InkDocument.Stroke s=strokes.get(i); if(s.finished&&hit(s,p,8/scale)) { selected.add(s.id); mode=8; break; }
            }
            original=bounds();
        }
        if(mode<0) { mode=9; lasso.add(p); }
    }
    public void move(InkDocument.Point p,long now) {
        last=p;
        if(mode==9) {
            InkDocument.Point previous=lasso.get(lasso.size()-1);
            if(Math.hypot(p.x-previous.x,p.y-previous.y)>2/scale) lasso.add(p);
        } else if(mode>=0&&Math.hypot(p.x-start.x,p.y-start.y)>2/scale) {
            if(!editing) {
                editing=true; emit.accept(Ink.id("edit_begin",id));
                List<Long> ids=new ArrayList<>(selected);
                for(int i=0;i<ids.size();i+=32) emit.accept(Ink.object("type","edit_add","id",id,"ids",new JSONArray(ids.subList(i,Math.min(i+32,ids.size())))));
            }
            if(now-lastSent>=16) { preview(); lastSent=now; }
        }
    }
    public void end() {
        if(editing) { preview(); emit.accept(Ink.id("edit_end",id)); editing=false; }
        else if(mode==9&&lasso.size()>=3) {
            for(InkDocument.Stroke s:document.strokes()) if(s.finished&&selected.size()<4096&&s.points.stream().allMatch(p -> inside(p,lasso))) selected.add(s.id);
        }
        mode=-1; lasso.clear();
    }
    private void preview() {
        float l=original[0],t=original[1],r=original[2],b=original[3],w=r-l,h=b-t;
        float sx=1,sy=1,tx=0,ty=0;
        if(mode==8) {
            tx=clamp(last.x-start.x,.5f-l,width-.5f-r); ty=clamp(last.y-start.y,.5f-t,height-.5f-b);
        } else {
            boolean left=mode==0||mode==6||mode==7,right=mode==2||mode==3||mode==4,top=mode<=2,bottom=mode>=4&&mode<=6;
            if(left) l=clamp(last.x,.5f,r-4); if(right) r=clamp(last.x,l+4,width-.5f);
            if(top) t=clamp(last.y,.5f,b-4); if(bottom) b=clamp(last.y,t+4,height-.5f);
            sx=(r-l)/w; sy=(b-t)/h;
            if(mode%2==0) {
                float anchorX=left?original[2]:original[0],anchorY=top?original[3]:original[1];
                float maxX=left?(anchorX-.5f)/w:(width-.5f-anchorX)/w,maxY=top?(anchorY-.5f)/h:(height-.5f-anchorY)/h;
                sx=sy=Math.max(.001f,Math.min(Math.max(sx,sy),Math.min(maxX,maxY)));
                tx=anchorX*(1-sx); ty=anchorY*(1-sy);
            } else { tx=l-original[0]*sx; ty=t-original[1]*sy; }
        }
        emit.accept(Ink.object("type","edit_transform","id",id,"sx",sx,"sy",sy,"tx",tx,"ty",ty));
    }
    private static float clamp(float v,float min,float max) { return Math.max(min,Math.min(max,v)); }
    private static boolean insideBounds(InkDocument.Point p,float[] b) { return p.x>=b[0]&&p.y>=b[1]&&p.x<=b[2]&&p.y<=b[3]; }
    private static boolean hit(InkDocument.Stroke s,InkDocument.Point p,float tolerance) {
        if(s.shape!=null&&!s.shape.kind.equals("line")&&inside(p,s.points)) return true;
        if(s.points.size()==1) return Math.hypot(p.x-s.points.get(0).x,p.y-s.points.get(0).y)<=tolerance+s.width/2;
        for(int i=1;i<s.points.size();i++) if(distance(p,s.points.get(i-1),s.points.get(i))<=tolerance+s.width/2) return true;
        return false;
    }
    private static double distance(InkDocument.Point p,InkDocument.Point a,InkDocument.Point b) {
        float dx=b.x-a.x,dy=b.y-a.y,len=dx*dx+dy*dy,t=len>0?clamp(((p.x-a.x)*dx+(p.y-a.y)*dy)/len,0,1):0;
        return Math.hypot(p.x-a.x-t*dx,p.y-a.y-t*dy);
    }
    private static boolean inside(InkDocument.Point p,List<InkDocument.Point> polygon) {
        boolean inside=false;
        for(int i=0,j=polygon.size()-1;i<polygon.size();j=i++) {
            InkDocument.Point a=polygon.get(j),b=polygon.get(i);
            if(distance(p,a,b)<.5) return true;
            if((a.y>p.y)!=(b.y>p.y)&&p.x<(b.x-a.x)*(p.y-a.y)/(b.y-a.y)+a.x) inside=!inside;
        }
        return inside;
    }
}
