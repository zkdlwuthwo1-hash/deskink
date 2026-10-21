package com.deskink.core;

import java.util.Arrays;

/** Immutable saved PC identity and its current reconnect credential. */
public final class PcRegistration {
    private final byte[] token,pcId;
    public PcRegistration(byte[] token,byte[] pcId) {
        if(!valid(token)||pcId.length!=16) throw new IllegalArgumentException("PC registration");
        this.token=token.clone(); this.pcId=pcId.clone();
    }
    public PcRegistration(byte[] token) { this(token,new byte[16]); }
    static boolean valid(byte[] bytes) {
        if(bytes.length!=16) return false;
        for(byte b:bytes) if(b!=0) return true;
        return false;
    }
    public byte[] token() { return token.clone(); }
    boolean matches(byte[] presented,byte[] issued,byte[] identity) {
        return Arrays.equals(token,presented)||Arrays.equals(token,issued)||(valid(identity)&&Arrays.equals(pcId,identity));
    }
    boolean samePc(PcRegistration other) { return other!=null&&matches(other.token,other.token,other.pcId); }
    public String encode() {
        char[] alphabet="0123456789abcdef".toCharArray(); StringBuilder text=new StringBuilder(64);
        for(byte[] part:new byte[][]{token,pcId}) for(byte b:part) text.append(alphabet[(b&255)>>4]).append(alphabet[b&15]);
        return text.toString();
    }
    public static PcRegistration decode(String text) {
        if(!text.matches("[0-9a-f]{32}([0-9a-f]{32})?")) return null;
        byte[] bytes=new byte[32];
        for(int i=0;i<text.length()/2;i++) bytes[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16);
        byte[] token=Arrays.copyOf(bytes,16);
        return valid(token)?new PcRegistration(token,Arrays.copyOfRange(bytes,16,32)):null;
    }
}
