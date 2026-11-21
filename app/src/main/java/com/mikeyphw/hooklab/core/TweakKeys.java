package com.mikeyphw.hooklab.core;

/** Framework-owned group. Never use MODE_WORLD_READABLE or a module-private file in hosts. */
public final class TweakKeys {
    private TweakKeys() {}
    public static final String GROUP = "hooklab.tweaks.v1";
    public static String enabled(String id) {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]*"))
            throw new IllegalArgumentException("Invalid tweak key");
        return "enabled." + id;
    }
}
