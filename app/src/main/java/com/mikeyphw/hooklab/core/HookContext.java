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

    public HookContext(String pkg, String process, long version, ClassLoader loader,
                       Object framework, Object applicationContext) {
        this.packageName = Objects.requireNonNull(pkg);
        this.processName = Objects.requireNonNull(process);
        if (version < 1) throw new IllegalArgumentException("Unverified version");
        this.versionCode = version;
        this.classLoader = Objects.requireNonNull(loader);
        this.framework = Objects.requireNonNull(framework);
        this.applicationContext = Objects.requireNonNull(applicationContext);
    }
    public <T> T framework(Class<T> type) { return type.cast(framework); }
    public <T> T appContext(Class<T> type) { return type.cast(applicationContext); }
}
