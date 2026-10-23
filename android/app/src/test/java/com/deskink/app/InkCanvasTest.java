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
    @Test public void unsupportedPartialEraserEndsThePenBeforeAButtonPressAndReportsWhy() {
        InkCanvas canvas=new InkCanvas(RuntimeEnvironment.getApplication()); canvas.layout(0,0,1000,700);
        List<JSONObject> sent=new ArrayList<>(); InkDocument d=new InkDocument();
        canvas.bind(d,c -> { sent.add(c); return true; },1000,700);
        canvas.editingEnabled(true); canvas.partialEraser(true);
        touch(canvas,MotionEvent.ACTION_DOWN,0,100,100);
        touch(canvas,MotionEvent.ACTION_MOVE,0,200,100);
        touch(canvas,MotionEvent.ACTION_MOVE,MotionEvent.BUTTON_STYLUS_PRIMARY,300,100);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));
        assertTrue("The pen stroke must end before blocking an unsupported eraser",d.strokes().get(0).finished);
        assertNull("The blocked eraser must cancel the pen's shape hold",d.strokes().get(0).shape);
        assertEquals(canvas.getContext().getString(R.string.erasing_update_required),org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        touch(canvas,MotionEvent.ACTION_MOVE,MotionEvent.BUTTON_STYLUS_PRIMARY,400,100);
        touch(canvas,MotionEvent.ACTION_MOVE,0,500,100);
        touch(canvas,MotionEvent.ACTION_UP,0,600,100);
        assertEquals(2,d.strokes().size());
        assertEquals(200,d.strokes().get(0).points.get(1).x,.01);
        assertEquals(500,d.strokes().get(1).points.get(0).x,.01);
        assertFalse(sent.stream().anyMatch(c -> c.optString("type").startsWith("erase")));
    }
    @Test public void selectedInkShowsADeleteButtonAndDeletionIsUndoable() {
        InkCanvas canvas=new InkCanvas(RuntimeEnvironment.getApplication()); canvas.layout(0,0,1000,700);
        InkDocument d=new InkDocument();
        d.apply(Ink.begin(1,Ink.point(100,100,1),new int[]{255,80,40,255},4,false));
        d.apply(new InkShape("rectangle",new InkDocument.Point(100,100,1),new InkDocument.Point(300,300,1)).command(1)); d.apply(Ink.id("end",1));
        d.apply(Ink.begin(2,Ink.point(600,500,1),new int[]{255,80,40,255},4,false)); d.apply(Ink.id("end",2));
        canvas.bind(d,c -> true,1000,700); canvas.editingEnabled(true); canvas.erasingEnabled(true); canvas.select(PenInput.Tool.LASSO);
        touch(canvas,MotionEvent.ACTION_DOWN,0,200,200); touch(canvas,MotionEvent.ACTION_UP,0,200,200);
        View delete=canvas.findViewWithTag("selection.delete");
        assertEquals(View.VISIBLE,delete.getVisibility()); assertTrue(delete.isEnabled());
        // The existing eight handles still resize the same selected object.
        touch(canvas,MotionEvent.ACTION_DOWN,0,300,300); touch(canvas,MotionEvent.ACTION_MOVE,0,400,400); touch(canvas,MotionEvent.ACTION_UP,0,400,400);
        assertEquals(400,d.strokes().get(0).points.get(2).x,.01);
        delete.performClick(); assertEquals(1,d.strokes().size()); assertEquals(2,d.strokes().get(0).id);
        assertEquals(View.GONE,delete.getVisibility());
        canvas.command("undo"); assertEquals(2,d.strokes().size()); assertEquals(400,d.strokes().get(0).points.get(2).x,.01);
        canvas.command("undo"); assertEquals(300,d.strokes().get(0).points.get(2).x,.01);
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void partialEraserLeavesATransparentGapAndBarrelReleaseRestoresPen() {
        InkCanvas canvas=new InkCanvas(RuntimeEnvironment.getApplication()); canvas.layout(0,0,1000,700);
        InkDocument d=new InkDocument();
        d.apply(Ink.begin(1,Ink.point(100,100,1),new int[]{255,80,40,255},4,false));
        d.apply(Ink.at("append",1,Ink.point(300,100,1))); d.apply(Ink.id("end",1));
        List<JSONObject> sent=new ArrayList<>(); canvas.bind(d,c -> { sent.add(c); return true; },1000,700);
        canvas.erasingEnabled(true); canvas.partialEraser(true);
        touch(canvas,MotionEvent.ACTION_DOWN,MotionEvent.BUTTON_STYLUS_PRIMARY,200,100);
        touch(canvas,MotionEvent.ACTION_UP,MotionEvent.BUTTON_STYLUS_PRIMARY,200,100);
        // Physical button release is independent of pen lift.
        touch(canvas,MotionEvent.ACTION_DOWN,0,700,500); touch(canvas,MotionEvent.ACTION_UP,0,710,500);
        assertEquals(PenInput.Tool.PEN,canvas.tool());
        assertTrue(sent.stream().anyMatch(c -> c.optString("type").equals("erase_partial_begin")));
        assertEquals(new TreeSet<>(Collections.singletonList(2)),d.strokes().get(0).breaks);
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(1000,700,android.graphics.Bitmap.Config.ARGB_8888);
        canvas.draw(new android.graphics.Canvas(bitmap));
        assertEquals(0,android.graphics.Color.alpha(bitmap.getPixel(200,100)));
        assertTrue(android.graphics.Color.alpha(bitmap.getPixel(150,100))>200);
        assertTrue(android.graphics.Color.alpha(bitmap.getPixel(250,100))>200);
        bitmap.recycle();
    }
    @Test public void aSlightlyTiltedRectangleMustNotBecomeAnEllipse() {
        try(org.robolectric.android.controller.ActivityController<android.app.Activity> controller=Robolectric.buildActivity(android.app.Activity.class).setup().visible()) {
            InkCanvas canvas=new InkCanvas(controller.get()); controller.get().setContentView(canvas); canvas.layout(0,0,1000,700);
            InkDocument document=new InkDocument(); List<JSONObject> sent=new ArrayList<>();
            canvas.bind(document,c -> { sent.add(c); return true; },1000,700); canvas.editingEnabled(true);
            // A 200-point square tilted ten degrees. Its four sides remain straight.
            touch(canvas,MotionEvent.ACTION_DOWN,0,168.88f,134.15f);
            for(float[] p:new float[][]{{365.85f,168.88f},{331.12f,365.85f},{134.15f,331.12f},{168.88f,134.15f}})
                touch(canvas,MotionEvent.ACTION_MOVE,0,p[0],p[1]);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
            JSONObject shape=sent.stream().filter(c -> c.optString("type").equals("shape")).findFirst().orElse(null);
            assertNotNull(shape);
            assertEquals("A slightly tilted box must stay a rectangle", "rectangle",shape.optString("kind"));
            assertFalse("The corrected shape must already exist while the pen is down",document.strokes().get(0).finished);
            assertEquals(5,document.strokes().get(0).points.size());
            touch(canvas,MotionEvent.ACTION_UP,0,168.88f,134.15f);
            assertEquals(5,document.strokes().get(0).points.size());
        }
    }

    @Test public void aSmallHandTremorDoesNotRestartTheHalfSecondHold() {
        try(org.robolectric.android.controller.ActivityController<android.app.Activity> controller=Robolectric.buildActivity(android.app.Activity.class).setup().visible()) {
            InkCanvas canvas=new InkCanvas(controller.get()); controller.get().setContentView(canvas);
            // Complete the window's first layout before measuring a stationary
            // screen-space hold; a resize would legitimately change coordinates.
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            canvas.layout(0,0,1000,700);
            InkDocument document=new InkDocument(); List<JSONObject> sent=new ArrayList<>();
            canvas.bind(document,c -> { sent.add(c); return true; },1000,700); canvas.editingEnabled(true);
            touch(canvas,MotionEvent.ACTION_DOWN,0,100,200);
            touch(canvas,MotionEvent.ACTION_MOVE,0,300,200);
            float jitter=4*canvas.getResources().getDisplayMetrics().density;
            for(int sample=0;sample<4;sample++) {
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
                touch(canvas,MotionEvent.ACTION_MOVE,0,300+(sample%2==0?jitter:-jitter),200);
            }
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(99));
            assertFalse(sent.stream().anyMatch(c -> c.optString("type").equals("shape")));
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1));
            JSONObject shape=sent.stream().filter(c -> c.optString("type").equals("shape")).findFirst().orElse(null);
            assertNotNull("A small hand tremor must not extend the half-second hold indefinitely",shape);
            assertEquals("line",shape.optString("kind"));
            touch(canvas,MotionEvent.ACTION_UP,0,300,200);
        }
    }

    @Test public void aStationaryPenCanCorrectALineDespiteSubPixelHoldSamples() {
        try(org.robolectric.android.controller.ActivityController<android.app.Activity> controller=Robolectric.buildActivity(android.app.Activity.class).setup().visible()) {
            InkCanvas canvas=new InkCanvas(controller.get()); controller.get().setContentView(canvas);
            // Settle initial window layout before replaying subpixel samples.
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            canvas.layout(0,0,1000,700);
            InkDocument document=new InkDocument(); List<JSONObject> sent=new ArrayList<>();
            canvas.bind(document,c -> { sent.add(c); return true; },1000,700); canvas.editingEnabled(true);
            touch(canvas,MotionEvent.ACTION_DOWN,0,100,200);
            for(int x=120;x<=300;x+=20) touch(canvas,MotionEvent.ACTION_MOVE,0,x,200);
            for(int sample=0;sample<60;sample++) {
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(8));
                touch(canvas,MotionEvent.ACTION_MOVE,0,300+(sample%2==0?.4f:-.4f),200);
            }
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20));
            JSONObject shape=sent.stream().filter(c -> c.optString("type").equals("shape")).findFirst().orElse(null);
            assertNotNull("A half-second hold with less than one pixel of pen jitter must still correct the line",shape);
            assertEquals("line",shape.optString("kind"));
            touch(canvas,MotionEvent.ACTION_UP,0,300,200);
        }
    }

    @Test public void oldHostsDisabledCorrectionAndLiftingEarlyAlwaysKeepOrdinaryInk() {
        for(int mode=0;mode<3;mode++) try(org.robolectric.android.controller.ActivityController<android.app.Activity> controller=Robolectric.buildActivity(android.app.Activity.class).setup().visible()) {
            InkCanvas canvas=new InkCanvas(controller.get()); controller.get().setContentView(canvas); canvas.layout(0,0,1000,700);
            List<JSONObject> sent=new ArrayList<>(); canvas.bind(new InkDocument(),c -> { sent.add(c); return true; },1000,700);
            canvas.editingEnabled(mode!=0); canvas.shapeCorrection(mode!=1);
            touch(canvas,MotionEvent.ACTION_DOWN,0,100,100); touch(canvas,MotionEvent.ACTION_MOVE,0,300,100);
            if(mode==2) {
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
                touch(canvas,MotionEvent.ACTION_UP,0,300,100);
            }
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));
            assertFalse(sent.stream().anyMatch(c -> c.optString("type").equals("shape")));
            canvas.disconnect();
        }
    }
    @Test public void lassoMovesAndCornerResizesMultipleObjectsWithOneUndoPerGesture() {
        InkCanvas canvas=new InkCanvas(RuntimeEnvironment.getApplication()); canvas.layout(0,0,1000,700);
        InkDocument document=new InkDocument();
        for(int id=1;id<=2;id++) {
            document.apply(Ink.begin(id,Ink.point(id==1?100:300,100,1),new int[]{255,80,40,255},4,false));
            if(id==1) document.apply(Ink.object("type","shape","id",1,"kind","rectangle","a",Ink.point(100,100,1),"b",Ink.point(200,200,1)));
            else document.apply(Ink.at("append",id,Ink.point(400,200,1)));
            document.apply(Ink.id("end",id));
        }
        canvas.bind(document,c -> true,1000,700); canvas.editingEnabled(true); canvas.select(PenInput.Tool.LASSO);
        touch(canvas,MotionEvent.ACTION_DOWN,0,80,80);
        for(float[] p:new float[][]{{420,80},{420,220},{80,220},{80,80}}) touch(canvas,MotionEvent.ACTION_MOVE,0,p[0],p[1]);
        touch(canvas,MotionEvent.ACTION_UP,0,80,80);
        touch(canvas,MotionEvent.ACTION_DOWN,0,150,150); touch(canvas,MotionEvent.ACTION_MOVE,0,250,200); touch(canvas,MotionEvent.ACTION_UP,0,250,200);
        assertEquals(200,document.strokes().get(0).points.get(0).x,.01); assertEquals(150,document.strokes().get(0).points.get(0).y,.01);
        assertEquals(400,document.strokes().get(1).points.get(0).x,.01);
        touch(canvas,MotionEvent.ACTION_DOWN,0,500,250); touch(canvas,MotionEvent.ACTION_MOVE,0,650,300); touch(canvas,MotionEvent.ACTION_UP,0,650,300);
        assertEquals(650,document.strokes().get(1).points.get(1).x,.01); assertEquals(300,document.strokes().get(1).points.get(1).y,.01);
        canvas.command("undo"); assertEquals(500,document.strokes().get(1).points.get(1).x,.01);
        canvas.command("undo"); assertEquals(100,document.strokes().get(0).points.get(0).x,.01);
    }
    @Test public void aClosedPenStrokeBecomesAShapeOnlyAfterHalfASecondOfHolding() {
        try(org.robolectric.android.controller.ActivityController<android.app.Activity> controller=Robolectric.buildActivity(android.app.Activity.class).setup().visible()) {
        InkCanvas canvas=new InkCanvas(controller.get()); controller.get().setContentView(canvas); canvas.layout(0,0,1000,700);
        InkDocument document=new InkDocument(); List<JSONObject> sent=new ArrayList<>();
        canvas.bind(document,c -> { sent.add(c); return true; },1920,1080); canvas.editingEnabled(true);
        touch(canvas,MotionEvent.ACTION_DOWN,0,400,300);
        for(float[] point:new float[][]{{600,300},{600,500},{400,500},{400,300}}) touch(canvas,MotionEvent.ACTION_MOVE,0,point[0],point[1]);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(499));
        assertFalse(sent.stream().anyMatch(c -> c.optString("type").equals("shape")));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1));
        JSONObject shape=sent.stream().filter(c -> c.optString("type").equals("shape")).findFirst().orElseThrow();
        assertEquals("rectangle",shape.optString("kind"));
        assertFalse("Correction must happen before lifting",document.strokes().get(0).finished);
        touch(canvas,MotionEvent.ACTION_UP,0,410,310);
        assertEquals(5,document.strokes().get(0).points.size());
        canvas.command("undo"); assertTrue(document.strokes().isEmpty());
        }
    }
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
