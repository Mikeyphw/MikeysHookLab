package com.mikeyphw.hooklab.packs;

import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.mikeyphw.hooklab.R;
import com.mikeyphw.hooklab.sdk.ModuleConfig;
import com.mikeyphw.hooklab.ui.FlatUi;
import java.util.ArrayList;
import java.util.List;

/** Compiled, reviewed native provider. Separate from explicitly admitted personal UI DEX. */
final class WordMasterUiProvider {
    interface Save {void review(ModuleConfig.Document draft);}
    private final Context context;private final Save save;
    private final LinearLayout panel;
    private ModuleConfig.Document draft;private String profile;private boolean active,busy,destroyed,rendering;private long renderGeneration;
    WordMasterUiProvider(Context context,ModuleConfig.Document original,Save save){this.context=context;this.save=save;draft=original;profile=original.active;panel=new LinearLayout(context);panel.setOrientation(LinearLayout.VERTICAL);}
    View create(){render();return panel;}
    ModuleConfig.Document draft(){return draft;}
    String profile(){return profile;}
    void restore(ModuleConfig.Document document,String editing){if(!destroyed&&document.profiles.containsKey(editing)){draft=document;profile=editing;}}
    void resume(){if(!destroyed){active=true;enabled(panel,!busy);}}
    void pause(){active=false;enabled(panel,false);}
    void busy(boolean value){busy=value;enabled(panel,active&&!busy);}
    void destroy(){destroyed=true;active=false;panel.removeAllViews();}
    private boolean usable(){return active&&!busy&&!destroyed&&!rendering;}
    private int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    private void enabled(View v,boolean on){v.setEnabled(on);if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)enabled(g.getChildAt(i),on);}}
    private void label(int id){TextView t=new TextView(context);t.setText(id);t.setTextSize(16);panel.addView(t);FlatUi.sectionHeading(t);}
    private void button(int id,Runnable run){MaterialButton b=new MaterialButton(context);FlatUi.button(b);b.setText(id);b.setOnClickListener(v->{if(usable())run.run();});panel.addView(b);}
    private interface Choice {void select(int index);}
    private void choice(int label,List<String> options,int selected,Choice update){label(label);final long generation=renderGeneration;Spinner spinner=new Spinner(context);spinner.setMinimumHeight(dp(48));spinner.setContentDescription(context.getString(label));ArrayAdapter<String> adapter=new ArrayAdapter<>(context,android.R.layout.simple_spinner_dropdown_item,options);spinner.setAdapter(adapter);spinner.setSelection(selected,false);panel.addView(spinner);spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){if(usable()&&generation==renderGeneration&&position!=selected)update.select(position);}});}
    private void render(){renderGeneration++;rendering=true;try{panel.removeAllViews();List<String> names=new ArrayList<>(draft.profiles.keySet());
        choice(R.string.mod03_edit_profile,names,names.indexOf(profile),i->{profile=names.get(i);render();});
        choice(R.string.mod03_active_profile,names,names.indexOf(draft.active),i->{draft=draft.choose(names.get(i));render();});
        List<String> overrides=new ArrayList<>();overrides.add(context.getString(R.string.mod03_inherit_profile));overrides.addAll(names);
        choice(R.string.mod03_app_override,overrides,draft.appOverride.isEmpty()?0:names.indexOf(draft.appOverride)+1,i->{draft=draft.override(i==0?"":names.get(i-1));render();});
        ModuleConfig.Filter f=draft.profiles.get(profile);SwitchMaterial custom=new SwitchMaterial(context);custom.setText(R.string.mod03_custom_filter);custom.setMinHeight(dp(48));custom.setChecked(f.custom);panel.addView(custom);
        custom.setOnCheckedChangeListener((v,on)->{if(usable()){draft=draft.put(profile,new ModuleConfig.Filter(on,f.opacity,f.tone));render();}});
        if(f.custom){label(R.string.mod03_opacity);Slider slider=new Slider(context);slider.setContentDescription(context.getString(R.string.mod03_opacity));slider.setValueFrom(40);slider.setValueTo(128);slider.setStepSize(1);slider.setValue(f.opacity);slider.setMinimumHeight(dp(48));panel.addView(slider);
            slider.addOnChangeListener((view,value,fromUser)->{if(fromUser&&usable())draft=draft.put(profile,new ModuleConfig.Filter(true,Math.round(value),draft.profiles.get(profile).tone));});
            List<String> tones=List.of("navy","neutral","warm");choice(R.string.mod03_tone,tones,tones.indexOf(f.tone),i->{draft=draft.put(profile,new ModuleConfig.Filter(true,draft.profiles.get(profile).opacity,tones.get(i)));render();});
            button(R.string.mod03_preview,()->preview(draft.profiles.get(profile).color()));
        }else button(R.string.mod03_preview,()->preview(draft.profiles.get(profile).color()));
        if(names.size()<ModuleConfig.MAX_PROFILES)button(R.string.mod03_new_profile,()->{EditText input=new EditText(context);input.setSingleLine(true);input.setHint(R.string.mod03_profile_hint);new MaterialAlertDialogBuilder(context).setTitle(R.string.mod03_new_profile).setView(input).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->{if(!usable())return;String n=input.getText().toString().trim();try{if(draft.profiles.containsKey(n))throw new IllegalArgumentException();draft=draft.put(n,draft.profiles.get(profile));profile=n;render();}catch(IllegalArgumentException bad){new MaterialAlertDialogBuilder(context).setMessage(R.string.mod03_profile_invalid).setPositiveButton(android.R.string.ok,null).show();}}).show();});
        if(!"default".equals(profile))button(R.string.mod03_remove_profile,()->new MaterialAlertDialogBuilder(context).setTitle(R.string.mod03_remove_profile).setMessage(profile).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->{if(usable()){draft=draft.remove(profile);profile="default";render();}}).show());
        button(R.string.mod03_save_profile,()->save.review(draft));
    }finally{rendering=false;enabled(panel,active&&!busy);}}
    private void preview(int color){android.widget.FrameLayout frame=new android.widget.FrameLayout(context);frame.setBackgroundColor(0xffaaaaaa);frame.setMinimumHeight(dp(112));View overlay=new View(context);overlay.setBackgroundColor(color);frame.addView(overlay,new android.widget.FrameLayout.LayoutParams(-1,dp(112)));new MaterialAlertDialogBuilder(context).setTitle(R.string.mod03_preview).setView(frame).setPositiveButton(android.R.string.ok,null).show();}
}
