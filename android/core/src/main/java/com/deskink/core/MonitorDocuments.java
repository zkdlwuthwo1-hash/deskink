package com.deskink.core;
import java.util.*;

/** Keeps the two most recently connected PC sessions, with independent monitors. */
public final class MonitorDocuments {
    private final Map<String,Map<String,InkDocument>> hosts=new LinkedHashMap<>(2,.75f,true);
    private InkDocument current=new InkDocument();
    public InkDocument select(String hostToken,String monitor) {
        current.suspend();
        Map<String,InkDocument> boards=hosts.computeIfAbsent(hostToken,ignored -> new HashMap<>());
        // A restarted Windows host has a fresh token and therefore a fresh board.
        while(hosts.size()>2) hosts.remove(hosts.keySet().iterator().next());
        current=boards.computeIfAbsent(monitor,ignored -> new InkDocument()); return current;
    }
}
