package com.deskink.core;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class UsbClientTest {
    static void send(OutputStream out,byte[] b) throws IOException { out.write(b.length&255); out.write(b.length>>8); out.write(b); out.flush(); }
    static byte[] read(InputStream stream) throws IOException {
        DataInputStream in=new DataInputStream(stream); int n=in.readUnsignedByte()|(in.readUnsignedByte()<<8);
        byte[] b=new byte[n]; in.readFully(b); return b;
    }
    static byte[] ack(byte[] hello,boolean authorized) throws Exception {
        byte[] ack=WireTest.vector(authorized?"hello_ack_paired":"hello_ack_unauthorized");
        System.arraycopy(hello,18,ack,18,4);
        if(authorized) { ack[ack.length-2]=4; ack[ack.length-1]=0x70; }
        return ack;
    }
    @Test public void realUsbSocketPairsBeforeSendingInkAndRejectsStaleMonitorBindings() throws Exception {
        BlockingQueue<UsbClient.State> states=new LinkedBlockingQueue<>();
        BlockingQueue<UsbClient.Binding> bindings=new LinkedBlockingQueue<>();
        UsbClient.Listener listener=new UsbClient.Listener() {
            public void state(UsbClient.State state) { states.add(state); }
            public void stream(UsbClient.Binding binding) { bindings.add(binding); }
            public void frame(FrameAssembler.Frame frame) { }
        };
        try(UsbClient client=new UsbClient(0,123,1920,1080,Collections.emptyList(),listener)) {
            int port=client.start();
            try(Socket host=new Socket("127.0.0.1",port)) {
                host.setSoTimeout(3000); OutputStream out=host.getOutputStream(); InputStream in=host.getInputStream();
                out.write(new byte[]{'E','M','L','I','N','K',1,0}); out.flush();
                byte[] hello=read(in); send(out,ack(hello,false));
                assertEquals(UsbClient.State.PAIRING,states.poll(3,TimeUnit.SECONDS));
                assertFalse(client.ink(null,Ink.command("clear")));
                client.pair(123456); hello=read(in);
                assertEquals(123456,ByteBuffer.wrap(hello).order(ByteOrder.LITTLE_ENDIAN).getInt(hello.length-4));
                send(out,ack(hello,true)); UsbClient.Binding binding=bindings.poll(3,TimeUnit.SECONDS); assertNotNull(binding);
                assertTrue(client.ink(binding,Ink.command("clear")));
                byte[] ink;
                do { ink=read(in); } while(ink[0]!='D');
                String json=new String(ink,4,ink.length-4,java.nio.charset.StandardCharsets.UTF_8);
                assertEquals(0x12345678L,new org.json.JSONObject(json).getLong("session"));
                byte[] config=WireTest.vector("stream_config"); ByteBuffer.wrap(config).order(ByteOrder.LITTLE_ENDIAN).putInt(16,4);
                send(out,config); assertNotNull(bindings.poll(3,TimeUnit.SECONDS));
                assertFalse(client.ink(binding,Ink.command("clear")));
            }
        }
    }
}
