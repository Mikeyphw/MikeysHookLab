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
    private static void capture(ActivityScenario<MainActivity> scenario, String name, int menu, int title) throws Exception {
        var ins=InstrumentationRegistry.getInstrumentation();
        ins.waitForIdleSync(); UiDevice.getInstance(ins).waitForIdle();
        File folder=new File(ins.getTargetContext().getFilesDir(),"uxr00-native-smoke");
        assertTrue(folder.isDirectory()||folder.mkdirs());
        final JSONObject[] geometry={null};
        scenario.onActivity(a->{try{
            assertEquals("com.mikeyphw.hooklab",a.getPackageName());
            assertEquals(MainActivity.class,a.getClass());
            assertNull(((HookLabApp)a.getApplication()).currentService());
            View decor=a.getWindow().getDecorView();
            MaterialToolbar toolbar=find(decor,MaterialToolbar.class);assertNotNull(toolbar);
            assertEquals(a.getString(title),String.valueOf(toolbar.getTitle()));
            NavigationBarView nav=find(decor,NavigationBarView.class);assertNotNull(nav);
            assertEquals(3,nav.getMenu().size());assertEquals(menu,nav.getSelectedItemId());
            float density=a.getResources().getDisplayMetrics().density;
            JSONObject g=new JSONObject(); g.put("route",name);g.put("selected_menu",menu);g.put("real_activity",true);g.put("disconnected",true);g.put("density",density);
            g.put("width_dp",a.getResources().getConfiguration().screenWidthDp);
            g.put("font_scale",a.getResources().getConfiguration().fontScale);
            WindowInsets wi=decor.getRootWindowInsets();assertNotNull(wi);
            android.graphics.Insets i=wi.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            g.put("insets_px",new JSONArray().put(i.left).put(i.top).put(i.right).put(i.bottom));
            JSONArray views=new JSONArray();project(decor,views,density);assertTrue(views.length()>10);g.put("views",views);
            JSONObject controls=new JSONObject();TextView titleView=label(toolbar,a.getString(title));
            controls.put("title",semantic(titleView,titleView,density));
            int[] ids={HOME,HOOKS,DIAGNOSTICS};String[] keys={"home","hooks","diagnostics"};
            for(int n=0;n<ids.length;n++){
                View item=nav.findViewById(ids[n]);assertNotNull(item);
                String nameLabel=String.valueOf(nav.getMenu().findItem(ids[n]).getTitle());
                controls.put(keys[n],semantic(item,label(item,nameLabel),density).put("selected",nav.getSelectedItemId()==ids[n]));
            }
            g.put("semantic_controls",controls);geometry[0]=g;
        }catch(Exception e){throw new AssertionError(e);}});
        assertNotNull(geometry[0]);
        try(FileOutputStream f=new FileOutputStream(new File(folder,name+".json"))){f.write(geometry[0].toString().getBytes(StandardCharsets.UTF_8));}
        Bitmap bitmap=ins.getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        try(FileOutputStream f=new FileOutputStream(new File(folder,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,f));}finally{bitmap.recycle();}
    }
    private static boolean containsText(View view,String text){
        if(view instanceof TextView && ((TextView)view).getText().toString().contains(text))return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(containsText(group.getChildAt(i),text))return true;}
        return false;
    }
    @Test public void realActivityOfflineNavigation() throws Exception {
        assertEquals(36,android.os.Build.VERSION.SDK_INT);
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        var app=(HookLabApp)instrumentation.getTargetContext().getApplicationContext();
        assertNull(app.currentService());assertEquals(0,app.diagnostics().size());
        assertEquals("en",java.util.Locale.getDefault().getLanguage());
        assertTrue(app.getSharedPreferences("appearance",android.content.Context.MODE_PRIVATE).edit().clear()
                .putInt("night",androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
                .putBoolean("dynamicColors",false).commit());
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            long until=android.os.SystemClock.elapsedRealtime()+12000;
            boolean settled=false;
            do{final boolean[] found={false};s.onActivity(a->found[0]=containsText(a.getWindow().getDecorView(),a.getString(R.string.status_disconnected)));settled=found[0];if(!settled)Thread.sleep(50);}while(!settled&&android.os.SystemClock.elapsedRealtime()<until);
            assertTrue("Disconnected framework did not settle",settled);
            capture(s,"home",HOME,R.string.app_name);
            onView(withId(HOOKS)).perform(click());capture(s,"hooks",HOOKS,R.string.ui_hooks);
            onView(withId(DIAGNOSTICS)).perform(click());capture(s,"diagnostics",DIAGNOSTICS,R.string.diagnostics);
            onView(withId(HOME)).perform(click());
            s.recreate();s.onActivity(a->{NavigationBarView n=find(a.getWindow().getDecorView(),NavigationBarView.class);assertNotNull(n);assertEquals(HOME,n.getSelectedItemId());});
        }
        // R05 settings-only route: preserve the real bridge without granting permission or executing anything.
        android.content.Intent settings=new android.content.Intent(instrumentation.getTargetContext(),
                com.mikeyphw.hooklab.termux.TermuxBridgeActivity.class)
                .setAction(com.mikeyphw.hooklab.termux.TermuxCommandPolicy.ACTION_SETTINGS);
        try(ActivityScenario<com.mikeyphw.hooklab.termux.TermuxBridgeActivity> bridge=ActivityScenario.launch(settings)){
            bridge.onActivity(a->{View decor=a.getWindow().getDecorView();
                assertTrue(containsText(decor,a.getString(R.string.termux_r05_settings_intro)));
                assertFalse(containsText(decor,a.getString(R.string.termux_r05_review_run)));
            });
        }
    }
}
