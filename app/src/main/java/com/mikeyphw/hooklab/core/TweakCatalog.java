package com.mikeyphw.hooklab.core;

import com.mikeyphw.hooklab.mcc.MccDiamondUi;
import com.mikeyphw.hooklab.mcc.MccIds;
import com.mikeyphw.hooklab.obtainx.ObtainXIds;
import com.mikeyphw.hooklab.obtainx.NeoStoreHook;
import com.mikeyphw.hooklab.obtainx.DroidifyHook;
import com.mikeyphw.hooklab.wordmaster.WordMasterNightHook;
import com.mikeyphw.hooklab.chatgpt.ChatGptCodeProbe;
import com.mikeyphw.hooklab.chatgpt.ChatGptTermuxHandoff;
import com.mikeyphw.hooklab.runtime.BuiltinRegistry;
import com.mikeyphw.hooklab.sdk.HookSdk.HookDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/** Compatibility facade over the one immutable metadata generation.
 * Manager discovery never resolves an executable adapter. */
public final class TweakCatalog {
    private TweakCatalog() { }
    private static final class Holder {
        static final List<TweakEngine.Entry<HookContext>> ENTRIES = create(BuiltinRegistry.current());
    }
    private static List<TweakEngine.Entry<HookContext>> create(BuiltinRegistry registry) {
        Set<String> expected = new HashSet<>(Arrays.asList(MccIds.DIAMOND_MANUAL,
                ObtainXIds.NEO_TWEAK, ObtainXIds.DROIDIFY_TWEAK, WordMasterNightHook.TWEAK_ID,
                ChatGptCodeProbe.TWEAK_ID, ChatGptTermuxHandoff.TWEAK_ID));
        List<TweakEngine.Entry<HookContext>> entries = new ArrayList<>();
        for (HookDescriptor hook : registry.hooks()) {
            if (!expected.remove(hook.id) || !adapterCompatible(hook)) throw new IllegalStateException("unreviewed built-in adapter");
            entries.add(new TweakEngine.Entry<>(new TweakSpec(hook.id,hook.packageName,hook.minVersion,hook.maxVersion),
                    hook.title,hook.description, context -> install(hook,context)));
        }
        if (!expected.isEmpty()) throw new IllegalStateException("missing built-in adapter");
        return TweakEngine.immutableCatalog(entries);
    }
    /** Resolve method references only after the engine has verified eligibility and opt-in. */
    private static void install(HookDescriptor hook, HookContext context) throws Exception {
        if (!hook.eligible(context.packageName, context.processName, context.versionCode, true, true, true))
            throw new IllegalStateException("adapter context mismatch");
        com.mikeyphw.hooklab.sdk.HookSdk.Entrypoint adapter = sdkContext -> {
            if (!sdkContext.settings.enabled(hook.id) || !hook.eligible(sdkContext.packageName,sdkContext.processName,sdkContext.version,true,true,true))
                throw new IllegalStateException("SDK context refused");
            sdkContext.diagnostics.record(com.mikeyphw.hooklab.sdk.HookSdk.Event.INSTALL_STARTED);
            resolve(hook.id).install(context);
            sdkContext.diagnostics.record(com.mikeyphw.hooklab.sdk.HookSdk.Event.INSTALL_COMPLETED);
        };
        adapter.install(context.sdk(hook.id));
    }
    /** Existing compiled integration bounds are also checked against manifest metadata. */
    private static boolean adapterCompatible(HookDescriptor hook) {
        switch (hook.id) {
            case MccIds.DIAMOND_MANUAL: return bounds(hook,MccIds.PACKAGE,1L,Long.MAX_VALUE);
            case ObtainXIds.NEO_TWEAK: return bounds(hook,ObtainXIds.NEO,ObtainXIds.NEO_CODE, ObtainXIds.NEO_CODE);
            case ObtainXIds.DROIDIFY_TWEAK: return bounds(hook,ObtainXIds.DROIDIFY,ObtainXIds.DROIDIFY_CODE, ObtainXIds.DROIDIFY_CODE);
            case WordMasterNightHook.TWEAK_ID: return bounds(hook,WordMasterNightHook.PACKAGE,WordMasterNightHook.VERSION_CODE, WordMasterNightHook.VERSION_CODE);
            case ChatGptCodeProbe.TWEAK_ID:
            case ChatGptTermuxHandoff.TWEAK_ID: return bounds(hook,ChatGptCodeProbe.PACKAGE,ChatGptCodeProbe.VERSION_CODE, ChatGptCodeProbe.VERSION_CODE);
            default: return false;
        }
    }
    private static boolean bounds(HookDescriptor hook,String pkg,long min,long max) {
        return hook.packageName.equals(pkg) && hook.minVersion == min && hook.maxVersion == max;
    }
    private static TweakEngine.Installer<HookContext> resolve(String id) {
        switch (id) {
            case MccIds.DIAMOND_MANUAL: return MccDiamondUi::install;
            case ObtainXIds.NEO_TWEAK: return NeoStoreHook::install;
            case ObtainXIds.DROIDIFY_TWEAK: return DroidifyHook::install;
            case WordMasterNightHook.TWEAK_ID: return WordMasterNightHook::install;
            case ChatGptCodeProbe.TWEAK_ID: return ChatGptCodeProbe::install;
            case ChatGptTermuxHandoff.TWEAK_ID: return ChatGptTermuxHandoff::install;
            default: throw new IllegalArgumentException("unknown built-in adapter");
        }
    }
    public static List<TweakEngine.Entry<HookContext>> entries() { return Holder.ENTRIES; }
    public static List<TweakSpec> all() {
        List<TweakSpec> result = new ArrayList<>();
        for (TweakEngine.Entry<HookContext> entry : entries()) result.add(entry.spec);
        return Collections.unmodifiableList(result);
    }
    public static boolean knowsPackage(String packageName) { return BuiltinRegistry.current().knowsPackage(packageName); }
}
