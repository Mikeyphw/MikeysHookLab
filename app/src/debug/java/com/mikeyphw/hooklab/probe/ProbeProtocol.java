package com.mikeyphw.hooklab.probe;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Bounded fixed-payload transport. A digest is not a signature; only APK-owned bytes are allowed. */
public final class ProbeProtocol {
    public static final String TARGET="com.mikeyphw.hooklab.probetarget", MANAGER="com.mikeyphw.hooklab";
    public static final String GROUP="hooklab.mod01.probe.v1", ENABLED="enabled", DIGEST="sha256", GENERATION="generation";
    public static final String ID="hooklab.probe.marker", ASSET="mod01-probe/classes.dex";
    public static final String ENTRY="com.mikeyphw.hooklab.modules.probe.ProbeEntrypoint";
    public static final int MAX_BYTES=256*1024;
    private ProbeProtocol() {}
    public static boolean eligible(String pkg,String process,long version,boolean scoped,boolean enabled,boolean sameSigner) {
        return scoped&&enabled&&sameSigner&&TARGET.equals(pkg)&&TARGET.equals(process)&&version==1;
    }
    public static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;
        while((n=in.read(buf))!=-1){if(n>MAX_BYTES-out.size())throw new IOException("probe size refused");out.write(buf,0,n);}
        byte[] b=out.toByteArray();if(b.length<112||b[0]!='d'||b[1]!='e'||b[2]!='x'||b[3]!='\n')throw new IOException("probe DEX refused");return b;
    }
    public static String digest(byte[] data) {
        try { StringBuilder result=new StringBuilder(64);for(byte b:MessageDigest.getInstance("SHA-256").digest(data))result.append("0123456789abcdef".charAt((b>>>4)&15)).append("0123456789abcdef".charAt(b&15));return result.toString(); }
        catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 unavailable",e);}
    }
    public static String file(String digest){if(digest==null||!digest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("probe digest");return "mod01-probe-"+digest;}
    /** ART accesses array() on non-direct buffers. Keep an independently copied,
     * direct buffer private to the fixed debug loader, after exact byte verification. */
    public static ByteBuffer verifiedDexBuffer(byte[] expected,byte[] observed,String claimed) {
        if(expected.length<112 || !matches(expected,observed,claimed))
            throw new IllegalArgumentException("probe payload identity refused");
        ByteBuffer buffer=ByteBuffer.allocateDirect(observed.length);
        buffer.put(observed);
        buffer.flip();
        return buffer;
    }
    public static boolean matches(byte[] expected,byte[] observed,String claimed){return expected.length<=MAX_BYTES&&observed.length<=MAX_BYTES&&digest(expected).equals(claimed)&&Arrays.equals(expected,observed);}
}
