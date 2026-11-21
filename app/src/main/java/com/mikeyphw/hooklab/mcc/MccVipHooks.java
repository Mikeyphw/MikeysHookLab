package com.mikeyphw.hooklab.mcc;

import android.util.Log;
import com.mikeyphw.hooklab.core.HookContext;
import com.mikeyphw.hooklab.core.TweakDiagnostics;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/** Scoped RevenueCat Flutter bridge from MCC-VIP-Lab, rewritten for libxposed API 102. */
public final class MccVipHooks {
    private MccVipHooks() { }

    public static void install(HookContext ctx) throws Exception {
        if (!MccIds.PACKAGE.equals(ctx.packageName)) throw new SecurityException("Scope mismatch");
        XposedInterface xposed = ctx.framework(XposedInterface.class);
        ClassLoader loader = ctx.classLoader;
        int installed = 0;
        // This is the confirmed DEX method from MCC-VIP-Lab/source_evidence.json.
        try {
            Class<?> mapper = Class.forName(
                "com.revenuecat.purchases.hybridcommon.mappers.CustomerInfoMapperKt", false, loader);
            for (Method method : mapper.getDeclaredMethods()) {
                if (!"map".equals(method.getName()) || method.getParameterCount() != 1 ||
                        !Map.class.isAssignableFrom(method.getReturnType())) continue;
                xposed.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .setId("hooklab.mcc.vip.map")
                    .intercept(chain -> {
                        Object raw = chain.proceed();
                        try {
                            Map<String,Object> updated = MccVipPayload.patch(raw);
                            return updated == null ? raw : updated;
                        } catch (Throwable failure) {
                            TweakDiagnostics.rethrowFatal(failure);
                            // No original map/host data or arbitrary stack trace in logs.
                            xposed.log(Log.WARN, "HookLabMCC", "VIP mapper patch skipped category=" +
                                    TweakDiagnostics.failureCategory(failure));
                            return raw;
                        }
                    });
                installed++;
            }
        } catch (ClassNotFoundException missing) {
            xposed.log(Log.INFO, "HookLabMCC", "VIP mapper not found in installed app");
        }

        // The Flutter MethodChannel result may bypass the mapper in some builds.
        try {
            Class<?> plugin = Class.forName("com.revenuecat.purchases_flutter.PurchasesFlutterPlugin", false, loader);
            Class<?> resultClass = Class.forName("io.flutter.plugin.common.MethodChannel$Result", false, loader);
            Method getInfo = plugin.getDeclaredMethod("getCustomerInfo", resultClass);
            xposed.hook(getInfo).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .setId("hooklab.mcc.vip.get_customer_info")
                .intercept(chain -> {
                    Object delegate = chain.getArg(0);
                    if (delegate == null || !resultClass.isInstance(delegate)) return chain.proceed();
                    Object wrapped;
                    try {
                        wrapped = Proxy.newProxyInstance(resultClass.getClassLoader(),
                                new Class<?>[]{resultClass}, (proxy, method, args) -> {
                            Object[] forwarded = args;
                            if ("success".equals(method.getName()) && args != null && args.length == 1) {
                                try {
                                    Map<String,Object> updated = MccVipPayload.patch(args[0]);
                                    if (updated != null) forwarded = new Object[]{updated};
                                } catch (Throwable failure) {
                                    TweakDiagnostics.rethrowFatal(failure);
                                    xposed.log(Log.WARN, "HookLabMCC", "VIP callback patch skipped category=" +
                                            TweakDiagnostics.failureCategory(failure));
                                }
                            }
                            try { return method.invoke(delegate, forwarded); }
                            catch (InvocationTargetException wrappedFailure) { throw wrappedFailure.getCause(); }
                        });
                    } catch (Throwable failure) {
                        TweakDiagnostics.rethrowFatal(failure);
                        return chain.proceed();
                    }
                    return chain.proceed(new Object[]{wrapped});
                });
            installed++;
        } catch (ClassNotFoundException | NoSuchMethodException missing) {
            xposed.log(Log.INFO, "HookLabMCC", "VIP Flutter bridge unavailable in installed app");
        }
        if (installed == 0) throw new IllegalStateException("MCC RevenueCat integration unavailable");
        xposed.log(Log.INFO, "HookLabMCC", "VIP bridge installed count=" + installed + " local_test_only=true");
    }
}
