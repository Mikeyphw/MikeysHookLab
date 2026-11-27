package com.mikeyphw.hooklab.sdk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.Set;

/** Reviewed typed configuration. No arbitrary keys, code, credentials or activation state. */
public final class ModuleConfig {
    public static final String GROUP="hooklab.config.v1", CURRENT="current", GENERATION="generation", PREVIOUS="previous";
    public static final String MODULE="wordmaster", HOOK="wordmaster.night.filter", TARGET="com.fugo.wowsolitaire";
    private static final String SCHEMA="hooklab.module.configuration.v1";
    public static final int MAX_BYTES=16384, MAX_PROFILES=8;
    private ModuleConfig(){}
    private static void require(boolean condition){if(!condition)throw new IllegalArgumentException("invalid module configuration");}
    private static String name(String value){require(value!=null&&value.matches("[a-z][a-z0-9_-]{0,31}"));return value;}
    private static Map<?,?> object(Object value,String... fields){require(value instanceof Map<?,?>);Map<?,?> result=(Map<?,?>)value;require(result.keySet().equals(Set.of(fields)));return result;}
    private static String string(Object value){require(value instanceof String);return (String)value;}
    public static final class Filter {
        public final boolean custom;
        public final int opacity;
        public final String tone;
        public Filter(boolean custom,int opacity,String tone){require(opacity>=40&&opacity<=128&&Set.of("navy","neutral","warm").contains(tone));this.custom=custom;this.opacity=opacity;this.tone=tone;}
        public int color(){if(!custom)return 0x64010712;int rgb="navy".equals(tone)?0x010712:"warm".equals(tone)?0x180c03:0x000000;return (opacity<<24)|rgb;}
        private String json(){return "{\"custom\":"+custom+",\"opacity\":"+opacity+",\"tone\":\""+tone+"\"}";}
        private static Filter read(Object value){Map<?,?> row=object(value,"custom","opacity","tone");require(row.get("custom") instanceof Boolean&&row.get("opacity") instanceof Long);return new Filter((Boolean)row.get("custom"),Math.toIntExact((Long)row.get("opacity")),string(row.get("tone")));}
    }
    public static Filter defaultsFilter(){return new Filter(false,100,"navy");}
    public static final class Document {
        public final Map<String,Filter> profiles;
        public final String active,appOverride;
        public Document(Map<String,Filter> profiles,String active,String override){
            require(profiles!=null&&profiles.size()>0&&profiles.size()<=MAX_PROFILES);TreeMap<String,Filter> copy=new TreeMap<>();
            for(Map.Entry<String,Filter> row:profiles.entrySet())copy.put(name(row.getKey()),Objects.requireNonNull(row.getValue()));
            require(copy.containsKey("default")&&copy.containsKey(name(active))&&override!=null&&(override.isEmpty()||copy.containsKey(name(override))));
            this.profiles=Collections.unmodifiableMap(copy);this.active=active;appOverride=override;
        }
        public Filter effective(String module,String hook,String pkg){require(MODULE.equals(module)&&HOOK.equals(hook)&&TARGET.equals(pkg));return profiles.get(appOverride.isEmpty()?active:appOverride);}
        public Document put(String profile,Filter values){Map<String,Filter> copy=new TreeMap<>(profiles);copy.put(name(profile),values);return new Document(copy,active,appOverride);}
        public Document choose(String profile){return new Document(profiles,profile,appOverride);}
        public Document override(String profile){return new Document(profiles,active,profile);}
        public Document remove(String profile){require(!"default".equals(profile)&&profiles.containsKey(profile));Map<String,Filter> copy=new TreeMap<>(profiles);copy.remove(profile);return new Document(copy,active.equals(profile)?"default":active,appOverride.equals(profile)?"":appOverride);}
        public String canonical(){StringBuilder result=new StringBuilder("{\"schema\":\"").append(SCHEMA).append("\",\"module\":\"").append(MODULE).append("\",\"hook\":\"").append(HOOK).append("\",\"active\":\"").append(active).append("\",\"overrides\":{");
            if(!appOverride.isEmpty())result.append('"').append(TARGET).append("\":\"").append(appOverride).append('"');result.append("},\"profiles\":{");boolean first=true;for(Map.Entry<String,Filter> row:profiles.entrySet()){if(!first)result.append(',');first=false;result.append('"').append(row.getKey()).append("\":").append(row.getValue().json());}return result.append("}}").toString();}
        public String digest(){return sha(canonical());}
    }
    public static Document defaults(){return new Document(Collections.singletonMap("default",defaultsFilter()),"default","");}
    public static Document parse(byte[] data){
        require(data!=null&&data.length>0&&data.length<=MAX_BYTES);Map<?,?> root=object(HookSdk.StrictJson.read(data),"schema","module","hook","active","overrides","profiles");
        require(SCHEMA.equals(root.get("schema"))&&MODULE.equals(root.get("module"))&&HOOK.equals(root.get("hook"))&&root.get("profiles") instanceof Map<?,?>&&root.get("overrides") instanceof Map<?,?>);
        Map<?,?> profiles=(Map<?,?>)root.get("profiles"),overrides=(Map<?,?>)root.get("overrides");require(overrides.isEmpty()||overrides.keySet().equals(Set.of(TARGET)));require(profiles.size()<=MAX_PROFILES);
        Map<String,Filter> values=new TreeMap<>();for(Map.Entry<?,?> row:profiles.entrySet())values.put(name(string(row.getKey())),Filter.read(row.getValue()));
        return new Document(values,string(root.get("active")),overrides.isEmpty()?"":string(overrides.get(TARGET)));
    }
    private static Document readCanonical(Object raw){String text=string(raw);Document result=parse(text.getBytes(StandardCharsets.UTF_8));require(result.canonical().equals(text));return result;}
    public static final class Frame {
        public final Document current,previous;
        private Frame(Document current,Document previous){this.current=current;this.previous=previous;}
        public boolean same(Frame other){return current.canonical().equals(other.current.canonical())&&(previous==null?other.previous==null:other.previous!=null&&previous.canonical().equals(other.previous.canonical()));}
    }
    /** Framework preference readback: absent legacy state has defaults; partial or corrupt state fails closed. */
    public static Frame read(Map<String,?> state){
        require(state!=null);if(state.isEmpty())return new Frame(defaults(),null);
        require(state.keySet().equals(Set.of(CURRENT,GENERATION,PREVIOUS)));
        Document current=readCanonical(state.get(CURRENT));require(current.digest().equals(state.get(GENERATION)));
        String previous=string(state.get(PREVIOUS));return new Frame(current,previous.isEmpty()?null:readCanonical(previous));
    }
    public static Map<String,String> replacement(Frame before,Document current){
        require(before!=null&&current!=null);Map<String,String> result=new TreeMap<>();result.put(CURRENT,current.canonical());result.put(GENERATION,current.digest());result.put(PREVIOUS,before.current.canonical());return Collections.unmodifiableMap(result);
    }
    private static String sha(String value){try{byte[] raw=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder(64);for(byte v:raw)b.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return b.toString();}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
