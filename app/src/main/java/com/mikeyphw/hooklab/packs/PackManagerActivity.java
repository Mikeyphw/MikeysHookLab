package com.mikeyphw.hooklab.packs;

import android.app.Activity;
import android.app.AlertDialog;
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

/** Minimal MOD-02 import/activation controls. MOD-03 integrates module-specific native UI. */
public final class PackManagerActivity extends Activity {
    private static final int OPEN_PACK=101,OPEN_PERSONAL=102,OPEN_TEST=103;
    private HookLabApp app;
    private PackStore store;
    private LinearLayout controls;
    private TextView status;
    private final List<Button> buttons=new ArrayList<>();
    private boolean busy;
    private Uri pendingShare;
    private interface Operation<T>{T run()throws Exception;}
    private interface Result<T>{void accept(T value);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);app=(HookLabApp)getApplication();store=new PackStore(getFilesDir().toPath().resolve("hookpacks"));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        int pad=Math.round(16*getResources().getDisplayMetrics().density);body.setPadding(pad,pad,pad,pad);scroll.addView(body);
        TextView intro=new TextView(this);intro.setText(R.string.pack_intro);body.addView(intro);status=new TextView(this);status.setText(R.string.pack_reading);body.addView(status);
        controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);body.addView(controls);setContentView(scroll);
        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        ViewCompat.setOnApplyWindowInsetsListener(scroll,(view,insets)->{Insets safe=insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());view.setPadding(safe.left,safe.top,safe.right,safe.bottom);return WindowInsetsCompat.CONSUMED;});ViewCompat.requestApplyInsets(scroll);
        // Exported share entry only stages a preview; no trust, install, publish, or activation on arrival.
        if(saved==null&&Intent.ACTION_SEND.equals(getIntent().getAction())){
            if("application/vnd.hooklab.pack".equals(getIntent().getType()))pendingShare=getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
        }
        refresh();
    }
    private boolean debug(){return (getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0;}
    private boolean alive(){return !isFinishing()&&!isDestroyed();}
    private void busy(boolean value){busy=value;for(Button b:buttons)b.setEnabled(!value);}
    private <T>void execute(Operation<T> task,Result<T> done){
        if(busy||!alive())return;busy(true);
        app.frameworkTask(()->{try{T result=task.run();runOnUiThread(()->{if(alive()){busy(false);done.accept(result);}});}
            catch(Exception|LinkageError e){runOnUiThread(()->{if(alive()){busy(false);status.setText(getString(R.string.pack_refused,e.getClass().getSimpleName()));}});}});
    }
    private void action(int label,Runnable run){Button b=new Button(this);b.setText(label);b.setOnClickListener(v->run.run());controls.addView(b);buttons.add(b);}
    private void text(String value){TextView row=new TextView(this);row.setText(value);controls.addView(row);}
    private void confirm(int title,int body,Runnable yes){new AlertDialog.Builder(this).setTitle(title).setMessage(body).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->yes.run()).show();}
    private void open(int request){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");startActivityForResult(intent,request);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(request==OPEN_PACK)previewPack(uri);else if(request==OPEN_PERSONAL||request==OPEN_TEST&&debug())previewKey(uri,request==OPEN_TEST?"test":"personal");}
    private byte[] read(Uri uri,int max)throws IOException{
        if(uri==null||!"content".equals(uri.getScheme()))throw new IOException("only document content accepted");
        try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("document unavailable");return HookPack.readBounded(in,max);}
    }
    private void previewKey(Uri uri,String channel){execute(()->read(uri,2048),key->{
        final String sha;try{sha=HookPack.signerFingerprint(key);}catch(Exception e){status.setText(R.string.pack_bad_key);return;}
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint(R.string.pack_key_hint);
        new AlertDialog.Builder(this).setTitle(R.string.pack_trust_title).setMessage(getString(R.string.pack_trust_prompt,channel,sha)).setView(input).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->{
            String confirmed=input.getText().toString().trim();execute(()->{store.trust(key,confirmed,channel);return null;},ignored->refresh());}).show();
    });}
    private static final class Preview {final byte[] bytes;final HookPack.Verified pack;Preview(byte[] bytes,HookPack.Verified pack){this.bytes=bytes;this.pack=pack;}}
    private void previewPack(Uri uri){execute(()->{byte[] bytes=read(uri,HookPack.MAX_ARCHIVE);return new Preview(bytes,store.inspect(new ByteArrayInputStream(bytes)));},preview->{
        HookPack.Verified p=preview.pack;if("test".equals(p.channel)&&!debug()){status.setText(R.string.pack_test_refused);return;}
        new AlertDialog.Builder(this).setTitle(R.string.pack_import).setMessage(getString(R.string.pack_preview,p.moduleId,p.version,p.channel,p.signer,p.archiveSha256)).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->execute(()->{store.install(new ByteArrayInputStream(preview.bytes),p.archiveSha256);return null;},ignored->refresh())).show();
    });}
    private static final class Model {final PackStore.State local;final String remote;Model(PackStore.State local,String remote){this.local=local;this.remote=remote;}}
    private void refresh(){execute(()->{
        PackStore.State local=store.state();String state=getString(R.string.pack_remote_unavailable);
        try{FrameworkPackRemote r=new FrameworkPackRemote(app);Map<String,?> values=r.state();if(Boolean.TRUE.equals(values.get(PackSnapshot.SAFE)))state=getString(R.string.pack_safe_active);
            else if(PackPublication.reconcile(store,r))state=getString(R.string.pack_remote_ready);else state=getString(R.string.pack_remote_pending);}
        catch(Exception|LinkageError ignored){/* local inspection still available, never claim active */}
        return new Model(local,state);
    },model->{render(model);Uri share=pendingShare;pendingShare=null;if(share!=null)previewPack(share);});}
    private void publish(){confirm(R.string.pack_publish,R.string.pack_publish_prompt,()->execute(()->{
        PackStore.State s=store.state();if("test".equals(s.channel())&&!debug())throw new IllegalStateException("test trust not allowed in release");return PackPublication.publish(store,new FrameworkPackRemote(app));
    },ignored->refresh()));}
    /** For destructive local selection changes, stop new migrated-hook loads before mutating the store. */
    private void stopAndChange(Operation<Void> change){execute(()->{FrameworkPackRemote remote=new FrameworkPackRemote(app);PackPublication.stop(remote);change.run();return null;},ignored->refresh());}
    private void render(Model m){controls.removeAllViews();buttons.clear();status.setText(m.remote);action(R.string.pack_refresh,this::refresh);
        byte[] key=m.local.key();String fingerprint=null;
        if(key!=null){try{fingerprint=HookPack.signerFingerprint(key);}catch(Exception e){status.setText(R.string.pack_bad_key);return;}text(getString(R.string.pack_publisher,m.local.channel(),fingerprint));}
        if(key==null){action(R.string.pack_trust_personal,()->open(OPEN_PERSONAL));if(debug())action(R.string.pack_trust_test,()->open(OPEN_TEST));}
        else{String confirmed=fingerprint;action(R.string.pack_revoke,()->confirm(R.string.pack_revoke,R.string.pack_revoke_prompt,()->stopAndChange(()->{store.revoke(confirmed);return null;})));action(R.string.pack_import,()->open(OPEN_PACK));}
        for(Map.Entry<String,PackStore.Version> row:m.local.versions().entrySet()){
            String module=row.getKey();PackStore.Version version=row.getValue();text(getString(R.string.pack_installed,module,version.highestVersion,version.current));
            for(com.mikeyphw.hooklab.sdk.HookSdk.HookDescriptor h:PackPolicy.hooks().values())if(h.moduleId.equals(module)){
                text(h.title+"\n"+getString(R.string.pack_local_mode,m.local.modes().get(h.id)));
                action(R.string.pack_select,()->confirm(R.string.pack_select,R.string.pack_select_prompt,()->execute(()->{FrameworkPackRemote r=new FrameworkPackRemote(app);store.select(h.id,r.scope().contains(h.packageName));return null;},ignored->refresh())));
                action(R.string.pack_disable,()->confirm(R.string.pack_disable,R.string.pack_disable_prompt,()->stopAndChange(()->{store.disable(h.id);return null;})));
            }
            if(!"-".equals(version.previous))action(R.string.pack_rollback,()->confirm(R.string.pack_rollback,R.string.pack_rollback_prompt,()->execute(()->{store.rollback(module);return null;},ignored->refresh())));
            action(R.string.pack_remove,()->confirm(R.string.pack_remove,R.string.pack_remove_prompt,()->stopAndChange(()->{store.remove(module);return null;})));
        }
        action(R.string.pack_publish,this::publish);
        action(R.string.pack_safe,()->confirm(R.string.pack_safe,R.string.pack_safe_prompt,()->execute(()->{PackPublication.stop(new FrameworkPackRemote(app));return null;},ignored->refresh())));
    }
}
