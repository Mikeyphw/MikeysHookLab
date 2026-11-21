package com.mikeyphw.hooklab.core;

import com.mikeyphw.hooklab.mcc.MccIds;
import com.mikeyphw.hooklab.mcc.MccVipHooks;
import com.mikeyphw.hooklab.mcc.MccDiamondUi;
import com.mikeyphw.hooklab.mcc.MccAutoWatcher;
import com.mikeyphw.hooklab.mcc.MccBannerHooks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Review-controlled registrations shared by UI and runtime. All start disabled. */
public final class TweakCatalog {
    private TweakCatalog() { }
    // The P10 source tracked versionName 20.2609.3009 only, not a versionCode.
    // Runtime gates stay broad for legacy parity; each UI entry explicitly warns that
    // newer versions have not been confirmed. Never describe this as tested support.
    private static final List<TweakEngine.Entry<HookContext>> REGISTRY =
        TweakEngine.immutableCatalog(Arrays.<TweakEngine.Entry<HookContext>>asList(
            new TweakEngine.Entry<>(
                new TweakSpec(MccIds.VIP, MccIds.PACKAGE, 1L, Long.MAX_VALUE),
                "MCC · Local VIP CustomerInfo",
                "Synthetic RevenueCat CustomerInfo for local personal testing; not a purchase or verified receipt.",
                MccVipHooks::install),
            new TweakEngine.Entry<>(
                new TweakSpec(MccIds.DIAMOND_MANUAL, MccIds.PACKAGE, 1L, Long.MAX_VALUE),
                "MCC · Diamond control button",
                "Shows a compact ♦ 9 button near the Flutter diamond indicator. Manual root action restarts the app.",
                MccDiamondUi::install),
            new TweakEngine.Entry<>(
                new TweakSpec(MccIds.DIAMOND_AUTO, MccIds.PACKAGE, 1L, Long.MAX_VALUE),
                "MCC · Auto refill at zero",
                "Observes only a single account's persisted one-byte zero; verified module action writes 9 and restarts MCC. Requires root and Android 14+.",
                MccAutoWatcher::install),
            new TweakEngine.Entry<>(
                new TweakSpec(MccIds.BANNER_HIDE, MccIds.PACKAGE, 1L, Long.MAX_VALUE),
                "MCC · Hide native banner",
                "Hides only the targeted Flutter Google Mobile Ads native banner views if present.",
                MccBannerHooks::install)
        ));

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
