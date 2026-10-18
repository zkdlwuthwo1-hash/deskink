package com.deskink.core;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class FramedConnectionTest {
    @Test public void fragmentedUsbStreamPreservesPacketBoundariesAndRejectsOversize() throws Exception {
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress());
            Socket host=new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort())) {
            host.setSoTimeout(3000);
            Future<byte[]> receiver=executor.submit(() -> {
                try(FramedConnection link=new FramedConnection(server.accept())) {
                    byte[] packet=link.read(); link.send(packet);
                    assertThrows(IOException.class,link::read); return packet;
                }
            });
            OutputStream out=host.getOutputStream();
            for(byte b:new byte[]{'E','M','L','I','N','K',1,0,3,0,4,5,6}) { out.write(b); out.flush(); }
            byte[] echo=new byte[5]; new DataInputStream(host.getInputStream()).readFully(echo);
            assertArrayEquals(new byte[]{3,0,4,5,6},echo);
            out.write(new byte[]{(byte)0xff,(byte)0xff}); out.flush();
            assertArrayEquals(new byte[]{4,5,6},receiver.get(3,TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
}
