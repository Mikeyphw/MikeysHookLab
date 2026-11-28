package com.mikeyphw.hooklab.runtime.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Manager-only private durable store. Immutable archives precede one atomic state commit.
 * A staged/verified import cannot change a framework generation until explicit publication. */
public final class PackStore {
    private static final int MAX_STATE=32768, MAX_ARCHIVES=16, MAX_REVOKED=32;
    private static final String HEADER="HOOKLAB-PACK-STORE-V1";
    private static final Object JVM_LOCK=new Object();
    private final Path root, archives, journal, published, inflight;
    public interface Fault {void before(String stage) throws IOException;}
    private final Fault fault;
    public PackStore(Path root) {this(root,stage->{});}
    public PackStore(Path root,Fault fault){this.root=root.toAbsolutePath().normalize();archives=this.root.resolve("archives");journal=this.root.resolve("state");published=this.root.resolve("published");inflight=this.root.resolve("publication-inflight");this.fault=java.util.Objects.requireNonNull(fault);}
    public static final class Version {
        public final String current, previous, highestVersion, highestDigest;
        private Version(String current,String previous,String highestVersion,String highestDigest){PackPolicy.requireHash(current);if(!"-".equals(previous))PackPolicy.requireHash(previous);PackPolicy.requireHash(highestDigest);PackPolicy.compareVersions(highestVersion,highestVersion);this.current=current;this.previous=previous;this.highestVersion=highestVersion;this.highestDigest=highestDigest;}
    }
    public static final class State {
        private byte[] key;
        private String channel="-";
        private final Map<String,Version> versions=new TreeMap<>();
        private final Map<String,String[]> highWater=new TreeMap<>();
        private final Map<String,String> modes=new TreeMap<>();
        private final Map<String,String> pins=new TreeMap<>();
        private final Set<String> revoked=new TreeSet<>();
        private State(){for(String id:PackPolicy.hooks().keySet())modes.put(id,PackSnapshot.BUILTIN);}
        public byte[] key(){return key==null?null:key.clone();}
        public String channel(){return channel;}
        public Map<String,Version> versions(){return Collections.unmodifiableMap(new TreeMap<>(versions));}
        public Map<String,String> modes(){return Collections.unmodifiableMap(new TreeMap<>(modes));}
        public Map<String,String> pins(){return Collections.unmodifiableMap(new TreeMap<>(pins));}
        public String reviewDigest()throws GeneralSecurityException{return HookPack.digest(encode(this));}
        public Set<String> revoked(){return Collections.unmodifiableSet(new TreeSet<>(revoked));}
        public PackSnapshot snapshot() throws GeneralSecurityException {
            Map<String,String> choices=new TreeMap<>();
            for(Map.Entry<String,String> e:modes.entrySet()){
                String mode=e.getValue();if("pack".equals(mode)){Version version=versions.get(PackPolicy.hook(e.getKey()).moduleId);if(version==null)throw new IllegalArgumentException("missing selected archive");mode=version.current;}
                choices.put(e.getKey(),mode);
            }
            return PackSnapshot.create(key,channel,choices);
        }
    }
    private interface Action<T>{T run(State state)throws IOException,GeneralSecurityException;}
    private <T>T locked(Action<T> action)throws IOException,GeneralSecurityException {
        synchronized(JVM_LOCK){
            directory(root);directory(archives);
            try(FileChannel channel=FileChannel.open(root.resolve("lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);FileLock lock=channel.lock()){
                if(!lock.isValid())throw new IOException("store lock invalid");recoverStages(root);recoverStages(archives);return action.run(readState());
            }
        }
    }
    private static void directory(Path p)throws IOException {
        Files.createDirectories(p);if(!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(p))throw new IOException("private store directory refused");
    }
    /** A process that died before its atomic rename can leave only private stage files.
     * The process/file lock proves no active writer owns these exact names. */
    private static void recoverStages(Path dir)throws IOException {
        java.util.List<Path> stages=new java.util.ArrayList<>();
        try(java.util.stream.Stream<Path> files=Files.list(dir)){for(Path p:(Iterable<Path>)files::iterator){
            if(!p.getFileName().toString().matches("\\.stage-[0-9]+\\.tmp"))continue;
            if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("unsafe abandoned stage");stages.add(p);
        }}
        for(Path p:stages)Files.delete(p);if(!stages.isEmpty())fsyncDir(dir);
    }
    private static void fsyncDir(Path p)throws IOException{try(FileChannel f=FileChannel.open(p,StandardOpenOption.READ)){f.force(true);}}
    private void atomic(Path target,byte[] bytes,String stage)throws IOException {
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)&&!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("unsafe store destination");
        Path temp=Files.createTempFile(target.getParent(),".stage-",".tmp");
        try{
            try(FileChannel f=FileChannel.open(temp,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){ByteBuffer b=ByteBuffer.wrap(bytes);while(b.hasRemaining())f.write(b);f.force(true);}
            fault.before(stage+"_RENAME");
            Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            fsyncDir(target.getParent());fault.before(stage+"_DURABLE");
        }finally{Files.deleteIfExists(temp);}
    }
    private static byte[] file(Path p,int max)throws IOException {
        if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>max)throw new IOException("private store file refused");
        try(InputStream in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)){return HookPack.readBounded(in,max);}
    }
    private static byte[] encode(State s){
        StringBuilder b=new StringBuilder(HEADER).append('\n').append(s.key==null?"-":Base64.getEncoder().encodeToString(s.key)).append('\n').append(s.channel).append('\n');
        for(String sha:s.revoked)b.append("revoked ").append(sha).append('\n');
        for(Map.Entry<String,String[]> e:s.highWater.entrySet())b.append("highwater ").append(e.getKey()).append(' ').append(e.getValue()[0]).append(' ').append(e.getValue()[1]).append('\n');
        for(Map.Entry<String,Version> e:s.versions.entrySet()){
            Version v=e.getValue();b.append("module ").append(e.getKey()).append(' ').append(v.current).append(' ').append(v.previous).append(' ').append(v.highestVersion).append(' ').append(v.highestDigest).append('\n');
        }
        for(Map.Entry<String,String> e:s.pins.entrySet())b.append("pin ").append(e.getKey()).append(' ').append(e.getValue()).append('\n');
        for(Map.Entry<String,String> e:s.modes.entrySet())b.append("hook ").append(e.getKey()).append(' ').append(e.getValue()).append('\n');
        return b.toString().getBytes(StandardCharsets.US_ASCII);
    }
    private State readState()throws IOException,GeneralSecurityException {
        if(!Files.exists(journal,LinkOption.NOFOLLOW_LINKS))return new State();
        byte[] bytes=file(journal,MAX_STATE);String[] lines=new String(bytes,StandardCharsets.US_ASCII).split("\n",-1);
        if(lines.length<4||!HEADER.equals(lines[0]))throw new IOException("store header");
        State s=new State();s.modes.clear();if(!"-".equals(lines[1])){s.key=Base64.getDecoder().decode(lines[1]);HookPack.signerFingerprint(s.key);}
        s.channel=lines[2];for(int i=3;i<lines.length-1;i++){
            String[] p=lines[i].split(" ",-1);if(p.length==2&&"revoked".equals(p[0])){PackPolicy.requireHash(p[1]);if(!s.revoked.add(p[1]))throw new IOException("duplicate revoked key");}
            else if(p.length==4&&"highwater".equals(p[0])){PackPolicy.requireModule(p[1]);PackPolicy.compareVersions(p[2],p[2]);PackPolicy.requireHash(p[3]);if(s.highWater.put(p[1],new String[]{p[2],p[3]})!=null)throw new IOException("duplicate highwater");}
            else if(p.length==6&&"module".equals(p[0])){PackPolicy.requireModule(p[1]);if(s.versions.put(p[1],new Version(p[2],p[3],p[4],p[5]))!=null)throw new IOException("duplicate module");}
            else if(p.length==3&&"pin".equals(p[0])){PackPolicy.requireModule(p[1]);PackPolicy.compareVersions(p[2],p[2]);if(s.pins.put(p[1],p[2])!=null)throw new IOException("duplicate pin");}
            else if(p.length==3&&"hook".equals(p[0])){PackPolicy.hook(p[1]);if(!java.util.Arrays.asList(PackSnapshot.BUILTIN,PackSnapshot.DISABLED,"pack").contains(p[2])||s.modes.put(p[1],p[2])!=null)throw new IOException("invalid hook mode");}
            else throw new IOException("store row");
        }
        if(s.revoked.size()>MAX_REVOKED||s.key!=null&&s.revoked.contains(HookPack.signerFingerprint(s.key))||!s.modes.keySet().equals(PackPolicy.hooks().keySet())||s.key==null&&!s.versions.isEmpty()||!java.util.Arrays.equals(bytes,encode(s)))throw new IOException("store identity");
        for(Map.Entry<String,Version> e:s.versions.entrySet()){String[] high=s.highWater.get(e.getKey());if(high==null||!high[0].equals(e.getValue().highestVersion)||!high[1].equals(e.getValue().highestDigest))throw new IOException("highwater mismatch");}
        if(s.key==null&&!s.highWater.isEmpty())throw new IOException("untrusted highwater");s.snapshot();return s;
    }
    private void save(State s)throws IOException,GeneralSecurityException{s.snapshot();byte[] bytes=encode(s);if(bytes.length>MAX_STATE)throw new IOException("store size");atomic(journal,bytes,"STATE");}
    public State state()throws IOException,GeneralSecurityException{return locked(s->s);}
    /** Metadata-only view from one locked, signature-verified private-store read. */
    public static final class PackMetadata {
        public final String moduleId, version, archiveSha256, signer, channel, sourceSha, sourceTree;
        public final long runId, runAttempt, settingsSchema;
        public final boolean hasUi;public final com.mikeyphw.hooklab.sdk.ModuleUiResources uiResources;
        private PackMetadata(HookPack.Verified p){
            Map<?,?> v=(Map<?,?>)com.mikeyphw.hooklab.sdk.HookSdk.StrictJson.read(p.provenance());
            moduleId=p.moduleId;version=p.version;archiveSha256=p.archiveSha256;signer=p.signer;channel=p.channel;
            sourceSha=(String)v.get("source_sha");sourceTree=(String)v.get("source_tree");runId=(Long)v.get("run_id");runAttempt=(Long)v.get("run_attempt");settingsSchema=(Long)v.get("settings_schema");hasUi=p.hasUi();uiResources=p.uiResources;
        }
    }
    public static final class Catalog {
        public final State state;
        public final Map<String,PackMetadata> current, previous;
        private Catalog(State state,Map<String,PackMetadata> current,Map<String,PackMetadata> previous){
            this.state=state;this.current=Collections.unmodifiableMap(current);this.previous=Collections.unmodifiableMap(previous);
        }
    }
    private PackMetadata metadata(State s,String module,String digest)throws IOException,GeneralSecurityException {
        HookPack.Verified p=verify(s,file(archive(digest),HookPack.MAX_ARCHIVE));
        if(!p.moduleId.equals(module)||!p.archiveSha256.equals(digest))throw new IOException("catalog archive identity mismatch");
        return new PackMetadata(p);
    }
    public HookPack.Verified uiArchive(String module,String sha)throws IOException,GeneralSecurityException {
        PackPolicy.requireModule(module);PackPolicy.requireHash(sha);return locked(s->{Version v=s.versions.get(module);if(v==null||!sha.equals(v.current))throw new IllegalArgumentException("UI archive selection changed");return verify(s,verifiedBytes(s,sha));});
    }
    public Catalog catalog()throws IOException,GeneralSecurityException {
        return locked(s->{Map<String,PackMetadata> current=new TreeMap<>(),previous=new TreeMap<>();
            for(Map.Entry<String,Version> e:s.versions.entrySet()){
                current.put(e.getKey(),metadata(s,e.getKey(),e.getValue().current));
                if(!"-".equals(e.getValue().previous))previous.put(e.getKey(),metadata(s,e.getKey(),e.getValue().previous));
            }return new Catalog(s,current,previous);
        });
    }
    public void trust(byte[] publicKey,String confirmedFingerprint,String channel)throws IOException,GeneralSecurityException {
        final byte[] key=publicKey.clone();String sha=HookPack.signerFingerprint(key);if(!sha.equals(confirmedFingerprint))throw new IllegalArgumentException("trust confirmation mismatch");
        locked(s->{if(s.revoked.contains(sha)||s.key!=null&&(!java.util.Arrays.equals(s.key,key)||!s.channel.equals(channel)))throw new IllegalArgumentException("revoke current publisher before rotation");s.key=key;s.channel=channel;save(s);return null;});
    }
    /** Revoke first, then permit explicit enrollment of a different publisher. Retain trust revocation evidence; unused archive bytes are reclaimable. */
    public void revoke(String confirmedFingerprint)throws IOException,GeneralSecurityException {
        locked(s->{if(s.key==null||!HookPack.signerFingerprint(s.key).equals(confirmedFingerprint)||s.revoked.size()>=MAX_REVOKED)throw new IllegalArgumentException("revocation refused");s.revoked.add(confirmedFingerprint);s.key=null;s.channel="-";s.versions.clear();s.highWater.clear();s.pins.clear();for(String id:s.modes.keySet())if("pack".equals(s.modes.get(id)))s.modes.put(id,PackSnapshot.DISABLED);save(s);return null;});
    }
    private HookPack.Verified verify(State s,byte[] bytes)throws GeneralSecurityException {
        if(s.key==null)throw new IllegalArgumentException("no trusted publisher");HookPack.Verified pack=HookPack.verify(bytes,s.key);PackPolicy.verifyMigration(pack);if(!s.channel.equals(pack.channel))throw new IllegalArgumentException("pack trust channel mismatch");return pack;
    }
    private Path archive(String sha){PackPolicy.requireHash(sha);return archives.resolve(sha+".hookpack");}
    private byte[] verifiedBytes(State s,String sha)throws IOException,GeneralSecurityException {
        byte[] b=file(archive(sha),HookPack.MAX_ARCHIVE);if(!verify(s,b).archiveSha256.equals(sha))throw new IOException("archive hash mismatch");return b;
    }
    // Retention is manager-private. No framework file or user document is deleted.
    // Protect installed current/previous, last acknowledged generation, and every
    // possible in-flight generation until a new remote readback is durable.
    private static Set<String> selectedDigests(PackSnapshot snapshot){
        Set<String> result=new TreeSet<>();for(String value:snapshot.choices().values())if(value.matches("[0-9a-f]{64}"))result.add(value);return result;
    }
    private Set<String> inFlightDigests()throws IOException {
        Set<String> result=new TreeSet<>();if(!Files.exists(inflight,LinkOption.NOFOLLOW_LINKS))return result;
        byte[] raw=file(inflight,MAX_STATE);String[] lines=new String(raw,StandardCharsets.US_ASCII).split("\n",-1);
        if(lines.length<2||!"HOOKLAB-PACK-INFLIGHT-V1".equals(lines[0])||!lines[lines.length-1].isEmpty())throw new IOException("invalid in-flight retention journal");
        for(int i=1;i<lines.length-1;i++){String sha=lines[i];if(!sha.matches("[0-9a-f]{64}")||!result.add(sha))throw new IOException("invalid in-flight retention digest");}
        if(result.size()>MAX_ARCHIVES||!java.util.Arrays.equals(raw,encodeInFlight(result)))throw new IOException("invalid in-flight retention identity");return result;
    }
    private static byte[] encodeInFlight(Set<String> digests){StringBuilder b=new StringBuilder("HOOKLAB-PACK-INFLIGHT-V1\n");for(String sha:digests)b.append(sha).append('\n');return b.toString().getBytes(StandardCharsets.US_ASCII);}
    private Set<String> protectedDigests(State state)throws IOException,GeneralSecurityException {
        Set<String> keep=inFlightDigests();
        for(Version version:state.versions.values()){keep.add(version.current);if(!"-".equals(version.previous))keep.add(version.previous);}
        if(Files.exists(published,LinkOption.NOFOLLOW_LINKS)){
            String receipt=new String(file(published,MAX_STATE),StandardCharsets.US_ASCII);int split=receipt.indexOf('\n');
            if(split!=64)throw new IOException("invalid published retention receipt");
            keep.addAll(selectedDigests(PackSnapshot.parse(receipt.substring(split+1),receipt.substring(0,split))));
        }
        return keep;
    }
    private void makeArchiveRoom(State state)throws IOException,GeneralSecurityException {makeArchiveRoom(state,Collections.emptySet());}
    private void makeArchiveRoom(State state,Set<String> reserved)throws IOException,GeneralSecurityException {
        java.util.List<Path> files=new java.util.ArrayList<>();
        try(java.util.stream.Stream<Path> stream=Files.list(archives)){for(Path path:(Iterable<Path>)stream.sorted()::iterator){
            String name=path.getFileName().toString();
            if(!name.endsWith(".hookpack"))continue;
            if(!name.matches("[0-9a-f]{64}\\.hookpack")||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("unsafe archive retention entry");files.add(path);
        }}
        if(files.size()<MAX_ARCHIVES)return;
        Set<String> keep=protectedDigests(state);keep.addAll(reserved);int required=files.size()-MAX_ARCHIVES+1;
        java.util.List<Path> reclaim=new java.util.ArrayList<>();
        for(Path path:files){String sha=path.getFileName().toString().substring(0,64);if(keep.contains(sha))continue;
            // Only delete exact content-addressed store-owned bytes. Never follow links.
            if(!HookPack.digest(file(path,HookPack.MAX_ARCHIVE)).equals(sha))throw new IOException("unreferenced archive identity mismatch");
            reclaim.add(path);if(reclaim.size()==required)break;
        }
        if(reclaim.size()!=required)throw new IOException("pack retention limit; reconcile pending publication");
        for(Path path:reclaim){fault.before("RECLAIM_DELETE");Files.delete(path);fsyncDir(archives);fault.before("RECLAIM_DURABLE");}
    }
    public HookPack.Verified inspect(InputStream in)throws IOException,GeneralSecurityException {byte[] b=HookPack.readBounded(in,HookPack.MAX_ARCHIVE);return locked(s->verify(s,b));}
    public HookPack.Verified install(InputStream in,String confirmedHash)throws IOException,GeneralSecurityException {
        byte[] bytes=HookPack.readBounded(in,HookPack.MAX_ARCHIVE);
        return locked(s->{HookPack.Verified p=verify(s,bytes);if(!p.archiveSha256.equals(confirmedHash))throw new IllegalArgumentException("import confirmation mismatch");Version v=s.versions.get(p.moduleId);String[] high=s.highWater.get(p.moduleId);
            if(high!=null){int cmp=PackPolicy.compareVersions(p.version,high[0]);if(cmp<0||cmp==0&&!p.archiveSha256.equals(high[1]))throw new IllegalArgumentException("stale or conflicting pack version");}
            if(v!=null&&p.archiveSha256.equals(v.current))return p;
            Path dst=archive(p.archiveSha256);if(Files.exists(dst,LinkOption.NOFOLLOW_LINKS)){if(!java.util.Arrays.equals(bytes,verifiedBytes(s,p.archiveSha256)))throw new IOException("archive collision");}
            else{makeArchiveRoom(s);atomic(dst,bytes,"ARCHIVE");}
            s.highWater.put(p.moduleId,new String[]{p.version,p.archiveSha256});s.versions.put(p.moduleId,new Version(p.archiveSha256,v==null?"-":v.current,p.version,p.archiveSha256));save(s);return p;});
    }
    /** Pin only a reviewed, installed version. A pin cannot grant trust or enable a hook. */
    public void pin(String module,boolean enabled,String reviewDigest)throws IOException,GeneralSecurityException {
        locked(s->{PackPolicy.requireModule(module);if(!s.reviewDigest().equals(reviewDigest))throw new IllegalArgumentException("stale review");
            if(enabled){Version v=s.versions.get(module);if(v==null)throw new IllegalArgumentException("pin needs installed version");s.pins.put(module,metadata(s,module,v.current).version);}else s.pins.remove(module);save(s);return null;});
    }
    /** Verify the complete reviewed batch before staging; one state rename activates
     * the local catalog transaction. Existing remote generation is unchanged. */
    public Map<String,PackMetadata> installBatch(Map<String,byte[]> reviewed,String expectedState)throws IOException,GeneralSecurityException {
        if(reviewed.isEmpty()||reviewed.size()>PackPolicy.MODULES.size())throw new IllegalArgumentException("update batch bound");
        Map<String,byte[]> input=new TreeMap<>();for(Map.Entry<String,byte[]> e:reviewed.entrySet()){PackPolicy.requireHash(e.getKey());byte[] bytes=e.getValue();if(bytes==null||bytes.length>HookPack.MAX_ARCHIVE)throw new IllegalArgumentException("archive bound");input.put(e.getKey(),bytes.clone());}
        return locked(s->{if(!s.reviewDigest().equals(expectedState))throw new IllegalArgumentException("stale update review");
            Map<String,HookPack.Verified> candidates=new TreeMap<>();for(Map.Entry<String,byte[]> e:input.entrySet()){
                HookPack.Verified p=verify(s,e.getValue());if(!p.archiveSha256.equals(e.getKey())||candidates.put(p.moduleId,p)!=null)throw new IllegalArgumentException("update identity");
                String pin=s.pins.get(p.moduleId);if(pin!=null&&!pin.equals(p.version))throw new IllegalArgumentException("pinned version");String[] high=s.highWater.get(p.moduleId);
                if(high!=null&&(PackPolicy.compareVersions(p.version,high[0])<0||PackPolicy.compareVersions(p.version,high[0])==0&&!p.archiveSha256.equals(high[1])))throw new IllegalArgumentException("stale or conflicting pack version");
            }
            for(HookPack.Verified p:candidates.values()){byte[] bytes=input.get(p.archiveSha256);Path dst=archive(p.archiveSha256);
                if(Files.exists(dst,LinkOption.NOFOLLOW_LINKS)){if(!java.util.Arrays.equals(bytes,verifiedBytes(s,p.archiveSha256)))throw new IOException("archive collision");}
                else{makeArchiveRoom(s,input.keySet());atomic(dst,bytes,"ARCHIVE");}}
            Map<String,PackMetadata> result=new TreeMap<>();for(HookPack.Verified p:candidates.values()){Version old=s.versions.get(p.moduleId);if(old==null||!old.current.equals(p.archiveSha256)){s.highWater.put(p.moduleId,new String[]{p.version,p.archiveSha256});s.versions.put(p.moduleId,new Version(p.archiveSha256,old==null?"-":old.current,p.version,p.archiveSha256));}result.put(p.moduleId,new PackMetadata(p));}
            save(s);return Collections.unmodifiableMap(result);});
    }
    public void select(String id,boolean scoped)throws IOException,GeneralSecurityException {
        locked(s->{if(!scoped)throw new IllegalArgumentException("scope not confirmed");Version v=s.versions.get(PackPolicy.hook(id).moduleId);if(v==null)throw new IllegalArgumentException("pack not installed");verifiedBytes(s,v.current);s.modes.put(id,"pack");save(s);return null;});
    }
    public void disable(String id)throws IOException,GeneralSecurityException {locked(s->{PackPolicy.hook(id);s.modes.put(id,PackSnapshot.DISABLED);save(s);return null;});}
    public void remove(String module)throws IOException,GeneralSecurityException {
        locked(s->{PackPolicy.requireModule(module);s.versions.remove(module);for(String id:s.modes.keySet())if(PackPolicy.hook(id).moduleId.equals(module)&&"pack".equals(s.modes.get(id)))s.modes.put(id,PackSnapshot.DISABLED);save(s);return null;});
    }
    public void rollback(String module)throws IOException,GeneralSecurityException {
        locked(s->{PackPolicy.requireModule(module);Version v=s.versions.get(module);if(v==null||"-".equals(v.previous))throw new IllegalArgumentException("no retained prior version");HookPack.Verified p=verify(s,verifiedBytes(s,v.previous));if(!module.equals(p.moduleId))throw new IllegalArgumentException("rollback identity");s.versions.put(module,new Version(v.previous,v.current,v.highestVersion,v.highestDigest));save(s);return null;});
    }
    /** Hold the same private transaction lock over source, remote commit and acknowledgement. */
    interface PublicationAction<T>{T run(PackSnapshot desired,PublicationAccess access)throws IOException,GeneralSecurityException;}
    interface PublicationAccess {byte[] bytes(String sha)throws IOException,GeneralSecurityException;void prepare()throws IOException;void acknowledge()throws IOException;}
    <T>T publication(PublicationAction<T> action)throws IOException,GeneralSecurityException {
        return locked(s->{PackSnapshot expected=s.snapshot();return action.run(expected,new PublicationAccess(){
            @Override public byte[] bytes(String sha)throws IOException,GeneralSecurityException{if(!expected.choices().containsValue(sha))throw new IllegalArgumentException("unselected archive");return verifiedBytes(s,sha);}
            @Override public void prepare()throws IOException{Set<String> pending=inFlightDigests();pending.addAll(selectedDigests(expected));if(pending.size()>MAX_ARCHIVES)throw new IOException("too many unresolved generations");atomic(inflight,encodeInFlight(pending),"INFLIGHT");}
            @Override public void acknowledge()throws IOException{atomic(published,(expected.generation+"\n"+expected.text).getBytes(StandardCharsets.US_ASCII),"PUBLISHED");if(Files.deleteIfExists(inflight))fsyncDir(root);}
        });});
    }
    public boolean acknowledged(PackSnapshot expected)throws IOException,GeneralSecurityException {
        return locked(s->{Path p=published;if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return false;return java.util.Arrays.equals(file(p,MAX_STATE),(expected.generation+"\n"+expected.text).getBytes(StandardCharsets.US_ASCII));});
    }
}
