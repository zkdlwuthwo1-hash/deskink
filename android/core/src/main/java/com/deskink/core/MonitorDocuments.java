package com.deskink.core;
import java.util.*;

/** Mirrors Windows/iPad: monitor boards survive stream switches, not host restarts. */
public final class MonitorDocuments {
    private final Map<String,InkDocument> boards=new HashMap<>();
    private String token;
    private InkDocument current=new InkDocument();
    public InkDocument select(String hostToken,String monitor) {
        current.suspend();
        if(!Objects.equals(token,hostToken)) { boards.clear(); token=hostToken; }
        current=boards.computeIfAbsent(monitor,ignored -> new InkDocument()); return current;
    }
}
