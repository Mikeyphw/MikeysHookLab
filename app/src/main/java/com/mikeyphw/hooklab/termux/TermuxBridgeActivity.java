package com.mikeyphw.hooklab.termux;

import android.content.ComponentName;
import android.content.pm.Signature;
import android.os.SystemClock;
import android.app.PendingIntent;
import android.widget.CheckBox;
import java.security.MessageDigest;
import java.util.Map;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mikeyphw.hooklab.R;
import com.mikeyphw.hooklab.chatgpt.ChatGptCodeProbe;
import com.mikeyphw.hooklab.ui.FlatUi;

/** TB01/R05: one foreground review and permission owner. Untrusted intent never executes code. */
public final class TermuxBridgeActivity extends AppCompatActivity {
    private static final String TAG="HookLabTermux",PACKAGE="com.termux",SERVICE="com.termux.app.RunCommandService";
    private static final String PREFS="termux_bridge";
    private static final int REQUEST_PERMISSION=902;
    private static final String[] SHELLS={"zsh","bash","sh","python3"};
    private String source;
    private SharedPreferences preferences;
    private EditText workdir;
    private Spinner shell;
    private MaterialButton grant,run;
    private TextView status;
    private boolean dispatched,confirming,direct,settingsRoute,submitted;
    private String requestId;
    private long requestTime,requestWall;
    private CheckBox directMode;
    private boolean authenticatedCaller() throws Exception {
        if(!ChatGptCodeProbe.PACKAGE.equals(getCallingPackage()))return false;
        PackageInfo caller=getPackageManager().getPackageInfo(getCallingPackage(),PackageManager.GET_SIGNING_CERTIFICATES);
        if(caller.getLongVersionCode()!=ChatGptCodeProbe.VERSION_CODE||!ChatGptCodeProbe.VERSION_NAME.equals(caller.versionName)||caller.signingInfo==null)return false;
        Signature[] signers=caller.signingInfo.getApkContentsSigners();
        if(signers==null||signers.length!=1)return false;
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(signers[0].toByteArray());
        StringBuilder hex=new StringBuilder(64);for(byte b:hash)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return TermuxCommandPolicy.HOST_CERT_SHA256.contentEquals(hex);
    }
    // Activity callbacks are serialized on the main thread; persist a bounded replay window
    // before accepting any command, including review mode. No source is retained here.
    private boolean admitRequest(String id,long sent,long wall) {
        long now=System.currentTimeMillis();if(!TermuxCommandPolicy.freshRequest(id,sent,SystemClock.elapsedRealtime())||!TermuxCommandPolicy.freshWall(wall,now))return false;
        SharedPreferences ledger=getSharedPreferences("termux_replay",MODE_PRIVATE);
        SharedPreferences.Editor edit=ledger.edit();int count=0;
        for(Map.Entry<String,?> entry:ledger.getAll().entrySet()){
            if(!(entry.getValue() instanceof Long))return false;
            long time=(Long)entry.getValue();
            if(time<=now&&now-time>TermuxCommandPolicy.REQUEST_TTL_MS)edit.remove(entry.getKey());
            else {if(entry.getKey().equals(id))return false;count++;}
        }
        return count<128&&edit.putLong(id,wall).commit();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private static void event(String code){Log.i(TAG,"CGT01_P09_"+code);}
    @Override public void onCreate(Bundle saved){
        int night=getSharedPreferences("appearance",MODE_PRIVATE).getInt("night",AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if(night==100){setTheme(R.style.Theme_HookLab_Amoled);night=AppCompatDelegate.MODE_NIGHT_YES;}
        getDelegate().setLocalNightMode(night);super.onCreate(saved);
        // Never restore previously supplied executable content after a process restart/rotation.
        if(saved!=null){event("STALE_REQUEST_REFUSED");finish();return;}
        Intent inbound=getIntent();String action=inbound==null?null:inbound.getAction();
        preferences=getSharedPreferences(PREFS,MODE_PRIVATE);
        settingsRoute=TermuxCommandPolicy.ACTION_SETTINGS.equals(action);
        try{
            if(TermuxCommandPolicy.ACTION_PREVIEW.equals(action)){
                // Android supplies this package only for a real startActivityForResult caller.
                if(!authenticatedCaller())throw new SecurityException();
                requestId=inbound.getStringExtra(TermuxCommandPolicy.EXTRA_REQUEST_ID);
                requestTime=inbound.getLongExtra(TermuxCommandPolicy.EXTRA_REQUEST_TIME,-1);
                requestWall=inbound.getLongExtra(TermuxCommandPolicy.EXTRA_REQUEST_WALL,-1);
                source=TermuxCommandPolicy.checkedCommand(inbound.getStringExtra(TermuxCommandPolicy.EXTRA_SOURCE));
                if(!admitRequest(requestId,requestTime,requestWall))throw new SecurityException();
                direct=preferences.getBoolean("direct_r06_opt_in",false);
                event("PREVIEW_ACCEPTED");
            }else if(!TermuxCommandPolicy.ACTION_SETTINGS.equals(action))throw new SecurityException();
        }catch(Exception invalid){event("REQUEST_REFUSED");finish();return;}
        // Never retain caller-supplied extras, ClipData, URI grants, or executable source.
        // The settings-only route needs its trusted lifecycle identity: AndroidX
        // ActivityScenario (and route observers) compare action and component.
        if (TermuxCommandPolicy.ACTION_SETTINGS.equals(action)) {
            setIntent(new Intent(this, TermuxBridgeActivity.class)
                    .setAction(TermuxCommandPolicy.ACTION_SETTINGS));
        } else {
            setIntent(new Intent()); // Command-preview identity and payload stay discarded.
        }
        if(source!=null)getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(FlatUi.canvas(this));
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{Insets i=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});
        MaterialToolbar toolbar=new MaterialToolbar(this);FlatUi.toolbar(toolbar);toolbar.setTitle(R.string.termux_r05_title);toolbar.setNavigationIcon(R.drawable.ic_nav_back);toolbar.setNavigationContentDescription(R.string.ui_back);toolbar.setNavigationOnClickListener(v->finish());root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(56)));
        ScrollView scroll=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(12),dp(18),dp(24));scroll.addView(content,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        if(direct){
            status=label(content,R.string.termux_r06_direct_pending);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            ViewCompat.requestApplyInsets(root);root.post(()->attemptDirect(hasWindowFocus()));return;
        }
        label(content,source==null?R.string.termux_r05_settings_intro:R.string.termux_r05_review_intro);
        if(source!=null){TextView preview=new TextView(this);preview.setText(source);preview.setTypeface(Typeface.MONOSPACE);preview.setTextIsSelectable(true);preview.setSaveEnabled(false);preview.setTextDirection(View.TEXT_DIRECTION_LTR);preview.setPadding(0,dp(8),0,dp(16));content.addView(preview,new LinearLayout.LayoutParams(-1,-2));}
        label(content,R.string.termux_r05_interpreter);
        shell=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,SHELLS);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);shell.setAdapter(adapter);shell.setSaveEnabled(false);String prior=preferences.getString("shell","zsh");for(int i=0;i<SHELLS.length;i++)if(SHELLS[i].equals(prior))shell.setSelection(i);content.addView(shell,new LinearLayout.LayoutParams(-1,-2));
        label(content,R.string.termux_r05_workdir);workdir=new EditText(this);workdir.setSingleLine(true);workdir.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);workdir.setSaveEnabled(false);workdir.setText(preferences.getString("workdir",TermuxCommandPolicy.HOME));content.addView(workdir,new LinearLayout.LayoutParams(-1,-2));
        if(settingsRoute){
            directMode=new CheckBox(this);directMode.setText(R.string.termux_r06_direct_toggle);directMode.setChecked(preferences.getBoolean("direct_r06_opt_in",false));
            directMode.setFilterTouchesWhenObscured(true);content.addView(directMode);
            label(content,R.string.termux_r06_direct_warning);
            button(content,R.string.termux_r05_save,v->saveSettings());
            TextView last=label(content,R.string.termux_r06_no_result);
            last.setText(TermuxSessionReceipt.summary(this));
        }
        label(content,R.string.termux_r05_prerequisites);
        status=label(content,R.string.termux_r05_idle);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        grant=button(content,R.string.termux_r05_grant,v->requestGrant());
        if(source!=null)run=button(content,R.string.termux_r05_review_run,v->confirmExecution());
        ViewCompat.requestApplyInsets(root);refresh();
    }
    private TextView label(LinearLayout p,int id){TextView t=new TextView(this);t.setText(id);t.setTextSize(15);t.setPadding(0,dp(10),0,dp(10));p.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;}
    private MaterialButton button(LinearLayout p,int id,View.OnClickListener listener){MaterialButton b=new MaterialButton(this);FlatUi.button(b);b.setText(id);b.setFilterTouchesWhenObscured(true);b.setOnClickListener(listener);p.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    private boolean serviceAvailable(){
        try{ServiceInfo service=getPackageManager().getServiceInfo(new ComponentName(PACKAGE,SERVICE),0);
            return service.exported&&service.enabled&&TermuxCommandPolicy.PERMISSION.equals(service.permission);}
        catch(PackageManager.NameNotFoundException|RuntimeException e){return false;}
    }
    private boolean granted(){return checkSelfPermission(TermuxCommandPolicy.PERMISSION)==PackageManager.PERMISSION_GRANTED;}
    private void refresh(){if(status==null||direct)return;boolean available=serviceAvailable(),ok=available&&granted();grant.setEnabled(available&&!ok);if(run!=null)run.setEnabled(ok&&!dispatched&&!confirming);
        if(!dispatched)status.setText(!available?R.string.termux_r05_unavailable:ok?R.string.termux_r05_ready:R.string.termux_r05_permission_missing);}
    @Override protected void onResume(){super.onResume();refresh();}
    private void requestGrant(){if(!serviceAvailable()){refresh();return;}if(!granted())requestPermissions(new String[]{TermuxCommandPolicy.PERMISSION},REQUEST_PERMISSION);}
    @Override public void onRequestPermissionsResult(int code,String[] names,int[] results){super.onRequestPermissionsResult(code,names,results);if(code==REQUEST_PERMISSION){event(granted()?"PERMISSION_GRANTED":"PERMISSION_DENIED");refresh();}} // Never dispatch here.
    private boolean saveSettings(){if(!settingsRoute)return false;try{String p=TermuxCommandPolicy.checkedWorkingDirectory(workdir.getText().toString());String s=TermuxCommandPolicy.checkedShell((String)shell.getSelectedItem());if(!preferences.edit().putString("workdir",p).putString("shell",s).putBoolean("direct_r06_opt_in",directMode.isChecked()).commit()){status.setText(R.string.termux_r06_save_failed);return false;}workdir.setText(p);status.setText(R.string.termux_r05_saved);return true;}catch(IllegalArgumentException bad){status.setText(R.string.termux_r05_bad_settings);return false;}}
    private void confirmExecution(){
        if(source==null||dispatched||confirming||isFinishing()||!hasWindowFocus()||!granted()||!serviceAvailable()){refresh();return;}
        final String text,dir,interpreter;
        try{text=TermuxCommandPolicy.checkedCommand(source);dir=TermuxCommandPolicy.checkedWorkingDirectory(workdir.getText().toString());interpreter=TermuxCommandPolicy.checkedShell((String)shell.getSelectedItem());}
        catch(IllegalArgumentException invalid){status.setText(R.string.termux_r05_bad_settings);return;}
        confirming=true;run.setEnabled(false);
        androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.termux_r05_confirm_title).setMessage(getString(R.string.termux_r05_confirm_body,interpreter,dir))
            .setNegativeButton(android.R.string.cancel,(d,w)->event("CANCELLED"))
            .setPositiveButton(R.string.termux_r05_execute,(d,w)->dispatch(text,dir,interpreter)).create();
        dialog.setOnDismissListener(d->{confirming=false;refresh();});dialog.show();dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setFilterTouchesWhenObscured(true);
    }
    @Override public void onWindowFocusChanged(boolean focused){
        super.onWindowFocusChanged(focused);
        attemptDirect(focused);
    }
    private void attemptDirect(boolean focused){
        if(!focused||!direct||dispatched||source==null||status==null)return;
        // A saved opt-in explicitly trusts the whole pinned signed host process. It
        // does not attest the provenance of an individual click inside that process.
        if(!granted()||!serviceAvailable()||!preferences.getBoolean("direct_r06_opt_in",false)
                ||!TermuxCommandPolicy.freshRequest(requestId,requestTime,SystemClock.elapsedRealtime())
                ||!TermuxCommandPolicy.freshWall(requestWall,System.currentTimeMillis())){
            source=null;status.setText(R.string.termux_r06_direct_refused);event("DIRECT_REFUSED");return;
        }
        try{dispatch(source,TermuxCommandPolicy.checkedWorkingDirectory(preferences.getString("workdir",TermuxCommandPolicy.HOME)),
                TermuxCommandPolicy.checkedShell(preferences.getString("shell","zsh")));}
        catch(IllegalArgumentException invalid){source=null;status.setText(R.string.termux_r05_bad_settings);}
    }
    private void dispatch(String text,String dir,String interpreter){
        // Review confirmation or the explicitly authorized, fresh direct route. Never trust incoming paths/argv.
        if(dispatched||(!confirming&&!direct)||source==null||!source.equals(text)||isFinishing()||!granted()||!serviceAvailable()){event("DISPATCH_REFUSED");refresh();return;}
        Intent request=new Intent("com.termux.RUN_COMMAND").setClassName(PACKAGE,SERVICE)
            .putExtra("com.termux.RUN_COMMAND_PATH",TermuxCommandPolicy.EXECUTABLE)
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS",TermuxCommandPolicy.arguments(text,interpreter))
            .putExtra("com.termux.RUN_COMMAND_WORKDIR",TermuxCommandPolicy.checkedWorkingDirectory(dir))
            .putExtra("com.termux.RUN_COMMAND_RUNNER","terminal-session")
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND",false)
            .putExtra("com.termux.RUN_COMMAND_REPLACE_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS",false)
            .putExtra("com.termux.RUN_COMMAND_SESSION_ACTION","0");
        // Dispatch is at most once per preview even on an ambiguous platform failure.
        dispatched=true;if(run!=null)run.setEnabled(false);source=null;
        PendingIntent result;
        try{result=TermuxSessionReceipt.begin(this,requestId);}catch(RuntimeException failure){result=null;}
        if(result==null){TermuxSessionReceipt.refused(this,requestId);event("DISPATCH_REFUSED");status.setText(R.string.termux_r05_dispatch_failed);return;}
        request.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",result);
        try{ComponentName target=startService(request);if(target==null){TermuxSessionReceipt.uncertain(this,requestId);event("DISPATCH_REFUSED");status.setText(R.string.termux_r05_dispatch_failed);}
            else{TermuxSessionReceipt.submitted(this,requestId);event("DISPATCH_REQUESTED");status.setText(R.string.termux_r05_dispatched);submitted=true;}}
        catch(RuntimeException failure){TermuxSessionReceipt.uncertain(this,requestId);event("DISPATCH_REFUSED");status.setText(R.string.termux_r05_dispatch_failed);}
    }
    @Override protected void onStop(){super.onStop();if(direct&&submitted)finish();}
    @Override protected void onDestroy(){source=null;requestId=null;super.onDestroy();}
}
