package com.deskink.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class InkDocumentTest {
    @Test public void selectionPreviewsUseTheOriginalGeometryAndCommitOneUndoStep() {
        InkDocument d=new InkDocument();
        for(int id=1;id<=2;id++) { d.apply(Ink.begin(id,Ink.point(id*100,100,1),new int[]{255,0,0,255},4,false)); d.apply(Ink.id("end",id)); }
        d.apply(Ink.id("edit_begin",9)); d.apply(Ink.object("type","edit_add","id",9,"ids",new org.json.JSONArray(new long[]{1,2})));
        d.apply(Ink.object("type","edit_transform","id",9,"sx",1,"sy",1,"tx",10,"ty",20));
        d.apply(Ink.object("type","edit_transform","id",9,"sx",2,"sy",1.5,"tx",20,"ty",30));
        d.apply(Ink.id("edit_end",9));
        assertEquals(220,d.strokes().get(0).points.get(0).x,.01); assertEquals(420,d.strokes().get(1).points.get(0).x,.01);
        assertEquals(180,d.strokes().get(0).points.get(0).y,.01);
        d.apply(Ink.command("undo")); assertEquals(100,d.strokes().get(0).points.get(0).x,.01);
        d.apply(Ink.command("redo")); assertEquals(220,d.strokes().get(0).points.get(0).x,.01);
        d.apply(Ink.command("undo")); d.apply(Ink.command("undo")); assertEquals(1,d.strokes().size());
    }
    @Test public void shapeCorrectionKeepsTheBrushAndRemainsOneUndoableStroke() {
        InkDocument d=new InkDocument();
        d.apply(Ink.begin(7,Ink.point(98,102,.5f),new int[]{255,210,35,80},20,false));
        d.apply(Ink.object("type","shape","id",7,"kind","rectangle","a",Ink.point(100,100,.5f),"b",Ink.point(300,300,.5f)));
        assertEquals(5,d.strokes().get(0).points.size());
        assertEquals(0x50ffd223,d.strokes().get(0).color);
        assertEquals(20,d.strokes().get(0).width,.01);
        d.apply(Ink.id("end",7)); d.apply(Ink.command("undo")); assertTrue(d.strokes().isEmpty());
        d.apply(Ink.command("redo")); assertEquals(5,d.strokes().get(0).points.size());
    }
    @Test public void twoConnectedPcSessionsKeepSeparateMonitorInkWhenSwitchingBack() {
        MonitorDocuments boards=new MonitorDocuments();
        InkDocument first=boards.select("pc-one","Screen 1");
        first.apply(Ink.begin(1,Ink.point(10,10,1),new int[]{255,0,0,255},4,false)); first.apply(Ink.id("end",1));
        InkDocument second=boards.select("pc-two","Screen 1");
        assertTrue(second.strokes().isEmpty());
        assertSame(first,boards.select("pc-one","Screen 1"));
        assertEquals(1,first.strokes().size());
        assertSame(second,boards.select("pc-two","Screen 1"));
    }
    @Test public void temporaryInkExpiresWithoutChangingPermanentUndoAndMonitorsKeepTheirOwnHistory() {
        MonitorDocuments boards=new MonitorDocuments();
        InkDocument one=boards.select("pc-token","Screen 1"); one.advanceTime(100);
        one.apply(Ink.begin(1,Ink.point(5,5,1),new int[]{255,0,0,255},4,false)); one.apply(Ink.id("end",1));
        one.apply(Ink.begin(2,Ink.point(8,8,1),new int[]{255,0,0,255},4,true)); one.apply(Ink.id("end",2));
        one.advanceTime(2100); assertTrue(one.temporary().isEmpty()); assertEquals(1,one.strokes().size());
        InkDocument two=boards.select("pc-token","Screen 2"); assertTrue(two.strokes().isEmpty());
        assertSame(one,boards.select("pc-token","Screen 1"));
        one.apply(Ink.command("undo")); assertTrue(one.strokes().isEmpty());
        assertNotSame(one,boards.select("new-process-token","Screen 1"));
    }
    @Test public void aSweptEraserRemovesCrossedStrokesAndOneUndoRestoresTheGesture() {
        InkDocument d=new InkDocument();
        for(int i=1;i<=2;i++) {
            d.apply(Ink.begin(i,Ink.point(i*20,0,1),new int[]{0,0,0,255},4,false));
            d.apply(Ink.at("append",i,Ink.point(i*20,100,1))); d.apply(Ink.id("end",i));
        }
        d.apply(Ink.object("type","erase_begin","id",3,"point",Ink.point(0,50,1),"radius",2));
        d.apply(Ink.at("erase_to",3,Ink.point(60,50,1))); d.apply(Ink.id("erase_end",3));
        assertTrue(d.strokes().isEmpty());
        d.apply(Ink.command("undo")); assertEquals(2,d.strokes().size());
        assertEquals(1,d.strokes().get(0).id); assertEquals(2,d.strokes().get(1).id);
        d.apply(Ink.command("redo")); assertTrue(d.strokes().isEmpty());
    }
    @Test public void aDrawnStrokeCanBeUndoneRedoneAndClearedUsingWireCommands() {
        InkDocument d=new InkDocument();
        d.apply(Ink.begin(1,Ink.point(10,20,1),new int[]{255,80,40,255},4,false));
        d.apply(Ink.at("append",1,Ink.point(30,40,1)));
        d.apply(Ink.id("end",1));
        assertEquals(2,d.strokes().get(0).points.size());
        d.apply(Ink.command("undo")); assertTrue(d.strokes().isEmpty());
        d.apply(Ink.command("redo")); assertEquals(1,d.strokes().size());
        d.apply(Ink.command("clear")); assertTrue(d.strokes().isEmpty());
        d.apply(Ink.command("undo")); assertEquals(1,d.strokes().size());
    }
}
