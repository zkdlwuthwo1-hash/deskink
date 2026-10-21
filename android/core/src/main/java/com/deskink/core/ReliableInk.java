package com.deskink.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import org.json.JSONObject;

/** A bounded, ordered pen send window. Clock values are monotonic milliseconds. */
public final class ReliableInk {
    private static final int LIMIT=256,RETRY_BATCH=32;
    private static final long RETRY_MS=20,MAX_AGE_MS=250;
    private final long session,epoch;
    private long sequence,lastRetry;
    private boolean failed;
    private final NavigableMap<Long,Pending> pending=new TreeMap<>();
    private static final class Pending {
        final byte[] bytes;
        final long started;
        Pending(byte[] bytes,long started) { this.bytes=bytes; this.started=started; }
    }
    public ReliableInk(long session,long epoch) {
        if(session<=0||epoch<=0) throw new IllegalArgumentException("active stream required");
        this.session=session; this.epoch=epoch;
    }
    /** Returns the immediate datagram, or null when the session must disconnect. */
    public byte[] offer(JSONObject command,long nowMs) {
        if(expired(nowMs)||pending.size()>=LIMIT) { failed=true; return null; }
        byte[] bytes=Ink.envelope(session,epoch,sequence+1,command);
        if(bytes.length>1028) { failed=true; return null; }
        bytes[3]='2';
        if(pending.isEmpty()) lastRetry=nowMs;
        pending.put(++sequence,new Pending(bytes,nowMs));
        return bytes.clone();
    }
    public void acknowledge(byte[] bytes) {
        if(bytes.length!=20||bytes[0]!='D'||bytes[1]!='A'||bytes[2]!='0'||bytes[3]!='2') return;
        ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); b.position(4);
        if(Integer.toUnsignedLong(b.getInt())!=session||Integer.toUnsignedLong(b.getInt())!=epoch) return;
        long ack=b.getLong();
        if(ack<0||ack>sequence) return;
        pending.headMap(ack,true).clear();
    }
    public List<byte[]> retry(long nowMs) {
        if(expired(nowMs)||nowMs-lastRetry<RETRY_MS) return Collections.emptyList();
        lastRetry=nowMs;
        List<byte[]> packets=new ArrayList<>();
        for(Pending item:pending.values()) {
            packets.add(item.bytes.clone());
            if(packets.size()==RETRY_BATCH) break;
        }
        return packets;
    }
    public boolean expired(long nowMs) {
        if(!pending.isEmpty()&&nowMs-pending.firstEntry().getValue().started>=MAX_AGE_MS) failed=true;
        return failed;
    }
}
