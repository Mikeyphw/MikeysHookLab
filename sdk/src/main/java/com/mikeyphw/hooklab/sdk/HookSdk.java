package com.mikeyphw.hooklab.sdk;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/** SDK v1 binary boundary. No Android UI, network, manager storage, or credentials. */
public final class HookSdk {
    public static final int VERSION = 1;
    private HookSdk() {}
    public static void requireCompatible(int minimum, int maximum) {
        if (minimum < 1 || minimum > VERSION || maximum < VERSION || maximum < minimum)
            throw new IllegalArgumentException("SDK version incompatible");
    }
    public interface Entrypoint {
        void install(Context context) throws Exception;
    }
    public enum Event { INSTALL_STARTED, INSTALL_COMPLETED, PROBE_READY }
    public enum ApplyMode { RESTART_HOST }
    public interface Diagnostics { void record(Event code); }
    /** Narrow registration port for the owned feasibility target. Other hook APIs
     * require separately reviewed SDK extensions. No arbitrary shell/network API. */
    public interface Marker { String after(String original); }
    public interface Hooks { void registerMarker(String id, Marker marker); }

    public static final class Settings {
        private final Map<String,Boolean> flags;
        public Settings(Map<String,Boolean> values) {
            Map<String,Boolean> copy = new LinkedHashMap<>();
            for (Map.Entry<String,Boolean> value : Objects.requireNonNull(values).entrySet()) {
                copy.put(HookDescriptor.identifier(value.getKey()), Objects.requireNonNull(value.getValue()));
            }
            flags = Collections.unmodifiableMap(copy);
        }
        public boolean enabled(String key) { return Boolean.TRUE.equals(flags.get(HookDescriptor.identifier(key))); }
    }
    public static final class Context {
        public final String packageName, processName, generation;
        public final long version;
        public final ClassLoader hostLoader;
        public final Settings settings;
        public final Diagnostics diagnostics;
        public final Hooks hooks;
        public final ApplyMode applyMode = ApplyMode.RESTART_HOST;
        private final PackHookContext packHost;
        public Context(String pkg, String process, long version, String generation, ClassLoader loader,
                       Settings settings, Diagnostics diagnostics, Hooks hooks) {
            this(pkg,process,version,generation,loader,settings,diagnostics,hooks,null,null);
        }
        /** Only the trusted parent dispatcher supplies this host registration port. */
        public Context(String pkg,String process,long version,String generation,ClassLoader loader,
                       Settings settings,Diagnostics diagnostics,Hooks hooks,Object hostContext,PackHookApi api) {
            if (version < 1 || !Objects.requireNonNull(pkg).equals(process) || generation == null || !generation.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("unverified host context");
            packageName = pkg; processName = process; this.version = version; this.generation = generation;
            hostLoader = Objects.requireNonNull(loader); this.settings = Objects.requireNonNull(settings);
            this.diagnostics = Objects.requireNonNull(diagnostics); this.hooks=Objects.requireNonNull(hooks);
            if((hostContext==null)!=(api==null))throw new IllegalArgumentException("incomplete pack host port");
            packHost=api==null?null:new PackHookContext(this,hostContext,api);
        }
        public PackHookContext packHost(){if(packHost==null)throw new IllegalStateException("pack host port unavailable");return packHost;}
    }

    /** Immutable SDK v1 metadata. No executable class references. */
    public static final class HookDescriptor {
        public final String id, moduleId, packageName, title, description;
        public final long minVersion, maxVersion;

        public HookDescriptor(String id, String moduleId, String packageName, long minVersion,
                              long maxVersion, String title, String description) {
            this.id = identifier(id);
            this.moduleId = identifier(moduleId);
            this.packageName = Objects.requireNonNull(packageName);
            if (!packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+") ||
                    minVersion < 1 || maxVersion < minVersion) throw new IllegalArgumentException("target identity");
            this.minVersion = minVersion;
            this.maxVersion = maxVersion;
            this.title = text(title);
            this.description = text(description);
        }
        public static String identifier(String value) {
            if (value == null || !value.matches("[a-z][a-z0-9_.-]{0,127}")) throw new IllegalArgumentException("identifier");
            return value;
        }
        public static String text(String value) {
            if (value == null || value.trim().isEmpty() || value.length() > 4096) throw new IllegalArgumentException("metadata text");
            return value;
        }
        /** Scope must be explicitly confirmed by the caller's framework authority. */
        public boolean eligible(String pkg, String process, long version, boolean scope, boolean optIn, boolean safe) {
            return scope && optIn && safe && matchesTarget(packageName,minVersion,maxVersion,pkg,process,version);
        }
        public static boolean matchesTarget(String target,long min,long max,String pkg,String process,long version) {
            return target.equals(pkg) && target.equals(process) && version >= min && version <= max;
        }
    }

    /** Metadata-only module identity, independent from executable adapters. */
    public static final class ModuleDescriptor {
        public final String id, groupId, title, description;
        public ModuleDescriptor(String id, String groupId, String title, String description) {
            this.id = HookDescriptor.identifier(id);
            this.groupId = HookDescriptor.identifier(groupId);
            this.title = HookDescriptor.text(title);
            this.description = HookDescriptor.text(description);
        }
    }

    /** Bounded strict JSON profile: UTF-8, unique keys, signed 64-bit integers, no trailing input. */
    public static final class StrictJson {
        public static final int MAX_BYTES = 65536;
        private final String input;
        private int pos, nodes;
        private StrictJson(String input) { this.input = input; }
        public static Object read(byte[] bytes) {
            if (bytes == null || bytes.length > MAX_BYTES) throw invalid();
            try {
                String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                StrictJson reader = new StrictJson(text);
                Object result = reader.value(0); reader.space();
                if (reader.pos != text.length()) throw invalid();
                return result;
            } catch (CharacterCodingException e) { throw invalid(); }
        }
        private static IllegalArgumentException invalid() { return new IllegalArgumentException("invalid bounded JSON"); }
        private void space() { while (pos < input.length() && " \r\n\t".indexOf(input.charAt(pos)) >= 0) pos++; }
        private boolean eat(char c) { space(); if (pos < input.length() && input.charAt(pos) == c) { pos++; return true; } return false; }
        private void need(char c) { if (!eat(c)) throw invalid(); }
        private Object value(int depth) {
            if (depth > 16 || ++nodes > 4096) throw invalid();
            space(); if (pos == input.length()) throw invalid(); char c = input.charAt(pos);
            if (c == '{') {
                pos++; Map<String,Object> map = new LinkedHashMap<>();
                if (eat('}')) return map;
                do { String key = string(); need(':'); if (map.containsKey(key)) throw invalid(); map.put(key, value(depth+1)); } while (eat(','));
                need('}'); return map;
            }
            if (c == '[') {
                pos++; List<Object> list = new ArrayList<>(); if (eat(']')) return list;
                do { list.add(value(depth+1)); } while (eat(',')); need(']'); return list;
            }
            if (c == '"') return string();
            if (input.startsWith("true",pos)) { pos+=4; return true; }
            if (input.startsWith("false",pos)) { pos+=5; return false; }
            if (input.startsWith("null",pos)) { pos+=4; return null; }
            int begin=pos; if (c=='-') pos++;
            if (pos==input.length()) throw invalid();
            if (input.charAt(pos)=='0') pos++;
            else { if (input.charAt(pos)<'1'||input.charAt(pos)>'9') throw invalid(); while(pos<input.length()&&input.charAt(pos)>='0'&&input.charAt(pos)<='9') pos++; }
            try { return Long.parseLong(input.substring(begin,pos)); } catch(NumberFormatException e) { throw invalid(); }
        }
        private String string() {
            need('"'); StringBuilder b = new StringBuilder();
            while (pos < input.length()) {
                char c=input.charAt(pos++);
                if (c=='"') {
                    for(int i=0;i<b.length();i++) { char x=b.charAt(i); if(Character.isHighSurrogate(x)) { if(++i>=b.length()||!Character.isLowSurrogate(b.charAt(i))) throw invalid(); } else if(Character.isLowSurrogate(x)) throw invalid(); }
                    return b.toString();
                }
                if(c<32) throw invalid();
                if(c=='\\') {
                    if(pos==input.length()) throw invalid(); char e=input.charAt(pos++);
                    switch(e) {
                        case '"': case '\\': case '/': c=e; break;
                        case 'b': c='\b'; break; case 'f': c='\f'; break; case 'n': c='\n'; break; case 'r': c='\r'; break; case 't': c='\t'; break;
                        case 'u': int n=0; for(int i=0;i<4;i++){ if(pos==input.length())throw invalid(); char h=input.charAt(pos++); int v="0123456789abcdef".indexOf(Character.toLowerCase(h)); if(v<0||h>127)throw invalid(); n=n*16+v;} c=(char)n; break;
                        default: throw invalid();
                    }
                }
                b.append(c);
            }
            throw invalid();
        }
    }

    /** Version-one metadata contracts only. Parsing is not signature, install or release approval. */
    public static final class ContractReader {
        private ContractReader() {}
        public static String validate(byte[] bytes) {
            Object parsed=StrictJson.read(bytes);
            if(!(parsed instanceof Map)) throw invalid();
            Map<?,?> obj=(Map<?,?>)parsed;
            String schema=text(obj,"schema");
            switch(schema) {
                case "hooklab.module.manifest.v1": manifest(obj); break;
                case "hooklab.settings.v1": settings(obj); break;
                case "hooklab.index.v1": index(obj); break;
                case "hooklab.module.receipt.v1": receipt(obj); break;
                default: throw invalid();
            }
            return schema;
        }
        private static IllegalArgumentException invalid(){return new IllegalArgumentException("invalid module metadata contract");}
        private static Map<?,?> row(Object value,String...fields){
            if(!(value instanceof Map))throw invalid();Map<?,?> map=(Map<?,?>)value;
            if(!map.keySet().equals(new HashSet<>(Arrays.asList(fields))))throw invalid();return map;
        }
        private static String text(Map<?,?> map,String key){Object v=map.get(key);if(!(v instanceof String))throw invalid();return (String)v;}
        private static String match(Map<?,?> map,String key,String pattern){String v=text(map,key);if(!v.matches(pattern))throw invalid();return v;}
        private static String id(Map<?,?> map,String key){return HookDescriptor.identifier(text(map,key));}
        private static long number(Map<?,?> map,String key){Object v=map.get(key);if(!(v instanceof Long))throw invalid();return (Long)v;}
        private static long positive(Map<?,?> map,String key){long v=number(map,key);if(v<1)throw invalid();return v;}
        private static void version(Map<?,?> map){match(map,"version","(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})");}
        private static void hash(Map<?,?> map,String key,int len){match(map,key,"[0-9a-f]{"+len+"}");}
        private static List<?> array(Map<?,?> map,String key){Object v=map.get(key);if(!(v instanceof List))throw invalid();return (List<?>)v;}
        private static void manifest(Map<?,?> obj){
            row(obj,"schema","module_id","version","sdk_min","sdk_max","api","entrypoint","hooks");
            String module=match(obj,"module_id","[a-z][a-z0-9_]{0,63}");version(obj);
            long minimum=positive(obj,"sdk_min"),maximum=positive(obj,"sdk_max");
            if(minimum>Integer.MAX_VALUE||maximum>Integer.MAX_VALUE)throw invalid();
            HookSdk.requireCompatible((int)minimum,(int)maximum);
            if(number(obj,"api")!=102)throw invalid();
            match(obj,"entrypoint","com\\.mikeyphw\\.hooklab\\.modules\\."+module+"\\.[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
            Set<String> seen=new HashSet<>();for(Object raw:array(obj,"hooks")){
                Map<?,?> hook=row(raw,"id","package","min_version","max_version");
                if(!seen.add(id(hook,"id")))throw invalid();
                match(hook,"package","[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
                if(positive(hook,"max_version")<positive(hook,"min_version"))throw invalid();
            }if(seen.isEmpty())throw invalid();
        }
        private static void settings(Map<?,?> obj){
            row(obj,"schema","module_id","generation","flags");id(obj,"module_id");hash(obj,"generation",64);
            Object raw=obj.get("flags");if(!(raw instanceof Map))throw invalid();
            for(Map.Entry<?,?> e:((Map<?,?>)raw).entrySet()){
                HookDescriptor.identifier((String)e.getKey());if(!(e.getValue() instanceof Boolean))throw invalid();
            }
        }
        private static void index(Map<?,?> obj){
            row(obj,"schema","revision","modules");positive(obj,"revision");Set<String> seen=new HashSet<>();
            for(Object raw:array(obj,"modules")){
                Map<?,?> m=row(raw,"module_id","version","sha256","size");
                String id=id(m,"module_id");version(m);if(!seen.add(id+":"+text(m,"version")))throw invalid();
                hash(m,"sha256",64);positive(m,"size");
            }
        }
        private static void receipt(Map<?,?> obj){
            row(obj,"schema","module_id","version","source_sha","source_tree","sha256","state");
            id(obj,"module_id");version(obj);hash(obj,"source_sha",40);hash(obj,"source_tree",40);hash(obj,"sha256",64);
            match(obj,"state","STAGED|VERIFIED|READY|ACTIVE|FAILED|ROLLED_BACK");
        }
    }
}
