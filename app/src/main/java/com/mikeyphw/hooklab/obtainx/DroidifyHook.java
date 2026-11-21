package com.mikeyphw.hooklab.obtainx;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import com.mikeyphw.hooklab.core.HookContext;
import com.mikeyphw.hooklab.core.TweakDiagnostics;
import io.github.libxposed.api.XposedInterface;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Droid-ify 0.7.8/780 adapter ported from NeoStoreObtainX PR #12.
 * Native toolbar action only; no overlay, host repository writes or ObtainX hooks.
 */
public final class DroidifyHook {
    private DroidifyHook() { }
    private static final AtomicBoolean INSTALLED=new AtomicBoolean();
    private static final int ITEM_ID=0x4E534F58;
    private static final String FRAGMENT="com.looker.droidify.ui.appDetail.AppDetailFragment";
    private static final String TOOLBAR="com.google.android.material.appbar.MaterialToolbar";
    private static final String ROUTE="package_name", REPO_ROUTE="repo_address";

    /** Avoid XposedHelpers.callMethod on inherited/optimized Fragment getters. */
    private static Object inheritedGetter(Object object,String method) throws ReflectiveOperationException {
        if(object==null)return null;
        for(Method m:object.getClass().getMethods()) {
            if(m.getName().equals(method)&&m.getParameterCount()==0)return m.invoke(object);
        }
        return null;
    }
    private static Object member(Object object,String name) throws ReflectiveOperationException {
        if(object==null)return null;
        Class<?> cls=object.getClass();
        while(cls!=null){
            try {Field field=cls.getDeclaredField(name);field.setAccessible(true);return field.get(object);}
            catch(NoSuchFieldException missing){cls=cls.getSuperclass();}
        }
        return null;
    }
    private static Bundle arguments(Object fragment) {
        try {
            Object o=inheritedGetter(fragment,"getArguments");
            if(o instanceof Bundle)return (Bundle)o;
            o=member(fragment,"mArguments");
            return o instanceof Bundle?(Bundle)o:null;
        } catch(ReflectiveOperationException | RuntimeException e){return null;}
    }
    private static String route(Object fragment){
        Bundle b=arguments(fragment);
        return b==null?null:b.getString(ROUTE);
    }
    private static Activity host(View view) {
        Context ctx=view.getContext();
        Set<Context> seen=new HashSet<>();
        for(int n=0;ctx!=null&&n<16&&seen.add(ctx);n++) {
            if(ctx instanceof Activity)return (Activity)ctx;
            if(!(ctx instanceof ContextWrapper))return null;
            ctx=((ContextWrapper)ctx).getBaseContext();
        }
        return null;
    }
    private static View toolbar(View root){
        ArrayDeque<View> queue=new ArrayDeque<>();queue.add(root);
        for(int n=0;!queue.isEmpty()&&n<200;n++) {
            View v=queue.removeFirst();
            for(Class<?> type=v.getClass();type!=null;type=type.getSuperclass())
                if(TOOLBAR.equals(type.getName()))return v;
            if(v instanceof ViewGroup){
                ViewGroup group=(ViewGroup)v;
                for(int i=0;i<Math.min(50,group.getChildCount());i++)queue.addLast(group.getChildAt(i));
            }
        }
        return null;
    }
    private static String string(Object obj,String name) throws ReflectiveOperationException {
        Object v=member(obj,name);return v instanceof String?(String)v:null;
    }
    private static Long number(Object obj,String name) throws ReflectiveOperationException {
        Object v=member(obj,name);return v instanceof Number?((Number)v).longValue():null;
    }
    private static SourcePolicy.Snapshot fresh(Object fragment,String bound) throws ReflectiveOperationException {
        Object raw=member(fragment,"products");
        if(!(raw instanceof List))return null;
        List<?> pairs=(List<?>)raw;
        if(pairs.isEmpty()||pairs.get(0)==null)return null;
        Object pair=pairs.get(0);
        Object product=inheritedGetter(pair,"getFirst");
        if(product==null)product=member(pair,"first");
        Object repo=inheritedGetter(pair,"getSecond");
        if(repo==null)repo=member(pair,"second");
        if(product==null||repo==null)return null;
        String auth=string(repo,"authentication");
        if(auth!=null&&!auth.isEmpty())return null;
        String address=string(repo,"address");
        Bundle args=arguments(fragment);
        if(args==null)return null;
        String boundRepo=args.getString(REPO_ROUTE);
        if(boundRepo!=null&&!boundRepo.isEmpty()&&!boundRepo.equals(address))return null;
        String src=string(product,"source");
        if(src==null||src.isEmpty())src=string(product,"web");
        return SourcePolicy.ready(bound,bound,string(product,"packageName"),
            number(product,"repositoryId"),number(repo,"id"),address,
            auth==null?"":auth,src);
    }
    private static void note(XposedInterface xp,String text){
        xp.log(Log.INFO,"HookLabObtainX","DROIDIFY="+text);
    }
    private static void tapped(Object fragment,WeakReference<View> viewRef,
                               WeakReference<Activity> hostRef,String packageBound,XposedInterface xp) {
        Activity activity=hostRef.get();
        View page=viewRef.get();
        if(activity==null||page==null||!NeoStoreAction.visible(activity)||
            !page.isAttachedToWindow()||!page.isShown())return;
        try {
            if(!Boolean.TRUE.equals(inheritedGetter(fragment,"isResumed")) ||
                inheritedGetter(fragment,"getView")!=page ||
                !packageBound.equals(route(fragment)))return;
            SourcePolicy.Snapshot live=fresh(fragment,packageBound);
            if(live==null){
                Toast.makeText(activity,"ObtainX source not ready or unsupported",Toast.LENGTH_SHORT).show();
                note(xp,"SOURCE_NOT_READY");return;
            }
            ObtainXBridge.Outcome result=ObtainXBridge.launch(activity,live,route(fragment));
            if(result!=ObtainXBridge.Outcome.DISPATCHED_FOR_PREVIEW)
                Toast.makeText(activity,"ObtainX preview unavailable",Toast.LENGTH_SHORT).show();
            note(xp,result.name());
        } catch(ReflectiveOperationException | RuntimeException | LinkageError e) {
            note(xp,"TAP_REFUSED_"+e.getClass().getSimpleName());
            Toast.makeText(activity,"ObtainX action unavailable",Toast.LENGTH_SHORT).show();
        }
    }
    private static void attach(Object fragment,View view,XposedInterface xp) {
        String stage="VIEW";
        try {
            if(view==null)return;
            stage="HOST_ACTIVITY";
            Activity activity=host(view);
            if(activity==null){note(xp,"ATTACH_REFUSED_ACTIVITY_CONTEXT");return;}
            stage="ROUTE_ARGUMENTS";
            String pkg=route(fragment);
            if(!SourcePolicy.packageId(pkg)){note(xp,"ATTACH_REFUSED_ROUTE_UNAVAILABLE");return;}
            stage="NATIVE_TOOLBAR";
            View bar=toolbar(view);
            if(bar==null){note(xp,"NATIVE_TOOLBAR_MISSING");return;}
            stage="TOOLBAR_MENU";
            Object raw=inheritedGetter(bar,"getMenu");
            if(!(raw instanceof Menu)){note(xp,"ATTACH_REFUSED_TOOLBAR_MENU");return;}
            Menu menu=(Menu)raw;
            if(menu.findItem(ITEM_ID)!=null)return;
            stage="MENU_ADD";
            MenuItem item=menu.add(0,ITEM_ID,0,"Add to ObtainX");
            stage="ICON_AND_LISTENER";
            try {
                Drawable image=activity.getPackageManager().getApplicationIcon(ObtainXIds.OBTAIN_X).mutate();
                item.setIcon(image);
            } catch(PackageManager.NameNotFoundException | SecurityException absent) {
                item.setIcon(android.R.drawable.stat_sys_download_done);
            }
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            item.setContentDescription("Add to ObtainX; import preview only");
            WeakReference<Object> weakFragment=new WeakReference<>(fragment);
            WeakReference<View> weakView=new WeakReference<>(view);
            WeakReference<Activity> weakHost=new WeakReference<>(activity);
            item.setOnMenuItemClickListener(pressed->{
                Object live=weakFragment.get();
                if(live!=null)tapped(live,weakView,weakHost,pkg,xp);
                return true;
            });
            note(xp,"NATIVE_TOOLBAR_ACTION_ADDED");
        } catch(Throwable error){
            TweakDiagnostics.rethrowFatal(error);
            note(xp,"ATTACH_REFUSED_"+stage+"_"+error.getClass().getSimpleName());
        }
    }
    public static void install(HookContext ctx) throws Exception {
        if(!ObtainXIds.DROIDIFY.equals(ctx.packageName)||ctx.versionCode!=ObtainXIds.DROIDIFY_CODE)
            throw new SecurityException("Unverified Droid-ify host");
        // Require versionName as well as the verified versionCode; never hook a
        // different host build that happens to reuse the same numeric code.
        android.content.Context hostContext=ctx.appContext(android.content.Context.class);
        android.content.pm.PackageInfo pkgInfo=hostContext.getPackageManager()
            .getPackageInfo(ObtainXIds.DROIDIFY,0);
        if(!ObtainXIds.DROIDIFY_VERSION.equals(pkgInfo.versionName))
            throw new SecurityException("Pinned host versionName mismatch");
        if(!INSTALLED.compareAndSet(false,true))return;
        XposedInterface xp=ctx.framework(XposedInterface.class);
        try {
            Class<?> fragment=Class.forName(FRAGMENT,false,ctx.classLoader);
            Method onView=fragment.getDeclaredMethod("onViewCreated",View.class,Bundle.class);
            xp.hook(onView).setId("hooklab.obtainx.droidify.view_created")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain->{
                    Object result=chain.proceed();
                    Object object=chain.getThisObject();
                    Object view=chain.getArg(0);
                    if(view instanceof View)attach(object,(View)view,xp);
                    return result;
                });
            note(xp,"SOURCE_PINNED_HOOK_INSTALLED");
        } catch(Throwable error){
            INSTALLED.set(false);
            TweakDiagnostics.rethrowFatal(error);
            if(error instanceof Exception)throw(Exception)error;
            throw new IllegalStateException(error);
        }
    }
}
