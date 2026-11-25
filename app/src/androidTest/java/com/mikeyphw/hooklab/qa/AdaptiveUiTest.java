package com.mikeyphw.hooklab.qa;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.navigation.NavigationBarView;
import com.mikeyphw.hooklab.HookLabApp;
import com.mikeyphw.hooklab.R;
import com.mikeyphw.hooklab.ui.MainActivity;
import com.mikeyphw.hooklab.ui.Ui08QualityPolicy;
import com.mikeyphw.hooklab.ui.UiLayoutPolicy;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static org.junit.Assert.*;

/** Fresh offline production activities. Fixture data never claims real LSPosed execution. */
@RunWith(AndroidJUnit4.class)
public final class AdaptiveUiTest {
    private static final String[] ROUTES={"home","hooks","group","hook","diagnostics","settings","scope","about","help","privacy","mcc"};
    private final android.app.Instrumentation ins=InstrumentationRegistry.getInstrumentation();
    private final HookLabApp app=(HookLabApp)ins.getTargetContext().getApplicationContext();
    private final UiDevice device=UiDevice.getInstance(ins);
    private final File folder=new File(app.getFilesDir(),"uxr04-adaptive");
    private static <T extends View> T find(View root,Class<T> type){
        if(type.isInstance(root))return type.cast(root);
        if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){T v=find(g.getChildAt(i),type);if(v!=null)return v;}}
        return null;
    }
    private static void walk(View root,List<View> result){
        if(!root.isShown())return;result.add(root);
        if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++)walk(g.getChildAt(i),result);}
    }
    private static void tap(int label){onView(withText(label)).perform(scrollTo(),click());}
    private static void back(){androidx.test.espresso.Espresso.pressBack();}
    private void palette(Context c,JSONObject row)throws Exception{
        int[] backgrounds={com.google.android.material.R.attr.colorSurface,com.google.android.material.R.attr.colorSurfaceContainer,com.google.android.material.R.attr.colorSurfaceVariant};
        int[] foregrounds={com.google.android.material.R.attr.colorOnSurface,com.google.android.material.R.attr.colorOnSurfaceVariant};
        double minimum=21;
        for(int bg:backgrounds)for(int fg:foregrounds){double value=Ui08QualityPolicy.contrast(MaterialColors.getColor(c,fg,0),MaterialColors.getColor(c,bg,0));minimum=Math.min(minimum,value);}
        assertTrue("resolved palette text contrast",minimum>=4.5);
        double primary=Ui08QualityPolicy.contrast(MaterialColors.getColor(c,com.google.android.material.R.attr.colorOnPrimary,0),MaterialColors.getColor(c,androidx.appcompat.R.attr.colorPrimary,0));
        assertTrue("resolved primary contrast",primary>=4.5);
        row.put("text_contrast_min",minimum).put("primary_contrast",primary);
    }
    private void capture(ActivityScenario<? extends androidx.appcompat.app.AppCompatActivity> scenario,String name,JSONArray records)throws Exception{
        ins.waitForIdleSync();JSONObject row=new JSONObject().put("route",name);
        scenario.onActivity(a->{try{
            View root=a.getWindow().getDecorView();List<View> views=new ArrayList<>();walk(root,views);
            float density=a.getResources().getDisplayMetrics().density;
            Rect window=new Rect();assertTrue(root.getGlobalVisibleRect(window));
            int headings=0,actions=0,focusable=0,visible=0;
            for(View v:views){
                if(v.isAccessibilityHeading())headings++;
                if(v.isFocusable())focusable++;
                if(v.isClickable()&&v.isEnabled()){
                    actions++;Rect bounds=new Rect();
                    if(v.getGlobalVisibleRect(bounds)){visible++;assertTrue(window.contains(bounds));}
                    // Explicit app controls, not framework-internal dialog descendants.
                    if(v instanceof com.google.android.material.button.MaterialButton||v instanceof com.google.android.material.materialswitch.MaterialSwitch||v instanceof com.google.android.material.chip.Chip){
                        assertTrue("minimum action width",v.getMeasuredWidth()>=Math.round(48*density));
                        assertTrue("minimum action height",v.getMeasuredHeight()>=Math.round(48*density));
                        AccessibilityNodeInfo node=v.createAccessibilityNodeInfo();assertTrue("action node enabled",node.isEnabled());node.recycle();
                    }
                }
                if(v instanceof TextView && !(v instanceof android.widget.EditText) && v.isAccessibilityHeading()){
                    TextView t=(TextView)v;
                    if(t.getLayout()!=null)for(int i=0;i<t.getLayout().getLineCount();i++)assertEquals("heading ellipsis",0,t.getLayout().getEllipsisCount(i));
                }
            }
            assertTrue("no accessible actions",actions>0&&focusable>0&&visible>0);
            assertTrue("missing headings",headings>0);
            row.put("width_dp",a.getResources().getConfiguration().screenWidthDp).put("height_dp",a.getResources().getConfiguration().screenHeightDp)
                .put("orientation",a.getResources().getConfiguration().orientation).put("font_scale",a.getResources().getConfiguration().fontScale).put("headings",headings).put("actions",actions).put("focusable",focusable).put("visible_actions",visible);
            palette(a,row);
            if(name.equals("home")||name.equals("hooks")||name.equals("diagnostics")){
                NavigationBarView nav=find(root,NavigationBarView.class);assertNotNull(nav);assertTrue(nav.isShown());assertEquals(3,nav.getMenu().size());
                assertEquals(UiLayoutPolicy.useRail(a.getResources().getConfiguration().screenWidthDp),nav instanceof com.google.android.material.navigationrail.NavigationRailView);
                row.put("rail",nav instanceof com.google.android.material.navigationrail.NavigationRailView);
            }
        }catch(Exception failure){throw new AssertionError("ADAPTIVE_GEOMETRY_"+name,failure);}});
        assertTrue(device.takeScreenshot(new File(folder,name+".png")));
        device.dumpWindowHierarchy(new File(folder,name+".xml"));records.put(row);
    }
    private void awaitOrientation(ActivityScenario<MainActivity> scenario,int wanted)throws Exception {
        for(int i=0;i<30;i++){
            ins.waitForIdleSync();final boolean[] observed={false};
            scenario.onActivity(a->observed[0]=a.getResources().getConfiguration().orientation==wanted);
            if(observed[0])return;SystemClock.sleep(200);
        }
        fail("actual Activity orientation did not change");
    }
    private void awaitIme(ActivityScenario<MainActivity> scenario)throws Exception {
        for(int i=0;i<30;i++){
            ins.waitForIdleSync();final boolean[] visible={false};
            scenario.onActivity(a->{androidx.core.view.WindowInsetsCompat w=androidx.core.view.ViewCompat.getRootWindowInsets(a.getWindow().getDecorView());visible[0]=w!=null&&w.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime());});
            if(visible[0])return;SystemClock.sleep(200);
        }
        fail("actual IME was not observed");
    }
    private void checkCatalogHitTargets(ActivityScenario<MainActivity> s){
        onView(withTagValue(org.hamcrest.Matchers.is((Object)"r02:open:group.obtainx"))).perform(scrollTo());
        s.onActivity(a->{View root=a.getWindow().getDecorView(),open=root.findViewWithTag("r02:open:group.obtainx"),expand=root.findViewWithTag("r02:expand:group.obtainx");
            assertNotNull(open);assertNotNull(expand);Rect o=new Rect(),e=new Rect();assertTrue(open.getGlobalVisibleRect(o));assertTrue(expand.getGlobalVisibleRect(e));
            int min=Math.round(48*a.getResources().getDisplayMetrics().density);assertTrue(o.width()>=min&&open.getMeasuredHeight()>=min&&e.width()>=min&&e.height()>=min);assertFalse(Rect.intersects(o,e));assertTrue(open.isFocusable()&&expand.isFocusable());
        });
        onView(withTagValue(org.hamcrest.Matchers.is((Object)"r02:expand:group.obtainx"))).perform(click());
        onView(withTagValue(org.hamcrest.Matchers.is((Object)"r02:open:group.obtainx"))).check(matches(isDisplayed()));
        onView(withTagValue(org.hamcrest.Matchers.is((Object)"r02:expand:group.obtainx"))).perform(click());
    }
    @Test public void adaptiveJourney()throws Exception{
        androidx.test.espresso.accessibility.AccessibilityChecks.enable().setRunChecksFromRootView(true);
        assertTrue(Build.VERSION.SDK_INT==29||Build.VERSION.SDK_INT==36);assertNull(app.currentService());assertEquals(0,app.diagnostics().size());
        assertTrue(folder.mkdirs()||folder.isDirectory());
        String theme=InstrumentationRegistry.getArguments().getString("uxr04_theme","dark");
        int night=theme.equals("light")?AppCompatDelegate.MODE_NIGHT_NO:theme.equals("amoled")?100:theme.equals("system")?AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM:AppCompatDelegate.MODE_NIGHT_YES;
        assertTrue(app.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().clear().putInt("night",night).putBoolean("dynamicColors",theme.equals("dynamic")).commit());
        JSONArray records=new JSONArray();long started=SystemClock.elapsedRealtime();
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            long launch=SystemClock.elapsedRealtime()-started;
            onView(withText(R.string.ui06_browse_hooks)).perform(scrollTo());capture(s,"home",records);
            onView(withId(202)).perform(click());capture(s,"hooks",records);
            onView(withHint(R.string.ui_search_hooks)).perform(click(),replaceText("Neo"));
            awaitIme(s);onView(withHint(R.string.ui_search_hooks)).perform(closeSoftKeyboard());
            onView(withHint(R.string.ui_search_hooks)).check(matches(hasFocus()));
            s.recreate();onView(withHint(R.string.ui_search_hooks)).check(matches(withText("Neo")));onView(withHint(R.string.ui_search_hooks)).check(matches(hasFocus()));
            final int[] orientation={0};s.onActivity(a->orientation[0]=a.getResources().getConfiguration().orientation);
            try {
                if(orientation[0]==1)device.setOrientationLeft();else device.setOrientationNatural();
                awaitOrientation(s,orientation[0]==1?2:1);
                onView(withHint(R.string.ui_search_hooks)).check(matches(withText("Neo")));
            } finally {
                if(orientation[0]==1)device.setOrientationNatural();else device.setOrientationLeft();
            }
            awaitOrientation(s,orientation[0]);onView(withHint(R.string.ui_search_hooks)).check(matches(withText("Neo")));
            onView(withHint(R.string.ui_search_hooks)).perform(replaceText(""),closeSoftKeyboard());
            checkCatalogHitTargets(s);
            onView(withTagValue(org.hamcrest.Matchers.is((Object)"r02:open:group.obtainx"))).perform(scrollTo(),click());capture(s,"group",records);
            String hook=com.mikeyphw.hooklab.core.TweakCatalog.entries().stream().filter(e->"neostore.obtainx.manual".equals(e.spec.id)).findFirst().orElseThrow(IllegalStateException::new).title;
            onView(withText(hook)).perform(scrollTo(),click());capture(s,"hook",records);back();back();
            onView(withId(203)).perform(click());capture(s,"diagnostics",records);
            tap(R.string.dx01_import);onView(withText(android.R.string.cancel)).perform(click());assertEquals(0,app.diagnostics().size());
            onView(withId(100)).perform(click());capture(s,"settings",records);
            if(Build.VERSION.SDK_INT==29)onView(withText(R.string.r04_dynamic_unavailable)).perform(scrollTo()).check(matches(isDisplayed()));
            tap(R.string.r03_theme);onView(withText(android.R.string.cancel)).perform(click());
            device.pressDPadDown();s.onActivity(a->{View target=a.getWindow().getDecorView().findViewWithTag("r04:action:"+R.string.r03_theme);assertNotNull(target);assertTrue(target.requestFocus());});
            s.recreate();ins.waitForIdleSync();s.onActivity(a->{View target=a.getWindow().getDecorView().findViewWithTag("r04:action:"+R.string.r03_theme);assertNotNull(target);assertTrue(target.hasFocus());});
            tap(R.string.r03_scope);capture(s,"scope",records);back();tap(R.string.about);capture(s,"about",records);
            String expected=InstrumentationRegistry.getArguments().getString("uxr04_source","");assertTrue(expected.matches("[0-9a-f]{40}"));assertEquals(expected,app.getString(R.string.hooklab_build_source));
            tap(R.string.ui07_open_help);capture(s,"help",records);tap(R.string.r03_privacy);capture(s,"privacy",records);
            s.recreate();onView(withText(R.string.r03_privacy_heading)).check(matches(isDisplayed()));back();
            tap(R.string.ui07_help_mcc_title);
            // Separate activity; do not inspect storage or request root in emulator.
            onView(withText(R.string.ui07_mcc_write)).perform(scrollTo(),click());onView(withText(android.R.string.cancel)).perform(click());
            onView(withText(R.string.ui07_mcc_idle)).perform(scrollTo()).check(matches(isDisplayed()));back();
            assertNull(app.currentService());assertEquals(0,app.diagnostics().size());
            // MCC activity is launched independently solely for its measured hierarchy.
            try(ActivityScenario<com.mikeyphw.hooklab.mcc.MccControlActivity> m=ActivityScenario.launch(com.mikeyphw.hooklab.mcc.MccControlActivity.class)){capture(m,"mcc",records);}
            assertEquals(ROUTES.length,records.length());
            JSONObject result=new JSONObject().put("schema","hooklab.uxr04.adaptive.v1").put("theme",theme).put("api",Build.VERSION.SDK_INT).put("source",expected).put("routes",records).put("ime_observed",true).put("orientation_round_trip",true).put("separate_catalog_targets",true).put("scenario_launch_ms",launch).put("framework_disconnected",true).put("physical_device_qualified",false);
            Files.write(new File(folder,"journey.json").toPath(),result.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    @Test public void boundedTimelinePerformance() throws Exception {
        assertNull(app.currentService());assertEquals(0,app.diagnostics().size());
        assertTrue(folder.mkdirs()||folder.isDirectory());
        StringBuilder fixture=new StringBuilder();for(int i=0;i<4001;i++)fixture.append("2026-11-23T21:42:08Z HookLabVerbose event=NEO_HOOK_INSTALLED\n");
        long start=SystemClock.elapsedRealtime();
        app.diagnostics().importStream(new java.io.ByteArrayInputStream(fixture.toString().getBytes(StandardCharsets.UTF_8)),"LSPOSED_MODULE");
        long importMs=SystemClock.elapsedRealtime()-start;assertEquals(4000,app.diagnostics().size());assertEquals(1,app.diagnostics().dropped());
        JSONArray durations=new JSONArray(),memory=new JSONArray();
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            onView(withId(203)).perform(click());
            for(int page=1;page<=5;page++){
                onView(withText(app.getString(R.string.r01_page,page,134))).perform(scrollTo()).check(matches(isDisplayed()));
                s.onActivity(a->{List<View> views=new ArrayList<>();walk(a.getWindow().getDecorView(),views);int count=0;
                    for(View v:views)if(v instanceof TextView&&"NEO_HOOK_INSTALLED".contentEquals(((TextView)v).getText()))count++;
                    assertEquals("bounded UI page",30,count);
                    memory.put(android.os.Debug.getPss());});
                long t=SystemClock.elapsedRealtime();tap(R.string.dx01_next);ins.waitForIdleSync();durations.put(SystemClock.elapsedRealtime()-t);
            }
        }
        JSONObject result=new JSONObject().put("schema","hooklab.uxr04.performance.v1").put("events",4000).put("dropped",1).put("page_size",30).put("import_ms",importMs).put("page_action_ms",durations).put("sampled_pss_kb",memory).put("budget_enforced",false);
        Files.write(new File(folder,"performance.json").toPath(),result.toString().getBytes(StandardCharsets.UTF_8));
    }

}
