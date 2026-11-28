import com.mikeyphw.hooklab.runtime.distribution.DistributionIndex;
import com.mikeyphw.hooklab.runtime.pack.PackStore;
import com.mikeyphw.hooklab.runtime.pack.HookPack;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import com.mikeyphw.hooklab.packs.distribution.GithubRepository;
import java.nio.charset.StandardCharsets;

/** Independent verifier of the exact signed release bytes and store transaction fixtures. */
public final class Mod04DistributionChecks {
    private static void check(boolean result){if(!result)throw new AssertionError("distribution check failed");}
    private static byte[] bytes(Path p)throws Exception{return Files.readAllBytes(p);}
    private interface Checked {void run()throws Exception;}
    private static void refused(Checked f)throws Exception {try{f.run();}catch(IllegalArgumentException|java.io.IOException|java.security.GeneralSecurityException expected){return;}throw new AssertionError("expected refusal");}
    private static Map<String,byte[]> payloads(DistributionIndex index,Path root)throws Exception{
        Map<String,byte[]> result=new TreeMap<>();for(DistributionIndex.Entry e:index.entries)result.put(e.sha256,bytes(root.resolve(e.sha256+".hookpack")));return result;
    }
    private static DistributionIndex verify(Path path,byte[] key,String repository,Path inputs)throws Exception{
        DistributionIndex i=DistributionIndex.verify(bytes(path),key,repository);
        for(DistributionIndex.Entry e:i.entries){e.verifyPack(bytes(inputs.resolve(e.sha256+".hookpack")),key);e.verifyQualification(bytes(inputs.resolve(e.qualificationSha256+".json")),repository);}return i;
    }
    private static void network(DistributionIndex first,byte[] key,String repo,Path root,Path inputs)throws Exception{
        byte[] metadata=("[{\"tag_name\":\"hooklab-index-r"+first.revision+"\",\"draft\":false,\"prerelease\":false,\"assets\":[{\"name\":\"hooklab-index.json\",\"size\":"+first.encoded().length+",\"id\":999}]}]").getBytes(StandardCharsets.UTF_8);
        final String token="github_pat_"+"a".repeat(30);final boolean[] cached={false};final int[] calls={0};
        GithubRepository.Transport transport=(uri,accept,credential,etag,max)->{calls[0]++;String path=uri.getPath();if("api.github.com".equals(uri.getHost())){check(token.equals(credential));if(path.endsWith("/releases"))return new GithubRepository.Reply(200,metadata,null,null);if(path.endsWith("/999")){if(cached[0]){check("\"fixture\"".equals(etag));return new GithubRepository.Reply(304,new byte[0],null,null);}return new GithubRepository.Reply(302,new byte[0],"https://release-assets.githubusercontent.com/fixture",null);}for(DistributionIndex.Entry e:first.entries){if(path.endsWith("/"+e.assetId))return new GithubRepository.Reply(200,Files.readAllBytes(inputs.resolve(e.sha256+".hookpack")),null,null);if(path.endsWith("/"+e.qualificationAssetId))return new GithubRepository.Reply(200,Files.readAllBytes(inputs.resolve(e.qualificationSha256+".json")),null,null);}throw new AssertionError("unexpected API path");}check(credential==null&&etag==null);return new GithubRepository.Reply(200,first.encoded(),null,"\"fixture\"");};
        GithubRepository.Cache cache=new GithubRepository.Cache(root);try(GithubRepository api=new GithubRepository(repo,token.toCharArray(),transport)){GithubRepository.Cached online=api.refresh(cache,key,first.issuedAt);check(online.online);check(!cache.read(repo,key).online);check(api.stable(online,online.index.entries.get(0),key,first.issuedAt).length>0);refused(()->api.stable(cache.read(repo,key),first.entries.get(0),key,first.issuedAt));cached[0]=true;check(api.refresh(cache,key,first.issuedAt).online);refused(()->api.refresh(cache,key,first.expiresAt));}check(calls[0]>5);
        for(int status:new int[]{401,403,404,410,429,500})try(GithubRepository api=new GithubRepository(repo,null,(u,a,c,e,m)->{check(c==null);return new GithubRepository.Reply(status,new byte[0],null,null);})){refused(()->api.testAccess(false));}
        try(GithubRepository api=new GithubRepository(repo,token.toCharArray(),(u,a,c,e,m)->u.getPath().endsWith("/releases")?new GithubRepository.Reply(200,metadata,null,null):new GithubRepository.Reply(302,new byte[0],"https://bad.invalid/",null))){refused(()->api.refresh(cache,key,first.issuedAt));}
        try(GithubRepository api=new GithubRepository(repo,null,(u,a,c,e,m)->new GithubRepository.Reply(200,new byte[m+1],null,null))){refused(()->api.testAccess(false));}
        GithubRepository ended=new GithubRepository(repo,null,(u,a,c,e,m)->{throw new AssertionError("closed session network");});ended.close();refused(()->ended.testAccess(false));
    }
    private static void testing(Path root,byte[] key,String repo)throws Exception {
        Map<?,?> routes=DistributionIndex.parse(bytes(root.resolve("routes.json")));
        for(String fault:java.util.List.of("none","job","digest","publisher")) {
            GithubRepository.Transport transport=(u,a,c,e,m)->{check(u.getHost().equals("api.github.com"));String path=u.getPath().substring(("/repos/"+repo).length())+(u.getQuery()==null?"":"?"+u.getQuery());Object f=routes.get(path);check(f instanceof String);byte[] b=Files.readAllBytes(root.resolve((String)f));if(fault.equals("job")&&path.contains("/jobs?"))b=new String(b,StandardCharsets.UTF_8).replace("success","failure").getBytes(StandardCharsets.UTF_8);if(fault.equals("digest")&&path.endsWith("/102/zip"))b[0]^=1;return new GithubRepository.Reply(200,b,null,null);};
            try(GithubRepository api=new GithubRepository(repo,null,transport)){
                if(fault.equals("none")){GithubRepository.TestCandidate c=api.testing(123,key);check(c.packs.size()==3&&c.artifact==102&&c.source.equals("a".repeat(40)));}
                else refused(()->api.testing(123,fault.equals("publisher")?new byte[]{1}:key));
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if("testing".equals(args[0])){testing(Path.of(args[1]),bytes(Path.of(args[2])),args[3]);return;}
        byte[] key=bytes(Path.of(args[2]));Path inputs=Path.of(args[4]);DistributionIndex first=verify(Path.of(args[1]),key,args[3],inputs);
        if("verify".equals(args[0])){if(args.length>5)first.successor(DistributionIndex.verify(bytes(Path.of(args[5])),key,args[3]));System.out.println("MOD04_EXACT_DISTRIBUTION_VERIFIED_NOT_RELEASED");return;}
        check("fixture".equals(args[0]));DistributionIndex next=verify(Path.of(args[5]),key,args[3],inputs);next.successor(first);refused(()->first.successor(next));
        check(first.fresh(first.issuedAt));check(!first.fresh(first.expiresAt));check(!first.fresh(first.issuedAt-1));
        for(int n=6;n<9;n++){DistributionIndex bad=verify(Path.of(args[n]),key,args[3],inputs);refused(()->bad.successor(next));}
        Map<String,Long> targets=Map.of("com.openai.chatgpt",2632128L,"com.fugo.wowsolitaire",56240403L);
        check(first.newest("chatgpt",targets,null,null)!=null);check(first.newest("chatgpt",Map.of(),null,null)==null);check(first.newest("chatgpt",targets,"1.2.0",null)==null);check(next.newest("chatgpt",targets,"1.2.0","1.2.0")==null);
        byte[] changed=first.encoded();changed[changed.length/2]^=1;refused(()->DistributionIndex.verify(changed,key,args[3]));
        PackStore store=new PackStore(Path.of(args[9]));store.trust(key,HookPack.signerFingerprint(key),"personal");String initial=store.state().reviewDigest();Map<String,byte[]> original=payloads(first,inputs);store.installBatch(original,initial);check(store.catalog().current.size()==2);refused(()->store.installBatch(original,initial));
        String reviewed=store.state().reviewDigest();store.pin("chatgpt",true,reviewed);Map<String,byte[]> newer=new TreeMap<>();for(DistributionIndex.Entry e:next.entries)if(e.module.equals("chatgpt")&&e.version.equals("1.2.1"))newer.put(e.sha256,bytes(inputs.resolve(e.sha256+".hookpack")));
        refused(()->store.installBatch(newer,store.state().reviewDigest()));store.pin("chatgpt",false,store.state().reviewDigest());String before=store.state().reviewDigest();Map<String,byte[]> invalid=new TreeMap<>(newer);invalid.put("f".repeat(64),new byte[]{1,2,3});refused(()->store.installBatch(invalid,before));check(before.equals(store.state().reviewDigest()));
        store.installBatch(newer,before);check(store.catalog().current.get("chatgpt").version.equals("1.2.1"));store.rollback("chatgpt");check(store.catalog().current.get("chatgpt").version.equals("1.2.0"));check(store.state().modes().values().stream().noneMatch("pack"::equals));
        network(first,key,args[3],Path.of(args[9]).resolve("cache"),inputs);
        System.out.println("MOD04_DISTRIBUTION_FIXTURE_PASS");
    }
}
