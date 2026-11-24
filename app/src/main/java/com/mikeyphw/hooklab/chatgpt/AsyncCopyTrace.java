package com.mikeyphw.hooklab.chatgpt;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Ephemeral, identity-only propagation. Text equality is a separate keyed check. */
public final class AsyncCopyTrace {
    public interface Clock { long now(); }
    public enum Kind { BLOCK, CALLBACK, IXL, WRAPPER, CLIP, SAFE }
    private static final long TTL=120000L;
    private static final int CAP=256, LOG_ACTIONS=16;
    private final Clock clock;
    private final byte[] secret=new byte[32];
    private final ArrayList<Entry> entries=new ArrayList<>();
    private long next=1;
    private int actions;
    public AsyncCopyTrace(Clock c) {clock=c;new SecureRandom().nextBytes(secret);}
    public static final class Block {
        public final long id; final byte[] digest; final long born;
        Block(long i,byte[] d,long t){id=i;digest=d;born=t;}
    }
    public static final class Render {
        public final long id,blockId; final byte[] digest; final long born;
        final WeakReference<Object> annotated;
        Render(long i,long b,byte[] d,long t,Object a){id=i;blockId=b;digest=d;born=t;annotated=new WeakReference<>(a);}
    }
    public static final class Action {
        public final long id,renderId,blockId; public final boolean report;
        final byte[] digest; final long born; final WeakReference<Object> annotated;
        boolean clipboardReported; final HashSet<String> reportedStages=new HashSet<>();
        Action(long i,Render r,long t,boolean log){id=i;renderId=r.id;blockId=r.blockId;digest=r.digest;born=t;report=log;annotated=r.annotated;}
        public boolean ownsAnnotated(Object a){return a!=null && annotated.get()==a;}
    }
    private static final class Entry {
        final Kind kind; final WeakReference<Object> key; final long born;
        Object value; boolean ambiguous;
        Entry(Kind k,Object o,Object v,long t){kind=k;key=new WeakReference<>(o);value=v;born=t;}
    }
    private boolean live(long t){long n=clock.now();return n>=t && n-t<=TTL;}
    private void prune(){entries.removeIf(e->e.key.get()==null||!live(e.born));}
    private Entry find(Kind k,Object o){if(o==null)return null;prune();for(Entry e:entries)if(e.kind==k&&e.key.get()==o)return e;return null;}
    private void bind(Kind k,Object key,Object value){
        if(key==null||value==null)return;
        Entry e=find(k,key);
        if(e!=null){if(e.value!=value){e.ambiguous=true;e.value=null;}return;}
        while(entries.size()>=CAP)entries.remove(0);
        entries.add(new Entry(k,key,value,clock.now()));
    }
    private Object lookup(Kind k,Object key){Entry e=find(k,key);return e==null||e.ambiguous?null:e.value;}
    private byte[] digest(String text){
        if(text==null||text.length()>1048576)return null;
        // Reject unpaired UTF-16 so UTF-8 replacement never produces false parity.
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=text.length()||!Character.isLowSurrogate(text.charAt(i)))return null;}else if(Character.isLowSurrogate(c))return null;}
        byte[] b=text.getBytes(StandardCharsets.UTF_8);if(b.length>1048576)return null;
        try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(secret,"HmacSHA256"));return m.doFinal(b);}catch(java.security.GeneralSecurityException e){throw new IllegalStateException(e);}
    }
    private static boolean equal(byte[] a,byte[] b){return a!=null&&b!=null&&MessageDigest.isEqual(a,b);}
    public synchronized Block block(String text){byte[] d=digest(text);return d==null?null:new Block(next++,d,clock.now());}
    public synchronized void annotate(Block block,Object annotated,String text){if(block!=null&&live(block.born)&&equal(block.digest,digest(text)))bind(Kind.BLOCK,annotated,block);}
    public synchronized Render render(Object annotated,String text){
        byte[] d=digest(text);if(annotated==null||d==null)return null;
        Block b=(Block)lookup(Kind.BLOCK,annotated);
        return new Render(next++,b!=null&&live(b.born)&&equal(b.digest,d)?b.id:0,d,clock.now(),annotated);
    }
    public synchronized void callback(Render r,Object callback,Object annotated){if(r!=null&&live(r.born)&&annotated!=null&&r.annotated.get()==annotated)bind(Kind.CALLBACK,callback,r);}
    public synchronized Action begin(Object callback){Render r=(Render)lookup(Kind.CALLBACK,callback);if(r==null||!live(r.born)||r.annotated.get()==null)return null;boolean log=actions<LOG_ACTIONS;if(log)actions++;return new Action(next++,r,clock.now(),log);}
    public synchronized void tag(Kind kind,Object key,Action a){if(kind==Kind.BLOCK||kind==Kind.CALLBACK)throw new IllegalArgumentException("action kind");if(a!=null&&live(a.born))bind(kind,key,a);}
    public synchronized Action get(Kind kind,Object key){if(kind==Kind.BLOCK||kind==Kind.CALLBACK)throw new IllegalArgumentException("action kind");Action a=(Action)lookup(kind,key);return a!=null&&live(a.born)?a:null;}
    public synchronized String clipboard(Action a,Object clip,String text){
        if(a==null||get(Kind.CLIP,clip)!=a||a.clipboardReported)return "UNBOUND";
        a.clipboardReported=true;byte[] d=digest(text);return d==null?"UNSAFE_TEXT":equal(a.digest,d)?"MATCH":"MISMATCH";
    }
    public synchronized boolean report(Action a,String stage){return a!=null&&a.report&&live(a.born)&&a.reportedStages.size()<32&&a.reportedStages.add(stage);}
    public synchronized int size(){prune();return entries.size();}
}
