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
            // Import is one explicit source sheet, cancellation leaves the store untouched.
            onView(withText(R.string.dx01_import)).perform(click());
            onView(withText(R.string.dx01_module)).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
            onView(withText(R.string.dx01_framework)).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
            onView(withText(R.string.dx01_logcat_import)).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
            onView(withText(android.R.string.cancel)).perform(scrollTo(),click());assertEquals(3,app.diagnostics().size());
            // Content URI only; malformed paths must fail before resolver access.
            try{com.mikeyphw.hooklab.diagnostics.DiagnosticSaf.importDocument(app.getContentResolver(),android.net.Uri.parse("file:///not-allowed"),app.diagnostics(),"LSPOSED_MODULE");fail();}
            catch(java.io.IOException expected){assertEquals(3,app.diagnostics().size());}
            onView(withText(app.getString(R.string.r01_filter_count,0))).perform(click());
            onView(withHint(R.string.dx01_filter_search)).perform(scrollTo(),replaceText("NEO_HOOK_INSTALLED"),closeSoftKeyboard());
            onView(withText(R.string.r01_apply)).perform(scrollTo(),click());
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();
                assertTrue(hasText(root,"NEO_HOOK_INSTALLED",true));assertFalse(hasText(root,"DROID_TOOLBAR_MISSING",true));
                assertTrue(hasText(root,a.getString(R.string.dx01_event_counts,1,3,0),true));
                assertEquals(3,app.diagnostics().size());assertNull(app.currentService());assertFalse(hasText(root,MARKER,false));
            });
            onView(withText(app.getString(R.string.r01_filter_count,1))).perform(click());
            onView(withHint(R.string.dx01_filter_search)).perform(scrollTo(),replaceText("DROID"),closeSoftKeyboard());
            onView(withText(android.R.string.cancel)).perform(scrollTo(),click());
            scenario.onActivity(a->assertTrue(hasText(a.getWindow().getDecorView(),"NEO_HOOK_INSTALLED",true)));
            onView(withText("NEO_HOOK_INSTALLED")).perform(scrollTo(),click());
            String eventId=app.diagnostics().filtered(null).stream().filter(e->e.code.equals("NEO_HOOK_INSTALLED")).findFirst().orElseThrow().id();
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertTrue(hasText(root,eventId,true));assertFalse(hasText(root,MARKER,false));});
            // Preview cancellation has no clipboard side effect. Use only synthetic data.
            scenario.onActivity(a->{android.content.ClipboardManager manager=(android.content.ClipboardManager)a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);manager.setPrimaryClip(android.content.ClipData.newPlainText("fixture","UXR01_CLIPBOARD_SENTINEL"));});
            onView(withText(R.string.dx01_copy_event)).perform(scrollTo(),click());
            onView(withText(android.R.string.cancel)).perform(click());
            scenario.onActivity(a->{android.content.ClipboardManager manager=(android.content.ClipboardManager)a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);assertEquals("UXR01_CLIPBOARD_SENTINEL",manager.getPrimaryClip().getItemAt(0).getText().toString());manager.clearPrimaryClip();});
            scenario.recreate();
            scenario.onActivity(a->assertTrue(hasText(a.getWindow().getDecorView(),eventId,true)));
            androidx.test.espresso.Espresso.pressBack();
            scenario.onActivity(a->assertTrue(hasText(a.getWindow().getDecorView(),"NEO_HOOK_INSTALLED",true)));
            onView(withText(R.string.r01_health)).perform(scrollTo(),click());
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertFalse(hasText(root,"NEO_HOOK_INSTALLED",true));assertFalse(hasText(root,a.getString(R.string.dx01_import),true));assertFalse(hasText(root,a.getString(R.string.dx02_choose_target),true));});
            onView(withText(R.string.dx02_capture)).perform(scrollTo(),click());
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertTrue(hasText(root,a.getString(R.string.dx02_choose_target),true));assertFalse(hasText(root,a.getString(R.string.dx01_import),true));});
            onView(withText(R.string.r01_timeline)).perform(scrollTo(),click());
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertFalse(hasText(root,a.getString(R.string.dx02_choose_target),true));assertTrue(hasText(root,"NEO_HOOK_INSTALLED",true));});
            onView(withText(app.getString(R.string.r01_filter_count,1))).perform(click());
            onView(withText(R.string.r01_clear)).perform(scrollTo(),click());
            scenario.onActivity(a->assertTrue(hasText(a.getWindow().getDecorView(),"DROID_TOOLBAR_MISSING",true)));

        }
        File folder=new File(ins.getTargetContext().getFilesDir(),"uxr00-native-smoke");assertTrue(folder.isDirectory()||folder.mkdirs());
        JSONObject result=new JSONObject().put("schema","hooklab.diagnostics.fixture.v1").put("synthetic",true)
                .put("runtime_qualified",false).put("real_activity",true).put("framework_disconnected",true)
                .put("assertions",new JSONArray().put("three_normalized_events").put("actual_timeline_projection").put("filter_one_of_three").put("raw_marker_not_rendered").put("store_unchanged").put("no_framework_service").put("filter_cancel_preserves_applied").put("stable_inspector_identity_recreate").put("isolated_workspaces").put("clear_filter").put("single_import_sheet_cancel").put("unsafe_uri_refused").put("redaction_preview_cancel_clipboard_unchanged"));
        try(FileOutputStream f=new FileOutputStream(new File(folder,"fixture.json"))){f.write(result.toString().getBytes(StandardCharsets.UTF_8));}
    }
}
