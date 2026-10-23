package com.deskink.core;
import org.json.*;
import java.util.*;

/** UI-thread-owned ink and history. Commands are the same on the tablet and PC. */
public final class InkDocument {
    public static final class Point {
        public final float x,y,pressure;
        public Point(float x,float y,float pressure) { this.x=x; this.y=y; this.pressure=pressure; }
        Point(JSONObject p) { x=(float)p.optDouble("x"); y=(float)p.optDouble("y"); pressure=(float)p.optDouble("pressure",1); }
    }
    public static final class Stroke {
        public final long id;
        public final int color;
        public final float width;
        public final List<Point> points=new ArrayList<>();
        public final Set<Integer> breaks=new TreeSet<>();
        public boolean finished;
        public long expires;
        public InkShape shape;
        Stroke(JSONObject c) {
            id=c.optLong("id"); JSONObject brush=c.optJSONObject("brush");
            JSONArray rgba=brush.optJSONArray("color");
            color=(rgba.optInt(3)<<24)|(rgba.optInt(0)<<16)|(rgba.optInt(1)<<8)|rgba.optInt(2);
            width=(float)brush.optDouble("width"); points.add(new Point(c.optJSONObject("point")));
        }
        private Stroke(Stroke source,List<Point> points) {
            id=source.id; color=source.color; width=source.width; this.points.addAll(points);
            finished=source.finished; expires=source.expires; shape=source.shape;
            breaks.addAll(source.breaks);
        }
    }
    private final List<Stroke> strokes=new ArrayList<>();
    private final List<Stroke> temporary=new ArrayList<>();
    public JSONObject pointer;
    private long now,pointerExpires;
    private final Deque<List<Stroke>> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private List<Stroke> eraseBefore;
    private long eraseId;
    private Point eraseLast;
    private float eraseRadius;
    private boolean partialErase,eraseChanged;
    private List<Stroke> editBefore;
    private final Set<Long> editIds=new LinkedHashSet<>();
    private long editId;
    private boolean editTransformed,editChanged,editDeleted;
    public boolean visible=true;
    public long revision,staticRevision;
    public List<Stroke> strokes() { return Collections.unmodifiableList(strokes); }
    public List<Stroke> temporary() { return Collections.unmodifiableList(temporary); }
    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public void advanceTime(long millis) {
        now=Math.max(now,millis);
        if(temporary.removeIf(s -> s.finished&&s.expires<=now)) revision++;
        if(pointer!=null&&pointerExpires<=now) { pointer=null; revision++; }
    }
    public void suspend() {
        if(editBefore!=null) apply(Ink.id("edit_end",editId));
        for(Stroke s:new ArrayList<>(strokes)) if(!s.finished) apply(Ink.id("end",s.id));
        if(eraseBefore!=null) apply(Ink.id("erase_end",eraseId));
        temporary.clear(); pointer=null; revision++;
    }
    private void remember(List<Stroke> before) {
        undo.addLast(before); redo.clear(); if(undo.size()>100) undo.removeFirst();
    }
    public void apply(JSONObject c) {
        String type=c.optString("type"); long id=c.optLong("id");
        if(editBefore!=null&&!type.startsWith("edit_")&&!type.equals("pointer")&&!type.equals("set_visible")) return;
        switch(type) {
            case "edit_begin":
                if(editBefore!=null||eraseBefore!=null||strokes.stream().anyMatch(s -> !s.finished)) return;
                editBefore=new ArrayList<>(strokes); editId=id; editIds.clear(); editTransformed=editChanged=editDeleted=false; break;
            case "edit_add":
                if(editBefore==null||editId!=id||editTransformed) return;
                JSONArray ids=c.optJSONArray("ids");
                if(ids==null||ids.length()==0||ids.length()>32||editIds.size()+ids.length()>4096) return;
                Set<Long> added=new LinkedHashSet<>();
                for(int i=0;i<ids.length();i++) {
                    long selected=ids.optLong(i);
                    if(editIds.contains(selected)||!added.add(selected)||strokes.stream().noneMatch(s -> s.id==selected)) return;
                }
                editIds.addAll(added); break;
            case "edit_transform":
                if(editBefore==null||editId!=id||editIds.isEmpty()||editDeleted) return;
                float sx=(float)c.optDouble("sx"),sy=(float)c.optDouble("sy"),tx=(float)c.optDouble("tx"),ty=(float)c.optDouble("ty");
                if(!Float.isFinite(sx)||!Float.isFinite(sy)||!Float.isFinite(tx)||!Float.isFinite(ty)||sx<.001f||sy<.001f||sx>1000||sy>1000) return;
                for(int i=0;i<editBefore.size();i++) {
                    Stroke original=editBefore.get(i); if(!editIds.contains(original.id)) continue;
                    List<Point> transformed=new ArrayList<>(); for(Point p:original.points) transformed.add(transform(p,sx,sy,tx,ty));
                    Stroke replacement=new Stroke(original,transformed);
                    if(original.shape!=null) replacement.shape=new InkShape(original.shape.kind,transform(original.shape.a,sx,sy,tx,ty),transform(original.shape.b,sx,sy,tx,ty));
                    strokes.set(i,replacement);
                }
                editTransformed=true; editChanged=sx!=1||sy!=1||tx!=0||ty!=0; break;
            case "edit_delete":
                if(editBefore==null||editId!=id||editIds.isEmpty()||editDeleted) return;
                strokes.removeIf(s -> editIds.contains(s.id));
                editDeleted=editTransformed=editChanged=true; break;
            case "edit_end":
                if(editBefore==null||editId!=id) return;
                if(editChanged) remember(editBefore);
                editBefore=null; editIds.clear(); break;
            case "shape":
                for(int i=0;i<strokes.size();i++) {
                    Stroke old=strokes.get(i); if(old.id!=id||old.finished) continue;
                    InkShape shape=new InkShape(c.optString("kind"),new Point(c.optJSONObject("a")),new Point(c.optJSONObject("b")));
                    List<Point> points=shape.points(); if(points.isEmpty()) return;
                    Stroke corrected=new Stroke(old,points); corrected.shape=shape; strokes.set(i,corrected); break;
                }
                break;
            case "pointer": pointer=c.isNull("point")?null:c; pointerExpires=now+750; break;
            case "transient_begin": temporary.add(new Stroke(c)); break;
            case "erase_partial_begin":
            case "erase_begin":
                if(eraseBefore!=null) return;
                eraseBefore=new ArrayList<>(strokes); eraseId=id;
                eraseLast=new Point(c.optJSONObject("point")); eraseRadius=(float)c.optDouble("radius");
                partialErase=type.equals("erase_partial_begin"); eraseChanged=false;
                erase(eraseLast,eraseLast); break;
            case "erase_to":
                if(eraseBefore==null||eraseId!=id) return;
                Point next=new Point(c.optJSONObject("point")); erase(eraseLast,next); eraseLast=next; break;
            case "erase_end":
                if(eraseBefore==null||eraseId!=id) return;
                if(eraseChanged||eraseBefore.size()!=strokes.size()) remember(eraseBefore);
                eraseBefore=null; break;
            case "begin": strokes.add(new Stroke(c)); redo.clear(); break;
            case "append":
                for(Stroke s:strokes) if(s.id==id&&!s.finished) { s.points.add(new Point(c.optJSONObject("point"))); break; }
                for(Stroke s:temporary) if(s.id==id&&!s.finished) { s.points.add(new Point(c.optJSONObject("point"))); break; }
                break;
            case "end":
                for(Stroke s:strokes) if(s.id==id&&!s.finished) {
                    s.finished=true; List<Stroke> before=new ArrayList<>(strokes); before.remove(s); remember(before); break;
                }
                for(Stroke s:temporary) if(s.id==id&&!s.finished) { s.finished=true; s.expires=now+2000; break; }
                break;
            case "undo": if(!undo.isEmpty()) { redo.addLast(new ArrayList<>(strokes)); strokes.clear(); strokes.addAll(undo.removeLast()); } break;
            case "redo": if(!redo.isEmpty()) { undo.addLast(new ArrayList<>(strokes)); strokes.clear(); strokes.addAll(redo.removeLast()); } break;
            case "clear": if(!strokes.isEmpty()) remember(new ArrayList<>(strokes)); strokes.clear(); temporary.clear(); pointer=null; break;
            case "set_visible": visible=c.optBoolean("visible"); break;
            default: return;
        }
        revision++;
        if(!type.equals("append")&&!type.equals("begin")&&!type.equals("pointer")&&!type.equals("transient_begin")&&!type.equals("set_visible")) staticRevision++;
    }
    private static Point transform(Point p,float sx,float sy,float tx,float ty) { return new Point(p.x*sx+tx,p.y*sy+ty,p.pressure); }
    private void erase(Point a,Point b) {
        if(partialErase) {
            for(int i=0;i<strokes.size();i++) {
                Stroke original=strokes.get(i); PartialEraser.Cut cut=PartialEraser.cut(original,a,b,eraseRadius);
                if(cut!=null) {
                    Stroke replacement=new Stroke(original,cut.points); replacement.shape=null;
                    replacement.breaks.clear(); replacement.breaks.addAll(cut.breaks);
                    strokes.set(i,replacement); eraseChanged=true;
                }
            }
            strokes.removeIf(s -> s.points.isEmpty()); return;
        }
        strokes.removeIf(s -> {
            float limit=(eraseRadius+s.width/2)*(eraseRadius+s.width/2);
            for(int i=0;i<s.points.size();i++) {
                if(i==0||s.breaks.contains(i)) { if(distance(s.points.get(i),a,b)<=limit) return true; }
                else if(segmentDistance(a,b,s.points.get(i-1),s.points.get(i))<=limit) return true;
            }
            return false;
        });
    }
    private static float distance(Point p,Point a,Point b) {
        float dx=b.x-a.x,dy=b.y-a.y,l=dx*dx+dy*dy;
        float t=l>0?Math.max(0,Math.min(1,((p.x-a.x)*dx+(p.y-a.y)*dy)/l)):0;
        float x=p.x-a.x-t*dx,y=p.y-a.y-t*dy; return x*x+y*y;
    }
    private static float segmentDistance(Point a,Point b,Point c,Point d) {
        float rx=b.x-a.x,ry=b.y-a.y,sx=d.x-c.x,sy=d.y-c.y,cross=rx*sy-ry*sx;
        if(Math.abs(cross)>1.1920929e-7f) {
            float t=((c.x-a.x)*sy-(c.y-a.y)*sx)/cross,u=((c.x-a.x)*ry-(c.y-a.y)*rx)/cross;
            if(t>=0&&t<=1&&u>=0&&u<=1) return 0;
        }
        return Math.min(Math.min(distance(a,c,d),distance(b,c,d)),Math.min(distance(c,a,b),distance(d,a,b)));
    }
}
