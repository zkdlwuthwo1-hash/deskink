package com.deskink.app;

import android.graphics.Bitmap;
import android.view.*;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.deskink.core.Wire;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Actual IP-entry UI -> UDP -> MediaCodec -> TextureView and acknowledged toolbar command. */
@RunWith(AndroidJUnit4.class)
public class WirelessStreamingTest {
    private static DatagramPacket read(DatagramSocket socket) throws IOException {
        DatagramPacket packet=new DatagramPacket(new byte[2048],2048); socket.receive(packet);
        return new DatagramPacket(Arrays.copyOf(packet.getData(),packet.getLength()),packet.getLength(),packet.getSocketAddress());
    }
    private static void send(DatagramSocket socket,SocketAddress peer,byte[] bytes) throws IOException { socket.send(new DatagramPacket(bytes,bytes.length,peer)); }
    private static boolean greenMonitorVisible(int[] bounds) {
        // Observe the composed display, not TextureView.getBitmap(), which can
        // pull a newer buffer even when the visible window has not repainted.
        Bitmap screen=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        if(screen==null) return false;
        try {
            for(int y=4;y<=6;y++) for(int x=4;x<=6;x++) {
                int pixel=screen.getPixel(bounds[0]+bounds[2]*x/10,bounds[1]+bounds[3]*y/10);
                if(android.graphics.Color.green(pixel)<=100||android.graphics.Color.red(pixel)>=50||android.graphics.Color.blue(pixel)>=90) return false;
            }
            return true;
        } finally { screen.recycle(); }
    }
    @Test public void wirelessSessionChangesMonitorVideoBeforeTheNextPenGesture() throws Exception {
        // Android's generic loopback is ::1; the app's IP form uses IPv4.
        try(DatagramSocket host=new DatagramSocket(0,InetAddress.getByName("127.0.0.1"));
                ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)) {
            assertTrue(host.getLocalAddress() instanceof Inet4Address);
            host.setSoTimeout(2500);
            activity.onActivity(a -> a.getWindow().getDecorView().findViewWithTag("network").performClick());
            // Dialog.onShow installs the Connect handler on the next UI turn.
            // A person cannot fill the fields before that turn, either.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AtomicBoolean submitted=new AtomicBoolean();
            activity.onActivity(a -> {
                for(View window:android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    EditText address=window.findViewWithTag("network.address"); if(address==null) continue;
                    address.setText("127.0.0.1"); ((EditText)window.findViewWithTag("network.port")).setText(Integer.toString(host.getLocalPort()));
                    window.findViewById(android.R.id.button1).performClick();
                    assertEquals(View.GONE,window.findViewWithTag("network.error").getVisibility()); submitted.set(true);
                }
            });
            assertTrue("The visible IP form must actually be submitted",submitted.get());
            DatagramPacket hello=read(host); SocketAddress peer=hello.getSocketAddress();
            long nonce=Integer.toUnsignedLong(ByteBuffer.wrap(hello.getData()).order(ByteOrder.LITTLE_ENDIAN).getInt(18));
            ByteBuffer config=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            config.putInt(1).putShort((short)1280).putShort((short)720).putShort((short)60).put((byte)0).put((byte)0).putInt(2000000);
            byte[] name="DeskInk PC · Screen 1".getBytes(StandardCharsets.UTF_8);
            ByteBuffer ack=ByteBuffer.allocate(33+name.length+18).order(ByteOrder.LITTLE_ENDIAN);
            ack.put((byte)0).put((byte)2).putInt((int)nonce).putInt(42).putShort((short)1000).putShort((short)500).putShort((short)3000)
                .put(config.array()).put((byte)name.length).put(name).put(new byte[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16}).putShort((short)0xf004);
            send(host,peer,Wire.control(0x11,0,1,ack.array()));
            byte[] h264;
            try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("deskink_workspace_1280x720.h264");
                    ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192]; for(int n;(n=in.read(buffer))!=-1;) out.write(buffer,0,n); h264=out.toByteArray();
            }
            int fragments=(h264.length+1367)/1368; AtomicBoolean shown=new AtomicBoolean();
            for(int attempt=0;attempt<30&&!shown.get();attempt++) {
                for(int index=fragments-1;index>=0;index--) {
                    int n=Math.min(1368,h264.length-index*1368); ByteBuffer media=ByteBuffer.allocate(32+n).order(ByteOrder.LITTLE_ENDIAN);
                    media.putShort((short)0x4d45).put((byte)2).put((byte)1).put((byte)1).put((byte)0).putShort((short)n)
                        .putInt(42).putInt(1).putInt(attempt+1).putShort((short)index).putShort((short)fragments).putLong(attempt*16667L).put(h264,index*1368,n);
                    send(host,peer,media.array());
                }
                Thread.sleep(100);
                activity.onActivity(a -> {
                    TextureView view=a.getWindow().getDecorView().findViewWithTag("video"); if(!view.isAvailable()) return;
                    Bitmap image=view.getBitmap(64,64);
                    if(image!=null) { for(int y=8;y<56;y+=8) for(int x=8;x<56;x+=8) if((image.getPixel(x,y)&0xffffff)>0x101010) shown.set(true); image.recycle(); }
                });
            }
            assertTrue("UDP frame reordering must still display real decoded video",shown.get());
            int[] videoBounds=new int[4];
            activity.onActivity(a -> {
                View view=a.getWindow().getDecorView().findViewWithTag("video"); int[] location=new int[2]; view.getLocationOnScreen(location);
                videoBounds[0]=location[0]; videoBounds[1]=location[1]; videoBounds[2]=view.getWidth(); videoBounds[3]=view.getHeight();
            });
            assertFalse("The first monitor fixture must be distinguishable from the second",greenMonitorVisible(videoBounds));

            // The Windows pipeline restarts on monitor change, keeping the UDP
            // endpoint while publishing a fresh epoch and authenticated label.
            ByteBuffer secondConfig=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            secondConfig.putInt(2).putShort((short)1280).putShort((short)720).putShort((short)60).put((byte)0).put((byte)0).putInt(2000000);
            send(host,peer,Wire.control(0x18,42,2,secondConfig.array()));
            DatagramPacket nextHello;
            do { nextHello=read(host); } while(Wire.decode(nextHello.getData()).type!=0x10);
            long secondNonce=Integer.toUnsignedLong(ByteBuffer.wrap(nextHello.getData()).order(ByteOrder.LITTLE_ENDIAN).getInt(18));
            byte[] secondName="DeskInk PC · Screen 2".getBytes(StandardCharsets.UTF_8);
            ByteBuffer secondAck=ByteBuffer.allocate(33+secondName.length+18).order(ByteOrder.LITTLE_ENDIAN);
            secondAck.put((byte)0).put((byte)2).putInt((int)secondNonce).putInt(43).putShort((short)1000).putShort((short)500).putShort((short)3000)
                .put(secondConfig.array()).put((byte)secondName.length).put(secondName)
                .put(new byte[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16}).putShort((short)0xf004);
            send(host,peer,Wire.control(0x11,0,3,secondAck.array()));
            byte[] green;
            try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("monitor_switch_green_1280x720.h264");
                    ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192]; for(int n;(n=in.read(buffer))!=-1;) out.write(buffer,0,n); green=out.toByteArray();
            }
            int greenFragments=(green.length+1367)/1368;
            for(int index=0;index<greenFragments;index++) {
                int n=Math.min(1368,green.length-index*1368); ByteBuffer media=ByteBuffer.allocate(32+n).order(ByteOrder.LITTLE_ENDIAN);
                media.putShort((short)0x4d45).put((byte)2).put((byte)1).put((byte)1).put((byte)0).putShort((short)n)
                    .putInt(43).putInt(2).putInt(1).putShort((short)index).putShort((short)greenFragments).putLong(0).put(green,index*1368,n);
                send(host,peer,media.array());
            }
            // A static monitor may have only its first frame. Do not draw, tap
            // the toolbar, or keep feeding frames to make the display update.
            boolean secondShown=false;
            for(int attempt=0;attempt<20&&!secondShown;attempt++) {
                Thread.sleep(100);
                send(host,peer,Wire.control(0x18,43,4+attempt,secondConfig.array()));
                secondShown=greenMonitorVisible(videoBounds);
            }
            if(!secondShown) WorkspaceUiTest.capture("monitor-switch-failed");
            assertTrue("The new monitor must appear without a pen gesture or another changed desktop frame",secondShown);
            activity.onActivity(a -> a.getWindow().getDecorView().findViewWithTag("clear").performClick());
            DatagramPacket command;
            do { command=read(host); } while(command.getData()[0]!='D');
            assertEquals("DI02",new String(command.getData(),0,4,StandardCharsets.UTF_8));
            org.json.JSONObject envelope=new org.json.JSONObject(new String(command.getData(),4,command.getLength()-4,StandardCharsets.UTF_8));
            assertEquals("clear",envelope.getJSONObject("command").getString("type"));
            assertEquals("Ink must refer to the same monitor as the displayed video",2,envelope.getInt("epoch"));
            byte[] response=ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).put(new byte[]{'D','A','0','2'})
                .putInt(43).putInt(2).putLong(envelope.getLong("seq")).array(); send(host,peer,response);
            WorkspaceUiTest.capture("wireless-streaming");
        } finally {
            InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("usb-pairing",0)
                .edit().remove("lan-address").remove("lan-port").commit();
        }
    }
}
