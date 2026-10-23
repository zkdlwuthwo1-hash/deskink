package com.deskink.core;

import org.json.JSONObject;
import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Opt-in cross-language check against the real Windows Rust sender, without USB. */
public class WindowsLanInteropTest {
    @Test public void aNewAndroidTabletPairsOverLanAndExchangesVideoAndInkWithWindows() throws Exception {
        String readyFile=System.getenv("DESKINK_INTEROP_READY");
        assumeTrue("Start the Windows android_lan_fixture and set DESKINK_INTEROP_READY",
            readyFile!=null&&!readyFile.isEmpty());
        JSONObject ready=new JSONObject(Files.readString(Path.of(readyFile)));
        BlockingQueue<UsbClient.State> states=new LinkedBlockingQueue<>();
        BlockingQueue<UsbClient.Binding> bindings=new LinkedBlockingQueue<>();
        BlockingQueue<FrameAssembler.Frame> frames=new LinkedBlockingQueue<>();
        BlockingQueue<UdpClient.Failure> failures=new LinkedBlockingQueue<>();
        AutoConnectSession.Listener observed=new AutoConnectSession.Listener() {
            public void state(UsbClient.State state) { states.add(state); }
            public void stream(UsbClient.Binding binding) { bindings.add(binding); }
            public void frame(FrameAssembler.Frame frame) { frames.offer(frame); }
            public void failure(UdpClient.Failure failure) { failures.add(failure); }
        };
        try(AutoConnectSession app=new AutoConnectSession(0,0x1234abcdL,1920,1080,
                Collections.emptyList(),null,observed)) {
            app.start();
            app.connectLan(ready.getString("address"),ready.getInt("port"));
            assertEquals("A fresh tablet must receive the code prompt from Windows; failures="+failures,
                UsbClient.State.PAIRING,states.poll(3,TimeUnit.SECONDS));
            app.pair(ready.getInt("code"));
            assertEquals("Entering the Windows code must establish the first wireless session; failures="+failures,
                UsbClient.State.CONNECTED,states.poll(3,TimeUnit.SECONDS));
            UsbClient.Binding binding=bindings.poll(1,TimeUnit.SECONDS);
            assertNotNull("Windows must supply the authenticated monitor binding",binding);
            assertTrue("The first connection must use LAN without a USB cable",app.isLan());
            FrameAssembler.Frame frame=frames.poll(3,TimeUnit.SECONDS);
            assertNotNull("Windows must deliver a complete video frame after pairing",frame);
            assertTrue("The decoder needs a keyframe to start",frame.keyframe);
            assertArrayEquals("UDP fragments must preserve the real H.264 sample",
                Files.readAllBytes(Path.of(ready.getString("framePath"))),frame.bytes);
            assertTrue("The paired tablet must accept a clear command",app.ink(binding,Ink.command("clear")));
            Path statusFile=Path.of(ready.getString("resultPath"));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            boolean cleared=false;
            while(System.nanoTime()<deadline&&!cleared) {
                if(Files.isRegularFile(statusFile)) {
                    cleared=new JSONObject(Files.readString(statusFile)).optBoolean("clear_received");
                }
                if(!cleared) Thread.sleep(20);
            }
            assertTrue("Windows must authenticate and receive the tablet's clear command",cleared);
            // An unacknowledged pen command expires after 250 ms. Observe the public
            // connection long enough to reject a host that consumes ink but never ACKs it.
            assertNull("Windows must acknowledge ink and keep the wireless session alive",
                failures.poll(500,TimeUnit.MILLISECONDS));
            assertTrue("The authenticated monitor must remain selected after clear",app.owns(binding));
        } finally {
            Files.writeString(Path.of(ready.getString("stopPath")),"stop\n");
        }
    }
}
