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
import dalvik.system.InMemoryDexClassLoader;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Debug-only real framework experiment; not the future general module loader. */
final class ProbeBridge {
    private ProbeBridge() {}
    static boolean supportsHost(String pkg) { return ProbeProtocol.TARGET.equals(pkg); }
    static void dispatch(XposedModule module,Context context,String pkg,String process,long version,ClassLoader hostLoader) {
        try {
            if(module.getApiVersion()!=102 || (module.getFrameworkProperties()&XposedInterface.PROP_CAP_REMOTE)==0)throw new IllegalStateException("remote unavailable");
            if((context.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0 ||
                    (module.getModuleApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0)throw new IllegalStateException("debug only");
            SharedPreferences prefs=module.getRemotePreferences(ProbeProtocol.GROUP);
            boolean sameSigner=context.getPackageManager().checkSignatures(pkg,ProbeProtocol.MANAGER)==PackageManager.SIGNATURE_MATCH;
            // API102 scope is established by actual framework injection; no inferred UI state.
            if(!ProbeProtocol.eligible(pkg,process,version,true,prefs.getBoolean(ProbeProtocol.ENABLED,false),sameSigner))return;
            String generation=BuiltinRegistry.current().generation;
            if(!generation.equals(prefs.getString(ProbeProtocol.GENERATION,null)))return;
            String claimed=prefs.getString(ProbeProtocol.DIGEST,null);
            byte[] expected;
            try(ZipFile zip=new ZipFile(module.getModuleApplicationInfo().sourceDir)) {
                ZipEntry entry=zip.getEntry("assets/"+ProbeProtocol.ASSET);
                if(entry==null||entry.getSize()<112||entry.getSize()>ProbeProtocol.MAX_BYTES)throw new IllegalStateException("missing built-in fixture");
                try(java.io.InputStream in=zip.getInputStream(entry)){expected=ProbeProtocol.read(in);}
            }
            if(!ProbeProtocol.digest(expected).equals(claimed))return;
            byte[] bytes;
            try(ParcelFileDescriptor.AutoCloseInputStream in=new ParcelFileDescriptor.AutoCloseInputStream(module.openRemoteFile(ProbeProtocol.file(claimed)))){bytes=ProbeProtocol.read(in);}
            if(!ProbeProtocol.matches(expected,bytes,claimed)||!prefs.getBoolean(ProbeProtocol.ENABLED,false)||
                    !generation.equals(prefs.getString(ProbeProtocol.GENERATION,null))||!claimed.equals(prefs.getString(ProbeProtocol.DIGEST,null)))return;
            ClassLoader sdkLoader=HookSdk.class.getClassLoader();
            InMemoryDexClassLoader dex=new InMemoryDexClassLoader(ByteBuffer.wrap(bytes).asReadOnlyBuffer(),sdkLoader);
            Class<?> type=Class.forName(ProbeProtocol.ENTRY,false,dex);
            if(type.getClassLoader()!=dex||!HookSdk.Entrypoint.class.isAssignableFrom(type))throw new IllegalStateException("SDK class identity");
            HookSdk.Entrypoint fixture=type.asSubclass(HookSdk.Entrypoint.class).getDeclaredConstructor().newInstance();
            AtomicReference<HookSdk.Marker> marker=new AtomicReference<>();
            HookSdk.Context sdk=new HookSdk.Context(pkg,process,version,generation,hostLoader,
                    new HookSdk.Settings(Collections.singletonMap(ProbeProtocol.ID,true)),
                    code->module.log(Log.INFO,"HookLabProbe","SDK event="+code.name()),
                    (id,callback)->{if(!ProbeProtocol.ID.equals(id)||callback==null||!marker.compareAndSet(null,callback))throw new IllegalStateException("probe registration refused");});
            fixture.install(sdk);
            HookSdk.Marker callback=marker.get();if(callback==null)throw new IllegalStateException("probe did not register");
            Class<?> target=Class.forName(ProbeProtocol.TARGET+".ProbeTargetActivity",false,hostLoader);
            module.hook(target.getDeclaredMethod("sample")).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).setId("hooklab.mod01.probe.marker").intercept(chain->{
                Object original=chain.proceed();
                if(!"BASE".equals(original))return original;
                try {
                    // Recheck explicit enable before invoking the benign callback; no host writes.
                    if(!prefs.getBoolean(ProbeProtocol.ENABLED,false)||!claimed.equals(prefs.getString(ProbeProtocol.DIGEST,null))||!generation.equals(prefs.getString(ProbeProtocol.GENERATION,null)))return original;
                    String result=callback.after((String)original);
                    if(!"MOD01_PROBE_OK".equals(result))return original;
                    module.log(Log.INFO,"HookLabProbe","MOD01_PROBE_EXECUTED sha256="+claimed+" generation="+generation);return result;
                }catch(RuntimeException|LinkageError failure){return original;}
            });
            module.log(Log.INFO,"HookLabProbe","MOD01_PROBE_REGISTERED sha256="+claimed+" properties="+module.getFrameworkProperties());
        }catch(Exception|LinkageError failure){module.log(Log.WARN,"HookLabProbe","MOD01_PROBE_REFUSED category="+failure.getClass().getSimpleName());}
    }
}
