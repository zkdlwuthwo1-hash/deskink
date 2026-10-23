package com.deskink.app;

import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import com.deskink.core.Wire;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

/** Real first-use UI and UDP packets; no USB connection or stored registration. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class WirelessPairingUiTest {
    @Test public void aFreshTabletPairsOverWifiAndSendsInkWithoutEverConnectingUsb() throws Exception {
        try(DatagramSocket host=new DatagramSocket(0,InetAddress.getByName("127.0.0.1"));
                ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create().start()) {
            host.setSoTimeout(1500);
            View workspace=controller.get().getWindow().getDecorView();
            workspace.findViewWithTag("welcome.network").performClick(); idle();
            AlertDialog network=ShadowAlertDialog.getLatestAlertDialog();
            View form=network.getWindow().getDecorView();
            ((EditText)form.findViewWithTag("network.address")).setText("127.0.0.1");
            ((EditText)form.findViewWithTag("network.port")).setText(Integer.toString(host.getLocalPort()));
            network.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();

            DatagramPacket hello=readHello(host);
            assertEquals("The first LAN request must not require a pre-existing PC code",0,code(hello));
            send(host,hello,ack(hello,false));
            await(() -> {
                AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
                return dialog!=null&&dialog.isShowing()&&dialog.getWindow().getDecorView().findViewWithTag("pair.code")!=null;
            },"An unregistered LAN PC must open the pairing code form");
            AlertDialog pairing=ShadowAlertDialog.getLatestAlertDialog();
            ((EditText)pairing.getWindow().getDecorView().findViewWithTag("pair.code")).setText("123456");
            pairing.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();

            DatagramPacket paired=readHello(host);
            assertEquals("The code entered in the form must go to the selected LAN PC",123456,code(paired));
            send(host,paired,ack(paired,true));
            await(() -> workspace.findViewWithTag("clear").isEnabled(),"Successful LAN pairing must enable the drawing controls");
            assertFalse(pairing.isShowing());
            workspace.findViewWithTag("clear").performClick();
            DatagramPacket ink=readType(host,(byte)'D');
            assertEquals("DI02",new String(ink.getData(),0,4,StandardCharsets.UTF_8));
            org.json.JSONObject envelope=new org.json.JSONObject(new String(ink.getData(),4,ink.getLength()-4,StandardCharsets.UTF_8));
            assertEquals("clear",envelope.getJSONObject("command").getString("type"));
            controller.stop();
        }
    }

    private static void idle() { Shadows.shadowOf(Looper.getMainLooper()).idle(); }
    private static void await(BooleanSupplier ready,String message) throws Exception {
        long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        do { idle(); if(ready.getAsBoolean()) return; Thread.sleep(10); } while(System.nanoTime()<until);
        fail(message);
    }
    private static DatagramPacket read(DatagramSocket host) throws Exception {
        DatagramPacket packet=new DatagramPacket(new byte[2048],2048); host.receive(packet);
        return new DatagramPacket(Arrays.copyOf(packet.getData(),packet.getLength()),packet.getLength(),packet.getSocketAddress());
    }
    private static DatagramPacket readHello(DatagramSocket host) throws Exception {
        for(int i=0;i<32;i++) { DatagramPacket packet=read(host); if(Wire.decode(packet.getData()).type==0x10) return packet; }
        throw new AssertionError("No HELLO2 received");
    }
    private static DatagramPacket readType(DatagramSocket host,byte type) throws Exception {
        for(int i=0;i<32;i++) { DatagramPacket packet=read(host); if(packet.getData()[0]==type) return packet; }
        throw new AssertionError("No ink packet received");
    }
    private static int code(DatagramPacket packet) {
        return ByteBuffer.wrap(packet.getData()).order(ByteOrder.LITTLE_ENDIAN).getInt(packet.getLength()-4);
    }
    private static byte[] ack(DatagramPacket hello,boolean authorized) throws Exception {
        // Shared Rust/Swift/Java wire vectors define the external host contract.
        String key=authorized?"hello_ack_paired ":"hello_ack_unauthorized ";
        for(String line:Files.readAllLines(Path.of("..","..","proto","testdata","v2_vectors.txt"))) {
            if(!line.startsWith(key)) continue;
            byte[] response=java.util.HexFormat.of().parseHex(line.substring(key.length()));
            System.arraycopy(hello.getData(),18,response,18,4);
            if(authorized) { response[response.length-2]=4; response[response.length-1]=(byte)0xf0; }
            return response;
        }
        throw new AssertionError("Missing host wire vector");
    }
    private static void send(DatagramSocket host,DatagramPacket peer,byte[] bytes) throws Exception {
        host.send(new DatagramPacket(bytes,bytes.length,peer.getSocketAddress()));
    }
}
