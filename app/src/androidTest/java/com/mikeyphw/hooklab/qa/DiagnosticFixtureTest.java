package com.mikeyphw.hooklab.qa;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.mikeyphw.hooklab.HookLabApp;
import com.mikeyphw.hooklab.R;
import com.mikeyphw.hooklab.ui.MainActivity;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

/** Instrumentation-only synthetic imports via actual bounded production store.
 * This covers data projection, not the SAF picker or a real LSPosed service.
 */
@RunWith(AndroidJUnit4.class)
public final class DiagnosticFixtureTest {
    private static final String MARKER="FIXTURE_SECRET_NOT_DISPLAYABLE";
    private static boolean hasText(View v,String text,boolean exact){
        if(v instanceof TextView){String value=((TextView)v).getText().toString();if(exact?value.equals(text):value.contains(text))return true;}
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(hasText(g.getChildAt(i),text,exact))return true;}
        return false;
    }
    @Test public void importedFixtureProjectionAndFilter() throws Exception {
        var ins=InstrumentationRegistry.getInstrumentation();
        HookLabApp app=(HookLabApp)ins.getTargetContext().getApplicationContext();
        assertNull(app.currentService());assertEquals(0,app.diagnostics().size());
        String fixture="2026-11-23T21:42:08Z HookLabVerbose event=NEO_HOOK_INSTALLED\n"
                +"2026-11-23T21:41:32Z WARN HookLabVerbose event=DROID_TOOLBAR_MISSING\n"
                +"2026-11-23T21:40:15Z token="+MARKER+"\n";
        var receipt=app.diagnostics().importStream(new ByteArrayInputStream(fixture.getBytes(StandardCharsets.UTF_8)),"LSPOSED_MODULE");
        assertEquals(3,receipt.accepted);assertEquals(3,app.diagnostics().size());
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            onView(withId(203)).perform(click());
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();
                assertEquals(MainActivity.class,a.getClass());assertNull(app.currentService());
                assertTrue(hasText(root,"NEO_HOOK_INSTALLED",true));assertTrue(hasText(root,"DROID_TOOLBAR_MISSING",true));
                assertTrue(hasText(root,a.getString(R.string.dx01_event_counts,3,3,0),true));
                assertFalse(hasText(root,MARKER,false));
            });
            onView(withHint(R.string.dx01_filter_search)).perform(scrollTo(),replaceText("NEO_HOOK_INSTALLED"),closeSoftKeyboard());
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();
                assertTrue(hasText(root,"NEO_HOOK_INSTALLED",true));assertFalse(hasText(root,"DROID_TOOLBAR_MISSING",true));
                assertTrue(hasText(root,a.getString(R.string.dx01_event_counts,1,3,0),true));
                assertEquals(3,app.diagnostics().size());assertNull(app.currentService());assertFalse(hasText(root,MARKER,false));
            });
        }
        File folder=new File(ins.getTargetContext().getFilesDir(),"uxr00-native-smoke");assertTrue(folder.isDirectory()||folder.mkdirs());
        JSONObject result=new JSONObject().put("schema","hooklab.diagnostics.fixture.v1").put("synthetic",true)
                .put("runtime_qualified",false).put("real_activity",true).put("framework_disconnected",true)
                .put("assertions",new JSONArray().put("three_normalized_events").put("actual_timeline_projection").put("filter_one_of_three").put("raw_marker_not_rendered").put("store_unchanged").put("no_framework_service"));
        try(FileOutputStream f=new FileOutputStream(new File(folder,"fixture.json"))){f.write(result.toString().getBytes(StandardCharsets.UTF_8));}
    }
}
