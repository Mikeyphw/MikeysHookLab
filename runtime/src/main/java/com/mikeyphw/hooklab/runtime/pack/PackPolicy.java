package com.mikeyphw.hooklab.runtime.pack;

import com.mikeyphw.hooklab.runtime.BuiltinRegistry;
import com.mikeyphw.hooklab.sdk.HookSdk;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Set;

/** This migration admits only the reviewed catalog, not new host scope or capabilities. */
public final class PackPolicy {
    private PackPolicy() { }
    public static final Set<String> MODULES = Collections.unmodifiableSet(new TreeSet<>(java.util.Arrays.asList("obtainx","wordmaster","chatgpt")));
    public static Map<String,HookSdk.HookDescriptor> hooks() {
        Map<String,HookSdk.HookDescriptor> rows=new TreeMap<>();
        for(HookSdk.HookDescriptor h:BuiltinRegistry.current().hooks())if(MODULES.contains(h.moduleId))rows.put(h.id,h);
        return Collections.unmodifiableMap(rows);
    }
    public static HookSdk.HookDescriptor hook(String id) {
        HookSdk.HookDescriptor h=hooks().get(id);if(h==null)throw new IllegalArgumentException("unreviewed pack hook");return h;
    }
    public static void requireModule(String module) {
        if(!MODULES.contains(module))throw new IllegalArgumentException("unreviewed pack module");
    }
    public static void requireHash(String sha) {
        if(sha==null||!sha.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("invalid pack digest");
    }
    public static String remoteFile(String sha){requireHash(sha);return "hookpack-"+sha;}
    public static void verifyMigration(HookPack.Verified pack) {
        requireModule(pack.moduleId);
        if(!("com.mikeyphw.hooklab.modules."+pack.moduleId+".Entrypoint").equals(pack.entrypoint))throw new IllegalArgumentException("unreviewed pack entrypoint");
        Map<?,?> manifest=(Map<?,?>)HookSdk.StrictJson.read(pack.manifest());
        Map<String,HookSdk.HookDescriptor> expected=new TreeMap<>();
        for(HookSdk.HookDescriptor h:hooks().values())if(h.moduleId.equals(pack.moduleId))expected.put(h.id,h);
        for(Object item:(java.util.List<?>)manifest.get("hooks")){
            Map<?,?> row=(Map<?,?>)item;HookSdk.HookDescriptor h=expected.remove(row.get("id"));
            if(h==null||!h.packageName.equals(row.get("package"))||!Long.valueOf(h.minVersion).equals(row.get("min_version"))||!Long.valueOf(h.maxVersion).equals(row.get("max_version")))throw new IllegalArgumentException("pack target/catalog drift");
        }
        if(!expected.isEmpty())throw new IllegalArgumentException("pack hook roster incomplete");
        Map<?,?> provenance=(Map<?,?>)HookSdk.StrictJson.read(pack.provenance());
        if(!Long.valueOf(1).equals(provenance.get("settings_schema")))throw new IllegalArgumentException("unsupported pack settings migration");
    }
    public static int compareVersions(String a,String b) {
        String pattern="(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})";
        if(a==null||b==null||!a.matches(pattern)||!b.matches(pattern))throw new IllegalArgumentException("invalid pack version");
        String[] x=a.split("\\."),y=b.split("\\.");for(int i=0;i<3;i++){int c=Integer.compare(Integer.parseInt(x[i]),Integer.parseInt(y[i]));if(c!=0)return c;}return 0;
    }
}
