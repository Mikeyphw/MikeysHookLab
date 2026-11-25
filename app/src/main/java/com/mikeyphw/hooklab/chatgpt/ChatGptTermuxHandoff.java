package com.mikeyphw.hooklab.chatgpt;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import com.mikeyphw.hooklab.core.HookContext;
import com.mikeyphw.hooklab.core.TweakDiagnostics;
import com.mikeyphw.hooklab.termux.TermuxCommandPolicy;
import io.github.libxposed.api.XposedInterface;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

/** P08: exact-build native Compose Row with independently tracked Run and Copy children. */
public final class ChatGptTermuxHandoff {
    public static final String TWEAK_ID="chatgpt.codeblock.termux.handoff";
    private static final String TAG="HookLabCGT01";
    private static final AtomicBoolean ENABLED=new AtomicBoolean(),FAILED=new AtomicBoolean();
    private static final ThreadLocal<Object> FRAME=new ThreadLocal<>();
    private static WeakReference<Activity> foreground=new WeakReference<>(null);
    private static final int HOST_KEY=0xd01ebe4d;
    // Fixed distinct call-site keys; never derive them from message text or global counters.
    static final int ROW_KEY=0x484c0501,RUN_SLOT=0x484c0502,COPY_SLOT=0x484c0503,
        RUN_KEY=0x484c0504,COPY_KEY=0x484c0505,BIND_SLOT=0x484c0506,ICON_SLOT=0x484c0507,ICON_KEY=0x484c0508;
    private ChatGptTermuxHandoff(){}
    public static void install(HookContext ctx)throws Exception {
        if(!ChatGptCodeProbe.PACKAGE.equals(ctx.packageName)||!ctx.packageName.equals(ctx.processName)||ctx.versionCode!=ChatGptCodeProbe.VERSION_CODE)
            throw new SecurityException("Unsupported host");
        Context app=ctx.appContext(Context.class);PackageInfo info=app.getPackageManager().getPackageInfo(ctx.packageName,0);
        if(info.getLongVersionCode()!=ChatGptCodeProbe.VERSION_CODE||!ChatGptCodeProbe.VERSION_NAME.equals(info.versionName))throw new SecurityException("Unsupported build");
        Host host=new Host(ctx.classLoader); // Resolve all exact signatures before registering any hook.
        XposedInterface xp=ctx.framework(XposedInterface.class);
        xp.hook(Activity.class.getDeclaredMethod("onPostResume")).setId("hooklab.cgt01.p08.resume")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{Object result=chain.proceed();
                if(ENABLED.get()&&chain.getThisObject() instanceof Activity){Activity a=(Activity)chain.getThisObject();if(ChatGptCodeProbe.PACKAGE.equals(a.getPackageName()))foreground=new WeakReference<>(a);}return result;});
        xp.hook(Activity.class.getDeclaredMethod("onPause")).setId("hooklab.cgt01.p08.pause")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{if(foreground.get()==chain.getThisObject())foreground.clear();return chain.proceed();});
        xp.hook(host.contentInvoke).setId("hooklab.cgt01.p08.frame")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                Object previous=FRAME.get();Object current=null;
                if(ENABLED.get()&&!FAILED.get())try{current=host.contentCode.get(chain.getThisObject());}catch(ReflectiveOperationException e){log("FRAME_REFUSED");}
                if(current==null)FRAME.remove();else FRAME.set(current);
                try{return chain.proceed();}finally{if(previous==null)FRAME.remove();else FRAME.set(previous);}
            });
        xp.hook(host.factory).setId("hooklab.cgt01.p08.wrapper")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                // Before touching the Composer, refuse any unexpected call site or malformed capture.
                if(!ENABLED.get()||FAILED.get()||FRAME.get()==null||!Integer.valueOf(HOST_KEY).equals(chain.getArg(0))||!Boolean.TRUE.equals(chain.getArg(1)))return chain.proceed();
                Object delegate=chain.getArg(2);Object code;Object wrapped;
                try{
                    if(delegate==null||delegate.getClass()!=host.q12||host.branch.getByte(delegate)!=8)return chain.proceed();
                    code=host.capture.get(delegate);
                    if(code!=FRAME.get()||!host.co3.isInstance(code)||!host.composer.isInstance(chain.getArg(3)))return chain.proceed();
                    // Refuse empty, overlong or visually deceptive source before changing layout.
                    TermuxCommandPolicy.checkedCommand((String)host.codeText.get(code));
                    wrapped=host.proxy(host.f3,new RowContent(host,code,delegate));
                }catch(ReflectiveOperationException|IllegalArgumentException unavailable){log("CAPTURE_REFUSED");return chain.proceed();}
                return chain.proceed(new Object[]{HOST_KEY,true,wrapped,chain.getArg(3)});
            });
        ENABLED.set(true);log("INSTALLED");
    }
    private static void log(String event){Log.i(TAG,"CGT01_P08_"+event+" execution=NONE");}
    private static boolean ready(Activity a){return a!=null&&!a.isFinishing()&&!a.isDestroyed()&&a.hasWindowFocus()&&a.getWindow().getDecorView().isAttachedToWindow();}
    private static void openPreview(TermuxOfferGate b){
        if(FAILED.get()||Looper.myLooper()!=Looper.getMainLooper()){log("CLICK_REFUSED");return;}
        Activity a=foreground.get();if(!ready(a)){log("CLICK_REFUSED");return;}
        String source=b.selected(SystemClock.elapsedRealtime());if(source==null){log("STALE_REFUSED");return;}
        Intent intent=new Intent(TermuxCommandPolicy.ACTION_PREVIEW)
            .setComponent(new ComponentName("com.mikeyphw.hooklab","com.mikeyphw.hooklab.termux.TermuxBridgeActivity"))
            .putExtra(TermuxCommandPolicy.EXTRA_SOURCE,source)
            .putExtra(TermuxCommandPolicy.EXTRA_REQUEST_ID,java.util.UUID.randomUUID().toString())
            .putExtra(TermuxCommandPolicy.EXTRA_REQUEST_TIME,SystemClock.elapsedRealtime())
            .putExtra(TermuxCommandPolicy.EXTRA_REQUEST_WALL,System.currentTimeMillis());
        // Calling package is authenticated by Android in the receiver via startActivityForResult.
        try{a.startActivityForResult(intent,0x484c);log("PREVIEW_REQUESTED");}
        catch(RuntimeException unavailable){log("PREVIEW_REFUSED");}
    }
    /** Kotlin's obfuscated host Function types are loaded from the host, never bundled twice. */
    private abstract static class Lambda implements InvocationHandler {
        abstract Object invokeBody(Object[] args)throws Throwable;
        @Override public final Object invoke(Object proxy,Method method,Object[] args)throws Throwable {
            if(method.getDeclaringClass()==Object.class){
                switch(method.getName()) {case "equals":return proxy==args[0];case "hashCode":return System.identityHashCode(proxy);case "toString":return "HookLabComposeLambda";default:throw new UnsupportedOperationException();}
            }
            return invokeBody(args==null?new Object[0]:args);
        }
    }
    private static final class RowContent extends Lambda {
        final Host h;final Object code,delegate,run;
        RowContent(Host h,Object code,Object delegate){this.h=h;this.code=code;this.delegate=delegate;
            this.run=h.proxy(h.f2,new Lambda(){Object invokeBody(Object[] args)throws Throwable{return h.runButton(code,args[0]);}});}
        @Override Object invokeBody(Object[] a)throws Throwable {
            Object scope=a[0],c=a[1];
            // Once a Composer is mutated there is no safe arbitrary fallback/replay. Let the
            // host abort the failed composition, latch clicks disabled and require process restart.
            if(!h.composer.isInstance(c)||!h.scope.isInstance(scope))throw new IllegalStateException("Unexpected composition");
            try{
                h.call(h.begin,c,ROW_KEY);
                Object policy=h.call(h.rowPolicy,null,h.arrangement,h.alignment,c,0);
                int key=Long.hashCode(h.composite.getLong(c));Object locals=h.call(h.locals,c);
                Object mod=h.call(h.materialize,null,c,h.emptyModifier);
                h.call(h.startNode,c);
                if(h.inserting.getBoolean(c))h.call(h.createNode,c,h.nodeFactory);else h.call(h.reuseNode,c);
                h.call(h.set,null,c,policy,h.measureSetter);h.call(h.set,null,c,locals,h.localsSetter);
                h.call(h.keySetter,null,key,c,h.compositeSetter,c);h.call(h.set,null,c,mod,h.modifierSetter);
                h.call(h.begin,c,RUN_SLOT);Object runLambda=h.call(h.factory,null,RUN_KEY,true,run,c);h.call(h.invoke2,runLambda,c,0);h.call(h.end,c,false);
                h.call(h.begin,c,COPY_SLOT);Object copyLambda=h.call(h.factory,null,COPY_KEY,true,delegate,c);h.call(h.invoke3,copyLambda,scope,c,0);h.call(h.end,c,false);
                h.call(h.end,c,true);h.call(h.end,c,false);
                if(h.renderLogged.compareAndSet(false,true))log("ROW_COMPOSED");return h.unit;
            }catch(Throwable error){FAILED.set(true);log("COMPOSITION_FAILED");TweakDiagnostics.rethrowFatal(error);throw error;}
        }
    }
    private static final class Binding extends Lambda {
        final Host h;final TermuxOfferGate state;
        Binding(Host h,Object code,String source){this.h=h;state=new TermuxOfferGate(code,source);}
        // Function0 and RememberObserver share the same slot-owned object.
        @Override Object invokeBody(Object[] args){openPreview(state);return h.unit;}
        Object lifecycle(String method){switch(method){case "f":state.remembered();return null;case "m":case "n":state.dispose();return null;default:throw new UnsupportedOperationException();}}
    }
    private static final class Host {
        final ClassLoader loader;final Class<?> q12,co3,composer,scope,f0,f2,f3,remember,zdr;
        final Field branch,capture,contentCode,codeText,inserting,composite;
        final Method contentInvoke,factory,begin,end,rowPolicy,locals,materialize,startNode,createNode,reuseNode,set,keySetter,invoke2,invoke3,readSlot,writeSlot,iconButton,iconPainter,drawIcon;
        final Object arrangement,alignment,emptyModifier,nodeFactory,measureSetter,localsSetter,compositeSetter,modifierSetter,unit,emptySlot,terminalAsset;
        final AtomicBoolean renderLogged=new AtomicBoolean();
        Host(ClassLoader l)throws Exception {
            loader=l;q12=type("q12");co3=type("co3");composer=type("oba0");scope=type("fm3");f0=type("kotlin.jvm.functions.Function0");f2=type("kotlin.jvm.functions.Function2");f3=type("kotlin.jvm.functions.Function3");remember=type("q4z0");zdr=type("zdr");
            Class<?> rqr=type("rqr"),r0=type("r0o0"),gqr=type("gqr"),cxl=type("cxl");
            branch=field(q12,"a",byte.class);capture=field(q12,"b",Object.class);contentCode=field(cxl,"p",co3);codeText=field(co3,"b",String.class);
            if(!Modifier.isFinal(codeText.getModifiers())||!f3.isAssignableFrom(q12)||!remember.isInterface()||!rqr.isAssignableFrom(composer))throw new NoSuchMethodException("Host shape changed");
            for(String name:new String[]{"f","m","n"})method(remember,name,void.class,false);
            contentInvoke=method(cxl,"invoke",Object.class,false,Object.class,Object.class);
            factory=method(type("txy0"),"N",zdr,true,int.class,boolean.class,Object.class,rqr);
            begin=method(composer,"n0",void.class,false,int.class);end=method(composer,"s",void.class,false,boolean.class);
            rowPolicy=method(type("sf11"),"a",type("uf11"),true,type("x37"),type("mqa"),rqr,int.class);
            locals=method(composer,"n",type("ont0"),false);materialize=method(type("bq81"),"X",r0,true,rqr,r0);
            startNode=method(composer,"r0",void.class,false);createNode=method(composer,"m",void.class,false,f0);reuseNode=method(composer,"A0",void.class,false);
            set=method(type("hn61"),"f",void.class,true,rqr,Object.class,f2);keySetter=method(type("bqg"),"u",void.class,true,int.class,composer,type("z4e"),composer);
            invoke2=method(zdr,"a",Object.class,false,rqr,int.class);invoke3=method(zdr,"b",Object.class,false,Object.class,rqr,int.class);
            readSlot=method(composer,"X",Object.class,false);writeSlot=method(composer,"x0",void.class,false,Object.class);
            inserting=field(composer,"S",boolean.class);composite=field(composer,"T",long.class);
            arrangement=constant(type("pgq"),"a",type("orn0"));alignment=constant(type("by1"),"l",type("mqa"));emptyModifier=constant(type("o0o0"),"a",type("o0o0"));
            nodeFactory=constant(type("androidx.compose.ui.node.LayoutNode"),"a2",type("y850"));measureSetter=constant(gqr,"f",type("kfr"));localsSetter=constant(gqr,"e",type("dgd"));compositeSetter=constant(gqr,"g",type("z4e"));modifierSetter=constant(gqr,"d",type("x4e"));
            unit=constant(type("mec1"),"a",type("mec1"));emptySlot=constant(type("by1"),"s",type("qro0"));
            if(!f0.isInstance(nodeFactory)||!f2.isInstance(measureSetter)||!f2.isInstance(localsSetter)||!f2.isInstance(modifierSetter))throw new NoSuchMethodException("Node setters");
            iconButton=method(type("g3v"),"m",void.class,true,f0,r0,type("l5d0"),boolean.class,boolean.class,String.class,f0,type("nfo0"),type("ecc"),f3,rqr,int.class,int.class);
            Class<?> drawable=type("da50"),painter=type("f1s0");
            var assetFactory=type("e950").getDeclaredConstructor(byte.class);assetFactory.setAccessible(true);
            terminalAsset=method(type("e950"),"invoke",Object.class,false).invoke(assetFactory.newInstance((byte)12));
            if(!drawable.isInstance(terminalAsset))throw new NoSuchMethodException("Terminal drawable");
            iconPainter=method(type("lje0"),"a",painter,true,drawable,rqr,int.class);
            drawIcon=method(type("t6d0"),"a",void.class,true,painter,String.class,r0,long.class,rqr,int.class,int.class);
            // Exercise proxy construction during installation, before editing any Composer state.
            proxy(f3,new Lambda(){Object invokeBody(Object[] a){return unit;}});
            Proxy.newProxyInstance(loader,new Class<?>[]{f0,remember},(p,m,a)->unit);
        }
        Class<?> type(String name)throws ClassNotFoundException{return Class.forName(name,false,loader);}
        Field field(Class<?> c,String n,Class<?> t)throws Exception {Field f=c.getDeclaredField(n);if(f.getType()!=t)throw new NoSuchFieldException(c.getName()+"."+n);f.setAccessible(true);return f;}
        Object constant(Class<?> c,String n,Class<?> t)throws Exception{Field f=field(c,n,t);if(!Modifier.isStatic(f.getModifiers()))throw new NoSuchFieldException(n);Object v=f.get(null);if(v==null)throw new NoSuchFieldException(n);return v;}
        Method method(Class<?> c,String n,Class<?> ret,boolean stat,Class<?>...args)throws Exception{Method m=c.getDeclaredMethod(n,args);if(m.getReturnType()!=ret||Modifier.isStatic(m.getModifiers())!=stat)throw new NoSuchMethodException(c.getName()+"."+n);m.setAccessible(true);return m;}
        Object proxy(Class<?> type,Lambda handler){return Proxy.newProxyInstance(loader,new Class<?>[]{type},handler);}
        Object call(Method m,Object target,Object...args)throws Throwable{try{return m.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}}
        Object runButton(Object code,Object c)throws Throwable {
            call(begin,c,BIND_SLOT);
            Object click=call(readSlot,c);Binding bound=null;
            if(click!=emptySlot&&click!=null&&Proxy.isProxyClass(click.getClass())){
                InvocationHandler ih=Proxy.getInvocationHandler(click);
                // Binding handler owns lifecycle and click; verify its exact private type.
                if(ih instanceof BindingHandler)bound=((BindingHandler)ih).binding;
            }
            if(bound==null||!bound.state.owns(code)){
                bound=new Binding(this,code,TermuxCommandPolicy.checkedCommand((String)codeText.get(code)));
                click=Proxy.newProxyInstance(loader,new Class<?>[]{f0,remember},new BindingHandler(bound));call(writeSlot,c,click);
            }
            call(end,c,false);
            call(begin,c,ICON_SLOT);
            Object icon=call(factory,null,ICON_KEY,true,proxy(f3,new Lambda(){Object invokeBody(Object[] a)throws Throwable {
                // Use the exact host painter, with an explicit packed Compose sRGB white tint.
                Object painter=call(iconPainter,null,terminalAsset,a[1],0);
                call(drawIcon,null,painter,null,null,0xFFFFFFFF00000000L,a[1],0,0x4);return unit;
            }}),c);
            call(end,c,false);
            // Default modifier/colors/enabled/selected/secondary action/interaction/style;
            // pass the accessibility label and independent content explicitly.
            call(iconButton,null,click,null,null,false,false,"Run in Termux",null,null,null,icon,c,0,0x1de);
            return unit;
        }
    }
    private static final class BindingHandler implements InvocationHandler {
        final Binding binding;
        BindingHandler(Binding b){binding=b;}
        @Override public Object invoke(Object p,Method m,Object[] a)throws Throwable {
            if(m.getDeclaringClass()==binding.h.remember)return binding.lifecycle(m.getName());
            return binding.invoke(p,m,a);
        }
    }
}
