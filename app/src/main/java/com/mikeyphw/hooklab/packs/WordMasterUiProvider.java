package com.mikeyphw.hooklab.packs;

import android.content.Context;
import android.view.View;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mikeyphw.hooklab.R;
import com.mikeyphw.hooklab.sdk.ModuleConfig;
import java.util.List;

/** Original typed Word Master configuration. The rendering defect is tracked separately. */
final class WordMasterUiProvider extends ProfileEditor<ModuleConfig.Document,ModuleConfig.Filter> {
    WordMasterUiProvider(Context context,ModuleConfig.Document original,Save<ModuleConfig.Document> save){super(context,ModuleConfig.Document.class,original,save);}
    @Override protected int overrideLabel(){return R.string.mod03_app_override;}
    @Override protected void renderOptions(ModuleConfig.Filter f){
        switchControl(R.string.mod03_custom_filter,f.custom,on->{draft=draft.put(profile,new ModuleConfig.Filter(on,f.opacity,f.tone));render();});
        if(f.custom){
            sliderControl(R.string.mod03_opacity,40,128,1,f.opacity,value->draft=draft.put(profile,new ModuleConfig.Filter(true,value,draft.profiles.get(profile).tone)));
            List<String> tones=List.of("navy","neutral","warm");choice(R.string.mod03_tone,tones,tones.indexOf(f.tone),i->{draft=draft.put(profile,new ModuleConfig.Filter(true,draft.profiles.get(profile).opacity,tones.get(i)));render();});
        }
        button(R.string.mod03_preview,()->preview(draft.profiles.get(profile).color()));
    }
    private void preview(int color){android.widget.FrameLayout frame=new android.widget.FrameLayout(context);frame.setBackgroundColor(0xffaaaaaa);frame.setMinimumHeight(dp(112));View overlay=new View(context);overlay.setBackgroundColor(color);frame.addView(overlay,new android.widget.FrameLayout.LayoutParams(-1,dp(112)));new MaterialAlertDialogBuilder(context).setTitle(R.string.mod03_preview).setView(frame).setPositiveButton(android.R.string.ok,null).show();}
}
