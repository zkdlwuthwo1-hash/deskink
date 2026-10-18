package com.deskink.core;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/** Ordered USB video assembly. After a gap, delta frames cannot safely be decoded. */
public final class FrameAssembler {
    public static final int MAX_FRAME=4*1024*1024;
    public static final class Frame {
        public final byte[] bytes;
        public final long sequence, captureMicros;
        public final boolean keyframe;
        Frame(byte[] bytes,long sequence,long capture,boolean key) {
            this.bytes=bytes; this.sequence=sequence; captureMicros=capture; keyframe=key;
        }
    }
    private long session,epoch,seq=-1,capture;
    private int next,count;
    private boolean waiting=true,key;
    private final ByteArrayOutputStream data=new ByteArrayOutputStream();
    public long completed,dropped,fragments;
    public void reset(long session,long epoch) { this.session=session; this.epoch=epoch; recover(); }
    public void recover() { data.reset(); next=0; seq=-1; waiting=true; }
    public boolean needsKeyframe() { return waiting; }
    public Frame accept(Wire.Packet packet) {
        if(packet.type!=1 || packet.session!=session || packet.sequence!=epoch) return null;
        ByteBuffer b=packet.raw(); long incoming=Integer.toUnsignedLong(b.getInt(16));
        int index=Short.toUnsignedInt(b.getShort(20)), total=Short.toUnsignedInt(b.getShort(22));
        boolean incomingKey=(packet.flags&1)!=0;
        if(total==0 || total>3066 || index>=total) { dropped++; recover(); return null; }
        if(index==0) {
            if(next!=0||(seq>=0&&incoming!=((seq+1)&0xffffffffL))) { dropped++; waiting=true; }
            data.reset(); next=0; seq=incoming; count=total; capture=b.getLong(24); key=incomingKey;
        }
        if(seq!=incoming || next!=index || count!=total || key!=incomingKey || (waiting&&!key)) {
            dropped++; recover(); return null;
        }
        ByteBuffer body=packet.body();
        if(data.size()+body.remaining()>MAX_FRAME) { dropped++; recover(); return null; }
        byte[] bytes=new byte[body.remaining()]; body.get(bytes); data.write(bytes,0,bytes.length);
        next++; fragments++;
        if(next!=count) return null;
        Frame frame=new Frame(data.toByteArray(),seq,capture,key);
        next=0; data.reset(); waiting=false; completed++; return frame;
    }
}
