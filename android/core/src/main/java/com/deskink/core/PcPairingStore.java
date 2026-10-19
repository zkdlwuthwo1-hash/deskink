package com.deskink.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Two PC registrations committed together before becoming reconnect candidates. */
public final class PcPairingStore {
    private final Path file;
    public PcPairingStore(Path file) { this.file=file; }
    public List<PcRegistration> load(String legacy) throws IOException {
        String contents;
        try {
            if(Files.size(file)>1024) throw new IOException("Invalid saved PC registrations");
            contents=new String(Files.readAllBytes(file),StandardCharsets.US_ASCII);
        } catch(NoSuchFileException missing) { contents=legacy; }
        List<PcRegistration> saved=new ArrayList<>();
        for(String entry:contents.split(",")) {
            PcRegistration pc=PcRegistration.decode(entry);
            if(pc!=null&&saved.size()<2) saved.add(pc);
        }
        return saved;
    }
    public void save(List<PcRegistration> saved) throws IOException {
        if(saved.size()>2) throw new IOException("Too many PC registrations");
        List<String> entries=new ArrayList<>();
        for(PcRegistration pc:saved) entries.add(pc.encode());
        Files.createDirectories(file.getParent());
        Path temporary=Files.createTempFile(file.getParent(),"pc-registration-",".tmp");
        try {
            try(FileChannel output=FileChannel.open(temporary,StandardOpenOption.WRITE)) {
                ByteBuffer bytes=ByteBuffer.wrap(String.join(",",entries).getBytes(StandardCharsets.US_ASCII));
                while(bytes.hasRemaining()) output.write(bytes);
                output.force(true);
            }
            Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
