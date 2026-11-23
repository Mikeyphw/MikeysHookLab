package com.mikeyphw.hooklab.core;

import com.mikeyphw.hooklab.mcc.MccDiamondUi;
import com.mikeyphw.hooklab.mcc.MccIds;
import com.mikeyphw.hooklab.obtainx.ObtainXIds;
import com.mikeyphw.hooklab.obtainx.NeoStoreHook;
import com.mikeyphw.hooklab.obtainx.DroidifyHook;
import com.mikeyphw.hooklab.wordmaster.WordMasterNightHook;
import com.mikeyphw.hooklab.chatgpt.ChatGptCodeProbe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Five independently opted-in, exact-host API102 integrations. */
public final class TweakCatalog {
    private TweakCatalog() { }

    // Keep the existing ID so users need not re-enable their manual button after an update.
    // Previously persisted VIP, banner and auto-refill flags have no registered installers.
    private static final List<TweakEngine.Entry<HookContext>> REGISTRY =
        TweakEngine.immutableCatalog(Arrays.<TweakEngine.Entry<HookContext>>asList(
            new TweakEngine.Entry<>(
                new TweakSpec(MccIds.DIAMOND_MANUAL, MccIds.PACKAGE, 1L, Long.MAX_VALUE),
                "MCC · Manual diamond button",
                "Tap ♦ 9 inside MCC to open manual control; confirmed root only in HookLab. " +
                "No VIP, banner or automatic refills.",
                MccDiamondUi::install),
            new TweakEngine.Entry<>(
                new TweakSpec(ObtainXIds.NEO_TWEAK, ObtainXIds.NEO,
                    ObtainXIds.NEO_CODE, ObtainXIds.NEO_CODE),
                "Neo Store · Open in ObtainX",
                "Neo Store 1.3.3/1304 only. Show ObtainX-branded header icon on a " +
                "verified app page; tap to open ObtainX import preview. No auto imports.",
                NeoStoreHook::install),
            new TweakEngine.Entry<>(
                new TweakSpec(ObtainXIds.DROIDIFY_TWEAK, ObtainXIds.DROIDIFY,
                    ObtainXIds.DROIDIFY_CODE, ObtainXIds.DROIDIFY_CODE),
                "Droid-ify · Add to ObtainX",
                "Droid-ify 0.7.8/780 only. Adds one native toolbar action after " +
                "validating the current app and repository. No host data writes.",
                DroidifyHook::install),
            new TweakEngine.Entry<>(
                new TweakSpec(WordMasterNightHook.TWEAK_ID, WordMasterNightHook.PACKAGE,
                    WordMasterNightHook.VERSION_CODE, WordMasterNightHook.VERSION_CODE),
                "Word Master · Night filter (experimental)",
                "Word Master 0.6.10 only. Dims the Unity gameplay view without " +
                "recoloring in-game textures. Fully opt-in; restart the game to apply or remove.",
                WordMasterNightHook::install),
            new TweakEngine.Entry<>(
                new TweakSpec(ChatGptCodeProbe.TWEAK_ID, ChatGptCodeProbe.PACKAGE,
                    ChatGptCodeProbe.VERSION_CODE, ChatGptCodeProbe.VERSION_CODE),
                "ChatGPT · Code-block action diagnostic (experimental)",
                "Read-only, opt-in diagnostics for ChatGPT 1.2026.314/2631431. " +
                "Correlates native callback identities and copied text with session-only HMAC; " +
                "no command execution, message storage, Termux dispatch or raw text logs.",
                ChatGptCodeProbe::install)
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
