package com.mikeyphw.hooklab.core;

/** Framework-owned group. Never use MODE_WORLD_READABLE or a module-private file in hosts. */
public final class TweakKeys {
    private TweakKeys() {}
    public static final String GROUP = "hooklab.tweaks.v1";
    /** Framework-owned, default-off diagnostic option. No host-private storage. */
    public static final String VERBOSE_LSPOSED_LOGS = "diagnostics.verbose_logs";
    public static String enabled(String id) {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]*"))
            throw new IllegalArgumentException("Invalid tweak key");
        return "enabled." + id;
    }
}
