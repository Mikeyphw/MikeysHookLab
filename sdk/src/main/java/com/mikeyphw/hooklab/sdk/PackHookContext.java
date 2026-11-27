package com.mikeyphw.hooklab.sdk;

import java.util.Objects;

/** Adapter for the reviewed migration sources. No raw framework, manager storage or credential port. */
public final class PackHookContext {
    public final String packageName,processName;
    public final long versionCode;
    public final ClassLoader classLoader;
    private final PackHookApi api;
    private final Object host;
    PackHookContext(HookSdk.Context context,Object host,PackHookApi api){
        packageName=context.packageName;processName=context.processName;versionCode=context.version;classLoader=context.hostLoader;
        this.api=Objects.requireNonNull(api);this.host=Objects.requireNonNull(host);
    }
    public <T>T framework(Class<T> type){if(type!=PackHookApi.class)throw new IllegalArgumentException("unsupported pack API");return type.cast(api);}
    public <T>T appContext(Class<T> type){return type.cast(host);}
}
