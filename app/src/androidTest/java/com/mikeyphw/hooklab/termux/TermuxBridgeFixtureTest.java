package com.mikeyphw.hooklab.termux;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.mikeyphw.hooklab.R;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Synthetic Android authorization/preferences and PendingIntent fixture, not real Termux or host evidence. */
@androidx.test.filters.SdkSuppress(minSdkVersion=36,maxSdkVersion=36)
@RunWith(AndroidJUnit4.class)
public final class TermuxBridgeFixtureTest {
    private static <T extends View> T find(View v,Class<T> type,String label){
        if(type.isInstance(v)&&(!(v instanceof TextView)||label.equals(((TextView)v).getText().toString())))return type.cast(v);
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){T t=find(g.getChildAt(i),type,label);if(t!=null)return t;}}
        return null;
    }
    @Test public void syntheticAuthorizationAndReceipt() throws Exception {
        var ins=InstrumentationRegistry.getInstrumentation();Context c=ins.getTargetContext();
        String stage="TERMUX_SETTINGS";
        try{
            // A fresh isolated emulator is guaranteed by the native runner.
            assertFalse(c.getSharedPreferences("termux_bridge",Context.MODE_PRIVATE).getBoolean("direct_r06_opt_in",false));
            Intent settings=new Intent(c,TermuxBridgeActivity.class).setAction(TermuxCommandPolicy.ACTION_SETTINGS);
            try(ActivityScenario<TermuxBridgeActivity> scenario=ActivityScenario.launch(settings)){
                scenario.onActivity(a->{
                    View root=a.getWindow().getDecorView();
                    CheckBox opt=find(root,CheckBox.class,a.getString(R.string.termux_r06_direct_toggle));assertNotNull(opt);assertFalse(opt.isChecked());
                    com.google.android.material.button.MaterialButton save=find(root,com.google.android.material.button.MaterialButton.class,a.getString(R.string.termux_r05_save));assertNotNull(save);
                    opt.performClick();assertFalse(c.getSharedPreferences("termux_bridge",Context.MODE_PRIVATE).getBoolean("direct_r06_opt_in",false));
                    save.performClick();assertTrue(c.getSharedPreferences("termux_bridge",Context.MODE_PRIVATE).getBoolean("direct_r06_opt_in",false));
                    opt.performClick();save.performClick();assertFalse(c.getSharedPreferences("termux_bridge",Context.MODE_PRIVATE).getBoolean("direct_r06_opt_in",true));
                    try{
                        Method admit=TermuxBridgeActivity.class.getDeclaredMethod("admitRequest",String.class,long.class,long.class);admit.setAccessible(true);
                        String nonce=UUID.randomUUID().toString();long elapsed=SystemClock.elapsedRealtime(),wall=System.currentTimeMillis();
                        assertEquals(true,admit.invoke(a,nonce,elapsed,wall));
                        assertEquals(false,admit.invoke(a,nonce,elapsed,wall));
                        assertEquals(false,admit.invoke(a,UUID.randomUUID().toString(),elapsed,wall-16000));
                    }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
                });
            }
            stage="TERMUX_RESULT";
            String id=UUID.randomUUID().toString();final PendingIntent[] p={null};
            ins.runOnMainSync(()->{p[0]=TermuxSessionReceipt.begin(c,id);assertNotNull(p[0]);TermuxSessionReceipt.submitted(c,id);});
            Bundle result=new Bundle();result.putInt("err",-1);result.putInt("exitCode",7);result.putString("stdout","synthetic_unused_payload");
            Intent fill=new Intent().putExtra("result",result);p[0].send(c,0,fill);
            var ledger=c.getSharedPreferences("termux_session_receipts",Context.MODE_PRIVATE);
            long deadline=SystemClock.elapsedRealtime()+5000;
            while(!"SESSION_EXIT:7".equals(ledger.getString("latest",""))&&SystemClock.elapsedRealtime()<deadline){ins.waitForIdleSync();Thread.sleep(50);}
            assertEquals("SESSION_EXIT:7",ledger.getString("latest",""));
            assertFalse(ledger.getAll().toString().contains("synthetic_unused_payload"));
            try{p[0].send(c,0,fill);fail("One-shot receipt reused");}catch(PendingIntent.CanceledException expected){}
        }catch(Throwable failure){throw new AssertionError("UXR00_STAGE_"+stage,failure);}
    }
}
