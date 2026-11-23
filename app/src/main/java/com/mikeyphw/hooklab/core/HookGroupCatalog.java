package com.mikeyphw.hooklab.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    private static final class Definition {
        final String id, title, description;
        final List<String> hookIds;
        Definition(String id, String title, String description, String... hookIds) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.hookIds = Collections.unmodifiableList(Arrays.asList(hookIds));
        }
    }
    private static final List<Definition> DEFINITIONS = Collections.unmodifiableList(Arrays.asList(
        new Definition("group.obtainx", "ObtainX Integration",
            "Independent Neo Store and Droid-ify actions; imports are always manual.",
            "neostore.obtainx.manual", "droidify.obtainx.manual"),
        new Definition("group.chatgpt_termux", "ChatGPT Code Diagnostics",
            "Read-only callback diagnostic. Termux execution is not available.",
            "chatgpt.codeblock.copy.probe"),
        new Definition("group.mcc", "MCC Diamond Control",
            "Manual button with explicit confirmation for root-sensitive control.",
            "mcc.diamond.control"),
        new Definition("group.wordmaster", "Word Master Night Filter",
            "Experimental dimming; not a full Unity dark theme.",
            "wordmaster.night.filter")
    ));

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
        List<Group> groups = new ArrayList<>();
        Set<String> assigned = new HashSet<>();
        for (Definition definition : DEFINITIONS) {
            List<TweakSpec> members = new ArrayList<>();
            for (String hookId : definition.hookIds) {
                if (!assigned.add(hookId))
                    throw new IllegalStateException("Hook mapped to multiple groups: " + hookId);
                TweakSpec spec = byId.get(hookId);
                if (spec != null) members.add(spec);
            }
            // Do not advertise groups whose hooks are not registered in the app.
            if (!members.isEmpty())
                groups.add(new Group(definition.id, definition.title, definition.description, members));
        }
        for (String id : byId.keySet())
            if (!assigned.contains(id))
                throw new IllegalStateException("Registered hook without reviewed group: " + id);
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
