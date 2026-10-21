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
    @Test public void networkAddressAndPortStayReachableOnTheTablet() throws Exception {
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)) {
            activity.onActivity(a -> a.getWindow().getDecorView().findViewWithTag("network").performClick());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            capture("network"); // Keep the actual layout even when a reachability assertion fails.
            activity.onActivity(a -> {
                boolean found=false;
                for(View window:android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    View address=window.findViewWithTag("network.address"); if(address==null) continue;
                    found=true; Rect visible=new Rect(); assertTrue("The initial PC address must be visible above the help",address.getGlobalVisibleRect(visible));
                    android.widget.EditText port=window.findViewWithTag("network.port"); assertEquals("9876",port.getText().toString());
                    Rect available=new Rect(); window.getWindowVisibleDisplayFrame(available);
                    for(int id:new int[]{android.R.id.button1,android.R.id.button2,android.R.id.button3}) {
                        android.widget.Button action=window.findViewById(id);
                        if(action==null||action.getVisibility()!=View.VISIBLE) continue;
                        String label=action.getText().toString();
                        assertTrue(label+" must remain visible",action.getGlobalVisibleRect(visible));
                        assertEquals(label+" must not be clipped vertically",action.getHeight(),visible.height());
                        assertEquals(label+" must not be clipped horizontally",action.getWidth(),visible.width());
                        assertTrue(label+" must fit inside the visible window",available.contains(visible));
                    }
                }
                assertTrue("IP entry must open from the toolbar",found);
            });
        }
    }
    static void capture(String name) throws Exception {
        name+=InstrumentationRegistry.getArguments().getString("captureSuffix","");
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
            activity.onActivity(a -> {
                for(View window:android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    android.widget.EditText code=window.findViewWithTag("pair.code");
                    if(code!=null) {
                        code.requestFocus(); code.setText("482691"); // Explicitly illustrative; never submitted.
                        a.getSystemService(android.view.inputmethod.InputMethodManager.class).showSoftInput(code,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            });
            java.util.concurrent.atomic.AtomicBoolean keyboardShown=new java.util.concurrent.atomic.AtomicBoolean();
            long deadline=android.os.SystemClock.uptimeMillis()+10000;
            while(!keyboardShown.get()&&android.os.SystemClock.uptimeMillis()<deadline) {
                activity.onActivity(a -> {
                    for(View window:android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                        if(window.findViewWithTag("pair.code")==null) continue;
                        android.view.WindowInsets insets=window.getRootWindowInsets();
                        keyboardShown.set(insets!=null&&insets.isVisible(android.view.WindowInsets.Type.ime()));
                    }
                });
                Thread.sleep(100);
            }
            assertTrue("Capture must wait until the software keyboard is actually visible",keyboardShown.get());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            activity.onActivity(a -> {
                assertFalse("Pairing must keep its instructions visible instead of using a fullscreen editor",
                    a.getSystemService(android.view.inputmethod.InputMethodManager.class).isFullscreenMode());
                for(View window:android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    View code=window.findViewWithTag("pair.code"); if(code==null) continue;
                    Rect input=new Rect(),action=new Rect(),available=new Rect();
                    window.getWindowVisibleDisplayFrame(available);
                    assertTrue("Pairing input stays reachable with the keyboard open",code.getGlobalVisibleRect(input));
                    View connect=window.findViewById(android.R.id.button1);
                    assertTrue(connect.getGlobalVisibleRect(action));
                    assertTrue("Connect must remain above the keyboard",available.contains(action));
                    assertEquals("Connect must remain fully visible",connect.getHeight(),action.height());
                }
            });
            capture("pairing-keyboard");
        }
    }
}
