package com.mikeyphw.hooklab.qa;

import android.content.Context;
import android.graphics.Insets;
import android.graphics.Rect;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ScrollView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import com.mikeyphw.hooklab.probe.ProbeDexLoader;
import com.mikeyphw.hooklab.probe.ProbeProtocol;
import com.mikeyphw.hooklab.probe.TransportProbeActivity;
import com.mikeyphw.hooklab.runtime.BuiltinRegistry;
import com.mikeyphw.hooklab.sdk.HookSdk;
import java.io.InputStream;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real ART and system-inset checks on the isolated API36 emulator. No Vector claim. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion=36,maxSdkVersion=36)
@android.annotation.TargetApi(36)
public final class ProbeArtFixtureTest {
    @Test public void verifiedDexAndBothProbeWindows() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        byte[] payload;
        try(InputStream in=context.getAssets().open(ProbeProtocol.ASSET)){payload=ProbeProtocol.read(in);}
        String digest=ProbeProtocol.digest(payload);
        HookSdk.Entrypoint entry=ProbeDexLoader.loadVerified(payload,payload.clone(),digest);
        AtomicReference<HookSdk.Marker> marker=new AtomicReference<>();
        entry.install(new HookSdk.Context(ProbeProtocol.TARGET,ProbeProtocol.TARGET,1,BuiltinRegistry.current().generation,
                context.getClassLoader(),new HookSdk.Settings(Collections.singletonMap(ProbeProtocol.ID,true)),
                event->{},(id,callback)->{assertEquals(ProbeProtocol.ID,id);assertTrue(marker.compareAndSet(null,callback));}));
        assertNotNull(marker.get());assertEquals("MOD01_PROBE_OK",marker.get().after("BASE"));
        byte[] tampered=payload.clone();tampered[tampered.length-1]^=1;
        try{ProbeDexLoader.loadVerified(payload,tampered,digest);fail("tampered DEX accepted");}
        catch(IllegalArgumentException expected){/* Fail closed before ART. */}
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        AtomicReference<Rect> safeBounds=new AtomicReference<>();
        try(ActivityScenario<TransportProbeActivity> scenario=ActivityScenario.launch(TransportProbeActivity.class)){
            assertTrue(device.wait(Until.hasObject(By.pkg(ProbeProtocol.MANAGER).text(Pattern.compile("(?i)Inspect framework and target"))),10000));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity->{
                ViewGroup host=activity.findViewById(android.R.id.content);
                assertEquals(1,host.getChildCount());ScrollView root=(ScrollView)host.getChildAt(0);
                WindowInsets wi=root.getRootWindowInsets();assertNotNull(wi);
                Insets i=wi.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                assertTrue("expected visible status-bar reservation",i.top>0);
                assertEquals(i.left,root.getPaddingLeft());assertEquals(i.top,root.getPaddingTop());
                assertEquals(i.right,root.getPaddingRight());assertEquals(i.bottom,root.getPaddingBottom());
                Rect w=activity.getWindowManager().getCurrentWindowMetrics().getBounds();
                safeBounds.set(new Rect(w.left+i.left,w.top+i.top,w.right-i.right,w.bottom-i.bottom));
                int[] location=new int[2];root.getChildAt(0).getLocationOnScreen(location);
                assertTrue("manager content intersects status bar",location[1]>=safeBounds.get().top);
            });
        }
        String launched=device.executeShellCommand("am start -W -n "+ProbeProtocol.TARGET+"/.ProbeTargetActivity");
        assertTrue(launched,launched.contains("Status: ok"));
        UiObject2 run=device.wait(Until.findObject(By.pkg(ProbeProtocol.TARGET).text(Pattern.compile("(?i)Run benign marker"))),10000);
        assertNotNull("owned target run button missing",run);Rect b=run.getVisibleBounds();
        assertTrue("owned target overlaps system bars: "+b,safeBounds.get().contains(b));
        run.click();assertTrue("unhooked emulator must remain BASE",device.wait(Until.hasObject(By.pkg(ProbeProtocol.TARGET).text("BASE")),10000));
        UiObject2 result=device.findObject(By.pkg(ProbeProtocol.TARGET).text("BASE"));
        assertNotNull(result);assertTrue(safeBounds.get().contains(result.getVisibleBounds()));
        device.pressBack();
    }
}
