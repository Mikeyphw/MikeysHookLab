package com.mikeyphw.hooklab.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Review-controlled registrations shared by the UI and the runtime. */
public final class TweakCatalog {
    private TweakCatalog() {}

    // Keep this empty until the first app-specific feature is separately reviewed.
    // Every future entry must specify exact package, process policy, version bounds and installer.
    private static final List<TweakEngine.Entry<HookContext>> REGISTRY =
            TweakEngine.immutableCatalog(Collections.<TweakEngine.Entry<HookContext>>emptyList());

    public static List<TweakEngine.Entry<HookContext>> entries() { return REGISTRY; }
    public static List<TweakSpec> all() {
        List<TweakSpec> specs = new ArrayList<>();
        for (TweakEngine.Entry<HookContext> entry : REGISTRY) specs.add(entry.spec);
        return Collections.unmodifiableList(specs);
    }
    public static boolean knowsPackage(String pkg) {
        for (TweakSpec spec : all()) if (spec.packageName.equals(pkg)) return true;
        return false;
    }
}
