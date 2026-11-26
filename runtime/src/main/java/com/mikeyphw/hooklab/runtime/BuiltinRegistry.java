package com.mikeyphw.hooklab.runtime;

import com.mikeyphw.hooklab.sdk.HookSdk.HookDescriptor;
import com.mikeyphw.hooklab.sdk.HookSdk.ModuleDescriptor;
import com.mikeyphw.hooklab.sdk.HookSdk;
import com.mikeyphw.hooklab.sdk.HookSdk.StrictJson;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable APK-bound registry. Both host and manager read this same compiled resource.
 * External registry activation and transport are deliberately not implemented here. */
public final class BuiltinRegistry {
    public static final String RESOURCE = "/META-INF/hooklab/builtin-registry.json";
    private static final class Holder { static final BuiltinRegistry INSTANCE = load(); }
    public static BuiltinRegistry current() { return Holder.INSTANCE; }
    public final String generation;
    private final List<HookDescriptor> hooks;
    private final List<ModuleDescriptor> modules;
    private final Map<String,HookDescriptor> byId;
    private BuiltinRegistry(String generation, List<ModuleDescriptor> modules, List<HookDescriptor> hooks) {
        this.generation = generation;
        this.modules = Collections.unmodifiableList(new ArrayList<>(modules));
        this.hooks = Collections.unmodifiableList(new ArrayList<>(hooks));
        Map<String,HookDescriptor> map = new LinkedHashMap<>();
        for (HookDescriptor h : hooks) map.put(h.id,h);
        byId = Collections.unmodifiableMap(map);
    }
    public List<HookDescriptor> hooks() { return hooks; }
    public List<ModuleDescriptor> modules() { return modules; }
    public HookDescriptor hook(String id) { return byId.get(id); }
    public boolean knowsPackage(String pkg) { for(HookDescriptor h:hooks) if(h.packageName.equals(pkg)) return true; return false; }
    private static BuiltinRegistry load() {
        try (InputStream stream = BuiltinRegistry.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("registry unavailable");
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
            while ((count = stream.read(buffer)) != -1) {
                if (count > StrictJson.MAX_BYTES - out.size()) throw new IllegalArgumentException("oversized registry");
                out.write(buffer,0,count);
            }
            return read(out.toByteArray());
        } catch (IOException e) { throw new IllegalStateException("registry unavailable", e); }
    }
    public static BuiltinRegistry read(byte[] bytes) {
        // Copy before parsing/hash to bind metadata to identical bytes even with a mutable caller.
        if(bytes==null||bytes.length>StrictJson.MAX_BYTES) throw new IllegalArgumentException("registry size");
        bytes=bytes.clone();
        Map<?,?> root = object(StrictJson.read(bytes),"schema","sdk_min","sdk_max","modules","hooks");
        if(!"hooklab.builtin.registry.v1".equals(root.get("schema"))) throw new IllegalArgumentException("registry schema");
        HookSdk.requireCompatible(Math.toIntExact(number(root,"sdk_min")), Math.toIntExact(number(root,"sdk_max")));
        List<ModuleDescriptor> modules=new ArrayList<>(); Set<String> moduleIds=new HashSet<>(), groupIds=new HashSet<>();
        for(Object item:list(root,"modules")) {
            Map<?,?> row=object(item,"id","group","title","description");
            ModuleDescriptor m=new ModuleDescriptor(string(row,"id"),string(row,"group"),string(row,"title"),string(row,"description"));
            if(!moduleIds.add(m.id)||!groupIds.add(m.groupId)) throw new IllegalArgumentException("duplicate module/group");
            modules.add(m);
        }
        List<HookDescriptor> hooks=new ArrayList<>(); Set<String> hookIds=new HashSet<>(), assigned=new HashSet<>();
        for(Object item:list(root,"hooks")) {
            Map<?,?> row=object(item,"id","module","package","min_version","max_version","title","description");
            HookDescriptor h=new HookDescriptor(string(row,"id"),string(row,"module"),string(row,"package"),number(row,"min_version"),number(row,"max_version"),string(row,"title"),string(row,"description"));
            if(!moduleIds.contains(h.moduleId)||!hookIds.add(h.id)) throw new IllegalArgumentException("unknown module/duplicate hook");
            assigned.add(h.moduleId); hooks.add(h);
        }
        if(hooks.isEmpty()||!assigned.equals(moduleIds)) throw new IllegalArgumentException("missing registry members");
        return new BuiltinRegistry(digest(bytes),modules,hooks);
    }
    private static Map<?,?> object(Object value,String... keys) {
        if(!(value instanceof Map)||!((Map<?,?>)value).keySet().equals(new HashSet<>(Arrays.asList(keys)))) throw new IllegalArgumentException("registry fields");
        return (Map<?,?>)value;
    }
    private static List<?> list(Map<?,?> obj,String key) { Object v=obj.get(key); if(!(v instanceof List)) throw new IllegalArgumentException("registry array"); return (List<?>)v; }
    private static String string(Map<?,?> obj,String key) { Object v=obj.get(key); if(!(v instanceof String)) throw new IllegalArgumentException("registry string"); return (String)v; }
    private static long number(Map<?,?> obj,String key) { Object v=obj.get(key); if(!(v instanceof Long)) throw new IllegalArgumentException("registry integer"); return (Long)v; }
    private static String digest(byte[] bytes) {
        try { byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes); StringBuilder s=new StringBuilder(64); for(byte b:hash) { s.append("0123456789abcdef".charAt((b>>>4)&15)); s.append("0123456789abcdef".charAt(b&15)); } return s.toString(); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable",e); }
    }

    /** Framework-owned metadata handshake; host has only the read port. */
    public static final class RegistryGeneration {
        public static final String GROUP = "hooklab.registry.v1";
        public static final String KEY = "builtin.generation";
        private RegistryGeneration() {}
        public interface Reader { String read(); }
        public interface Publisher extends Reader { boolean write(String generation); }
        public static boolean matches(String generation, Reader reader) {
            if (generation == null || !generation.matches("[0-9a-f]{64}") || reader == null) return false;
            try { return generation.equals(reader.read()); }
            catch (RuntimeException | LinkageError failure) { return false; }
        }
        /** Only manager code calls this. Never modifies existing hook preferences. */
        public static boolean publish(String generation, Publisher publisher) {
            if (generation == null || !generation.matches("[0-9a-f]{64}") || publisher == null) return false;
            try {
                if (generation.equals(publisher.read())) return true;
                return publisher.write(generation) && generation.equals(publisher.read());
            } catch (RuntimeException | LinkageError failure) { return false; }
        }
    }
}
