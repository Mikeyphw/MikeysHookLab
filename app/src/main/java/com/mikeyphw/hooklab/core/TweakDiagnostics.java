package com.mikeyphw.hooklab.core;

/** Privacy-preserving diagnostic codes and UI-managed framework safety locks. */
public final class TweakDiagnostics {
    private TweakDiagnostics() {}
    // Framework preferences are READ-ONLY in hooked apps. Only HookLab's UI edits this group.
    public static final String GROUP = "hooklab.safety.v1";

    public enum Outcome {
        NONE, INSTALLED, INSTALL_FAILED, QUARANTINED, DISABLED,
        PREFERENCE_UNAVAILABLE, QUARANTINE_UNAVAILABLE
    }

    public static String quarantineKey(String id) {
        TweakKeys.enabled(id); // identical reviewed tweak ID validation
        return "quarantine." + id;
    }

    /** Fixed categories only; never log exception messages, arguments, or stack traces. */
    public static String failureCategory(Throwable failure) {
        if (failure instanceof LinkageError) return "LINKAGE";
        if (failure instanceof SecurityException) return "SECURITY";
        if (failure instanceof RuntimeException) return "RUNTIME";
        if (failure instanceof Error) return "ERROR";
        return "CHECKED";
    }

    /** JVM fatal signals cannot be safely contained by third-party hook isolation. */
    public static void rethrowFatal(Throwable failure) {
        if (failure instanceof ThreadDeath) throw (ThreadDeath) failure;
        if (failure instanceof VirtualMachineError) throw (VirtualMachineError) failure;
    }
}
