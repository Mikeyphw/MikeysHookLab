package com.mikeyphw.hooklab.packs;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.test.core.app.ActivityScenario;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.mikeyphw.hooklab.R;
import com.mikeyphw.hooklab.sdk.ChatGptConfig;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Production editor wiring in the isolated emulator. Not framework persistence evidence. */
public final class ChatGptProfileFixture {
    private ChatGptProfileFixture(){}
    private static View control(View view,CharSequence text,Class<? extends View> type){
        if(type.isInstance(view)&&view instanceof android.widget.TextView&&text.toString().contentEquals(((android.widget.TextView)view).getText()))return view;
        if(view instanceof ViewGroup){ViewGroup parent=(ViewGroup)view;for(int i=0;i<parent.getChildCount();i++){View found=control(parent.getChildAt(i),text,type);if(found!=null)return found;}}
        return null;
    }
    public static void exercise(ActivityScenario<PackManagerActivity> scenario){
        scenario.onActivity(activity->{
            AtomicReference<ChatGptConfig.Document> reviewed=new AtomicReference<>();
            ChatGptConfig.Document original=ChatGptConfig.defaults().put("quiet",new ChatGptConfig.Options(true,false,1200)).choose("quiet");
            ChatGptUiProvider editor=new ChatGptUiProvider(activity,original,reviewed::set);
            View root=editor.create();editor.resume();
            try{
                SwitchMaterial show=(SwitchMaterial)control(root,activity.getString(R.string.mod03_chatgpt_show_run),SwitchMaterial.class);assertNotNull(show);assertTrue(show.isChecked());
                assertTrue(show.performClick());assertFalse(editor.draft().profiles().get("quiet").showRun);
                assertNull(control(root,activity.getString(R.string.mod03_chatgpt_layout_diagnostics),SwitchMaterial.class));
                Button save=(Button)control(root,activity.getString(R.string.mod03_save_profile),Button.class);assertNotNull(save);
                editor.pause();assertFalse(save.isEnabled());editor.resume();assertTrue(save.performClick());
                assertNotNull(reviewed.get());assertFalse(reviewed.get().effective(ChatGptConfig.MODULE,ChatGptConfig.HOOK,ChatGptConfig.TARGET).showRun);
                assertTrue(original.profiles().get("quiet").showRun);assertEquals(1200,reviewed.get().profiles().get("quiet").tapIntervalMs);
                assertEquals(reviewed.get().canonical(),ChatGptConfig.parse(reviewed.get().canonical().getBytes(java.nio.charset.StandardCharsets.UTF_8)).canonical());
            }finally{editor.destroy();}
        });
    }
}
