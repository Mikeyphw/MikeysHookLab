package com.mikeyphw.hooklab.chatgpt;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * P03: privacy-preserving correlation of a specific callback OBJECT, its rendered
 * source candidate and a synchronous clipboard write inside that callback.
 *
 * Not a UI block ID: instrumentation cannot infer visual selection or asynchronous
 * clipboard action causality from object identity alone. Never stores plaintext.
 */
public final class CodeBlockActionMatcher {
    public interface Clock { long nowMs(); }
    public enum Outcome { BOUND_VALUE_MATCH, BOUND_VALUE_MISMATCH,
        SHARED_CALLBACK_AMBIGUOUS, NO_BOUND_CALLBACK, UNSAFE_TEXT }
    public static final class Evidence {
        public final Outcome outcome;
        public final int role, bindingId, bytes, lineFeeds, carriageReturns, trailingLineFeeds;
        public final String keyedToken;
        Evidence(Outcome kind, Entry entry) {
            outcome=kind;
            role=entry==null?-1:entry.role;
            bindingId=entry==null?-1:entry.bindingId;
            bytes=entry==null?0:entry.bytes;
            lineFeeds=entry==null?0:entry.lf;
            carriageReturns=entry==null?0:entry.cr;
            trailingLineFeeds=entry==null?0:entry.trailing;
            keyedToken=entry==null?"":entry.token.substring(0,16);
        }
    }
    public static final class Frame {
        private final Entry entry;
        private final Frame previous;
        private Frame(Entry entry, Frame previous) { this.entry=entry; this.previous=previous; }
        public int role() { return entry.role; }
        public int bindingId() { return entry.bindingId; }
    }
    private static final class Entry {
        final WeakReference<Object> callback;
        final int role, bindingId, bytes, lf, cr, trailing;
        final String token;
        boolean ambiguous;
        long seen;
        Entry(Object action,int role,int id,String hash, byte[] data, String source,long now) {
            callback=new WeakReference<>(action);
            this.role=role; bindingId=id; token=hash; bytes=data.length; seen=now;
            int lfCount=0,crCount=0,last=0;
            for(int i=0;i<source.length();i++) {
                char c=source.charAt(i);
                if(c=='\n')lfCount++;
                if(c=='\r')crCount++;
            }
            for(int i=source.length()-1;i>=0&&source.charAt(i)=='\n';i--)last++;
            lf=lfCount;cr=crCount;trailing=last;
        }
    }
    private static final int MAX_BYTES=1024*1024, MAX_BINDINGS=128;
    private static final long MAX_AGE_MS=120000L;
    private static final char[] HEX="0123456789abcdef".toCharArray();
    private final byte[] secret=new byte[32];
    private final Clock clock;
    private final List<Entry> bindings=new ArrayList<>();
    private final ThreadLocal<Frame> active=new ThreadLocal<>();
    private int nextId;

    public CodeBlockActionMatcher(Clock clock) {
        if(clock==null)throw new IllegalArgumentException("clock");
        this.clock=clock;
        new SecureRandom().nextBytes(secret);
    }
    /** Returns false on unsupported/unsafe source. Neither callback nor raw text is persisted. */
    public synchronized boolean bind(Object callback,int role,String source) {
        if(callback==null || (role!=10 && role!=14))return false;
        byte[] bytes=asBytes(source);
        if(bytes==null)return false;
        long now=clock.nowMs();
        expire(now);
        String hash=hmac(bytes);
        for(Entry e:bindings) {
            if(e.callback.get()!=callback)continue;
            if(e.role!=role || !e.token.equals(hash)) e.ambiguous=true;
            e.seen=now;
            return !e.ambiguous;
        }
        if(nextId==Integer.MAX_VALUE) return false;
        bindings.add(new Entry(callback,role,++nextId,hash,bytes,source,now));
        while(bindings.size()>MAX_BINDINGS)bindings.remove(0);
        return true;
    }
    /** Called only from a verified, zero-argument Function0.invoke method hook. */
    public synchronized Frame enter(Object callback) {
        expire(clock.nowMs());
        if(callback==null)return null;
        for(Entry e:bindings) {
            if(e.callback.get()==callback) {
                Frame f=new Frame(e,active.get());active.set(f);return f;
            }
        }
        return null;
    }
    /** Only a clipboard write on this same thread and inside invoke can qualify. */
    public Evidence matchClipboardText(String text) {
        Frame f=active.get();
        if(f==null)return new Evidence(Outcome.NO_BOUND_CALLBACK,null);
        Entry e=f.entry;
        if(e.ambiguous)return new Evidence(Outcome.SHARED_CALLBACK_AMBIGUOUS,e);
        byte[] bytes=asBytes(text);
        if(bytes==null)return new Evidence(Outcome.UNSAFE_TEXT,e);
        boolean match=e.bytes==bytes.length && e.token.equals(hmac(bytes));
        return new Evidence(match?Outcome.BOUND_VALUE_MATCH:Outcome.BOUND_VALUE_MISMATCH,e);
    }
    public void exit(Frame frame) {
        if(frame==null)return;
        if(active.get()!=frame) { active.remove(); throw new IllegalStateException("Callback frame mismatch"); }
        if(frame.previous==null)active.remove(); else active.set(frame.previous);
    }
    public synchronized int bindingCount() {expire(clock.nowMs());return bindings.size();}
    private void expire(long now) {
        Iterator<Entry> it=bindings.iterator();
        while(it.hasNext()) {
            Entry e=it.next();
            if(e.callback.get()==null || now-e.seen>MAX_AGE_MS || now<e.seen)it.remove();
        }
    }
    private static byte[] asBytes(String source) {
        if(source==null || source.length()>MAX_BYTES)return null;
        for(int i=0;i<source.length();i++) {
            char c=source.charAt(i);
            if(Character.isHighSurrogate(c)) {
                if(++i>=source.length() || !Character.isLowSurrogate(source.charAt(i)))return null;
            } else if(Character.isLowSurrogate(c)) return null;
        }
        byte[] value=source.getBytes(StandardCharsets.UTF_8);
        return value.length<=MAX_BYTES?value:null;
    }
    private String hmac(byte[] bytes) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret,"HmacSHA256"));
            byte[] result=mac.doFinal(bytes);
            char[] hex=new char[result.length*2];
            for(int i=0;i<result.length;i++) {
                hex[i*2]=HEX[(result[i]>>>4)&15];
                hex[i*2+1]=HEX[result[i]&15];
            }
            return new String(hex);
        } catch(java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable",e);
        }
    }
}
