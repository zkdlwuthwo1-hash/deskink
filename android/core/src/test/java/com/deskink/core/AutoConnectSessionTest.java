package com.deskink.core;

import org.junit.Test;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class AutoConnectSessionTest {
    private static class Observed implements AutoConnectSession.Listener {
        final BlockingQueue<UsbClient.Binding> bindings=new LinkedBlockingQueue<>();
        final BlockingQueue<AutoConnectSession.Endpoint> remembered=new LinkedBlockingQueue<>();
        volatile boolean cleared;
        public void state(UsbClient.State state) { }
        public void stream(UsbClient.Binding binding) { bindings.add(binding); }
        public void frame(FrameAssembler.Frame frame) { }
        public void remember(AutoConnectSession.Endpoint endpoint) { if(endpoint==null) cleared=true; else remembered.add(endpoint); }
    }
    private static PcRegistration registration() {
        byte[] token=new byte[16]; for(int i=0;i<16;i++) token[i]=(byte)(i+1);
        return new PcRegistration(token);
    }
    @Test public void anAuthenticatedRememberedPcReconnectsWithoutEnteringItsAddressAgain() throws Exception {
        Observed observed=new Observed();
        try(DatagramSocket host=new DatagramSocket(0,InetAddress.getByName("127.0.0.1"));
                AutoConnectSession app=new AutoConnectSession(0,123,1920,1080,Arrays.asList(registration()),
                    new AutoConnectSession.Endpoint("127.0.0.1",host.getLocalPort(),registration()),observed)) {
            host.setSoTimeout(2000); app.start();
            DatagramPacket hello=UdpClientTest.receive(host);
            assertEquals(0x10,Wire.decode(hello.getData()).type);
            assertArrayEquals(registration().token(),Arrays.copyOfRange(hello.getData(),hello.getLength()-20,hello.getLength()-4));
            UdpClientTest.send(host,hello,UdpClientTest.ack(hello.getData(),true));
            assertNotNull(observed.bindings.poll(2,TimeUnit.SECONDS));
        }
    }
    @Test public void aUsableUsbTunnelWinsAtStartupWithoutSendingALanHello() throws Exception {
        Observed observed=new Observed();
        try(DatagramSocket lan=new DatagramSocket(0,InetAddress.getByName("127.0.0.1"));
                AutoConnectSession app=new AutoConnectSession(0,123,1920,1080,Arrays.asList(registration()),
                    new AutoConnectSession.Endpoint("127.0.0.1",lan.getLocalPort(),registration()),observed);
                Socket usb=new Socket("127.0.0.1",app.start())) {
            usb.setSoTimeout(2000); usb.getOutputStream().write(new byte[]{'E','M','L','I','N','K',1,0});
            byte[] hello=UsbClientTest.read(usb.getInputStream());
            UsbClientTest.send(usb.getOutputStream(),UsbClientTest.ack(hello,true));
            assertNotNull(observed.bindings.poll(2,TimeUnit.SECONDS));
            lan.setSoTimeout(1200);
            assertThrows("USB must suppress the remembered LAN dial",SocketTimeoutException.class,() -> UdpClientTest.receive(lan));
        }
    }
    @Test public void insertingUsbReleasesLanBeforeHelloAndRejectsTheOldPenBinding() throws Exception {
        Observed observed=new Observed();
        try(DatagramSocket lan=new DatagramSocket(0,InetAddress.getByName("127.0.0.1"));
                AutoConnectSession app=new AutoConnectSession(0,123,1920,1080,Arrays.asList(registration()),
                    new AutoConnectSession.Endpoint("127.0.0.1",lan.getLocalPort(),registration()),observed)) {
            lan.setSoTimeout(2000); int usbPort=app.start(); DatagramPacket hello=UdpClientTest.receive(lan);
            UdpClientTest.send(lan,hello,UdpClientTest.ack(hello.getData(),true));
            UsbClient.Binding old=observed.bindings.poll(2,TimeUnit.SECONDS); assertNotNull(old);
            try(Socket usb=new Socket("127.0.0.1",usbPort)) {
                usb.setSoTimeout(2000); usb.getOutputStream().write(new byte[]{'E','M','L','I','N','K',1,0});
                byte[] usbHello=UsbClientTest.read(usb.getInputStream());
                Wire.Packet bye;
                do { bye=Wire.decode(UdpClientTest.receive(lan).getData()); } while(bye.type!=0x13);
                assertEquals(old.session,bye.session); assertEquals(4,bye.body().get());
                assertFalse(app.ink(old,Ink.command("clear")));
                UsbClientTest.send(usb.getOutputStream(),UsbClientTest.ack(usbHello,true));
                UsbClient.Binding selected=observed.bindings.poll(2,TimeUnit.SECONDS); assertNotNull(selected);
                assertFalse(app.isLan()); assertTrue(app.ink(selected,Ink.command("clear")));
                byte[] pen; do { pen=UsbClientTest.read(usb.getInputStream()); } while(pen[0]!='D');
                assertEquals('1',pen[3]); // USB retains the existing DI01 pen protocol.
            }
        }
    }
    @Test public void unpluggingUsbReconnectsOnlyTheSameRememberedPc() throws Exception {
        Observed observed=new Observed();
        try(DatagramSocket lan=new DatagramSocket(0,InetAddress.getByName("127.0.0.1"));
                AutoConnectSession app=new AutoConnectSession(0,123,1920,1080,Arrays.asList(registration()),
                    new AutoConnectSession.Endpoint("127.0.0.1",lan.getLocalPort(),registration()),observed)) {
            int usbPort=app.start();
            try(Socket usb=new Socket("127.0.0.1",usbPort)) {
                usb.setSoTimeout(2000); usb.getOutputStream().write(new byte[]{'E','M','L','I','N','K',1,0});
                byte[] hello=UsbClientTest.read(usb.getInputStream()); UsbClientTest.send(usb.getOutputStream(),UsbClientTest.ack(hello,true));
                assertNotNull(observed.bindings.poll(2,TimeUnit.SECONDS));
            }
            lan.setSoTimeout(2000); DatagramPacket hello=UdpClientTest.receive(lan);
            UdpClientTest.send(lan,hello,UdpClientTest.ack(hello.getData(),true));
            assertNotNull(observed.bindings.poll(2,TimeUnit.SECONDS)); assertTrue(app.isLan());
        }
    }
    @Test public void connectingADifferentUsbPcDoesNotFallBackToThePreviousPc() throws Exception {
        Observed observed=new Observed();
        try(DatagramSocket previous=new DatagramSocket(0,InetAddress.getByName("127.0.0.1"));
                AutoConnectSession app=new AutoConnectSession(0,123,1920,1080,Arrays.asList(registration()),
                    new AutoConnectSession.Endpoint("127.0.0.1",previous.getLocalPort(),registration()),observed)) {
            int usbPort=app.start();
            try(Socket usb=new Socket("127.0.0.1",usbPort)) {
                usb.setSoTimeout(2000); usb.getOutputStream().write(new byte[]{'E','M','L','I','N','K',1,0});
                byte[] response=UsbClientTest.ack(UsbClientTest.read(usb.getInputStream()),true);
                Arrays.fill(response,response.length-18,response.length-2,(byte)99);
                UsbClientTest.send(usb.getOutputStream(),response);
                assertNotNull(observed.bindings.poll(2,TimeUnit.SECONDS));
            }
            previous.setSoTimeout(1200);
            assertThrows("Unplugging PC B must not send handwriting to PC A",SocketTimeoutException.class,() -> UdpClientTest.receive(previous));
            assertTrue("The unsafe fallback must also be removed from durable preferences",observed.cleared);
        }
    }
    @Test public void manualAddressBecomesAutomaticOnlyAfterSuccessfulPairingAndDurableSave() throws Exception {
        Observed observed=new Observed();
        try(DatagramSocket lan=new DatagramSocket(0,InetAddress.getByName("127.0.0.1"));
                AutoConnectSession app=new AutoConnectSession(0,123,1920,1080,Collections.emptyList(),null,observed)) {
            app.start(); app.connectLan("127.0.0.1",lan.getLocalPort()); lan.setSoTimeout(1500);
            DatagramPacket hello=UdpClientTest.receive(lan);
            UdpClientTest.send(lan,hello,UdpClientTest.ack(hello.getData(),false));
            assertNull("Entering an address must not remember an unpaired PC",observed.remembered.poll(100,TimeUnit.MILLISECONDS));
            app.pair(123456); hello=UdpClientTest.receive(lan);
            UdpClientTest.send(lan,hello,UdpClientTest.ack(hello.getData(),true));
            assertNotNull(observed.bindings.poll(2,TimeUnit.SECONDS));
            AutoConnectSession.Endpoint saved=observed.remembered.poll(1,TimeUnit.SECONDS);
            assertNotNull("A successful connection must persist its fallback address",saved);
            assertEquals("127.0.0.1",saved.address); assertEquals(lan.getLocalPort(),saved.port);
            assertArrayEquals(registration().token(),saved.registration.token());
        }
    }
    @Test public void aSavedAddressCannotSilentlyReconnectToADifferentAuthenticatedPc() throws Exception {
        Observed observed=new Observed();
        try(DatagramSocket lan=new DatagramSocket(0,InetAddress.getByName("127.0.0.1"));
                AutoConnectSession app=new AutoConnectSession(0,123,1920,1080,Arrays.asList(registration()),
                    new AutoConnectSession.Endpoint("127.0.0.1",lan.getLocalPort(),registration()),observed)) {
            app.start(); lan.setSoTimeout(2000); DatagramPacket hello=UdpClientTest.receive(lan);
            byte[] response=UdpClientTest.ack(hello.getData(),true);
            Arrays.fill(response,response.length-18,response.length-2,(byte)99);
            UdpClientTest.send(lan,hello,response);
            assertNull("A changed IP must not select a different PC",observed.bindings.poll(250,TimeUnit.MILLISECONDS));
            assertTrue(observed.cleared);
        }
    }
    @Test public void usbHandoffWaitsForTheInFlightDurableLanCredential() throws Exception {
        CountDownLatch saving=new CountDownLatch(1),release=new CountDownLatch(1);
        Observed observed=new Observed() {
            public boolean savePairings(List<PcRegistration> registrations) {
                saving.countDown();
                try { return release.await(2,TimeUnit.SECONDS); }
                catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
            }
        };
        try(DatagramSocket lan=new DatagramSocket(0,InetAddress.getByName("127.0.0.1"));
                AutoConnectSession app=new AutoConnectSession(0,123,1920,1080,Collections.emptyList(),null,observed)) {
            int port=app.start(); app.connectLan("127.0.0.1",lan.getLocalPort()); lan.setSoTimeout(1500);
            DatagramPacket hello=UdpClientTest.receive(lan); UdpClientTest.send(lan,hello,UdpClientTest.ack(hello.getData(),true));
            assertTrue(saving.await(1,TimeUnit.SECONDS));
            try(Socket usb=new Socket("127.0.0.1",port)) {
                usb.setSoTimeout(150); usb.getOutputStream().write(new byte[]{'E','M','L','I','N','K',1,0});
                try { assertThrows(SocketTimeoutException.class,() -> UsbClientTest.read(usb.getInputStream())); }
                finally { release.countDown(); }
                usb.setSoTimeout(2000); byte[] usbHello=UsbClientTest.read(usb.getInputStream());
                assertArrayEquals("A USB handoff must use the newly saved token without another pairing prompt",
                    registration().token(),Arrays.copyOfRange(usbHello,usbHello.length-20,usbHello.length-4));
            }
        } finally { release.countDown(); }
    }
    @Test public void stoppingTheForegroundSessionCancelsFallbackAndReleasesUsb() throws Exception {
        try(DatagramSocket lan=new DatagramSocket(0,InetAddress.getByName("127.0.0.1"));
                AutoConnectSession app=new AutoConnectSession(0,123,1920,1080,Arrays.asList(registration()),
                    new AutoConnectSession.Endpoint("127.0.0.1",lan.getLocalPort(),registration()),new Observed())) {
            int port=app.start(); app.close(); app.connectLan("127.0.0.1",lan.getLocalPort());
            lan.setSoTimeout(1200); assertThrows(SocketTimeoutException.class,() -> UdpClientTest.receive(lan));
            try(ServerSocket available=new ServerSocket()) {
                available.setReuseAddress(true); available.bind(new InetSocketAddress("127.0.0.1",port));
            }
        }
    }
    @Test public void aFailedCredentialSaveDoesNotCreateAnAutomaticEndpoint() throws Exception {
        Observed observed=new Observed() { public boolean savePairings(List<PcRegistration> saved) { return false; } };
        try(DatagramSocket lan=new DatagramSocket(0,InetAddress.getByName("127.0.0.1"));
                AutoConnectSession app=new AutoConnectSession(0,123,1920,1080,Collections.emptyList(),null,observed)) {
            app.start(); app.connectLan("127.0.0.1",lan.getLocalPort()); lan.setSoTimeout(1500);
            DatagramPacket hello=UdpClientTest.receive(lan); UdpClientTest.send(lan,hello,UdpClientTest.ack(hello.getData(),true));
            assertNotNull(observed.bindings.poll(2,TimeUnit.SECONDS));
            assertNull(observed.remembered.poll(150,TimeUnit.MILLISECONDS));
        }
    }
    @Test public void stoppingWhileAUsbPeerHasNotSentItsPreambleClosesThePeerImmediately() throws Exception {
        try(AutoConnectSession app=new AutoConnectSession(0,123,1920,1080,Collections.emptyList(),null,new Observed());
                Socket peer=new Socket("127.0.0.1",app.start())) {
            // The PC tunnel has connected, but has not yet sent its EMLINK preamble.
            peer.setSoTimeout(500);
            app.close();
            assertEquals("Backgrounding must close even a pre-handshake USB socket",-1,peer.getInputStream().read());
        }
    }
}
