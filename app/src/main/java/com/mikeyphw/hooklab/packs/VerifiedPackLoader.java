package com.mikeyphw.hooklab.packs;

import com.mikeyphw.hooklab.runtime.pack.HookPack;
import com.mikeyphw.hooklab.runtime.pack.PackPolicy;
import com.mikeyphw.hooklab.sdk.HookSdk;
import dalvik.system.InMemoryDexClassLoader;

/** Only an independently verified, catalog-admitted archive can enter ART. */
public final class VerifiedPackLoader {
    private VerifiedPackLoader() { }
    public static Class<? extends HookSdk.Entrypoint> load(HookPack.Verified pack)throws ClassNotFoundException {
        PackPolicy.verifyMigration(pack);
        InMemoryDexClassLoader dex=new InMemoryDexClassLoader(pack.directDex(),HookSdk.class.getClassLoader());
        Class<?> type=Class.forName(pack.entrypoint,false,dex);
        if(type.getClassLoader()!=dex||!HookSdk.Entrypoint.class.isAssignableFrom(type))throw new IllegalStateException("pack class identity refused");
        return type.asSubclass(HookSdk.Entrypoint.class);
    }
}
