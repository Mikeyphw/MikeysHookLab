package com.mikeyphw.hooklab.probe;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.view.WindowInsets;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.mikeyphw.hooklab.HookLabApp;
import com.mikeyphw.hooklab.R;
import com.mikeyphw.hooklab.runtime.BuiltinRegistry;
import io.github.libxposed.service.XposedService;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/** Explicit manager-side publication of one APK-owned benign debug payload. */
public final class TransportProbeActivity extends Activity {
    private HookLabApp app;
    private TextView status;
    private Button inspect, scope, publish, disable;
    private boolean busy;
    private interface Operation { String run(XposedService service) throws Exception; }
    private void applySystemInsets(ScrollView root) {
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view,insets)->{
                Insets safe=insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                view.setPadding(safe.left,safe.top,safe.right,safe.bottom);
                return WindowInsets.CONSUMED;
            });
            root.requestApplyInsets();
        }
        // API 29 retains the platform's default decor-fitting window.
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);app=(HookLabApp)getApplication();
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        int spacing=Math.round(16*getResources().getDisplayMetrics().density);
        content.setPadding(spacing,spacing,spacing,spacing);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(content);
        status=new TextView(this);status.setText(R.string.mod01_probe_intro);content.addView(status);
        inspect=button(content,R.string.mod01_probe_inspect,()->execute(this::inspect));
        scope=button(content,R.string.mod01_probe_scope,()->execute(this::scope));
        publish=button(content,R.string.mod01_probe_publish,()->execute(this::publish));
        disable=button(content,R.string.mod01_probe_disable,()->execute(this::disable));
        setContentView(scroll);
        applySystemInsets(scroll);
    }
    private Button button(LinearLayout parent,int text,Runnable action){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->action.run());parent.addView(b);return b;}
    private void setBusy(boolean value){busy=value;inspect.setEnabled(!value);scope.setEnabled(!value);publish.setEnabled(!value);disable.setEnabled(!value);}
    private void execute(Operation operation){
        if(busy)return;setBusy(true);
        app.frameworkTask(()->{
            String message;
            try{XposedService service=app.currentService();requireService(service);message=operation.run(service);}
            catch(Exception|LinkageError failure){message=getString(R.string.mod01_probe_refused,failure.getClass().getSimpleName());}
            final String output=message;runOnUiThread(()->{if(!isDestroyed()){status.setText(output);setBusy(false);}});
        });
    }
    private void requireService(XposedService service){if(service==null||service!=app.currentService()||service.getApiVersion()!=102||(service.getFrameworkProperties()&XposedService.PROP_CAP_REMOTE)==0)throw new IllegalStateException("remote unavailable");}
    private boolean scoped(XposedService service){requireService(service);List<String> values=service.getScope();return values!=null&&values.contains(ProbeProtocol.TARGET);}
    private void requireTarget() throws PackageManager.NameNotFoundException {
        PackageManager pm=getPackageManager();PackageInfo target=pm.getPackageInfo(ProbeProtocol.TARGET,0);
        if(target.getLongVersionCode()!=1||target.applicationInfo==null||(target.applicationInfo.flags&ApplicationInfo.FLAG_DEBUGGABLE)==0||pm.checkSignatures(getPackageName(),ProbeProtocol.TARGET)!=PackageManager.SIGNATURE_MATCH)throw new IllegalStateException("owned target identity refused");
    }
    private String inspect(XposedService service) throws Exception {
        requireTarget();SharedPreferences p=service.getRemotePreferences(ProbeProtocol.GROUP);
        return getString(R.string.mod01_probe_report,service.getFrameworkName(),service.getFrameworkVersion(),service.getFrameworkVersionCode(),service.getApiVersion(),service.getFrameworkProperties(),scoped(service),p.getBoolean(ProbeProtocol.ENABLED,false),p.getString(ProbeProtocol.DIGEST,"none"),BuiltinRegistry.current().generation,getString(R.string.hooklab_build_source));
    }
    private String scope(XposedService service) throws Exception {
        requireTarget();if(scoped(service))return getString(R.string.mod01_probe_already_scoped);
        requireService(service);service.requestScope(Collections.singletonList(ProbeProtocol.TARGET),new XposedService.OnScopeEventListener(){
            @Override public void onScopeRequestApproved(List<String> approved){runOnUiThread(()->{if(!isDestroyed())status.setText(R.string.mod01_probe_scope_callback);});}
            @Override public void onScopeRequestFailed(String message){runOnUiThread(()->{if(!isDestroyed())status.setText(R.string.mod01_probe_scope_failed);});}
        });
        return getString(R.string.mod01_probe_scope_pending);
    }
    private void setDisabled(XposedService service,SharedPreferences p){requireService(service);if(!p.edit().putBoolean(ProbeProtocol.ENABLED,false).commit()||p.getBoolean(ProbeProtocol.ENABLED,true))throw new IllegalStateException("disable readback refused");requireService(service);}
    private String disable(XposedService service){setDisabled(service,service.getRemotePreferences(ProbeProtocol.GROUP));return getString(R.string.mod01_probe_disabled);}
    private byte[] readRemote(XposedService service,String name) throws Exception {
        requireService(service);try(ParcelFileDescriptor.AutoCloseInputStream in=new ParcelFileDescriptor.AutoCloseInputStream(service.openRemoteFile(name))){return ProbeProtocol.read(in);}
    }
    private String publish(XposedService service) throws Exception {
        requireTarget();if(!scoped(service))throw new IllegalStateException("scope required");
        byte[] data;try(InputStream in=getAssets().open(ProbeProtocol.ASSET)){data=ProbeProtocol.read(in);}
        String digest=ProbeProtocol.digest(data),name=ProbeProtocol.file(digest),generation=BuiltinRegistry.current().generation;
        SharedPreferences p=service.getRemotePreferences(ProbeProtocol.GROUP);setDisabled(service,p);
        try {
            requireService(service);
            try(ParcelFileDescriptor.AutoCloseOutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(service.openRemoteFile(name))){
                Os.ftruncate(out.getFD(),0);Os.lseek(out.getFD(),0,OsConstants.SEEK_SET);out.write(data);out.flush();Os.fsync(out.getFD());
            }
            if(!ProbeProtocol.matches(data,readRemote(service,name),digest)||!scoped(service))throw new IllegalStateException("publication readback refused");
            requireService(service);
            if(!p.edit().putString(ProbeProtocol.DIGEST,digest).putString(ProbeProtocol.GENERATION,generation).putBoolean(ProbeProtocol.ENABLED,true).commit()||
                    !p.getBoolean(ProbeProtocol.ENABLED,false)||!digest.equals(p.getString(ProbeProtocol.DIGEST,null))||!generation.equals(p.getString(ProbeProtocol.GENERATION,null)))throw new IllegalStateException("activation readback refused");
            requireService(service);return inspect(service);
        }catch(Exception|LinkageError failure){setDisabled(service,p);throw failure;}
    }
}
