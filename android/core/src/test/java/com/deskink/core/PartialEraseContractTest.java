package com.deskink.core;
import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PartialEraseContractTest {
    @Test public void sweptAndRepeatedErasingMatchesTheWindowsAndIPadContract() throws Exception {
        JSONArray cases;
        try(java.io.InputStream input=getClass().getResourceAsStream("/deskink-partial-cases.json")) {
            assertNotNull(input); cases=new JSONArray(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        }
        for(int c=0;c<cases.length();c++) {
            JSONObject fixture=cases.getJSONObject(c); InkDocument d=new InkDocument(); JSONArray strokes=fixture.getJSONArray("strokes");
            for(int i=0;i<strokes.length();i++) {
                JSONArray points=strokes.getJSONArray(i);
                d.apply(Ink.begin(i+1,point(points.getJSONArray(0)),new int[]{255,210,35,80},4,false));
                for(int p=1;p<points.length();p++) d.apply(Ink.at("append",i+1,point(points.getJSONArray(p))));
                d.apply(Ink.id("end",i+1));
            }
            erase(d,fixture.getJSONArray("erase"),100);
            if(fixture.has("secondErase")) erase(d,fixture.getJSONArray("secondErase"),101);
            JSONArray expected=fixture.getJSONArray("expected"); assertEquals(fixture.getString("name"),expected.length(),d.strokes().size());
            for(int i=0;i<expected.length();i++) {
                JSONObject e=expected.getJSONObject(i); InkDocument.Stroke actual=d.strokes().get(i);
                assertEquals(e.getLong("id"),actual.id); assertEquals(0x50ffd223,actual.color); assertEquals(4,actual.width,0);
                JSONArray p=e.getJSONArray("points"); assertEquals(p.length(),actual.points.size());
                for(int j=0;j<p.length();j++) { assertEquals(p.getJSONArray(j).getDouble(0),actual.points.get(j).x,.002); assertEquals(p.getJSONArray(j).getDouble(1),actual.points.get(j).y,.002); }
                Set<Integer> breaks=new TreeSet<>(); JSONArray b=e.getJSONArray("breaks"); for(int j=0;j<b.length();j++) breaks.add(b.getInt(j));
                assertEquals(breaks,actual.breaks);
            }
            d.apply(Ink.command("undo")); if(fixture.has("secondErase")) d.apply(Ink.command("undo"));
            assertEquals(strokes.length(),d.strokes().size());
            for(int i=0;i<strokes.length();i++) { assertEquals(strokes.getJSONArray(i).length(),d.strokes().get(i).points.size()); assertTrue(d.strokes().get(i).breaks.isEmpty()); }
        }
    }
    private static JSONObject point(JSONArray xy) { return Ink.point((float)xy.optDouble(0),(float)xy.optDouble(1),1); }
    private static void erase(InkDocument d,JSONArray path,long id) throws Exception {
        d.apply(Ink.object("type","erase_partial_begin","id",id,"radius",10,"point",point(path.getJSONArray(0))));
        for(int p=1;p<path.length();p++) d.apply(Ink.at("erase_to",id,point(path.getJSONArray(p))));
        d.apply(Ink.id("erase_end",id));
    }
}
