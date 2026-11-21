package com.mikeyphw.hooklab.hook;
import io.github.libxposed.api.XposedModule;
/** M0 deliberately registers no hooks. Never initialize settings in a constructor. */
public final class HookLabModule extends XposedModule {
    // Future dispatch must filter exact package, process and verified version,
    // check framework-mediated opt-in and isolate each individual hook.
}
