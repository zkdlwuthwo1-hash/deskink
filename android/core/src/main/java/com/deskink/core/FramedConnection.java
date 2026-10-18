package com.deskink.core;
import java.io.*;
import java.net.Socket;
import java.util.Arrays;

/** One authenticated-protocol candidate on an ADB loopback tunnel. */
public final class FramedConnection implements AutoCloseable {
    private final Socket socket;
    private final DataInputStream input;
    private final OutputStream output;
    public FramedConnection(Socket socket) throws IOException {
        this.socket=socket;
        try {
            socket.setTcpNoDelay(true); socket.setSoTimeout(2500);
            input=new DataInputStream(new BufferedInputStream(socket.getInputStream(),32768));
            output=socket.getOutputStream();
            byte[] preamble=new byte[8]; input.readFully(preamble);
            if(!Arrays.equals(preamble,new byte[]{'E','M','L','I','N','K',1,0})) throw new IOException("EMLINK preamble");
            socket.setSoTimeout(5000);
        } catch(IOException e) { socket.close(); throw e; }
    }
    public byte[] read() throws IOException {
        int length=input.readUnsignedByte() | (input.readUnsignedByte()<<8);
        if(length<1 || length>Wire.MAX_PACKET) throw new IOException("EMLINK packet length");
        byte[] bytes=new byte[length]; input.readFully(bytes); return bytes;
    }
    public synchronized void send(byte[] bytes) throws IOException {
        if(bytes.length<1 || bytes.length>Wire.MAX_PACKET) throw new IOException("EMLINK packet length");
        byte[] framed=new byte[bytes.length+2]; framed[0]=(byte)bytes.length; framed[1]=(byte)(bytes.length>>8);
        System.arraycopy(bytes,0,framed,2,bytes.length); output.write(framed);
    }
    @Override public void close() throws IOException { socket.close(); }
}
