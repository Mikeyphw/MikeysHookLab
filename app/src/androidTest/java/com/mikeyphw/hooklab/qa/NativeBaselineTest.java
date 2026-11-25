package com.mikeyphw.hooklab.qa;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationBarView;
import com.mikeyphw.hooklab.HookLabApp;
import com.mikeyphw.hooklab.R;
import com.mikeyphw.hooklab.ui.MainActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;

/** Actual production Activity, ordinary disconnected emulator. No fixture backdoor. */
@androidx.test.filters.SdkSuppress(minSdkVersion = 36, maxSdkVersion = 36)
@RunWith(AndroidJUnit4.class)
public final class NativeBaselineTest {
    private static final int HOME=201, HOOKS=202, DIAGNOSTICS=203;
    private static <T extends View> T find(View v, Class<T> type) {
        if(type.isInstance(v)) return type.cast(v);
        if(v instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++) { T child=find(g.getChildAt(i),type); if(child!=null)return child; }
        }
        return null;
    }
    private static TextView label(View v, String expected) {
        Rect bounds=new Rect();
        if(v instanceof TextView && v.isShown() && v.getGlobalVisibleRect(bounds)
                && expected.equals(((TextView)v).getText().toString())) return (TextView)v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++){TextView t=label(g.getChildAt(i),expected);if(t!=null)return t;}}
        return null;
    }
    private static JSONObject semantic(View control, TextView text, float density) throws Exception {
        assertNotNull(control);assertNotNull(text);Rect r=new Rect();assertTrue(control.getGlobalVisibleRect(r));
        int c=text.getCurrentTextColor();float scaled=text.getResources().getDisplayMetrics().scaledDensity;
        return new JSONObject().put("label",text.getText().toString())
                .put("bounds",new JSONArray().put(r.left/density).put(r.top/density).put(r.width()/density).put(r.height()/density))
                .put("font_sp",text.getTextSize()/scaled)
                .put("color",new JSONArray().put((c>>16)&255).put((c>>8)&255).put(c&255));
    }
    /** Observe fixed resource-labelled controls only; no imported or private values. */
    private static void fixedLabels(View v, String expected, java.util.List<TextView> matches){
        if(!v.isShown())return;
        if(v instanceof TextView && expected.contentEquals(((TextView)v).getText()))matches.add((TextView)v);
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)fixedLabels(g.getChildAt(i),expected,matches);}
    }
    private static JSONObject bodyControl(View root,String expected,float density)throws Exception{
        java.util.List<TextView> matches=new java.util.ArrayList<>();fixedLabels(root,expected,matches);
        assertFalse("Missing fixed body control: "+expected,matches.isEmpty());
        JSONArray rows=new JSONArray();
        for(TextView t:matches){
            View control=t;
            // Row action may belong to the containing native button rather than its label.
            for(android.view.ViewParent parent=t.getParent();!control.isClickable()&&parent instanceof View;parent=parent.getParent()){
                View candidate=(View)parent;
                if(candidate instanceof android.widget.ScrollView)break;
                if(candidate.isClickable()){control=candidate;break;}
            }
            Rect b=new Rect();boolean visible=control.getGlobalVisibleRect(b);
            JSONObject row=new JSONObject().put("clickable",control.isClickable()).put("enabled",control.isEnabled()).put("visible",visible);
            row.put("geometry",visible?semantic(control,t,density):JSONObject.NULL);rows.put(row);
        }
        return new JSONObject().put("label",expected).put("instances",rows);
    }
    private static JSONObject bodyEvidence(View root,android.content.Context c,String route,float density)throws Exception{
        JSONObject result=new JSONObject();String[] keys;int[] ids;
        switch(route){
            case "home": case "connected-home": case "light-home": keys=new String[]{"scope","browse","diagnostics","readiness","recent","groups","hooks-count","apps-count"};ids=new int[]{R.string.r03_scope,R.string.ui06_browse_hooks,R.string.ui06_open_diagnostics,R.string.ui06_readiness,R.string.ui06_recent,R.string.r03_groups,R.string.r03_hooks,R.string.r03_apps};break;
            case "settings": keys=new String[]{"theme","dynamic","scope","verbose","privacy","about","help"};ids=new int[]{R.string.r03_theme,R.string.ui_dynamic_colors,R.string.r03_scope,R.string.verbose_lsposed_logs,R.string.r03_privacy,R.string.about,R.string.ui07_open_help};break;
            case "scope": keys=new String[]{"summary","request","remove"};ids=new int[]{R.string.ui07_scope_heading,R.string.scope_add,R.string.scope_remove};break;
            case "mcc": keys=new String[]{"inspect","confirm","root-inspect","status"};ids=new int[]{R.string.ui07_mcc_inspect,R.string.ui07_mcc_write,R.string.root_platform_inspect,R.string.r03_manual_state};break;
            case "about": keys=new String[]{"identity","repository","privacy","help"};ids=new int[]{R.string.ui07_about_identity,R.string.r03_repository,R.string.r03_privacy,R.string.ui07_open_help};break;
            case "help": keys=new String[]{"setup","hooks","logs","diagnostics","privacy","manual"};ids=new int[]{R.string.ui07_help_setup_title,R.string.ui07_open_hooks,R.string.ui07_help_logs_title,R.string.ui07_open_diagnostics,R.string.r03_privacy,R.string.ui07_help_mcc_title};break;
            case "privacy": keys=new String[]{"collection","retention","copy-export","framework-logs","diagnostics"};ids=new int[]{R.string.r03_collection,R.string.r03_retention,R.string.r03_copy_export,R.string.r03_framework_logs,R.string.ui07_open_diagnostics};break;
            case "hooks": case "by-app": keys=new String[]{"groups","by-app"};ids=new int[]{R.string.ui_groups,R.string.ui_by_app};break;
            case "group-detail": keys=new String[]{"hooks","scope","evidence"};ids=new int[]{R.string.ui05_group_hooks,R.string.ui05_scope_targets,R.string.dx03_inspect};break;
            case "hook-detail": keys=new String[]{"identity","preference","health","evidence"};ids=new int[]{R.string.ui05_hook_evidence,R.string.ui_tweak_setting,R.string.dx03_view_health,R.string.dx03_inspect};break;
            default:return result;
        }
        for(int i=0;i<keys.length;i++)result.put(keys[i],bodyControl(root,c.getString(ids[i]),density));
        return result;
    }
    private static JSONObject palette(android.content.Context c)throws Exception{
        int[] attrs={com.google.android.material.R.attr.colorSurface,com.google.android.material.R.attr.colorSurfaceContainer,com.google.android.material.R.attr.colorSurfaceVariant,com.google.android.material.R.attr.colorOnSurface,com.google.android.material.R.attr.colorOnSurfaceVariant,androidx.appcompat.R.attr.colorPrimary,com.google.android.material.R.attr.colorOnPrimary,com.google.android.material.R.attr.colorOutlineVariant,com.google.android.material.R.attr.colorError};
        String[] names={"bg","surface","surface2","ink","muted","accent","on","line","error"};JSONObject result=new JSONObject();
        for(int i=0;i<attrs.length;i++)result.put(names[i],String.format(java.util.Locale.ROOT,"#%06x",com.google.android.material.color.MaterialColors.getColor(c,attrs[i],0)&0xffffff));
        result.put("warn",String.format(java.util.Locale.ROOT,"#%06x",c.getColor(R.color.hooklab_warning)&0xffffff));return result;
    }
    private static void project(View v, JSONArray rows, float density) throws Exception {
        Rect b=new Rect();
        if(!v.isShown() || !v.getGlobalVisibleRect(b))return;
        // Fresh offline fixture only. No text values or descriptions in exported
        // hierarchy; route/toolbar titles are asserted against resource IDs.
        JSONObject row=new JSONObject();
        String type=v.getClass().getSimpleName();row.put("class",type.isEmpty()?"AnonymousView":type);
        row.put("clickable",v.isClickable());row.put("enabled",v.isEnabled());
        row.put("focusable",v.isFocusable());row.put("heading",v.isAccessibilityHeading());
        row.put("bounds_px",new JSONArray().put(b.left).put(b.top).put(b.right).put(b.bottom));
        row.put("width_dp",v.getWidth()/density);row.put("height_dp",v.getHeight()/density);
        if(v instanceof TextView){TextView t=(TextView)v;row.put("text_size_px",t.getTextSize());row.put("text_color",t.getCurrentTextColor());}
        rows.put(row);
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)project(g.getChildAt(i),rows,density);}
    }
    // Compare only the measured app viewport, never compare against the other run
    // while settling. Capture a stable full screenshot or fail closed.
    private static final class SettledCapture {
        final Bitmap screenshot; final JSONObject geometry;
        SettledCapture(Bitmap screenshot,JSONObject geometry){this.screenshot=screenshot;this.geometry=geometry;}
    }
    // Force each sample to observe a newly submitted hardware-rendered frame,
    // not repeated readback of one retained surface buffer. No pixel editing.
    private static void awaitCommittedFrame(ActivityScenario<? extends androidx.appcompat.app.AppCompatActivity> scenario) throws Exception {
        java.util.concurrent.CountDownLatch committed=new java.util.concurrent.CountDownLatch(1);
        scenario.onActivity(a->{
            View decor=a.getWindow().getDecorView();
            assertTrue("Hardware rendering required",decor.isHardwareAccelerated());
            android.view.ViewTreeObserver observer=decor.getViewTreeObserver();
            assertTrue(observer.isAlive());
            observer.registerFrameCommitCallback(committed::countDown);
            decor.invalidate();
        });
        assertTrue("Fresh frame was not committed",committed.await(2,java.util.concurrent.TimeUnit.SECONDS));
        // Frame commit signals submission, not display presentation. Give the
        // compositor time to latch the frame before the ordinary screenshot.
        Thread.sleep(100);
    }
    private static SettledCapture settledScreenshot(ActivityScenario<? extends androidx.appcompat.app.AppCompatActivity> scenario,String name,int menu,int title) throws Exception {
        var ins=InstrumentationRegistry.getInstrumentation();
        final long[] fade={0};
        scenario.onActivity(a->{
            android.widget.ScrollView scroll=find(a.getWindow().getDecorView(),android.widget.ScrollView.class);
            if(scroll!=null&&scroll.isVerticalScrollBarEnabled()&&scroll.isScrollbarFadingEnabled())
                fade[0]=(long)scroll.getScrollBarDefaultDelayBeforeFade()+scroll.getScrollBarFadeDuration();
        });
        Thread.sleep(Math.min(3000,Math.max(250,fade[0]+150)));
        long deadline=android.os.SystemClock.elapsedRealtime()+6000;
        Bitmap previous=null;String previousGeometry=null;int consecutive=0;
        try{
            do{
                awaitCommittedFrame(scenario);
                ins.waitForIdleSync();UiDevice.getInstance(ins).waitForIdle(1000);
                JSONObject before=captureGeometry(scenario,name,menu,title);
                JSONArray insets=before.getJSONArray("insets_px");int[] bounds=new int[4];for(int n=0;n<4;n++)bounds[n]=insets.getInt(n);
                Bitmap full=ins.getUiAutomation().takeScreenshot();assertNotNull(full);
                Bitmap cropped=null;
                try{
                    assertTrue(bounds[0]+bounds[2]<full.getWidth()&&bounds[1]+bounds[3]<full.getHeight());
                    cropped=Bitmap.createBitmap(full,bounds[0],bounds[1],full.getWidth()-bounds[0]-bounds[2],full.getHeight()-bounds[1]-bounds[3]);
                    if(cropped==full)cropped=full.copy(Bitmap.Config.ARGB_8888,false);
                    JSONObject after=captureGeometry(scenario,name,menu,title);
                    boolean coherent=before.toString().equals(after.toString());
                    consecutive=coherent?(previous!=null&&previous.sameAs(cropped)&&after.toString().equals(previousGeometry)?consecutive+1:1):0;
                    previousGeometry=coherent?after.toString():null;
                    if(previous!=null)previous.recycle();previous=cropped;cropped=null;
                    if(consecutive>=3){Bitmap result=full;full=null;return new SettledCapture(result,after);}
                }finally{if(full!=null)full.recycle();if(cropped!=null)cropped.recycle();}
                Thread.sleep(150);
            }while(android.os.SystemClock.elapsedRealtime()<deadline);
            throw new AssertionError("Viewport did not settle for three consecutive samples");
        }finally{if(previous!=null)previous.recycle();}
    }
    private static JSONObject captureGeometry(ActivityScenario<? extends androidx.appcompat.app.AppCompatActivity> scenario,String name,int menu,int title){
        final JSONObject[] geometry={null};
        scenario.onActivity(a->{try{
            assertEquals("com.mikeyphw.hooklab",a.getPackageName());
            assertEquals(name.equals("mcc")?com.mikeyphw.hooklab.mcc.MccControlActivity.class:MainActivity.class,a.getClass());
            assertNull(((HookLabApp)a.getApplication()).currentService());
            View decor=a.getWindow().getDecorView();
            MaterialToolbar toolbar=find(decor,MaterialToolbar.class);assertNotNull(toolbar);
            assertEquals(a.getString(title),String.valueOf(toolbar.getTitle()));
            NavigationBarView nav=find(decor,NavigationBarView.class);
            boolean manual=name.equals("mcc");
            boolean deep=manual||java.util.Set.of("group-detail","hook-detail","settings","scope","about","help","privacy").contains(name);
            if(manual){assertNull(nav);assertEquals(0,menu);}else{
                assertNotNull(nav);assertEquals(3,nav.getMenu().size());assertEquals(menu,nav.getSelectedItemId());assertEquals(!deep,nav.isShown());
            }
            if(deep){assertNotNull(toolbar.getNavigationIcon());assertEquals(a.getString(R.string.ui_back),String.valueOf(toolbar.getNavigationContentDescription()));}
            float density=a.getResources().getDisplayMetrics().density;
            JSONObject g=new JSONObject(); g.put("route",name);g.put("selected_menu",menu);g.put("real_activity",true);boolean fixture=java.util.Set.of("connected-home","settings","scope","about","help","privacy","light-home").contains(name);
            g.put("disconnected",!fixture);g.put("framework_fixture",fixture);g.put("density",density);
            g.put("build_source",a.getString(R.string.hooklab_build_source));
            String theme=(a.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES?"dark":"light";
            assertEquals(name.equals("light-home")?"light":"dark",theme);g.put("theme",theme);
            g.put("width_dp",a.getResources().getConfiguration().screenWidthDp);
            g.put("font_scale",a.getResources().getConfiguration().fontScale);
            WindowInsets wi=decor.getRootWindowInsets();assertNotNull(wi);
            android.graphics.Insets i=wi.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            g.put("insets_px",new JSONArray().put(i.left).put(i.top).put(i.right).put(i.bottom));
            JSONArray views=new JSONArray();project(decor,views,density);assertTrue(views.length()>10);g.put("views",views);
            JSONObject controls=new JSONObject();TextView titleView=label(toolbar,a.getString(title));
            controls.put("title",semantic(titleView,titleView,density));
            int[] ids={HOME,HOOKS,DIAGNOSTICS};String[] keys={"home","hooks","diagnostics"};
            if(!deep)for(int n=0;n<ids.length;n++){
                View item=nav.findViewById(ids[n]);assertNotNull(item);
                String nameLabel=String.valueOf(nav.getMenu().findItem(ids[n]).getTitle());
                controls.put(keys[n],semantic(item,label(item,nameLabel),density).put("selected",nav.getSelectedItemId()==ids[n]));
            }
            g.put("semantic_controls",controls);g.put("body_controls",bodyEvidence(decor,a,name,density));g.put("palette",palette(a));geometry[0]=g;
        }catch(Exception e){throw new AssertionError(e);}});
        assertNotNull(geometry[0]);
        return geometry[0];
    }
    public static void capture(ActivityScenario<? extends androidx.appcompat.app.AppCompatActivity> scenario, String name, int menu, int title) throws Exception {
        var ins=InstrumentationRegistry.getInstrumentation();
        ins.waitForIdleSync();UiDevice.getInstance(ins).waitForIdle();
        File folder=new File(ins.getTargetContext().getFilesDir(),"uxr00-native-smoke");
        assertTrue(folder.isDirectory()||folder.mkdirs());
        SettledCapture capture=settledScreenshot(scenario,name,menu,title);
        try{
            try(FileOutputStream f=new FileOutputStream(new File(folder,name+".json"))){f.write(capture.geometry.toString().getBytes(StandardCharsets.UTF_8));}
            try(FileOutputStream f=new FileOutputStream(new File(folder,name+".png"))){assertTrue(capture.screenshot.compress(Bitmap.CompressFormat.PNG,100,f));}
        }finally{capture.screenshot.recycle();}
    }
    private static boolean containsText(View view,String text){
        if(view instanceof TextView && ((TextView)view).getText().toString().contains(text))return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(containsText(group.getChildAt(i),text))return true;}
        return false;
    }
    @Test public void realActivityOfflineNavigation() throws Exception {
        String stage="PRECONDITIONS";
        try {
        assertEquals(36,android.os.Build.VERSION.SDK_INT);
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        var app=(HookLabApp)instrumentation.getTargetContext().getApplicationContext();
        assertNull(app.currentService());assertEquals(0,app.diagnostics().size());
        assertEquals("en",java.util.Locale.getDefault().getLanguage());
        assertTrue(app.getSharedPreferences("appearance",android.content.Context.MODE_PRIVATE).edit().clear()
                .putInt("night",androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
                .putBoolean("dynamicColors",false).commit());
        stage="MAIN_LAUNCH";
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            stage="DISCONNECTED_SETTLE";
            long until=android.os.SystemClock.elapsedRealtime()+12000;
            boolean settled=false;
            do{final boolean[] found={false};s.onActivity(a->found[0]=containsText(a.getWindow().getDecorView(),a.getString(R.string.status_disconnected)));settled=found[0];if(!settled)Thread.sleep(50);}while(!settled&&android.os.SystemClock.elapsedRealtime()<until);
            assertTrue("Disconnected framework did not settle",settled);
            stage="HOME_CAPTURE";capture(s,"home",HOME,R.string.app_name);
            stage="HOOKS_TAP";onView(withId(HOOKS)).perform(click());
            stage="R02_EXPAND";
            onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription(app.getString(R.string.r02_expand,app.getString(R.string.ui_group_obtainx)))).perform(androidx.test.espresso.action.ViewActions.scrollTo(),click());
            onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription(app.getString(R.string.r02_collapse,app.getString(R.string.ui_group_obtainx)))).check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
            stage="HOOKS_CAPTURE";capture(s,"hooks",HOOKS,R.string.ui_hooks);
            stage="R02_GROUP_CAPTURE";
            // Touch the actual parent action independently of its chevron, then return.
            onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription(org.hamcrest.Matchers.startsWith(app.getString(R.string.ui_group_obtainx)+", "))).perform(androidx.test.espresso.action.ViewActions.scrollTo(),new androidx.test.espresso.action.GeneralClickAction(androidx.test.espresso.action.Tap.SINGLE,androidx.test.espresso.action.GeneralLocation.CENTER_LEFT,androidx.test.espresso.action.Press.FINGER));
            androidx.test.espresso.Espresso.pressBack();
            onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.ui_group_obtainx)).perform(androidx.test.espresso.action.ViewActions.scrollTo(),click());
            capture(s,"group-detail",HOOKS,R.string.r02_group_details);
            stage="R02_HOOK_CAPTURE";
            String neo=com.mikeyphw.hooklab.core.TweakCatalog.entries().stream().filter(e->"neostore.obtainx.manual".equals(e.spec.id)).findFirst().orElseThrow().title;
            onView(androidx.test.espresso.matcher.ViewMatchers.withText(neo)).perform(androidx.test.espresso.action.ViewActions.scrollTo(),click());
            capture(s,"hook-detail",HOOKS,R.string.r02_hook_configuration);
            androidx.test.espresso.Espresso.pressBack();androidx.test.espresso.Espresso.pressBack();
            stage="R02_SINGLE_GROUP";
            onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.ui_group_wordmaster)).perform(androidx.test.espresso.action.ViewActions.scrollTo(),click());
            s.onActivity(a->{MaterialToolbar toolbar=find(a.getWindow().getDecorView(),MaterialToolbar.class);assertEquals(a.getString(R.string.r02_hook_configuration),String.valueOf(toolbar.getTitle()));assertTrue(containsText(a.getWindow().getDecorView(),"wordmaster.night.filter"));});
            androidx.test.espresso.Espresso.pressBack();
            stage="R02_BY_APP_CAPTURE";
            onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.ui_by_app)).perform(androidx.test.espresso.action.ViewActions.scrollTo(),click());
            capture(s,"by-app",HOOKS,R.string.ui_hooks);
            stage="DIAGNOSTICS_TAP";onView(withId(DIAGNOSTICS)).perform(click());
            stage="DIAGNOSTICS_CAPTURE";capture(s,"diagnostics",DIAGNOSTICS,R.string.diagnostics);
            stage="HOME_TAP";onView(withId(HOME)).perform(click());
            stage="HOME_RECREATE";s.recreate();s.onActivity(a->{NavigationBarView n=find(a.getWindow().getDecorView(),NavigationBarView.class);assertNotNull(n);assertEquals(HOME,n.getSelectedItemId());});
        }
        stage="R03_CONNECTED";
        com.mikeyphw.hooklab.ui.FrameworkFixtureTest.captureWholeApp();
        stage="R03_MCC";
        try(ActivityScenario<com.mikeyphw.hooklab.mcc.MccControlActivity> manual=ActivityScenario.launch(com.mikeyphw.hooklab.mcc.MccControlActivity.class)){
            assertEquals(com.mikeyphw.hooklab.mcc.MccManualOperation.Stage.IDLE,com.mikeyphw.hooklab.mcc.MccManualOperation.shared().snapshot().stage);
            capture(manual,"mcc",0,R.string.r03_manual_control);
            onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.ui07_mcc_write)).perform(androidx.test.espresso.action.ViewActions.scrollTo(),click());
            onView(androidx.test.espresso.matcher.ViewMatchers.withText(android.R.string.cancel)).perform(click());
            assertEquals(com.mikeyphw.hooklab.mcc.MccManualOperation.Stage.IDLE,com.mikeyphw.hooklab.mcc.MccManualOperation.shared().snapshot().stage);
        }
        // R05 settings-only route: preserve the real bridge without granting permission or executing anything.
        stage="SETTINGS_LAUNCH";
        android.content.Intent settings=new android.content.Intent(instrumentation.getTargetContext(),
                com.mikeyphw.hooklab.termux.TermuxBridgeActivity.class)
                .setAction(com.mikeyphw.hooklab.termux.TermuxCommandPolicy.ACTION_SETTINGS)
                .putExtra("synthetic_unused_payload", "FIXTURE_MUST_NOT_SURVIVE");
        try(ActivityScenario<com.mikeyphw.hooklab.termux.TermuxBridgeActivity> bridge=ActivityScenario.launch(settings)){
            stage="SETTINGS_ASSERTIONS";
            bridge.onActivity(a->{View decor=a.getWindow().getDecorView();
                // Regression for lifecycle identity without retaining any inbound payload.
                assertEquals(com.mikeyphw.hooklab.termux.TermuxCommandPolicy.ACTION_SETTINGS,a.getIntent().getAction());
                assertEquals(new android.content.ComponentName(a,com.mikeyphw.hooklab.termux.TermuxBridgeActivity.class),a.getIntent().getComponent());
                assertNull(a.getIntent().getExtras());assertNull(a.getIntent().getData());
                assertNull(a.getIntent().getClipData());assertEquals(0,a.getIntent().getFlags());
                assertTrue(containsText(decor,a.getString(R.string.termux_r05_settings_intro)));
                assertFalse(containsText(decor,a.getString(R.string.termux_r05_review_run)));
            });
            stage="SETTINGS_CLOSE";
        }
        } catch (AssertionError | Exception failure) {
            // Stable synthetic marker: host exports only the allowlisted stage, exception
            // class and source locations, never the exception message or UI/log contents.
            if(failure instanceof AssertionError && failure.getMessage()!=null && failure.getMessage().startsWith("UXR00_STAGE_R03_"))throw (AssertionError)failure;
            throw new AssertionError("UXR00_STAGE_"+stage,failure);
        }
    }
}
