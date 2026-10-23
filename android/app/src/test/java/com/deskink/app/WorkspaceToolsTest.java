package com.deskink.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
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
    @Test public void eraserOpensItsOwnSizesAndWidthOnlyOffersPenWidths() {
        try(org.robolectric.android.controller.ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup().visible()) {
            WorkspaceView workspace=new WorkspaceView(controller.get(),() -> {},() -> {});
            controller.get().setContentView(workspace);
            workspace.ink.bind(new InkDocument(),command -> true,1920,1080);
            workspace.ink.editingEnabled(true);
            View eraser=workspace.findViewWithTag("tool.ERASER");
            assertTrue(eraser.performClick());
            AlertDialog sizes=ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull("Tapping eraser must expose its three pointer sizes",sizes);
            for(int radius:new int[]{12,24,48}) assertNotNull(sizes.getWindow().getDecorView().findViewWithTag("eraser.size."+radius));
            sizes.getWindow().getDecorView().findViewWithTag("eraser.size.48").performClick();
            assertEquals(PenInput.Tool.ERASER,workspace.ink.tool());
            assertFalse(sizes.isShowing());
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
