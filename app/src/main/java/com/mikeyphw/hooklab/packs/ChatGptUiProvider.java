package com.mikeyphw.hooklab.packs;

import android.content.Context;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mikeyphw.hooklab.R;
import com.mikeyphw.hooklab.sdk.ChatGptConfig;

/** Configuration for the existing exact-build ChatGPT handoff; not an execution port. */
final class ChatGptUiProvider extends ProfileEditor<ChatGptConfig.Document,ChatGptConfig.Options> {
    ChatGptUiProvider(Context context,ChatGptConfig.Document original,Save<ChatGptConfig.Document> save){super(context,ChatGptConfig.Document.class,original,save);}
    @Override protected int overrideLabel(){return R.string.mod03_chatgpt_app_override;}
    @Override protected void renderOptions(ChatGptConfig.Options options){
        switchControl(R.string.mod03_chatgpt_show_run,options.showRun,on->{ChatGptConfig.Options old=draft.profiles().get(profile);draft=draft.put(profile,new ChatGptConfig.Options(on,old.layoutDiagnostics,old.tapIntervalMs));render();});
        if(options.showRun){
            switchControl(R.string.mod03_chatgpt_layout_diagnostics,options.layoutDiagnostics,on->{ChatGptConfig.Options old=draft.profiles().get(profile);draft=draft.put(profile,new ChatGptConfig.Options(old.showRun,on,old.tapIntervalMs));});
            sliderControl(R.string.mod03_chatgpt_interval,ChatGptConfig.MIN_INTERVAL_MS,ChatGptConfig.MAX_INTERVAL_MS,100,options.tapIntervalMs,value->{ChatGptConfig.Options old=draft.profiles().get(profile);draft=draft.put(profile,new ChatGptConfig.Options(old.showRun,old.layoutDiagnostics,value));});
        }
        button(R.string.mod03_chatgpt_preview,()->{
            ChatGptConfig.Options value=draft.profiles().get(profile);
            String summary=context.getString(R.string.mod03_chatgpt_preview_message,context.getString(value.showRun?R.string.mod03_yes:R.string.mod03_no),context.getString(value.layoutDiagnostics?R.string.mod03_yes:R.string.mod03_no),value.tapIntervalMs);
            new MaterialAlertDialogBuilder(context).setTitle(R.string.mod03_chatgpt_preview).setMessage(summary).setPositiveButton(android.R.string.ok,null).show();
        });
    }
}
