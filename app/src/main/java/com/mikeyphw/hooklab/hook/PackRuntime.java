package com.mikeyphw.hooklab.hook;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import com.mikeyphw.hooklab.core.TweakDiagnostics;
import com.mikeyphw.hooklab.core.TweakKeys;
import com.mikeyphw.hooklab.core.VerboseLogs;
import com.mikeyphw.hooklab.runtime.pack.HookPack;
import com.mikeyphw.hooklab.runtime.pack.PackHookTransaction;
import com.mikeyphw.hooklab.runtime.pack.PackPolicy;
import com.mikeyphw.hooklab.runtime.pack.PackSnapshot;
import com.mikeyphw.hooklab.sdk.HookSdk;
import com.mikeyphw.hooklab.sdk.PackHookApi;
import com.mikeyphw.hooklab.packs.VerifiedPackLoader;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Executable;
import java.util.Collections;
import java.util.Set;

/** Read-only host path. A bad imported registry never silently falls back to a built-in hook. */
final class PackRuntime {
    private final XposedModule module;
    private final Context host;
    private final ClassLoader hostLoader;
    private final SharedPreferences remote,settings,safety;
    private final PackSnapshot snapshot;
    private final Set<String> failed;
    private final String pkg,process;
    private final long version;
    private final com.mikeyphw.hooklab.sdk.ModuleConfig.Filter filter;
    private final com.mikeyphw.hooklab.sdk.ChatGptConfig.Options chatGpt;
    PackRuntime(XposedModule module,Context host,String pkg,String process,long version,ClassLoader loader,SharedPreferences settings,SharedPreferences safety,Set<String> failed,com.mikeyphw.hooklab.sdk.ModuleConfig.Filter filter,com.mikeyphw.hooklab.sdk.ChatGptConfig.Options chatGpt){
        this.module=module;this.host=host;this.pkg=pkg;this.process=process;this.version=version;hostLoader=loader;this.settings=settings;this.safety=safety;this.failed=failed;this.filter=filter;this.chatGpt=chatGpt;
        SharedPreferences preferences=null;PackSnapshot observed=null;
        try{preferences=module.getRemotePreferences(PackSnapshot.GROUP);observed=PackSnapshot.read(preferences.getAll());}
        catch(Exception|LinkageError e){module.log(Log.WARN,"HookLabPack","PACK_REGISTRY_REFUSED category="+TweakDiagnostics.failureCategory(e));}
        remote=preferences;snapshot=observed;
    }
    boolean eligible(String id){
        if(!PackPolicy.hooks().containsKey(id))return true; // MCC and other retained built-ins are independent.
        return snapshot!=null&&!PackSnapshot.DISABLED.equals(snapshot.choice(id));
    }
    boolean usesPack(String id){return PackPolicy.hooks().containsKey(id)&&snapshot!=null&&snapshot.choice(id).matches("[0-9a-f]{64}");}
    private boolean active(String id,String sha){
        try{
            if(remote==null||failed.contains(id))return false;
            PackSnapshot actual=PackSnapshot.read(remote.getAll());
            return actual.generation.equals(snapshot.generation)&&actual.allows(id,sha,pkg,process,version,true,settings.getBoolean(TweakKeys.enabled(id),false),!safety.getBoolean(TweakDiagnostics.quarantineKey(id),false));
        }catch(Exception|LinkageError e){return false;}
    }
    private boolean permits(Executable executable){
        Class<?> type=executable.getDeclaringClass();
        if(type==Activity.class)return "onPause".equals(executable.getName())||"onPostResume".equals(executable.getName());
        if(type==ClipboardManager.class)return "setPrimaryClip".equals(executable.getName())&&"com.openai.chatgpt".equals(pkg);
        ClassLoader defining=type.getClassLoader();
        if(defining==null||defining==Activity.class.getClassLoader()||defining==HookSdk.class.getClassLoader())return false;
        try{return Class.forName(type.getName(),false,hostLoader)==type;}
        catch(ClassNotFoundException|LinkageError e){return false;}
    }
    void install(String id)throws Exception {
        if(!usesPack(id))throw new IllegalStateException("pack not selected");String sha=snapshot.choice(id);
        if(!active(id,sha))throw new IllegalStateException("pack activation changed");
        final HookPack.Verified pack;
        try(ParcelFileDescriptor.AutoCloseInputStream input=new ParcelFileDescriptor.AutoCloseInputStream(module.openRemoteFile(PackPolicy.remoteFile(sha)))){
            pack=HookPack.verify(HookPack.readBounded(input,HookPack.MAX_ARCHIVE),snapshot.key());
        }
        PackPolicy.verifyMigration(pack);
        if(!snapshot.channel.equals(pack.channel)||"test".equals(pack.channel)&&(module.getModuleApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0||!sha.equals(pack.archiveSha256)||!PackPolicy.hook(id).moduleId.equals(pack.moduleId)||!active(id,sha))throw new IllegalStateException("pack source/activation mismatch");
        // Direct buffer, verified bytes only. Parent must define the one shared SDK.
        Class<? extends HookSdk.Entrypoint> type=VerifiedPackLoader.load(pack);
        if(!active(id,sha))throw new IllegalStateException("pack activation changed");
        PackHookTransaction transaction=new PackHookTransaction(id,new PackHookTransaction.Backend(){
            @Override public boolean permits(Executable e){return PackRuntime.this.permits(e);}
            @Override public PackHookTransaction.Handle install(Executable e,String name,PackHookApi.Callback callback){
                XposedInterface.HookHandle handle=module.hook(e).setId(name).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->callback.intercept(new PackHookApi.Chain(){
                    @Override public Object getThisObject(){return chain.getThisObject();}
                    @Override public Object getArg(int index){return chain.getArg(index);}
                    @Override public Object proceed()throws Throwable{return chain.proceed();}
                    @Override public Object proceed(Object[] args)throws Throwable{return chain.proceed(args);}
                }));
                return handle::unhook;
            }
            @Override public void log(int priority,String tag,String message){module.log(priority,tag,message);}
            @Override public void verbose(String event){VerboseLogs.emit(VerboseLogs.Event.valueOf(event));}
            @Override public void failed(String category){failed.add(id);module.log(Log.WARN,"HookLabPack","PACK_CALLBACK_QUARANTINED id="+id+" category="+category);}
        },()->active(id,sha));
        try{
            HookSdk.Entrypoint entry=type.getDeclaredConstructor().newInstance();
            HookSdk.Context context=new HookSdk.Context(pkg,process,version,snapshot.generation,hostLoader,
                    new HookSdk.Settings(Collections.singletonMap(id,true),PackPolicy.settingsSchema(pack)==2?filter:com.mikeyphw.hooklab.sdk.ModuleConfig.defaultsFilter(),PackPolicy.settingsSchema(pack)==2?chatGpt:com.mikeyphw.hooklab.sdk.ChatGptConfig.defaultsOptions()),event->module.log(Log.INFO,"HookLabPack","SDK event="+event.name()),
                    (ignored,marker)->{throw new IllegalStateException("probe registration not available in production packs");},host,transaction);
            entry.install(context);transaction.commit();
            module.log(Log.INFO,"HookLabPack","PACK_INSTALLED id="+id+" module="+pack.moduleId+" version="+pack.version+" sha256="+sha+" generation="+snapshot.generation);
        }catch(Throwable failure){transaction.rollback();TweakDiagnostics.rethrowFatal(failure);if(failure instanceof Exception)throw (Exception)failure;throw new IllegalStateException("pack installation failed",failure);}
    }
}
