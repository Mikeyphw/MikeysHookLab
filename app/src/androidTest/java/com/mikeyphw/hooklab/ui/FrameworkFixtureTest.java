package com.mikeyphw.hooklab.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.mikeyphw.hooklab.HookLabApp;
import com.mikeyphw.hooklab.R;
import com.mikeyphw.hooklab.core.*;
import com.mikeyphw.hooklab.diagnostics.CaptureSession;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

/** Instrumentation-only fixture data. All commands go through the production store.
 * No real host, root, LSPosed, or runtime execution is used or qualified. */
@RunWith(AndroidJUnit4.class)
public final class FrameworkFixtureTest {
    private static final String NEO="neostore.obtainx.manual", DROID="droidify.obtainx.manual";
    private static final String PKG="com.machiav3lli.fdroid";
    private static final String[] ASSERTIONS={"connected_actual_activity","confirmed_write_readback","denied_write_no_optimism","scope_denial","stale_callback_rejected","scope_approval_readback","same_package_independent_hooks","injected_clock_capture_marker","capture_restart_same_route","health_exact_same_package_routes","health_navigation_no_writes","filtered_multi_app_route","catalog_recreate_and_return","catalog_navigation_no_writes","installed_package_icon_identity","missing_and_unknown_icon_states","no_real_framework"};
    private static final class FakePort implements UiEnvironment.Port {
        final SharedPreferences flags,safety;
        final Set<String> scope=ConcurrentHashMap.newKeySet();
        final AtomicReference<UiEnvironment.ScopeListener> pending=new AtomicReference<>();
        final AtomicInteger writes=new AtomicInteger();
        final AtomicInteger scopeCalls=new AtomicInteger();
        volatile boolean denyWrite;
        volatile String requested;
        FakePort(HookLabApp app,String name){
            flags=app.getSharedPreferences("uxr00.fixture."+name+".flags",Context.MODE_PRIVATE);
            safety=app.getSharedPreferences("uxr00.fixture."+name+".safety",Context.MODE_PRIVATE);
            assertTrue(flags.edit().clear().commit());assertTrue(safety.edit().clear().commit());
        }
        @Override public int getApiVersion(){return 102;}
        @Override public String getFrameworkName(){return "Synthetic QA framework";}
        @Override public String getFrameworkVersion(){return "0-fixture";}
        @Override public Collection<String> getScope(){return new ArrayList<>(scope);}
        private static Object call(Object instance,java.lang.reflect.Method method,Object[] args)throws Throwable{
            try{return method.invoke(instance,args);}catch(InvocationTargetException e){throw e.getCause();}
        }
        @Override public SharedPreferences getRemotePreferences(String group){
            SharedPreferences backing=TweakKeys.GROUP.equals(group)?flags:safety;
            return (SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,m,args)->{
                if(!m.getName().equals("edit"))return call(backing,m,args);
                writes.incrementAndGet();
                if(!denyWrite)return backing.edit();
                // Denial fails the actual production commit/readback path without changing backing data.
                return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(editor,em,ea)->{
                    if(em.getName().equals("commit"))return false;
                    if(em.getReturnType()==SharedPreferences.Editor.class)return editor;
                    if(em.getName().equals("apply"))throw new AssertionError("No optimistic apply");
                    throw new AssertionError("Unexpected fixture editor command "+em.getName());
                });
            });
        }
        @Override public void requestScope(List<String> packages,UiEnvironment.ScopeListener cb){
            scopeCalls.incrementAndGet();assertEquals(Collections.singletonList(PKG),packages);requested=packages.get(0);
            assertTrue(pending.compareAndSet(null,cb));
        }
        @Override public void removeScope(List<String> packages){scopeCalls.incrementAndGet();scope.removeAll(packages);}
        void answer(boolean approved){
            UiEnvironment.ScopeListener cb=pending.getAndSet(null);assertNotNull(cb);
            if(approved){scope.add(requested);cb.onScopeRequestApproved(Collections.singletonList(requested));}
            else cb.onScopeRequestFailed("synthetic denial");
        }
    }
    private static final class FixtureEnvironment extends UiEnvironment {
        volatile FakePort port;
        volatile long elapsed=100000L,wall=1760000000000L;
        final List<TweakEngine.Entry<HookContext>> catalog;
        FixtureEnvironment(HookLabApp app,FakePort initial){
            super(app);port=initial;
            List<TweakEngine.Entry<HookContext>> rows=new ArrayList<>();
            for(TweakEngine.Entry<HookContext> e:TweakCatalog.entries()){
                if(NEO.equals(e.spec.id)||DROID.equals(e.spec.id)){
                    // Two existing reviewed IDs, same synthetic host for route testing only.
                    // No installer from this fixture may run.
                    TweakSpec spec=new TweakSpec(e.spec.id,PKG,e.spec.minVersion,e.spec.maxVersion);
                    rows.add(new TweakEngine.Entry<>(spec,e.title,e.description,c->{throw new AssertionError("fixture installer forbidden");}));
                }
            }
            catalog=TweakEngine.immutableCatalog(rows);assertEquals(2,catalog.size());
        }
        @Override Port currentService(){return port;}
        @Override List<TweakEngine.Entry<HookContext>> entries(){return catalog;}
        @Override Long installedVersion(TweakSpec spec){return spec.minVersion;}
        @Override long elapsedRealtime(){return elapsed;}
        @Override long wallTime(){return wall;}
    }
    private static View find(View root,Predicate<View> p){
        if(root.isShown()&&p.test(root))return root;
        if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){View v=find(g.getChildAt(i),p);if(v!=null)return v;}}
        return null;
    }
    private static int count(View v,Predicate<View> p){
        int result=v.isShown()&&p.test(v)?1:0;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)result+=count(g.getChildAt(i),p);}
        return result;
    }
    private static void await(ActivityScenario<MainActivity> s,Predicate<View> condition)throws Exception{
        long until=android.os.SystemClock.elapsedRealtime()+12000;
        do {final boolean[] ok={false};s.onActivity(a->ok[0]=find(a.getWindow().getDecorView(),condition)!=null);if(ok[0])return;Thread.sleep(50);}while(android.os.SystemClock.elapsedRealtime()<until);
        fail("Production view condition did not settle");
    }
    private static void awaitPending(FakePort port)throws Exception{
        long until=android.os.SystemClock.elapsedRealtime()+10000;
        while(port.pending.get()==null&&android.os.SystemClock.elapsedRealtime()<until)Thread.sleep(50);
        assertNotNull(port.pending.get());
    }
    private static void switchState(ActivityScenario<MainActivity> s,boolean checked)throws Exception{
        await(s,v->v instanceof MaterialSwitch&&((MaterialSwitch)v).isChecked()==checked&&v.isEnabled());
    }
    private static void readyText(ActivityScenario<MainActivity> s,HookLabApp app,int id)throws Exception{
        String label=app.getString(id);await(s,v->v instanceof TextView&&label.contentEquals(((TextView)v).getText())&&v.isEnabled());
    }
    private static android.graphics.Bitmap iconPixels(android.graphics.drawable.Drawable drawable){
        android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(64,64,android.graphics.Bitmap.Config.ARGB_8888);
        drawable.setBounds(0,0,64,64);drawable.draw(new android.graphics.Canvas(image));return image;
    }
    private static void press(int id){onView(withText(id)).perform(scrollTo(),click());}
    @Test public void connectedCommandsStaleCallbackAndClock()throws Exception{
        String stage="FRAMEWORK_SETUP";
        try {
        HookLabApp app=(HookLabApp)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        assertNull(app.currentService());
        FakePort first=new FakePort(app,"first"),replacement=new FakePort(app,"replacement");
        FixtureEnvironment env=new FixtureEnvironment(app,first);
        assertEquals(1,HookGroupCatalog.byPackage(env.all()).size());
        Intent intent=new Intent(app,MainActivity.class).setAction("com.mikeyphw.hooklab.action.OPEN_HOOK").putExtra("hook_id",NEO).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try(AutoCloseable lease=UiEnvironmentFactory.installInstrumentationFixture(env);ActivityScenario<MainActivity> s=ActivityScenario.launch(intent)){
            stage="FRAMEWORK_PREFERENCES";
            switchState(s,false);assertEquals(0,first.writes.get());
            press(R.string.ui_enable_tweak);switchState(s,true);
            assertTrue(first.flags.getBoolean(TweakKeys.enabled(NEO),false));assertFalse(first.flags.getBoolean(TweakKeys.enabled(DROID),false));
            int writes=first.writes.get();first.denyWrite=true;
            press(R.string.ui_enable_tweak);switchState(s,true);
            assertTrue(first.writes.get()>writes);assertTrue(first.flags.getBoolean(TweakKeys.enabled(NEO),false));first.denyWrite=false;
            stage="FRAMEWORK_SCOPE";
            readyText(s,app,R.string.scope_add);press(R.string.scope_add);awaitPending(first);first.answer(false);readyText(s,app,R.string.scope_add);assertTrue(first.scope.isEmpty());
            press(R.string.scope_add);awaitPending(first);env.port=replacement;first.answer(true);
            switchState(s,false);readyText(s,app,R.string.scope_add);assertTrue(replacement.scope.isEmpty());
            press(R.string.scope_add);awaitPending(replacement);replacement.answer(true);readyText(s,app,R.string.scope_remove);assertEquals(Collections.singleton(PKG),replacement.scope);
            stage="FRAMEWORK_GROUP";
            // Actual native group page uses the supplied presentation catalog.
            int beforeNavigation=replacement.writes.get(),beforeScope=replacement.scopeCalls.get();
            press(R.string.ui_group_obtainx);
            assertEquals(2,env.entries().size());assertEquals(1,HookGroupCatalog.groups(env.all()).get(0).packages.size());
            await(s,v->v instanceof TextView&&((TextView)v).getText().toString().contains(PKG));
            s.onActivity(a->{View root=a.getWindow().getDecorView();
                for(TweakEngine.Entry<HookContext> e:env.entries())assertEquals(1,count(root,v->v instanceof TextView&&e.title.contentEquals(((TextView)v).getText())));
                assertEquals(0,count(root,v->v instanceof TextView&&a.getString(R.string.ui05_open_hook).contentEquals(((TextView)v).getText())));
                assertEquals(1,count(root,v->v instanceof TextView&&a.getString(R.string.scope_remove).contentEquals(((TextView)v).getText())));});
            for(TweakEngine.Entry<HookContext> e:env.entries()){
                onView(withText(e.title)).perform(scrollTo(),click());
                s.onActivity(a->{View root=a.getWindow().getDecorView();assertEquals(1,count(root,v->v instanceof com.google.android.material.appbar.MaterialToolbar&&a.getString(R.string.r02_hook_configuration).contentEquals(((com.google.android.material.appbar.MaterialToolbar)v).getTitle())));assertEquals(1,count(root,v->v instanceof TextView&&e.title.contentEquals(((TextView)v).getText())));});
                androidx.test.espresso.Espresso.pressBack();
            }
            assertEquals(beforeNavigation,replacement.writes.get());assertEquals(beforeScope,replacement.scopeCalls.get());assertEquals(Collections.singleton(PKG),replacement.scope);assertNull(replacement.pending.get());
            stage="R02_ICON_PROVIDER";
            s.onActivity(a->{
                AppIconProvider icons=new AppIconProvider(a,Set.of(a.getPackageName(),PKG));
                AppIconProvider.Entry actual=icons.load(a.getPackageName());
                assertEquals(AppIconProvider.Availability.AVAILABLE,actual.availability);assertNotNull(actual.drawable);
                try{android.graphics.drawable.Drawable expected=a.getPackageManager().getApplicationIcon(a.getPackageName());
                    assertTrue(iconPixels(actual.drawable).sameAs(iconPixels(expected)));
                    AppIconProvider.Entry repeated=icons.load(a.getPackageName());assertNotSame(actual.drawable,repeated.drawable);
                    icons.invalidate(a.getPackageName());assertTrue(iconPixels(icons.load(a.getPackageName()).drawable).sameAs(iconPixels(expected)));
                }catch(android.content.pm.PackageManager.NameNotFoundException absent){throw new AssertionError(absent);}
                assertEquals(AppIconProvider.Availability.NOT_INSTALLED,icons.load(PKG).availability);
                assertEquals(AppIconProvider.Availability.UNKNOWN,icons.load("com.hooklab.unreviewed.fixture").availability);
            });
            stage="R02_CATALOG_ROUTES";
            androidx.test.espresso.Espresso.pressBack();
            onView(withId(202)).perform(click());
            press(R.string.ui_by_app);
            onView(withHint(R.string.ui_search_hooks)).perform(replaceText("Neo Store"),closeSoftKeyboard());
            onView(withContentDescription(org.hamcrest.Matchers.startsWith(PKG+", "))).perform(scrollTo(),click());
            s.onActivity(a->{View root=a.getWindow().getDecorView();for(TweakEngine.Entry<HookContext> e:env.entries())assertEquals(1,count(root,v->v instanceof TextView&&e.title.contentEquals(((TextView)v).getText())));});
            s.recreate();
            s.onActivity(a->{View root=a.getWindow().getDecorView();for(TweakEngine.Entry<HookContext> e:env.entries())assertEquals(1,count(root,v->v instanceof TextView&&e.title.contentEquals(((TextView)v).getText())));});
            androidx.test.espresso.Espresso.pressBack();
            onView(withHint(R.string.ui_search_hooks)).check(androidx.test.espresso.assertion.ViewAssertions.matches(withText("Neo Store")));
            assertEquals(beforeNavigation,replacement.writes.get());assertEquals(beforeScope,replacement.scopeCalls.get());assertEquals(Collections.singleton(PKG),replacement.scope);assertNull(replacement.pending.get());
            stage="HEALTH_EXACT_ROUTE";
            int beforeHealth=replacement.writes.get();
            onView(withId(203)).perform(click());press(R.string.r01_health);
            for(TweakEngine.Entry<HookContext> e:env.entries()){
                onView(withText(e.title)).perform(scrollTo(),click());
                // Only the selected exact hook owns the report and setup route.
                s.onActivity(a->{View root=a.getWindow().getDecorView();assertTrue(count(root,v->v instanceof TextView&&a.getString(R.string.dx03_open_setup).contentEquals(((TextView)v).getText()))==1);});
                press(R.string.dx03_open_setup);
                s.onActivity(a->{View root=a.getWindow().getDecorView();assertTrue(count(root,v->v instanceof TextView&&e.title.contentEquals(((TextView)v).getText()))>=1);});
                androidx.test.espresso.Espresso.pressBack();androidx.test.espresso.Espresso.pressBack();
            }
            assertEquals(beforeHealth,replacement.writes.get());assertNull(replacement.pending.get());
            stage="CAPTURE_START";
            press(R.string.dx02_capture);press(R.string.dx02_choose_target);
            TweakEngine.Entry<HookContext> neo=env.entries().stream().filter(e->NEO.equals(e.spec.id)).findFirst().orElseThrow();
            onView(withText(neo.title+" · "+PKG)).perform(click());onView(withText(R.string.dx01_module)).perform(click());
            assertNotNull(app.capture());assertEquals(NEO,app.capture().targetHookId);assertEquals(CaptureSession.State.ACTIVE,app.capture().state());
            stage="CAPTURE_ACTIVE_RENDER";
            // The screen is already CAPTURE; require the real active controls immediately
            // after the source dialog, rather than waiting for an unrelated framework refresh.
            onView(withText(R.string.dx02_mark_repro)).check(androidx.test.espresso.assertion.ViewAssertions.matches(isEnabled()));
            onView(withText(R.string.dx02_stop)).check(androidx.test.espresso.assertion.ViewAssertions.matches(isEnabled()));
            stage="CAPTURE_MARKER";
            env.elapsed+=5000;press(R.string.dx02_mark_repro);assertEquals(5000,app.capture().markers().get(0).elapsedMillis);
            stage="CAPTURE_STOP";
            env.elapsed+=2000;press(R.string.dx02_stop);assertEquals(CaptureSession.State.STOPPED,app.capture().state());assertEquals(7000,app.capture().elapsed(env.elapsed));
            CaptureSession previous=app.capture();
            stage="CAPTURE_RESTART";
            press(R.string.dx02_new);
            onView(withText(neo.title+" · "+PKG)).perform(click());onView(withText(R.string.dx01_module)).perform(click());
            assertNotSame(previous,app.capture());assertEquals(CaptureSession.State.ACTIVE,app.capture().state());
            assertEquals(0,app.capture().markersCount());
            onView(withText(R.string.dx02_mark_repro)).check(androidx.test.espresso.assertion.ViewAssertions.matches(isEnabled()));
            env.elapsed+=1000;press(R.string.dx02_mark_repro);assertEquals(1000,app.capture().markers().get(0).elapsedMillis);
            assertEquals(1,previous.markersCount());
            press(R.string.dx02_stop);assertEquals(CaptureSession.State.STOPPED,app.capture().state());
            assertEquals(1000,app.capture().elapsed(env.elapsed));
            assertNull(app.currentService());
        }
        stage="FRAMEWORK_RECEIPT";
        // No raw logs, credentials, package content, or runtime claims in receipt.
        JSONObject result=new JSONObject().put("schema","hooklab.framework.fixture.v1").put("synthetic",true).put("runtime_qualified",false).put("real_activity",true).put("assertions",new JSONArray(Arrays.asList(ASSERTIONS)));
        File folder=new File(app.getFilesDir(),"uxr00-native-smoke");assertTrue(folder.isDirectory()||folder.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(folder,"framework-fixture.json"))){out.write(result.toString().getBytes(StandardCharsets.UTF_8));}
        } catch(Throwable failure){throw new AssertionError("UXR00_STAGE_"+stage,failure);}
    }
}
