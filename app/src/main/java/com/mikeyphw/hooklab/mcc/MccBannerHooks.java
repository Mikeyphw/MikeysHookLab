package com.mikeyphw.hooklab.mcc;

import android.view.View;
import com.mikeyphw.hooklab.core.HookContext;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/** Explicit opt-in adaptation of MCC-VIP-Lab's optional native Flutter banner concealment. */
public final class MccBannerHooks {
    private MccBannerHooks() { }
    public static void install(HookContext ctx) throws Exception {
        if (!MccIds.PACKAGE.equals(ctx.packageName)) throw new SecurityException("Scope mismatch");
        XposedInterface framework = ctx.framework(XposedInterface.class);
        Set<Class<?>> wrapped = new HashSet<>();
        int count = 0;
        for (String name : new String[]{"io.flutter.plugins.googlemobileads.FlutterBannerAd",
                "io.flutter.plugins.googlemobileads.FlutterAdManagerBannerAd"}) {
            try {
                Class<?> cls = Class.forName(name, false, ctx.classLoader);
                for (Method method : cls.getDeclaredMethods()) {
                    if (!"getPlatformView".equals(method.getName()) || method.getParameterCount() != 0) continue;
                    framework.hook(method)
                        .setId("hooklab.mcc.banner.platform")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object platformView = chain.proceed();
                            if (platformView == null) return null;
                            try {
                                Method getView = platformView.getClass().getMethod("getView");
                                Object view = getView.invoke(platformView);
                                if (view instanceof View) ((View)view).setVisibility(View.GONE);
                                synchronized (wrapped) {
                                    if (wrapped.add(platformView.getClass())) {
                                        framework.hook(getView)
                                            .setId("hooklab.mcc.banner.view")
                                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                                            .intercept(next -> {
                                                Object returned = next.proceed();
                                                if (returned instanceof View) ((View)returned).setVisibility(View.GONE);
                                                return returned;
                                            });
                                    }
                                }
                            } catch (ReflectiveOperationException | RuntimeException ignored) { }
                            return platformView;
                        });
                    count++;
                }
            } catch (ClassNotFoundException missing) { /* unsupported ad SDK or version */ }
        }
        if (count == 0) throw new IllegalStateException("MCC native banner classes unavailable");
    }
}
