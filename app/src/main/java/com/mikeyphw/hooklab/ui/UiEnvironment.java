package com.mikeyphw.hooklab.ui;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import com.mikeyphw.hooklab.HookLabApp;
import com.mikeyphw.hooklab.core.HookContext;
import com.mikeyphw.hooklab.core.TweakCatalog;
import com.mikeyphw.hooklab.core.TweakEngine;
import com.mikeyphw.hooklab.core.TweakSpec;
import com.mikeyphw.hooklab.mcc.MccIds;
import io.github.libxposed.service.XposedService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** UI-only boundary. Hook dispatch always uses the real, immutable TweakCatalog.
 * The release factory constructs only this implementation; it accepts no extras,
 * files, exported commands, reflection hooks, or test flags. */
class UiEnvironment {
    interface ScopeListener {
        void onScopeRequestApproved(List<String> approved);
        void onScopeRequestFailed(String reason);
    }
    interface Port {
        int getApiVersion();
        String getFrameworkName();
        String getFrameworkVersion();
        Collection<String> getScope();
        SharedPreferences getRemotePreferences(String group);
        void requestScope(List<String> packages, ScopeListener listener);
        void removeScope(List<String> packages);
    }
    final HookLabApp app;
    private XposedService lastBinder;
    private Port lastPort;
    UiEnvironment(HookLabApp app) { this.app = java.util.Objects.requireNonNull(app); }

    /** Identity-stable port, not a new wrapper on every read. No binder IPC here. */
    synchronized Port currentService() {
        XposedService bound = app.currentService();
        if (bound != lastBinder) {
            lastBinder = bound;
            lastPort = bound == null ? null : new BinderPort(bound);
        }
        return lastPort;
    }
    List<TweakEngine.Entry<HookContext>> entries() { return TweakCatalog.entries(); }
    List<TweakSpec> all() {
        List<TweakSpec> result = new ArrayList<>();
        for (TweakEngine.Entry<HookContext> entry : entries()) result.add(entry.spec);
        return Collections.unmodifiableList(result);
    }
    boolean knowsPackage(String pkg) {
        for (TweakSpec spec : all()) if (spec.packageName.equals(pkg)) return true;
        return false;
    }
    Long installedVersion(TweakSpec spec) {
        // Preserve the MCC special-case: its actual version is checked by its worker.
        if (MccIds.PACKAGE.equals(spec.packageName)) return null;
        try { return app.getPackageManager().getPackageInfo(spec.packageName, 0).getLongVersionCode(); }
        catch (PackageManager.NameNotFoundException | SecurityException ignored) { return null; }
    }
    long elapsedRealtime() { return SystemClock.elapsedRealtime(); }
    long wallTime() { return System.currentTimeMillis(); }

    private static final class BinderPort implements Port {
        private final XposedService binder;
        BinderPort(XposedService binder) { this.binder = binder; }
        @Override public int getApiVersion() { return binder.getApiVersion(); }
        @Override public String getFrameworkName() { return binder.getFrameworkName(); }
        @Override public String getFrameworkVersion() { return binder.getFrameworkVersion(); }
        @Override public Collection<String> getScope() { return binder.getScope(); }
        @Override public SharedPreferences getRemotePreferences(String group) { return binder.getRemotePreferences(group); }
        @Override public void requestScope(List<String> packages, ScopeListener listener) {
            binder.requestScope(packages, new XposedService.OnScopeEventListener() {
                @Override public void onScopeRequestApproved(List<String> approved) { listener.onScopeRequestApproved(approved); }
                @Override public void onScopeRequestFailed(String reason) { listener.onScopeRequestFailed(reason); }
            });
        }
        @Override public void removeScope(List<String> packages) { binder.removeScope(packages); }
    }
}
