package com.mikeyphw.hooklab.qa;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.FrameMetrics;
import android.view.Window;
import android.widget.ScrollView;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
    private String phase="SETUP";
    private String activeTest="";
    private void phase(String value){
        phase=value;
        // The fixed phase survives exceptions whose text is suppressed by AndroidJUnitRunner.
        // Never persist raw exception messages, hierarchy text or device logcat.
        try{
            if(!folder.mkdirs()&&!folder.isDirectory())throw new java.io.IOException("phase directory unavailable");
            JSONObject record=new JSONObject().put("schema","hooklab.uxr04.phase.v1")
                .put("phase",phase).put("test",activeTest)
                .put("source",InstrumentationRegistry.getArguments().getString("uxr04_source",""));
            File tmp=new File(folder,"phase.tmp"), target=new File(folder,"phase.json");
            Files.write(tmp.toPath(),record.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception e){throw new IllegalStateException("ADAPTIVE_PHASE_WRITE_FAILED",e);}
    }
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
        phase("CAPTURE_"+name.toUpperCase(java.util.Locale.ROOT));
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
                    if(v instanceof com.google.android.material.button.MaterialButton||v instanceof com.google.android.material.materialswitch.MaterialSwitch||v instanceof com.google.android.material.chip.Chip
                        ||v instanceof androidx.appcompat.view.menu.ActionMenuItemView||v instanceof android.widget.ImageButton
                        ||v.getId()==201||v.getId()==202||v.getId()==203
                        ||(v.getTag() instanceof String&&((String)v.getTag()).startsWith("r04:action:"))){
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
    private void homeTargetGeometry(ActivityScenario<MainActivity> scenario)throws Exception {
        // Capture only safe numeric geometry and class/resource identities, not text.
        // Keep this even if the real accessibility framework subsequently refuses.
        JSONObject record=new JSONObject();
        record.put("schema","hooklab.uxr04.home-targets.v1").put("source",InstrumentationRegistry.getArguments().getString("uxr04_source", ""));
        JSONArray targets=new JSONArray();
        scenario.onActivity(a->{try{
            List<View> views=new ArrayList<>();walk(a.getWindow().getDecorView(),views);
            int minimum=Math.round(48*a.getResources().getDisplayMetrics().density);
            for(View v:views){
                if(!v.isClickable()||!v.isEnabled()||v.getImportantForAccessibility()==View.IMPORTANT_FOR_ACCESSIBILITY_NO||v.getImportantForAccessibility()==View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)continue;
                Rect visible=new Rect();if(!v.getGlobalVisibleRect(visible))continue;
                String kind=v.getClass().getName();if(!kind.matches("(?:android|androidx|com\\.android\\.internal|com\\.google\\.android\\.material|com\\.mikeyphw\\.hooklab)\\.[A-Za-z0-9_.$]{1,180}"))continue;
                if(targets.length()>=128)throw new IllegalStateException("home target inventory limit");
                targets.put(new JSONObject().put("id",v.getId()).put("class",kind).put("width_px",v.getWidth()).put("height_px",v.getHeight()).put("visible_width_px",visible.width()).put("visible_height_px",visible.height()).put("minimum_px",minimum));
            }
        }catch(org.json.JSONException e){throw new IllegalStateException(e);}});
        record.put("targets",targets);Files.write(new File(folder,"home-targets.json").toPath(),record.toString().getBytes(StandardCharsets.UTF_8));
    }
    private void prepareMatrixOrientation() throws Exception {
        android.os.Bundle args=InstrumentationRegistry.getArguments();
        int width=Integer.parseInt(args.getString("uxr04_width_px","0"));
        int height=Integer.parseInt(args.getString("uxr04_height_px","0"));
        assertTrue("valid matrix orientation",width>0&&height>0);
        // The launcher can insist on portrait even with a valid rotation lock.
        // Request the exact system rotation before launch, but measure the real
        // application display only after the production Activity is resumed.
        assertTrue("matrix rotation freeze refused",ins.getUiAutomation().setRotation(width>height
            ?android.app.UiAutomation.ROTATION_FREEZE_90:android.app.UiAutomation.ROTATION_FREEZE_0));
    }
    private void awaitInitialViewport(ActivityScenario<MainActivity> scenario)throws Exception {
        android.os.Bundle args=InstrumentationRegistry.getArguments();
        int width=Integer.parseInt(args.getString("uxr04_width_px","0")),height=Integer.parseInt(args.getString("uxr04_height_px","0"));
        float font=Float.parseFloat(args.getString("uxr04_font_scale","0"));
        assertTrue("valid matrix viewport",width>0&&height>0&&font>=1f);
        final int[] sample=new int[6];final float[] observedFont={0f};
        for(int i=0;i<50;i++){
            ins.waitForIdleSync();final boolean[] observed={false};
            scenario.onActivity(a->{android.content.res.Configuration c=a.getResources().getConfiguration();
                android.view.Display display=a.getWindowManager().getDefaultDisplay();
                android.graphics.Point real=new android.graphics.Point();display.getRealSize(real);
                sample[0]=real.x;sample[1]=real.y;sample[2]=display.getRotation();sample[3]=c.densityDpi;
                sample[4]=c.screenWidthDp;sample[5]=c.screenHeightDp;observedFont[0]=c.fontScale;
                observed[0]=real.x==width&&real.y==height&&display.getRotation()==(width>height?android.view.Surface.ROTATION_90:android.view.Surface.ROTATION_0)
                    &&c.orientation==(width>height?2:1)&&Math.abs(c.fontScale-font)<.01f
                    &&c.densityDpi==320&&c.screenWidthDp>=width/2-80&&c.screenWidthDp<=width/2
                    &&c.screenHeightDp>=height/2-100&&c.screenHeightDp<=height/2;
            });
            if(observed[0])return;
            // Bounded recovery of the same requested lock, not an orientation
            // fallback: the exact dimensions, rotation and screenshot still gate.
            if(i==10||i==25)prepareMatrixOrientation();
            SystemClock.sleep(200);
        }
        JSONObject observed=new JSONObject().put("schema","hooklab.uxr04.viewport-failure.v1")
            .put("source",args.getString("uxr04_source","")).put("test",activeTest)
            .put("width_px",sample[0]).put("height_px",sample[1]).put("rotation",sample[2])
            .put("density_dpi",sample[3]).put("width_dp",sample[4]).put("height_dp",sample[5]).put("font_scale",observedFont[0]);
        Files.write(new File(folder,"viewport-failure.json").toPath(),observed.toString().getBytes(StandardCharsets.UTF_8));
        fail("actual matrix viewport did not settle");
    }
    private View awaitPrimaryNavTarget(ActivityScenario<MainActivity> scenario) {
        final View[] previous={null,null};final Rect previousBounds=new Rect();int stable=0;
        for(int i=0;i<50;i++){
            ins.waitForIdleSync();final View[] observed={null,null};final Rect bounds=new Rect();
            scenario.onActivity(a->{
                View root=a.getWindow().getDecorView();NavigationBarView nav=root.findViewById(R.id.hooklab_primary_navigation);
                View item=nav==null?null:nav.findViewById(202);int min=Math.round(48*a.getResources().getDisplayMetrics().density);
                if(root.hasWindowFocus()&&item!=null&&item.isAttachedToWindow()&&item.isShown()&&item.isEnabled()&&item.isClickable()
                    &&item.getGlobalVisibleRect(bounds)&&bounds.width()>=min&&bounds.height()>=min){observed[0]=root;observed[1]=item;}
            });
            if(observed[0]!=null&&observed[0]==previous[0]&&observed[1]==previous[1]&&bounds.equals(previousBounds)){
                if(++stable>=2)return observed[0];
            }else stable=0;
            previous[0]=observed[0];previous[1]=observed[1];previousBounds.set(bounds);SystemClock.sleep(200);
        }
        throw new AssertionError("stable primary navigation target missing");
    }
    private static float[] visibleTouchCenter(View view){
        Rect visible=new Rect();if(!view.getLocalVisibleRect(visible)||visible.isEmpty())throw new AssertionError("navigation target not visible");
        int[] screen=new int[2];view.getLocationOnScreen(screen);
        return new float[]{screen[0]+visible.exactCenterX(),screen[1]+visible.exactCenterY()};
    }
    private View awaitCatalogRoot(ActivityScenario<MainActivity> scenario)throws Exception {
        // A successful click or screenshot is not proof that the destination
        // Activity window is current. Wait for the real Hooks selection and a
        // laid-out, attached search field, then bind Espresso to that decor.
        // Never perform navigation, focus, text entry or IME actions here.
        final boolean[] sample=new boolean[5];
        for(int i=0;i<50;i++){
            ins.waitForIdleSync();final View[] observed={null};
            scenario.onActivity(a->{
                View root=a.getWindow().getDecorView();
                NavigationBarView nav=root.findViewById(R.id.hooklab_primary_navigation);
                MaterialToolbar toolbar=find(root,MaterialToolbar.class);
                View field=root.findViewById(R.id.hooklab_catalog_search);
                sample[0]=root.isAttachedToWindow()&&root.hasWindowFocus();
                sample[1]=nav!=null&&nav.getSelectedItemId()==202;
                sample[2]=toolbar!=null&&a.getString(R.string.ui_hooks).contentEquals(toolbar.getTitle()==null?"":toolbar.getTitle());
                sample[3]=field instanceof android.widget.EditText&&field.isAttachedToWindow()&&field.isShown();
                sample[4]=sample[3]&&field.isEnabled()&&field.getWidth()>0&&field.getHeight()>=Math.round(48*a.getResources().getDisplayMetrics().density);
                if(sample[0]&&sample[1]&&sample[2]&&sample[3]&&sample[4])observed[0]=root;
            });
            if(observed[0]!=null)return observed[0];
            SystemClock.sleep(200);
        }
        // Preserve only source-bound booleans. Raw view dumps never leave the test.
        JSONObject evidence=new JSONObject().put("schema","hooklab.uxr04.catalog-readiness.v1")
            .put("source",InstrumentationRegistry.getArguments().getString("uxr04_source",""))
            .put("test",activeTest).put("phase",phase).put("window_focused",sample[0])
            .put("hooks_selected",sample[1]).put("hooks_title",sample[2])
            .put("search_attached",sample[3]).put("search_laid_out",sample[4]);
        Files.write(new File(folder,"catalog-readiness.json").toPath(),evidence.toString().getBytes(StandardCharsets.UTF_8));
        fail("Hooks catalog readiness missing");
        throw new AssertionError("unreachable");
    }
    // Do not use ActivityScenario.onActivity while the old Activity is being
    // destroyed. Observe the actual resumed Activity and its focused window.
    private void awaitRotatedSearch(int rotation,String query)throws Exception {
        android.os.Bundle args=InstrumentationRegistry.getArguments();
        int w=Integer.parseInt(args.getString("uxr04_width_px","0")),h=Integer.parseInt(args.getString("uxr04_height_px","0"));
        int expectedWidth=rotation==android.view.Surface.ROTATION_90?Math.max(w,h):Math.min(w,h);
        int expectedHeight=rotation==android.view.Surface.ROTATION_90?Math.min(w,h):Math.max(w,h);
        final View[] previous={null};int stable=0;
        for(int i=0;i<50;i++){
            final View[] observed={null};
            ins.runOnMainSync(()->{
                java.util.Collection<android.app.Activity> resumed=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED);
                if(resumed.size()!=1)return;
                android.app.Activity a=resumed.iterator().next();if(!(a instanceof MainActivity)||a.isFinishing()||a.isDestroyed())return;
                View root=a.getWindow().getDecorView();android.view.Display display=a.getWindowManager().getDefaultDisplay();android.graphics.Point size=new android.graphics.Point();display.getRealSize(size);
                android.widget.EditText field=root.findViewById(R.id.hooklab_catalog_search);NavigationBarView nav=root.findViewById(R.id.hooklab_primary_navigation);
                androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(root);Rect bounds=new Rect();
                if(root.isAttachedToWindow()&&root.hasWindowFocus()&&display.getRotation()==rotation&&size.x==expectedWidth&&size.y==expectedHeight
                    &&a.getResources().getConfiguration().orientation==(rotation==android.view.Surface.ROTATION_90?2:1)
                    &&field!=null&&field.isAttachedToWindow()&&field.isShown()&&field.hasFocus()&&query.contentEquals(field.getText())
                    &&field.getGlobalVisibleRect(bounds)&&bounds.height()>=Math.round(48*a.getResources().getDisplayMetrics().density)
                    &&insets!=null&&!insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())
                    &&nav!=null&&nav.getSelectedItemId()==202&&nav.isShown())observed[0]=root;
            });
            if(observed[0]!=null&&observed[0]==previous[0]){if(++stable>=2)return;}else stable=0;
            previous[0]=observed[0];SystemClock.sleep(200);
        }
        fail("rotated Activity, real display, search focus, text or IME not stably observed");
    }
    private int rotationSequence;
    private void requestSearchRotation(int rotation)throws Exception {
        // Do not mutate the runner's own UiAutomation connection while an
        // Activity is destroyed/recreated. Request the hosted shell mutation,
        // then independently observe the real resumed Activity below.
        String source=InstrumentationRegistry.getArguments().getString("uxr04_source","");
        String nonce=InstrumentationRegistry.getArguments().getString("uxr04_rotation_nonce","");
        assertTrue(source.matches("[0-9a-f]{40}")&&nonce.matches("[0-9a-f]{32}")&&rotationSequence<2);
        assertTrue(rotation==android.view.Surface.ROTATION_0||rotation==android.view.Surface.ROTATION_90);
        JSONObject request=new JSONObject().put("schema","hooklab.rotation-request.v1").put("source",source)
            .put("nonce",nonce).put("sequence",++rotationSequence).put("rotation",rotation).put("pid",android.os.Process.myPid());
        File tmp=new File(folder,"rotation-request.tmp"),target=new File(folder,"rotation-request.json");
        Files.write(tmp.toPath(),request.toString().getBytes(StandardCharsets.UTF_8));
        Files.move(tmp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    private View awaitFocusedActivityRoot(ActivityScenario<MainActivity> scenario){
        for(int i=0;i<50;i++){
            ins.waitForIdleSync();final View[] observed={null};
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();if(root.isAttachedToWindow()&&root.hasWindowFocus())observed[0]=root;});
            if(observed[0]!=null)return observed[0];SystemClock.sleep(200);
        }
        throw new AssertionError("Activity window focus not restored");
    }
    private void awaitSearch(ActivityScenario<MainActivity> scenario,boolean imeExpected,String query)throws Exception {
        // Insets and focus are asynchronous across real keyboard transitions.
        // Wait for all observable conditions, never manufacture focus in the test.
        for(int i=0;i<50;i++){
            ins.waitForIdleSync();final boolean[] observed={false};
            scenario.onActivity(a->{
                View root=a.getWindow().getDecorView();android.widget.EditText search=root.findViewById(R.id.hooklab_catalog_search);
                androidx.core.view.WindowInsetsCompat w=androidx.core.view.ViewCompat.getRootWindowInsets(root);
                NavigationBarView nav=find(root,NavigationBarView.class);
                if(search==null||w==null||nav==null)return;
                boolean rail=nav instanceof com.google.android.material.navigationrail.NavigationRailView;
                Rect bounds=new Rect();boolean visible=search.getGlobalVisibleRect(bounds)&&bounds.height()>=Math.round(48*a.getResources().getDisplayMetrics().density);
                observed[0]=search.hasFocus()&&query.contentEquals(search.getText())&&visible
                    &&w.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==imeExpected
                    &&nav.isShown()==(rail||!imeExpected);
            });
            if(observed[0])return;SystemClock.sleep(200);
        }
        fail("actual search focus, text, keyboard or navigation state not observed");
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
        activeTest="adaptiveJourney";
        try{phase("SETUP");runAdaptiveJourney();}
        catch(Exception|AssertionError failure){QaFailureEvidence.record(app,activeTest,phase,failure);throw new AssertionError("ADAPTIVE_PHASE_"+phase,failure);}
    }
    private void runAdaptiveJourney()throws Exception{
        androidx.test.espresso.accessibility.AccessibilityChecks.enable().setRunChecksFromRootView(true);
        assertTrue(Build.VERSION.SDK_INT==29||Build.VERSION.SDK_INT==36);assertNull(app.currentService());assertEquals(0,app.diagnostics().size());
        assertTrue(folder.mkdirs()||folder.isDirectory());
        String theme=InstrumentationRegistry.getArguments().getString("uxr04_theme","dark");
        int night=theme.equals("light")?AppCompatDelegate.MODE_NIGHT_NO:theme.equals("amoled")?100:theme.equals("system")?AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM:AppCompatDelegate.MODE_NIGHT_YES;
        assertTrue(app.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().clear().putInt("night",night).putBoolean("dynamicColors",theme.equals("dynamic")).commit());
        JSONArray records=new JSONArray();long started=SystemClock.elapsedRealtime();
        phase("LAUNCH_HOME");prepareMatrixOrientation();
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            long launch=SystemClock.elapsedRealtime()-started;
            phase("VIEWPORT_READY");awaitInitialViewport(s);
            phase("HOME_SCROLL");homeTargetGeometry(s);
            s.onActivity(a->{
                View root=a.getWindow().getDecorView();int minimum=Math.round(48*a.getResources().getDisplayMetrics().density);
                NavigationBarView nav=find(root,NavigationBarView.class);assertNotNull(nav);
                for(int i=0;i<nav.getMenu().size();i++){
                    View action=nav.findViewById(nav.getMenu().getItem(i).getItemId());assertNotNull(action);
                    Rect rect=new Rect();assertTrue(action.getGlobalVisibleRect(rect));
                    assertTrue("navigation visible target below 48dp",rect.width()>=minimum&&rect.height()>=minimum);
                }
                MaterialToolbar toolbar=find(root,MaterialToolbar.class);assertNotNull(toolbar);
                androidx.appcompat.widget.ActionMenuView menu=find(toolbar,androidx.appcompat.widget.ActionMenuView.class);assertNotNull(menu);
                for(int i=0;i<menu.getChildCount();i++){
                    View action=menu.getChildAt(i);if(!action.isShown()||!action.isEnabled()||!action.isClickable())continue;
                    Rect rect=new Rect();assertTrue(action.getGlobalVisibleRect(rect));
                    assertTrue("toolbar visible target below 48dp",rect.width()>=minimum&&rect.height()>=minimum);
                }
            });
            // The real shortcut row owns accessibility and the click target; its
            // decorative child text is deliberately hidden from AT. Scroll the
            // actual action, then assert it is exposed before capturing Home.
            onView(withTagValue(org.hamcrest.Matchers.is((Object)("r04:action:"+R.string.ui06_browse_hooks))))
                .perform(scrollTo()).check(matches(isDisplayingAtLeast(90)));
            phase("HOME_SHORTCUT_VISIBLE");
            s.onActivity(a->{View target=a.getWindow().getDecorView().findViewWithTag("r04:action:"+R.string.ui06_browse_hooks);
                assertNotNull(target);assertTrue(target.isEnabled()&&target.isClickable()&&target.isFocusable());
                assertTrue(target.getContentDescription()!=null&&target.getContentDescription().length()>0);
            });capture(s,"home",records);
            phase("OPEN_HOOKS");View navigationRoot=awaitPrimaryNavTarget(s);
            onView(org.hamcrest.Matchers.allOf(withId(202),isDescendantOfA(withId(R.id.hooklab_primary_navigation)),isDisplayed()))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.withDecorView(org.hamcrest.Matchers.is(navigationRoot)))
                .perform(new androidx.test.espresso.action.GeneralClickAction(androidx.test.espresso.action.Tap.SINGLE,AdaptiveUiTest::visibleTouchCenter,androidx.test.espresso.action.Press.FINGER));
            awaitCatalogRoot(s);capture(s,"hooks",records);
            phase("SEARCH_OPEN");View catalogRoot=awaitCatalogRoot(s);
            onView(withId(R.id.hooklab_catalog_search)).inRoot(androidx.test.espresso.matcher.RootMatchers.withDecorView(org.hamcrest.Matchers.is(catalogRoot))).perform(scrollTo(),click());
            awaitSearch(s,true,"");
            phase("SEARCH_TYPE");onView(withId(R.id.hooklab_catalog_search)).perform(replaceText("Neo"));awaitSearch(s,true,"Neo");
            phase("SEARCH_CLOSE_IME");onView(withId(R.id.hooklab_catalog_search)).perform(closeSoftKeyboard());awaitSearch(s,false,"Neo");
            phase("SEARCH_RECREATE");s.recreate();awaitSearch(s,false,"Neo");
            phase("SEARCH_ROTATE");final int[] rotation={0};s.onActivity(a->rotation[0]=a.getWindowManager().getDefaultDisplay().getRotation());
            assertTrue(rotation[0]==android.view.Surface.ROTATION_0||rotation[0]==android.view.Surface.ROTATION_90);
            int opposite=rotation[0]==android.view.Surface.ROTATION_0?android.view.Surface.ROTATION_90:android.view.Surface.ROTATION_0;
            phase("SEARCH_ROTATE_REQUEST");requestSearchRotation(opposite);
            phase("SEARCH_ROTATE_SETTLE");awaitRotatedSearch(opposite,"Neo");
            phase("SEARCH_ROTATE_RETURN");requestSearchRotation(rotation[0]);
            phase("SEARCH_ROTATE_RETURN_SETTLE");awaitRotatedSearch(rotation[0],"Neo");
            phase("SEARCH_CLEAR");onView(withId(R.id.hooklab_catalog_search)).perform(replaceText(""),closeSoftKeyboard());awaitSearch(s,false,"");
            phase("CATALOG_HIT_TARGETS");checkCatalogHitTargets(s);
            phase("OPEN_GROUP");onView(withTagValue(org.hamcrest.Matchers.is((Object)"r02:open:group.obtainx"))).perform(scrollTo(),click());capture(s,"group",records);
            phase("OPEN_HOOK");String hook=com.mikeyphw.hooklab.core.TweakCatalog.entries().stream().filter(e->"neostore.obtainx.manual".equals(e.spec.id)).findFirst().orElseThrow(IllegalStateException::new).title;
            onView(withText(hook)).perform(scrollTo(),click());capture(s,"hook",records);back();back();
            phase("OPEN_DIAGNOSTICS");onView(withId(203)).perform(click());capture(s,"diagnostics",records);
            phase("IMPORT_OPEN");View diagnosticsRoot=awaitFocusedActivityRoot(s);
            onView(withText(R.string.dx01_import)).inRoot(androidx.test.espresso.matcher.RootMatchers.withDecorView(org.hamcrest.Matchers.is(diagnosticsRoot))).perform(scrollTo(),click());
            phase("IMPORT_DIALOG_READY");onView(withText(R.string.dx01_choose_source)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(isDisplayed()));
            phase("IMPORT_CANCEL");onView(withId(R.id.hooklab_import_cancel)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(withText(android.R.string.cancel))).perform(scrollTo(),click());
            phase("IMPORT_DISMISSED");awaitFocusedActivityRoot(s);onView(withText(R.string.dx01_import)).check(matches(isDisplayed()));assertEquals(0,app.diagnostics().size());
            phase("OPEN_SETTINGS");onView(withId(100)).perform(click());capture(s,"settings",records);
            if(Build.VERSION.SDK_INT==29)onView(withText(R.string.r04_dynamic_unavailable)).perform(scrollTo()).check(matches(isDisplayed()));
            phase("THEME_CANCEL");tap(R.string.r03_theme);onView(withText(android.R.string.cancel)).perform(click());
            phase("SETTINGS_KEYBOARD_FOCUS");device.pressDPadDown();s.onActivity(a->{View target=a.getWindow().getDecorView().findViewWithTag("r04:action:"+R.string.r03_theme);assertNotNull(target);assertTrue(target.requestFocus());});
            s.recreate();ins.waitForIdleSync();s.onActivity(a->{View target=a.getWindow().getDecorView().findViewWithTag("r04:action:"+R.string.r03_theme);assertNotNull(target);assertTrue(target.hasFocus());});
            phase("OPEN_SCOPE");tap(R.string.r03_scope);capture(s,"scope",records);back();tap(R.string.about);capture(s,"about",records);
            phase("SOURCE_PROVENANCE");String expected=InstrumentationRegistry.getArguments().getString("uxr04_source","");assertTrue(expected.matches("[0-9a-f]{40}"));assertEquals(expected,app.getString(R.string.hooklab_build_source));
            phase("OPEN_HELP");tap(R.string.ui07_open_help);capture(s,"help",records);tap(R.string.r03_privacy);capture(s,"privacy",records);
            phase("PRIVACY_RECREATE");s.recreate();onView(withText(R.string.r03_privacy_heading)).check(matches(isDisplayed()));back();
            phase("MCC_CANCEL");tap(R.string.ui07_help_mcc_title);
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
    private JSONObject nativeTimelineFrames(ActivityScenario<MainActivity> scenario)throws Exception {
        final int requested=80, minimum=20;
        HandlerThread collector=new HandlerThread("hooklab-frame-metrics");collector.start();
        TreeMap<Long,Long> samples=new TreeMap<>();AtomicInteger dropped=new AtomicInteger();
        AtomicInteger moved=new AtomicInteger();AtomicReference<Throwable> failure=new AtomicReference<>();
        AtomicBoolean registered=new AtomicBoolean();
        CountDownLatch finished=new CountDownLatch(1);
        Window.OnFrameMetricsAvailableListener listener=(window,metrics,lost)->{
            dropped.addAndGet(lost);
            long duration=metrics.getMetric(FrameMetrics.TOTAL_DURATION),vsync=metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP);
            if(metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME)==0&&duration>0&&vsync>0){
                synchronized(samples){if(samples.size()<512)samples.putIfAbsent(vsync,duration);}
            }
        };
        try{
            scenario.onActivity(a->{
                View root=a.getWindow().getDecorView();assertTrue("hardware acceleration required",root.isHardwareAccelerated());
                ScrollView scroll=find(root,ScrollView.class);assertNotNull(scroll);
                int step=Math.max(1,Math.round(24*a.getResources().getDisplayMetrics().density));
                a.getWindow().addOnFrameMetricsAvailableListener(listener,new Handler(collector.getLooper()));registered.set(true);
                // Real scroll movement over the rendered 30-event timeline. No
                // sleep-derived timings, screenshot work, synthetic frame values,
                // or alternate UI surface is used.
                scroll.postOnAnimation(new Runnable(){int direction=1;int count=0;
                    @Override public void run(){try{
                        if(failure.get()!=null||count>=requested){finished.countDown();return;}
                        if(!scroll.canScrollVertically(direction))direction=-direction;
                        if(!scroll.canScrollVertically(direction))throw new AssertionError("timeline not scrollable");
                        int old=scroll.getScrollY();scroll.scrollBy(0,direction*step);
                        if(scroll.getScrollY()==old)throw new AssertionError("timeline scroll did not move");
                        moved.incrementAndGet();count++;scroll.postOnAnimation(this);
                    }catch(RuntimeException|AssertionError e){failure.compareAndSet(null,e);finished.countDown();}}
                });
            });
            assertTrue("native scroll workload timed out",finished.await(20,TimeUnit.SECONDS));
            if(failure.get()!=null)throw new AssertionError("native timeline workload failed",failure.get());
            assertEquals(requested,moved.get());
            // Frame delivery is asynchronous even after the final scroll action.
            for(int i=0;i<50;i++){synchronized(samples){if(samples.size()>=minimum)break;}SystemClock.sleep(100);}
        }finally{
            failure.compareAndSet(null,new IllegalStateException("collector stopped"));
            try{if(registered.get())scenario.onActivity(a->a.getWindow().removeOnFrameMetricsAvailableListener(listener));}
            finally{collector.quitSafely();collector.join(5000);assertFalse("frame collector did not stop",collector.isAlive());}
        }
        JSONArray rows=new JSONArray();synchronized(samples){
            assertTrue("insufficient completed native frame samples",samples.size()>=minimum);
            for(java.util.Map.Entry<Long,Long> entry:samples.entrySet())rows.put(new JSONObject().put("intended_vsync_ns",entry.getKey()).put("duration_ns",entry.getValue()));
        }
        return new JSONObject().put("schema","hooklab.uxr04.native-frames.v1")
            .put("workload","timeline_scroll_4000_events").put("requested_scroll_steps",requested)
            .put("completed_scroll_steps",moved.get()).put("dropped_callbacks",dropped.get()).put("samples",rows);
    }
    @Test public void boundedTimelinePerformance() throws Exception {
        activeTest="boundedTimelinePerformance";
        try{phase("SETUP");runBoundedTimelinePerformance();}
        catch(Exception|AssertionError failure){QaFailureEvidence.record(app,activeTest,phase,failure);throw new AssertionError("ADAPTIVE_PHASE_"+phase,failure);}
    }
    private void runBoundedTimelinePerformance() throws Exception {
        phase("PERF_IMPORT");assertNull(app.currentService());assertEquals(0,app.diagnostics().size());
        assertTrue(folder.mkdirs()||folder.isDirectory());
        StringBuilder fixture=new StringBuilder();for(int i=0;i<4001;i++)fixture.append("2026-11-23T21:42:08Z HookLabVerbose event=NEO_HOOK_INSTALLED\n");
        long start=SystemClock.elapsedRealtime();
        app.diagnostics().importStream(new java.io.ByteArrayInputStream(fixture.toString().getBytes(StandardCharsets.UTF_8)),"LSPOSED_MODULE");
        long importMs=SystemClock.elapsedRealtime()-start;assertEquals(4000,app.diagnostics().size());assertEquals(1,app.diagnostics().dropped());
        JSONArray durations=new JSONArray(),memory=new JSONArray();JSONObject nativeFrames;
        phase("PERF_LAUNCH");prepareMatrixOrientation();try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            phase("VIEWPORT_READY");awaitInitialViewport(s);
            onView(withId(203)).perform(click());
            phase("PERF_PAGE");for(int page=1;page<=5;page++){
                onView(withText(app.getString(R.string.r01_page,page,134))).perform(scrollTo()).check(matches(isDisplayed()));
                s.onActivity(a->{List<View> views=new ArrayList<>();walk(a.getWindow().getDecorView(),views);int count=0;
                    for(View v:views)if(v instanceof TextView&&"NEO_HOOK_INSTALLED".contentEquals(((TextView)v).getText()))count++;
                    assertEquals("bounded UI page",30,count);
                    memory.put(android.os.Debug.getPss());});
                long t=SystemClock.elapsedRealtime();tap(R.string.dx01_next);ins.waitForIdleSync();durations.put(SystemClock.elapsedRealtime()-t);
            }
            phase("PERF_NATIVE_FRAMES");nativeFrames=nativeTimelineFrames(s);
        }
        phase("PERF_WRITE");JSONObject result=new JSONObject().put("schema","hooklab.uxr04.performance.v2").put("events",4000).put("dropped",1).put("page_size",30).put("import_ms",importMs).put("page_action_ms",durations).put("sampled_pss_kb",memory).put("native_frames",nativeFrames).put("budget_enforced",false);
        Files.write(new File(folder,"performance.json").toPath(),result.toString().getBytes(StandardCharsets.UTF_8));
    }

}
