package com.deskink.app;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.deskink.core.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class VideoDecoderTest {
    @Test public void mediaCodecDecodesTheSharedH264FixtureToAnAndroidSurface() throws Exception {
        CountDownLatch decoded=new CountDownLatch(1);
        SurfaceTexture texture=new SurfaceTexture(false); texture.setDefaultBufferSize(64,64);
        Surface surface=new Surface(texture);
        try(VideoDecoder decoder=new VideoDecoder(() -> {},decoded::countDown)) {
            decoder.surface(surface);
            ByteBuffer config=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            config.putInt(1).putShort((short)64).putShort((short)64).putShort((short)60).put((byte)0).put((byte)0).putInt(2000000);
            decoder.configure(Wire.decode(Wire.control(0x18,42,1,config.array())).config());
            byte[] video;
            try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("single_idr_64x64.h264"); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] b=new byte[4096]; for(int n;(n=in.read(b))!=-1;) out.write(b,0,n); video=out.toByteArray();
            }
            FrameAssembler assembler=new FrameAssembler(); assembler.reset(42,1); int total=(video.length+1367)/1368;
            for(int i=0;i<total;i++) {
                int n=Math.min(1368,video.length-1368*i); ByteBuffer packet=ByteBuffer.allocate(32+n).order(ByteOrder.LITTLE_ENDIAN);
                packet.putShort((short)0x4d45).put((byte)2).put((byte)1).put((byte)1).put((byte)0).putShort((short)n)
                    .putInt(42).putInt(1).putInt(1).putShort((short)i).putShort((short)total).putLong(1).put(video,1368*i,n);
                FrameAssembler.Frame frame=assembler.accept(Wire.decode(packet.array())); if(frame!=null) decoder.offer(frame);
            }
            assertTrue("MediaCodec must output the H264 frame",decoded.await(10,TimeUnit.SECONDS));
        } finally { surface.release(); texture.release(); }
    }
}
