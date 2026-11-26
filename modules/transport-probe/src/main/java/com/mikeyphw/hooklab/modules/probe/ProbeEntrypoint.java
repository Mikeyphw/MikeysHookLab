package com.mikeyphw.hooklab.modules.probe;

import com.mikeyphw.hooklab.sdk.HookSdk;

/** Independently compiled, self-authored, fixed benign marker. */
public final class ProbeEntrypoint implements HookSdk.Entrypoint {
    @Override public void install(HookSdk.Context context) {
        if (!"com.mikeyphw.hooklab.probetarget".equals(context.packageName) ||
                !context.packageName.equals(context.processName) || context.version!=1 ||
                !context.settings.enabled("hooklab.probe.marker"))
            throw new IllegalArgumentException("probe context refused");
        context.hooks.registerMarker("hooklab.probe.marker", original ->
                "BASE".equals(original) ? "MOD01_PROBE_OK" : original);
        context.diagnostics.record(HookSdk.Event.PROBE_READY);
    }
}
