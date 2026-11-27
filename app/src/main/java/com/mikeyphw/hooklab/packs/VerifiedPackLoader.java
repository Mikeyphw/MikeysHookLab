package com.mikeyphw.hooklab.packs;

import com.mikeyphw.hooklab.runtime.pack.HookPack;
import com.mikeyphw.hooklab.runtime.pack.PackPolicy;
import com.mikeyphw.hooklab.sdk.HookSdk;
import dalvik.system.InMemoryDexClassLoader;

/** Only an independently verified, catalog-admitted archive can enter ART. */
public final class VerifiedPackLoader {
    private VerifiedPackLoader() { }
    static ModuleUiProvider loadUi(HookPack.Verified pack,String reviewedDigest)throws ReflectiveOperationException {
        PackPolicy.requireUiAdmission(pack,reviewedDigest);
        InMemoryDexClassLoader dex=new InMemoryDexClassLoader(pack.directUiDex(),ModuleUiProvider.class.getClassLoader());
        Class<?> type=Class.forName(pack.uiEntrypoint(),false,dex);
        if(type.getClassLoader()!=dex||!ModuleUiProvider.class.isAssignableFrom(type))throw new IllegalStateException("UI provider class identity refused");
        return type.asSubclass(ModuleUiProvider.class).getDeclaredConstructor().newInstance();
    }
    public static Class<? extends HookSdk.Entrypoint> load(HookPack.Verified pack)throws ClassNotFoundException {
        PackPolicy.verifyMigration(pack);
        InMemoryDexClassLoader dex=new InMemoryDexClassLoader(pack.directDex(),HookSdk.class.getClassLoader());
        Class<?> type=Class.forName(pack.entrypoint,false,dex);
        if(type.getClassLoader()!=dex||!HookSdk.Entrypoint.class.isAssignableFrom(type))throw new IllegalStateException("pack class identity refused");
        return type.asSubclass(HookSdk.Entrypoint.class);
    }
}
