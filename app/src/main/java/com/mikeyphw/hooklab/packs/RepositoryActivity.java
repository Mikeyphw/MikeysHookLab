package com.mikeyphw.hooklab.packs;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mikeyphw.hooklab.R;
import com.mikeyphw.hooklab.packs.distribution.GithubRepository;
import com.mikeyphw.hooklab.runtime.distribution.DistributionIndex;
import com.mikeyphw.hooklab.runtime.pack.HookPack;
import com.mikeyphw.hooklab.runtime.pack.PackPolicy;
import com.mikeyphw.hooklab.runtime.pack.PackStore;
import com.mikeyphw.hooklab.sdk.HookSdk;
import com.mikeyphw.hooklab.ui.FlatUi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Manager-only repository workspace. No automatic trust, install, publish, or host execution. */
public final class RepositoryActivity extends AppCompatActivity {
    private PackStore store;
    private GithubRepository.Cache cache;
    private SharedPreferences choices;
    private EditText repository, token, search, runId;
    private TextView status;
    private LinearLayout controls, rows;
    private final List<MaterialButton> buttons=new ArrayList<>();
    private char[] credential;
    private GithubRepository activeClient;
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private String selected=DistributionIndex.OFFICIAL;
    private GithubRepository.Cached index;
    private PackStore.Catalog catalog;
    private boolean visible,busy,testing;
    private volatile long epoch;
    private interface Work<T>{T run()throws Exception;}
    private interface Done<T>{void accept(T value);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private long now(){return System.currentTimeMillis()/1000;}
    @Override protected void onCreate(Bundle state){
        SharedPreferences appearance=getSharedPreferences("appearance",MODE_PRIVATE);int night=appearance.getInt("night",AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if(night==100){setTheme(R.style.Theme_HookLab_Amoled);night=AppCompatDelegate.MODE_NIGHT_YES;}getDelegate().setLocalNightMode(night);super.onCreate(state);
        store=new PackStore(getFilesDir().toPath().resolve("hookpacks"));cache=new GithubRepository.Cache(getFilesDir().toPath().resolve("repository-cache"));choices=getSharedPreferences("distribution_choices",MODE_PRIVATE);
        // Same manager UID as reviewed custom UI; no claim of credential isolation.
        // The token is session-only, not placed in saved state, prefs, logs or intents.
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(FlatUi.canvas(this));MaterialToolbar toolbar=new MaterialToolbar(this);FlatUi.toolbar(toolbar);toolbar.setTitle(R.string.mod04_title);root.addView(toolbar,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);int pad=dp(16);body.setPadding(pad,pad,pad,pad);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));controls=body;
        setContentView(root);WindowCompat.setDecorFitsSystemWindows(getWindow(),false);ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets)->{androidx.core.graphics.Insets safe=insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());view.setPadding(safe.left,safe.top,safe.right,safe.bottom);return WindowInsetsCompat.CONSUMED;});ViewCompat.requestApplyInsets(root);
        text(body,getString(R.string.mod04_intro));repository=edit(body,R.string.mod04_repository,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);repository.setText(DistributionIndex.OFFICIAL);
        button(body,R.string.mod04_select_repository,this::selectRepository);button(body,R.string.mod04_official,()->{repository.setText(DistributionIndex.OFFICIAL);selectRepository();});
        token=edit(body,R.string.mod04_token,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);token.setSaveEnabled(false);token.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        button(body,R.string.mod04_token_set,this::setToken);button(body,R.string.mod04_token_remove,()->{clearToken();status.setText(R.string.mod04_token_removed);});button(body,R.string.mod04_test_access,()->access(false));button(body,R.string.mod04_test_actions,()->access(true));
        button(body,R.string.mod04_stable,()->{testing=false;clearRows();load(false);});if(debug())button(body,R.string.mod04_testing,()->{testing=true;index=null;clearRows();status.setText(R.string.mod04_testing_caution);});
        search=edit(body,R.string.mod04_search,InputType.TYPE_CLASS_TEXT);button(body,R.string.mod04_filter,()->{if(!testing&&catalog!=null&&index!=null)render();});runId=edit(body,R.string.mod04_run,InputType.TYPE_CLASS_NUMBER);if(debug())button(body,R.string.mod04_inspect_run,this::inspectTesting);
        button(body,R.string.mod04_refresh,()->{testing=false;load(true);});button(body,R.string.mod04_cache,()->{testing=false;load(false);});button(body,R.string.mod04_notice,()->{boolean on=!choices.getBoolean("foreground_notice",false);choices.edit().putBoolean("foreground_notice",on).apply();status.setText(on?R.string.mod04_notice_on:R.string.mod04_notice_off);});
        status=new TextView(this);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(status);rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);body.addView(rows);
    }
    @Override protected void onStart(){super.onStart();visible=true;long previous=choices.getLong("last_notice",0);if(choices.getBoolean("foreground_notice",false)&&now()-previous>=3600){choices.edit().putLong("last_notice",now()).apply();load(true);}else load(false);}
    @Override protected void onStop(){synchronized(this){visible=false;epoch++;if(activeClient!=null){activeClient.close();activeClient=null;}clearToken();index=null;catalog=null;busy=false;}super.onStop();}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    private boolean debug(){return (getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0;}
    private void clearToken(){if(credential!=null)Arrays.fill(credential,'\0');credential=null;if(token!=null)token.setText("");}
    private EditText edit(LinearLayout parent,int hint,int input){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setInputType(input);e.setMinimumHeight(dp(48));e.setSaveEnabled(false);parent.addView(e);return e;}
    private void text(LinearLayout parent,CharSequence value){TextView v=new TextView(this);v.setText(value);parent.addView(v);}
    private void button(LinearLayout parent,int label,Runnable run){button(parent,getString(label),run);}
    private void button(LinearLayout parent,CharSequence label,Runnable run){MaterialButton b=new MaterialButton(this);FlatUi.button(b);b.setText(label);b.setEnabled(!busy);b.setOnClickListener(v->{if(visible&&!busy)run.run();});parent.addView(b,new LinearLayout.LayoutParams(-1,-2));buttons.add(b);}
    private void busy(boolean b){busy=b;for(MaterialButton v:buttons)v.setEnabled(!b);repository.setEnabled(!b);token.setEnabled(!b);search.setEnabled(!b);runId.setEnabled(!b);}
    private <T>void work(Work<T> operation,Done<T> done){if(!visible||busy)return;busy(true);final long ticket=epoch;status.setText(R.string.mod04_working);worker.execute(()->{try{T result=operation.run();runOnUiThread(()->{if(visible&&epoch==ticket&&!isDestroyed()){busy(false);done.accept(result);}});}catch(Exception|LinkageError e){String reason=e instanceof GithubRepository.Refused?e.getMessage():"VERIFICATION_REFUSED";runOnUiThread(()->{if(visible&&epoch==ticket&&!isDestroyed()){busy(false);status.setText(getString(R.string.mod04_refused,reason));}});}});}
    private void selectRepository(){try{String requested=DistributionIndex.repository(repository.getText().toString().trim());new MaterialAlertDialogBuilder(this).setTitle(R.string.mod04_select_repository).setMessage(getString(R.string.mod04_repo_review,requested)).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->{if(!visible||busy)return;clearToken();selected=requested;testing=false;index=null;catalog=null;clearRows();load(false);}).show();}catch(RuntimeException e){status.setText(R.string.mod04_repo_invalid);}}
    private void setToken(){char[] value=token.getText().toString().toCharArray();try(GithubRepository checked=new GithubRepository(selected,value)){if(!checked.repository().equals(selected))throw new IllegalArgumentException();clearToken();credential=value.clone();status.setText(R.string.mod04_token_set_done);}catch(RuntimeException e){status.setText(R.string.mod04_token_invalid);}finally{Arrays.fill(value,'\0');token.setText("");}}
    private GithubRepository client(){if(activeClient!=null)activeClient.close();activeClient=new GithubRepository(selected,credential);return activeClient;}
    private void access(boolean actions){final GithubRepository api=client();work(()->{try(GithubRepository a=api){a.testAccess(actions);return true;}},ok->status.setText(R.string.mod04_access_ok));}
    private static final class Listing {final PackStore.Catalog catalog;final GithubRepository.Cached index;final String warning;Listing(PackStore.Catalog c,GithubRepository.Cached i,String w){catalog=c;index=i;warning=w;}}
    private void load(boolean online){if(!visible||busy)return;final GithubRepository api=client();work(()->{try(GithubRepository a=api){PackStore.Catalog c=store.catalog();byte[] key=c.state.key();if(key==null||!"personal".equals(c.state.channel()))return new Listing(c,null,"PERSONAL_PUBLISHER_REQUIRED");if(!online)return new Listing(c,cache.read(a.repository(),key),null);try{return new Listing(c,a.refresh(cache,key,now()),null);}catch(GithubRepository.Refused e){return new Listing(c,cache.read(a.repository(),key),e.getMessage());}}},listing->{catalog=listing.catalog;index=listing.index;render();if(listing.warning!=null)status.setText(getString(R.string.mod04_refused,listing.warning));});}
    private Map<String,Long> hostVersions(){Map<String,Long> versions=new TreeMap<>();for(HookSdk.HookDescriptor h:PackPolicy.hooks().values())try{versions.put(h.packageName,getPackageManager().getPackageInfo(h.packageName,0).getLongVersionCode());}catch(PackageManager.NameNotFoundException ignored){}return versions;}
    private void clearRows(){for(int i=buttons.size()-1;i>=0;i--)if(buttons.get(i).getParent()==rows)buttons.remove(i);rows.removeAllViews();}
    private void render(){clearRows();if(catalog==null)return;String filter=search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);Map<String,Long> versions=hostVersions();List<DistributionIndex.Entry> updates=new ArrayList<>();
        if(index==null)status.setText(R.string.mod04_no_index);else status.setText(getString(index.online&&index.index.fresh(now())?R.string.mod04_online:R.string.mod04_offline,index.index.repository,index.index.revision,index.index.signer));
        for(String module:new java.util.TreeSet<>(PackPolicy.MODULES)){PackStore.PackMetadata installed=catalog.current.get(module);String pin=catalog.state.pins().get(module);DistributionIndex.Entry candidate=index==null?null:index.index.newest(module,versions,installed==null?null:installed.version,pin);if(candidate!=null)updates.add(candidate);
            if(!module.contains(filter))continue;text(rows,getString(R.string.mod04_module,module,installed==null?"-":installed.version,pin==null?"-":pin));
            if(installed!=null)button(rows,pin==null?R.string.mod04_pin:R.string.mod04_unpin,()->pin(module,pin==null));
            if(index!=null){for(DistributionIndex.Entry e:index.index.entries)if(e.module.equals(module)){text(rows,getString(R.string.mod04_details,e.version,e.sha256,e.source,e.tree,e.run,e.attempt,e.changelog,index.index.revoked.contains(e.sha256)?getString(R.string.mod04_revoked):getString(e.compatible(versions)?R.string.mod03_compatible:R.string.mod03_incompatible)));}
                if(candidate!=null&&index.online&&index.index.fresh(now()))button(rows,getString(R.string.mod04_review_one,candidate.version),()->prepare(List.of(candidate)));}}
        if(!updates.isEmpty()&&index!=null&&index.online&&index.index.fresh(now()))button(rows,R.string.mod04_update_all,()->prepare(updates));
    }
    private void pin(String module,boolean pin){final PackStore.Catalog current=catalog;new MaterialAlertDialogBuilder(this).setTitle(pin?R.string.mod04_pin:R.string.mod04_unpin).setMessage(module).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->work(()->{store.pin(module,pin,current.state.reviewDigest());return true;},ok->load(false))).show();}
    private void prepare(List<DistributionIndex.Entry> candidates){if(index==null||catalog==null||!index.online)return;final GithubRepository.Cached reviewed=index;final PackStore.Catalog c=catalog;final GithubRepository api=client();final Map<String,Long> versions=hostVersions();
        work(()->{try(GithubRepository a=api){byte[] key=c.state.key();GithubRepository.Cached latest=a.refresh(cache,key,now());if(!latest.index.digest.equals(reviewed.index.digest))throw new IllegalArgumentException("index changed");Map<String,byte[]> packs=new TreeMap<>();for(DistributionIndex.Entry e:candidates){if(!e.compatible(versions))throw new IllegalArgumentException("host version changed");DistributionIndex.Entry current=latest.index.entries.stream().filter(x->x.sha256.equals(e.sha256)).findFirst().orElseThrow();packs.put(e.sha256,a.stable(latest,current,key,now()));}return packs;}},packs->{StringBuilder message=new StringBuilder(getString(R.string.mod04_commit_caution));for(DistributionIndex.Entry e:candidates)message.append('\n').append(e.module).append(' ').append(e.version).append('\n').append(e.sha256);reviewInstall(c,packs,message.toString(),()->{for(DistributionIndex.Entry e:candidates)if(!e.compatible(hostVersions()))throw new IllegalArgumentException("host version changed");GithubRepository.Cached current=cache.read(reviewed.index.repository,c.state.key());if(current==null||!current.index.digest.equals(reviewed.index.digest)||!current.index.fresh(now()))throw new IllegalArgumentException("index review changed");});});}
    private interface Verify {void run()throws Exception;}
    private void reviewInstall(PackStore.Catalog c,Map<String,byte[]> packs,String message,Verify verify){final long ticket=epoch;new MaterialAlertDialogBuilder(this).setTitle(R.string.mod04_commit_title).setMessage(message).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->work(()->{verify.run();synchronized(this){if(epoch!=ticket||!visible)throw new IllegalArgumentException("review ended");store.installBatch(packs,c.state.reviewDigest());}return true;},ok->{load(false);new MaterialAlertDialogBuilder(this).setMessage(R.string.mod04_installed).setPositiveButton(android.R.string.ok,null).show();})).show();}
    private boolean compatible(HookPack.Verified p){Map<String,Long> v=hostVersions();Map<?,?> manifest=DistributionIndex.parse(p.manifest());for(Object item:DistributionIndex.list(manifest,"hooks",32)){Map<?,?> h=DistributionIndex.object(item);Long installed=v.get(DistributionIndex.text(h,"package"));if(installed==null||installed<DistributionIndex.number(h,"min_version")||installed>DistributionIndex.number(h,"max_version"))return false;}return true;}
    private void inspectTesting(){if(!debug()||!testing)return;final long run;try{run=Long.parseLong(runId.getText().toString());if(run<1)throw new IllegalArgumentException();}catch(RuntimeException e){status.setText(R.string.mod04_run_invalid);return;}final GithubRepository api=client();work(()->{try(GithubRepository a=api){PackStore.Catalog c=store.catalog();if(!"test".equals(c.state.channel())||c.state.key()==null)throw new IllegalArgumentException("explicit test publisher required");GithubRepository.TestCandidate candidate=a.testing(run,c.state.key());return new Object[]{c,candidate};}},result->{PackStore.Catalog c=(PackStore.Catalog)result[0];GithubRepository.TestCandidate candidate=(GithubRepository.TestCandidate)result[1];clearRows();status.setText(getString(R.string.mod04_test_identity,candidate.run,candidate.attempt,candidate.workflow,candidate.artifact,candidate.source,candidate.tree,candidate.digest));for(Map.Entry<String,byte[]> e:candidate.packs.entrySet())try{HookPack.Verified p=HookPack.verify(e.getValue(),c.state.key());if(!compatible(p))continue;button(rows,getString(R.string.mod04_review_one,p.moduleId+" "+p.version),()->reviewInstall(c,Map.of(p.archiveSha256,e.getValue()),getString(R.string.mod04_testing_caution)+"\n"+p.archiveSha256,()->{if(!compatible(p))throw new IllegalArgumentException("host version changed");}));}catch(Exception e1){status.setText(R.string.mod04_no_index);}});}
}
