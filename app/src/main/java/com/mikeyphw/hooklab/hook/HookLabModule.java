package com.mikeyphw.hooklab.hook;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;
import com.mikeyphw.hooklab.core.HookContext;
import com.mikeyphw.hooklab.core.TweakCatalog;
import com.mikeyphw.hooklab.core.TweakEngine;
import com.mikeyphw.hooklab.core.TweakKeys;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.util.concurrent.atomic.AtomicBoolean;

/** API 102: one guarded, late version-verified dispatcher; zero app hooks in M1-M3. */
public final class HookLabModule extends XposedModule {
    private String process;
    private final AtomicBoolean bootstrapInstalled = new AtomicBoolean();
    private final AtomicBoolean dispatched = new AtomicBoolean();

    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        process = param.getProcessName();
        if (param.isSystemServer() || TweakCatalog.entries().isEmpty()) {
            // Nothing registered, so do not retain package callbacks in unrelated processes.
            detach();
        }
    }

    @Override public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        String pkg = param.getPackageName();
        if (!param.isFirstPackage() || process == null || !pkg.equals(process)
                || !TweakCatalog.knowsPackage(pkg)) return;
        if (!bootstrapInstalled.compareAndSet(false, true)) return;
        try {
            // ApplicationInfo.longVersionCode is a hidden SDK field: obtain public
            // PackageInfo#getLongVersionCode only after a trusted Context is attached.
            hook(Application.class.getDeclaredMethod("attach", Context.class))
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("hooklab.bootstrap.attach")
                .intercept(chain -> {
                    Object result = chain.proceed(); // Always propagate host's original errors.
                    if (dispatched.compareAndSet(false, true)) {
                        try {
                            Context ctx = (Context) chain.getArg(0);
                            dispatchForHost(ctx, pkg, param.getDefaultClassLoader());
                        } catch (Exception ex) {
                            log(Log.ERROR, "HookLab", "Host bootstrap failed closed", ex);
                        }
                    }
                    return result;
                });
        } catch (ReflectiveOperationException | RuntimeException ex) {
            log(Log.ERROR, "HookLab", "Unable to register safe host bootstrap", ex);
        }
    }

    private void dispatchForHost(Context ctx, String pkg, ClassLoader loader) {
        if (!pkg.equals(ctx.getPackageName())) return;
        final long version;
        try {
            version = ctx.getPackageManager().getPackageInfo(pkg, 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException | RuntimeException ex) {
            log(Log.WARN, "HookLab", "Unverified host version; skipping tweaks", ex);
            return;
        }
        if (version < 1) return;
        final SharedPreferences settings;
        try {
            settings = getRemotePreferences(TweakKeys.GROUP);
        } catch (RuntimeException ex) {
            log(Log.WARN, "HookLab", "Framework preferences unavailable; skipping tweaks", ex);
            return;
        }
        HookContext context = new HookContext(pkg, process, version, loader, this, ctx);
        TweakEngine.dispatch(TweakCatalog.entries(), pkg, process, version,
                id -> settings.getBoolean(TweakKeys.enabled(id), false), context,
                (id, exception) -> log(Log.ERROR, "HookLab", "Tweak failed: " + id, exception));
    }
}
