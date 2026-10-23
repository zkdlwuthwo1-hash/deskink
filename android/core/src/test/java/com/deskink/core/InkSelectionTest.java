package com.deskink.core;

import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class InkSelectionTest {
    private static InkDocument.Point p(float x,float y) { return new InkDocument.Point(x,y,1); }
    private static InkDocument line(float x1,float y1,float x2,float y2) {
        InkDocument doc=new InkDocument();
        doc.apply(Ink.begin(1,Ink.point(x1,y1,1),new int[]{255,80,40,255},4,false));
        doc.apply(new InkShape("line",p(x1,y1),p(x2,y2)).command(1)); doc.apply(Ink.id("end",1));
        return doc;
    }
    @Test public void resizingAnExtremelyThinSelectionStaysWithinTheSharedWireContract() {
        InkDocument doc=line(10,100,10,300); List<JSONObject> sent=new ArrayList<>();
        InkSelection selection=new InkSelection(doc,1920,1080,c -> { sent.add(c); doc.apply(c); });
        selection.begin(p(10,200),1,8); selection.end();
        selection.begin(p(10.5f,200),1,9); selection.move(p(1919,200),100); selection.end();
        JSONObject transform=sent.stream().filter(c -> c.optString("type").equals("edit_transform")).findFirst().orElseThrow();
        assertTrue("Windows accepts scale factors only through 1000",transform.optDouble("sx")<=1000);
        assertTrue(transform.optDouble("sx")>=.001);
        assertTrue(doc.strokes().get(0).points.get(0).x<=1920);
        doc.apply(Ink.object("type","undo")); assertEquals(10,doc.strokes().get(0).points.get(0).x,.01);
    }
    @Test public void movingAFullWidthSelectionCannotPushItOutsideTheMonitor() {
        InkDocument doc=line(0,200,1920,200);
        InkSelection selection=new InkSelection(doc,1920,1080,doc::apply);
        selection.begin(p(500,200),1,8); selection.end();
        selection.begin(p(600,200),1,9); selection.move(p(800,300),100); selection.end();
        assertEquals(0,doc.strokes().get(0).points.get(0).x,0);
        assertEquals(1920,doc.strokes().get(0).points.get(1).x,0);
        assertEquals(300,doc.strokes().get(0).points.get(1).y,.01);
    }
}
