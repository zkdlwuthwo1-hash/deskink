package com.deskink.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** The existing version 2 datagram contract; all integers are little endian. */
public final class Wire {
    private Wire() {}
    public static final int MAX_PACKET = 1400;
    static ByteBuffer le(byte[] bytes) { return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); }
    static long u32(ByteBuffer b) { return Integer.toUnsignedLong(b.getInt()); }
    static int u16(ByteBuffer b) { return Short.toUnsignedInt(b.getShort()); }
    static void require(boolean ok) throws IOException { if (!ok) throw new IOException("Invalid DeskInk packet"); }
    public static byte[] control(int type,long session,long sequence,byte[] body) {
        if (body.length>MAX_PACKET-16) throw new IllegalArgumentException("packet too large");
        ByteBuffer b=ByteBuffer.allocate(16+body.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)0x4d45).put((byte)2).put((byte)type).putShort((short)0).putShort((short)body.length);
        b.putInt((int)session).putInt((int)sequence).put(body); return b.array();
    }
    public static byte[] hello(long sequence,long nonce,int port,int decoderCaps,int features,
            int pxW,int pxH,int ptW,int ptH,int refresh,String name,long device,int fps,byte[] token,int code) {
        byte[] n=name.getBytes(StandardCharsets.UTF_8);
        if(n.length>64 || token.length!=16) throw new IllegalArgumentException("hello identity");
        ByteBuffer b=ByteBuffer.allocate(22+n.length+29).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte)2).put((byte)2).putInt((int)nonce).putShort((short)port).putShort((short)decoderCaps)
            .putShort((short)features).putShort((short)pxW).putShort((short)pxH)
            .putShort((short)ptW).putShort((short)ptH).put((byte)refresh).put((byte)n.length).put(n)
            .putLong(device).put((byte)fps).put(token).putInt(code);
        return control(0x10,0,sequence,b.array());
    }
    public static byte[] keyframe(long session,long seq,long epoch,long last,int reason) {
        ByteBuffer b=ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt((int)epoch).putInt((int)last).put((byte)reason); return control(0x14,session,seq,b.array());
    }
    public static byte[] report(long session,long seq,long epoch,long highest,long complete,long dropped,
            long received,long lost,long jitter,int fps,int assembled,int decoding,int latency,int rtt) {
        ByteBuffer b=ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt((int)epoch).putInt((int)highest).putInt((int)complete).putInt((int)dropped)
            .putInt((int)received).putInt((int)lost).putInt((int)jitter).putShort((short)fps)
            .put((byte)assembled).put((byte)decoding).putShort((short)latency).putShort((short)rtt);
        return control(0x15,session,seq,b.array());
    }
    public static final class Config {
        public final long epoch;
        public final int width, height, fps, codec, flags;
        public final long bitrate;
        Config(ByteBuffer b) {
            epoch=u32(b); width=u16(b); height=u16(b); fps=u16(b);
            codec=Byte.toUnsignedInt(b.get()); flags=Byte.toUnsignedInt(b.get()); bitrate=u32(b);
        }
    }
    public static final class Ack {
        public final int status, version, heartbeatMs, reportMs, timeoutMs, caps;
        public final long nonce, session;
        public final Config config;
        public final String host;
        public final byte[] token;
        Ack(ByteBuffer b) throws IOException {
            require(b.remaining()>=33);
            status=Byte.toUnsignedInt(b.get()); version=Byte.toUnsignedInt(b.get());
            nonce=u32(b); session=u32(b); heartbeatMs=u16(b); reportMs=u16(b); timeoutMs=u16(b);
            config=new Config(b); int count=Byte.toUnsignedInt(b.get());
            require(count<=64 && b.remaining()>=count);
            byte[] name=new byte[count]; b.get(name); host=new String(name,StandardCharsets.UTF_8);
            token=new byte[16];
            if (b.remaining()>=18) { b.get(token); caps=u16(b); } else { caps=0; }
        }
    }
    public static final class Packet {
        public final int type, flags;
        public final long session, sequence;
        private final byte[] bytes;
        Packet(byte[] data) {
            bytes=data.clone(); ByteBuffer b=le(bytes); type=Byte.toUnsignedInt(b.get(3));
            flags=Byte.toUnsignedInt(b.get(4)); b.position(8); session=u32(b); sequence=u32(b);
        }
        public ByteBuffer body() { ByteBuffer b=le(bytes); b.position(type==1?32:16); return b.slice().order(ByteOrder.LITTLE_ENDIAN); }
        public Ack ack() throws IOException { require(type==0x11); return new Ack(body()); }
        ByteBuffer raw() { return le(bytes); }
        public Config config() throws IOException {
            require(type==0x12 || type==0x18); ByteBuffer b=body();
            if(type==0x12) { require(b.remaining()>=24); b.position(8); }
            require(b.remaining()>=16); return new Config(b);
        }
    }
    public static Packet decode(byte[] bytes) throws IOException {
        require(bytes.length>=16 && bytes.length<=MAX_PACKET);
        ByteBuffer b=le(bytes);
        require(u16(b)==0x4d45 && b.get()==2);
        int type=Byte.toUnsignedInt(b.get()); int header=type==1?32:16;
        require(bytes.length>=header && Short.toUnsignedInt(b.getShort(6))==bytes.length-header);
        return new Packet(bytes);
    }
}
