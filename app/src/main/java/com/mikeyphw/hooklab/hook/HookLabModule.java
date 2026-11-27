package com.mikeyphw.hooklab.hook;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;
import com.mikeyphw.hooklab.core.HookContext;
import com.mikeyphw.hooklab.core.HostFlagSnapshot;
import com.mikeyphw.hooklab.runtime.BuiltinRegistry;
import com.mikeyphw.hooklab.runtime.BuiltinRegistry.RegistryGeneration;
import com.mikeyphw.hooklab.core.TweakCatalog;
import com.mikeyphw.hooklab.core.TweakEngine;
import com.mikeyphw.hooklab.core.TweakKeys;
import com.mikeyphw.hooklab.core.TweakDiagnostics;
import com.mikeyphw.hooklab.core.VerboseLogs;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** API 102: guarded host dispatcher with per-tweak quarantine and advisory diagnostics. */
public final class HookLabModule extends XposedModule {
    private String process;
    private final AtomicBoolean bootstrapInstalled = new AtomicBoolean();
    private final AtomicBoolean dispatched = new AtomicBoolean();
    private final Set<String> failedInProcess = ConcurrentHashMap.newKeySet();

    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        process = param.getProcessName();
        if (param.isSystemServer()) {
            // Nothing registered, so do not retain package callbacks in unrelated processes.
            detach();
        }
    }

    @Override public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        String pkg = param.getPackageName();
        if (!param.isFirstPackage() || process == null || !pkg.equals(process)) return;
        try { if (!TweakCatalog.knowsPackage(pkg) && !ProbeBridge.supportsHost(pkg)) return; }
        catch (RuntimeException | LinkageError ex) {
            log(Log.WARN,"HookLab","Registry unavailable; host refused"); return;
        }
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
                        } catch (Throwable ex) {
                            TweakDiagnostics.rethrowFatal(ex);
                            log(Log.ERROR, "HookLab", "Host bootstrap failed closed category=" + TweakDiagnostics.failureCategory(ex));
                        }
                    }
                    return result;
                });
        } catch (Throwable ex) {
            TweakDiagnostics.rethrowFatal(ex);
            log(Log.ERROR, "HookLab", "Unable to register safe host bootstrap category=" + TweakDiagnostics.failureCategory(ex));
        }
    }

    private void dispatchForHost(Context ctx, String pkg, ClassLoader loader) {
        if (!pkg.equals(ctx.getPackageName())) return;
        final long version;
        try {
            version = ctx.getPackageManager().getPackageInfo(pkg, 0).getLongVersionCode();
        } catch (Throwable ex) {
            TweakDiagnostics.rethrowFatal(ex);
            log(Log.WARN, "HookLab", "Unverified host version; skipping tweaks category=" + TweakDiagnostics.failureCategory(ex));
            return;
        }
        if (version < 1) return;
        if (ProbeBridge.supportsHost(pkg)) {
            ProbeBridge.dispatch(this,ctx,pkg,process,version,loader); return;
        }
        try {
            if (getApiVersion() < API_102 || (getFrameworkProperties() & PROP_CAP_REMOTE) == 0 ||
                    !RegistryGeneration.matches(BuiltinRegistry.current().generation,
                            () -> getRemotePreferences(RegistryGeneration.GROUP).getString(RegistryGeneration.KEY,null))) {
                log(Log.WARN,"HookLab","Missing or stale registry generation; restart manager then host"); return;
            }
        } catch (RuntimeException | LinkageError ex) {
            log(Log.WARN,"HookLab","Registry transport unavailable; host refused"); return;
        }
        final SharedPreferences settings;
        try {
            settings = getRemotePreferences(TweakKeys.GROUP);
        } catch (Throwable ex) {
            TweakDiagnostics.rethrowFatal(ex);
            log(Log.WARN, "HookLab", "Framework preferences unavailable; skipping tweaks category=" + TweakDiagnostics.failureCategory(ex));
            return;
        }
        final SharedPreferences safety;
        try {
            safety = getRemotePreferences(TweakDiagnostics.GROUP); // read-only in the host process
        } catch (Throwable ex) {
            TweakDiagnostics.rethrowFatal(ex);
            log(Log.WARN, "HookLab", "Framework safety-lock state unavailable; skipping tweaks category=" + TweakDiagnostics.failureCategory(ex));
            return;
        }
        // Bind only after verified host identity and both framework preference groups.
        VerboseLogs.bind(settings, this);
        VerboseLogs.emit(VerboseLogs.Event.HOST_DISPATCH_READY);
        com.mikeyphw.hooklab.sdk.ModuleConfig.Filter config=com.mikeyphw.hooklab.sdk.ModuleConfig.defaultsFilter();boolean configValid=true;
        if(com.mikeyphw.hooklab.sdk.ModuleConfig.TARGET.equals(pkg))try{
            config=com.mikeyphw.hooklab.sdk.ModuleConfig.read(getRemotePreferences(com.mikeyphw.hooklab.sdk.ModuleConfig.GROUP).getAll()).current.effective(com.mikeyphw.hooklab.sdk.ModuleConfig.MODULE,com.mikeyphw.hooklab.sdk.ModuleConfig.HOOK,pkg);
        }catch(RuntimeException|LinkageError failure){configValid=false;log(Log.WARN,"HookLab","MODULE_CONFIG_REFUSED id=wordmaster.night.filter");}
        final boolean configReady=configValid;
        HookContext context = new HookContext(pkg, process, version, loader, this, ctx,
                event -> log(Log.INFO,"HookLab","SDK event="+event.name()),config);
        final java.util.List<TweakEngine.Entry<HookContext>> entries=TweakCatalog.entries();
        final PackRuntime packs=new PackRuntime(this,ctx,pkg,process,version,loader,settings,safety,failedInProcess,config);
        final java.util.List<TweakEngine.Entry<HookContext>> resolved=new java.util.ArrayList<>();
        for(TweakEngine.Entry<HookContext> entry:entries){
            if(packs.usesPack(entry.spec.id))resolved.add(new TweakEngine.Entry<>(entry.spec,entry.title,entry.description,ignored->packs.install(entry.spec.id)));
            else resolved.add(entry);
        }
        final HostFlagSnapshot snapshot=HostFlagSnapshot.capture(entries,pkg,process,version,
                id -> settings.getBoolean(TweakKeys.enabled(id), false),
                id -> safety.getBoolean(TweakDiagnostics.quarantineKey(id), false));
        if (!RegistryGeneration.matches(BuiltinRegistry.current().generation,
                () -> getRemotePreferences(RegistryGeneration.GROUP).getString(RegistryGeneration.KEY,null))) return;
        TweakEngine.dispatch(resolved, pkg, process, version,
                id -> packs.eligible(id) && snapshot.enabled(id) && (configReady||!com.mikeyphw.hooklab.sdk.ModuleConfig.HOOK.equals(id)), context,
                (id, failure) -> log(Log.ERROR, "HookLab",
                        "Tweak failure id=" + id + " category=" + TweakDiagnostics.failureCategory(failure)),
                new TweakEngine.FaultPolicy() {
                    @Override public boolean quarantined(String id) {
                        return failedInProcess.contains(id)
                                || snapshot.locked(id);
                    }
                    @Override public void quarantine(String id) {
                        // Host remote preferences cannot be written; latch in this process only.
                        failedInProcess.add(id);
                    }
                },
                (id, outcome, category) -> {
                    log(Log.INFO, "HookLab", "Tweak outcome id=" + id +
                        " status=" + outcome.name() + " category=" + category);
                    VerboseLogs.emit(VerboseLogs.Event.TWEAK_DISPATCH_OUTCOME);
                });
    }
}
