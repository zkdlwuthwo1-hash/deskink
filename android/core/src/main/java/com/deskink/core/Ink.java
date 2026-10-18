package com.deskink.core;
import org.json.*;
import java.nio.charset.StandardCharsets;

/** DeskInk command vocabulary shared by local rendering and Windows. */
public final class Ink {
    private Ink() {}
    public static JSONObject object(Object... pairs) {
        JSONObject out=new JSONObject();
        try { for(int i=0;i<pairs.length;i+=2) out.put((String)pairs[i],pairs[i+1]); }
        catch(JSONException e) { throw new IllegalArgumentException(e); }
        return out;
    }
    public static JSONObject point(float x,float y,float pressure) {
        return object("x",x,"y",y,"pressure",Math.max(0,Math.min(1,pressure)));
    }
    public static JSONArray color(int[] rgba) {
        JSONArray out=new JSONArray(); for(int value:rgba) out.put(value); return out;
    }
    public static JSONObject command(String type) { return object("type",type); }
    public static JSONObject id(String type,long id) { return object("type",type,"id",id); }
    public static JSONObject at(String type,long id,JSONObject p) { return object("type",type,"id",id,"point",p); }
    public static JSONObject begin(long id,JSONObject p,int[] color,float width,boolean temporary) {
        return object("type",temporary?"transient_begin":"begin","id",id,"point",p,
            "brush",object("color",new JSONArray(color),"width",width),"lifetime_ms",2000);
    }
    public static byte[] envelope(long session,long epoch,long seq,JSONObject command) {
        String json=object("version",1,"session",session,"epoch",epoch,"seq",seq,"command",command).toString();
        return ("DI01"+json).getBytes(StandardCharsets.UTF_8);
    }
}
