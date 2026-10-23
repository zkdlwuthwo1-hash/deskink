package com.deskink.core;

import java.util.*;
import java.util.function.Consumer;
import org.json.*;

/** Lasso selection and a single undoable move/resize gesture in desktop coordinates. */
public final class InkSelection {
    private enum Gesture { NONE, LASSO, MOVE, RESIZE }
    private enum Handle {
        TOP_LEFT(-1,-1), TOP(0,-1), TOP_RIGHT(1,-1), RIGHT(1,0),
        BOTTOM_RIGHT(1,1), BOTTOM(0,1), BOTTOM_LEFT(-1,1), LEFT(-1,0);
        final int xSide,ySide;
        Handle(int xSide,int ySide) { this.xSide=xSide; this.ySide=ySide; }
        boolean corner() { return xSide!=0&&ySide!=0; }
        float[] position(float[] b) { return new float[]{xSide<0?b[0]:xSide>0?b[2]:(b[0]+b[2])/2,ySide<0?b[1]:ySide>0?b[3]:(b[1]+b[3])/2}; }
    }
    private final InkDocument document;
    private final Consumer<JSONObject> emit;
    private final float width,height;
    private final Set<Long> selected=new LinkedHashSet<>();
    private final List<InkDocument.Point> lasso=new ArrayList<>();
    private float[] original;
    private InkDocument.Point start,last;
    private Gesture gesture=Gesture.NONE;
    private Handle handle;
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
        if(r-l<1) { l=clamp(l-.5f,0,width-1); r=l+1; }
        if(b-t<1) { t=clamp(t-.5f,0,height-1); b=t+1; }
        return new float[]{l,t,r,b};
    }
    public static float[][] handles(float[] b) {
        return Arrays.stream(Handle.values()).map(handle -> handle.position(b)).toArray(float[][]::new);
    }
    public void clear() { end(); selected.clear(); lasso.clear(); }
    public void begin(InkDocument.Point p,float scale,long id) {
        end(); this.start=this.last=p; this.scale=scale; this.id=id; original=bounds(); gesture=Gesture.NONE;
        if(original!=null) {
            for(Handle candidate:Handle.values()) {
                float[] position=candidate.position(original);
                if(Math.hypot(p.x-position[0],p.y-position[1])<=12/scale) { gesture=Gesture.RESIZE; handle=candidate; break; }
            }
            if(gesture==Gesture.NONE&&insideBounds(p,original)) gesture=Gesture.MOVE;
        }
        if(gesture==Gesture.NONE) {
            selected.clear();
            List<InkDocument.Stroke> strokes=document.strokes();
            for(int i=strokes.size()-1;i>=0;i--) {
                InkDocument.Stroke s=strokes.get(i); if(s.finished&&hit(s,p,8/scale)) { selected.add(s.id); gesture=Gesture.MOVE; break; }
            }
            original=bounds();
        }
        if(gesture==Gesture.NONE) { gesture=Gesture.LASSO; lasso.add(p); }
    }
    public void move(InkDocument.Point p,long now) {
        last=p;
        if(gesture==Gesture.LASSO) {
            InkDocument.Point previous=lasso.get(lasso.size()-1);
            if(Math.hypot(p.x-previous.x,p.y-previous.y)>2/scale) lasso.add(p);
        } else if(gesture!=Gesture.NONE&&Math.hypot(p.x-start.x,p.y-start.y)>2/scale) {
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
        else if(gesture==Gesture.LASSO&&lasso.size()>=3) {
            for(InkDocument.Stroke s:document.strokes()) if(s.finished&&selected.size()<4096&&s.points.stream().allMatch(p -> inside(p,lasso))) selected.add(s.id);
        }
        gesture=Gesture.NONE; lasso.clear();
    }
    private void preview() {
        float l=original[0],t=original[1],r=original[2],b=original[3],w=r-l,h=b-t;
        float sx=1,sy=1,tx=0,ty=0;
        if(gesture==Gesture.MOVE) {
            tx=clamp(last.x-start.x,-l,width-r); ty=clamp(last.y-start.y,-t,height-b);
        } else {
            float anchorX=handle.xSide<0?r:l,anchorY=handle.ySide<0?b:t;
            float maxX=Math.min(1000,(handle.xSide<0?anchorX:width-anchorX)/w);
            float maxY=Math.min(1000,(handle.ySide<0?anchorY:height-anchorY)/h);
            if(handle.xSide!=0) sx=clamp(handle.xSide*(last.x-anchorX)/w,Math.min(4/w,maxX),maxX);
            if(handle.ySide!=0) sy=clamp(handle.ySide*(last.y-anchorY)/h,Math.min(4/h,maxY),maxY);
            if(handle.corner()) {
                sx=sy=Math.min(Math.max(sx,sy),Math.min(maxX,maxY));
            }
            sx=clamp(sx,.001f,1000); sy=clamp(sy,.001f,1000);
            tx=clamp(anchorX*(1-sx),-l*sx,width-r*sx);
            ty=clamp(anchorY*(1-sy),-t*sy,height-b*sy);
        }
        emit.accept(Ink.object("type","edit_transform","id",id,"sx",sx,"sy",sy,"tx",tx,"ty",ty));
    }
    private static float clamp(float v,float min,float max) { return min>max?0:Math.max(min,Math.min(max,v)); }
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
