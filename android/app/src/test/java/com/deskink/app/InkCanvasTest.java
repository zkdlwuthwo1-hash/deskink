package com.deskink.app;
import android.view.*;
import com.deskink.core.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class InkCanvasTest {
    @Test public void selectedEraserSizeControlsBarrelErasingAndItsVisiblePointer() {
        for(float radius:new float[]{12,24,48}) {
            InkCanvas canvas=new InkCanvas(RuntimeEnvironment.getApplication()); canvas.layout(0,0,1000,700);
            List<JSONObject> sent=new ArrayList<>();
            canvas.bind(new InkDocument(),c -> { sent.add(c); return true; },1920,1080);
            canvas.select(PenInput.Tool.HIGHLIGHTER); canvas.width(8); canvas.eraserRadius(radius);
            touch(canvas,MotionEvent.ACTION_DOWN,MotionEvent.BUTTON_STYLUS_PRIMARY,500,350);
            JSONObject erase=sent.stream().filter(c -> c.optString("type").equals("erase_begin")).findFirst().orElseThrow();
            JSONObject pointer=sent.stream().filter(c -> c.optString("type").equals("pointer")).findFirst().orElseThrow();
            assertEquals("Eraser size must not depend on the pen width",radius,erase.optDouble("radius"),.01);
            assertEquals("The pointer must show the actual erase range",radius,pointer.optDouble("radius"),.01);
            touch(canvas,MotionEvent.ACTION_MOVE,0,520,350);
            assertEquals(PenInput.Tool.HIGHLIGHTER,canvas.tool());
            touch(canvas,MotionEvent.ACTION_UP,0,520,350);
        }
    }
    @Test public void aPalmLandingAndLiftingDuringAStrokeDoesNotRestartThePenGesture() {
        InkCanvas canvas=new InkCanvas(RuntimeEnvironment.getApplication()); canvas.layout(0,0,1000,700);
        List<JSONObject> sent=new ArrayList<>();
        canvas.bind(new InkDocument(),c -> {sent.add(c); return true;},1920,1080);
        touch(canvas,MotionEvent.ACTION_DOWN,0,400,300);
        MotionEvent.PointerProperties pen=new MotionEvent.PointerProperties(); pen.id=0; pen.toolType=MotionEvent.TOOL_TYPE_STYLUS;
        MotionEvent.PointerProperties palm=new MotionEvent.PointerProperties(); palm.id=1; palm.toolType=MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords a=new MotionEvent.PointerCoords(); a.x=450; a.y=300; a.pressure=1;
        MotionEvent.PointerCoords b=new MotionEvent.PointerCoords(); b.x=650; b.y=500;
        for(int action:new int[]{MotionEvent.ACTION_POINTER_DOWN|(1<<8),MotionEvent.ACTION_MOVE,MotionEvent.ACTION_POINTER_UP|(1<<8)}) {
            MotionEvent event=MotionEvent.obtain(100,110,action,2,new MotionEvent.PointerProperties[]{pen,palm},
                new MotionEvent.PointerCoords[]{a,b},0,0,1,1,0,0,InputDevice.SOURCE_STYLUS,0);
            canvas.dispatchTouchEvent(event); event.recycle();
        }
        touch(canvas,MotionEvent.ACTION_UP,0,500,300);
        canvas.command("clear");
        List<JSONObject> gestures=new ArrayList<>();
        for(JSONObject c:sent) if(!c.optString("type").equals("pointer")) gestures.add(c);
        assertEquals(Arrays.asList("begin","append","append","end","clear"),
            gestures.stream().map(c -> c.optString("type")).collect(java.util.stream.Collectors.toList()));
        long id=gestures.get(0).optLong("id");
        for(int i=1;i<4;i++) assertEquals(id,gestures.get(i).optLong("id"));
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void nativeCanvasShowsInkImmediatelyAndClearRemovesIt() {
        InkCanvas canvas=new InkCanvas(RuntimeEnvironment.getApplication()); canvas.layout(0,0,1000,700);
        canvas.bind(new InkDocument(),c -> true,1920,1080);
        touch(canvas,MotionEvent.ACTION_DOWN,0,450,350); touch(canvas,MotionEvent.ACTION_UP,0,550,350);
        android.graphics.Bitmap pixels=android.graphics.Bitmap.createBitmap(1000,700,android.graphics.Bitmap.Config.ARGB_8888);
        canvas.draw(new android.graphics.Canvas(pixels));
        assertEquals(0xffff5028,pixels.getPixel(500,350));
        canvas.command("clear"); pixels.eraseColor(0); canvas.draw(new android.graphics.Canvas(pixels));
        assertEquals(0,pixels.getPixel(500,350));
    }
    static MotionEvent fingers(int action,float x1,float x2) {
        MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[2];
        MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[2];
        for(int i=0;i<2;i++) {
            props[i]=new MotionEvent.PointerProperties(); props[i].id=i; props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coords[i]=new MotionEvent.PointerCoords(); coords[i].x=i==0?x1:x2; coords[i].y=350;
        }
        return MotionEvent.obtain(100,110,action,2,props,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
    }
    @Test public void twoFingersZoomWithoutSendingInkAndPenUsesTheInverseTransform() {
        InkCanvas canvas=new InkCanvas(RuntimeEnvironment.getApplication()); canvas.layout(0,0,1000,700);
        List<JSONObject> sent=new ArrayList<>(); canvas.bind(new InkDocument(),c -> {sent.add(c); return true;},1920,1080);
        for(MotionEvent e:new MotionEvent[]{fingers(MotionEvent.ACTION_POINTER_DOWN|(1<<8),400,600),fingers(MotionEvent.ACTION_MOVE,300,700)}) {
            canvas.dispatchTouchEvent(e); e.recycle();
        }
        assertEquals(2,canvas.viewport.zoom(),.01); assertTrue(sent.isEmpty());
        touch(canvas,MotionEvent.ACTION_DOWN,0,500,350); touch(canvas,MotionEvent.ACTION_UP,0,500,350);
        JSONObject point=sent.get(0).optJSONObject("point");
        assertEquals(960,point.optDouble("x"),.01); assertEquals(540,point.optDouble("y"),.01);
    }
    static MotionEvent pen(int action,int buttons,float x,float y) {
        MotionEvent.PointerProperties p=new MotionEvent.PointerProperties(); p.id=0; p.toolType=MotionEvent.TOOL_TYPE_STYLUS;
        MotionEvent.PointerCoords c=new MotionEvent.PointerCoords(); c.x=x; c.y=y; c.pressure=1;
        return MotionEvent.obtain(100,110,action,1,new MotionEvent.PointerProperties[]{p},new MotionEvent.PointerCoords[]{c},
            0,buttons,1,1,0,0,InputDevice.SOURCE_STYLUS,0);
    }
    static void touch(InkCanvas canvas,int action,int buttons,float x,float y) {
        MotionEvent event=pen(action,buttons,x,y); canvas.dispatchTouchEvent(event); event.recycle();
    }
    @Test public void barrelButtonCanChangeToolDuringContactAndReleaseRestoresHighlighter() {
        InkCanvas canvas=new InkCanvas(RuntimeEnvironment.getApplication()); canvas.layout(0,0,1000,700);
        List<JSONObject> sent=new ArrayList<>(); InkDocument doc=new InkDocument();
        canvas.bind(doc,c -> { sent.add(c); return true; },1920,1080);
        canvas.select(PenInput.Tool.HIGHLIGHTER);
        touch(canvas,MotionEvent.ACTION_DOWN,0,400,300);
        touch(canvas,MotionEvent.ACTION_MOVE,MotionEvent.BUTTON_STYLUS_PRIMARY,500,350);
        assertEquals(PenInput.Tool.ERASER,canvas.tool());
        touch(canvas,MotionEvent.ACTION_MOVE,0,550,350);
        assertEquals(PenInput.Tool.HIGHLIGHTER,canvas.tool());
        touch(canvas,MotionEvent.ACTION_UP,0,600,350);
        List<String> gestures=new ArrayList<>();
        for(JSONObject c:sent) if(!c.optString("type").equals("pointer")) gestures.add(c.optString("type"));
        assertEquals(Arrays.asList("begin","end","erase_begin","erase_end","begin","append","end"),gestures);
    }
}
