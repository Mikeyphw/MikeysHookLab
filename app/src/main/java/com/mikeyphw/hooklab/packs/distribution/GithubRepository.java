package com.mikeyphw.hooklab.packs.distribution;

import com.mikeyphw.hooklab.runtime.distribution.DistributionIndex;
import com.mikeyphw.hooklab.runtime.pack.HookPack;
import com.mikeyphw.hooklab.sdk.HookSdk;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import javax.net.ssl.HttpsURLConnection;

/** Manager-only read broker. The selected repository and publisher are explicit;
 * no credential, network client or executable loader is available in the SDK. */
public final class GithubRepository implements AutoCloseable {
    public static final int MAX_METADATA=65536, MAX_TEST_ZIP=32*1024*1024;
    private static final int MAX_PAGES=10, TIMEOUT=10000;
    public static final class Refused extends IOException {
        private static final long serialVersionUID=1L;
        Refused(String code){super(code);}
    }
    public interface Transport {
        Reply get(URI uri,String accept,String credential,String etag,int max) throws IOException;
    }
    public static final class Reply {
        public final int code; public final byte[] bytes; public final String location,etag;
        public Reply(int code,byte[] bytes,String location,String etag){this.code=code;this.bytes=bytes;this.location=location;this.etag=etag;}
    }
    private static final class Network implements Transport {
        @Override public Reply get(URI uri,String accept,String credential,String etag,int max)throws IOException {
            HttpsURLConnection c=null;
            try{
                c=(HttpsURLConnection)uri.toURL().openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(TIMEOUT);c.setReadTimeout(TIMEOUT);c.setUseCaches(false);c.setRequestMethod("GET");
                c.setRequestProperty("User-Agent","HookLab-manager");c.setRequestProperty("X-GitHub-Api-Version","2022-11-28");c.setRequestProperty("Accept",accept);c.setRequestProperty("Accept-Encoding","identity");
                if(credential!=null)c.setRequestProperty("Authorization","Bearer "+credential);
                if(etag!=null)c.setRequestProperty("If-None-Match",etag);
                int status=c.getResponseCode();String loc=c.getHeaderField("Location"),tag=c.getHeaderField("ETag");byte[] body=new byte[0];
                if(status==200){long len=c.getContentLengthLong();if(len>max)throw new Refused("DOWNLOAD_LIMIT");long deadline=System.nanoTime()+30_000_000_000L;
                    try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;){if(n==0)continue;if(out.size()>max-n||System.nanoTime()>deadline)throw new Refused("DOWNLOAD_LIMIT");out.write(b,0,n);}body=out.toByteArray();if(len>=0&&body.length!=len)throw new Refused("TRUNCATED_DOWNLOAD");}}
                return new Reply(status,body,loc,tag);
            }catch(Refused e){throw e;}catch(IOException|RuntimeException e){throw new Refused("NETWORK_UNAVAILABLE");}finally{if(c!=null)c.disconnect();}
        }
    }
    private final String repository;private char[] credential;private boolean closed;private final Transport transport;
    public GithubRepository(String repository,char[] credential){this(repository,credential,new Network());}
    public GithubRepository(String repository,char[] credential,Transport transport){this.repository=DistributionIndex.repository(repository);this.transport=java.util.Objects.requireNonNull(transport);if(credential!=null&&credential.length>0){if(!new String(credential).matches("github_pat_[A-Za-z0-9_]{20,250}"))throw new IllegalArgumentException("fine-grained read token required");this.credential=credential.clone();}}
    @Override public synchronized void close(){closed=true;if(credential!=null)Arrays.fill(credential,'\0');credential=null;}
    private synchronized String sessionToken()throws Refused{require(!closed,"SESSION_ENDED");return credential==null?null:new String(credential);}
    public String repository(){return repository;}
    private URI api(String route){return URI.create("https://api.github.com/repos/"+repository+route);}
    private static void require(boolean v,String code)throws Refused{if(!v)throw new Refused(code);}
    private static boolean allowed(URI uri,boolean api){String host=uri.getHost();if(!"https".equals(uri.getScheme())||host==null||uri.getRawUserInfo()!=null||uri.getRawFragment()!=null||(uri.getPort()!=-1&&uri.getPort()!=443))return false;
        if(api)return host.equals("api.github.com");return host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com")||host.matches("productionresultssa[0-9]+\\.blob\\.core\\.windows\\.net")||host.equals("pipelines.actions.githubusercontent.com");}
    private static String etag(String s){return s!=null&&s.matches("(?:W/)?\"[A-Za-z0-9:_./+=-]{1,128}\"")?s:null;}
    private Reply get(String route,boolean binary,String conditional,int max)throws IOException {
        URI uri=api(route);String token=sessionToken();String tag=etag(conditional);boolean first=true;
        for(int redirects=0;redirects<=3;redirects++){
            sessionToken();require(allowed(uri,first),"ORIGIN_REFUSED");Reply r=transport.get(uri,binary?"application/octet-stream":"application/vnd.github+json",first?token:null,first?tag:null,max);
            sessionToken();require(r.bytes!=null&&r.bytes.length<=max,"DOWNLOAD_LIMIT");
            if(r.code==200||r.code==304&&first&&tag!=null)return r;
            if(binary&&(r.code==301||r.code==302||r.code==303||r.code==307||r.code==308)){require(r.location!=null&&r.location.length()<=8192,"REDIRECT_REFUSED");try{uri=uri.resolve(r.location);}catch(IllegalArgumentException e){throw new Refused("REDIRECT_REFUSED");}first=false;continue;}
            throw new Refused(r.code==401?"TOKEN_REJECTED":r.code==403||r.code==429?"SCOPE_OR_RATE_LIMIT":r.code==404?"NOT_FOUND_OR_NO_ACCESS":r.code==410?"EXPIRED_ARTIFACT":"HTTP_REFUSED");
        }throw new Refused("REDIRECT_LIMIT");
    }
    private Object json(String route)throws IOException{return HookSdk.StrictJson.read(get(route,false,null,MAX_METADATA).bytes);}
    private static Map<?,?> obj(Object o){return DistributionIndex.object(o);}
    private static String str(Map<?,?> o,String k){return DistributionIndex.text(o,k);}
    private static long num(Map<?,?> o,String k){return DistributionIndex.number(o,k);}
    private static List<?> rows(Object o,int bound)throws IOException{require(o instanceof List&&((List<?>)o).size()<=bound,"METADATA_LIMIT");return (List<?>)o;}
    public void testAccess(boolean actions)throws IOException {Map<?,?> r=obj(json(""));require(repository.equals(str(r,"full_name")),"REPOSITORY_MISMATCH");if(actions){Map<?,?> q=obj(json("/actions/runs?per_page=1"));require(q.get("workflow_runs") instanceof List,"ACTIONS_ACCESS_REFUSED");}}
    private static final class Discovered {final long asset,revision;Discovered(long a,long r){asset=a;revision=r;}}
    private Discovered indexAsset()throws IOException {
        long newest=0,asset=0;boolean complete=false;
        for(int page=1;page<=MAX_PAGES;page++){
            List<?> releases=rows(json("/releases?per_page=10&page="+page),10);
            for(Object row:releases){Map<?,?> r=obj(row);String tag=str(r,"tag_name");if(!tag.matches("hooklab-index-r[1-9][0-9]{0,17}")||!Boolean.FALSE.equals(r.get("draft"))||!Boolean.FALSE.equals(r.get("prerelease")))continue;
                long n=Long.parseLong(tag.substring("hooklab-index-r".length()));if(n<newest)continue;long id=0;
                for(Object item:rows(r.get("assets"),20)){Map<?,?> a=obj(item);if("hooklab-index.json".equals(str(a,"name"))){require(id==0&&num(a,"size")>0&&num(a,"size")<=DistributionIndex.MAX_BYTES,"INDEX_ASSET_REFUSED");id=num(a,"id");}}
                require(id>0&&(n!=newest||asset==id),"INDEX_ASSET_REFUSED");newest=n;asset=id;
            }
            if(releases.size()<10){complete=true;break;}
        }require(complete,"RELEASE_PAGINATION_LIMIT");require(asset>0,"NO_STABLE_INDEX");return new Discovered(asset,newest);
    }
    public static final class Cached {
        public final DistributionIndex index;public final long asset;public final String etag;public final boolean online;
        Cached(DistributionIndex i,long a,String t,boolean online){index=i;asset=a;etag=t;this.online=online;}
    }
    /** One atomic file per repository and pinned publisher, with reverified signed bytes on every read. */
    public static final class Cache {
        private static final Object LOCK=new Object();
        private final Path root;
        public Cache(Path path){root=path.toAbsolutePath().normalize();}
        private Path file(String repo,byte[] key)throws GeneralSecurityException{return root.resolve(HookPack.digest((DistributionIndex.repository(repo)+"\n"+HookPack.signerFingerprint(key)).getBytes(java.nio.charset.StandardCharsets.US_ASCII))+".index");}
        public Cached read(String repo,byte[] key)throws IOException,GeneralSecurityException {synchronized(LOCK){
            if(Files.exists(root,LinkOption.NOFOLLOW_LINKS))require(Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS),"CACHE_REFUSED");Path p=file(repo,key);if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return null;require(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&Files.size(p)<=DistributionIndex.MAX_BYTES+512,"CACHE_REFUSED");byte[] all=Files.readAllBytes(p);int a=-1,b=-1;for(int i=0;i<all.length&&b<0;i++)if(all[i]=='\n'){if(a<0)a=i;else b=i;}
            require(a>0&&a<22&&b>a&&b-a<256,"CACHE_REFUSED");long asset=Long.parseLong(new String(all,0,a,java.nio.charset.StandardCharsets.US_ASCII));String t=new String(all,a+1,b-a-1,java.nio.charset.StandardCharsets.US_ASCII);require(asset>0&&(t.isEmpty()||etag(t)!=null),"CACHE_REFUSED");return new Cached(DistributionIndex.verify(Arrays.copyOfRange(all,b+1,all.length),key,repo),asset,etag(t),false);
        }}
        public void write(Cached value,byte[] key)throws IOException,GeneralSecurityException {synchronized(LOCK){
            Files.createDirectories(root);require(Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS),"CACHE_REFUSED");Cached old=read(value.index.repository,key);value.index.successor(old==null?null:old.index);Path p=file(value.index.repository,key);if(old==null)try(java.util.stream.Stream<Path> files=Files.list(root)){require(files.count()<8,"CACHE_IDENTITY_LIMIT");}Path tmp=Files.createTempFile(root,".index-",".tmp");
            try{byte[] head=(value.asset+"\n"+(value.etag==null?"":value.etag)+"\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII);try(java.io.OutputStream out=Files.newOutputStream(tmp)){out.write(head);out.write(value.index.encoded());}try(FileChannel f=FileChannel.open(tmp,StandardOpenOption.WRITE)){f.force(true);}Files.move(tmp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);try(FileChannel f=FileChannel.open(root,StandardOpenOption.READ)){f.force(true);}}finally{Files.deleteIfExists(tmp);}
        }}
    }
    public Cached refresh(Cache cache,byte[] key,long now)throws IOException,GeneralSecurityException {
        Cached old=cache.read(repository,key);Discovered found=indexAsset();long id=found.asset;Reply r=get("/releases/assets/"+id,true,old!=null&&old.asset==id?old.etag:null,DistributionIndex.MAX_BYTES);
        DistributionIndex index=r.code==304?old.index:DistributionIndex.verify(r.bytes,key,repository);require(index.revision==found.revision&&index.fresh(now),"INDEX_STALE_OR_WRONG_REVISION");index.successor(old==null?null:old.index);Cached value=new Cached(index,id,r.code==304?old.etag:etag(r.etag),true);cache.write(value,key);return value;
    }
    public byte[] stable(Cached cached,DistributionIndex.Entry e,byte[] key,long now)throws IOException,GeneralSecurityException {
        require(cached.online&&cached.index.repository.equals(repository)&&cached.index.fresh(now)&&cached.index.entries.contains(e)&&!cached.index.revoked.contains(e.sha256),"UPDATE_REVIEW_STALE");
        byte[] qualification=get("/releases/assets/"+e.qualificationAssetId,true,null,(int)e.qualificationSize).bytes;e.verifyQualification(qualification,repository);
        byte[] bytes=get("/releases/assets/"+e.assetId,true,null,(int)e.size).bytes;e.verifyPack(bytes,key);return bytes;
    }
    public static final class TestCandidate {
        public final long run,attempt,workflow,artifact;public final String source,tree,digest;public final Map<String,byte[]> packs;public final byte[] publisher;
        TestCandidate(long r,long a,long w,long artifact,String s,String t,String d,Map<String,byte[]> packs,byte[] p){run=r;attempt=a;workflow=w;this.artifact=artifact;source=s;tree=t;digest=d;this.packs=java.util.Collections.unmodifiableMap(packs);publisher=p;}
    }
    private Map<?,?> artifact(long run,String name,String head,long repoId)throws IOException {
        Map<?,?> found=null;boolean complete=false;for(int page=1;page<=MAX_PAGES;page++){Map<?,?> r=obj(json("/actions/runs/"+run+"/artifacts?per_page=10&page="+page));List<?> items=rows(r.get("artifacts"),10);for(Object row:items){Map<?,?> a=obj(row);if(name.equals(str(a,"name"))){require(found==null&&Boolean.FALSE.equals(a.get("expired"))&&java.time.Instant.parse(str(a,"expires_at")).isAfter(java.time.Instant.now()),"ARTIFACT_EXPIRED_OR_DUPLICATE");Map<?,?> binding=obj(a.get("workflow_run"));require(run==num(binding,"id")&&head.equals(str(binding,"head_sha"))&&repoId==num(binding,"repository_id")&&repoId==num(binding,"head_repository_id"),"ARTIFACT_BINDING_REFUSED");found=a;}}if(items.size()<10){complete=true;break;}}
        require(complete&&found!=null,"ARTIFACT_MISSING_OR_LIMIT");return found;
    }
    private Map<String,byte[]> zip(Map<?,?> a,Set<String> names,int max)throws IOException,GeneralSecurityException {
        long id=num(a,"id"),size=num(a,"size_in_bytes");String digest=str(a,"digest");require(id>0&&size>0&&size<=max&&digest.matches("sha256:[a-f0-9]{64}"),"ARTIFACT_IDENTITY_REFUSED");byte[] b=get("/actions/artifacts/"+id+"/zip",true,null,(int)size).bytes;require(b.length==size&&digest.equals("sha256:"+HookPack.digest(b)),"ARTIFACT_DIGEST_MISMATCH");
        Map<String,byte[]> parts=new HashMap<>();int total=0;try(ZipInputStream z=new ZipInputStream(new java.io.ByteArrayInputStream(b))){for(ZipEntry e;(e=z.getNextEntry())!=null;){String n=e.getName();require(!e.isDirectory()&&names.contains(n)&&!parts.containsKey(n),"ARTIFACT_INVENTORY_REFUSED");byte[] part=HookPack.readBounded(z,n.endsWith(".hookpack")?HookPack.MAX_ARCHIVE:MAX_METADATA);total+=part.length;require(total<=max,"ARTIFACT_LIMIT");parts.put(n,part);z.closeEntry();}}require(parts.keySet().equals(names),"ARTIFACT_INVENTORY_REFUSED");return parts;
    }
    private void verifyJobs(long run,long attempt,String source)throws IOException {
        Set<String> required=new java.util.HashSet<>(Set.of("HookLab / impact plan","HookLab / targeted qualification","HookLab / source contracts","Android build, unit tests and lint","HookLab / terminal contract","HookLab / Go qualification","design / Frozen browser reference and semantic gate","visual / Real Activity native visual candidate gate","ci24 / Summarize all 24 shards (fail closed)","adaptive / Extended UI / exact matrix aggregate"));
        for(int i=1;i<=24;i++)required.add("ci24 / JVM / shard "+i+"/24");for(String c:List.of("api29-compact","api29-standard","api36-360","api36-390","api36-393","api36-600","api36-840","api36-compact","api36-landscape","api36-tablet-landscape"))required.add("adaptive / Adaptive UI / "+c);
        Set<String> skipped=Set.of("functional","ci24 / Standalone dependency preparation","adaptive / Extended UI / exact shared APK build");Set<String> seen=new java.util.HashSet<>();boolean complete=false;
        for(int page=1;page<=MAX_PAGES;page++){Map<?,?> result=obj(json("/actions/runs/"+run+"/attempts/"+attempt+"/jobs?per_page=5&page="+page));List<?> jobs=rows(result.get("jobs"),5);for(Object raw:jobs){Map<?,?> job=obj(raw);String name=str(job,"name");require(seen.add(name)&&run==num(job,"run_id")&&attempt==num(job,"run_attempt")&&source.equals(str(job,"head_sha"))&&"completed".equals(str(job,"status"))&&(required.contains(name)&&"success".equals(str(job,"conclusion"))||skipped.contains(name)&&"skipped".equals(str(job,"conclusion"))),"JOB_QUALIFICATION_REFUSED");}if(jobs.size()<5){complete=true;require(seen.size()==num(result,"total_count"),"JOB_ROSTER_REFUSED");break;}}
        require(complete&&seen.containsAll(required),"JOB_ROSTER_REFUSED");
    }
    public TestCandidate testing(long run,byte[] trustedTestKey)throws IOException,GeneralSecurityException {
        require(run>0,"RUN_ID_REFUSED");Map<?,?> r=obj(json("/actions/runs/"+run));long attempt=num(r,"run_attempt"),workflow=num(r,"workflow_id");String source=str(r,"head_sha");require(num(r,"id")==run&&attempt>0&&workflow>0&&source.matches("[0-9a-f]{40}")&&repository.equals(str(obj(r.get("head_repository")),"full_name"))&&repository.equals(str(obj(r.get("repository")),"full_name"))&&"completed".equals(str(r,"status"))&&"success".equals(str(r,"conclusion")),"RUN_NOT_QUALIFIED");
        require(".github/workflows/android.yml".equals(str(r,"path").split("@",2)[0])&&(!DistributionIndex.OFFICIAL.equals(repository)||workflow==425729236),"WORKFLOW_REFUSED");long repoId=num(obj(r.get("repository")),"id");require(repoId>0&&repoId==num(obj(r.get("head_repository")),"id"),"REPOSITORY_MISMATCH");
        verifyJobs(run,attempt,source);
        Map<?,?> qa=artifact(run,"hooklab-qualification-"+run+"-"+attempt,source,repoId);Map<String,byte[]> q=zip(qa,Set.of("plan.json","receipt.json"),2*MAX_METADATA);Map<?,?> receipt=DistributionIndex.parse(q.get("receipt.json"));String tree=str(receipt,"source_tree");Map<?,?> commit=obj(json("/git/commits/"+source));require(tree.matches("[a-f0-9]{40}")&&source.equals(str(commit,"sha"))&&tree.equals(str(obj(commit.get("tree")),"sha")),"SOURCE_TREE_REFUSED");DistributionIndex.qualification(q.get("receipt.json"),repository,source,tree,run,attempt);
        Map<?,?> plan=DistributionIndex.parse(q.get("plan.json"));require(source.equals(str(plan,"source_sha"))&&tree.equals(str(plan,"source_tree"))&&str(receipt,"plan_sha256").equals(str(plan,"plan_sha256")),"PLAN_IDENTITY_REFUSED");
        Map<?,?> a=artifact(run,"hooklab-mod02-test-packs-"+run+"-"+attempt,source,repoId);Map<String,byte[]> files=zip(a,Set.of("receipt.json","publisher-test.der","obtainx.hookpack","chatgpt.hookpack","wordmaster.hookpack"),MAX_TEST_ZIP);require(Arrays.equals(trustedTestKey,files.get("publisher-test.der")),"TEST_PUBLISHER_MISMATCH");
        Map<?,?> p=DistributionIndex.parse(files.get("receipt.json"));require("hooklab.mod02.hosted.v1".equals(str(p,"schema"))&&source.equals(str(p,"source_sha"))&&tree.equals(str(p,"source_tree"))&&run==num(p,"run_id")&&attempt==num(p,"run_attempt")&&"SIGNED_TEST_PACKS_VERIFIED_NOT_ACTIVATED".equals(str(p,"status")),"TEST_RECEIPT_REFUSED");Map<?,?> hashes=obj(p.get("files"));require(hashes.keySet().equals(Set.of("publisher-test.der","obtainx.hookpack","chatgpt.hookpack","wordmaster.hookpack")),"TEST_RECEIPT_REFUSED");Map<String,byte[]> packs=new HashMap<>();for(String name:hashes.keySet().stream().map(Object::toString).toArray(String[]::new)){require(str(hashes,name).equals(HookPack.digest(files.get(name))),"TEST_PACK_DIGEST_MISMATCH");if(name.endsWith(".hookpack")){HookPack.Verified v=HookPack.verify(files.get(name),trustedTestKey);com.mikeyphw.hooklab.runtime.pack.PackPolicy.verifyMigration(v);Map<?,?> pr=DistributionIndex.parse(v.provenance());require(name.equals(v.moduleId+".hookpack")&&"test".equals(v.channel)&&source.equals(str(pr,"source_sha"))&&tree.equals(str(pr,"source_tree"))&&run==num(pr,"run_id")&&attempt==num(pr,"run_attempt"),"TEST_PACK_PROVENANCE_REFUSED");packs.put(v.moduleId,files.get(name));}}
        return new TestCandidate(run,attempt,workflow,num(a,"id"),source,tree,str(a,"digest"),packs,files.get("publisher-test.der"));
    }
}
