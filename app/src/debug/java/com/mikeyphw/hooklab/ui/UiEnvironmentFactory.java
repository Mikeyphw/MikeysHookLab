package com.mikeyphw.hooklab.ui;

import android.content.pm.ApplicationInfo;
import android.os.Build;
import com.mikeyphw.hooklab.HookLabApp;

/** Process-local test seam, compiled only in debug. No externally callable Android component. */
final class UiEnvironmentFactory {
    private static UiEnvironment installed;
    private UiEnvironmentFactory() {}
    static synchronized UiEnvironment create(HookLabApp app) {
        if (installed == null) return new UiEnvironment(app);
        if (installed.app != app || app.currentService() != null)
            throw new IllegalStateException("Fixture cannot coexist with a real framework");
        return installed;
    }
    static synchronized AutoCloseable installInstrumentationFixture(UiEnvironment fixture) {
        HookLabApp app = fixture.app;
        if (installed != null || (app.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0
                || Build.VERSION.SDK_INT != 36 || !(Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish"))
                || app.currentService() != null)
            throw new IllegalStateException("Only an isolated debug API36 emulator fixture is allowed");
        installed = fixture;
        return () -> {
            synchronized (UiEnvironmentFactory.class) {
                if (installed != fixture) throw new IllegalStateException("Fixture ownership changed");
                installed = null;
            }
        };
    }
}
