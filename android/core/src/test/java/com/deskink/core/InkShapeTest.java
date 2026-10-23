package com.deskink.core;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class InkShapeTest {
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
