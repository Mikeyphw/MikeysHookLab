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
