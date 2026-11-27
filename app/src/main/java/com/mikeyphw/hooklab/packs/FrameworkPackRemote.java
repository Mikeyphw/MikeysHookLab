package com.mikeyphw.hooklab.packs;

import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import com.mikeyphw.hooklab.HookLabApp;
import com.mikeyphw.hooklab.runtime.pack.HookPack;
import com.mikeyphw.hooklab.runtime.pack.PackPublication;
import com.mikeyphw.hooklab.runtime.pack.PackSnapshot;
import io.github.libxposed.service.XposedService;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Manager-only binder port. Every operation binds to the same live framework instance. */
final class FrameworkPackRemote implements PackPublication.Remote {
    private final HookLabApp app;
    private final XposedService service;
    FrameworkPackRemote(HookLabApp app)throws IOException{this.app=app;service=app.currentService();require();}
    private void require()throws IOException {
        try{if(service==null||service!=app.currentService()||service.getApiVersion()!=102||(service.getFrameworkProperties()&XposedService.PROP_CAP_REMOTE)==0)throw new IOException("framework remote unavailable");}
        catch(RuntimeException|LinkageError e){throw new IOException("framework remote unavailable",e);}
    }
    private SharedPreferences prefs()throws IOException{require();return service.getRemotePreferences(PackSnapshot.GROUP);}
    @Override public Map<String,?> state()throws IOException{Map<String,?> result=new HashMap<>(prefs().getAll());require();return result;}
    @Override public Set<String> scope()throws IOException{require();List<String> scope=service.getScope();require();if(scope==null)throw new IOException("framework scope unavailable");return new HashSet<>(scope);}
    /** Read-only observed opt-in/lock state, from the same bound framework. */
    Map<String,Boolean> flags(String group,boolean safety)throws IOException {
        require();Map<String,?> observed=service.getRemotePreferences(group).getAll();require();
        Map<String,Boolean> flags=new HashMap<>();
        for(String id:com.mikeyphw.hooklab.runtime.pack.PackPolicy.hooks().keySet()){
            String key=safety?com.mikeyphw.hooklab.core.TweakDiagnostics.quarantineKey(id):com.mikeyphw.hooklab.core.TweakKeys.enabled(id);
            Object value=observed.get(key);if(value!=null&&!(value instanceof Boolean))throw new IOException("invalid hook flag");
            flags.put(id,Boolean.TRUE.equals(value));
        }return flags;
    }
    com.mikeyphw.hooklab.sdk.ModuleConfig.Frame configuration()throws IOException {
        require();Map<String,?> state=service.getRemotePreferences(com.mikeyphw.hooklab.sdk.ModuleConfig.GROUP).getAll();require();return com.mikeyphw.hooklab.sdk.ModuleConfig.read(state);
    }
    void configuration(com.mikeyphw.hooklab.sdk.ModuleConfig.Frame expected,com.mikeyphw.hooklab.sdk.ModuleConfig.Document update)throws IOException {
        if(!configuration().same(expected))throw new IOException("configuration changed; refresh first");
        Map<String,String> values=com.mikeyphw.hooklab.sdk.ModuleConfig.replacement(expected,update);
        require();SharedPreferences.Editor editor=service.getRemotePreferences(com.mikeyphw.hooklab.sdk.ModuleConfig.GROUP).edit();
        for(Map.Entry<String,String> e:values.entrySet())editor.putString(e.getKey(),e.getValue());
        if(!editor.commit())throw new IOException("configuration commit refused");require();
        if(!configuration().same(com.mikeyphw.hooklab.sdk.ModuleConfig.read(values)))throw new IOException("configuration readback refused");
    }
    @Override public void write(String name,byte[] bytes)throws IOException{
        require();if(!name.matches("hookpack-[0-9a-f]{64}")||bytes.length>HookPack.MAX_ARCHIVE)throw new IOException("remote file identity");
        try(ParcelFileDescriptor.AutoCloseOutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(service.openRemoteFile(name))){Os.ftruncate(out.getFD(),0);Os.lseek(out.getFD(),0,OsConstants.SEEK_SET);out.write(bytes);out.flush();Os.fsync(out.getFD());}
        catch(Exception|LinkageError e){throw new IOException("remote file write refused",e);}require();
    }
    @Override public byte[] read(String name)throws IOException{
        require();if(!name.matches("hookpack-[0-9a-f]{64}"))throw new IOException("remote file identity");
        try(ParcelFileDescriptor.AutoCloseInputStream in=new ParcelFileDescriptor.AutoCloseInputStream(service.openRemoteFile(name))){byte[] b=HookPack.readBounded(in,HookPack.MAX_ARCHIVE);require();return b;}
        catch(Exception|LinkageError e){throw new IOException("remote file read refused",e);}
    }
    @Override public void commit(PackSnapshot snapshot)throws IOException {
        if(!prefs().edit().putString(PackSnapshot.STATE,snapshot.text).putString(PackSnapshot.GENERATION,snapshot.generation).putBoolean(PackSnapshot.SAFE,false).commit())throw new IOException("snapshot commit refused");require();
    }
    @Override public void safeMode()throws IOException{if(!prefs().edit().putBoolean(PackSnapshot.SAFE,true).commit())throw new IOException("safe mode commit refused");require();}
}
