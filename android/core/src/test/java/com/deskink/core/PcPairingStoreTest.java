package com.deskink.core;

import org.junit.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class PcPairingStoreTest {
    @Test public void legacyRegistrationMigratesAndBothPcsSurviveReplacingTheFile() throws Exception {
        Path folder=Files.createTempDirectory("deskink-pairing-test-"); Path file=folder.resolve("pcs");
        try {
            byte[] first=new byte[16],second=new byte[16]; Arrays.fill(first,(byte)1); Arrays.fill(second,(byte)2);
            PcPairingStore store=new PcPairingStore(file);
            List<PcRegistration> migrated=store.load("0102030405060708090a0b0c0d0e0f10");
            assertEquals(1,migrated.size());
            List<PcRegistration> saved=Arrays.asList(new PcRegistration(first,first),new PcRegistration(second,second));
            store.save(saved);
            Collections.reverse(saved); store.save(saved);
            List<PcRegistration> restored=new PcPairingStore(file).load("");
            assertEquals(2,restored.size());
            assertEquals(saved.get(0).encode(),restored.get(0).encode());
            assertEquals(saved.get(1).encode(),restored.get(1).encode());
            try(java.util.stream.Stream<Path> files=Files.list(folder)) { assertEquals(1,files.count()); }
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(folder); }
    }
}
