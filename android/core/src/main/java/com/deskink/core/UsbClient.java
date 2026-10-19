package com.deskink.core;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Loopback-only USB session. The socket writer never runs on the pen/UI thread. */
public final class UsbClient implements AutoCloseable {
    public enum State { PAIRING, RATE_LIMITED, BUSY, CONNECTED, DISCONNECTED, INCOMPATIBLE }
    public interface Listener {
        void state(State state);
        void stream(Binding binding);
        void frame(FrameAssembler.Frame frame);
        /** Called off the UI thread. Only a successful durable write promotes credentials. */
        default boolean savePairings(List<PcRegistration> registrations) { return true; }
        default DecodeStats decoding() { return new DecodeStats(0,0,0); }
    }
    public static final class DecodeStats {
        public final long completed;
        public final int pending,inFlight;
        public DecodeStats(long completed,int pending,int inFlight) {
            this.completed=completed; this.pending=pending; this.inFlight=inFlight;
        }
    }
    public static final class Binding {
        public final long session;
        public final Wire.Config config;
        public final String host;
        public final byte[] token;
        Binding(long session,Wire.Config config,String host,byte[] token) {
            this.session=session; this.config=config; this.host=host; this.token=token.clone();
        }
    }
    private final Object lock=new Object();
    private final int port,width,height;
    private final long device;
    private final List<PcRegistration> registrations=new ArrayList<>();
    private final Listener listener;
    private final SecureRandom random=new SecureRandom();
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r -> daemon(r,"DeskInk heartbeat"));
    private volatile boolean closed;
    private ServerSocket server;
    private Connection current;
    private Binding binding;
    private long inkSequence,controlSequence,nonce,lastKeyframe;
    private int tokenIndex,pairingCode;
    private static Thread daemon(Runnable task,String name) { Thread t=new Thread(task,name); t.setDaemon(true); return t; }
    public UsbClient(int port,long device,int width,int height,List<PcRegistration> saved,Listener listener) {
        this.port=port; this.device=device; this.width=width; this.height=height; this.listener=listener;
        for(PcRegistration pc:saved) if(pc!=null&&registrations.size()<2) registrations.add(pc);
    }
    /** Snapshot for durable app storage; callers cannot mutate live credentials. */
    public List<PcRegistration> savedPairings() {
        synchronized(lock) { return new ArrayList<>(registrations); }
    }
    /** Call off the Android main thread. Returns the actual port for socket integration tests. */
    public int start() throws IOException {
        synchronized(lock) {
            if(closed||server!=null) throw new IOException("USB listener unavailable");
            server=new ServerSocket(); server.setReuseAddress(true);
            server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),port),1);
            daemon(this::accept,"DeskInk USB reader").start();
            timer.scheduleAtFixedRate(this::tick,500,500,TimeUnit.MILLISECONDS);
            return server.getLocalPort();
        }
    }
    private void accept() {
        while(!closed) {
            Connection candidate=null;
            try {
                Socket socket=server.accept();
                FramedConnection link=new FramedConnection(socket);
                socket.setSoTimeout(0); // handshake and session liveness are checked by the timer
                candidate=new Connection(link);
                synchronized(lock) {
                    if(closed) { candidate.close(); return; }
                    current=candidate; binding=null; tokenIndex=0; pairingCode=0; hello();
                }
                while(!closed&&!candidate.stopped) receive(candidate,Wire.decode(link.read()));
            } catch(IOException ignored) {
                // USB removal, host switch and shutdown all close the framed socket.
            } finally {
                if(candidate!=null) candidate.close();
                boolean disconnected=false;
                synchronized(lock) {
                    if(current==candidate) { current=null; binding=null; disconnected=!closed; }
                }
                if(disconnected) listener.state(State.DISCONNECTED);
            }
        }
    }
    private final class Connection {
        final FramedConnection link;
        final ArrayBlockingQueue<byte[]> outgoing=new ArrayBlockingQueue<>(256);
        final FrameAssembler frames=new FrameAssembler();
        final Thread writer;
        volatile boolean stopped;
        volatile long lastPacket=System.nanoTime();
        boolean awaitingPairing;
        long highest;
        long reportedDecoded=-1,reportedAt;
        Connection(FramedConnection link) {
            this.link=link;
            writer=daemon(() -> {
                try { while(!stopped) link.send(outgoing.take()); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); }
                catch(IOException ignored) { }
                finally { close(); }
            },"DeskInk USB writer"); writer.start();
        }
        boolean offer(byte[] bytes) {
            if(stopped) return false;
            if(!outgoing.offer(bytes)) { close(); return false; }
            return true;
        }
        void close() {
            stopped=true; try { link.close(); } catch(IOException ignored) { }
            writer.interrupt(); outgoing.clear();
        }
    }
    private void hello() {
        if(current==null) return;
        nonce=Integer.toUnsignedLong(random.nextInt());
        current.lastPacket=System.nanoTime();
        current.offer(Wire.hello(++controlSequence,nonce,9877,1,9,width,height,width,height,60,
            "DeskInk Android",device,60,presentedToken(),pairingCode));
    }
    private byte[] presentedToken() {
        return pairingCode==0&&!registrations.isEmpty()?registrations.get(tokenIndex).token():new byte[16];
    }
    public void pair(int code) {
        if(code<1||code>999999) throw new IllegalArgumentException("six-digit code");
        synchronized(lock) { if(binding!=null) return; pairingCode=code; hello(); }
    }
    public boolean ink(Binding expected,JSONObject command) {
        synchronized(lock) {
            if(expected==null||binding!=expected||current==null) return false;
            return current.offer(Ink.envelope(binding.session,binding.config.epoch,++inkSequence,command));
        }
    }
    public void requestKeyframe() {
        synchronized(lock) {
            long now=System.nanoTime();
            if(binding!=null&&current!=null&&now-lastKeyframe>150_000_000L) {
                lastKeyframe=now;
                current.offer(Wire.keyframe(binding.session,++controlSequence,binding.config.epoch,current.highest,1));
            }
        }
    }
    private void receive(Connection source,Wire.Packet packet) throws IOException {
        Runnable notification;
        synchronized(lock) { notification=receiveLocked(source,packet); }
        // Video/UI consumers may be busy; never hold the pen writer's lock here.
        if(notification!=null) notification.run();
    }
    private Runnable receiveLocked(Connection source,Wire.Packet packet) throws IOException {
        if(current!=source) return null;
        if(packet.type==0x11) {
            Wire.Ack ack=packet.ack(); if(ack.nonce!=nonce) return null;
            source.lastPacket=System.nanoTime();
            if(ack.status!=0) {
                source.awaitingPairing=true;
                if((ack.status==4||ack.status==5)&&pairingCode==0&&tokenIndex+1<registrations.size()) { tokenIndex++; hello(); return null; }
                State state=ack.status==5?State.RATE_LIMITED:ack.status==1?State.BUSY:State.PAIRING;
                return () -> listener.state(state);
            }
            if(ack.version!=2||ack.session==0||(ack.caps&0x7000)!=0x7000) {
                source.close(); return () -> listener.state(State.INCOMPATIBLE);
            }
            if(binding!=null&&binding.session==ack.session&&binding.config.epoch==ack.config.epoch) return null;
            Binding next=new Binding(ack.session,ack.config,ack.host,ack.token);
            configure(source,next);
            List<PcRegistration> saved=new ArrayList<>(registrations);
            if(PcRegistration.valid(ack.token)) {
                byte[] presented=presentedToken();
                saved.removeIf(pc -> pc.matches(presented,ack.token,ack.pcId));
                saved.add(0,new PcRegistration(ack.token,ack.pcId));
                while(saved.size()>2) saved.remove(saved.size()-1);
            }
            return () -> {
                if(listener.savePairings(Collections.unmodifiableList(saved))) {
                    synchronized(lock) {
                        if(!closed&&current==source) { registrations.clear(); registrations.addAll(saved); tokenIndex=0; }
                    }
                }
                listener.stream(next); listener.state(State.CONNECTED);
            };
        }
        if(binding==null||packet.session!=binding.session) return null;
        source.lastPacket=System.nanoTime();
        if(packet.type==0x12||packet.type==0x18) {
            Wire.Config config=packet.config();
            if(config.epoch!=binding.config.epoch&&(int)(config.epoch-binding.config.epoch)>0) {
                Binding next=new Binding(binding.session,config,binding.host,binding.token);
                configure(source,next); return () -> listener.stream(next);
            }
        } else if(packet.type==0x13) { source.close(); }
        else if(packet.type==0x16) {
            ByteBuffer input=packet.body(); Wire.require(input.remaining()>=8);
            ByteBuffer out=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
            long now=System.nanoTime()/1000; out.putLong(input.getLong()).putLong(now).putLong(now);
            source.offer(Wire.control(0x17,binding.session,++controlSequence,out.array()));
        } else if(packet.type==1) {
            FrameAssembler.Frame frame=source.frames.accept(packet);
            if(frame!=null) { source.highest=frame.sequence; return () -> listener.frame(frame); }
            else if(source.frames.needsKeyframe()) requestKeyframe();
        }
        return null;
    }
    private void configure(Connection source,Binding next) throws IOException {
        Wire.Config c=next.config;
        Wire.require(c.epoch!=0&&c.codec==0&&c.width>0&&c.height>0&&c.width<=8192&&c.height<=8192&&(long)c.width*c.height<=16777216);
        binding=next; inkSequence=0; source.lastPacket=System.nanoTime(); source.frames.reset(next.session,c.epoch);
        source.outgoing.clear(); lastKeyframe=0;
        source.reportedDecoded=-1;
        requestKeyframe();
    }
    private void tick() {
        DecodeStats decoding=listener.decoding();
        synchronized(lock) {
            if(current==null) return;
            long timeout=binding==null&&current.awaitingPairing?60_000_000_000L:5_000_000_000L;
            if(System.nanoTime()-current.lastPacket>timeout) { current.close(); return; }
            if(binding==null) return;
            long now=System.nanoTime();
            int fps=current.reportedDecoded<0?0:(int)Math.min(65535,
                Math.max(0,decoding.completed-current.reportedDecoded)*10_000_000_000.0/Math.max(1,now-current.reportedAt));
            current.reportedDecoded=decoding.completed; current.reportedAt=now;
            FrameAssembler f=current.frames;
            current.offer(Wire.report(binding.session,++controlSequence,binding.config.epoch,current.highest,
                f.completed,f.dropped,f.fragments,0,0,fps,decoding.pending,decoding.inFlight,0,0));
        }
    }
    @Override public void close() {
        synchronized(lock) {
            closed=true; binding=null; if(current!=null) current.close();
            try { if(server!=null) server.close(); } catch(IOException ignored) { }
            timer.shutdownNow();
        }
    }
}
