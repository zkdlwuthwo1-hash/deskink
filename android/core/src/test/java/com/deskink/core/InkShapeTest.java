package com.deskink.core;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class InkShapeTest {
    @Test public void aHandDrawnBoxWithASmallClosingGapIsStillARectangle() {
        InkShape box=InkShape.recognize(points(100,100,300,100,300,300,100,300,100,160));
        assertNotNull("Four almost straight sides should correct while holding",box);
        assertEquals("rectangle",box.kind);
    }
    @Test public void aTiltedRectangleIsNotMistakenForAnOval() {
        InkShape box=InkShape.recognize(points(200,100,400,300,300,400,100,200,200,100));
        assertNotNull(box);
        assertEquals("rectangle",box.kind);
    }

    @Test public void tiltedAndRoundedBoxesStayDistinctFromCirclesOvalsAndHandwriting() throws Exception {
        org.json.JSONArray fixtures;
        try(java.io.InputStream input=getClass().getResourceAsStream("/deskink-shape-cases.json")) {
            assertNotNull(input);
            fixtures=new org.json.JSONArray(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        }
        for(int i=0;i<fixtures.length();i++) {
            org.json.JSONObject fixture=fixtures.getJSONObject(i);
            for(int degrees:new int[]{-15,-10,0,10,15}) for(float scale:new float[]{.5f,1,3}) {
                double angle=Math.toRadians(degrees),c=Math.cos(angle),s=Math.sin(angle);
                List<InkDocument.Point> path=new ArrayList<>();
                org.json.JSONArray coordinates=fixture.getJSONArray("points");
                for(int j=0;j<coordinates.length();j++) {
                    org.json.JSONArray xy=coordinates.getJSONArray(j); double x=xy.getDouble(0)-200,y=xy.getDouble(1)-200;
                    path.add(new InkDocument.Point((float)(600+scale*(x*c-y*s)),(float)(600+scale*(x*s+y*c)),.5f));
                }
                InkShape shape=InkShape.recognize(path);
                String expected=fixture.isNull("expected")?null:fixture.getString("expected");
                // Rotated elongated ellipses were not always recognized before
                // this fix. They may retain their ink, but must never become boxes.
                if(degrees!=0&&fixture.optBoolean("allowUnrecognizedWhenTilted")&&shape==null) continue;
                assertEquals(fixture.getString("name")+" at "+degrees+" degrees, scale "+scale,expected,shape==null?null:shape.kind);
            }
        }
    }

    private static List<InkDocument.Point> points(float... xy) {
        List<InkDocument.Point> result=new ArrayList<>(); for(int i=0;i<xy.length;i+=2) result.add(new InkDocument.Point(xy[i],xy[i+1],.5f)); return result;
    }
    @Test public void recognizesANearlyStraightLineAndAnOvalButLeavesHandwritingUnchanged() {
        InkShape line=InkShape.recognize(points(100,100,150,101,200,99,250,100));
        assertNotNull(line); assertEquals("line",line.kind); assertEquals(250,line.b.x,.01);
        InkShape oval=InkShape.recognize(points(300,150,292,169,271,185,238,196,200,200,162,196,129,185,108,169,100,150,108,131,129,115,162,104,200,100,238,104,271,115,292,131,300,150));
        assertNotNull(oval); assertEquals("ellipse",oval.kind); assertEquals(100,oval.a.x,.01); assertEquals(200,oval.b.y,.01);
        assertNull(InkShape.recognize(points(100,100,140,200,180,100,220,200,260,100)));
        assertNull(InkShape.recognize(points(100,100,100,200,200,200))); // unfinished box
        assertNull(InkShape.recognize(points(100,100,104,101,103,104,100,104,100,100))); // punctuation
    }
}
