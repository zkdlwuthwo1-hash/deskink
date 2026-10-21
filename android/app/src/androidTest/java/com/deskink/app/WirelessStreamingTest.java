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
    @Test public void wirelessSessionDisplaysReorderedVideoAndAcknowledgesTheClearButton() throws Exception {
        try(DatagramSocket host=new DatagramSocket(0,InetAddress.getLoopbackAddress());
                ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)) {
            host.setSoTimeout(2500);
            activity.onActivity(a -> {
                a.getWindow().getDecorView().findViewWithTag("network").performClick();
                for(View window:android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    EditText address=window.findViewWithTag("network.address"); if(address==null) continue;
                    address.setText("127.0.0.1"); ((EditText)window.findViewWithTag("network.port")).setText(Integer.toString(host.getLocalPort()));
                    window.findViewById(android.R.id.button1).performClick();
                }
            });
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
            activity.onActivity(a -> a.getWindow().getDecorView().findViewWithTag("clear").performClick());
            DatagramPacket command;
            do { command=read(host); } while(command.getData()[0]!='D');
            assertEquals("DI02",new String(command.getData(),0,4,StandardCharsets.UTF_8));
            org.json.JSONObject envelope=new org.json.JSONObject(new String(command.getData(),4,command.getLength()-4,StandardCharsets.UTF_8));
            assertEquals("clear",envelope.getJSONObject("command").getString("type"));
            byte[] response=ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).put(new byte[]{'D','A','0','2'})
                .putInt(42).putInt(1).putLong(envelope.getLong("seq")).array(); send(host,peer,response);
            WorkspaceUiTest.capture("wireless-streaming");
        } finally {
            InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("usb-pairing",0)
                .edit().remove("lan-address").remove("lan-port").commit();
        }
    }
}
