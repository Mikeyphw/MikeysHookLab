package com.mikeyphw.hooklab.wordmaster;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.mikeyphw.hooklab.core.HookContext;
import com.mikeyphw.hooklab.core.TweakDiagnostics;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reversible, touch-transparent night filter for Word Master's Unity viewport.
 * This is not a Unity shader/theme replacement. It does not recolor game assets,
 * intercept advertising, alter settings, or access saved progress.
 */
public final class WordMasterNightHook {
    public static final String PACKAGE = "com.fugo.wowsolitaire";
    public static final String VERSION_NAME = "0.6.10";
    public static final long VERSION_CODE = 56240403L;
    public static final String TWEAK_ID = "wordmaster.night.filter";
    private static final String ACTIVITY = "com.unity3d.player.UnityPlayerActivity";
    private static final String TAG = "hooklab.wordmaster.night.scrim.v1";
    // A moderate, neutral-navy overlay. Avoid strong opacity that hides gameplay.
    private static final int FILTER = 0x64010712;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private WordMasterNightHook() { }

    public static boolean matches(String pkg, long version, String versionName) {
        return PACKAGE.equals(pkg) && VERSION_CODE == version && VERSION_NAME.equals(versionName);
    }

    /** Must be called on the Activity UI thread. Idempotent after resume/recreation. */
    static void ensureShade(Activity activity) {
        if (!ACTIVITY.equals(activity.getClass().getName()) || activity.isFinishing()
                || activity.isDestroyed()) return;
        View root = activity.getWindow().getDecorView();
        if (!(root instanceof ViewGroup)) return;
        ViewGroup decor = (ViewGroup) root;
        View prior = decor.findViewWithTag(TAG);
        if (prior != null) {
            prior.bringToFront();
            return;
        }
        View shade = new View(activity);
        shade.setTag(TAG);
        shade.setBackgroundColor(FILTER);
        shade.setClickable(false);
        shade.setLongClickable(false);
        shade.setFocusable(false);
        shade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (decor instanceof FrameLayout) {
            decor.addView(shade, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            decor.addView(shade, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        shade.bringToFront();
    }

    public static void install(HookContext context) throws Exception {
        if (!PACKAGE.equals(context.packageName) || context.versionCode != VERSION_CODE
                || !PACKAGE.equals(context.processName)) {
            throw new SecurityException("Word Master version or process mismatch");
        }
        Context host = context.appContext(Context.class);
        PackageInfo actual = host.getPackageManager().getPackageInfo(PACKAGE, 0);
        if (!matches(context.packageName, actual.getLongVersionCode(), actual.versionName)) {
            throw new SecurityException("Unverified Word Master host build");
        }
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> target = Class.forName(ACTIVITY, false, context.classLoader);
            Method resume = target.getDeclaredMethod("onResume");
            XposedInterface xp = context.framework(XposedInterface.class);
            xp.hook(resume).setId("hooklab.wordmaster.night.resume")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed(); // Never mask host lifecycle errors.
                        Object current = chain.getThisObject();
                        if (current instanceof Activity) {
                            Activity activity = (Activity) current;
                            activity.runOnUiThread(() -> {
                                try { ensureShade(activity); }
                                catch (Throwable failure) {
                                    TweakDiagnostics.rethrowFatal(failure);
                                    xp.log(Log.WARN, "HookLab", "WORD_MASTER_NIGHT_FILTER_REFUSED category="
                                            + TweakDiagnostics.failureCategory(failure));
                                }
                            });
                        }
                        return result;
                    });
        } catch (Throwable failure) {
            INSTALLED.set(false);
            TweakDiagnostics.rethrowFatal(failure);
            if (failure instanceof Exception) throw (Exception) failure;
            throw new IllegalStateException("Word Master night filter registration failed", failure);
        }
    }
}
