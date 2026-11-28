package com.mikeyphw.hooklab.packs;

import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import com.mikeyphw.hooklab.HookLabApp;
import com.mikeyphw.hooklab.sdk.ModuleConfig;
import com.mikeyphw.hooklab.sdk.ChatGptConfig;
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
    private static final Object CONFIGURATION_LOCK=new Object();
    static final class Configuration {
        final XposedService service;final String module;final ModuleConfig.ProfileDocument<?,?> current,previous;
        Configuration(XposedService service,String module,ModuleConfig.ProfileDocument<?,?> current,ModuleConfig.ProfileDocument<?,?> previous){this.service=service;this.module=module;this.current=current;this.previous=previous;}
        boolean same(Configuration other){return other!=null&&service==other.service&&module.equals(other.module)&&current.canonical().equals(other.current.canonical())&&(previous==null?other.previous==null:other.previous!=null&&previous.canonical().equals(other.previous.canonical()));}
    }
    private static String configGroup(String module){if(ModuleConfig.MODULE.equals(module))return ModuleConfig.GROUP;if(ChatGptConfig.MODULE.equals(module))return ChatGptConfig.GROUP;throw new IllegalArgumentException("unknown configuration module");}
    private Configuration frame(String module,Map<String,?> state){
        if(ModuleConfig.MODULE.equals(module)){ModuleConfig.Frame f=ModuleConfig.read(state);return new Configuration(service,module,f.current,f.previous);}
        if(ChatGptConfig.MODULE.equals(module)){ChatGptConfig.Frame f=ChatGptConfig.read(state);return new Configuration(service,module,f.current,f.previous);}
        throw new IllegalArgumentException("unknown configuration module");
    }
    Configuration configuration(String module)throws IOException {
        require();Map<String,?> state=service.getRemotePreferences(configGroup(module)).getAll();require();return frame(module,state);
    }
    void configuration(Configuration expected,ModuleConfig.ProfileDocument<?,?> update)throws IOException {
        synchronized(CONFIGURATION_LOCK){
            require();if(expected.service!=service||!expected.module.equals(update.module())||!configuration(expected.module).same(expected))throw new IOException("configuration changed; refresh first");
            // Reparse the typed document at the transaction boundary before committing only the reviewed fields.
            ModuleConfig.ProfileDocument<?,?> checked=ModuleConfig.parseForModule(expected.module,update.canonical().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Map<String,String> values=new HashMap<>();values.put(ModuleConfig.CURRENT,checked.canonical());values.put(ModuleConfig.GENERATION,checked.digest());values.put(ModuleConfig.PREVIOUS,expected.current.canonical());
            Configuration wanted=frame(expected.module,values);
            require();SharedPreferences.Editor editor=service.getRemotePreferences(configGroup(expected.module)).edit();
            for(Map.Entry<String,String> e:values.entrySet())editor.putString(e.getKey(),e.getValue());
            if(!editor.commit())throw new IOException("configuration commit refused");require();
            if(!configuration(expected.module).same(wanted))throw new IOException("configuration readback refused");
        }
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
