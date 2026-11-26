package com.mikeyphw.hooklab.hook;

import android.content.Context;
import io.github.libxposed.api.XposedModule;

/** Release builds never recognize a transport experiment target. */
final class ProbeBridge {
    private ProbeBridge() {}
    static boolean supportsHost(String pkg) { return false; }
    static void dispatch(XposedModule module, Context context, String pkg, String process, long version, ClassLoader loader) {
        throw new IllegalStateException("probe unavailable in release");
    }
}
