package com.mikeyphw.hooklab.core;

import java.util.Objects;

/** Values are only constructed after the framework has verified the host package version. */
public final class HookContext {
    public final String packageName;
    public final String processName;
    public final long versionCode;
    public final ClassLoader classLoader;
    private final Object framework;
    private final Object applicationContext;
    private final com.mikeyphw.hooklab.sdk.HookSdk.Diagnostics diagnostics;
    private final com.mikeyphw.hooklab.sdk.ModuleConfig.Filter filter;

    public HookContext(String pkg, String process, long version, ClassLoader loader,
                       Object framework, Object applicationContext, com.mikeyphw.hooklab.sdk.HookSdk.Diagnostics diagnostics,
                       com.mikeyphw.hooklab.sdk.ModuleConfig.Filter filter) {
        this.filter=Objects.requireNonNull(filter);
        this.packageName = Objects.requireNonNull(pkg);
        this.processName = Objects.requireNonNull(process);
        if (version < 1) throw new IllegalArgumentException("Unverified version");
        this.versionCode = version;
        this.classLoader = Objects.requireNonNull(loader);
        this.framework = Objects.requireNonNull(framework);
        this.applicationContext = Objects.requireNonNull(applicationContext);
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }
    /** Built-in adapters bridge to the stable SDK only after dispatch authorizes the hook. */
    public com.mikeyphw.hooklab.sdk.HookSdk.Context sdk(String id) {
        return new com.mikeyphw.hooklab.sdk.HookSdk.Context(packageName,processName,versionCode,
                com.mikeyphw.hooklab.runtime.BuiltinRegistry.current().generation,classLoader,
                new com.mikeyphw.hooklab.sdk.HookSdk.Settings(java.util.Collections.singletonMap(id,true),filter),
                diagnostics,(hook,marker) -> { throw new IllegalStateException("probe port unavailable in built-ins"); });
    }
    public com.mikeyphw.hooklab.sdk.ModuleConfig.Filter wordMasterFilter(){if(!com.mikeyphw.hooklab.sdk.ModuleConfig.TARGET.equals(packageName))throw new IllegalStateException("configuration scope refused");return filter;}
    public <T> T framework(Class<T> type) { return type.cast(framework); }
    public <T> T appContext(Class<T> type) { return type.cast(applicationContext); }
}
