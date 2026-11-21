package com.mikeyphw.hooklab.core;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Opt-in, rate-limited, pre-declared stage events written through libxposed's
 * framework logger. No package identifiers, repository URLs, credentials,
 * account data, exception messages or host objects are serialized.
 *
 * Host processes read framework-owned remote preferences only. This class
 * never modifies preferences and never writes to Android logcat or files.
 */
public final class VerboseLogs {
    private VerboseLogs() { }
    public enum Event {
        HOST_DISPATCH_READY, TWEAK_DISPATCH_OUTCOME,
        MCC_HOOK_INSTALLED, MCC_BUTTON_ATTACHED, MCC_CONTROL_OPEN,
        MCC_CONTROL_OPEN_REFUSED,
        NEO_HOOK_INSTALLED, NEO_PAGE_READY, NEO_PAGE_WAITING,
        NEO_POLL_REFUSED, NEO_TAP, NEO_TAP_REFUSED,
        NEO_PREVIEW_REQUESTED, NEO_PREVIEW_REFUSED,
        DROID_HOOK_INSTALLED, DROID_ATTACH_ATTEMPT,
        DROID_TOOLBAR_ADDED, DROID_TOOLBAR_MISSING,
        DROID_TAP, DROID_SOURCE_REFUSED,
        DROID_PREVIEW_REQUESTED, DROID_PREVIEW_REFUSED
    }
    private static final long MIN_GAP_MS = 3000L;
    private static final AtomicLongArray LAST = new AtomicLongArray(Event.values().length);
    private static volatile SharedPreferences remotePreferences;
    private static volatile XposedInterface logger;

    /** Called after trusted host identity and framework preferences are verified. */
    public static void bind(SharedPreferences preferences, XposedInterface framework) {
        if (preferences == null || framework == null) throw new IllegalArgumentException("Missing verified logger");
        remotePreferences = preferences;
        logger = framework;
    }

    /** Read the live framework preference on each event; default is always off. */
    public static boolean enabled() {
        SharedPreferences p = remotePreferences;
        if (p == null) return false;
        try {
            return p.getBoolean(TweakKeys.VERBOSE_LSPOSED_LOGS, false);
        } catch (RuntimeException error) {
            return false;
        }
    }

    static boolean gate(boolean enabled, long now, long prior, long gap) {
        return enabled && (prior == 0 || now < prior || now - prior >= gap);
    }

    /** Bounded vocabulary prevents accidental sensitive data leaks. */
    public static void emit(Event event) {
        if (event == null || !enabled()) return;
        XposedInterface xp = logger;
        if (xp == null) return;
        long now = SystemClock.elapsedRealtime();
        int index = event.ordinal();
        long prior = LAST.get(index);
        if (!gate(true, now, prior, MIN_GAP_MS) || !LAST.compareAndSet(index, prior, now)) return;
        try {
            xp.log(Log.DEBUG, "HookLabVerbose", "event=" + event.name());
        } catch (RuntimeException | LinkageError ignored) {
            // Diagnostic logging must never change target-app behavior.
        }
    }
}
