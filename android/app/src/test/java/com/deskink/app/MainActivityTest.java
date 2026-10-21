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
    @Test public void disconnectRemainsPausedAfterBackgroundingUntilAutomaticConnectionIsChosen() throws Exception {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create().start()) {
            MainActivity activity=controller.get();
            activity.getWindow().getDecorView().findViewWithTag("network").performClick();
            android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(View.VISIBLE,dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).getVisibility());
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).performClick();
            // AlertDialog Builder actions are Handler messages, unlike the
            // explicitly installed positive-button listener in the IP form.
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertFalse("The disconnect action must finish before backgrounding",dialog.isShowing());
            controller.stop().start();
            try(java.net.ServerSocket available=new java.net.ServerSocket()) {
                available.setReuseAddress(true); available.bind(new java.net.InetSocketAddress("127.0.0.1",9877));
            }
            activity.getWindow().getDecorView().findViewWithTag("network").performClick();
            dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(activity.getString(R.string.network_usb),dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).getText().toString());
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertFalse("The reconnect action must run before testing its socket",dialog.isShowing());
            java.net.Socket usb=null;
            for(int i=0;i<20&&usb==null;i++) {
                try { usb=new java.net.Socket("127.0.0.1",9877); } catch(java.io.IOException error) { Thread.sleep(50); }
            }
            assertNotNull("Explicit automatic reconnect must resume the USB listener",usb);
            usb.close(); controller.stop();
        }
    }
    @Test public void lanEntryShowsThePortAndRejectsAnInvalidAddressWithoutLeavingUsb() {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create()) {
            View network=controller.get().getWindow().getDecorView().findViewWithTag("network");
            assertNotNull("LAN connection must be available beside the USB controls",network); network.performClick();
            android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            View root=dialog.getWindow().getDecorView();
            android.widget.EditText address=root.findViewWithTag("network.address"),port=root.findViewWithTag("network.port");
            assertEquals("9876",port.getText().toString()); address.setText("999.1.1.1");
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(View.VISIBLE,root.findViewWithTag("network.error").getVisibility());
            assertTrue(dialog.isShowing()); dialog.dismiss();
        }
    }
    @Test @Config(qualifiers="mdpi")
    public void pairingErrorCanBeBroughtIntoViewInAShortWindow() {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create()) {
            controller.get().getWindow().getDecorView().findViewWithTag("pair").performClick();
            android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            View root=dialog.getWindow().getDecorView();
            android.widget.EditText code=root.findViewWithTag("pair.code"); code.setText("25");
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            root.measure(View.MeasureSpec.makeMeasureSpec(380,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(260,View.MeasureSpec.EXACTLY));
            root.layout(0,0,380,260);
            View error=root.findViewWithTag("pair.error");
            error.requestRectangleOnScreen(new android.graphics.Rect(0,0,error.getWidth(),error.getHeight()),true);
            android.graphics.Rect visible=new android.graphics.Rect();
            assertTrue("Pairing validation must remain reachable above the keyboard",error.getGlobalVisibleRect(visible));
            assertEquals("The full error needs to be readable",error.getHeight(),visible.height());
            dialog.dismiss();
        }
    }
    @Test public void historyButtonsReflectTheAvailableUndoAndRedoActions() {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create()) {
            View root=controller.get().getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1200,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));
            root.layout(0,0,1200,800);
            View undo=root.findViewWithTag("undo"),redo=root.findViewWithTag("redo");
            assertFalse("No document is connected yet",undo.isEnabled());
            InkCanvas canvas=root.findViewWithTag("ink"); canvas.bind(new InkDocument(),c -> true,1920,1080);
            assertFalse(undo.isEnabled()); assertFalse(redo.isEnabled());
            InkCanvasTest.touch(canvas,MotionEvent.ACTION_DOWN,0,400,300);
            InkCanvasTest.touch(canvas,MotionEvent.ACTION_UP,0,500,300);
            assertTrue(undo.isEnabled()); assertFalse(redo.isEnabled());
            undo.performClick(); assertFalse(undo.isEnabled()); assertTrue(redo.isEnabled());
            redo.performClick(); assertTrue(undo.isEnabled()); assertFalse(redo.isEnabled());
        }
    }
    @Test public void visibilityControlFollowsDrawingThatAutomaticallyShowsHiddenInk() {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create()) {
            View root=controller.get().getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1200,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));
            root.layout(0,0,1200,800);
            InkCanvas canvas=root.findViewWithTag("ink"); InkDocument document=new InkDocument();
            canvas.bind(document,c -> true,1920,1080);
            View visibility=root.findViewWithTag("hide"); visibility.performClick();
            assertFalse(document.visible);
            InkCanvasTest.touch(canvas,MotionEvent.ACTION_DOWN,0,400,300);
            InkCanvasTest.touch(canvas,MotionEvent.ACTION_UP,0,500,300);
            assertTrue(document.visible);
            visibility.performClick();
            assertFalse("The next tap must hide the ink that drawing made visible again",document.visible);
        }
    }
    @Test public void selectedToolTracksTheSPenButtonAndReturnsToThePreviousTool() {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create()) {
            View root=controller.get().getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1200,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));
            root.layout(0,0,1200,800);
            InkCanvas canvas=root.findViewWithTag("ink"); canvas.bind(new InkDocument(),c -> true,1920,1080);
            View highlighter=root.findViewWithTag("tool.HIGHLIGHTER"),eraser=root.findViewWithTag("tool.ERASER");
            highlighter.performClick();
            assertTrue("The selected tool must be exposed to accessibility and visible feedback",highlighter.isSelected());
            InkCanvasTest.touch(canvas,MotionEvent.ACTION_DOWN,0,400,300);
            InkCanvasTest.touch(canvas,MotionEvent.ACTION_MOVE,MotionEvent.BUTTON_STYLUS_PRIMARY,450,300);
            assertTrue(eraser.isSelected()); assertFalse(highlighter.isSelected());
            InkCanvasTest.touch(canvas,MotionEvent.ACTION_MOVE,0,500,300);
            assertTrue(highlighter.isSelected()); assertFalse(eraser.isSelected());
            InkCanvasTest.touch(canvas,MotionEvent.ACTION_UP,0,500,300);
        }
    }
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
