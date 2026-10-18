package com.deskink.core;

import java.nio.charset.StandardCharsets;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class InkTest {
    @Test public void ordinaryAndTemporaryStrokesMatchTheSharedWindowsCommandContract() throws Exception {
        JSONArray commands;
        try(var input=getClass().getResourceAsStream("/deskink-tools.json")) {
            commands=new JSONArray(new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }
        JSONObject ordinary=Ink.begin(1,Ink.point(100,100,.5f),new int[]{255,210,35,80},20,false);
        JSONObject temporary=Ink.begin(3,Ink.point(20,30,.5f),new int[]{255,80,40,255},4,true);
        assertTrue("Windows rejects unknown fields in ordinary begin: "+ordinary,commands.getJSONObject(0).similar(ordinary));
        assertTrue(commands.getJSONObject(11).similar(temporary));
    }
}
