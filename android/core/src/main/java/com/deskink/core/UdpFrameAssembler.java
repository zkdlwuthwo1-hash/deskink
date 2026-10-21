package com.deskink.core;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.*;

/** Bounded UDP reassembly. Missing video is repaired briefly, then a fresh IDR is required. */
public final class UdpFrameAssembler {
    private static final long MAX_AGE_MS=50;
    private static final int FRAME_LIMIT=4,BYTE_LIMIT=8*1024*1024;
    private long session,epoch,last=-1;
    private boolean waiting=true;
    private int bytes;
    private final Map<Long,Assembly> frames=new LinkedHashMap<>();
    public long completed,dropped,fragments,lost,repaired,nacks;
    private static final class Assembly {
        final long sequence,capture,started;
        final boolean key;
        final byte[][] parts;
        int count,size;
        long repairAt;
        Assembly(long sequence,long capture,boolean key,int count,long now) {
            this.sequence=sequence; this.capture=capture; this.key=key; parts=new byte[count][];
            started=now; repairAt=now;
        }
        boolean complete() { return count==parts.length; }
    }
    public static final class Missing {
        public final long sequence;
        public final int count;
        public final int[] indices;
        Missing(long sequence,int count,int[] indices) { this.sequence=sequence; this.count=count; this.indices=indices; }
    }
    public void reset(long session,long epoch) {
        this.session=session; this.epoch=epoch; last=-1; waiting=true; frames.clear(); bytes=0;
        completed=dropped=fragments=lost=repaired=nacks=0;
    }
    public boolean needsKeyframe() { return waiting; }
    public int depth() { return frames.size(); }
    public List<FrameAssembler.Frame> accept(Wire.Packet packet,long nowMs) {
        expire(nowMs);
        if(packet.type!=1||packet.session!=session||packet.sequence!=epoch) return Collections.emptyList();
        ByteBuffer b=packet.raw(); long sequence=Integer.toUnsignedLong(b.getInt(16));
        int index=Short.toUnsignedInt(b.getShort(20)),count=Short.toUnsignedInt(b.getShort(22));
        boolean key=(packet.flags&1)!=0;
        if(count==0||count>3066||index>=count||(last>=0&&(int)(sequence-last)<=0)) return Collections.emptyList();
        Assembly frame=frames.get(sequence);
        if(frame==null) {
            if(frames.size()>=FRAME_LIMIT) recover();
            frame=new Assembly(sequence,b.getLong(24),key,count,nowMs); frames.put(sequence,frame);
        }
        if(frame.parts.length!=count||frame.key!=key||frame.capture!=b.getLong(24)) return Collections.emptyList();
        if(frame.parts[index]==null) {
            ByteBuffer body=packet.body();
            if(frame.size+body.remaining()>FrameAssembler.MAX_FRAME||bytes+body.remaining()>BYTE_LIMIT) { recover(); return Collections.emptyList(); }
            byte[] part=new byte[body.remaining()]; body.get(part); frame.parts[index]=part;
            frame.size+=part.length; bytes+=part.length; frame.count++; fragments++;
            if((packet.flags&2)!=0) repaired++;
        }
        // A complete keyframe safely supersedes missing older delta frames.
        if(frame.complete()&&frame.key&&(waiting||sequence!=((last+1)&0xffffffffL))) {
            for(Iterator<Assembly> it=frames.values().iterator();it.hasNext();) {
                Assembly prior=it.next();
                if((int)(prior.sequence-sequence)<0) { drop(prior); it.remove(); }
            }
            last=(sequence-1)&0xffffffffL; waiting=false;
        }
        List<FrameAssembler.Frame> ready=new ArrayList<>();
        while(!waiting) {
            Assembly next=frames.get((last+1)&0xffffffffL);
            if(next==null||!next.complete()) break;
            ByteArrayOutputStream out=new ByteArrayOutputStream(next.size);
            for(byte[] part:next.parts) out.write(part,0,part.length);
            ready.add(new FrameAssembler.Frame(out.toByteArray(),next.sequence,next.capture,next.key));
            frames.remove(next.sequence); bytes-=next.size; last=next.sequence; completed++;
        }
        return ready;
    }
    /** Each NACK contains at most 64 sorted fragment indices, matching protocol v2. */
    public List<Missing> repairs(long nowMs) {
        expire(nowMs);
        List<Missing> requests=new ArrayList<>();
        for(Assembly frame:frames.values()) {
            if(frame.complete()||nowMs-frame.repairAt<8) continue;
            frame.repairAt=nowMs;
            int[] missing=new int[64]; int count=0;
            for(int i=0;i<frame.parts.length;i++) if(frame.parts[i]==null) {
                missing[count++]=i;
                if(count==64) break;
            }
            if(count>0) { requests.add(new Missing(frame.sequence,frame.parts.length,Arrays.copyOf(missing,count))); nacks++; }
        }
        return requests;
    }
    private void expire(long nowMs) {
        for(Assembly frame:frames.values()) if(nowMs-frame.started>=MAX_AGE_MS) { recover(); return; }
    }
    private void drop(Assembly frame) { bytes-=frame.size; dropped++; lost+=frame.parts.length-frame.count; }
    private void recover() {
        for(Assembly frame:frames.values()) drop(frame);
        frames.clear(); bytes=0; waiting=true;
    }
}
