package com.mikeyphw.hooklab.runtime.pack;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Single manager publication owner. Remote bytes are immutable digest-addressed objects. */
public final class PackPublication {
    private PackPublication(){}
    public interface Remote {
        Map<String,?> state() throws IOException;
        Set<String> scope() throws IOException;
        void write(String name,byte[] bytes)throws IOException;
        byte[] read(String name)throws IOException;
        void commit(PackSnapshot snapshot)throws IOException;
        void safeMode()throws IOException;
    }
    private static Set<String> digests(PackSnapshot s){Set<String> result=new TreeSet<>();for(String v:s.choices().values())if(!PackSnapshot.BUILTIN.equals(v)&&!PackSnapshot.DISABLED.equals(v))result.add(v);return result;}
    private static void validateScope(PackSnapshot s,Remote remote)throws IOException {
        Set<String> scope=remote.scope();if(scope==null)throw new IOException("scope unavailable");
        for(Map.Entry<String,String> e:s.choices().entrySet())if(e.getValue().matches("[0-9a-f]{64}")&&!scope.contains(PackPolicy.hook(e.getKey()).packageName))throw new IOException("selected package lacks scope");
    }
    private static void verifyRemote(PackSnapshot s,Remote remote,String sha)throws IOException,GeneralSecurityException {
        HookPack.Verified pack=HookPack.verify(remote.read(PackPolicy.remoteFile(sha)),s.key());PackPolicy.verifyMigration(pack);
        if(!s.channel.equals(pack.channel)||!sha.equals(pack.archiveSha256))throw new IOException("remote archive digest mismatch");
        for(Map.Entry<String,String> e:s.choices().entrySet())if(sha.equals(e.getValue())&&!PackPolicy.hook(e.getKey()).moduleId.equals(pack.moduleId))throw new IOException("remote module identity mismatch");
    }
    public static PackSnapshot publish(PackStore store,Remote remote)throws IOException,GeneralSecurityException {
        return store.publication((s,access)->{
            validateScope(s,remote);
            for(String sha:digests(s)){
                byte[] bytes=access.bytes(sha);
                // A matching existing remote file is never truncated/replaced.
                boolean present=false;try{verifyRemote(s,remote,sha);present=true;}catch(IOException|GeneralSecurityException|IllegalArgumentException ignored){/* absent or incomplete, retry staged bytes */}
                if(!present){remote.write(PackPolicy.remoteFile(sha),bytes);verifyRemote(s,remote,sha);}
            }
            validateScope(s,remote);access.prepare();remote.commit(s);PackSnapshot actual=PackSnapshot.read(remote.state());
            if(!s.generation.equals(actual.generation)||!s.text.equals(actual.text))throw new IOException("publication readback refused");
            access.acknowledge();return s;
        });
    }
    public static boolean reconcile(PackStore store,Remote remote)throws IOException,GeneralSecurityException {
        return store.publication((desired,access)->{
            Map<String,?> values=remote.state();PackSnapshot actual=PackSnapshot.read(values);
            if(!desired.generation.equals(actual.generation)||!values.containsKey(PackSnapshot.STATE))return false;
            for(String sha:digests(actual)){access.bytes(sha);verifyRemote(actual,remote,sha);}access.acknowledge();return true;
        });
    }
    /** Independent disable path; no file, key, scope or current registry validation needed. */
    public static void stop(Remote remote)throws IOException {
        remote.safeMode();if(!Boolean.TRUE.equals(remote.state().get(PackSnapshot.SAFE)))throw new IOException("safe mode readback refused");
    }
}
