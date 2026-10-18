package com.deskink.core;
import org.junit.Test;
import java.nio.*;
import static org.junit.Assert.*;

public class FrameAssemblerTest {
    @Test public void aWholeMissingDeltaFrameRequiresANewKeyframeBeforeVideoResumes() throws Exception {
        FrameAssembler a=new FrameAssembler(); a.reset(7,2);
        assertNotNull(a.accept(Wire.decode(fragment(7,2,1,0,1,1,(byte)1))));
        assertNull(a.accept(Wire.decode(fragment(7,2,3,0,1,0,(byte)3))));
        assertTrue(a.needsKeyframe());
        assertNull(a.accept(Wire.decode(fragment(7,2,4,0,1,0,(byte)4))));
        assertNotNull(a.accept(Wire.decode(fragment(7,2,5,0,1,1,(byte)5))));
        assertNotNull(a.accept(Wire.decode(fragment(7,2,6,0,1,0,(byte)6))));
    }
    static byte[] fragment(long session,long epoch,long seq,int index,int count,int flags,byte... body) {
        ByteBuffer b=ByteBuffer.allocate(32+body.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)0x4d45).put((byte)2).put((byte)1).put((byte)flags).put((byte)0)
          .putShort((short)body.length).putInt((int)session).putInt((int)epoch).putInt((int)seq)
          .putShort((short)index).putShort((short)count).putLong(999).put(body);
        return b.array();
    }
    @Test public void monitorChangeRejectsOldVideoAndOnlyDeliversCompleteOrderedFrames() throws Exception {
        FrameAssembler a=new FrameAssembler(); a.reset(7,2);
        assertNull(a.accept(Wire.decode(fragment(7,1,1,0,1,1,(byte)9))));
        assertNull(a.accept(Wire.decode(fragment(7,2,2,0,2,1,(byte)1,(byte)2))));
        FrameAssembler.Frame frame=a.accept(Wire.decode(fragment(7,2,2,1,2,1,(byte)3)));
        assertArrayEquals(new byte[]{1,2,3},frame.bytes); assertTrue(frame.keyframe);
        assertNull(a.accept(Wire.decode(fragment(7,2,3,1,2,0,(byte)4))));
        assertNull(a.accept(Wire.decode(fragment(7,2,4,0,1,0,(byte)5))));
        assertNotNull(a.accept(Wire.decode(fragment(7,2,5,0,1,1,(byte)6))));
    }
}
