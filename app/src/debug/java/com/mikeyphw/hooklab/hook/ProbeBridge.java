package com.mikeyphw.hooklab.hook;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import com.mikeyphw.hooklab.probe.ProbeProtocol;
import com.mikeyphw.hooklab.runtime.BuiltinRegistry;
import com.mikeyphw.hooklab.sdk.HookSdk;
import com.mikeyphw.hooklab.probe.ProbeDexLoader;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Debug-only real framework experiment; not the future general module loader. */
final class ProbeBridge {
    private ProbeBridge() {}
    static boolean supportsHost(String pkg) { return ProbeProtocol.TARGET.equals(pkg); }
    private static void refused(XposedModule module,String stage,String reason) {
        module.log(Log.WARN,"HookLabProbe","MOD01_PROBE_REFUSED stage="+stage+" category="+reason);
    }
    private static void inactive(XposedModule module,String reason) {
        module.log(Log.INFO,"HookLabProbe","MOD01_PROBE_INACTIVE reason="+reason);
    }
    static void dispatch(XposedModule module,Context context,String pkg,String process,long version,ClassLoader hostLoader) {
        String stage="FRAMEWORK";
        try {
            module.log(Log.INFO,"HookLabProbe","MOD01_PROBE_DISPATCH");
            if(module.getApiVersion()!=102 || (module.getFrameworkProperties()&XposedInterface.PROP_CAP_REMOTE)==0)throw new IllegalStateException("remote unavailable");
            stage="DEBUG_IDENTITY";
            if((context.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0 ||
                    (module.getModuleApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0)throw new IllegalStateException("debug only");
            stage="REMOTE_PREFERENCES";
            SharedPreferences prefs=module.getRemotePreferences(ProbeProtocol.GROUP);
            stage="TARGET_IDENTITY";
            boolean sameSigner=context.getPackageManager().checkSignatures(pkg,ProbeProtocol.MANAGER)==PackageManager.SIGNATURE_MATCH;
            // API102 scope is established by actual framework injection; no inferred UI state.
            if(!ProbeProtocol.eligible(pkg,process,version,true,true,sameSigner)){refused(module,stage,"TARGET_IDENTITY_MISMATCH");return;}
            if(!prefs.getBoolean(ProbeProtocol.ENABLED,false)){inactive(module,"DISABLED");return;}
            stage="GENERATION";
            String generation=BuiltinRegistry.current().generation;
            if(!generation.equals(prefs.getString(ProbeProtocol.GENERATION,null))){refused(module,stage,"GENERATION_MISMATCH");return;}
            String claimed=prefs.getString(ProbeProtocol.DIGEST,null);
            stage="OWNED_ASSET";
            byte[] expected;
            try(ZipFile zip=new ZipFile(module.getModuleApplicationInfo().sourceDir)) {
                ZipEntry entry=zip.getEntry("assets/"+ProbeProtocol.ASSET);
                if(entry==null||entry.getSize()<112||entry.getSize()>ProbeProtocol.MAX_BYTES)throw new IllegalStateException("missing built-in fixture");
                try(java.io.InputStream in=zip.getInputStream(entry)){expected=ProbeProtocol.read(in);}
            }
            if(!ProbeProtocol.digest(expected).equals(claimed)){refused(module,stage,"DIGEST_MISMATCH");return;}
            stage="REMOTE_FILE";
            byte[] bytes;
            try(ParcelFileDescriptor.AutoCloseInputStream in=new ParcelFileDescriptor.AutoCloseInputStream(module.openRemoteFile(ProbeProtocol.file(claimed)))){bytes=ProbeProtocol.read(in);}
            stage="VERIFY_REMOTE_BYTES";
            if(!ProbeProtocol.matches(expected,bytes,claimed)){refused(module,stage,"BYTE_MISMATCH");return;}
            if(!prefs.getBoolean(ProbeProtocol.ENABLED,false)||
                    !generation.equals(prefs.getString(ProbeProtocol.GENERATION,null))||!claimed.equals(prefs.getString(ProbeProtocol.DIGEST,null))){inactive(module,"ACTIVATION_CHANGED");return;}
            stage="DEX_LOAD";
            HookSdk.Entrypoint fixture=ProbeDexLoader.loadVerified(expected,bytes,claimed);
            stage="SDK_INSTALL";
            AtomicReference<HookSdk.Marker> marker=new AtomicReference<>();
            HookSdk.Context sdk=new HookSdk.Context(pkg,process,version,generation,hostLoader,
                    new HookSdk.Settings(Collections.singletonMap(ProbeProtocol.ID,true)),
                    code->module.log(Log.INFO,"HookLabProbe","SDK event="+code.name()),
                    (id,callback)->{if(!ProbeProtocol.ID.equals(id)||callback==null||!marker.compareAndSet(null,callback))throw new IllegalStateException("probe registration refused");});
            fixture.install(sdk);
            HookSdk.Marker callback=marker.get();if(callback==null)throw new IllegalStateException("probe did not register");
            stage="HOOK_REGISTER";
            Class<?> target=Class.forName(ProbeProtocol.TARGET+".ProbeTargetActivity",false,hostLoader);
            module.hook(target.getDeclaredMethod("sample")).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).setId("hooklab.mod01.probe.marker").intercept(chain->{
                Object original=chain.proceed();
                if(!"BASE".equals(original))return original;
                try {
                    // Recheck explicit enable before invoking the benign callback; no host writes.
                    if(!prefs.getBoolean(ProbeProtocol.ENABLED,false)||!claimed.equals(prefs.getString(ProbeProtocol.DIGEST,null))||!generation.equals(prefs.getString(ProbeProtocol.GENERATION,null))){inactive(module,"ACTIVATION_CHANGED");return original;}
                    String result=callback.after((String)original);
                    if(!"MOD01_PROBE_OK".equals(result)){refused(module,"CALLBACK","UNEXPECTED_MARKER");return original;}
                    module.log(Log.INFO,"HookLabProbe","MOD01_PROBE_EXECUTED sha256="+claimed+" generation="+generation);return result;
                }catch(RuntimeException|LinkageError failure){refused(module,"CALLBACK",failure.getClass().getSimpleName());return original;}
            });
            module.log(Log.INFO,"HookLabProbe","MOD01_PROBE_REGISTERED sha256="+claimed+" properties="+module.getFrameworkProperties());
        }catch(Exception|LinkageError failure){refused(module,stage,failure.getClass().getSimpleName());}
    }
}
