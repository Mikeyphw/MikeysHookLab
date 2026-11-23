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
    private ChatGptCodeProbe() {}

    private static Class<?> type(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name,false,loader);
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
                    try { MATCHER.observePair((String)chain.getArg(0), (String)chain.getArg(1)); }
                    catch(Throwable failure) {
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
                        CodeBlockProbeMatcher.Evidence evidence=MATCHER.compareCopiedText(text.toString());
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
                +"captured_content=NONE persistent_storage=NONE");
        } catch(Throwable failure) {
            ACTIVE.set(false);
            INSTALLED.set(false);
            TweakDiagnostics.rethrowFatal(failure);
            if (failure instanceof Exception) throw (Exception)failure;
            throw new IllegalStateException("CGT01 probe registration failed",failure);
        }
    }
}
