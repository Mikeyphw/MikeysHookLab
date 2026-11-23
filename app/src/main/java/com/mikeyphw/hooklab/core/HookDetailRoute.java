package com.mikeyphw.hooklab.core;

import java.util.List;
import java.util.Objects;

/** UI-only exact navigation: never select a different hook as a fallback.
 * This class does not read or write LSPosed state or install hooks.
 */
public final class HookDetailRoute {
    private HookDetailRoute() { }

    public static TweakSpec hook(List<TweakSpec> registered, String id) {
        Objects.requireNonNull(registered, "registered");
        if (id == null || id.isEmpty()) return null;
        TweakSpec selected = null;
        for (TweakSpec candidate : registered) {
            if (candidate.id.equals(id)) {
                if (selected != null) throw new IllegalStateException("Duplicate hook ID");
                selected = candidate;
            }
        }
        return selected;
    }

    public static HookGroupCatalog.Group group(List<HookGroupCatalog.Group> groups, String id) {
        Objects.requireNonNull(groups, "groups");
        if (id == null || id.isEmpty()) return null;
        HookGroupCatalog.Group selected = null;
        for (HookGroupCatalog.Group candidate : groups) {
            if (candidate.id.equals(id)) {
                if (selected != null) throw new IllegalStateException("Duplicate group ID");
                selected = candidate;
            }
        }
        return selected;
    }

    public static HookGroupCatalog.Group groupForHook(List<HookGroupCatalog.Group> groups,
                                                       String hookId) {
        if (hookId == null) return null;
        HookGroupCatalog.Group selected = null;
        for (HookGroupCatalog.Group group : groups) {
            for (TweakSpec member : group.members) {
                if (hookId.equals(member.id)) {
                    if (selected != null) throw new IllegalStateException("Hook in multiple groups");
                    selected = group;
                }
            }
        }
        return selected;
    }

    public static boolean groupContains(HookGroupCatalog.Group group, String hookId) {
        if (group == null || hookId == null) return false;
        for (TweakSpec member : group.members) if (hookId.equals(member.id)) return true;
        return false;
    }
}
