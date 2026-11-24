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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

/** CGT01-P06. Exact-build, read-only observer; no clipboard reads, dispatch or execution. */
public final class ChatGptCodeProbe {
    public static final String PACKAGE="com.openai.chatgpt";
    public static final String VERSION_NAME="1.2026.321";
    public static final long VERSION_CODE=2632128L;
    public static final String TWEAK_ID="chatgpt.codeblock.copy.probe";
    public static final String EXPECTED_RENDER_OWNER="lxl";
    private static final String TAG="HookLabCGT01";
    private static final AtomicBoolean INSTALLED=new AtomicBoolean(), ACTIVE=new AtomicBoolean();
    private static final AtomicBoolean NATIVE_RENDER_SEEN=new AtomicBoolean(), COPY_BUTTON_RENDER_SEEN=new AtomicBoolean();
    private static final CodeBlockProbeMatcher MATCHER=new CodeBlockProbeMatcher(SystemClock::elapsedRealtime);
    private static final AsyncCopyTrace TRACE=new AsyncCopyTrace(SystemClock::elapsedRealtime);
    private static final ThreadLocal<AsyncCopyTrace.Block> BLOCK=new ThreadLocal<>();
    private static final ThreadLocal<AsyncCopyTrace.Render> RENDER=new ThreadLocal<>();
    private static final ThreadLocal<AsyncCopyTrace.Action> HANDLER=new ThreadLocal<>(), IXL=new ThreadLocal<>(), SAFE=new ThreadLocal<>(), WRAP=new ThreadLocal<>(), BACKEND=new ThreadLocal<>();
    private ChatGptCodeProbe(){}
    private static Class<?> type(ClassLoader l,String n)throws ClassNotFoundException{return Class.forName(n,false,l);}
    private static Field field(Class<?> c,String n,Class<?> expected)throws Exception{Field f=c.getDeclaredField(n);if(f.getType()!=expected)throw new NoSuchFieldException(n);f.setAccessible(true);return f;}
    private static Method method(Class<?> c,String n,Class<?> ret,boolean stat,Class<?>...args)throws Exception{Method m=c.getDeclaredMethod(n,args);if(m.getReturnType()!=ret||Modifier.isStatic(m.getModifiers())!=stat)throw new NoSuchMethodException(n);return m;}
    private static <T> void set(ThreadLocal<T> slot,T v){if(v==null)slot.remove();else slot.set(v);}
    private static String value(Field f,Object o)throws IllegalAccessException{return o==null?null:(String)f.get(o);}
    private static String clipValue(ClipData c){if(c==null||c.getItemCount()!=1||c.getItemAt(0)==null)return null;CharSequence t=c.getItemAt(0).getText();return t==null?null:t.toString();}
    private static void event(XposedInterface x,String code,AsyncCopyTrace.Action a){if(TRACE.report(a,code))x.log(Log.INFO,TAG,"CGT01_P06_"+code+" action_id="+a.id+" render_id="+a.renderId+" block_id="+a.blockId+" visual_block_identity=UNVERIFIED");}
    private static void error(XposedInterface x,Throwable t){TweakDiagnostics.rethrowFatal(t);x.log(Log.WARN,TAG,"CGT01_P06_OBSERVATION_REFUSED category="+TweakDiagnostics.failureCategory(t));}
    public static void install(HookContext context)throws Exception{
        if(!PACKAGE.equals(context.packageName)||!PACKAGE.equals(context.processName)||context.versionCode!=VERSION_CODE)throw new SecurityException("ChatGPT process/build mismatch");
        Context host=context.appContext(Context.class);PackageInfo installed=host.getPackageManager().getPackageInfo(PACKAGE,0);
        if(installed.getLongVersionCode()!=VERSION_CODE||!VERSION_NAME.equals(installed.versionName))throw new SecurityException("ChatGPT build mismatch");
        if(!INSTALLED.compareAndSet(false,true))return;
        try{
            ClassLoader l=context.classLoader;
            Class<?> lxl=type(l,EXPECTED_RENDER_OWNER), co=type(l,"co3"), d0=type(l,"d0m"), rqr=type(l,"rqr"), r0=type(l,"r0o0"), xpm=type(l,"xpm0"), f0=type(l,"kotlin.jvm.functions.Function0"), f2=type(l,"kotlin.jvm.functions.Function2"), continuation=type(l,"l0u"), twl=type(l,"twl"), ixl=type(l,"ixl"), vm=type(l,"vm90"), wrap=type(l,"t7l"), ay=type(l,"ay2"), vv=type(l,"vv2");
            Field coText=field(co,"b",String.class), resultCo=field(d0,"a",co), twBranch=field(twl,"a",byte.class), twCode=field(twl,"e",Object.class), ixBranch=field(ixl,"n",byte.class), ixCode=field(ixl,"s",Object.class), vmBranch=field(vm,"n",byte.class), vmPayload=field(vm,"p",Object.class), wrapClip=field(wrap,"a",ClipData.class);
            if(!f0.isAssignableFrom(twl)||!f2.isAssignableFrom(ixl)||!f2.isAssignableFrom(vm))throw new NoSuchMethodException("callback interface");
            Method render=method(lxl,"a",void.class,true,String.class,String.class,xpm,r0,r0,float.class,boolean.class,boolean.class,boolean.class,boolean.class,f0,f2,boolean.class,boolean.class,f0,boolean.class,type(l,"va41"),rqr,int.class,int.class);
            Method annotate=method(type(l,"mfk1"),"u",d0,true,String.class,String.class,xpm,boolean.class,Integer.class,rqr,int.class,int.class);
            Method copyButton=method(lxl,"c",void.class,true,int.class,int.class,co,rqr,r0);
            Constructor<?> callback=twl.getDeclaredConstructor(Object.class,Object.class,String.class,Object.class,Object.class,type(l,"bho0"),byte.class);
            Method handler=method(twl,"invoke",Object.class,false);
            Constructor<?> coroutine=ixl.getDeclaredConstructor(Object.class,String.class,Object.class,Object.class,Object.class,Object.class,continuation,byte.class);
            Method ixCreate=method(ixl,"create",continuation,false,Object.class,continuation), ixRun=method(ixl,"invokeSuspend",Object.class,false,Object.class);
            Constructor<?> wrapper=wrap.getDeclaredConstructor(ClipData.class);
            Method vmCreate=method(vm,"create",continuation,false,Object.class,continuation), vmRun=method(vm,"invokeSuspend",Object.class,false,Object.class);
            Method ayWrite=method(ay,"b",Object.class,false,wrap,continuation), vvWrite=method(vv,"b",Object.class,false,wrap,continuation);
            Method clipboard=ClipboardManager.class.getDeclaredMethod("setPrimaryClip",ClipData.class);
            XposedInterface xp=context.framework(XposedInterface.class);
            // Resolve every signature before registering. All callbacks inactive until the last hook succeeds.
            xp.hook(render).setId("hooklab.cgt01.p06.render").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                AsyncCopyTrace.Block old=BLOCK.get(), b=null;
                if(ACTIVE.get())try{String source=(String)chain.getArg(1);b=TRACE.block(source);MATCHER.observePair((String)chain.getArg(0),source);}catch(Throwable t){error(xp,t);}
                set(BLOCK,b);try{Object result=chain.proceed();if(ACTIVE.get()&&NATIVE_RENDER_SEEN.compareAndSet(false,true))xp.log(Log.INFO,TAG,"CGT01_NATIVE_RENDER_OBSERVED build=2632128 visual_block_identity=UNVERIFIED");return result;}finally{set(BLOCK,old);}
            });
            xp.hook(annotate).setId("hooklab.cgt01.p06.annotate").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{Object result=chain.proceed();if(ACTIVE.get()&&BLOCK.get()!=null&&result!=null)try{Object a=resultCo.get(result);if(co.isInstance(a))TRACE.annotate(BLOCK.get(),a,value(coText,a));}catch(Throwable t){error(xp,t);}return result;});
            xp.hook(copyButton).setId("hooklab.cgt01.p06.copybutton").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                AsyncCopyTrace.Render old=RENDER.get(),r=null;if(ACTIVE.get())try{Object a=chain.getArg(2);if(co.isInstance(a))r=TRACE.render(a,value(coText,a));}catch(Throwable t){error(xp,t);}
                set(RENDER,r);try{Object result=chain.proceed();if(ACTIVE.get()&&COPY_BUTTON_RENDER_SEEN.compareAndSet(false,true))xp.log(Log.INFO,TAG,"CGT01_COPY_BUTTON_RENDER_OBSERVED build=2632128 tap=UNVERIFIED block_identity=UNVERIFIED");return result;}finally{set(RENDER,old);}
            });
            xp.hook(callback).setId("hooklab.cgt01.p06.twl.ctor").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{Object result=chain.proceed();if(ACTIVE.get()&&RENDER.get()!=null)try{Object o=chain.getThisObject();if(twBranch.getByte(o)==0)TRACE.callback(RENDER.get(),o,twCode.get(o));}catch(Throwable t){error(xp,t);}return result;});
            xp.hook(handler).setId("hooklab.cgt01.p06.twl.invoke").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                AsyncCopyTrace.Action old=HANDLER.get(),a=null;if(ACTIVE.get())try{Object o=chain.getThisObject();if(twBranch.getByte(o)==0){a=TRACE.begin(o);if(a!=null&&!a.ownsAnnotated(twCode.get(o)))a=null;}event(xp,"HANDLER",a);}catch(Throwable t){a=null;error(xp,t);}
                set(HANDLER,a);try{return chain.proceed();}finally{set(HANDLER,old);}
            });
            xp.hook(coroutine).setId("hooklab.cgt01.p06.ixl.ctor").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{Object result=chain.proceed();AsyncCopyTrace.Action a=HANDLER.get();if(ACTIVE.get()&&a!=null)try{Object o=chain.getThisObject();if(ixBranch.getByte(o)==0&&a.ownsAnnotated(ixCode.get(o))){TRACE.tag(AsyncCopyTrace.Kind.IXL,o,a);event(xp,"IXL_CONSTRUCTED",a);}else event(xp,"IXL_IDENTITY_REFUSED",a);}catch(Throwable t){error(xp,t);}return result;});
            xp.hook(ixCreate).setId("hooklab.cgt01.p06.ixl.create").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{AsyncCopyTrace.Action a=ACTIVE.get()?TRACE.get(AsyncCopyTrace.Kind.IXL,chain.getThisObject()):null;Object result=chain.proceed();if(a!=null&&ixl.isInstance(result))try{if(ixBranch.getByte(result)==0&&a.ownsAnnotated(ixCode.get(result))){TRACE.tag(AsyncCopyTrace.Kind.IXL,result,a);event(xp,"IXL_CONTINUATION",a);}else event(xp,"IXL_CONTINUATION_REFUSED",a);}catch(Throwable t){error(xp,t);}return result;});
            xp.hook(ixRun).setId("hooklab.cgt01.p06.ixl.resume").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{AsyncCopyTrace.Action old=IXL.get(),a=ACTIVE.get()?TRACE.get(AsyncCopyTrace.Kind.IXL,chain.getThisObject()):null;set(IXL,a);try{event(xp,"IXL_RESUME",a);return chain.proceed();}finally{set(IXL,old);}});
            xp.hook(wrapper).setId("hooklab.cgt01.p06.t7l.ctor").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{Object result=chain.proceed();AsyncCopyTrace.Action a=WRAP.get()!=null?WRAP.get():IXL.get();if(ACTIVE.get()&&a!=null)try{Object o=chain.getThisObject();Object c=wrapClip.get(o);if(c!=null&&c==chain.getArg(0)){TRACE.tag(AsyncCopyTrace.Kind.WRAPPER,o,a);TRACE.tag(AsyncCopyTrace.Kind.CLIP,c,a);event(xp,"CLIP_WRAPPED",a);}}catch(Throwable t){error(xp,t);}return result;});
            xp.hook(vmCreate).setId("hooklab.cgt01.p06.safe.create").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{AsyncCopyTrace.Action a=ACTIVE.get()?TRACE.get(AsyncCopyTrace.Kind.WRAPPER,chain.getArg(0)):null;Object result=chain.proceed();if(a!=null&&vm.isInstance(result))try{if(vmBranch.getByte(result)==2&&vmPayload.get(result)==chain.getArg(0)){TRACE.tag(AsyncCopyTrace.Kind.SAFE,result,a);event(xp,"SAFE_CONTINUATION",a);}}catch(Throwable t){error(xp,t);}return result;});
            xp.hook(vmRun).setId("hooklab.cgt01.p06.safe.resume").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{AsyncCopyTrace.Action old=SAFE.get(),a=ACTIVE.get()?TRACE.get(AsyncCopyTrace.Kind.SAFE,chain.getThisObject()):null;set(SAFE,a);try{event(xp,"SAFE_RESUME",a);return chain.proceed();}finally{set(SAFE,old);}});
            xp.hook(ayWrite).setId("hooklab.cgt01.p06.wrapper.write").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{AsyncCopyTrace.Action old=WRAP.get(),a=ACTIVE.get()?TRACE.get(AsyncCopyTrace.Kind.WRAPPER,chain.getArg(0)):null;if(a!=SAFE.get()&&a!=old)a=null;set(WRAP,a);try{event(xp,"CLIPBOARD_WRAPPER",a);return chain.proceed();}finally{set(WRAP,old);}});
            xp.hook(vvWrite).setId("hooklab.cgt01.p06.backend.write").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{AsyncCopyTrace.Action old=BACKEND.get(),a=ACTIVE.get()?TRACE.get(AsyncCopyTrace.Kind.WRAPPER,chain.getArg(0)):null;if(a!=SAFE.get()&&a!=WRAP.get())a=null;set(BACKEND,a);try{event(xp,"BACKEND",a);return chain.proceed();}finally{set(BACKEND,old);}});
            xp.hook(clipboard).setId("hooklab.cgt01.p06.clipboard.write").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                Object result=chain.proceed();if(!ACTIVE.get())return result;
                try{ClipData clip=(ClipData)chain.getArg(0);AsyncCopyTrace.Action a=BACKEND.get();
                    // Inspect only a tracked payload or the original renderer value-parity candidate. Never read global clipboard.
                    String copied=clipValue(clip);if(copied==null)return result;
                    if(a!=null){String verdict=TRACE.clipboard(a,clip,copied);event(xp,"CLIPBOARD_"+verdict,a);}
                    CodeBlockProbeMatcher.Evidence e=MATCHER.compareCopiedText(copied);if(e.result==CodeBlockProbeMatcher.Result.MATCH_VALUE_ONLY)xp.log(Log.INFO,TAG,"CGT01_COPY_MATCH_VALUE_ONLY slot="+e.parameterSlot+" bytes="+e.utf8Length+" action_causality=UNVERIFIED");
                }catch(Throwable t){error(xp,t);}return result;
            });
            ACTIVE.set(true);xp.log(Log.INFO,TAG,"CGT01_P06_HOOKS_INSTALLED build=2632128 mode=READ_ONLY captured_content=NONE persistent_storage=NONE");xp.log(Log.INFO,TAG,"CGT01_PROBE_INSTALLED build=2632128 mode=READ_ONLY");
        }catch(Throwable t){ACTIVE.set(false);TweakDiagnostics.rethrowFatal(t);if(t instanceof Exception)throw (Exception)t;throw new IllegalStateException("CGT01 P06 registration failed",t);}
    }
}
