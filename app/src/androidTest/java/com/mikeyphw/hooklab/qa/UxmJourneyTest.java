package com.mikeyphw.hooklab.qa;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.SeekBar;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.google.android.material.shape.MaterialShapeDrawable;
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
import java.nio.file.Files;
import java.util.HashSet;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withTagValue;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

/** Instrumentation-only reader of real production views. No replacement Activity. */
@RunWith(AndroidJUnit4.class)
@androidx.test.filters.SdkSuppress(minSdkVersion=36,maxSdkVersion=36)
public final class UxmJourneyTest {
    private static final String PREFIX="uxm:";
    private static final String FOLDER="uxm-native";
    private static Context app(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static File file(String n){return new File(new File(app().getFilesDir(),FOLDER),n);}
    private static void guard(){
        assertEquals(36,Build.VERSION.SDK_INT);
        assertTrue(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"));
        assertTrue((app().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0);
        assertNull(((HookLabApp)app().getApplicationContext()).currentService());
    }
    private static String color(int n){return String.format(java.util.Locale.ROOT,"rgba(%d, %d, %d, %.3f)",(n>>16)&255,(n>>8)&255,n&255,((n>>>24)&255)/255.0);}
    private static int surfaceColor(Drawable d,View v){
        if(d instanceof ColorDrawable)return ((ColorDrawable)d).getColor();
        if(d instanceof GradientDrawable){android.content.res.ColorStateList c=((GradientDrawable)d).getColor();return c==null?0:c.getColorForState(v.getDrawableState(),c.getDefaultColor());}
        if(d instanceof MaterialShapeDrawable){android.content.res.ColorStateList c=((MaterialShapeDrawable)d).getFillColor();return c==null?0:c.getColorForState(v.getDrawableState(),c.getDefaultColor());}
        return 0;
    }
    private static float radius(Drawable d,View v){
        if(d instanceof GradientDrawable)return ((GradientDrawable)d).getCornerRadius();
        if(d instanceof MaterialShapeDrawable)return ((MaterialShapeDrawable)d).getShapeAppearanceModel().getTopLeftCornerSize().getCornerSize(new android.graphics.RectF(0,0,v.getWidth(),v.getHeight()));
        return 0;
    }
    private static String role(View v){
        if(v instanceof CompoundButton)return "switch";
        if(v instanceof SeekBar)return "slider";
        if(v instanceof EditText)return "textbox";
        if(v instanceof android.widget.Spinner)return "combobox";
        if(v.isAccessibilityHeading())return "heading";
        if(v.isClickable())return "button";
        return "group";
    }
    private static JSONArray rect(View v,float d,int left,int top){int[] p=new int[2];v.getLocationOnScreen(p);return new JSONArray().put((p[0]-left)/d).put((p[1]-top)/d).put(v.getWidth()/d).put(v.getHeight()/d);}
    private static JSONObject observe(View v,String id,float d,int left,int top)throws Exception{
        String label=v.getContentDescription()!=null?v.getContentDescription().toString():v instanceof TextView?((TextView)v).getText().toString():"";
        JSONObject state=new JSONObject();
        if(v instanceof CompoundButton)state.put("checked",((CompoundButton)v).isChecked());
        if(v instanceof EditText)state.put("value",((EditText)v).getText().toString());
        if(v instanceof SeekBar)state.put("value",Integer.toString(((SeekBar)v).getProgress()));
        if(v instanceof android.widget.Spinner)state.put("value",String.valueOf(((android.widget.Spinner)v).getSelectedItem()));
        if(v.isSelected())state.put("selected",true);
        if(!v.isEnabled())state.put("disabled",true);
        Rect clipped=new Rect();boolean visible=v.getGlobalVisibleRect(clipped);
        float scaled=v.getResources().getDisplayMetrics().scaledDensity;
        return new JSONObject().put("id",id).put("role",role(v)).put("label",label.replaceAll("\\s+"," ").trim()).put("state",state)
            .put("bounds",rect(v,d,left,top)).put("visible",visible).put("clickable",v.isClickable()).put("focusable",v.isFocusable())
            .put("font_sp",v instanceof TextView?((TextView)v).getTextSize()/scaled:0)
            .put("color",v instanceof TextView?color(((TextView)v).getCurrentTextColor()):color(0))
            .put("background",color(surfaceColor(v.getBackground(),v))).put("radius",radius(v.getBackground(),v)/d)
            .put("touch_dp",new JSONArray().put(v.getWidth()/d).put(v.getHeight()/d));
    }
    private static void collect(View v,JSONArray rows,HashSet<String> seen,float d,int left,int top)throws Exception{
        if(!v.isShown()||v.getWidth()==0||v.getHeight()==0)return;
        Object t=v.getTag();if(t instanceof String&&((String)t).startsWith(PREFIX)){
            String id=((String)t).substring(PREFIX.length());assertTrue("duplicate UXM semantic id",seen.add(id));rows.put(observe(v,id,d,left,top));
        }
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)collect(g.getChildAt(i),rows,seen,d,left,top);}
    }
    private static void write(String name,byte[] data)throws Exception{try(FileOutputStream f=new FileOutputStream(file(name))){f.write(data);}}
    private static void execute(ActivityScenario<MainActivity> s,JSONObject step)throws Exception{
        String op=step.getString("op");if(op.equals("back")){pressBack();return;}if(op.equals("recreate")){s.recreate();return;}
        String id=step.getString("id");var v=onView(withTagValue(is((Object)(PREFIX+id))));
        switch(op){
            case "click":v.perform(click());break;
            case "text":v.perform(replaceText(step.getString("value")));break;
            case "check":v.check((view,e)->{if(e!=null)throw e;assertTrue(view instanceof CompoundButton);});v.perform(click());v.check((view,e)->{if(e!=null)throw e;try{assertEquals(step.getBoolean("value"),((CompoundButton)view).isChecked());}catch(org.json.JSONException ex){throw new AssertionError(ex);}});break;
            case "expect":v.check((view,e)->{if(e!=null)throw e;try{if(step.has("text")){assertTrue(view instanceof TextView);assertEquals(step.getString("text"),((TextView)view).getText().toString());}if(step.has("checked")){assertTrue(view instanceof CompoundButton);assertEquals(step.getBoolean("checked"),((CompoundButton)view).isChecked());}if(step.has("enabled"))assertEquals(step.getBoolean("enabled"),view.isEnabled());if(step.has("focused"))assertEquals(step.getBoolean("focused"),view.isFocused());if(step.has("visible")){Rect r=new Rect();assertEquals(step.getBoolean("visible"),view.isShown()&&view.getGlobalVisibleRect(r));}}catch(org.json.JSONException ex){throw new AssertionError(ex);}});break;
            default:throw new AssertionError("Unsupported UX-M operation");
        }
    }
    /** Foundation witness uses the existing actual catalog, never a fake migrated screen. */
    @Test public void foundationWitness()throws Exception{
        guard();try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            onView(withId(202)).perform(click());onView(withId(R.id.hooklab_catalog_search)).check(matches(isDisplayed()));
            final JSONObject[] evidence={null};s.onActivity(a->{try{View root=a.getWindow().getDecorView();View search=root.findViewById(R.id.hooklab_catalog_search);assertNotNull(search);float d=search.getResources().getDisplayMetrics().density;evidence[0]=new JSONObject().put("route","legacy-catalog").put("real_activity",true).put("source",app().getString(R.string.hooklab_build_source)).put("search",observe(search,"legacy-search",d,0,0));}catch(Exception e){throw new AssertionError(e);}});
            assertTrue(file("").isDirectory()||file("").mkdirs());write("foundation.json",evidence[0].toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    /** An admitted case executes its exact source-bound steps against tagged real views. */
    @Test public void admittedJourney()throws Exception{
        guard();JSONObject spec=new JSONObject(Files.readString(file("case.json").toPath(),StandardCharsets.UTF_8));
        assertEquals("disconnected-debug",spec.getString("fixture"));
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            JSONArray steps=spec.getJSONArray("steps");for(int i=0;i<steps.length();i++)execute(s,steps.getJSONObject(i));
            var ins=InstrumentationRegistry.getInstrumentation();ins.waitForIdleSync();UiDevice.getInstance(ins).waitForIdle(1000);
            final JSONObject[] data={null};
            onView(withTagValue(is((Object)(PREFIX+spec.getString("capture_root"))))).check((v,e)->{if(e!=null)throw e;try{
                View decor=v.getRootView();float d=decor.getResources().getDisplayMetrics().density;WindowInsets in=decor.getRootWindowInsets();assertNotNull(in);android.graphics.Insets bars=in.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                JSONArray rows=new JSONArray();collect(decor,rows,new HashSet<>(),d,bars.left,bars.top);assertTrue(rows.length()>0);
                data[0]=new JSONObject().put("case",spec.getString("id")).put("fixture",spec.getString("fixture")).put("source",app().getString(R.string.hooklab_build_source)).put("journey_steps",steps.length()).put("theme",(decor.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES?"dark":"light").put("density",d).put("insets_px",new JSONArray().put(bars.left).put(bars.top).put(bars.right).put(bars.bottom)).put("components",rows);
            }catch(Exception x){throw new AssertionError(x);}});
            assertNotNull(data[0]);Bitmap b=ins.getUiAutomation().takeScreenshot();assertNotNull(b);try(FileOutputStream f=new FileOutputStream(file("case.png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,f));}finally{b.recycle();}
            write("case-result.json",data[0].toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
