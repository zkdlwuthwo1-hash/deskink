package com.deskink.core;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Foreground connection entry point. A usable USB tunnel takes priority over a saved LAN PC. */
public final class AutoConnectSession implements AutoCloseable {
    public static final class Endpoint {
        public final String address;
        public final int port;
        public final PcRegistration registration;
        public Endpoint(String address,int port,PcRegistration registration) {
            UdpClient.endpoint(address,port);
            this.address=address; this.port=port; this.registration=registration;
        }
    }
    public interface Listener extends UdpClient.Listener {
        default void transport(boolean lan) { }
        default void remember(Endpoint endpoint) { }
    }
    private final UsbClient usb;
    private final long device;
    private final int width,height;
    private final Listener listener;
    private final Object pairingLock=new Object();
    // A retired reader may finish a frame while a new transport is being selected.
    // Serialize decoder callbacks at that boundary, never pen writes or socket I/O.
    private final Object mediaLock=new Object();
    private final ScheduledExecutorService control=Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread=new Thread(r,"DeskInk connections"); thread.setDaemon(true); return thread;
    });
    private volatile List<PcRegistration> registrations;
    private Endpoint remembered;
    private volatile UdpClient network;
    private volatile UsbClient.Binding binding;
    private volatile boolean closed,lan,usbAvailable;
    private ScheduledFuture<?> fallback;
    private volatile long networkGeneration;
    public AutoConnectSession(int usbPort,long device,int width,int height,List<PcRegistration> saved,
            Endpoint remembered,Listener listener) {
        this.device=device; this.width=width; this.height=height; this.listener=listener;
        registrations=new ArrayList<>(saved); this.remembered=remembered!=null&&remembered.registration!=null?remembered:null;
        usb=new UsbClient(usbPort,device,width,height,saved,new UsbClient.Listener() {
            public boolean usbAvailable() {
                return sync(() -> {
                    if(fallback!=null) fallback.cancel(false);
                    synchronized(mediaLock) { usbAvailable=true; lan=false; binding=null; }
                    stopLan(4); listener.transport(false);
                    synchronized(pairingLock) { usb.savedPairings(registrations); }
                    return !closed;
                });
            }
            public boolean savePairings(List<PcRegistration> saved) {
                synchronized(pairingLock) {
                    if(closed||!usbAvailable||!listener.savePairings(saved)) return false;
                    registrations=new ArrayList<>(saved); return true;
                }
            }
            public void state(UsbClient.State state) { post(() -> {
                if(!usbAvailable) return;
                if(state==UsbClient.State.DISCONNECTED) { usbAvailable=false; binding=null; scheduleFallback(); }
                listener.state(state);
            }); }
            public void stream(UsbClient.Binding next) { sync(() -> {
                if(!usbAvailable) return false;
                Endpoint savedEndpoint=AutoConnectSession.this.remembered;
                if(savedEndpoint!=null&&!savedEndpoint.registration.matches(next.token,next.token,next.pcId)) {
                    AutoConnectSession.this.remembered=null; listener.remember(null);
                }
                synchronized(mediaLock) { if(closed) return false; binding=next; listener.stream(next); }
                return true;
            }); }
            public void frame(FrameAssembler.Frame frame) {
                synchronized(mediaLock) { if(!closed&&usbAvailable&&!lan&&binding!=null) listener.frame(frame); }
            }
            public UsbClient.DecodeStats decoding() { return !lan?listener.decoding():new UsbClient.DecodeStats(0,0,0); }
        });
    }
    public int start() throws IOException {
        int port=usb.start();
        post(this::scheduleFallback);
        return port;
    }
    private void scheduleFallback() {
        if(fallback!=null) fallback.cancel(false);
        fallback=control.schedule(() -> {
            if(!closed&&!usbAvailable&&remembered!=null) startLan(remembered);
        },750,TimeUnit.MILLISECONDS);
    }
    private void startLan(Endpoint endpoint) {
        if(closed) return;
        synchronized(mediaLock) { lan=true; binding=null; }
        listener.transport(true);
        final long generation=++networkGeneration;
        network=new UdpClient(endpoint.address,endpoint.port,device,width,height,registrations,new UdpClient.Listener() {
            private boolean durablyPaired;
            public boolean savePairings(List<PcRegistration> saved) {
                synchronized(pairingLock) {
                    durablyPaired=false;
                    if(endpoint.registration!=null&&(saved.isEmpty()||!endpoint.registration.samePc(saved.get(0)))) return false;
                    if(closed||generation!=networkGeneration||!listener.savePairings(saved)) return false;
                    registrations=new ArrayList<>(saved); durablyPaired=true; return true;
                }
            }
            public void state(UsbClient.State state) { post(() -> {
                if(generation!=networkGeneration) return;
                if(state==UsbClient.State.DISCONNECTED) binding=null;
                listener.state(state);
            }); }
            public void stream(UsbClient.Binding next) { sync(() -> {
                if(generation!=networkGeneration) return false;
                if(endpoint.registration!=null&&!endpoint.registration.matches(next.token,next.token,next.pcId)) {
                    remembered=null; listener.remember(null); binding=null; stopLan(0);
                    listener.state(UsbClient.State.DISCONNECTED); listener.failure(UdpClient.Failure.UNREACHABLE);
                    return false;
                }
                if(durablyPaired&&PcRegistration.valid(next.token)) {
                    remembered=new Endpoint(endpoint.address,endpoint.port,new PcRegistration(next.token,next.pcId));
                    listener.remember(remembered);
                }
                synchronized(mediaLock) { if(closed) return false; binding=next; listener.stream(next); }
                return true;
            }); }
            public void frame(FrameAssembler.Frame frame) {
                synchronized(mediaLock) { if(!closed&&generation==networkGeneration&&binding!=null) listener.frame(frame); }
            }
            public UsbClient.DecodeStats decoding() { return listener.decoding(); }
            public void failure(UdpClient.Failure failure) { post(() -> { if(generation==networkGeneration) listener.failure(failure); }); }
        });
        UdpClient starting=network;
        try { if(!closed) starting.start(); }
        catch(IOException error) { starting.close(); if(!closed) listener.failure(UdpClient.Failure.UNREACHABLE); }
        finally { if(closed) starting.close(); }
    }
    private void stopLan(int reason) {
        ++networkGeneration; UdpClient previous=network; network=null;
        if(previous!=null) previous.disconnect(reason);
    }
    private void post(Runnable task) {
        if(closed) return;
        try { control.execute(() -> { if(!closed) task.run(); }); }
        catch(RejectedExecutionException ignored) { }
    }
    private boolean sync(Callable<Boolean> task) {
        if(closed) return false;
        Future<Boolean> pending=null;
        try { pending=control.submit(() -> !closed&&task.call()); return pending.get(5,TimeUnit.SECONDS); }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        catch(ExecutionException|TimeoutException|RejectedExecutionException failed) { }
        if(pending!=null) pending.cancel(true);
        close(); return false;
    }
    public boolean isLan() { return lan; }
    public boolean isClosed() { return closed; }
    /** Explicit address entry enables LAN, while an already usable USB connection keeps priority. */
    public void connectLan(String address,int port) {
        Endpoint endpoint=new Endpoint(address,port,null);
        post(() -> {
            if(fallback!=null) fallback.cancel(false);
            if(!usbAvailable) { stopLan(0); startLan(endpoint); }
        });
    }
    public void pair(int code) {
        if(closed) return;
        UdpClient active=network; if(lan&&active!=null) active.pair(code); else usb.pair(code);
    }
    public void requestKeyframe() {
        if(closed) return;
        UdpClient active=network; if(lan&&active!=null) active.requestKeyframe(); else usb.requestKeyframe();
    }
    public boolean owns(UsbClient.Binding expected) { return !closed&&expected!=null&&binding==expected; }
    public boolean ink(UsbClient.Binding expected,org.json.JSONObject command) {
        if(!owns(expected)) return false;
        UdpClient active=network;
        return lan?active!=null&&active.ink(expected,command):usb.ink(expected,command);
    }
    @Override public void close() {
        closed=true; binding=null; if(fallback!=null) fallback.cancel(false);
        usb.close(); UdpClient previous=network; if(previous!=null) previous.close(); control.shutdownNow();
    }
}
