package com.deskink.core;

import java.io.IOException;
import java.net.*;
import java.nio.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Explicit LAN connection: UDP video plus a separate acknowledged pen send window. */
public final class UdpClient implements AutoCloseable {
    public enum Failure { UNREACHABLE, CONNECTION_LOST, INK_DELAY }
    public interface Listener extends UsbClient.Listener {
        default void failure(Failure failure) { }
    }
    private final Object lock=new Object();
    private final String host;
    private final int port,width,height;
    private final long device;
    private final Listener listener;
    private final List<PcRegistration> registrations=new ArrayList<>();
    private final ArrayBlockingQueue<byte[]> outgoing=new ArrayBlockingQueue<>(256);
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r -> daemon(r,"DeskInk LAN timer"));
    private final UdpFrameAssembler frames=new UdpFrameAssembler();
    private volatile boolean closed;
    private DatagramSocket socket;
    private Thread writer;
    private UsbClient.Binding binding;
    private ReliableInk ink;
    private long nonce,controlSequence,lastPacket,lastHello,lastReport,lastKeyframe,highest,reportedDecoded=-1,reportedAt;
    private int tokenIndex,pairingCode,helloAttempts;
    private boolean awaitingPairing;
    private byte[] pendingHello;
    private static long now() { return System.nanoTime()/1_000_000; }
    private static Thread daemon(Runnable task,String name) { Thread t=new Thread(task,name); t.setDaemon(true); return t; }
    public UdpClient(String host,int port,long device,int width,int height,List<PcRegistration> saved,Listener listener) {
        this.host=host; this.port=port; this.device=device; this.width=width; this.height=height; this.listener=listener;
        for(PcRegistration pc:saved) if(pc!=null&&registrations.size()<2) registrations.add(pc);
    }
    /** Numeric unicast IPv4; a campus LAN may use globally routable addresses. */
    public static InetSocketAddress endpoint(String host,int port) {
        if(port<1||port>65535||host==null||!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) throw new IllegalArgumentException("LAN IPv4 and port required");
        String[] parts=host.split("\\."); byte[] address=new byte[4];
        for(int i=0;i<4;i++) { int part=Integer.parseInt(parts[i]); if(part>255) throw new IllegalArgumentException("Invalid IPv4"); address[i]=(byte)part; }
        try {
            InetAddress ip=InetAddress.getByAddress(address);
            if(ip.isAnyLocalAddress()||ip.isMulticastAddress()||(address[0]&255)==0||(address[0]&255)>=240) throw new IllegalArgumentException("Use the PC's unicast address");
            return new InetSocketAddress(ip,port);
        } catch(UnknownHostException impossible) { throw new IllegalArgumentException(impossible); }
    }
    /** Opens sockets off the UI thread; returns the tablet's actual UDP port. */
    public int start() throws IOException {
        synchronized(lock) {
            if(closed||socket!=null) throw new IOException("LAN client unavailable");
            InetSocketAddress destination=endpoint(host,port);
            socket=new DatagramSocket(); socket.setReceiveBufferSize(256*1024); socket.setSendBufferSize(32*1024);
            socket.connect(destination); // the kernel rejects datagrams from other peers
            writer=daemon(this::write,"DeskInk LAN writer"); writer.start();
            hello(); daemon(this::read,"DeskInk LAN reader").start();
            timer.scheduleAtFixedRate(this::tick,5,5,TimeUnit.MILLISECONDS);
            return socket.getLocalPort();
        }
    }
    public List<PcRegistration> savedPairings() { synchronized(lock) { return new ArrayList<>(registrations); } }
    private void write() {
        try { while(!closed) { byte[] bytes=outgoing.take(); socket.send(new DatagramPacket(bytes,bytes.length)); } }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        catch(IOException ignored) { fail(Failure.CONNECTION_LOST); }
    }
    private void read() {
        try {
            while(!closed) {
                byte[] bytes=new byte[2048]; DatagramPacket packet=new DatagramPacket(bytes,bytes.length); socket.receive(packet);
                bytes=Arrays.copyOf(bytes,packet.getLength());
                if(bytes.length>=4&&bytes[0]=='D'&&bytes[1]=='A'&&bytes[2]=='0'&&bytes[3]=='2') {
                    synchronized(lock) { if(ink!=null) ink.acknowledge(bytes); }
                    continue;
                }
                try { receive(Wire.decode(bytes)); } catch(IOException ignored) { /* malformed LAN traffic cannot reset the live stream */ }
            }
        } catch(IOException ignored) { fail(Failure.CONNECTION_LOST); }
    }
    private boolean offer(byte[] bytes) {
        if(closed) return false;
        if(outgoing.offer(bytes)) return true;
        fail(Failure.INK_DELAY); return false;
    }
    private byte[] presentedToken() {
        return pairingCode==0&&!registrations.isEmpty()?registrations.get(tokenIndex).token():new byte[16];
    }
    private void hello() {
        if(socket==null||closed) return;
        nonce=Integer.toUnsignedLong(new SecureRandom().nextInt());
        lastPacket=lastHello=now(); helloAttempts=1; awaitingPairing=false;
        // input + NACK + monitor boards + reliable DeskInk ink
        pendingHello=Wire.hello(++controlSequence,nonce,socket.getLocalPort(),1,0x1d,width,height,width,height,60,
            "DeskInk Android",device,60,presentedToken(),pairingCode);
        offer(pendingHello);
    }
    public void pair(int code) {
        if(code<1||code>999999) throw new IllegalArgumentException("six-digit code");
        synchronized(lock) { if(binding!=null||closed) return; pairingCode=code; hello(); }
    }
    public boolean ink(UsbClient.Binding expected,JSONObject command) {
        synchronized(lock) {
            if(expected==null||binding!=expected||ink==null||closed) return false;
            byte[] packet=ink.offer(command,now());
            if(packet==null) { fail(Failure.INK_DELAY); return false; }
            return offer(packet);
        }
    }
    public void requestKeyframe() {
        synchronized(lock) {
            long now=now();
            if(binding!=null&&!closed&&now-lastKeyframe>=500) {
                lastKeyframe=now;
                offer(Wire.keyframe(binding.session,++controlSequence,binding.config.epoch,highest,1));
            }
        }
    }
    private void receive(Wire.Packet packet) throws IOException {
        Runnable notification;
        synchronized(lock) { notification=receiveLocked(packet); }
        if(notification!=null&&!closed) notification.run();
    }
    private Runnable receiveLocked(Wire.Packet packet) throws IOException {
        if(closed) return null;
        if(packet.type==0x11) {
            Wire.Ack ack=packet.ack(); if(ack.nonce!=nonce) return null;
            lastPacket=now();
            if(ack.status!=0) {
                awaitingPairing=true;
                if((ack.status==4||ack.status==5)&&pairingCode==0&&tokenIndex+1<registrations.size()) { tokenIndex++; hello(); return null; }
                UsbClient.State state=ack.status==5?UsbClient.State.RATE_LIMITED:ack.status==1?UsbClient.State.BUSY:UsbClient.State.PAIRING;
                return () -> listener.state(state);
            }
            if(ack.version!=2||ack.session==0||(ack.caps&0xf000)!=0xf000) {
                return () -> { listener.state(UsbClient.State.INCOMPATIBLE); close(); };
            }
            if(binding!=null&&binding.session==ack.session&&binding.config.epoch==ack.config.epoch) return null;
            UsbClient.Binding next=new UsbClient.Binding(ack.session,ack.config,ack.host,ack.token);
            configure(next);
            List<PcRegistration> saved=new ArrayList<>(registrations);
            if(PcRegistration.valid(ack.token)) {
                byte[] presented=presentedToken(); saved.removeIf(pc -> pc.matches(presented,ack.token,ack.pcId));
                saved.add(0,new PcRegistration(ack.token,ack.pcId)); while(saved.size()>2) saved.remove(saved.size()-1);
            }
            return () -> {
                if(listener.savePairings(Collections.unmodifiableList(saved))) synchronized(lock) {
                    if(!closed) { registrations.clear(); registrations.addAll(saved); tokenIndex=0; }
                }
                if(!closed) { listener.stream(next); listener.state(UsbClient.State.CONNECTED); }
            };
        }
        if(binding==null||packet.session!=binding.session) return null;
        lastPacket=now();
        if(packet.type==0x12||packet.type==0x18) {
            Wire.Config config=packet.config();
            if(config.epoch!=binding.config.epoch&&(int)(config.epoch-binding.config.epoch)>0) {
                UsbClient.Binding next=new UsbClient.Binding(binding.session,config,binding.host,binding.token);
                configure(next); return () -> listener.stream(next);
            }
        } else if(packet.type==0x13) { return () -> fail(Failure.CONNECTION_LOST); }
        else if(packet.type==0x16) {
            ByteBuffer input=packet.body(); Wire.require(input.remaining()>=8);
            long micros=System.nanoTime()/1000;
            byte[] pong=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).putLong(input.getLong()).putLong(micros).putLong(micros).array();
            offer(Wire.control(0x17,binding.session,++controlSequence,pong));
        } else if(packet.type==1) {
            List<FrameAssembler.Frame> ready=frames.accept(packet,now());
            if(!ready.isEmpty()) { highest=ready.get(ready.size()-1).sequence; return () -> { for(FrameAssembler.Frame frame:ready) listener.frame(frame); }; }
        }
        return null;
    }
    private void configure(UsbClient.Binding next) throws IOException {
        Wire.Config c=next.config;
        Wire.require(c.epoch!=0&&c.codec==0&&c.width>0&&c.height>0&&c.width<=8192&&c.height<=8192&&(long)c.width*c.height<=16777216);
        binding=next; ink=new ReliableInk(next.session,c.epoch); frames.reset(next.session,c.epoch);
        outgoing.clear(); highest=0; lastKeyframe=0; reportedDecoded=-1; lastReport=lastPacket=now(); requestKeyframe();
    }
    private void tick() {
        synchronized(lock) {
            if(closed||socket==null) return;
            long now=now();
            if(binding==null) {
                if(awaitingPairing) { if(now-lastPacket>60_000) fail(Failure.UNREACHABLE); return; }
                if(now-lastHello>=250) {
                    if(helloAttempts>=8) { fail(Failure.UNREACHABLE); return; }
                    helloAttempts++; lastHello=now; offer(pendingHello);
                }
                return;
            }
            if(now-lastPacket>3_000) { fail(Failure.CONNECTION_LOST); return; }
            if(ink.expired(now)) { fail(Failure.INK_DELAY); return; }
            for(byte[] retry:ink.retry(now)) if(!offer(retry)) return;
            for(UdpFrameAssembler.Missing missing:frames.repairs(now)) {
                ByteBuffer body=ByteBuffer.allocate(11+missing.indices.length*2).order(ByteOrder.LITTLE_ENDIAN);
                body.putInt((int)binding.config.epoch).putInt((int)missing.sequence).putShort((short)missing.count).put((byte)missing.indices.length);
                for(int index:missing.indices) body.putShort((short)index);
                if(!offer(Wire.control(0x19,binding.session,++controlSequence,body.array()))) return;
            }
            if(frames.needsKeyframe()) requestKeyframe();
            if(now-lastReport>=500) {
                UsbClient.DecodeStats decoding=listener.decoding();
                int fps=reportedDecoded<0?0:(int)Math.min(65535,Math.max(0,decoding.completed-reportedDecoded)*10_000L/Math.max(1,now-reportedAt));
                reportedDecoded=decoding.completed; reportedAt=lastReport=now;
                byte[] basic=Wire.report(binding.session,++controlSequence,binding.config.epoch,highest,
                    frames.completed,frames.dropped,frames.fragments,frames.lost,0,fps,frames.depth(),decoding.inFlight+decoding.pending,0,0);
                byte[] report=Arrays.copyOf(basic,basic.length+14);
                ByteBuffer extended=ByteBuffer.wrap(report).order(ByteOrder.LITTLE_ENDIAN);
                extended.putShort(6,(short)(report.length-16)); extended.position(basic.length);
                extended.putInt((int)frames.repaired).putInt((int)frames.nacks).putInt(0).putShort((short)0);
                offer(report);
            }
        }
    }
    private void fail(Failure failure) {
        synchronized(lock) { if(closed) return; close(); }
        listener.state(UsbClient.State.DISCONNECTED); listener.failure(failure);
    }
    @Override public void close() {
        synchronized(lock) {
            closed=true; binding=null; ink=null; outgoing.clear(); timer.shutdownNow();
            if(socket!=null) socket.close(); if(writer!=null) writer.interrupt();
        }
    }
}
