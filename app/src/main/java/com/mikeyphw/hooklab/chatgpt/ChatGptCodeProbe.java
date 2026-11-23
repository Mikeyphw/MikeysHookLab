package com.mikeyphw.hooklab.chatgpt;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.SystemClock;
import android.util.Log;
import com.mikeyphw.hooklab.core.HookContext;
import com.mikeyphw.hooklab.core.TweakDiagnostics;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CGT01-P02: Read-only investigation of ChatGPT 2631431 code-block text parity.
 * Does not add actions, read unrelated message storage, launch Termux, or save source text.
 * An observed clipboard match proves equality of VALUES only; distinct block identity
 * and actual tap-to-handler causality require separate on-device evidence.
 */
public final class ChatGptCodeProbe {
    public static final String PACKAGE="com.openai.chatgpt";
    public static final String VERSION_NAME="1.2026.314";
    public static final long VERSION_CODE=2631431L;
    public static final String TWEAK_ID="chatgpt.codeblock.copy.probe";
    public static final String EXPECTED_RENDER_OWNER="p4l";
    private static final String LOG_TAG="HookLabCGT01";
    private static final AtomicBoolean INSTALLED=new AtomicBoolean();
    private static final AtomicBoolean ACTIVE=new AtomicBoolean();
    private static final CodeBlockProbeMatcher MATCHER =
            new CodeBlockProbeMatcher(SystemClock::elapsedRealtime);
    private static final AtomicLong LAST_UNMATCHED=new AtomicLong();
    // P03 observes callback OBJECT identity; never substitute equal text for block identity.
    private static final CodeBlockActionMatcher ACTION =
            new CodeBlockActionMatcher(SystemClock::elapsedRealtime);
    private static final Set<Class<?>> CALLBACK_CLASSES = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger CALLBACK_CLASS_COUNT = new AtomicInteger();
    private static final int MAX_ACTION_CLASSES = 24;
    private ChatGptCodeProbe() {}

    private static Class<?> type(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name,false,loader);
    }

    /**
     * Hook only a zero-argument Function0 class actually passed into the exact
     * CodeBlockImpl render call. No generic Function0 or arbitrary clipboard hook.
     * Role 10/14 is observed, NOT assumed to be the Copy button.
     */
    private static void bindAction(XposedInterface xp, ClassLoader loader,
                                   Object callback, int role, String source) {
        if (callback == null || source == null) return;
        try {
            Class<?> iface = Class.forName("kotlin.jvm.functions.Function0", false, loader);
            if (!iface.isInstance(callback)) return;
            if (!ACTION.bind(callback, role, source)) return;
            Class<?> owner = callback.getClass();
            if (CALLBACK_CLASSES.contains(owner)) return;
            if (CALLBACK_CLASS_COUNT.get() >= MAX_ACTION_CLASSES) return;
            Method invoke = owner.getDeclaredMethod("invoke");
            if (invoke.getReturnType() != Object.class) return;
            if (!CALLBACK_CLASSES.add(owner)) return;
            int count = CALLBACK_CLASS_COUNT.incrementAndGet();
            try {
                xp.hook(invoke)
                    .setId("hooklab.cgt01.action.invoke." + count)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        CodeBlockActionMatcher.Frame frame=ACTION.enter(chain.getThisObject());
                        if (frame==null) return chain.proceed();
                        try {
                            xp.log(Log.INFO,LOG_TAG,"CGT01_ACTION_CALLBACK_INVOKED role="
                                    +frame.role()+" binding_id="+frame.bindingId()
                                    +" button_identity=UNVERIFIED");
                            return chain.proceed();
                        } finally {
                            ACTION.exit(frame);
                        }
                    });
            } catch(Throwable failure) {
                CALLBACK_CLASSES.remove(owner);
                CALLBACK_CLASS_COUNT.decrementAndGet();
                TweakDiagnostics.rethrowFatal(failure);
                xp.log(Log.WARN,LOG_TAG,"CGT01_ACTION_HOOK_FAILED role=" + role
                        +" category="+TweakDiagnostics.failureCategory(failure));
            }
        } catch(Throwable failure) {
            TweakDiagnostics.rethrowFatal(failure);
            xp.log(Log.WARN,LOG_TAG,"CGT01_ACTION_BIND_FAILED role="+role
                    +" category="+TweakDiagnostics.failureCategory(failure));
        }
    }

    public static void install(HookContext context) throws Exception {
        if (!PACKAGE.equals(context.packageName) || !PACKAGE.equals(context.processName)
                || context.versionCode!=VERSION_CODE) {
            throw new SecurityException("ChatGPT process/build mismatch");
        }
        Context host=context.appContext(Context.class);
        PackageInfo installed=host.getPackageManager().getPackageInfo(PACKAGE,0);
        if (installed.getLongVersionCode()!=VERSION_CODE || !VERSION_NAME.equals(installed.versionName)) {
            throw new SecurityException("ChatGPT versionName/versionCode mismatch");
        }
        if (!INSTALLED.compareAndSet(false,true)) return;
        try {
            // Exact DEX descriptor from original ChatGPT 1.2026.314 (2631431), classes7.dex.
            ClassLoader loader=context.classLoader;
            Method render=type(loader,EXPECTED_RENDER_OWNER).getDeclaredMethod("a",
                String.class, String.class, type(loader,"kki0"), type(loader,"yuj0"),
                type(loader,"yuj0"), float.class, boolean.class, boolean.class,
                boolean.class, boolean.class,
                type(loader,"kotlin.jvm.functions.Function0"),
                type(loader,"kotlin.jvm.functions.Function2"),
                boolean.class, boolean.class, type(loader,"kotlin.jvm.functions.Function0"),
                boolean.class, type(loader,"bz11"), type(loader,"qvq"), int.class, int.class);
            if (!Modifier.isStatic(render.getModifiers()) || render.getReturnType()!=void.class)
                throw new NoSuchMethodException("CodeBlockImpl signature mismatch");
            Method clipboard=ClipboardManager.class.getDeclaredMethod("setPrimaryClip",ClipData.class);
            XposedInterface xp=context.framework(XposedInterface.class);
            xp.hook(render).setId("hooklab.cgt01.codeblock.render.2631431")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result=chain.proceed(); // Host drawing always happens first.
                    if (!ACTIVE.get()) return result;
                    try {
                        String source=(String)chain.getArg(1);
                        MATCHER.observePair((String)chain.getArg(0), source);
                        // Both candidate click callbacks are bound separately. Their roles
                        // remain hypothetical until real device tap-to-Copy evidence.
                        bindAction(xp,loader,chain.getArg(10),10,source);
                        bindAction(xp,loader,chain.getArg(14),14,source);
                    } catch(Throwable failure) {
                        TweakDiagnostics.rethrowFatal(failure);
                        xp.log(Log.WARN,LOG_TAG,"CGT01_RENDER_OBSERVATION_FAILED category="
                                +TweakDiagnostics.failureCategory(failure));
                    }
                    return result;
                });
            xp.hook(clipboard).setId("hooklab.cgt01.clipboard.write")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result=chain.proceed(); // Never change clipboard contents/host errors.
                    if (!ACTIVE.get()) return result;
                    try {
                        ClipData clip=(ClipData)chain.getArg(0);
                        if (clip==null || clip.getItemCount()!=1 || clip.getItemAt(0)==null) return result;
                        CharSequence text=clip.getItemAt(0).getText();
                        if (text==null) return result;
                        String copied=text.toString();
                        CodeBlockActionMatcher.Evidence causal=ACTION.matchClipboardText(copied);
                        if (causal.outcome==CodeBlockActionMatcher.Outcome.BOUND_VALUE_MATCH) {
                            xp.log(Log.INFO,LOG_TAG,"CGT01_ACTION_COPY_BOUND_VALUE_MATCH role="
                                +causal.role+" binding_id="+causal.bindingId
                                +" bytes="+causal.bytes+" lf="+causal.lineFeeds
                                +" cr="+causal.carriageReturns
                                +" trailing_lf="+causal.trailingLineFeeds
                                +" ephemeral_token="+causal.keyedToken
                                +" callback_identity=OBSERVED visual_block_identity=UNVERIFIED");
                        } else if (causal.outcome==CodeBlockActionMatcher.Outcome.SHARED_CALLBACK_AMBIGUOUS) {
                            xp.log(Log.INFO,LOG_TAG,"CGT01_ACTION_SHARED_CALLBACK_AMBIGUOUS role="
                                +causal.role+" block_identity=UNVERIFIED");
                        } else if (causal.outcome==CodeBlockActionMatcher.Outcome.BOUND_VALUE_MISMATCH) {
                            xp.log(Log.INFO,LOG_TAG,"CGT01_ACTION_COPY_VALUE_MISMATCH role="
                                +causal.role+" binding_id="+causal.bindingId);
                        }
                        CodeBlockProbeMatcher.Evidence evidence=MATCHER.compareCopiedText(copied);
                        if (evidence.result==CodeBlockProbeMatcher.Result.MATCH_VALUE_ONLY) {
                            xp.log(Log.INFO,LOG_TAG,"CGT01_COPY_MATCH_VALUE_ONLY slot="
                                +evidence.parameterSlot+" bytes="+evidence.utf8Length
                                +" lf="+evidence.lineFeeds+" cr="+evidence.carriageReturns
                                +" trailing_lf="+evidence.trailingNewlines
                                +" ephemeral_token="+evidence.ephemeralToken
                                +" identity=UNVERIFIED action_causality=UNVERIFIED");
                        } else if (evidence.result==CodeBlockProbeMatcher.Result.AMBIGUOUS_SLOTS) {
                            xp.log(Log.INFO,LOG_TAG,"CGT01_COPY_AMBIGUOUS_SLOTS identity=UNVERIFIED");
                        } else if(evidence.result==CodeBlockProbeMatcher.Result.UNMATCHED) {
                            long now=SystemClock.elapsedRealtime();
                            long previous=LAST_UNMATCHED.get();
                            if(now-previous>=15000L && LAST_UNMATCHED.compareAndSet(previous,now)) {
                                xp.log(Log.INFO,LOG_TAG,"CGT01_COPY_NO_RENDER_CANDIDATE path=UNRESOLVED");
                            }
                        }
                    } catch(Throwable failure) {
                        TweakDiagnostics.rethrowFatal(failure);
                        xp.log(Log.WARN,LOG_TAG,"CGT01_COPY_OBSERVATION_FAILED category="
                                +TweakDiagnostics.failureCategory(failure));
                    }
                    return result;
                });
            ACTIVE.set(true);
            xp.log(Log.INFO,LOG_TAG,"CGT01_PROBE_INSTALLED build=2631431 mode=READ_ONLY "
                +"p03_action_hooks=DISCOVER_ON_RENDER captured_content=NONE persistent_storage=NONE");
        } catch(Throwable failure) {
            ACTIVE.set(false);
            INSTALLED.set(false);
            TweakDiagnostics.rethrowFatal(failure);
            if (failure instanceof Exception) throw (Exception)failure;
            throw new IllegalStateException("CGT01 probe registration failed",failure);
        }
    }
}
