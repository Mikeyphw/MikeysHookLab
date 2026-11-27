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
    static void require(boolean condition){if(!condition)throw new IllegalArgumentException("invalid module configuration");}
    static String name(String value){require(value!=null&&value.matches("[a-z][a-z0-9_-]{0,31}"));return value;}
    static Map<?,?> object(Object value,String... fields){require(value instanceof Map<?,?>);Map<?,?> result=(Map<?,?>)value;require(result.keySet().equals(Set.of(fields)));return result;}
    static String string(Object value){require(value instanceof String);return (String)value;}
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
    /** Shared typed editor surface. Existing Word Master Document ABI remains intact. */
    public interface ProfileDocument<D extends ProfileDocument<D,V>,V> {
        String module(); Map<String,V> profiles(); String active(); String appOverride();
        D put(String profile,V values); D choose(String profile); D override(String profile); D remove(String profile);
        String canonical(); String digest();
    }
    /** Shared profile constraints and immutable edit operations for reviewed schemas. */
    static final class Profiles<V> {
        final Map<String,V> values; final String active,override;
        Profiles(Map<String,V> profiles,String active,String override){
            require(profiles!=null&&profiles.size()>0&&profiles.size()<=MAX_PROFILES);TreeMap<String,V> copy=new TreeMap<>();
            for(Map.Entry<String,V> row:profiles.entrySet())copy.put(name(row.getKey()),Objects.requireNonNull(row.getValue()));
            require(copy.containsKey("default")&&copy.containsKey(name(active))&&override!=null&&(override.isEmpty()||copy.containsKey(name(override))));
            values=Collections.unmodifiableMap(copy);this.active=active;this.override=override;
        }
        V effective(){return values.get(override.isEmpty()?active:override);}
        Profiles<V> put(String profile,V value){Map<String,V> copy=new TreeMap<>(values);copy.put(name(profile),value);return new Profiles<>(copy,active,override);}
        Profiles<V> choose(String profile){return new Profiles<>(values,profile,override);}
        Profiles<V> override(String profile){return new Profiles<>(values,active,profile);}
        Profiles<V> remove(String profile){require(!"default".equals(profile)&&values.containsKey(profile));Map<String,V> copy=new TreeMap<>(values);copy.remove(profile);return new Profiles<>(copy,active.equals(profile)?"default":active,override.equals(profile)?"":override);}
        String canonical(String module,String hook,String target,java.util.function.Function<V,String> encode){
            StringBuilder result=new StringBuilder("{\"schema\":\"").append(SCHEMA).append("\",\"module\":\"").append(module).append("\",\"hook\":\"").append(hook).append("\",\"active\":\"").append(active).append("\",\"overrides\":{");
            if(!override.isEmpty())result.append('"').append(target).append("\":\"").append(override).append('"');result.append("},\"profiles\":{");boolean first=true;for(Map.Entry<String,V> row:values.entrySet()){if(!first)result.append(',');first=false;result.append('"').append(row.getKey()).append("\":").append(encode.apply(row.getValue()));}return result.append("}}").toString();
        }
    }
    static <V> Profiles<V> parseProfiles(byte[] data,String module,String hook,String target,java.util.function.Function<Object,V> decode){
        require(data!=null&&data.length>0&&data.length<=MAX_BYTES);Map<?,?> root=object(HookSdk.StrictJson.read(data),"schema","module","hook","active","overrides","profiles");
        require(SCHEMA.equals(root.get("schema"))&&module.equals(root.get("module"))&&hook.equals(root.get("hook"))&&root.get("profiles") instanceof Map<?,?>&&root.get("overrides") instanceof Map<?,?>);
        Map<?,?> profiles=(Map<?,?>)root.get("profiles"),overrides=(Map<?,?>)root.get("overrides");require(overrides.isEmpty()||overrides.keySet().equals(Set.of(target)));require(profiles.size()<=MAX_PROFILES);
        Map<String,V> values=new TreeMap<>();for(Map.Entry<?,?> row:profiles.entrySet())values.put(name(string(row.getKey())),decode.apply(row.getValue()));
        return new Profiles<>(values,string(root.get("active")),overrides.isEmpty()?"":string(overrides.get(target)));
    }
    public static ProfileDocument<?,?> parseForModule(String module,byte[] data){
        if(MODULE.equals(module))return parse(data);
        if(ChatGptConfig.MODULE.equals(module))return ChatGptConfig.parse(data);
        throw new IllegalArgumentException("unknown configuration module");
    }
    public static ProfileDocument<?,?> parseDocument(byte[] data){
        require(data!=null&&data.length>0&&data.length<=MAX_BYTES);Map<?,?> root=object(HookSdk.StrictJson.read(data),"schema","module","hook","active","overrides","profiles");return parseForModule(string(root.get("module")),data);
    }
    public static final class Document implements ProfileDocument<Document,Filter> {
        public final Map<String,Filter> profiles;
        public final String active,appOverride;
        private final Profiles<Filter> data;
        public Document(Map<String,Filter> profiles,String active,String override){this(new Profiles<>(profiles,active,override));}
        private Document(Profiles<Filter> data){this.data=data;profiles=data.values;active=data.active;appOverride=data.override;}
        public String module(){return MODULE;} public Map<String,Filter> profiles(){return profiles;} public String active(){return active;} public String appOverride(){return appOverride;}
        public Filter effective(String module,String hook,String pkg){require(MODULE.equals(module)&&HOOK.equals(hook)&&TARGET.equals(pkg));return data.effective();}
        public Document put(String profile,Filter values){return new Document(data.put(profile,values));}
        public Document choose(String profile){return new Document(data.choose(profile));}
        public Document override(String profile){return new Document(data.override(profile));}
        public Document remove(String profile){return new Document(data.remove(profile));}
        public String canonical(){return data.canonical(MODULE,HOOK,TARGET,Filter::json);}
        public String digest(){return sha(canonical());}
    }
    public static Document defaults(){return new Document(Collections.singletonMap("default",defaultsFilter()),"default","");}
    public static Document parse(byte[] data){return new Document(parseProfiles(data,MODULE,HOOK,TARGET,Filter::read));}
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
    static String sha(String value){try{byte[] raw=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder(64);for(byte v:raw)b.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return b.toString();}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
