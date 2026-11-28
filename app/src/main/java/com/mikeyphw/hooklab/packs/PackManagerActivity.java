package com.mikeyphw.hooklab.packs;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.ImageView;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mikeyphw.hooklab.ui.FlatUi;
import com.mikeyphw.hooklab.ui.MainActivity;
import com.mikeyphw.hooklab.runtime.BuiltinRegistry;
import com.mikeyphw.hooklab.sdk.HookSdk;
import com.mikeyphw.hooklab.sdk.ModuleConfig;
import com.mikeyphw.hooklab.sdk.ChatGptConfig;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
import com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline;
import com.mikeyphw.hooklab.core.TweakKeys;
import com.mikeyphw.hooklab.core.TweakDiagnostics;
import java.util.Set;
import java.util.Collections;
import java.util.HashSet;
import java.util.TreeMap;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.mikeyphw.hooklab.HookLabApp;
import com.mikeyphw.hooklab.R;
import com.mikeyphw.hooklab.runtime.pack.HookPack;
import com.mikeyphw.hooklab.runtime.pack.PackPolicy;
import com.mikeyphw.hooklab.runtime.pack.PackPublication;
import com.mikeyphw.hooklab.runtime.pack.PackSnapshot;
import com.mikeyphw.hooklab.runtime.pack.PackStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Native module workspace; selection, scope and runtime execution are distinct. */
public final class PackManagerActivity extends AppCompatActivity {
    private static final int OPEN_PACK=101,OPEN_PERSONAL=102,OPEN_TEST=103,IMPORT_CONFIG=104,EXPORT_CONFIG=105;
    private HookLabApp app;
    private PackStore store;
    private LinearLayout controls;
    private TextView status;
    private final List<Button> buttons=new ArrayList<>();
    private boolean busy;
    private static final String ACTION_MODULE="com.mikeyphw.hooklab.action.MODULE_DETAILS";
    private static final String EXTRA_MODULE="module_id";
    private String moduleId="";
    private int tab=0;
    private MaterialToolbar toolbar;
    private Model latest;
    private ModuleConfig.ProfileDocument<?,?> exportDraft;
    private ProfileEditor<?,?> provider;
    private ModuleUiProvider importedProvider;private View importedView;private Button importedClose;private boolean importedResumed,activityResumed;
    private boolean visible;
    private ModuleConfig.ProfileDocument<?,?> restoredDraft;
    private String restoredBase="",restoredProfile="";
    public static Intent moduleIntent(Context context,String module){return new Intent(context,PackManagerActivity.class).setAction(ACTION_MODULE).putExtra(EXTRA_MODULE,module);}
    private static HookSdk.ModuleDescriptor descriptor(String module){for(HookSdk.ModuleDescriptor d:BuiltinRegistry.current().modules())if(d.id.equals(module))return d;return null;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private final Runnable diagnosticObserver=()->{if(latest!=null&&tab==2&&!busy&&alive())render(latest);};
    private Uri pendingShare;
    private interface Operation<T>{T run()throws Exception;}
    private interface Result<T>{void accept(T value);}
    @Override public void onCreate(Bundle saved){
        SharedPreferences appearance=getSharedPreferences("appearance",MODE_PRIVATE);int night=appearance.getInt("night",AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        boolean amoled=night==100;if(amoled){setTheme(R.style.Theme_HookLab_Amoled);night=AppCompatDelegate.MODE_NIGHT_YES;}
        getDelegate().setLocalNightMode(night);super.onCreate(saved);
        if(Build.VERSION.SDK_INT>=31&&!amoled&&appearance.getBoolean("dynamicColors",true))try{DynamicColors.applyToActivityIfAvailable(this);}catch(RuntimeException ignored){}
        app=(HookLabApp)getApplication();store=new PackStore(getFilesDir().toPath().resolve("hookpacks"));
        if(saved!=null){String id=saved.getString(EXTRA_MODULE,"");if(descriptor(id)!=null)moduleId=id;tab=Math.max(0,Math.min(3,saved.getInt("tab")));try{String draft=saved.getString("configDraft","");if(!draft.isEmpty()){restoredDraft=ModuleConfig.parseDocument(draft.getBytes(StandardCharsets.UTF_8));restoredBase=saved.getString("configBase","");restoredProfile=saved.getString("configProfile","");}String export=saved.getString("exportDraft","");if(!export.isEmpty())exportDraft=ModuleConfig.parseDocument(export.getBytes(StandardCharsets.UTF_8));}catch(RuntimeException ignored){restoredDraft=null;exportDraft=null;}}
        else if(ACTION_MODULE.equals(getIntent().getAction()))try{String id=getIntent().getStringExtra(EXTRA_MODULE);if(descriptor(id)!=null)moduleId=id;}catch(RuntimeException ignored){}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(FlatUi.canvas(this));
        toolbar=new MaterialToolbar(this);FlatUi.toolbar(toolbar);toolbar.setTitle(R.string.pack_title);root.addView(toolbar,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);int pad=dp(16);body.setPadding(pad,pad,pad,pad);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        status=new TextView(this);status.setText(R.string.pack_reading);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(status);
        controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);body.addView(controls);setContentView(root);
        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets)->{Insets safe=insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());view.setPadding(safe.left,safe.top,safe.right,safe.bottom);return WindowInsetsCompat.CONSUMED;});ViewCompat.requestApplyInsets(root);
        // Exported share entry only stages a preview; no trust, install, publish, or activation on arrival.
        if(saved==null&&Intent.ACTION_SEND.equals(getIntent().getAction())){
            if("application/vnd.hooklab.pack".equals(getIntent().getType()))pendingShare=getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
        }
        refresh();
    }
    @Override protected void onStart(){super.onStart();visible=true;app.addDiagnosticObserver(diagnosticObserver);if(latest!=null&&!busy)refresh();}
    @Override protected void onStop(){visible=false;destroyImported();if(provider!=null)provider.pause();app.removeDiagnosticObserver(diagnosticObserver);super.onStop();}
    @Override protected void onResume(){super.onResume();activityResumed=true;if(provider!=null)provider.resume();}
    @Override protected void onPause(){activityResumed=false;destroyImported();if(provider!=null)provider.pause();super.onPause();}
    @Override protected void onDestroy(){destroyImported();destroyProvider();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString(EXTRA_MODULE,moduleId);out.putInt("tab",tab);if(provider!=null&&latest!=null&&latest.configuration(provider.draft().module())!=null){out.putString("configDraft",provider.draft().canonical());out.putString("configBase",latest.configuration(provider.draft().module()).current.digest());out.putString("configProfile",provider.profile());}if(exportDraft!=null)out.putString("exportDraft",exportDraft.canonical());super.onSaveInstanceState(out);}
    private boolean debug(){return (getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0;}
    private boolean alive(){return !isFinishing()&&!isDestroyed();}
    private void busy(boolean value){busy=value;for(Button b:buttons)b.setEnabled(!value);if(provider!=null)provider.busy(value);if(value&&importedProvider!=null)destroyImported();}
    private <T>void execute(Operation<T> task,Result<T> done){
        if(busy||!alive())return;busy(true);
        app.frameworkTask(()->{try{T result=task.run();runOnUiThread(()->{if(alive()){busy(false);done.accept(result);}});}
            catch(Exception|LinkageError e){runOnUiThread(()->{if(alive()){busy(false);status.setText(getString(R.string.pack_refused,e.getClass().getSimpleName()));}});}});
    }
    private void action(int label,Runnable run){action(getString(label),run);}
    private void action(CharSequence label,Runnable run){MaterialButton b=new MaterialButton(this);FlatUi.button(b);b.setText(label);b.setOnClickListener(v->{if(!busy)run.run();});controls.addView(b,new LinearLayout.LayoutParams(-1,-2));buttons.add(b);}
    private void heading(CharSequence title){TextView row=new TextView(this);row.setText(title);row.setTextSize(20);row.setPadding(0,dp(16),0,dp(8));FlatUi.sectionHeading(row);controls.addView(row);}
    private void openHook(String id){startActivity(new Intent(this,MainActivity.class).setAction(MainActivity.ACTION_OPEN_HOOK).putExtra("hook_id",id));}
    private void selectModule(String id){moduleId=id;tab=0;render(latest);}
    private void text(String value){TextView row=new TextView(this);row.setText(value);controls.addView(row);}
    private void confirm(int title,int body,Runnable yes){new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(body).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->yes.run()).show();}
    private void open(int request){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");startActivityForResult(intent,request);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null){if(request==EXPORT_CONFIG)exportDraft=null;return;}Uri uri=data.getData();if(request==IMPORT_CONFIG){previewConfiguration(uri);return;}if(request==EXPORT_CONFIG){exportConfiguration(uri);return;}if(request==OPEN_PACK)previewPack(uri);else if(request==OPEN_PERSONAL||request==OPEN_TEST&&debug())previewKey(uri,request==OPEN_TEST?"test":"personal");}
    private byte[] read(Uri uri,int max)throws IOException{
        if(uri==null||!"content".equals(uri.getScheme()))throw new IOException("only document content accepted");
        try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("document unavailable");return HookPack.readBounded(in,max);}
    }
    private void previewKey(Uri uri,String channel){execute(()->read(uri,2048),key->{
        final String sha;try{sha=HookPack.signerFingerprint(key);}catch(Exception e){status.setText(R.string.pack_bad_key);return;}
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint(R.string.pack_key_hint);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.pack_trust_title).setMessage(getString(R.string.pack_trust_prompt,channel,sha)).setView(input).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->{
            String confirmed=input.getText().toString().trim();execute(()->{store.trust(key,confirmed,channel);return null;},ignored->refresh());}).show();
    });}
    private static final class Preview {final byte[] bytes;final HookPack.Verified pack;Preview(byte[] bytes,HookPack.Verified pack){this.bytes=bytes;this.pack=pack;}}
    private void previewPack(Uri uri){execute(()->{byte[] bytes=read(uri,HookPack.MAX_ARCHIVE);return new Preview(bytes,store.inspect(new ByteArrayInputStream(bytes)));},preview->{
        HookPack.Verified p=preview.pack;if("test".equals(p.channel)&&!debug()){status.setText(R.string.pack_test_refused);return;}
        new MaterialAlertDialogBuilder(this).setTitle(R.string.pack_import).setMessage(getString(R.string.pack_preview,p.moduleId,p.version,p.channel,p.signer,p.archiveSha256)).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->execute(()->{store.install(new ByteArrayInputStream(preview.bytes),p.archiveSha256);return null;},ignored->refresh())).show();
    });}
    private static final class Model {
        final PackStore.Catalog catalog;final PackStore.State local;final String remote,generation;
        final Set<String> scope;final Map<String,Boolean> enabled,locked;final Map<String,Long> versions;final Map<String,FrameworkPackRemote.Configuration> configurations;
        FrameworkPackRemote.Configuration configuration(String module){return configurations.get(module);}
        Model(PackStore.Catalog catalog,String remote,String generation,Set<String> scope,Map<String,Boolean> enabled,Map<String,Boolean> locked,Map<String,Long> versions,Map<String,FrameworkPackRemote.Configuration> configurations){this.configurations=Collections.unmodifiableMap(new TreeMap<>(configurations));this.catalog=catalog;this.local=catalog.state;this.remote=remote;this.generation=generation;this.scope=scope;this.enabled=enabled;this.locked=locked;this.versions=versions;}
    }
    private void refresh(){execute(()->{
        PackStore.Catalog catalog=store.catalog();String state=getString(R.string.pack_remote_unavailable);String generation="";Set<String> scope=null;Map<String,Boolean> enabled=Collections.emptyMap(),locked=Collections.emptyMap();Map<String,Long> versions=new TreeMap<>();Map<String,FrameworkPackRemote.Configuration> configurations=new TreeMap<>();
        for(HookSdk.HookDescriptor h:BuiltinRegistry.current().hooks())try{PackageInfo info=getPackageManager().getPackageInfo(h.packageName,0);versions.put(h.id,info.getLongVersionCode());}catch(PackageManager.NameNotFoundException|RuntimeException ignored){}
        try{FrameworkPackRemote r=new FrameworkPackRemote(app);Map<String,?> values=r.state();if(Boolean.TRUE.equals(values.get(PackSnapshot.SAFE)))state=getString(R.string.pack_safe_active);
            else if(PackPublication.reconcile(store,r)){state=getString(R.string.pack_remote_ready);generation=PackSnapshot.read(r.state()).generation;if(!generation.equals(catalog.state.snapshot().generation)){state=getString(R.string.pack_remote_pending);generation="";}}
            else state=getString(R.string.pack_remote_pending);
            scope=r.scope();enabled=r.flags(TweakKeys.GROUP,false);locked=r.flags(TweakDiagnostics.GROUP,true);
            for(String module:List.of(ModuleConfig.MODULE,ChatGptConfig.MODULE))try{configurations.put(module,r.configuration(module));}catch(Exception|LinkageError refused){/* unavailable config does not hide other modules */}
        }catch(Exception|LinkageError ignored){/* local inspection remains available; unavailable flags are unknown */}
        return new Model(catalog,state,generation,scope,enabled,locked,versions,configurations);
    },model->{render(model);Uri share=pendingShare;pendingShare=null;if(share!=null)previewPack(share);});}
    private void publish(){confirm(R.string.pack_publish,R.string.pack_publish_prompt,()->execute(()->{
        PackStore.State s=store.state();if("test".equals(s.channel())&&!debug())throw new IllegalStateException("test trust not allowed in release");return PackPublication.publish(store,new FrameworkPackRemote(app));
    },ignored->refresh()));}
    /** For destructive local selection changes, stop new migrated-hook loads before mutating the store. */
    private void stopAndChange(Operation<Void> change){execute(()->{FrameworkPackRemote remote=new FrameworkPackRemote(app);PackPublication.stop(remote);change.run();return null;},ignored->refresh());}
    private void publisher(Model m){
        heading(getString(R.string.mod03_publisher));byte[] key=m.local.key();String fingerprint=null;
        if(key!=null){try{fingerprint=HookPack.signerFingerprint(key);}catch(Exception e){status.setText(R.string.pack_bad_key);return;}text(getString(R.string.pack_publisher,m.local.channel(),fingerprint));}
        if(key==null){action(R.string.pack_trust_personal,()->open(OPEN_PERSONAL));if(debug())action(R.string.pack_trust_test,()->open(OPEN_TEST));}
        else{String confirmed=fingerprint;action(R.string.pack_import,()->open(OPEN_PACK));action(R.string.pack_revoke,()->confirm(R.string.pack_revoke,R.string.pack_revoke_prompt,()->stopAndChange(()->{store.revoke(confirmed);return null;})));}
    }
    private void metadata(PackStore.PackMetadata p){text(getString(R.string.mod03_archive,p.version,p.channel,p.archiveSha256));text(getString(R.string.mod03_provenance,p.signer,p.sourceSha,p.sourceTree,p.runId,p.runAttempt,p.settingsSchema));}
    private String hookState(Model m,HookSdk.HookDescriptor hook){
        Long version=m.versions.get(hook.id);String installed=version==null?getString(R.string.mod03_unknown):Long.toString(version);
        String compatibility=version==null?getString(R.string.mod03_unknown):getString(version>=hook.minVersion&&version<=hook.maxVersion?R.string.mod03_compatible:R.string.mod03_incompatible);
        String scope=m.scope==null?getString(R.string.mod03_unknown):getString(m.scope.contains(hook.packageName)?R.string.mod03_yes:R.string.mod03_no);
        Boolean on=m.enabled.get(hook.id),lock=m.locked.get(hook.id);String mode=m.local.modes().get(hook.id);
        return getString(R.string.mod03_hook_state,hook.packageName,installed,compatibility,scope,on==null?getString(R.string.mod03_unknown):getString(on?R.string.mod03_yes:R.string.mod03_no),lock==null?getString(R.string.mod03_unknown):getString(lock?R.string.mod03_yes:R.string.mod03_no),mode==null?getString(R.string.mod03_builtin):mode);
    }
    private void hooks(Model m,HookSdk.ModuleDescriptor module){
        for(HookSdk.HookDescriptor h:BuiltinRegistry.current().hooks())if(h.moduleId.equals(module.id)){
            heading(h.title);text(hookState(m,h));action(R.string.mod03_hook_controls,()->openHook(h.id));
            if(m.catalog.current.containsKey(module.id)&&PackPolicy.hooks().containsKey(h.id)){
                action(R.string.pack_select,()->confirm(R.string.pack_select,R.string.pack_select_prompt,()->execute(()->{FrameworkPackRemote r=new FrameworkPackRemote(app);store.select(h.id,r.scope().contains(h.packageName));return null;},ignored->refresh())));
                action(R.string.pack_disable,()->confirm(R.string.pack_disable,R.string.pack_disable_prompt,()->stopAndChange(()->{store.disable(h.id);return null;})));
            }
        }
        text(getString(R.string.mod03_restart));
    }
    private void logs(Model m,HookSdk.ModuleDescriptor module){
        DiagnosticTimeline.Filter filter=new DiagnosticTimeline.Filter();Set<String> ids=new HashSet<>();for(HookSdk.HookDescriptor h:BuiltinRegistry.current().hooks())if(h.moduleId.equals(module.id))ids.add(h.id);filter.groupHooks=ids;
        List<DiagnosticTimeline.Event> rows=app.diagnostics().filtered(filter);text(getString(R.string.mod03_logs_caution));int count=0;
        for(int i=rows.size()-1;i>=0&&count<30;i--){DiagnosticTimeline.Event e=rows.get(i);if(!module.id.equals(e.moduleId))continue;count++;
            heading(e.code);text(e.hookId+" · "+e.moduleVersion+"\n"+e.archiveSha256+"\n"+e.registryGeneration+"\n"+e.fileSha256+":"+e.lineNumber);
            PackStore.PackMetadata p=m.catalog.current.get(module.id);boolean match=p!=null&&p.archiveSha256.equals(e.archiveSha256)&&p.version.equals(e.moduleVersion)&&m.generation.equals(e.registryGeneration);
            text(getString(match?R.string.mod03_log_matches:R.string.mod03_log_stale));
        }if(count==0)text(getString(R.string.mod03_no_logs));
    }
    private void destroyProvider(){destroyImported();if(provider!=null){provider.pause();provider.destroy();provider=null;}}
    private void destroyImported(){
        ModuleUiProvider old=importedProvider;View view=importedView;boolean resumed=importedResumed;
        importedProvider=null;importedView=null;importedResumed=false;
        if(view!=null&&view.getParent()==controls)controls.removeView(view);
        if(importedClose!=null){controls.removeView(importedClose);buttons.remove(importedClose);importedClose=null;}
        if(old!=null){try{if(resumed)old.pause();}catch(RuntimeException|LinkageError|AssertionError ignored){}finally{try{old.destroy();}catch(RuntimeException|LinkageError|AssertionError ignored){}}}
    }
    private void customUi(Model m,HookSdk.ModuleDescriptor module){
        PackStore.PackMetadata pack=m.catalog.current.get(module.id);if(pack==null)return;
        String help=pack.uiResources.translation(module.id,"help",getResources().getConfiguration().getLocales().get(0),null);
        if(help!=null)text(help);
        if(!pack.hasUi)return;
        if(!"personal".equals(pack.channel)){text(getString(R.string.mod03_ui_test_refused));return;}
        action(R.string.mod03_ui_open,()->new MaterialAlertDialogBuilder(this).setTitle(R.string.mod03_ui_trust_title)
            .setMessage(getString(R.string.mod03_ui_trust_prompt,pack.signer,pack.archiveSha256))
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.mod03_ui_consent,(d,w)->{
                final String id=module.id,sha=pack.archiveSha256;
                execute(()->store.uiArchive(id,sha),verified->{
                    if(!visible||!activityResumed||!alive()||tab!=1||!id.equals(moduleId)||latest!=m)return;
                    destroyImported();try{
                        final ModuleUiProvider custom=VerifiedPackLoader.loadUi(verified,sha);importedProvider=custom;
                        ModuleUiProvider.Host host=new ModuleUiProvider.Host(){
                            @Override public Context themedContext(){return PackManagerActivity.this;}
                            @Override public String moduleId(){return id;}
                            @Override public com.mikeyphw.hooklab.sdk.ModuleUiResources resources(){return verified.uiResources;}
                            @Override public ModuleConfig.Document wordMasterSettings(){if(!ModuleConfig.MODULE.equals(id)||m.configuration(id)==null||!configurationSupported(m,id))throw new IllegalStateException("configuration unavailable");return (ModuleConfig.Document)m.configuration(id).current;}
                            @Override public void reviewWordMasterSettings(ModuleConfig.Document draft){if(importedProvider!=custom||!visible||busy||latest!=m||!ModuleConfig.MODULE.equals(id))throw new IllegalStateException("provider inactive");saveConfiguration(m,java.util.Objects.requireNonNull(draft));}
                            @Override public ChatGptConfig.Document chatGptSettings(){if(!ChatGptConfig.MODULE.equals(id)||m.configuration(id)==null||!configurationSupported(m,id))throw new IllegalStateException("configuration unavailable");return (ChatGptConfig.Document)m.configuration(id).current;}
                            @Override public void reviewChatGptSettings(ChatGptConfig.Document draft){if(importedProvider!=custom||!visible||busy||latest!=m||!ChatGptConfig.MODULE.equals(id))throw new IllegalStateException("provider inactive");saveConfiguration(m,java.util.Objects.requireNonNull(draft));}
                            @Override public TextView heading(CharSequence label){TextView t=new TextView(PackManagerActivity.this);t.setText(label);t.setTextSize(20);FlatUi.sectionHeading(t);return t;}
                            @Override public Button button(CharSequence label,Runnable callback){MaterialButton b=new MaterialButton(PackManagerActivity.this);FlatUi.button(b);b.setText(label);b.setOnClickListener(v->{if(importedProvider==custom&&visible&&!busy&&latest==m)try{callback.run();}catch(RuntimeException|LinkageError|AssertionError failure){destroyImported();status.setText(R.string.mod03_ui_failed);}});return b;}
                        };
                        View view=custom.create(host);if(view==null||view.getParent()!=null)throw new IllegalStateException("invalid provider view");
                        importedView=view;controls.addView(view);importedResumed=true;custom.resume();
                        action(R.string.mod03_ui_close,this::destroyImported);importedClose=buttons.get(buttons.size()-1);
                    }catch(ReflectiveOperationException|RuntimeException|LinkageError|AssertionError failure){destroyImported();status.setText(R.string.mod03_ui_failed);}
                });
            }).show());
    }
    private void saveConfiguration(Model m,ModuleConfig.ProfileDocument<?,?> draft){
        final String id=draft.module();final FrameworkPackRemote.Configuration frame=m.configuration(id);
        if(frame==null||!moduleId.equals(id)||latest!=m||!configurationSupported(m,id))return;
        int message=ChatGptConfig.MODULE.equals(id)?R.string.mod03_chatgpt_restart_config:R.string.mod03_restart_config;
        new MaterialAlertDialogBuilder(this).setTitle(R.string.mod03_config_review).setMessage(draft.canonical()+"\n\n"+getString(message)).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->{
            if(!visible||latest!=m||!moduleId.equals(id)||tab!=1)return;
            execute(()->{new FrameworkPackRemote(app).configuration(frame,draft);return null;},ignored->refresh());
        }).show();
    }
    private boolean configurationSupported(Model m,String module){
        String hook=ModuleConfig.MODULE.equals(module)?ModuleConfig.HOOK:ChatGptConfig.MODULE.equals(module)?ChatGptConfig.HOOK:null;
        if(hook==null)return false;String mode=m.local.modes().get(hook);PackStore.PackMetadata pack=m.catalog.current.get(module);
        return PackSnapshot.BUILTIN.equals(mode)||"pack".equals(mode)&&pack!=null&&pack.settingsSchema==2;
    }
    private void configuration(Model m){
        String id=moduleId;FrameworkPackRemote.Configuration frame=m.configuration(id);
        if(frame==null){text(getString(R.string.mod03_config_unavailable));return;}
        if(!configurationSupported(m,id)){text(getString(R.string.mod03_config_unsupported));return;}
        try{
            if(frame.current instanceof ModuleConfig.Document)provider=new WordMasterUiProvider(this,(ModuleConfig.Document)frame.current,draft->saveConfiguration(m,draft));
            else if(frame.current instanceof ChatGptConfig.Document)provider=new ChatGptUiProvider(this,(ChatGptConfig.Document)frame.current,draft->saveConfiguration(m,draft));
            else throw new IllegalStateException("unknown typed configuration");
            if(restoredDraft!=null&&id.equals(restoredDraft.module())&&frame.current.digest().equals(restoredBase))provider.restore(restoredDraft,restoredProfile);
            restoredDraft=null;controls.addView(provider.create());if(activityResumed)provider.resume();
        }catch(RuntimeException|LinkageError failure){destroyProvider();text(getString(R.string.mod03_config_unavailable));return;}
        action(R.string.mod03_config_import,()->{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");startActivityForResult(intent,IMPORT_CONFIG);});
        action(R.string.mod03_config_export,()->{exportDraft=frame.current;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"hooklab-"+id+"-profile.json");startActivityForResult(intent,EXPORT_CONFIG);});
        action(R.string.mod03_config_reset,()->saveConfiguration(m,ChatGptConfig.MODULE.equals(id)?ChatGptConfig.defaults():ModuleConfig.defaults()));
        if(frame.previous!=null)action(R.string.mod03_config_undo,()->saveConfiguration(m,frame.previous));
    }
    private void previewConfiguration(Uri uri){Model m=latest;String id=moduleId;if(m==null||m.configuration(id)==null||!configurationSupported(m,id))return;
        execute(()->ModuleConfig.parseForModule(id,read(uri,ModuleConfig.MAX_BYTES)),draft->saveConfiguration(m,draft));
    }
    private void exportConfiguration(Uri uri){ModuleConfig.ProfileDocument<?,?> doc=exportDraft;exportDraft=null;if(doc==null)return;
        execute(()->{if(!"content".equals(uri.getScheme()))throw new IOException("only document content accepted");try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("document unavailable");out.write(doc.canonical().getBytes(StandardCharsets.UTF_8));out.flush();}return null;},ignored->status.setText(R.string.mod03_config_exported));
    }
    private void render(Model m){
        if(provider!=null&&latest!=null&&moduleId.equals(provider.draft().module())&&tab==1){
            FrameworkPackRemote.Configuration before=latest.configuration(moduleId),after=m.configuration(moduleId);
            if(before!=null&&before.same(after)){restoredDraft=provider.draft();restoredBase=before.current.digest();restoredProfile=provider.profile();}
        }
        destroyProvider();latest=m;controls.removeAllViews();buttons.clear();status.setText(m.remote);action(R.string.pack_refresh,this::refresh);
        HookSdk.ModuleDescriptor module=descriptor(moduleId);
        if(module==null){toolbar.setTitle(R.string.pack_title);heading(getString(R.string.mod03_installed));text(getString(R.string.pack_intro));
            for(HookSdk.ModuleDescriptor d:BuiltinRegistry.current().modules()){
                PackStore.PackMetadata p=m.catalog.current.get(d.id);heading(d.title);text(p==null?getString(R.string.mod03_builtin):getString(R.string.mod03_selected_version,p.version));action(R.string.mod03_module_details,()->selectModule(d.id));
            }publisher(m);
        }else{
            toolbar.setTitle(module.title);action(R.string.mod03_all_modules,()->selectModule(""));
            int[] names={R.string.mod03_overview,R.string.mod03_configure,R.string.mod03_logs,R.string.mod03_versions};
            for(int i=0;i<names.length;i++){final int next=i;action(getString(names[i])+(tab==i?" · "+getString(R.string.mod03_selected):""),()->{tab=next;render(m);});}
            heading(getString(names[tab]));PackStore.PackMetadata current=m.catalog.current.get(module.id),previous=m.catalog.previous.get(module.id);
            switch(tab){case 0:text(module.description);if(current!=null)metadata(current);else text(getString(R.string.mod03_builtin));hooks(m,module);break;
                case 1:text(getString(R.string.mod03_config_caution));if(ModuleConfig.MODULE.equals(module.id)||ChatGptConfig.MODULE.equals(module.id))configuration(m);customUi(m,module);hooks(m,module);break;
                case 2:logs(m,module);break;
                case 3:if(current!=null){heading(getString(R.string.mod03_current_archive));metadata(current);}else text(getString(R.string.mod03_builtin));
                    if(previous!=null){heading(getString(R.string.mod03_previous_archive));metadata(previous);action(R.string.pack_rollback,()->confirm(R.string.pack_rollback,R.string.pack_rollback_prompt,()->execute(()->{store.rollback(module.id);return null;},ignored->refresh())));}
                    if(current!=null)action(R.string.pack_remove,()->confirm(R.string.pack_remove,R.string.pack_remove_prompt,()->stopAndChange(()->{store.remove(module.id);return null;})));
                    if(PackPolicy.MODULES.contains(module.id)&&m.local.key()!=null)action(R.string.pack_import,()->open(OPEN_PACK));break;
                default:throw new IllegalStateException("module tab");
            }
        }
        action(R.string.pack_publish,this::publish);
        action(R.string.mod04_title,()->startActivity(new Intent(this,RepositoryActivity.class)));
        action(R.string.pack_safe,()->confirm(R.string.pack_safe,R.string.pack_safe_prompt,()->execute(()->{PackPublication.stop(new FrameworkPackRemote(app));return null;},ignored->refresh())));
    }
}
