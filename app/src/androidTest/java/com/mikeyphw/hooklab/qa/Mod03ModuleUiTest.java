package com.mikeyphw.hooklab.qa;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.mikeyphw.hooklab.HookLabApp;
import com.mikeyphw.hooklab.R;
import com.mikeyphw.hooklab.packs.PackManagerActivity;
import com.mikeyphw.hooklab.runtime.pack.HookPack;
import com.mikeyphw.hooklab.runtime.pack.PackPolicy;
import com.mikeyphw.hooklab.runtime.pack.PackStore;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/** Real production module Activity in an isolated hosted emulator. Fixture trust is not device acceptance. */
@RunWith(AndroidJUnit4.class)
public final class Mod03ModuleUiTest {
    private static final android.app.Instrumentation INS=InstrumentationRegistry.getInstrumentation();
    private static final Context APP=INS.getTargetContext();
    private static final File DIR=new File(APP.getFilesDir(),"mod03-test");
    private String phase="SETUP";
    private static void tap(int id){onView(allOf(isAssignableFrom(Button.class),withText(id))).perform(scrollTo(),click());}
    private static void waitFor(int id)throws Exception{
        long until=android.os.SystemClock.elapsedRealtime()+15000;
        do{INS.waitForIdleSync();try{onView(allOf(isAssignableFrom(Button.class),withText(id))).check(matches(isEnabled()));return;}
            catch(androidx.test.espresso.NoMatchingViewException|AssertionError e){Thread.sleep(100);}}
        while(android.os.SystemClock.elapsedRealtime()<until);
        throw new AssertionError("module controls unavailable: "+id);
    }
    private static void touchTargets(View view){
        if(view instanceof Button&&view.isShown()&&view.isEnabled()){
            float d=view.getResources().getDisplayMetrics().density;
            assertTrue("undersized module button",view.getMeasuredHeight()>=48*d&&view.getMeasuredWidth()>=48*d);
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)touchTargets(group.getChildAt(i));}
    }
    private static void capture(ActivityScenario<PackManagerActivity> activity,String name,int title)throws Exception{
        onView(allOf(withText(title),not(isAssignableFrom(Button.class)))).perform(scrollTo()).check(matches(isDisplayed()));
        activity.onActivity(a->touchTargets(a.getWindow().getDecorView()));
        INS.waitForIdleSync();androidx.test.uiautomator.UiDevice.getInstance(INS).waitForIdle();
        Bitmap bitmap=INS.getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        try(FileOutputStream out=new FileOutputStream(new File(DIR,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}
    }
    @Test public void realModuleScreensAndExplicitProvider()throws Exception{
        try{runModuleJourney();}
        catch(Exception|AssertionError failure){QaFailureEvidence.record(APP,"realModuleScreensAndExplicitProvider",phase,failure);throw new AssertionError("MODULE_PHASE_"+phase,failure);}
    }
    private void runModuleJourney()throws Exception{
        phase="SETUP";assertNull(((HookLabApp)APP.getApplicationContext()).currentService());
        PackStore store=new PackStore(new File(APP.getFilesDir(),"hookpacks").toPath());
        assertNull("fresh hosted pack store only",store.state().key());
        byte[] key=Files.readAllBytes(new File(DIR,"publisher.der").toPath());
        byte[] bytes=Files.readAllBytes(new File(DIR,"wordmaster.hookpack").toPath());
        HookPack.Verified pack=HookPack.verify(bytes,key);PackPolicy.requireUiAdmission(pack,pack.archiveSha256);
        store.trust(key,pack.signer,"personal");
        for(String module:java.util.List.of("wordmaster","chatgpt")){
            phase=module.toUpperCase(java.util.Locale.ROOT)+"_ADMISSION";byte[] archive=Files.readAllBytes(new File(DIR,module+".hookpack").toPath());
            HookPack.Verified admitted=HookPack.verify(archive,key);PackPolicy.requireUiAdmission(admitted,admitted.archiveSha256);
            assertEquals(module,admitted.moduleId);store.install(new ByteArrayInputStream(archive),admitted.archiveSha256);
            exercise(module);
        }
        phase="REPOSITORY";
        try(ActivityScenario<PackManagerActivity> activity=ActivityScenario.launch(PackManagerActivity.moduleIntent(APP,"chatgpt"))){
            waitFor(R.string.mod04_title);tap(R.string.mod04_title);waitFor(R.string.mod04_token_set);
            onView(withHint(R.string.mod04_repository)).check(matches(withText("Mikeyphw/MikeysHookLab")));
            onView(withHint(R.string.mod04_token)).perform(scrollTo(),replaceText("github_pat_"+"a".repeat(30)),closeSoftKeyboard());
            tap(R.string.mod04_token_set);onView(withText(R.string.mod04_token_set_done)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withHint(R.string.mod04_token)).check(matches(withText("")));tap(R.string.mod04_token_remove);
            onView(withText(R.string.mod04_token_removed)).perform(scrollTo()).check(matches(isDisplayed()));
            tap(R.string.mod04_select_repository);onView(withText(android.R.string.cancel)).perform(click());
            pressBack();activity.onActivity(a->assertFalse(a.isFinishing()));
        }
        phase="RUNTIME_UNCHANGED";for(String id:java.util.List.of("wordmaster.night.filter","chatgpt.codeblock.copy.probe","chatgpt.codeblock.termux.handoff"))assertEquals("module navigation must not activate runtime","builtin",store.state().modes().get(id));
    }
    private void exercise(String module)throws Exception{
        boolean chat="chatgpt".equals(module);String prefix=chat?"chatgpt-":"",stage=chat?"CHATGPT_":"WORDMASTER_";phase=stage+"LAUNCH";
        try(ActivityScenario<PackManagerActivity> activity=ActivityScenario.launch(PackManagerActivity.moduleIntent(APP,module))){
            phase=stage+"OVERVIEW";waitFor(R.string.mod03_configure);capture(activity,prefix+"overview",R.string.mod03_overview);
            phase=stage+"CONFIGURE";tap(R.string.mod03_configure);waitFor(R.string.mod03_ui_open);capture(activity,prefix+"configure",R.string.mod03_configure);
            phase=stage+"CANCEL";tap(R.string.mod03_ui_open);onView(withText(android.R.string.cancel)).perform(click());
            onView(withText(R.string.mod03_ui_close)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
            phase=stage+"ADMIT";tap(R.string.mod03_ui_open);onView(withText(R.string.mod03_ui_consent)).perform(click());waitFor(R.string.mod03_ui_close);
            phase=stage+"CONTENT";onView(withText(chat?"ChatGPT custom settings":"Word Master custom settings")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText(chat?"Review and restart ChatGPT. Existing execution consent is unchanged. Profiles cannot enable direct execution.":"Changes require review and a host restart.")).perform(scrollTo()).check(matches(isDisplayed()));
            phase=stage+"CLOSE";tap(R.string.mod03_ui_close);onView(withText(R.string.mod03_ui_close)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
            phase=stage+"READMIT";tap(R.string.mod03_ui_open);onView(withText(R.string.mod03_ui_consent)).perform(click());waitFor(R.string.mod03_ui_close);
            phase=stage+"RECREATE";activity.recreate();waitFor(R.string.mod03_ui_open);onView(withText(R.string.mod03_ui_close)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
            phase=stage+"LOGS";tap(R.string.mod03_logs);capture(activity,prefix+"logs",R.string.mod03_logs);
            phase=stage+"VERSIONS";tap(R.string.mod03_versions);capture(activity,prefix+"versions",R.string.mod03_versions);
            activity.onActivity(a->{assertFalse(a.isFinishing());});
            if(chat){phase="CHATGPT_EDITOR";com.mikeyphw.hooklab.packs.ChatGptProfileFixture.exercise(activity);}
        }
    }
}
