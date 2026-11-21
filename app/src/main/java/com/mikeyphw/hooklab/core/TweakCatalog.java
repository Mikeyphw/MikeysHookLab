package com.mikeyphw.hooklab.core;

import com.mikeyphw.hooklab.mcc.MccDiamondUi;
import com.mikeyphw.hooklab.mcc.MccIds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Only one explicitly enabled MCC hook is shipped: the manual diamond button. */
public final class TweakCatalog {
    private TweakCatalog() { }

    // Keep the existing ID so users need not re-enable their manual button after an update.
    // Previously persisted VIP, banner and auto-refill flags have no registered installers.
    private static final List<TweakEngine.Entry<HookContext>> REGISTRY =
        TweakEngine.immutableCatalog(Collections.singletonList(
            new TweakEngine.Entry<>(
                new TweakSpec(MccIds.DIAMOND_MANUAL, MccIds.PACKAGE, 1L, Long.MAX_VALUE),
                "MCC · Manual diamond button",
                "Tap ♦ 9 inside MCC to open the manual control. Only an explicit second tap " +
                "requests root; no automatic refills, VIP spoofing or advertising hooks.",
                MccDiamondUi::install)
        ));

    public static List<TweakEngine.Entry<HookContext>> entries() { return REGISTRY; }
    public static List<TweakSpec> all() {
        List<TweakSpec> result = new ArrayList<>();
        for (TweakEngine.Entry<HookContext> entry : REGISTRY) result.add(entry.spec);
        return Collections.unmodifiableList(result);
    }
    public static boolean knowsPackage(String packageName) {
        for (TweakSpec spec : all()) if (spec.packageName.equals(packageName)) return true;
        return false;
    }
}
