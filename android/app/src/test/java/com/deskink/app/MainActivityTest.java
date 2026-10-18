package com.deskink.app;
import android.view.*;
import com.deskink.core.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class MainActivityTest {
    @Test public void foregroundActivityStartsUsbAndClosingItReleasesThePort() throws Exception {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create().start()) {
            java.net.Socket socket=null;
            for(int i=0;i<20&&socket==null;i++) {
                try { socket=new java.net.Socket("127.0.0.1",9877); } catch(java.io.IOException e) { Thread.sleep(50); }
            }
            assertNotNull("Foreground app must accept the PC USB tunnel",socket);
            socket.close(); controller.stop();
        }
        try(java.net.ServerSocket available=new java.net.ServerSocket()) {
            available.setReuseAddress(true); available.bind(new java.net.InetSocketAddress("127.0.0.1",9877));
        }
    }
    @Test public void toolbarSelectsToolsAndClearUndoControlsTheVisibleDocument() {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create()) {
            MainActivity activity=controller.get(); View root=activity.getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1200,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));
            root.layout(0,0,1200,800);
            InkCanvas canvas=root.findViewWithTag("ink"); InkDocument doc=new InkDocument();
            canvas.bind(doc,c -> true,1920,1080);
            root.findViewWithTag("tool.HIGHLIGHTER").performClick(); assertEquals(PenInput.Tool.HIGHLIGHTER,canvas.tool());
            InkCanvasTest.touch(canvas,MotionEvent.ACTION_DOWN,0,400,300);
            InkCanvasTest.touch(canvas,MotionEvent.ACTION_UP,0,500,300);
            assertEquals(1,doc.strokes().size());
            root.findViewWithTag("clear").performClick(); assertTrue(doc.strokes().isEmpty());
            root.findViewWithTag("undo").performClick(); assertEquals(1,doc.strokes().size());
            root.findViewWithTag("tool.ERASER").performClick(); assertEquals(PenInput.Tool.ERASER,canvas.tool());
        }
    }
}
