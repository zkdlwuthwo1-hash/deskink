package com.deskink.app;

import android.media.*;
import android.os.*;
import android.view.Surface;
import com.deskink.core.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Hardware-backed H264 with bounded pending/in-flight frames and IDR recovery. */
public final class VideoDecoder implements AutoCloseable {
    private final HandlerThread thread=new HandlerThread("DeskInk video",android.os.Process.THREAD_PRIORITY_DISPLAY);
    private final Handler handler;
    private final Runnable requestKeyframe,decoded;
    private final ArrayDeque<FrameAssembler.Frame> pending=new ArrayDeque<>(2);
    private final ArrayDeque<Integer> inputs=new ArrayDeque<>(); // decoder-thread only
    private Wire.Config config;
    private Surface surface;
    private MediaCodec codec;
    private boolean waiting=true,resetNeeded,wake,closed;
    private int inFlight;
    public VideoDecoder(Runnable requestKeyframe,Runnable decoded) {
        this.requestKeyframe=requestKeyframe; this.decoded=decoded; thread.start(); handler=new Handler(thread.getLooper());
    }
    public synchronized void configure(Wire.Config value) {
        config=value; pending.clear(); waiting=true; resetNeeded=true; schedule();
    }
    public synchronized void surface(Surface value) {
        surface=value; pending.clear(); waiting=true; resetNeeded=true; schedule(); handler.post(requestKeyframe);
    }
    public synchronized void offer(FrameAssembler.Frame frame) {
        if(closed||surface==null||config==null) return;
        if(pending.size()>=2) { pending.clear(); waiting=true; resetNeeded=true; handler.post(requestKeyframe); }
        if(waiting&&!frame.keyframe) return;
        if(frame.keyframe) waiting=false;
        pending.addLast(frame); schedule();
    }
    private void schedule() {
        if(!closed&&!wake) { wake=true; handler.post(this::drain); }
    }
    private void drain() {
        synchronized(this) {
            wake=false; if(closed) return;
            if(resetNeeded) { disposeCodec(); resetNeeded=false; }
            if(surface==null||!surface.isValid()||config==null||pending.isEmpty()) return;
            try {
                if(codec==null) {
                    if(!pending.peekFirst().keyframe) { fail(); return; }
                    startCodec(pending.peekFirst().bytes);
                }
                while(!pending.isEmpty()&&!inputs.isEmpty()&&inFlight<2) {
                    FrameAssembler.Frame frame=pending.removeFirst(); int index=inputs.removeFirst();
                    ByteBuffer buffer=codec.getInputBuffer(index);
                    if(buffer==null||buffer.capacity()<frame.bytes.length) { fail(); return; }
                    buffer.clear(); buffer.put(frame.bytes);
                    codec.queueInputBuffer(index,0,frame.bytes.length,System.nanoTime()/1000,0); inFlight++;
                }
            } catch(Exception error) { fail(); }
        }
    }
    private void startCodec(byte[] keyframe) throws Exception {
        byte[] sps=nal(keyframe,7),pps=nal(keyframe,8);
        if(sps==null||pps==null) throw new IllegalArgumentException("Missing H264 parameter sets");
        MediaFormat format=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,config.width,config.height);
        format.setByteBuffer("csd-0",ByteBuffer.wrap(sps)); format.setByteBuffer("csd-1",ByteBuffer.wrap(pps));
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,FrameAssembler.MAX_FRAME); format.setInteger(MediaFormat.KEY_PRIORITY,0);
        if(Build.VERSION.SDK_INT>=30) format.setInteger(MediaFormat.KEY_LOW_LATENCY,1);
        codec=MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        codec.setCallback(new MediaCodec.Callback() {
            @Override public void onInputBufferAvailable(MediaCodec source,int index) {
                synchronized(VideoDecoder.this) { if(source!=codec||closed) return; inputs.addLast(index); schedule(); }
            }
            @Override public void onOutputBufferAvailable(MediaCodec source,int index,MediaCodec.BufferInfo info) {
                synchronized(VideoDecoder.this) {
                    if(source!=codec||closed) return;
                    try { source.releaseOutputBuffer(index,true); inFlight=Math.max(0,inFlight-1); handler.post(decoded); schedule(); }
                    catch(IllegalStateException error) { fail(); }
                }
            }
            @Override public void onOutputFormatChanged(MediaCodec source,MediaFormat format) { }
            @Override public void onError(MediaCodec source,MediaCodec.CodecException error) {
                synchronized(VideoDecoder.this) { if(source==codec) fail(); }
            }
        },handler);
        codec.configure(format,surface,null,0); codec.start();
    }
    private void fail() {
        disposeCodec(); pending.clear(); waiting=true; handler.post(requestKeyframe);
    }
    private void disposeCodec() {
        MediaCodec previous=codec; codec=null; inputs.clear(); inFlight=0;
        if(previous!=null) { try { previous.stop(); } catch(IllegalStateException ignored) { } finally { previous.release(); } }
    }
    private static byte[] nal(byte[] bytes,int type) {
        for(int at=0;at+3<bytes.length;) {
            int prefix=prefix(bytes,at);
            if(prefix==0) { at++; continue; }
            int end=at+prefix;
            while(end+3<bytes.length&&prefix(bytes,end)==0) end++;
            if(end+3>=bytes.length) end=bytes.length;
            if(at+prefix<bytes.length&&(bytes[at+prefix]&31)==type) return Arrays.copyOfRange(bytes,at,end);
            at=end;
        }
        return null;
    }
    private static int prefix(byte[] bytes,int i) {
        if(i+2>=bytes.length||bytes[i]!=0||bytes[i+1]!=0) return 0;
        if(bytes[i+2]==1) return 3;
        return i+3<bytes.length&&bytes[i+2]==0&&bytes[i+3]==1?4:0;
    }
    @Override public synchronized void close() {
        if(closed) return; closed=true; pending.clear();
        handler.post(() -> { synchronized(VideoDecoder.this) { disposeCodec(); } thread.quitSafely(); });
    }
}
