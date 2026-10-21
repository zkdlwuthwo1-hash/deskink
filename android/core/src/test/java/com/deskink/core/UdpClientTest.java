package com.deskink.core;

import org.junit.Test;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class UdpClientTest {
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
