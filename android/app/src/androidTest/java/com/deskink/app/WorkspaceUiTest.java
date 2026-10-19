package com.deskink.app;

import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.view.View;
import android.widget.HorizontalScrollView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Native layout and reachability, complementary to the socket/codec test. */
@RunWith(AndroidJUnit4.class)
public class WorkspaceUiTest {
    static void capture(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Thread.sleep(250); // Let the surface compositor present the completed layout.
        try(android.os.ParcelFileDescriptor capture=InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("screencap -p /sdcard/Download/deskink-android-"+name+".png");
            java.io.InputStream output=new android.os.ParcelFileDescriptor.AutoCloseInputStream(capture)) {
            byte[] discard=new byte[1024]; while(output.read(discard)!=-1) { }
        }
    }
    @Test public void workspaceControlsRemainOutsideInkAndReachableInBothOrientations() throws Exception {
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)) {
            for(int orientation:new int[]{ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}) {
                activity.onActivity(a -> a.setRequestedOrientation(orientation));
                Thread.sleep(600);
                activity.onActivity(a -> {
                    View root=a.getWindow().getDecorView();
                    InkCanvas canvas=root.findViewWithTag("ink");
                    Rect ink=new Rect(),dock=new Rect(),fit=new Rect();
                    assertTrue(canvas.getGlobalVisibleRect(ink));
                    assertTrue(root.findViewWithTag("toolbar").getGlobalVisibleRect(dock));
                    assertTrue(root.findViewWithTag("fit").getGlobalVisibleRect(fit));
                    assertTrue("Dock must stay below the ink viewport",ink.bottom<=dock.top);
                    assertTrue("Header must stay above the ink viewport",fit.bottom<=ink.top);
                    HorizontalScrollView scroll=root.findViewWithTag("toolbar.scroll"); scroll.setSmoothScrollingEnabled(false); scroll.fullScroll(View.FOCUS_RIGHT);
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                activity.onActivity(a -> {
                    View clear=a.getWindow().getDecorView().findViewWithTag("clear"); Rect visible=new Rect();
                    assertTrue(clear.getGlobalVisibleRect(visible));
                    float density=a.getResources().getDisplayMetrics().density;
                    assertTrue("Clear needs a 48dp touch target",visible.width()/density>=48&&visible.height()/density>=48);
                    HorizontalScrollView scroll=a.getWindow().getDecorView().findViewWithTag("toolbar.scroll"); scroll.fullScroll(View.FOCUS_LEFT);
                });
                capture(orientation==ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE?"welcome-landscape":"welcome-portrait");
            }
            activity.onActivity(a -> a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            Thread.sleep(600);
            activity.onActivity(a -> a.getWindow().getDecorView().findViewWithTag("pair").performClick());
            capture("pairing");
        }
    }
}
