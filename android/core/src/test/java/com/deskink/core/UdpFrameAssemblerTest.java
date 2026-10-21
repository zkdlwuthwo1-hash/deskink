package com.deskink.core;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class UdpFrameAssemblerTest {
    @Test public void aLostFragmentIsRequestedAndOnlyTheCompleteFrameIsDelivered() throws Exception {
        UdpFrameAssembler frames=new UdpFrameAssembler(); frames.reset(7,2);
        frames.accept(packet(1,0,3,1,(byte)1),0); frames.accept(packet(1,2,3,1,(byte)3),1);
        UdpFrameAssembler.Missing missing=frames.repairs(8).get(0);
        assertEquals(1,missing.sequence); assertEquals(3,missing.count); assertArrayEquals(new int[]{1},missing.indices);
        List<FrameAssembler.Frame> ready=frames.accept(packet(1,1,3,3,(byte)2),9);
        assertArrayEquals(new byte[]{1,2,3},ready.get(0).bytes); assertEquals(1,frames.repaired);
        assertTrue(frames.accept(packet(1,1,3,3,(byte)2),10).isEmpty());
    }
    @Test public void expiredDeltaFramesNeverBuildUpOrCorruptTheNextKeyframe() throws Exception {
        UdpFrameAssembler frames=new UdpFrameAssembler(); frames.reset(7,2);
        assertEquals(1,frames.accept(packet(1,0,1,1,(byte)1),0).size());
        frames.accept(packet(3,0,1,0,(byte)3),1); // frame 2 never arrived
        assertTrue(frames.repairs(51).isEmpty()); assertTrue(frames.needsKeyframe()); assertEquals(0,frames.depth());
        assertTrue(frames.accept(packet(4,0,1,0,(byte)4),52).isEmpty());
        List<FrameAssembler.Frame> ready=frames.accept(packet(5,0,1,1,(byte)5),53);
        assertEquals(1,ready.size()); assertArrayEquals(new byte[]{5},ready.get(0).bytes);
        frames.reset(7,3); assertTrue(frames.accept(packet(6,0,1,1,(byte)6),54).isEmpty());
    }
    @Test public void reorderedFragmentsAndFramesReachTheDecoderInSequence() throws Exception {
        UdpFrameAssembler frames=new UdpFrameAssembler(); frames.reset(7,2);
        assertTrue(frames.accept(packet(1,1,2,1,(byte)2),0).isEmpty());
        assertTrue(frames.accept(packet(2,0,1,0,(byte)3),1).isEmpty());
        List<FrameAssembler.Frame> ready=frames.accept(packet(1,0,2,1,(byte)1),2);
        assertEquals(2,ready.size());
        assertArrayEquals(new byte[]{1,2},ready.get(0).bytes);
        assertArrayEquals(new byte[]{3},ready.get(1).bytes);
    }
    private static Wire.Packet packet(long seq,int index,int count,int flags,byte... bytes) throws Exception {
        return Wire.decode(FrameAssemblerTest.fragment(7,2,seq,index,count,flags,bytes));
    }
}
