package com.deskink.core;

import org.junit.Test;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class UdpClientTest {
    @Test public void aNewMonitorEpochRefreshesItsAuthenticatedLabelBeforeAcceptingMoreInk() throws Exception {
        BlockingQueue<UsbClient.Binding> bindings=new LinkedBlockingQueue<>();
        BlockingQueue<UsbClient.State> states=new LinkedBlockingQueue<>();
        UdpClient.Listener listener=new UdpClient.Listener() {
            public void state(UsbClient.State state) { states.add(state); }
            public void stream(UsbClient.Binding binding) { bindings.add(binding); }
            public void frame(FrameAssembler.Frame frame) { }
            public boolean savePairings(List<PcRegistration> saved) { return false; }
        };
        try(DatagramSocket host=new DatagramSocket(0,InetAddress.getLoopbackAddress());
                UdpClient client=new UdpClient("127.0.0.1",host.getLocalPort(),123,1920,1080,Collections.emptyList(),listener)) {
            host.setSoTimeout(1000); client.start(); DatagramPacket hello=receive(host); send(host,hello,ack(hello.getData(),false));
            assertEquals(UsbClient.State.PAIRING,states.poll(2,TimeUnit.SECONDS)); client.pair(123456);
            hello=receive(host); byte[] firstAck=ack(hello.getData(),true); send(host,hello,firstAck);
            UsbClient.Binding first=bindings.poll(2,TimeUnit.SECONDS); assertNotNull(first);
            ByteBuffer config=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            config.putInt((int)first.config.epoch+1).putShort((short)1920).putShort((short)1080).putShort((short)60).put((byte)0).put((byte)0).putInt(15000000);
            send(host,hello,Wire.control(0x18,first.session,7,config.array()));
            DatagramPacket fresh;
            do { fresh=receive(host); } while(Wire.decode(fresh.getData()).type!=0x10);
            assertFalse(client.ink(first,Ink.command("clear")));
            assertNull("New monitor cannot reuse the previous label",bindings.poll(30,TimeUnit.MILLISECONDS));
            assertArrayEquals("Active credential must survive a failed durable save",first.token,Arrays.copyOfRange(fresh.getData(),fresh.getLength()-20,fresh.getLength()-4));
            assertEquals(0,ByteBuffer.wrap(fresh.getData()).order(ByteOrder.LITTLE_ENDIAN).getInt(fresh.getLength()-4));
            byte[] next=ack(fresh.getData(),true); ByteBuffer.wrap(next).order(ByteOrder.LITTLE_ENDIAN).putInt(32,(int)first.config.epoch+1);
            // Keep the fixture's name length while independently changing the authenticated label.
            next[49]='X'; send(host,fresh,next);
            UsbClient.Binding second=bindings.poll(2,TimeUnit.SECONDS); assertNotNull(second);
            assertNotEquals(first.host,second.host); assertEquals(first.config.epoch+1,second.config.epoch);
            assertTrue(client.ink(second,Ink.command("clear")));
        }
    }
    @Test public void anUnsupportedHostShowsUpdateInsteadOfRequestingAnImpossiblePairingCode() throws Exception {
        BlockingQueue<UsbClient.State> states=new LinkedBlockingQueue<>();
        UdpClient.Listener listener=new UdpClient.Listener() {
            public void state(UsbClient.State state) { states.add(state); }
            public void stream(UsbClient.Binding binding) { }
            public void frame(FrameAssembler.Frame frame) { }
        };
        try(DatagramSocket host=new DatagramSocket(0,InetAddress.getLoopbackAddress());
                UdpClient client=new UdpClient("127.0.0.1",host.getLocalPort(),123,1920,1080,Collections.emptyList(),listener)) {
            host.setSoTimeout(1000); client.start(); DatagramPacket hello=receive(host);
            byte[] unsupported=ack(hello.getData(),false); unsupported[16]=2; send(host,hello,unsupported);
            assertEquals(UsbClient.State.INCOMPATIBLE,states.poll(2,TimeUnit.SECONDS));
        }
    }
    @Test public void realLanSessionPairsAndRepairsLostInkWithoutChangingUsb() throws Exception {
        BlockingQueue<UsbClient.Binding> bindings=new LinkedBlockingQueue<>();
        BlockingQueue<UsbClient.State> states=new LinkedBlockingQueue<>();
        UdpClient.Listener listener=new UdpClient.Listener() {
            public void state(UsbClient.State state) { states.add(state); }
            public void stream(UsbClient.Binding binding) { bindings.add(binding); }
            public void frame(FrameAssembler.Frame frame) { }
        };
        try(DatagramSocket host=new DatagramSocket(0,InetAddress.getLoopbackAddress());
                UdpClient client=new UdpClient("127.0.0.1",host.getLocalPort(),123,1920,1080,Collections.emptyList(),listener)) {
            host.setSoTimeout(1500); client.start();
            DatagramPacket hello=receive(host); send(host,hello,ack(hello.getData(),false));
            assertEquals(UsbClient.State.PAIRING,states.poll(2,TimeUnit.SECONDS));
            client.pair(123456); hello=receive(host); send(host,hello,ack(hello.getData(),true));
            UsbClient.Binding binding=bindings.poll(2,TimeUnit.SECONDS); assertNotNull(binding);
            assertTrue(client.ink(binding,Ink.command("clear")));
            DatagramPacket ink=receiveInk(host),retry=receiveInk(host);
            assertArrayEquals(ink.getData(),retry.getData());
            send(host,ink,ReliableInkTest.ack(binding.session,binding.config.epoch,1));
            client.close(); assertFalse(client.ink(binding,Ink.command("undo")));
        }
    }
    static DatagramPacket receive(DatagramSocket socket) throws Exception {
        DatagramPacket packet=new DatagramPacket(new byte[2048],2048); socket.receive(packet);
        return new DatagramPacket(Arrays.copyOf(packet.getData(),packet.getLength()),packet.getLength(),packet.getSocketAddress());
    }
    static DatagramPacket receiveInk(DatagramSocket host) throws Exception {
        for(int i=0;i<32;i++) { DatagramPacket packet=receive(host); if(packet.getData()[0]=='D') return packet; }
        throw new AssertionError("No reliable ink arrived");
    }
    static void send(DatagramSocket host,DatagramPacket peer,byte[] bytes) throws Exception {
        host.send(new DatagramPacket(bytes,bytes.length,peer.getSocketAddress()));
    }
    static byte[] ack(byte[] hello,boolean ok) throws Exception {
        byte[] bytes=UsbClientTest.ack(hello,ok); if(ok) bytes[bytes.length-1]=(byte)0xf0; return bytes;
    }
}
