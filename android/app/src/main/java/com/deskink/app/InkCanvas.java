package com.deskink.app;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Button;
import com.deskink.core.*;
import org.json.*;
import java.util.function.Predicate;
import java.util.*;

/** Native stylus input; fingers never generate Windows ink or mouse events. */
public final class InkCanvas extends FrameLayout {
    private final PenInput pen=new PenInput();
    public final Viewport viewport=new Viewport();
    private InkDocument document=new InkDocument();
    private InkSelection selection;
    private Predicate<JSONObject> sender;
    private int desktopWidth=1920,desktopHeight=1080;
    private long nextId=System.currentTimeMillis(),gestureId;
    private PenInput.Tool activeTool;
    private int pointerId=-1;
    private int[] color={255,80,40,255};
    private float width=4;
    private float eraserRadius=12;
    private boolean editingEnabled,shapeCorrection=true,shapeFrozen;
    private boolean erasingEnabled,partialEraser;
    private final Button deleteSelection;
    private float holdX,holdY;
    private int strokePointCount,holdPointCount;
    private final Runnable hold=() -> {
        if(!editingEnabled||!shapeCorrection||shapeFrozen||pointerId<0||(activeTool!=PenInput.Tool.PEN&&activeTool!=PenInput.Tool.HIGHLIGHTER)) return;
        for(InkDocument.Stroke stroke:document.strokes()) if(stroke.id==gestureId&&!stroke.finished) {
            // Keep live ink unchanged, but do not mistake a stationary pen's
            // dense jitter samples for extra travel around the drawn shape.
            InkShape shape=InkShape.recognize(stroke.points.subList(0,Math.min(holdPointCount,stroke.points.size())));
            if(shape!=null) { shapeFrozen=true; emit(shape.command(gestureId)); }
            break;
        }
    };
    private float fingerX,fingerY,fingerSpan;
    private int fingerA=-1,fingerB=-1;
    public Runnable stateChanged=() -> {};
    public Runnable transformChanged=() -> {};
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<InkDocument.Stroke,StrokePath> paths=new IdentityHashMap<>();
    private Bitmap completedInk;
    private long cachedRevision=-1;
    private float cachedScale,cachedX,cachedY;
    private static final class StrokePath { final Path path=new Path(); int count; }
    public InkCanvas(Context context) {
        super(context); setWillNotDraw(false); setFocusable(true); setContentDescription("DeskInk drawing surface");
        deleteSelection=new Button(context); deleteSelection.setText(R.string.delete_selection);
        deleteSelection.setTextColor(0xffffb4ab); deleteSelection.setAllCaps(false); deleteSelection.setTextSize(13);
        deleteSelection.setTag("selection.delete"); deleteSelection.setContentDescription(context.getString(R.string.delete_selection));
        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();
        background.setColor(0xee252932); background.setCornerRadius(20*getResources().getDisplayMetrics().density);
        deleteSelection.setBackground(background); deleteSelection.setVisibility(GONE);
        float density=getResources().getDisplayMetrics().density;
        addView(deleteSelection,new FrameLayout.LayoutParams((int)(132*density),(int)(44*density)));
        deleteSelection.setOnClickListener(v -> {
            if(!erasingEnabled||sender==null||selection==null) return;
            finish(true); selection.deleteSelected(++nextId); updateSelectionControls(); invalidate(); stateChanged.run();
        });
    }
    public void bind(InkDocument document,Predicate<JSONObject> sender,int w,int h) {
        disconnect(); this.document=document; this.sender=sender; desktopWidth=w; desktopHeight=h;
        paths.clear(); cachedRevision=-1;
        viewport.resize(getWidth(),getHeight(),w,h); transformChanged.run(); stateChanged.run(); invalidate();
        selection=new InkSelection(document,w,h,this::emit);
    }
    public void disconnect() { finish(true); pen.cancel(); pointerId=-1; sender=null; document.suspend(); stateChanged.run(); invalidate(); }
    public PenInput.Tool tool() { return pen.tool(); }
    public boolean inkVisible() { return document.visible; }
    public boolean connected() { return sender!=null; }
    public boolean canUndo() { return document.canUndo(); }
    public boolean canRedo() { return document.canRedo(); }
    public void select(PenInput.Tool tool) { finish(true); if(tool==PenInput.Tool.LASSO&&!editingEnabled) return; if(selection!=null) selection.clear(); pen.select(tool); updateSelectionControls(); stateChanged.run(); invalidate(); }
    public void color(int[] rgba) { color=rgba.clone(); }
    public void width(float value) { width=value; }
    public void eraserRadius(float value) { eraserRadius=value==24||value==48?value:12; }
    public void erasingEnabled(boolean value) { erasingEnabled=value; updateSelectionControls(); stateChanged.run(); }
    public boolean erasingEnabled() { return erasingEnabled; }
    public void partialEraser(boolean value) { finish(true); partialEraser=value; }
    public void editingEnabled(boolean value) {
        editingEnabled=value;
        if(!value) { removeCallbacks(hold); if(selection!=null) selection.clear(); if(pen.tool()==PenInput.Tool.LASSO) select(PenInput.Tool.PEN); }
        stateChanged.run();
    }
    public boolean editingEnabled() { return editingEnabled; }
    public void shapeCorrection(boolean value) { shapeCorrection=value; if(!value) removeCallbacks(hold); }
    public boolean shapeCorrection() { return shapeCorrection; }
    public void command(String type) { finish(true); if(selection!=null) selection.clear(); emit(Ink.command(type)); updateSelectionControls(); }
    public void visible(boolean visible) { finish(true); emit(Ink.object("type","set_visible","visible",visible)); }
    private void emit(JSONObject command) {
        if(sender==null) return;
        if(!sender.test(command)) { sender=null; document.suspend(); stateChanged.run(); return; }
        document.advanceTime(SystemClock.uptimeMillis()); document.apply(command); invalidate();
        updateSelectionControls();
        String type=command.optString("type");
        if(!type.equals("append")&&!type.equals("pointer")&&!type.equals("erase_to")) stateChanged.run();
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
        viewport.resize(w,h,desktopWidth,desktopHeight);
        transformChanged.run();
        updateSelectionControls();
    }
    public void resetZoom() { viewport.reset(); transformChanged.run(); updateSelectionControls(); invalidate(); }
    private void fingers(MotionEvent e) {
        int a=-1,b=-1;
        for(int i=0;i<e.getPointerCount();i++) if(e.getToolType(i)==MotionEvent.TOOL_TYPE_FINGER) {
            if(a<0) a=i; else if(b<0) b=i;
        }
        int action=e.getActionMasked();
        if(a<0||b<0||pointerId>=0||action==MotionEvent.ACTION_POINTER_UP||action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL) {
            fingerA=fingerB=-1; return;
        }
        float x=(e.getX(a)+e.getX(b))/2,y=(e.getY(a)+e.getY(b))/2;
        float span=(float)Math.hypot(e.getX(a)-e.getX(b),e.getY(a)-e.getY(b));
        if(fingerA==e.getPointerId(a)&&fingerB==e.getPointerId(b)&&action==MotionEvent.ACTION_MOVE&&fingerSpan>1) {
            viewport.gesture(span/fingerSpan,fingerX,fingerY,x,y); transformChanged.run(); updateSelectionControls(); invalidate();
        }
        fingerA=e.getPointerId(a); fingerB=e.getPointerId(b); fingerX=x; fingerY=y; fingerSpan=span;
    }
    private JSONObject point(float x,float y,float pressure) {
        float[] p=viewport.desktop(x,y);
        return Ink.point(Math.max(0,Math.min(desktopWidth,p[0])),Math.max(0,Math.min(desktopHeight,p[1])),pressure);
    }
    private void begin(PenInput.Tool tool,JSONObject p) {
        activeTool=tool; gestureId=++nextId; shapeFrozen=false; strokePointCount=1;
        if(tool==PenInput.Tool.LASSO) { selection.begin(selectionPoint(p),viewport.scale(),gestureId); updateSelectionControls(); invalidate(); return; }
        if(!document.visible) emit(Ink.object("type","set_visible","visible",true));
        if(tool==PenInput.Tool.ERASER) emit(Ink.object("type",partialEraser?"erase_partial_begin":"erase_begin","id",gestureId,"point",p,"radius",eraserRadius));
        else if(tool!=PenInput.Tool.LASER) {
            int[] rgba=color.clone(); if(tool==PenInput.Tool.HIGHLIGHTER) rgba[3]=80;
            emit(Ink.begin(gestureId,p,rgba,tool==PenInput.Tool.HIGHLIGHTER?width*5:width,tool==PenInput.Tool.VANISHING));
        }
        armHold(p,true);
    }
    private void armHold(JSONObject p,boolean force) {
        if(!editingEnabled||!shapeCorrection||shapeFrozen||(activeTool!=PenInput.Tool.PEN&&activeTool!=PenInput.Tool.HIGHLIGHTER)) return;
        float x=(float)p.optDouble("x"),y=(float)p.optDouble("y");
        // Eight logical screen pixels, independent of display density or zoom.
        // Keep the half-second deadline despite small endpoint tremor.
        float holdSlop=8*getResources().getDisplayMetrics().density/viewport.scale();
        if(force||Math.hypot(x-holdX,y-holdY)>holdSlop) {
            holdX=x; holdY=y; holdPointCount=strokePointCount; removeCallbacks(hold); postDelayed(hold,500);
        }
    }
    private void move(JSONObject p) {
        if(activeTool==PenInput.Tool.LASSO) { selection.move(selectionPoint(p),SystemClock.uptimeMillis()); updateSelectionControls(); invalidate(); return; }
        if(shapeFrozen) return;
        if(activeTool==PenInput.Tool.ERASER) emit(Ink.at("erase_to",gestureId,p));
        else if(activeTool!=PenInput.Tool.LASER) { strokePointCount++; emit(Ink.at("append",gestureId,p)); }
        armHold(p,false);
    }
    private void finish(boolean lift) {
        removeCallbacks(hold);
        if(activeTool!=null) {
            if(activeTool==PenInput.Tool.LASSO) { selection.end(); invalidate(); }
            else if(activeTool==PenInput.Tool.ERASER) emit(Ink.id("erase_end",gestureId));
            else if(activeTool!=PenInput.Tool.LASER) emit(Ink.id("end",gestureId));
            activeTool=null;
        }
        if(lift) { pen.contactEnded(); stateChanged.run(); }
        updateSelectionControls();
    }
    private static InkDocument.Point selectionPoint(JSONObject p) { return new InkDocument.Point((float)p.optDouble("x"),(float)p.optDouble("y"),(float)p.optDouble("pressure")); }
    private void cursor(JSONObject p,boolean laser) {
        boolean erasing=(activeTool==null?pen.tool():activeTool)==PenInput.Tool.ERASER;
        emit(Ink.object("type","pointer","point",p==null?JSONObject.NULL:p,"color",Ink.color(erasing?new int[]{235,240,250,255}:color),
            "radius",erasing?eraserRadius:laser?10:Math.max(2,width/2),"laser",laser));
    }
    private void buttons(MotionEvent e) {
        PenInput.Tool before=pen.tool();
        pen.button((e.getButtonState()&(MotionEvent.BUTTON_STYLUS_PRIMARY|MotionEvent.BUTTON_STYLUS_SECONDARY))!=0);
        if(before!=pen.tool()) stateChanged.run();
    }
    private static boolean stylus(MotionEvent e,int i) {
        return e.getToolType(i)==MotionEvent.TOOL_TYPE_STYLUS||e.getToolType(i)==MotionEvent.TOOL_TYPE_ERASER;
    }
    // This continuous stylus surface has no click action. Accessible actions
    // (tools, clear, undo, zoom reset) are ordinary toolbar buttons.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent e) {
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_CANCEL) { finish(true); pen.cancel(); stateChanged.run(); pointerId=-1; cursor(null,false); return true; }
        // Pointer transitions belong to the pointer that changed, not the pen
        // still present in the same event. A palm must never open a new stroke.
        if((action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_POINTER_UP)
                &&pointerId>=0&&e.getPointerId(e.getActionIndex())!=pointerId) return true;
        int i=pointerId<0?e.getActionIndex():e.findPointerIndex(pointerId);
        if(i<0||!stylus(e,i)) { fingers(e); return true; }
        buttons(e); if(sender==null) return true;
        PenInput.Tool tool=e.getToolType(i)==MotionEvent.TOOL_TYPE_ERASER?PenInput.Tool.ERASER:pen.tool();
        if(tool==PenInput.Tool.ERASER&&partialEraser&&!erasingEnabled) {
            if(action==MotionEvent.ACTION_DOWN) android.widget.Toast.makeText(getContext(),R.string.erasing_update_required,android.widget.Toast.LENGTH_SHORT).show();
            if(action==MotionEvent.ACTION_UP) { finish(true); pointerId=-1; }
            return true;
        }
        JSONObject p=point(e.getX(i),e.getY(i),e.getPressure(i));
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN) {
            if(!viewport.contains(e.getX(i),e.getY(i))) return true;
            pointerId=e.getPointerId(i); begin(tool,p);
        } else if(pointerId>=0&&(action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)) {
            if(activeTool!=tool) { finish(false); begin(tool,p); }
            else {
                for(int h=0;h<e.getHistorySize();h++) move(point(e.getHistoricalX(i,h),e.getHistoricalY(i,h),e.getHistoricalPressure(i,h)));
                move(p);
            }
        }
        cursor(p,tool==PenInput.Tool.LASER);
        if(action==MotionEvent.ACTION_UP||(action==MotionEvent.ACTION_POINTER_UP&&e.getActionIndex()==i)) { finish(true); pointerId=-1; }
        return true;
    }
    @Override public boolean onGenericMotionEvent(MotionEvent e) {
        if(!stylus(e,e.getActionIndex())) return super.onGenericMotionEvent(e);
        buttons(e);
        if(e.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT) cursor(null,false);
        else cursor(point(e.getX(),e.getY(),0),pen.tool()==PenInput.Tool.LASER);
        return true;
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if(!focused) { finish(true); pen.cancel(); stateChanged.run(); pointerId=-1; cursor(null,false); }
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); document.advanceTime(SystemClock.uptimeMillis());
        if(document.visible) {
            if(completedInk==null||completedInk.getWidth()!=getWidth()||completedInk.getHeight()!=getHeight()) {
                if(completedInk!=null) completedInk.recycle();
                completedInk=Bitmap.createBitmap(Math.max(1,getWidth()),Math.max(1,getHeight()),Bitmap.Config.ARGB_8888);
                cachedRevision=-1;
            }
            if(cachedRevision!=document.staticRevision||cachedScale!=viewport.scale()||cachedX!=viewport.x()||cachedY!=viewport.y()) {
                completedInk.eraseColor(Color.TRANSPARENT);
                Canvas cache=new Canvas(completedInk); transform(cache);
                for(InkDocument.Stroke s:document.strokes()) if(s.finished) stroke(cache,s);
                cachedRevision=document.staticRevision; cachedScale=viewport.scale(); cachedX=viewport.x(); cachedY=viewport.y();
                paths.keySet().removeIf(s -> !document.strokes().contains(s)&&!document.temporary().contains(s));
            }
            canvas.drawBitmap(completedInk,0,0,null);
        }
        canvas.save(); transform(canvas);
        if(document.visible) {
            for(InkDocument.Stroke s:document.strokes()) if(!s.finished) stroke(canvas,s);
            for(InkDocument.Stroke s:document.temporary()) stroke(canvas,s);
        }
        JSONObject cursor=document.pointer;
        if(selection!=null&&pen.tool()==PenInput.Tool.LASSO&&document.visible) drawSelection(canvas);
        if(cursor!=null) {
            JSONObject p=cursor.optJSONObject("point");
            if(p!=null) {
                float x=(float)p.optDouble("x"),y=(float)p.optDouble("y"),r=(float)cursor.optDouble("radius",3);
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f/viewport.scale()); paint.setColor(Color.WHITE);
                canvas.drawCircle(x,y,Math.max(r,4/viewport.scale()),paint);
                boolean erasing=(activeTool==null?pen.tool():activeTool)==PenInput.Tool.ERASER;
                paint.setStyle(Paint.Style.FILL); paint.setColor(erasing?Color.WHITE:Color.rgb(color[0],color[1],color[2]));
                if(cursor.optBoolean("laser")) { paint.setAlpha(70); canvas.drawCircle(x,y,r*1.8f,paint); paint.setAlpha(255); }
                if(erasing) paint.setAlpha(30);
                canvas.drawCircle(x,y,r,paint); paint.setAlpha(255);
            }
        }
        canvas.restore();
        if(document.pointer!=null||!document.temporary().isEmpty()) postInvalidateDelayed(32);
    }
    private void drawSelection(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE); paint.setColor(0xff8ab4ff); paint.setStrokeWidth(1.5f/viewport.scale());
        paint.setPathEffect(new DashPathEffect(new float[]{6/viewport.scale(),4/viewport.scale()},0));
        if(!selection.lasso().isEmpty()) {
            Path path=new Path(); boolean first=true;
            for(InkDocument.Point p:selection.lasso()) { if(first) { path.moveTo(p.x,p.y); first=false; } else path.lineTo(p.x,p.y); }
            canvas.drawPath(path,paint);
        }
        float[] b=selection.bounds();
        if(b!=null) {
            canvas.drawRect(b[0],b[1],b[2],b[3],paint); paint.setPathEffect(null);
            for(float[] p:InkSelection.handles(b)) {
                paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE); canvas.drawCircle(p[0],p[1],5/viewport.scale(),paint);
                paint.setStyle(Paint.Style.STROKE); paint.setColor(0xff8ab4ff); canvas.drawCircle(p[0],p[1],5/viewport.scale(),paint);
            }
        }
        paint.setPathEffect(null);
    }
    private void updateSelectionControls() {
        if(deleteSelection==null) return;
        float[] b=selection==null?null:selection.bounds();
        boolean show=b!=null&&sender!=null&&document.visible&&pen.tool()==PenInput.Tool.LASSO&&editingEnabled&&erasingEnabled;
        deleteSelection.setVisibility(show?VISIBLE:GONE);
        if(!show) return;
        float density=getResources().getDisplayMetrics().density,w=132*density,h=44*density,gap=16*density;
        float right=b[2]*viewport.scale()+viewport.x(),top=b[1]*viewport.scale()+viewport.y();
        float bottom=b[3]*viewport.scale()+viewport.y();
        deleteSelection.setX(Math.max(4*density,Math.min(getWidth()-w-4*density,right-w)));
        deleteSelection.setY(Math.max(4*density,Math.min(getHeight()-h-4*density,top>=h+gap?top-h-gap:bottom+gap)));
        deleteSelection.setEnabled(activeTool==null);
    }
    private void transform(Canvas canvas) {
        canvas.translate(viewport.x(),viewport.y()); canvas.scale(viewport.scale(),viewport.scale());
        canvas.clipRect(0,0,desktopWidth,desktopHeight);
    }
    private void stroke(Canvas canvas,InkDocument.Stroke stroke) {
        StrokePath shape=paths.computeIfAbsent(stroke,ignored -> new StrokePath());
        for(;shape.count<stroke.points.size();shape.count++) {
            InkDocument.Point p=stroke.points.get(shape.count);
            if(shape.count==0||stroke.breaks.contains(shape.count)) { shape.path.moveTo(p.x,p.y); shape.path.lineTo(p.x+.001f,p.y); }
            else shape.path.lineTo(p.x,p.y);
        }
        paint.setColor(stroke.color); paint.setStrokeWidth(stroke.width); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        if(stroke.points.size()==1) {
            InkDocument.Point p=stroke.points.get(0); paint.setStyle(Paint.Style.FILL); canvas.drawCircle(p.x,p.y,stroke.width/2,paint);
        } else { paint.setStyle(Paint.Style.STROKE); canvas.drawPath(shape.path,paint); }
    }
    @Override protected void onDetachedFromWindow() {
        finish(true); super.onDetachedFromWindow(); if(completedInk!=null) { completedInk.recycle(); completedInk=null; }
    }
}
