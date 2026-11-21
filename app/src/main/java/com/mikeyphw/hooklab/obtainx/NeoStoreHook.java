package com.mikeyphw.hooklab.obtainx;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.mikeyphw.hooklab.core.HookContext;
import com.mikeyphw.hooklab.core.TweakDiagnostics;
import com.mikeyphw.hooklab.core.VerboseLogs;
import io.github.libxposed.api.XposedInterface;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Source-pinned Neo Store 1.3.3 (1304) view-model observer. Port of the
 * NeoStoreObtainX M4–M7 pipeline to modern libxposed API 102; no legacy API 82.
 * Exact app route, product and repository are re-read on each explicit tap.
 */
public final class NeoStoreHook {
    private NeoStoreHook() { }
    private static final AtomicBoolean INSTALLED=new AtomicBoolean();
    private static final long POLL_MS=600;
    private static Field field(Class<?> c,String name) throws NoSuchFieldException {
        Field f=c.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Class<?> load(ClassLoader cl,String name) throws ClassNotFoundException {
        return Class.forName(name,false,cl);
    }
    private static final class Symbols {
        final Class<?> activity,route;
        final Field navStack,routePackage,vmPackage,vmCore,suggested,embedded,
            productPackage,productRepo,productSource,productWeb,repoId,repoAddress,repoAuth,pairFirst,pairSecond;
        final Method getValue;
        Symbols(ClassLoader cl) throws ReflectiveOperationException {
            activity=load(cl,"com.machiav3lli.fdroid.NeoActivity");
            route=load(cl,"com.machiav3lli.fdroid.ui.navigation.NavRoute$App");
            Class<?> vm=load(cl,"com.machiav3lli.fdroid.viewmodels.AppPageVM");
            Class<?> core=load(cl,"com.machiav3lli.fdroid.viewmodels.CoreAppState");
            Class<?> embed=load(cl,"com.machiav3lli.fdroid.data.database.entity.EmbeddedProduct");
            Class<?> product=load(cl,"com.machiav3lli.fdroid.data.database.entity.Product");
            Class<?> repo=load(cl,"com.machiav3lli.fdroid.data.database.entity.Repository");
            Class<?> pair=load(cl,"kotlin.Pair");
            getValue=load(cl,"kotlinx.coroutines.flow.StateFlow").getMethod("getValue");
            navStack=field(activity,"navStack");routePackage=field(route,"packageName");
            vmPackage=field(vm,"packageName");vmCore=field(vm,"coreAppState");
            suggested=field(core,"suggestedProductRepo");embedded=field(embed,"product");
            productPackage=field(product,"packageName");productRepo=field(product,"repositoryId");
            productSource=field(product,"source");productWeb=field(product,"web");
            repoId=field(repo,"id");repoAddress=field(repo,"address");repoAuth=field(repo,"authentication");
            pairFirst=field(pair,"first");pairSecond=field(pair,"second");
        }
    }
    private static final class Observer {
        final Symbols s;
        final Handler main=new Handler(Looper.getMainLooper());
        final NeoStoreAction action=new NeoStoreAction();
        final List<WeakReference<Object>> models=new ArrayList<>();
        WeakReference<Activity> active=new WeakReference<>(null);
        Runnable scheduled;
        int epoch;
        Observer(Symbols symbols){s=symbols;}
        void observe(Object model) {
            WeakReference<Object> ref=new WeakReference<>(model);
            main.post(()->{
                Object vm=ref.get();if(vm==null)return;
                models.removeIf(x->x.get()==null||x.get()==vm);
                models.add(ref);while(models.size()>24)models.remove(0);
            });
        }
        private static String str(Object v){return v instanceof String?(String)v:null;}
        private static Long number(Object v){return v instanceof Number?((Number)v).longValue():null;}
        Object route(Activity a) throws ReflectiveOperationException {
            Object stack=s.navStack.get(a);
            if(!(stack instanceof List))return null;
            List<?> list=(List<?>)stack;
            Object top=list.isEmpty()?null:list.get(list.size()-1);
            return top!=null&&s.route.isInstance(top)?top:null;
        }
        SourcePolicy.Snapshot sample(Object top) throws ReflectiveOperationException {
            String activePackage=str(s.routePackage.get(top));
            if(activePackage==null)return null;
            Object vm=null;
            for(int i=models.size()-1;i>=0;i--){
                Object candidate=models.get(i).get();if(candidate==null)continue;
                Object flow=s.vmPackage.get(candidate);
                if(flow!=null&&activePackage.equals(s.getValue.invoke(flow))){vm=candidate;break;}
            }
            if(vm==null)return null;
            Object coreFlow=s.vmCore.get(vm);
            Object state=coreFlow==null?null:s.getValue.invoke(coreFlow);
            Object pair=state==null?null:s.suggested.get(state);
            Object embed=pair==null?null:s.pairFirst.get(pair);
            Object repo=pair==null?null:s.pairSecond.get(pair);
            if(embed==null||repo==null)return null;
            String auth=str(s.repoAuth.get(repo));
            if(auth==null||!auth.isEmpty())return null;
            Object product=s.embedded.get(embed);
            if(product==null)return null;
            String source=str(s.productSource.get(product));
            if(source==null||source.isEmpty())source=str(s.productWeb.get(product));
            return SourcePolicy.ready(activePackage,activePackage,
                str(s.productPackage.get(product)),number(s.productRepo.get(product)),
                number(s.repoId.get(repo)),str(s.repoAddress.get(repo)),auth,source);
        }
        void stop(){
            epoch++;
            if(scheduled!=null)main.removeCallbacks(scheduled);
            scheduled=null;active=new WeakReference<>(null);action.clear();
        }
        void resume(Activity a){
            if(!s.activity.isInstance(a))return;
            if(active.get()==a&&scheduled!=null)return;
            stop();active=new WeakReference<>(a);poll(epoch);
        }
        void pause(Activity a){if(active.get()==a)stop();}
        void poll(int token) {
            if(epoch!=token)return;
            Activity a=active.get();if(a==null)return;
            try {
                Object top=route(a);
                SourcePolicy.Snapshot snap=top==null?null:sample(top);
                VerboseLogs.emit(snap==null?VerboseLogs.Event.NEO_PAGE_WAITING:
                    VerboseLogs.Event.NEO_PAGE_READY);
                action.render(a,top,snap,(displayedRoute,displayed)->tap(token,displayedRoute,displayed));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                VerboseLogs.emit(VerboseLogs.Event.NEO_POLL_REFUSED);
                Log.w("HookLabObtainX","Neo Store source unavailable category="+ex.getClass().getSimpleName());
                stop();return;
            }
            Runnable next=()->poll(token);
            scheduled=next;main.postDelayed(next,POLL_MS);
        }
        void tap(int token,Object shownRoute,SourcePolicy.Snapshot shown) {
            Activity a=active.get();
            if(token!=epoch||!NeoStoreAction.visible(a))return;
            VerboseLogs.emit(VerboseLogs.Event.NEO_TAP);
            try {
                Object actualRoute=route(a);
                SourcePolicy.Snapshot fresh=actualRoute==null?null:sample(actualRoute);
                if(actualRoute!=shownRoute||!SourcePolicy.same(shown,fresh)) {
                    VerboseLogs.emit(VerboseLogs.Event.NEO_TAP_REFUSED);
                    action.result(a,ObtainXBridge.Outcome.UNSAFE_SOURCE);return;
                }
                ObtainXBridge.Outcome result=ObtainXBridge.launch(a,fresh,fresh.packageName);
                VerboseLogs.emit(result==ObtainXBridge.Outcome.DISPATCHED_FOR_PREVIEW
                    ?VerboseLogs.Event.NEO_PREVIEW_REQUESTED:VerboseLogs.Event.NEO_PREVIEW_REFUSED);
                action.result(a,result);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                VerboseLogs.emit(VerboseLogs.Event.NEO_TAP_REFUSED);
                action.result(a,ObtainXBridge.Outcome.UNSAFE_SOURCE);
            }
        }
    }
    public static void install(HookContext ctx) throws Exception {
        if(!ObtainXIds.NEO.equals(ctx.packageName)||ctx.versionCode!=ObtainXIds.NEO_CODE)
            throw new SecurityException("Unverified Neo Store host");
        // Require versionName as well as the verified versionCode; never hook a
        // different host build that happens to reuse the same numeric code.
        android.content.Context hostContext=ctx.appContext(android.content.Context.class);
        android.content.pm.PackageInfo pkgInfo=hostContext.getPackageManager()
            .getPackageInfo(ObtainXIds.NEO,0);
        if(!ObtainXIds.NEO_VERSION.equals(pkgInfo.versionName))
            throw new SecurityException("Pinned host versionName mismatch");
        if(!INSTALLED.compareAndSet(false,true))return;
        XposedInterface xposed=ctx.framework(XposedInterface.class);
        try {
            Symbols s=new Symbols(ctx.classLoader);
            Observer observer=new Observer(s);
            Class<?> vm=load(ctx.classLoader,"com.machiav3lli.fdroid.viewmodels.AppPageVM");
            int hooked=0;
            for(Constructor<?> c:vm.getDeclaredConstructors()) {
                if(c.getParameterCount()!=6)continue;
                xposed.hook(c).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .setId("hooklab.obtainx.neo.vm_constructor")
                    .intercept(chain->{
                        Object result=chain.proceed();
                        Object model=chain.getThisObject();
                        if(model!=null)observer.observe(model);
                        return result;
                    });
                hooked++;
            }
            if(hooked==0)throw new NoSuchMethodException("No pinned AppPageVM constructor");
            Method resume=s.activity.getDeclaredMethod("onResume");resume.setAccessible(true);
            xposed.hook(resume).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .setId("hooklab.obtainx.neo.resume").intercept(chain->{
                    Object result=chain.proceed();
                    Object host=chain.getThisObject();
                    if(host instanceof Activity)observer.main.post(()->observer.resume((Activity)host));
                    return result;
                });
            xposed.hook(Activity.class.getDeclaredMethod("onPause"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .setId("hooklab.obtainx.neo.pause").intercept(chain->{
                    Object host=chain.getThisObject();
                    if(host instanceof Activity)observer.pause((Activity)host);
                    return chain.proceed();
                });
            xposed.log(Log.INFO,"HookLabObtainX","Neo Store source-pinned API102 hooks installed");
            VerboseLogs.emit(VerboseLogs.Event.NEO_HOOK_INSTALLED);
        } catch (Throwable failure) {
            INSTALLED.set(false);
            TweakDiagnostics.rethrowFatal(failure);
            if(failure instanceof Exception)throw(Exception)failure;
            throw new IllegalStateException(failure);
        }
    }
}
