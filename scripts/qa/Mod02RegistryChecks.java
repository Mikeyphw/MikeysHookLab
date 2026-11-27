import com.mikeyphw.hooklab.runtime.pack.HookPack;
import com.mikeyphw.hooklab.runtime.pack.PackPolicy;
import com.mikeyphw.hooklab.runtime.pack.PackPublication;
import com.mikeyphw.hooklab.runtime.pack.PackSnapshot;
import com.mikeyphw.hooklab.runtime.pack.PackStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Real signed pack, durable files, and exact production publication methods. Fake transport only. */
public final class Mod02RegistryChecks {
    private static int count;
    private static final String ID="wordmaster.night.filter", MODULE="wordmaster", PKG="com.fugo.wowsolitaire";
    private static final long VERSION=56240403L;
    private Mod02RegistryChecks(){}
    private interface Step {void run()throws Exception;}
    private static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);count++;}
    private static void refuse(Step step,String label)throws Exception{try{step.run();throw new AssertionError("accepted "+label);}catch(IllegalArgumentException|IOException|java.security.GeneralSecurityException expected){count++;}}
    private static final class Remote implements PackPublication.Remote {
        final Map<String,Object> values=new HashMap<>();final Map<String,byte[]> files=new HashMap<>();final Set<String> scopes=new HashSet<>();
        boolean badRead,failWrite,failCommit,badCommit;int writes,commits;
        public Map<String,?> state(){return new HashMap<>(values);}
        public Set<String> scope(){return new HashSet<>(scopes);}
        public void write(String name,byte[] bytes)throws IOException{writes++;if(failWrite)throw new IOException("fixture disk failure");files.put(name,bytes.clone());}
        public byte[] read(String name)throws IOException{byte[] b=files.get(name);if(b==null)throw new IOException("missing");b=b.clone();if(badRead)b[0]^=1;return b;}
        public void commit(PackSnapshot s)throws IOException{commits++;if(failCommit)throw new IOException("fixture commit failure");values.put(PackSnapshot.STATE,s.text);values.put(PackSnapshot.GENERATION,badCommit?"f".repeat(64):s.generation);values.put(PackSnapshot.SAFE,false);}
        public void safeMode(){values.put(PackSnapshot.SAFE,true);}
    }
    private static HookPack.Verified install(PackStore s,byte[] pack)throws Exception{return s.install(new ByteArrayInputStream(pack),HookPack.digest(pack));}
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]);byte[] pub=Files.readAllBytes(Path.of(args[1])),other=Files.readAllBytes(Path.of(args[2])),a=Files.readAllBytes(Path.of(args[3])),b=Files.readAllBytes(Path.of(args[4])),wrong=Files.readAllBytes(Path.of(args[5]));
        String sha=HookPack.signerFingerprint(pub),aa=HookPack.digest(a),bb=HookPack.digest(b);
        PackStore store=new PackStore(root);Remote remote=new Remote();
        check(PackSnapshot.read(remote.state()).choice(ID).equals(PackSnapshot.BUILTIN),"legacy");
        refuse(()->PackSnapshot.read(Map.of(PackSnapshot.SAFE,false)),"partial state");
        refuse(()->install(store,a),"unknown key");refuse(()->store.trust(pub,"f".repeat(64),"test"),"unconfirmed fingerprint");
        store.trust(pub,sha,"test");refuse(()->store.trust(other,HookPack.signerFingerprint(other),"test"),"silent rotation");
        refuse(()->store.install(new ByteArrayInputStream(a),bb),"confirmation changed");refuse(()->install(store,wrong),"wrong signer");
        byte[] corrupt=a.clone();corrupt[30]^=1;refuse(()->install(store,corrupt),"corrupt archive");
        AtomicInteger faults=new AtomicInteger();PackStore failArchive=new PackStore(root,stage->{if(stage.equals("ARCHIVE_RENAME")){faults.incrementAndGet();throw new IOException("before archive rename");}});
        refuse(()->install(failArchive,a),"archive interruption");check(store.state().versions().isEmpty()&&faults.get()==1,"old state survives archive failure");
        PackStore failState=new PackStore(root,stage->{if(stage.equals("STATE_RENAME"))throw new IOException("before state rename");});
        refuse(()->install(failState,a),"state interruption");check(store.state().versions().isEmpty(),"unreferenced verified archive not active");
        install(store,a);check(store.state().versions().get(MODULE).current.equals(aa),"idempotent import recovery");
        check(store.state().snapshot().choice(ID).equals(PackSnapshot.BUILTIN),"import alone default off");
        refuse(()->store.select(ID,false),"denied scope");store.select(ID,true);
        refuse(()->PackPublication.publish(store,remote),"scope refused before publication");check(remote.writes==0&&remote.commits==0,"no remote mutation");
        remote.scopes.add(PKG);remote.failWrite=true;refuse(()->PackPublication.publish(store,remote),"remote staged write failure");check(remote.commits==0,"failed publication keeps prior generation");
        remote.failWrite=false;remote.badRead=true;refuse(()->PackPublication.publish(store,remote),"remote bytes tampered");check(remote.commits==0,"tamper blocks activation");
        remote.badRead=false;PackSnapshot active=PackPublication.publish(store,remote);check(active.choice(ID).equals(aa)&&store.acknowledged(active),"verified publication");
        check(active.allows(ID,aa,PKG,PKG,VERSION,true,true,true),"eligible");
        check(!active.allows(ID,aa,PKG,PKG+":child",VERSION,true,true,true),"child process refused");
        check(!active.allows(ID,aa,PKG,PKG,VERSION+1,true,true,true),"wrong version refused");
        check(!active.allows(ID,aa,PKG,PKG,VERSION,false,true,true),"unscoped refused");
        check(!active.allows(ID,aa,PKG,PKG,VERSION,true,false,true),"default off");
        check(!active.allows(ID,aa,PKG,PKG,VERSION,true,true,false),"quarantine");
        check(!active.allows(ID,bb,PKG,PKG,VERSION,true,true,true),"wrong digest");
        int writes=remote.writes;PackPublication.publish(store,remote);check(remote.writes==writes,"immutable remote file reuse");
        install(store,b);check(PackSnapshot.read(remote.state()).generation.equals(active.generation),"update staged only");
        refuse(()->install(store,a),"stale downgrade");
        remote.failCommit=true;refuse(()->PackPublication.publish(store,remote),"commit failure");check(PackSnapshot.read(remote.state()).generation.equals(active.generation),"old usable generation retained");
        remote.failCommit=false;PackStore failAck=new PackStore(root,stage->{if(stage.equals("PUBLISHED_RENAME"))throw new IOException("lost acknowledgement");});
        refuse(()->PackPublication.publish(failAck,remote),"ack crash");PackSnapshot updated=PackSnapshot.read(remote.state());check(updated.choice(ID).equals(bb)&&!store.acknowledged(updated),"remote ahead of local receipt");
        int commits=remote.commits;check(PackPublication.reconcile(new PackStore(root),remote)&&remote.commits==commits&&store.acknowledged(updated),"recovery observes, never replays");
        store.rollback(MODULE);check(store.state().snapshot().choice(ID).equals(aa),"explicit retained rollback");PackPublication.publish(store,remote);refuse(()->install(store,a),"highwater survives rollback");
        PackPublication.stop(remote);refuse(()->PackSnapshot.read(remote.state()),"independent safe mode");
        store.disable(ID);remote.scopes.clear();PackSnapshot disabled=PackPublication.publish(store,remote);check(disabled.choice(ID).equals(PackSnapshot.DISABLED),"disable with absent scope");
        store.remove(MODULE);check(store.state().versions().isEmpty()&&store.state().snapshot().choice(ID).equals(PackSnapshot.DISABLED),"removal no legacy resurrection");
        refuse(()->install(store,a),"highwater survives removal");
        store.revoke(sha);refuse(()->store.trust(pub,sha,"test"),"revoked key");store.trust(other,HookPack.signerFingerprint(other),"test");refuse(()->install(store,b),"old signer refused after rotation");
        check(store.state().revoked().contains(sha),"revocation durable");
        // Independent store instances share a JVM/process lock and one durable state file.
        Path concurrentRoot=root.resolve("concurrent");PackStore concurrent=new PackStore(concurrentRoot);
        concurrent.trust(pub,sha,"test");
        java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            java.util.List<java.util.concurrent.Future<String>> results=new java.util.ArrayList<>();
            for(int i=0;i<8;i++)results.add(pool.submit(()->install(new PackStore(concurrentRoot),a).archiveSha256));
            for(java.util.concurrent.Future<String> result:results)check(aa.equals(result.get(10,java.util.concurrent.TimeUnit.SECONDS)),"concurrent idempotent install");
            check(concurrent.state().versions().size()==1&&concurrent.state().versions().get(MODULE).current.equals(aa),"one durable version after concurrency");
        }finally{pool.shutdownNow();}
        PackStore failDurable=new PackStore(concurrentRoot,stage->{if(stage.equals("STATE_DURABLE"))throw new IOException("after durable rename");});
        refuse(()->install(failDurable,b),"post-commit acknowledgement interruption");
        check(new PackStore(concurrentRoot).state().versions().get(MODULE).current.equals(bb),"durable state observed after reopen");
        check(install(concurrent,b).archiveSha256.equals(bb)&&concurrent.state().versions().get(MODULE).previous.equals(aa),"retry does not advance retained previous");

        Path abandoned=concurrentRoot.resolve(".stage-123.tmp"), abandonedArchive=concurrentRoot.resolve("archives/.stage-456.tmp"), unrelated=concurrentRoot.resolve(".stage-preserve.tmp");
        Files.writeString(abandoned,"partial state");Files.writeString(abandonedArchive,"partial archive");Files.writeString(unrelated,"unrelated");
        check(new PackStore(concurrentRoot).state().versions().get(MODULE).current.equals(bb),"reopen after abandoned stages");
        check(!Files.exists(abandoned)&&!Files.exists(abandonedArchive)&&Files.isRegularFile(unrelated),"only exact abandoned stages reclaimed");
        // Fill the actual store with signed versions; lose a remote commit acknowledgement.
        // Reclaim only exact unreferenced bytes, not current/previous/published/in-flight.
        Path retentionRoot=root.resolve("retention");PackStore retained=new PackStore(retentionRoot);
        Remote retentionRemote=new Remote();retentionRemote.scopes.add(PKG);
        retained.trust(pub,sha,"test");install(retained,a);retained.select(ID,true);PackPublication.publish(retained,retentionRemote);
        install(retained,b);PackStore lostAck=new PackStore(retentionRoot,stage->{if(stage.equals("PUBLISHED_RENAME"))throw new IOException("lost ack");});
        refuse(()->PackPublication.publish(lostAck,retentionRemote),"retention lost acknowledgement");
        check(PackSnapshot.read(retentionRemote.state()).choice(ID).equals(bb),"in-flight remote commit actually visible");
        for(int i=6;i<20;i++)install(retained,Files.readAllBytes(Path.of(args[i])));
        Path archiveRoot=retentionRoot.resolve("archives");
        try(java.util.stream.Stream<Path> paths=Files.list(archiveRoot)){check(paths.filter(p->p.getFileName().toString().endsWith(".hookpack")).count()==16,"bounded archive inventory");}
        byte[] next=Files.readAllBytes(Path.of(args[20]));String nextDigest=HookPack.digest(next);
        PackStore.Version before=retained.state().versions().get(MODULE);
        PackStore failReclaim=new PackStore(retentionRoot,stage->{if(stage.equals("RECLAIM_DELETE"))throw new IOException("before reclaim");});
        refuse(()->install(failReclaim,next),"reclaim interruption before deletion");
        check(retained.state().versions().get(MODULE).current.equals(before.current),"reclaim does not mutate selection");
        Path journal=retentionRoot.resolve("publication-inflight");byte[] saved=Files.readAllBytes(journal);
        Files.writeString(journal,"invalid journal");refuse(()->install(retained,next),"corrupt retention journal fail closed");Files.write(journal,saved);
        PackStore failDurableReclaim=new PackStore(retentionRoot,stage->{if(stage.equals("RECLAIM_DURABLE"))throw new IOException("after reclaim");});
        refuse(()->install(failDurableReclaim,next),"reclaim interrupted after durable deletion");
        check(retained.state().versions().get(MODULE).current.equals(before.current),"durable reclaim preserves current version");
        for(String digest:new String[]{aa,bb,before.current,before.previous})check(Files.isRegularFile(archiveRoot.resolve(digest+".hookpack")),"protected archive retained");
        check(install(retained,next).archiveSha256.equals(nextDigest),"retry after reclamation");
        check(retained.state().versions().get(MODULE).previous.equals(before.current),"rollback pointer retained after reclaim");
        byte[] last=Files.readAllBytes(Path.of(args[21]));install(retained,last);
        for(String digest:new String[]{aa,bb,nextDigest,HookPack.digest(last)})check(Files.isRegularFile(archiveRoot.resolve(digest+".hookpack")),"in-flight and rollback archives survive later reclaim");
        try(java.util.stream.Stream<Path> paths=Files.list(archiveRoot)){check(paths.filter(p->p.getFileName().toString().endsWith(".hookpack")).count()==16,"subsequent import retains bounded inventory");}
        PackPublication.publish(retained,retentionRemote);check(!Files.exists(journal),"successful readback clears inflight journal");
        retained.rollback(MODULE);check(retained.state().snapshot().choice(ID).equals(nextDigest),"retained rollback after full capacity");

        System.out.println("MOD02_REGISTRY_CHECKS_PASS "+count);
    }
}
