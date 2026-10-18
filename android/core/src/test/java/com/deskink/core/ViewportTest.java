package com.deskink.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class ViewportTest {
    @Test public void tabletZoomKeepsTheFocusOnTheSameWindowsPointAndPansWithoutChangingDesktopSize() {
        Viewport v=new Viewport(); v.resize(1000,700,1920,1080);
        float[] before=v.desktop(500,350);
        assertArrayEquals(new float[]{960,540},before,.001f);
        v.gesture(2,500,350,500,350);
        assertArrayEquals(before,v.desktop(500,350),.001f);
        v.gesture(1,500,350,600,350);
        assertArrayEquals(before,v.desktop(600,350),.001f);
        assertEquals(2,v.zoom(),.001);
        v.reset(); assertEquals(1000,1920*v.scale(),.001);
        assertArrayEquals(before,v.desktop(500,350),.001f);
    }
}
