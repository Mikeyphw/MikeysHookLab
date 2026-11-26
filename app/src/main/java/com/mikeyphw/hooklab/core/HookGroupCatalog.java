package com.mikeyphw.hooklab.core;

import com.mikeyphw.hooklab.runtime.BuiltinRegistry;
import com.mikeyphw.hooklab.sdk.HookSdk.HookDescriptor;
import com.mikeyphw.hooklab.sdk.HookSdk.ModuleDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** UI-only grouping: the native TweakCatalog remains the single hook registry and
 * LSPosed preferences/scope remain independently keyed by TweakSpec.id/packageName.
 * Never install hooks, toggle a group, or infer active runtime state here.
 */
public final class HookGroupCatalog {
    private HookGroupCatalog() { }

    public static final class Group {
        public final String id, title, description;
        public final List<TweakSpec> members;
        public final List<String> packages;
        private Group(String id, String title, String description, List<TweakSpec> members) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.members = Collections.unmodifiableList(new ArrayList<>(members));
            LinkedHashSet<String> pkgs = new LinkedHashSet<>();
            for (TweakSpec item : members) pkgs.add(item.packageName);
            this.packages = Collections.unmodifiableList(new ArrayList<>(pkgs));
        }
    }
    /** Exact grouping of *registered* hooks, with explicit reviewed IDs only.
     * Fail closed on omitted, duplicate or unexpectedly reassigned IDs: an
     * unreviewed hook must not silently appear under an unrelated module.
     */
    public static List<Group> groups(List<TweakSpec> registered) {
        Objects.requireNonNull(registered, "registered");
        Map<String, TweakSpec> byId = new LinkedHashMap<>();
        for (TweakSpec spec : registered) {
            Objects.requireNonNull(spec, "spec");
            if (byId.putIfAbsent(spec.id, spec) != null)
                throw new IllegalArgumentException("Duplicate hook ID: " + spec.id);
        }
        BuiltinRegistry registry = BuiltinRegistry.current();
        List<Group> groups = new ArrayList<>();
        for (ModuleDescriptor module : registry.modules()) {
            List<TweakSpec> members = new ArrayList<>();
            for (HookDescriptor hook : registry.hooks()) {
                if (!module.id.equals(hook.moduleId)) continue;
                TweakSpec spec = byId.remove(hook.id);
                if (spec != null) members.add(spec);
            }
            if (!members.isEmpty()) groups.add(new Group(module.groupId,module.title,module.description,members));
        }
        if (!byId.isEmpty()) throw new IllegalStateException("Registered hook without reviewed group");
        return Collections.unmodifiableList(groups);
    }

    /** A stable hook can be selected independently even when two share a package. */
    public static TweakSpec byId(List<TweakSpec> registered, String id) {
        if (id == null) return null;
        for (TweakSpec spec : registered) if (id.equals(spec.id)) return spec;
        return null;
    }

    public static Map<String,List<TweakSpec>> byPackage(List<TweakSpec> registered) {
        Objects.requireNonNull(registered, "registered");
        Map<String,List<TweakSpec>> packages = new LinkedHashMap<>();
        for (TweakSpec spec : registered)
            packages.computeIfAbsent(spec.packageName, ignored -> new ArrayList<>()).add(spec);
        Map<String,List<TweakSpec>> result = new LinkedHashMap<>();
        for (Map.Entry<String,List<TweakSpec>> group : packages.entrySet())
            result.put(group.getKey(), Collections.unmodifiableList(new ArrayList<>(group.getValue())));
        return Collections.unmodifiableMap(result);
    }

    /** Locale-stable, read-only search; avoids changing any hook or scope. */
    public static boolean matches(String query, String... fields) {
        if (query == null || query.trim().isEmpty()) return true;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        for (String field : fields)
            if (field != null && field.toLowerCase(Locale.ROOT).contains(needle)) return true;
        return false;
    }
}
