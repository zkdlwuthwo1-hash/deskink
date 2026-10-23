package com.deskink.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.view.MotionEvent;
import com.deskink.core.InkDocument;
import com.deskink.core.PenInput;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class WorkspaceToolsTest {
    @Test public void eraserModeCanBeSelectedAndRememberedWithoutChangingPenWidth() {
        try(org.robolectric.android.controller.ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup().visible()) {
            WorkspaceView workspace=new WorkspaceView(controller.get(),() -> {},() -> {}); controller.get().setContentView(workspace);
            java.util.List<org.json.JSONObject> sent=new java.util.ArrayList<>();
            workspace.ink.bind(new InkDocument(),c -> { sent.add(c); return true; },1000,700); workspace.ink.erasingEnabled(true);
            workspace.findViewWithTag("tool.ERASER").performClick();
            ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView().findViewWithTag("eraser.partial").performClick();
            View menu=ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView();
            assertTrue(menu.findViewWithTag("eraser.partial").isSelected());
            menu.findViewWithTag("eraser.size.24").performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            workspace.ink.layout(0,0,1000,700); workspace.ink.onWindowFocusChanged(true);
            InkCanvasTest.touch(workspace.ink,MotionEvent.ACTION_DOWN,0,200,200);
            InkCanvasTest.touch(workspace.ink,MotionEvent.ACTION_UP,0,200,200);
            org.json.JSONObject erase=sent.stream().filter(c -> c.optString("type").equals("erase_partial_begin")).findFirst().orElseThrow();
            assertEquals(24,erase.optDouble("radius"),.01);
            WorkspaceView reopened=new WorkspaceView(controller.get(),() -> {},() -> {});
            reopened.ink.bind(new InkDocument(),c -> true,1000,700); reopened.ink.erasingEnabled(true);
            reopened.findViewWithTag("tool.ERASER").performClick();
            assertTrue(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView().findViewWithTag("eraser.partial").isSelected());
            reopened.closeSheet(); workspace.closeSheet();
        }
    }
    @Test public void eraserOpensItsOwnSizesAndWidthOnlyOffersPenWidths() {
        try(org.robolectric.android.controller.ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup().visible()) {
            WorkspaceView workspace=new WorkspaceView(controller.get(),() -> {},() -> {});
            controller.get().setContentView(workspace);
            workspace.ink.layout(0,0,1000,700);
            java.util.List<org.json.JSONObject> sent=new java.util.ArrayList<>();
            workspace.ink.bind(new InkDocument(),command -> { sent.add(command); return true; },1920,1080);
            workspace.ink.editingEnabled(true);
            View eraser=workspace.findViewWithTag("tool.ERASER");
            assertTrue(eraser.performClick());
            AlertDialog sizes=ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull("Tapping eraser must expose its three pointer sizes",sizes);
            // Android's real AlertDialog takes focus from the canvas. Do not
            // skip that public lifecycle event in the JVM view fixture.
            workspace.ink.onWindowFocusChanged(false);
            for(int radius:new int[]{12,24,48}) assertNotNull(sizes.getWindow().getDecorView().findViewWithTag("eraser.size."+radius));
            sizes.getWindow().getDecorView().findViewWithTag("eraser.size.48").performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(PenInput.Tool.ERASER,workspace.ink.tool());
            assertFalse(sizes.isShowing());
            workspace.ink.onWindowFocusChanged(true);
            float x=workspace.ink.getWidth()/2f,y=workspace.ink.getHeight()/2f;
            assertTrue("The test pen must touch the displayed desktop",workspace.ink.viewport.contains(x,y));
            InkCanvasTest.touch(workspace.ink,MotionEvent.ACTION_DOWN,0,x,y);
            org.json.JSONObject erase=sent.stream().filter(c -> c.optString("type").equals("erase_begin")).findFirst().orElseThrow();
            assertEquals(48,erase.optDouble("radius"),.01);
            InkCanvasTest.touch(workspace.ink,MotionEvent.ACTION_UP,0,x,y);
            assertEquals(PenInput.Tool.PEN,workspace.ink.tool());
            eraser.performClick();
            sizes=ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(sizes.getWindow().getDecorView().findViewWithTag("eraser.size.48").isSelected());
            workspace.closeSheet();
            workspace.findViewWithTag("width").performClick();
            View widths=ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView();
            for(int width:new int[]{2,4,8}) assertNotNull(widths.findViewWithTag("width."+width));
            assertNull("Pen width must not expose eraser settings",widths.findViewWithTag("eraser.size.48"));
            workspace.closeSheet();
            workspace.ink.disconnect();
        }
    }
}
