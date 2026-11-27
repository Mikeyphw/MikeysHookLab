package com.mikeyphw.hooklab.runtime.pack;

import com.mikeyphw.hooklab.runtime.BuiltinRegistry;
import com.mikeyphw.hooklab.sdk.HookSdk;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** One bounded, immutable, framework-owned generation; no credentials or host writes. */
public final class PackSnapshot {
    public static final String GROUP="hooklab.packs.v1", STATE="snapshot", GENERATION="generation", SAFE="safe_mode";
    public static final String BUILTIN="builtin", DISABLED="disabled";
    private static final String HEADER="HOOKLAB-PACK-SNAPSHOT-V1";
    public final String generation, text, channel;
    private final byte[] key;
    private final Map<String,String> choices;
    private PackSnapshot(byte[] key,String channel,Map<String,String> choices) throws GeneralSecurityException {
        this.key=key==null?null:key.clone();
        if(key==null&&!"-".equals(channel)||key!=null&&!"personal".equals(channel)&&!"test".equals(channel))throw new IllegalArgumentException("snapshot trust channel");this.channel=channel;
        if(this.key!=null)HookPack.signerFingerprint(this.key);
        if(!choices.keySet().equals(PackPolicy.hooks().keySet()))throw new IllegalArgumentException("pack snapshot roster");
        TreeMap<String,String> checked=new TreeMap<>();
        Map<String,String> moduleDigests=new TreeMap<>();
        for(Map.Entry<String,String> e:choices.entrySet()){
            String v=e.getValue();if(!BUILTIN.equals(v)&&!DISABLED.equals(v)){
                PackPolicy.requireHash(v);if(this.key==null)throw new IllegalArgumentException("untrusted active pack");
                String module=PackPolicy.hook(e.getKey()).moduleId, old=moduleDigests.put(module,v);
                if(old!=null&&!old.equals(v))throw new IllegalArgumentException("mixed module generations");
            }checked.put(e.getKey(),v);
        }
        this.choices=Collections.unmodifiableMap(checked);
        StringBuilder b=new StringBuilder(HEADER).append('\n').append(BuiltinRegistry.current().generation).append('\n').append(key==null?"-":Base64.getEncoder().encodeToString(this.key)).append('\n').append(channel).append('\n');
        for(Map.Entry<String,String> e:checked.entrySet())b.append(e.getKey()).append(' ').append(e.getValue()).append('\n');
        text=b.toString();generation=HookPack.digest(text.getBytes(StandardCharsets.US_ASCII));
    }
    public static PackSnapshot create(byte[] key,String channel,Map<String,String> choices) throws GeneralSecurityException {return new PackSnapshot(key,channel,choices);}
    public static PackSnapshot legacy() throws GeneralSecurityException {
        Map<String,String> all=new TreeMap<>();for(String id:PackPolicy.hooks().keySet())all.put(id,BUILTIN);return create(null,"-",all);
    }
    public static PackSnapshot parse(String text,String generation) throws GeneralSecurityException {
        PackPolicy.requireHash(generation);if(text==null||text.length()>8192)throw new IllegalArgumentException("pack snapshot size");
        String[] lines=text.split("\n",-1);if(lines.length!=PackPolicy.hooks().size()+5||!HEADER.equals(lines[0])||!BuiltinRegistry.current().generation.equals(lines[1]))throw new IllegalArgumentException("pack snapshot header");
        byte[] key="-".equals(lines[2])?null:Base64.getDecoder().decode(lines[2]);Map<String,String> choices=new TreeMap<>();
        for(int i=4;i<lines.length-1;i++){
            String[] row=lines[i].split(" ",-1);if(row.length!=2||choices.put(row[0],row[1])!=null)throw new IllegalArgumentException("duplicate snapshot row");
        }
        PackSnapshot result=create(key,lines[3],choices);if(!result.text.equals(text)||!result.generation.equals(generation))throw new IllegalArgumentException("snapshot identity");return result;
    }
    /** Missing both keys denotes the previous APK-only architecture. Partial state never does. */
    public static PackSnapshot read(Map<String,?> values) throws GeneralSecurityException {
        if(values==null)throw new IllegalArgumentException("snapshot unavailable");
        Object safe=values.get(SAFE);if(safe!=null&&!(safe instanceof Boolean))throw new IllegalArgumentException("safe mode type");
        if(Boolean.TRUE.equals(safe))throw new IllegalArgumentException("pack safe mode");
        if(!values.containsKey(STATE)&&!values.containsKey(GENERATION)&&!values.containsKey(SAFE))return legacy();
        Object state=values.get(STATE), generation=values.get(GENERATION);
        if(!(state instanceof String)||!(generation instanceof String))throw new IllegalArgumentException("partial snapshot");
        return parse((String)state,(String)generation);
    }
    public byte[] key(){return key==null?null:key.clone();}
    public String choice(String id){String v=choices.get(id);if(v==null)throw new IllegalArgumentException("unknown pack hook");return v;}
    public Map<String,String> choices(){return choices;}
    public boolean allows(String id,String digest,String pkg,String process,long version,boolean scoped,boolean optIn,boolean safe) {
        HookSdk.HookDescriptor h=PackPolicy.hook(id);
        return digest!=null&&digest.matches("[0-9a-f]{64}")&&digest.equals(choice(id))&&h.eligible(pkg,process,version,scoped,optIn,safe)&&key!=null;
    }
}
