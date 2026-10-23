package com.deskink.core;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Locale;
import org.json.*;

/** Public release descriptor and bounded, verified APK download. No installation side effects. */
public final class AppUpdate {
    public static final String FEED="https://raw.githubusercontent.com/zkdlwuthwo1-hash/deskink/main/updates/android.json";
    public final long versionCode,size;
    public final String version,url,sha256;
    private AppUpdate(long code,String version,String url,long size,String sha256) {
        versionCode=code; this.version=version; this.url=url; this.size=size; this.sha256=sha256;
    }
    public static AppUpdate parse(String json,long installed) throws IOException {
        try {
            if(json.length()>16_384) throw new IOException("Update information is too large");
            JSONObject object=new JSONObject(json);
            long code=object.getLong("version_code"),size=object.getLong("size");
            String version=object.getString("version"),url=object.getString("url"),sha=object.getString("sha256");
            if(code<=0||code>Integer.MAX_VALUE||size<=0||size>67_108_864||!sha.matches("[a-fA-F0-9]{64}")
                    ||!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                    ||!url.equals("https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v"+version+"/DeskInk-Android.apk")) {
                throw new IOException("Invalid DeskInk update information");
            }
            return code>installed?new AppUpdate(code,version,url,size,sha):null;
        } catch(JSONException error) { throw new IOException("Invalid update information",error); }
    }
    public static AppUpdate check(long installed) throws IOException {
        try(InputStream input=open(FEED); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096]; int n;
            while((n=input.read(buffer))!=-1) {
                if(output.size()+n>16_384) throw new IOException("Update information is too large");
                output.write(buffer,0,n);
            }
            return parse(output.toString(StandardCharsets.UTF_8.name()),installed);
        }
    }
    public void download(File target) throws IOException {
        boolean verified=false;
        try {
            try(InputStream input=open(url); OutputStream output=new FileOutputStream(target)) { copyVerified(input,output); }
            verified=true;
        } finally { if(!verified) target.delete(); }
    }
    /** The caller publishes the temporary output only after this returns successfully. */
    public void copyVerified(InputStream input,OutputStream output) throws IOException {
        MessageDigest digest;
        try { digest=MessageDigest.getInstance("SHA-256"); } catch(NoSuchAlgorithmException error) { throw new IOException(error); }
        byte[] buffer=new byte[32*1024]; long read=0; int n;
        while((n=input.read(buffer))!=-1) {
            read+=n; if(read>size) throw new IOException("Update size mismatch");
            digest.update(buffer,0,n); output.write(buffer,0,n);
        }
        StringBuilder hash=new StringBuilder(); for(byte value:digest.digest()) hash.append(String.format(Locale.ROOT,"%02x",value&255));
        if(read!=size||!hash.toString().equalsIgnoreCase(sha256)) throw new IOException("Update verification failed");
    }
    private static InputStream open(String address) throws IOException {
        URL url=new URL(address);
        for(int redirect=0;redirect<6;redirect++) {
            String host=url.getHost();
            if(!url.getProtocol().equals("https")||url.getUserInfo()!=null||(url.getPort()!=-1&&url.getPort()!=443)
                    ||!(host.equals("github.com")||host.equals("raw.githubusercontent.com")||host.equals("release-assets.githubusercontent.com"))) {
                throw new IOException("Unexpected update server");
            }
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(10_000); connection.setReadTimeout(15_000); connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent","DeskInk-Android-Updater"); connection.setUseCaches(false);
            try {
                int status=connection.getResponseCode();
                if(status==301||status==302||status==303||status==307||status==308) {
                    String location=connection.getHeaderField("Location");
                    if(location==null) throw new IOException("Missing update redirect");
                    url=new URL(url,location); connection.disconnect(); continue;
                }
                if(status!=200) throw new IOException("Update server returned "+status);
                return new FilterInputStream(connection.getInputStream()) {
                    @Override public void close() throws IOException { try { super.close(); } finally { connection.disconnect(); } }
                };
            } catch(IOException error) { connection.disconnect(); throw error; }
        }
        throw new IOException("Too many update redirects");
    }
}
