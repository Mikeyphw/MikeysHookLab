package com.mikeyphw.hooklab.chatgpt;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Process-local, privacy-preserving comparison of rendered strings against a
 * later ClipboardManager.setPrimaryClip payload. Never persists source text.
 * A match identifies an equal value, NOT which identical block was tapped.
 */
public final class CodeBlockProbeMatcher {
    public interface Clock { long nowMs(); }
    public enum Result { MATCH_VALUE_ONLY, AMBIGUOUS_SLOTS, UNMATCHED, UNSAFE_TEXT }
    public static final class Evidence {
        public final Result result;
        public final int parameterSlot, utf8Length, lineFeeds, carriageReturns, trailingNewlines;
        public final String ephemeralToken;
        private Evidence(Result result, int slot, int length, int lf, int cr, int trailing, String token) {
            this.result = result; parameterSlot = slot; utf8Length = length;
            lineFeeds = lf; carriageReturns = cr; trailingNewlines = trailing;
            ephemeralToken = token;
        }
        static Evidence no(Result r) { return new Evidence(r, -1, 0, 0, 0, 0, ""); }
    }
    private static final int MAX_SOURCE_BYTES = 1024 * 1024;
    private static final int MAX_CANDIDATES = 128;
    private static final long MAX_AGE_MS = 2 * 60 * 1000L;
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private final Clock clock;
    private final byte[] secret = new byte[32];
    private final LinkedHashMap<String, Candidate> candidates = new LinkedHashMap<>(16, .75f, true);
    private static final class Candidate {
        final int slot, bytes, lf, cr, trailing;
        long seen;
        Candidate(int slot, int bytes, int lf, int cr, int trailing, long seen) {
            this.slot=slot; this.bytes=bytes; this.lf=lf; this.cr=cr; this.trailing=trailing; this.seen=seen;
        }
    }

    public CodeBlockProbeMatcher(Clock clock) {
        if (clock == null) throw new IllegalArgumentException("clock required");
        this.clock=clock;
        new SecureRandom().nextBytes(secret);
    }

    /** CodeBlockImpl's first two String parameters, never retained as plaintext. */
    public synchronized void observePair(String first, String second) {
        register(first, 0);
        register(second, 1);
    }

    private void register(String value, int slot) {
        byte[] bytes = checkedBytes(value);
        if (bytes == null) return;
        long now = clock.nowMs();
        expire(now);
        String digest = mac(bytes);
        // Slot-specific key: the exact argument responsible must remain visible.
        String key = slot + ":" + digest;
        Candidate current = candidates.get(key);
        if (current != null) { current.seen = now; return; }
        int lf=0, cr=0, trailing=0;
        for (int i=0; i<value.length(); i++) {
            char c=value.charAt(i);
            if (c=='\n') lf++;
            if (c=='\r') cr++;
        }
        for (int i=value.length()-1; i>=0 && value.charAt(i)=='\n'; i--) trailing++;
        candidates.put(key, new Candidate(slot, bytes.length, lf, cr, trailing, now));
        while(candidates.size()>MAX_CANDIDATES) candidates.remove(candidates.keySet().iterator().next());
    }

    public synchronized Evidence compareCopiedText(String text) {
        byte[] bytes = checkedBytes(text);
        if (bytes == null) return Evidence.no(Result.UNSAFE_TEXT);
        expire(clock.nowMs());
        String digest = mac(bytes);
        Candidate found=null;
        int matches=0;
        for (int slot=0;slot<=1;slot++) {
            Candidate c=candidates.get(slot+":"+digest);
            if (c!=null) { matches++; found=c; }
        }
        if (matches==0) return Evidence.no(Result.UNMATCHED);
        if (matches!=1) return Evidence.no(Result.AMBIGUOUS_SLOTS);
        // The token is keyed per process and intentionally cannot reveal the SHA of a short command.
        return new Evidence(Result.MATCH_VALUE_ONLY, found.slot, found.bytes,
                found.lf, found.cr, found.trailing, digest.substring(0,16));
    }

    public synchronized int candidateCount() {
        expire(clock.nowMs()); return candidates.size();
    }

    private void expire(long now) {
        Iterator<Map.Entry<String,Candidate>> it=candidates.entrySet().iterator();
        while(it.hasNext()) if (now - it.next().getValue().seen > MAX_AGE_MS) it.remove();
    }

    private static byte[] checkedBytes(String s) {
        if (s==null || s.length()>MAX_SOURCE_BYTES) return null;
        // Reject unpaired UTF-16 surrogates instead of replacing them during UTF-8 encoding.
        for(int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            if(Character.isHighSurrogate(c)) {
                if(++i>=s.length() || !Character.isLowSurrogate(s.charAt(i))) return null;
            } else if(Character.isLowSurrogate(c)) return null;
        }
        byte[] bytes=s.getBytes(StandardCharsets.UTF_8);
        return bytes.length<=MAX_SOURCE_BYTES ? bytes : null;
    }
    private String mac(byte[] bytes) {
        try {
            Mac m=Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] hash=m.doFinal(bytes);
            char[] chars=new char[hash.length*2];
            for(int i=0;i<hash.length;i++) {
                chars[2*i]=HEX[(hash[i]>>>4)&15]; chars[2*i+1]=HEX[hash[i]&15];
            }
            return new String(chars);
        } catch(java.security.GeneralSecurityException error) {
            // Lack of a functioning platform cryptographic primitive is fatal to parity diagnostics.
            throw new IllegalStateException("Unavailable HMAC implementation",error);
        }
    }
}
