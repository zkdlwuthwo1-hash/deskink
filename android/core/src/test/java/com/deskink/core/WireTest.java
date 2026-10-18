package com.deskink.core;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class WireTest {
    @Test public void liveSessionReportsAndRecoveryMatchTheHostContract() throws Exception {
        assertArrayEquals(vector("keyframe_request"), Wire.keyframe(0x12345678L,44,3,4111,0));
        assertArrayEquals(vector("receiver_report"), Wire.report(0x12345678L,45,3,5000,4990,10,120000,250,2100,599,1,0,321,28));
        Wire.Config config=Wire.decode(vector("heartbeat")).config();
        assertEquals(3, config.epoch);
        assertEquals(2560, config.width);
        assertEquals(1440, config.height);
    }
    @Test public void clientPairingHelloMatchesPublishedVector() throws Exception {
        byte[] token={1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16};
        assertArrayEquals(vector("hello2_v030"), Wire.hello(4, 0xdeadbeefL, 9876, 3, 1,
            2420,1668,1210,834,120,"Ali's iPad Pro",0x0123456789abcdefL,120,token,123456));
    }
    static byte[] vector(String name) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                WireTest.class.getResourceAsStream("/v2_vectors.txt"), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null;) {
                if (!line.startsWith(name + " ")) continue;
                String hex = line.substring(name.length() + 1);
                byte[] b = new byte[hex.length() / 2];
                for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(hex.substring(i*2,i*2+2),16);
                return b;
            }
        }
        throw new IOException("missing fixture " + name);
    }
    @Test public void pairedWindowsAcknowledgementUsesSharedRustSwiftContract() throws Exception {
        Wire.Packet packet = Wire.decode(vector("hello_ack_paired"));
        Wire.Ack ack = packet.ack();
        assertEquals(0, ack.status);
        assertEquals(0xdeadbeefL, ack.nonce);
        assertEquals(0x12345678L, ack.session);
        assertEquals("ALI-PC", ack.host);
        assertEquals(2560, ack.config.width);
        assertEquals(1440, ack.config.height);
        assertEquals(60, ack.config.fps);
        assertArrayEquals(new byte[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16},ack.token);
        byte[] truncated = java.util.Arrays.copyOf(vector("hello_ack_paired"), 22);
        assertThrows(IOException.class, () -> Wire.decode(truncated));
    }
}
