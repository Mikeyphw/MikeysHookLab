package com.mikeyphw.hooklab.runtime.pack;

import com.mikeyphw.hooklab.sdk.HookSdk;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;

/** Strict, bounded ZIP32 profile. Only verified signed bytes are exposed. No extraction. */
public final class HookPack {
    public static final int MAX_ARCHIVE = 9 * 1024 * 1024;
    public static final int MAX_DEX = 8 * 1024 * 1024;
    public static final int MAX_METADATA = 65536;
    private static final String MANIFEST = "manifest.json", DEX = "runtime/classes.dex",
            PROVENANCE = "provenance.json", DIGESTS = "META-INF/digests.txt", SIGNATURE = "META-INF/signature.bin";
    private static final Set<String> NAMES = Collections.unmodifiableSet(new java.util.HashSet<>(Arrays.asList(MANIFEST, DEX, PROVENANCE, DIGESTS, SIGNATURE)));
    private HookPack() { }

    public static final class Verified {
        public final String moduleId, version, entrypoint, signer, archiveSha256, channel;
        private final byte[] manifest, dex, provenance, signedDigests, signature;
        private Verified(Map<String,byte[]> parts, String signer, String archiveSha256) {
            Map<?,?> m = (Map<?,?>) HookSdk.StrictJson.read(parts.get(MANIFEST));
            moduleId = (String)m.get("module_id"); version = (String)m.get("version"); entrypoint = (String)m.get("entrypoint");
            this.signer = signer; this.archiveSha256 = archiveSha256;
            channel=(String)((Map<?,?>)HookSdk.StrictJson.read(parts.get(PROVENANCE))).get("channel");
            manifest = parts.get(MANIFEST).clone(); dex = parts.get(DEX).clone(); provenance = parts.get(PROVENANCE).clone();
            signedDigests = parts.get(DIGESTS).clone(); signature = parts.get(SIGNATURE).clone();
        }
        public byte[] manifest() { return manifest.clone(); }
        public byte[] dex() { return dex.clone(); }
        public byte[] provenance() { return provenance.clone(); }
        public byte[] signedDigests() { return signedDigests.clone(); }
        public byte[] signature() { return signature.clone(); }
        /** A private direct buffer avoids the ART read-only heap-array failure. */
        public ByteBuffer directDex() { ByteBuffer result=ByteBuffer.allocateDirect(dex.length); result.put(dex).flip(); return result; }
    }
    public static byte[] readBounded(InputStream input, int max) throws IOException {
        if (max < 1 || max > MAX_ARCHIVE) throw new IllegalArgumentException("invalid size bound");
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] chunk = new byte[8192];
        int n; while ((n=input.read(chunk)) != -1) {
            if (n==0 || n>max-out.size()) throw new IOException("pack size refused");
            out.write(chunk,0,n);
        }
        return out.toByteArray();
    }
    private static void require(boolean test, String reason) {
        if (!test) throw new IllegalArgumentException("hookpack refused: "+reason);
    }
    private static byte[] sha(byte[] data) throws GeneralSecurityException { return MessageDigest.getInstance("SHA-256").digest(data); }
    public static String digest(byte[] data) throws GeneralSecurityException {
        StringBuilder text=new StringBuilder(64);
        for(byte b:sha(data))text.append("0123456789abcdef".charAt((b>>>4)&15)).append("0123456789abcdef".charAt(b&15));
        return text.toString();
    }
    public static String signerFingerprint(byte[] publicKey) throws GeneralSecurityException {
        key(publicKey); return digest(publicKey);
    }
    private static RSAPublicKey key(byte[] bytes) throws GeneralSecurityException {
        require(bytes != null && bytes.length <= 2048, "public key bound");
        java.security.PublicKey parsed=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
        require(parsed instanceof RSAPublicKey && Arrays.equals(parsed.getEncoded(),bytes), "public key identity");
        RSAPublicKey rsa=(RSAPublicKey)parsed;
        require(rsa.getModulus().bitLength()==3072 && rsa.getPublicExponent().equals(java.math.BigInteger.valueOf(65537)), "RSA profile");
        return rsa;
    }
    private static int u16(ByteBuffer b) { return Short.toUnsignedInt(b.getShort()); }
    private static long u32(ByteBuffer b) { return Integer.toUnsignedLong(b.getInt()); }
    private static int size(long n,int max) { require(n>=0 && n<=max,"size"); return (int)n; }
    private static void expect(ByteBuffer b,int value) { require(b.getInt()==value,"ZIP record"); }
    private static String name(ByteBuffer b,int n) {
        require(n>0 && n<=128 && n<=b.remaining(),"entry name length");
        byte[] data=new byte[n];b.get(data);String s=new String(data,StandardCharsets.US_ASCII);
        require(NAMES.contains(s) && Arrays.equals(data,s.getBytes(StandardCharsets.US_ASCII)),"entry allowlist");return s;
    }
    private static int limit(String name) { return DEX.equals(name)?MAX_DEX:SIGNATURE.equals(name)?384:MAX_METADATA; }
    private static final class Row {
        final int offset;final long crc;final byte[] bytes;
        Row(int offset,long crc,byte[] bytes){this.offset=offset;this.crc=crc;this.bytes=bytes;}
    }
    private static Map<String,byte[]> parse(byte[] archive) {
        require(archive!=null && archive.length>=22 && archive.length<=MAX_ARCHIVE,"archive bound");
        ByteBuffer b=ByteBuffer.wrap(archive).order(ByteOrder.LITTLE_ENDIAN); b.position(archive.length-22);
        expect(b,0x06054b50);require(u16(b)==0 && u16(b)==0,"multi-disk archive");
        int count=u16(b);require(count==NAMES.size() && u16(b)==count,"entry count");
        int centralSize=size(u32(b),archive.length), centralOffset=size(u32(b),archive.length);
        require(u16(b)==0 && (long)centralOffset+centralSize==archive.length-22L,"central directory bounds");
        // Read exactly the local entries from offset zero; reject prefix, gaps, descriptors and extras.
        b.position(0);Map<String,Row> local=new LinkedHashMap<>();String previous="";
        for(int i=0;i<count;i++) {
            int off=b.position();expect(b,0x04034b50);
            require(u16(b)==20 && u16(b)==0 && u16(b)==0 && u16(b)==0 && u16(b)==33,"local ZIP profile");
            long crc=u32(b), packed=u32(b), unpacked=u32(b);
            int len=u16(b);require(u16(b)==0,"local extra");String n=name(b,len);
            require(n.compareTo(previous)>0 && packed==unpacked,"entry order or compression");previous=n;
            int length=size(packed,limit(n));require(length>0 && length<=b.remaining() && (long)b.position()+length<=centralOffset,"local payload bound");
            byte[] bytes=new byte[length];b.get(bytes);CRC32 crc32=new CRC32();crc32.update(bytes);
            require(crc32.getValue()==crc,"entry CRC");local.put(n,new Row(off,crc,bytes));
        }
        require(b.position()==centralOffset && local.keySet().equals(NAMES),"local inventory");
        Map<String,byte[]> result=new LinkedHashMap<>();
        for(Map.Entry<String,Row> entry:local.entrySet()) {
            Row row=entry.getValue();expect(b,0x02014b50);
            require(u16(b)==0x0314 && u16(b)==20 && u16(b)==0 && u16(b)==0 && u16(b)==0 && u16(b)==33,"central ZIP profile");
            require(u32(b)==row.crc && u32(b)==row.bytes.length && u32(b)==row.bytes.length,"central payload identity");
            int len=u16(b);require(u16(b)==0 && u16(b)==0 && u16(b)==0 && u16(b)==0,"central extra, comment or disk");
            require(u32(b)==(0100644L<<16) && u32(b)==row.offset,"central mode or offset");
            require(name(b,len).equals(entry.getKey()),"central entry identity"); result.put(entry.getKey(),row.bytes);
        }
        require(b.position()==archive.length-22,"central directory trailing data");return Collections.unmodifiableMap(result);
    }
    private static byte[] digestManifest(Map<String,byte[]> parts,String signer) throws GeneralSecurityException {
        StringBuilder out=new StringBuilder("HOOKLAB-PACK-V1\nkey-sha256 ").append(signer).append('\n');
        for(Map.Entry<String,byte[]> e:new TreeMap<>(parts).entrySet()) {
            if(e.getKey().equals(DIGESTS)||e.getKey().equals(SIGNATURE))continue;
            out.append(digest(e.getValue())).append(' ').append(e.getValue().length).append(' ').append(e.getKey()).append('\n');
        }
        return out.toString().getBytes(StandardCharsets.US_ASCII);
    }
    // The v1 manifest/provenance fields are constrained to ASCII identifiers and
    // positive integers. Match Go's lexicographically sorted, whitespace-free JSON.
    private static void canonicalValue(StringBuilder out,Object value) {
        if(value instanceof Map<?,?>) {
            out.append('{');Map<String,Object> sorted=new TreeMap<>();
            for(Map.Entry<?,?> e:((Map<?,?>)value).entrySet())sorted.put((String)e.getKey(),e.getValue());
            boolean first=true;for(Map.Entry<String,Object> e:sorted.entrySet()) {
                if(!first)out.append(',');first=false;canonicalValue(out,e.getKey());out.append(':');canonicalValue(out,e.getValue());
            }out.append('}');
        }else if(value instanceof java.util.List<?>) {
            out.append('[');boolean first=true;for(Object e:(java.util.List<?>)value){if(!first)out.append(',');first=false;canonicalValue(out,e);}out.append(']');
        }else if(value instanceof String) {
            String text=(String)value;require(text.matches("[A-Za-z0-9_.$-]+"),"canonical string");out.append('"').append(text).append('"');
        }else if(value instanceof Long)out.append(value);
        else throw new IllegalArgumentException("hookpack refused: canonical type");
    }
    private static void requireCanonical(byte[] bytes) {
        StringBuilder out=new StringBuilder();canonicalValue(out,HookSdk.StrictJson.read(bytes));
        require(Arrays.equals(bytes,out.toString().getBytes(StandardCharsets.US_ASCII)),"noncanonical metadata");
    }
    private static void validateProvenance(byte[] data) {
        Object p=HookSdk.StrictJson.read(data);require(p instanceof Map<?,?>,"provenance type");Map<?,?> o=(Map<?,?>)p;
        require(o.keySet().equals(new java.util.HashSet<>(Arrays.asList("schema","source_sha","source_tree","run_id","run_attempt","settings_schema","channel"))),"provenance fields");
        require("hooklab.pack.provenance.v1".equals(o.get("schema")),"provenance schema");
        require("personal".equals(o.get("channel"))||"test".equals(o.get("channel")),"pack trust channel");
        for(String k:new String[]{"source_sha","source_tree"})require(o.get(k) instanceof String && ((String)o.get(k)).matches("[0-9a-f]{40}"),"source identity");
        for(String k:new String[]{"run_id","run_attempt","settings_schema"})require(o.get(k) instanceof Long && ((Long)o.get(k))>0,"provenance number");
    }
    private static int table(ByteBuffer h,int countOffset,int offsetOffset,int width,int length) {
        long count=Integer.toUnsignedLong(h.getInt(countOffset)),offset=Integer.toUnsignedLong(h.getInt(offsetOffset));
        require(count>0 && count<=65536 && offset>=112 && offset%4==0 && offset+count*width<=length,"DEX table bounds");return (int)offset;
    }
    private static String descriptor(byte[] dex,int offset) {
        require(offset>=112 && offset<dex.length,"DEX string offset");
        int pos=offset,count=0,shift=0;
        while(true){require(pos<dex.length && shift<=21,"DEX string length");int v=dex[pos++]&255;count|=(v&127)<<shift;if((v&128)==0)break;shift+=7;}
        require(count>0&&count<=256&&pos+count<dex.length&&dex[pos+count]==0,"DEX descriptor bound");
        String text=new String(dex,pos,count,StandardCharsets.US_ASCII);
        require(text.matches("L(?:[A-Za-z0-9_$]+/)+[A-Za-z0-9_$-]+;"),"DEX class descriptor");return text;
    }
    private static void validateDex(byte[] dex,byte[] manifest) throws GeneralSecurityException {
        require(dex.length>=112 && dex.length<=MAX_DEX && dex[0]=='d' && dex[1]=='e' && dex[2]=='x' && dex[3]=='\n' && dex[7]==0,"DEX header");
        require(Arrays.asList("035","037","038","039").contains(new String(dex,4,3,StandardCharsets.US_ASCII)),"DEX version");
        ByteBuffer h=ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN);
        require(h.getInt(32)==dex.length && h.getInt(36)==112 && h.getInt(40)==0x12345678,"DEX size/endian");
        MessageDigest signature=MessageDigest.getInstance("SHA-1");signature.update(dex,32,dex.length-32);
        require(MessageDigest.isEqual(signature.digest(),Arrays.copyOfRange(dex,12,32)),"DEX internal signature");
        java.util.zip.Adler32 checksum=new java.util.zip.Adler32();checksum.update(dex,12,dex.length-12);require(checksum.getValue()==Integer.toUnsignedLong(h.getInt(8)),"DEX checksum");
        int strings=table(h,56,60,4,dex.length),types=table(h,64,68,4,dex.length),classes=table(h,96,100,32,dex.length);
        int sc=h.getInt(56),tc=h.getInt(64),cc=h.getInt(96);
        Map<?,?> m=(Map<?,?>)HookSdk.StrictJson.read(manifest);
        String prefix="Lcom/mikeyphw/hooklab/modules/"+m.get("module_id")+"/";
        String entry="L"+((String)m.get("entrypoint")).replace('.','/')+";";
        Set<String> names=new java.util.HashSet<>();
        for(int i=0;i<cc;i++) {
            int type=h.getInt(classes+32*i);require(type>=0&&type<tc,"DEX class type index");
            int str=h.getInt(types+4*type);require(str>=0&&str<sc,"DEX descriptor index");
            String name=descriptor(dex,h.getInt(strings+4*str));require(name.startsWith(prefix)&&names.add(name),"DEX definition namespace or duplicate");
        }
        require(names.contains(entry),"DEX entry definition absent");
    }
    /** Public key is supplied by the caller's explicit trust store, never by the archive. */
    public static Verified verify(byte[] archive,byte[] trustedPublicKey) throws GeneralSecurityException {
        require(archive!=null && archive.length<=MAX_ARCHIVE && trustedPublicKey!=null && trustedPublicKey.length<=2048,"input bound");
        byte[] snapshot=archive.clone();byte[] publicKey=trustedPublicKey.clone();
        RSAPublicKey rsa=key(publicKey);String signer=digest(publicKey);
        final Map<String,byte[]> parts;
        try{parts=parse(snapshot);}catch(java.nio.BufferUnderflowException|IndexOutOfBoundsException e){throw new IllegalArgumentException("hookpack refused: truncated record",e);}
        byte[] expected=digestManifest(parts,signer);require(MessageDigest.isEqual(expected,parts.get(DIGESTS)),"digest inventory");
        Signature signature=Signature.getInstance("SHA256withRSA");signature.initVerify(rsa);signature.update(expected);
        require(parts.get(SIGNATURE).length==384 && signature.verify(parts.get(SIGNATURE)),"signature");
        require("hooklab.module.manifest.v1".equals(HookSdk.ContractReader.validate(parts.get(MANIFEST))),"manifest schema");
        validateProvenance(parts.get(PROVENANCE));requireCanonical(parts.get(MANIFEST));requireCanonical(parts.get(PROVENANCE));validateDex(parts.get(DEX),parts.get(MANIFEST));
        return new Verified(parts,signer,digest(snapshot));
    }
}
