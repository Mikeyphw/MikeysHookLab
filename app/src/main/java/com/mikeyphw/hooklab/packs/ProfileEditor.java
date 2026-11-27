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

/** Shared native profile controls and lifecycle. Providers only render typed settings. */
abstract class ProfileEditor<D extends ModuleConfig.ProfileDocument<D,V>,V> {
    interface Save<D> {void review(D draft);}
    protected final Context context;private final Save<D> save;private final Class<D> documentType;
    protected final LinearLayout panel;
    protected D draft;protected String profile;private boolean active,busy,destroyed,rendering;private long renderGeneration;
    ProfileEditor(Context context,Class<D> documentType,D original,Save<D> save){this.context=context;this.documentType=documentType;this.save=save;draft=original;profile=original.active();panel=new LinearLayout(context);panel.setOrientation(LinearLayout.VERTICAL);}
    View create(){render();return panel;}
    D draft(){return draft;}
    String profile(){return profile;}
    void restore(ModuleConfig.ProfileDocument<?,?> document,String editing){if(!destroyed&&documentType.isInstance(document)){D typed=documentType.cast(document);if(typed.profiles().containsKey(editing)){draft=typed;profile=editing;}}}
    void resume(){if(!destroyed){active=true;enabled(panel,!busy);}}
    void pause(){active=false;enabled(panel,false);}
    void busy(boolean value){busy=value;enabled(panel,active&&!busy);}
    void destroy(){destroyed=true;active=false;panel.removeAllViews();}
    protected boolean usable(){return active&&!busy&&!destroyed&&!rendering;}
    protected int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    private void enabled(View v,boolean on){v.setEnabled(on);if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)enabled(g.getChildAt(i),on);}}
    protected void label(int id){TextView t=new TextView(context);t.setText(id);t.setTextSize(16);panel.addView(t);FlatUi.sectionHeading(t);}
    protected void button(int id,Runnable run){MaterialButton b=new MaterialButton(context);FlatUi.button(b);b.setText(id);b.setOnClickListener(v->{if(usable())run.run();});panel.addView(b);}
    protected interface Choice {void select(int index);}
    protected void choice(int label,List<String> options,int selected,Choice update){label(label);final long generation=renderGeneration;Spinner spinner=new Spinner(context);spinner.setMinimumHeight(dp(48));spinner.setContentDescription(context.getString(label));ArrayAdapter<String> adapter=new ArrayAdapter<>(context,android.R.layout.simple_spinner_dropdown_item,options);spinner.setAdapter(adapter);spinner.setSelection(selected,false);panel.addView(spinner);spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){if(usable()&&generation==renderGeneration&&position!=selected)update.select(position);}});}
    protected final void render(){renderGeneration++;rendering=true;try{panel.removeAllViews();List<String> names=new ArrayList<>(draft.profiles().keySet());
        choice(R.string.mod03_edit_profile,names,names.indexOf(profile),i->{profile=names.get(i);render();});
        choice(R.string.mod03_active_profile,names,names.indexOf(draft.active()),i->{draft=draft.choose(names.get(i));render();});
        List<String> overrides=new ArrayList<>();overrides.add(context.getString(R.string.mod03_inherit_profile));overrides.addAll(names);
        choice(overrideLabel(),overrides,draft.appOverride().isEmpty()?0:names.indexOf(draft.appOverride())+1,i->{draft=draft.override(i==0?"":names.get(i-1));render();});
        renderOptions(draft.profiles().get(profile));
        if(names.size()<ModuleConfig.MAX_PROFILES)button(R.string.mod03_new_profile,()->{EditText input=new EditText(context);input.setSingleLine(true);input.setHint(R.string.mod03_profile_hint);new MaterialAlertDialogBuilder(context).setTitle(R.string.mod03_new_profile).setView(input).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->{if(!usable())return;String n=input.getText().toString().trim();try{if(draft.profiles().containsKey(n))throw new IllegalArgumentException();draft=draft.put(n,draft.profiles().get(profile));profile=n;render();}catch(IllegalArgumentException bad){new MaterialAlertDialogBuilder(context).setMessage(R.string.mod03_profile_invalid).setPositiveButton(android.R.string.ok,null).show();}}).show();});
        if(!"default".equals(profile))button(R.string.mod03_remove_profile,()->new MaterialAlertDialogBuilder(context).setTitle(R.string.mod03_remove_profile).setMessage(profile).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->{if(usable()){draft=draft.remove(profile);profile="default";render();}}).show());
        button(R.string.mod03_save_profile,()->save.review(draft));
    }finally{rendering=false;enabled(panel,active&&!busy);}}
    protected abstract int overrideLabel();
    protected abstract void renderOptions(V value);
    protected void switchControl(int id,boolean initial,java.util.function.Consumer<Boolean> update){
        SwitchMaterial control=new SwitchMaterial(context);control.setText(id);control.setMinHeight(dp(48));control.setChecked(initial);panel.addView(control);
        final long generation=renderGeneration;control.setOnCheckedChangeListener((v,on)->{if(usable()&&generation==renderGeneration)update.accept(on);});
    }
    protected void sliderControl(int id,int minimum,int maximum,int step,int initial,java.util.function.IntConsumer update){
        label(id);Slider slider=new Slider(context);slider.setContentDescription(context.getString(id));slider.setValueFrom(minimum);slider.setValueTo(maximum);slider.setStepSize(step);slider.setValue(initial);slider.setMinimumHeight(dp(48));panel.addView(slider);
        final long generation=renderGeneration;slider.addOnChangeListener((view,value,fromUser)->{if(fromUser&&usable()&&generation==renderGeneration)update.accept(Math.round(value));});
    }
}
