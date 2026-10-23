package com.deskink.app;

import android.graphics.Bitmap;
import android.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

/** Actual Activity -> USB protocol -> MediaCodec -> TextureView on an Android device. */
@RunWith(AndroidJUnit4.class)
public class StreamingTest {
    private byte[] asset(String name) throws IOException {
        try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name);
            ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; for(int n;(n=in.read(buffer))!=-1;) out.write(buffer,0,n); return out.toByteArray();
        }
    }
    private static byte[] read(InputStream in) throws IOException {
        DataInputStream data=new DataInputStream(in); int n=data.readUnsignedByte()|(data.readUnsignedByte()<<8);
        byte[] b=new byte[n]; data.readFully(b); return b;
    }
    private static void send(OutputStream out,byte[] b) throws IOException { out.write(b.length&255); out.write(b.length>>8); out.write(b); out.flush(); }
    private static byte[] control(int type,long session,byte[] body) { return com.deskink.core.Wire.control(type,session,1,body); }
    private static void pen(InkCanvas canvas,int action,float x,float y) {
        MotionEvent.PointerProperties property=new MotionEvent.PointerProperties(); property.id=0; property.toolType=MotionEvent.TOOL_TYPE_STYLUS;
        MotionEvent.PointerCoords point=new MotionEvent.PointerCoords(); point.x=canvas.viewport.x()+x*canvas.viewport.scale(); point.y=canvas.viewport.y()+y*canvas.viewport.scale(); point.pressure=1;
        long now=android.os.SystemClock.uptimeMillis();
        MotionEvent event=MotionEvent.obtain(now,now,action,1,new MotionEvent.PointerProperties[]{property},new MotionEvent.PointerCoords[]{point},0,0,1,1,0,0,InputDevice.SOURCE_STYLUS,0);
        canvas.dispatchTouchEvent(event); event.recycle();
    }
    @Test public void usbSessionDisplaysDecodedVideoAndToolbarProducesHostCommands() throws Exception {
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)) {
            Socket host=null;
            for(int i=0;i<50&&host==null;i++) {
                try { host=new Socket("127.0.0.1",9877); } catch(IOException e) { Thread.sleep(100); }
            }
            assertNotNull("App must listen for the USB connection",host);
            try(Socket socket=host) {
                socket.setSoTimeout(4000); InputStream in=socket.getInputStream(); OutputStream out=socket.getOutputStream();
                out.write(new byte[]{'E','M','L','I','N','K',1,0}); out.flush(); byte[] hello=read(in);
                long nonce=Integer.toUnsignedLong(ByteBuffer.wrap(hello).order(ByteOrder.LITTLE_ENDIAN).getInt(18));
                ByteBuffer config=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
                config.putInt(1).putShort((short)1280).putShort((short)720).putShort((short)60).put((byte)0).put((byte)0).putInt(2000000);
                byte[] name="DeskInk PC · Screen 1".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                ByteBuffer ack=ByteBuffer.allocate(33+name.length+18).order(ByteOrder.LITTLE_ENDIAN);
                ack.put((byte)0).put((byte)2).putInt((int)nonce).putInt(42).putShort((short)1000).putShort((short)500).putShort((short)3000)
                    .put(config.array()).put((byte)name.length).put(name).put(new byte[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16}).putShort((short)0x7804);
                send(out,control(0x11,0,ack.array()));
                byte[] h264=asset("deskink_workspace_1280x720.h264"); int fragments=(h264.length+1367)/1368;
                AtomicBoolean shown=new AtomicBoolean();
                for(int attempt=0;attempt<40&&!shown.get();attempt++) {
                    for(int i=0;i<fragments;i++) {
                        int n=Math.min(1368,h264.length-i*1368); ByteBuffer media=ByteBuffer.allocate(32+n).order(ByteOrder.LITTLE_ENDIAN);
                        media.putShort((short)0x4d45).put((byte)2).put((byte)1).put((byte)1).put((byte)0).putShort((short)n)
                            .putInt(42).putInt(1).putInt(attempt+1).putShort((short)i).putShort((short)fragments).putLong(attempt*16667L)
                            .put(h264,i*1368,n); send(out,media.array());
                    }
                    Thread.sleep(100);
                    activity.onActivity(a -> {
                        TextureView view=a.getWindow().getDecorView().findViewWithTag("video");
                        if(view==null||!view.isAvailable()) return;
                        Bitmap b=view.getBitmap(64,64);
                        if(b!=null) { for(int y=8;y<56;y+=8) for(int x=8;x<56;x+=8) if((b.getPixel(x,y)&0xffffff)>0x101010) shown.set(true); b.recycle(); }
                    });
                }
                assertTrue("Decoded fixture must appear on the TextureView",shown.get());
                activity.onActivity(a -> a.getWindow().getDecorView().findViewWithTag("clear").performClick());
                byte[] command; do { command=read(in); } while(command[0]!='D');
                String json=new String(command,4,command.length-4,java.nio.charset.StandardCharsets.UTF_8);
                assertEquals("clear",new org.json.JSONObject(json).getJSONObject("command").getString("type"));
                activity.onActivity(a -> {
                    InkCanvas canvas=a.getWindow().getDecorView().findViewWithTag("ink");
                    // One real native stylus stroke under the sample amplifier.
                    for(int i=0;i<=20;i++) {
                        MotionEvent.PointerProperties pen=new MotionEvent.PointerProperties(); pen.id=0; pen.toolType=MotionEvent.TOOL_TYPE_STYLUS;
                        MotionEvent.PointerCoords point=new MotionEvent.PointerCoords();
                        point.x=canvas.viewport.x()+(510+i*13)*canvas.viewport.scale();
                        point.y=canvas.viewport.y()+(490+(float)Math.sin(i*.3)*3)*canvas.viewport.scale(); point.pressure=1;
                        int action=i==0?MotionEvent.ACTION_DOWN:i==20?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;
                        MotionEvent event=MotionEvent.obtain(100,100+i*8,action,1,new MotionEvent.PointerProperties[]{pen},new MotionEvent.PointerCoords[]{point},0,0,1,1,0,0,InputDevice.SOURCE_STYLUS,0);
                        canvas.dispatchTouchEvent(event); event.recycle();
                    }
                });
                WorkspaceUiTest.capture("streaming");
                send(out,control(0x18,42,config.array()));
                activity.onActivity(a -> {
                    InkCanvas canvas=a.getWindow().getDecorView().findViewWithTag("ink");
                    pen(canvas,MotionEvent.ACTION_DOWN,150,450);
                    for(float[] p:new float[][]{{350,450},{350,600},{150,600},{150,450}}) pen(canvas,MotionEvent.ACTION_MOVE,p[0],p[1]);
                });
                Thread.sleep(1150);
                activity.onActivity(a -> {
                    InkCanvas canvas=a.getWindow().getDecorView().findViewWithTag("ink"); pen(canvas,MotionEvent.ACTION_UP,150,450);
                    a.getWindow().getDecorView().findViewWithTag("tool.LASSO").performClick();
                    pen(canvas,MotionEvent.ACTION_DOWN,250,525); pen(canvas,MotionEvent.ACTION_MOVE,310,540); pen(canvas,MotionEvent.ACTION_UP,310,540);
                });
                boolean corrected=false,moved=false;
                while(!corrected||!moved) {
                    byte[] packet=read(in); if(packet[0]!='D') continue;
                    org.json.JSONObject inkCommand=new org.json.JSONObject(new String(packet,4,packet.length-4,java.nio.charset.StandardCharsets.UTF_8)).getJSONObject("command");
                    if(inkCommand.optString("type").equals("shape")) { assertEquals("rectangle",inkCommand.getString("kind")); corrected=true; }
                    if(inkCommand.optString("type").equals("edit_transform")) { assertEquals(60,inkCommand.getDouble("tx"),.1); moved=true; }
                }
                WorkspaceUiTest.capture("selection");
                send(out,control(0x18,42,config.array()));
                activity.onActivity(a -> a.getWindow().getDecorView().findViewWithTag("color").performClick());
                WorkspaceUiTest.capture("colors");
                activity.onActivity(a -> {
                    for(View window:android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                        View ok=window.findViewById(android.R.id.button2); if(ok!=null) ok.performClick();
                    }
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                activity.onActivity(a -> a.getWindow().getDecorView().findViewWithTag("shape").performClick());
                activity.onActivity(a -> {
                    View shape=a.getWindow().getDecorView().findViewWithTag("shape");
                    assertFalse(shape.isSelected()); assertTrue(shape.isShown()); assertTrue(shape.isEnabled());
                });
                WorkspaceUiTest.capture("shape-off");
                activity.onActivity(a -> a.getWindow().getDecorView().findViewWithTag("shape").performClick());
                activity.onActivity(a -> a.getWindow().getDecorView().findViewWithTag("tool.ERASER").performClick());
                WorkspaceUiTest.capture("eraser-sizes");
                activity.onActivity(a -> {
                    boolean chosen=false;
                    for(View window:android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                        View large=window.findViewWithTag("eraser.size.48");
                        if(large!=null) { large.performClick(); chosen=true; }
                    }
                    assertTrue("Eraser tap must directly show all sizes",chosen);
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                activity.onActivity(a -> {
                    InkCanvas canvas=a.getWindow().getDecorView().findViewWithTag("ink");
                    assertTrue("The picker must return input focus to the canvas",canvas.hasWindowFocus());
                    pen(canvas,MotionEvent.ACTION_DOWN,450,450); pen(canvas,MotionEvent.ACTION_UP,450,450);
                });
                boolean erased=false;
                while(!erased) {
                    byte[] packet=read(in); if(packet[0]!='D') continue;
                    org.json.JSONObject eraseCommand=new org.json.JSONObject(new String(packet,4,packet.length-4,java.nio.charset.StandardCharsets.UTF_8)).getJSONObject("command");
                    if(eraseCommand.optString("type").equals("erase_begin")) { assertEquals(48,eraseCommand.getDouble("radius"),.01); erased=true; }
                }
            }
        }
    }
}
