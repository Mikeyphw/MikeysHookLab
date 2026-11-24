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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CGT01-P05: Read-only CopyButton callback causality diagnostics for ChatGPT 2632128.
 * Does not add actions, read unrelated message storage, launch Termux, or save source text.
 * An observed clipboard match proves equality of VALUES only; distinct block identity
 * and actual tap-to-handler causality require separate on-device evidence.
 */
public final class ChatGptCodeProbe {
    public static final String PACKAGE="com.openai.chatgpt";
    public static final String VERSION_NAME="1.2026.321";
    public static final long VERSION_CODE=2632128L;
    public static final String TWEAK_ID="chatgpt.codeblock.copy.probe";
    public static final String EXPECTED_RENDER_OWNER="lxl";
    private static final String LOG_TAG="HookLabCGT01";
    private static final AtomicBoolean INSTALLED=new AtomicBoolean();
    private static final AtomicBoolean ACTIVE=new AtomicBoolean();
    private static final CodeBlockProbeMatcher MATCHER =
            new CodeBlockProbeMatcher(SystemClock::elapsedRealtime);
    private static final AtomicLong LAST_UNMATCHED=new AtomicLong();
    // P04: presence of the pinned CopyButtonImpl renderer is NOT evidence of a tap.
    private static final AtomicBoolean NATIVE_RENDER_SEEN = new AtomicBoolean();
    private static final AtomicBoolean COPY_BUTTON_RENDER_SEEN = new AtomicBoolean();
    // P03 observes callback OBJECT identity; never substitute equal text for block identity.
    private static final CodeBlockActionMatcher ACTION =
            new CodeBlockActionMatcher(SystemClock::elapsedRealtime);
    // P05 tracks only instances of a pinned callback class constructed synchronously
    // inside CopyButtonImpl. It never retains source text or grants execute authority.
    private static final CopyButtonCausalityTracker COPY_CAUSALITY =
            new CopyButtonCausalityTracker(SystemClock::elapsedRealtime);

    private static final Set<Class<?>> CALLBACK_CLASSES = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger CALLBACK_CLASS_COUNT = new AtomicInteger();
    private static final int MAX_ACTION_CLASSES = 24;
    private ChatGptCodeProbe() {}

    private static void p05(XposedInterface xp,CopyButtonCausalityTracker.Event event,
                            int role,String fixedDetails) {
        int count=COPY_CAUSALITY.logCount(event,role);
        if(count!=0)xp.log(Log.INFO,LOG_TAG,"CGT01_P05_"+event.name()
                +" role="+role+" occurrence="+count+" "+fixedDetails);
    }

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
        if (callback == null) {
            p05(xp,CopyButtonCausalityTracker.Event.P03_NULL_CALLBACK,role,"binding=NOT_CREATED");
            return;
        }
        if (source == null) {
            p05(xp,CopyButtonCausalityTracker.Event.P03_NULL_SOURCE,role,"binding=NOT_CREATED");
            return;
        }
        try {
            Class<?> iface=Class.forName("kotlin.jvm.functions.Function0",false,loader);
            if(!iface.isInstance(callback)) {
                p05(xp,CopyButtonCausalityTracker.Event.P03_NOT_FUNCTION0,role,"binding=REJECTED");
                return;
            }
            if(!ACTION.bind(callback,role,source)) {
                p05(xp,CopyButtonCausalityTracker.Event.P03_BIND_AMBIGUOUS,role,"binding=REJECTED");
                return;
            }
            Class<?> owner=callback.getClass();
            if(CALLBACK_CLASSES.contains(owner)) {
                p05(xp,CopyButtonCausalityTracker.Event.P03_REUSED_CLASS,role,"hook=EXISTING");
                return;
            }
            if(CALLBACK_CLASS_COUNT.get()>=MAX_ACTION_CLASSES) {
                p05(xp,CopyButtonCausalityTracker.Event.P03_CAP_REACHED,role,"hook=REJECTED");
                return;
            }
            final Method invoke;
            try {invoke=owner.getDeclaredMethod("invoke");}
            catch(NoSuchMethodException unavailable) {
                p05(xp,CopyButtonCausalityTracker.Event.P03_INVOKE_MISSING,role,"hook=REJECTED");
                return;
            }
            if(invoke.getReturnType()!=Object.class) {
                p05(xp,CopyButtonCausalityTracker.Event.P03_INVOKE_RETURN_MISMATCH,
                    role,"hook=REJECTED");
                return;
            }
            if(!CALLBACK_CLASSES.add(owner)) {
                p05(xp,CopyButtonCausalityTracker.Event.P03_REUSED_CLASS,role,"hook=EXISTING");
                return;
            }
            int count=CALLBACK_CLASS_COUNT.incrementAndGet();
            try {
                xp.hook(invoke).setId("hooklab.cgt01.action.invoke."+count)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        CodeBlockActionMatcher.Frame frame=ACTION.enter(chain.getThisObject());
                        if(frame==null) {
                            p05(xp,CopyButtonCausalityTracker.Event.P03_FRAME_MISSING,role,
                                "invocation=NOT_BOUND");
                            return chain.proceed();
                        }
                        try {
                            p05(xp,CopyButtonCausalityTracker.Event.P03_CALLBACK_INVOKED,frame.role(),
                                "binding_id="+frame.bindingId()+" button_identity=UNVERIFIED");
                            if(COPY_CAUSALITY.occurrenceCount(
                                    CopyButtonCausalityTracker.Event.P03_CALLBACK_INVOKED,
                                    frame.role())<=4)
                                xp.log(Log.INFO,LOG_TAG,"CGT01_ACTION_CALLBACK_INVOKED role="
                                        +frame.role()+" binding_id="+frame.bindingId()
                                        +" button_identity=UNVERIFIED");
                            return chain.proceed();
                        } finally { ACTION.exit(frame); }
                    });
                p05(xp,CopyButtonCausalityTracker.Event.P03_HOOK_INSTALLED,role,
                    "class_hook=INSTALLED ordinal="+count);
            } catch(Throwable failure) {
                CALLBACK_CLASSES.remove(owner);
                CALLBACK_CLASS_COUNT.decrementAndGet();
                TweakDiagnostics.rethrowFatal(failure);
                p05(xp,CopyButtonCausalityTracker.Event.P03_HOOK_FAILED,role,
                    "category="+TweakDiagnostics.failureCategory(failure));
            }
        } catch(Throwable failure) {
            TweakDiagnostics.rethrowFatal(failure);
            p05(xp,CopyButtonCausalityTracker.Event.P03_BIND_FAILED,role,
                "category="+TweakDiagnostics.failureCategory(failure));
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
            // Exact method descriptors and static flags from ChatGPT 1.2026.321 (2632128), classes4.dex.
            ClassLoader loader=context.classLoader;
            Method render=type(loader,EXPECTED_RENDER_OWNER).getDeclaredMethod("a",
                String.class, String.class, type(loader,"xpm0"), type(loader,"r0o0"),
                type(loader,"r0o0"), float.class, boolean.class, boolean.class,
                boolean.class, boolean.class,
                type(loader,"kotlin.jvm.functions.Function0"),
                type(loader,"kotlin.jvm.functions.Function2"),
                boolean.class, boolean.class, type(loader,"kotlin.jvm.functions.Function0"),
                boolean.class, type(loader,"va41"), type(loader,"rqr"), int.class, int.class);
            if (!Modifier.isStatic(render.getModifiers()) || render.getReturnType()!=void.class)
                throw new NoSuchMethodException("CodeBlockImpl signature mismatch");
            // CopyButtonImpl is a separate, exact, static Compose renderer in the same DEX.
            // Its invocation indicates composition only; it does not identify a user gesture.
            Method copyButton=type(loader,EXPECTED_RENDER_OWNER).getDeclaredMethod("c",
                int.class, int.class, type(loader,"co3"), type(loader,"rqr"), type(loader,"r0o0"));
            if (!Modifier.isStatic(copyButton.getModifiers()) || copyButton.getReturnType()!=void.class)
                throw new NoSuchMethodException("CopyButtonImpl signature mismatch");
            Method clipboard=ClipboardManager.class.getDeclaredMethod("setPrimaryClip",ClipData.class);
            XposedInterface xp=context.framework(XposedInterface.class);
            // P05: classes4.dex lxl.c creates a twl Function0 via the exact
            // seven-argument constructor; twl.invoke lives in classes5.dex.
            // Constructor binding is allowed ONLY under the lxl.c render frame.
            // This remains an observed handler CANDIDATE until synchronous copy
            // confirmation; it is not a visual block identity or execution grant.
            try {
                Class<?> callbackOwner=type(loader,"twl");
                Class<?> function0=type(loader,"kotlin.jvm.functions.Function0");
                if(!function0.isAssignableFrom(callbackOwner))
                    throw new NoSuchMethodException("Pinned Copy handler not Function0");
                Constructor<?> constructor=callbackOwner.getDeclaredConstructor(
                    Object.class,Object.class,String.class,Object.class,Object.class,
                    type(loader,"bho0"),byte.class);
                Method handler=callbackOwner.getDeclaredMethod("invoke");
                if(handler.getReturnType()!=Object.class || handler.getParameterCount()!=0)
                    throw new NoSuchMethodException("Pinned Copy handler invoke mismatch");
                xp.hook(constructor).setId("hooklab.cgt01.p05.copyhandler.ctor.2632128")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result=chain.proceed();
                        if(ACTIVE.get() && COPY_CAUSALITY.inRender()) {
                            CopyButtonCausalityTracker.Mark mark=
                                COPY_CAUSALITY.constructed(chain.getThisObject());
                            if(mark!=null)
                                p05(xp,CopyButtonCausalityTracker.Event.BUTTON_CONSTRUCTOR_BOUND,0,
                                    "binding_id="+mark.bindingId+" render_id="+mark.renderId
                                    +" tap=UNVERIFIED visual_block_identity=UNVERIFIED");
                        }
                        return result;
                    });
                xp.hook(handler).setId("hooklab.cgt01.p05.copyhandler.invoke.2632128")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if(!ACTIVE.get() || !COPY_CAUSALITY.hasCandidates())
                            return chain.proceed();
                        CopyButtonCausalityTracker.InvocationFrame frame=
                            COPY_CAUSALITY.enterHandler(chain.getThisObject());
                        if(frame==null)return chain.proceed();
                        try {
                            p05(xp,CopyButtonCausalityTracker.Event.BUTTON_HANDLER_INVOKED,0,
                                "binding_id="+frame.bindingId+" render_id="+frame.renderId
                                +" actual_copy=UNVERIFIED");
                            return chain.proceed();
                        } finally {COPY_CAUSALITY.exitHandler(frame);}
                    });
                xp.log(Log.INFO,LOG_TAG,
                    "CGT01_P05_HANDLER_HOOKS_INSTALLED candidate=twl constructor=EXACT invoke=EXACT "
                    +"clipboard_causality=PENDING");
            } catch(Throwable failure) {
                TweakDiagnostics.rethrowFatal(failure);
                xp.log(Log.WARN,LOG_TAG,"CGT01_P05_HANDLER_INSTALL_REFUSED category="
                    +TweakDiagnostics.failureCategory(failure));
            }
            xp.hook(copyButton).setId("hooklab.cgt01.copybutton.render.2632128")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    CopyButtonCausalityTracker.RenderFrame frame=COPY_CAUSALITY.beginRender();
                    try {
                        Object result=chain.proceed();
                        if (ACTIVE.get() && COPY_BUTTON_RENDER_SEEN.compareAndSet(false,true)) {
                            xp.log(Log.INFO,LOG_TAG,
                                "CGT01_COPY_BUTTON_RENDER_OBSERVED build=2632128 tap=UNVERIFIED block_identity=UNVERIFIED");
                        }
                        return result;
                    } finally {COPY_CAUSALITY.endRender(frame);}
                });
            xp.hook(render).setId("hooklab.cgt01.codeblock.render.2632128")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result=chain.proceed(); // Host drawing always happens first.
                    if (!ACTIVE.get()) return result;
                    if (NATIVE_RENDER_SEEN.compareAndSet(false,true)) {
                        xp.log(Log.INFO,LOG_TAG,
                            "CGT01_NATIVE_RENDER_OBSERVED build=2632128 visual_block_identity=UNVERIFIED");
                    }
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
                        // Synchronous correlation on this thread only: do not infer
                        // which visual block was tapped from equal values.
                        CopyButtonCausalityTracker.InvocationFrame handlerFrame=
                            COPY_CAUSALITY.currentHandler();
                        if(handlerFrame!=null) {
                            if(evidence.result==CodeBlockProbeMatcher.Result.MATCH_VALUE_ONLY) {
                                p05(xp,CopyButtonCausalityTracker.Event.BUTTON_CLIPBOARD_MATCH,0,
                                    "binding_id="+handlerFrame.bindingId
                                    +" render_id="+handlerFrame.renderId
                                    +" slot="+evidence.parameterSlot
                                    +" bytes="+evidence.utf8Length
                                    +" ephemeral_token="+evidence.ephemeralToken
                                    +" synchronous=YES visual_block_identity=UNVERIFIED");
                            } else if(evidence.result==CodeBlockProbeMatcher.Result.AMBIGUOUS_SLOTS) {
                                p05(xp,CopyButtonCausalityTracker.Event.BUTTON_CLIPBOARD_AMBIGUOUS,0,
                                    "binding_id="+handlerFrame.bindingId
                                    +" source_value=AMBIGUOUS");
                            } else {
                                p05(xp,CopyButtonCausalityTracker.Event.BUTTON_CLIPBOARD_NO_MATCH,0,
                                    "binding_id="+handlerFrame.bindingId
                                    +" source_value=UNVERIFIED");
                            }
                        }
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
            xp.log(Log.INFO,LOG_TAG,"CGT01_PROBE_INSTALLED build=2632128 mode=READ_ONLY "
                +"p04_copybutton_render=OBSERVED_NOT_TAP p05_copyhandler=PINNED_CANDIDATE captured_content=NONE persistent_storage=NONE");
        } catch(Throwable failure) {
            ACTIVE.set(false);
            INSTALLED.set(false);
            TweakDiagnostics.rethrowFatal(failure);
            if (failure instanceof Exception) throw (Exception)failure;
            throw new IllegalStateException("CGT01 probe registration failed",failure);
        }
    }
}
