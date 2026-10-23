package com.deskink.core;

import org.junit.Test;
import java.io.*;
import static org.junit.Assert.*;

public class AppUpdateTest {
    private static String offer(String url) {
        return Ink.object("version_code",6,"version","0.6.0","url",url,"size",3,
            "sha256","ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad").toString();
    }
    private static final String URL="https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.6.0/DeskInk-Android.apk";
    @Test public void newReleaseIsOfferedAndOnlyItsCompleteVerifiedBytesAreAccepted() throws Exception {
        AppUpdate update=AppUpdate.parse(offer(URL),5);
        assertNotNull(update); assertEquals("0.6.0",update.version);
        assertNull(AppUpdate.parse(offer(URL),6)); assertNull(AppUpdate.parse(offer(URL),7));
        ByteArrayOutputStream verified=new ByteArrayOutputStream();
        update.copyVerified(new ByteArrayInputStream(new byte[]{97,98,99}),verified);
        assertArrayEquals(new byte[]{97,98,99},verified.toByteArray());
        assertThrows(IOException.class,() -> update.copyVerified(new ByteArrayInputStream(new byte[]{97,98}),new ByteArrayOutputStream()));
        assertThrows(IOException.class,() -> update.copyVerified(new ByteArrayInputStream(new byte[]{97,98,100}),new ByteArrayOutputStream()));
        assertThrows(IOException.class,() -> update.copyVerified(new ByteArrayInputStream(new byte[]{97,98,99,100}),new ByteArrayOutputStream()));
        assertThrows(IOException.class,() -> AppUpdate.parse(offer("https://example.com/DeskInk-Android.apk"),5));
        assertThrows(IOException.class,() -> AppUpdate.parse(offer(URL.replace("https:","http:")),5));
    }
}
