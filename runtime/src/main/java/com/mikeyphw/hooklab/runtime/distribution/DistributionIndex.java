package com.mikeyphw.hooklab.runtime.distribution;

import com.mikeyphw.hooklab.runtime.pack.HookPack;
import com.mikeyphw.hooklab.runtime.pack.PackPolicy;
import com.mikeyphw.hooklab.sdk.HookSdk;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Signed stable repository metadata only. No network, credential or executable loader. */
public final class DistributionIndex {
    public static final String OFFICIAL="Mikeyphw/MikeysHookLab";
    public static final String SCHEMA="hooklab.distribution.index.v1", ENVELOPE="hooklab.distribution.envelope.v1";
    public static final long MAX_AGE_SECONDS=31L*86400;
    public static final int MAX_BYTES=65536, MAX_RECEIPT=65536;
    private static final String[] REQUIRED_GATES={"source","android","ci24","terminal","design","visual","adaptive","go"};
    private static final Set<String> CASES=Set.of("api29-compact","api29-standard","api36-360","api36-390","api36-393","api36-600","api36-840","api36-compact","api36-landscape","api36-tablet-landscape");
    private static final byte[] DOMAIN="HOOKLAB-DISTRIBUTION-INDEX-V1\n".getBytes(StandardCharsets.US_ASCII);
    public final String repository, signer, digest;
    public final long revision, issuedAt, expiresAt;
    public final List<Entry> entries;
    public final Set<String> revoked;
    private final byte[] envelope;
    private DistributionIndex(String repository,String signer,String digest,long revision,long issuedAt,long expiresAt,List<Entry> entries,Set<String> revoked,byte[] envelope){this.repository=repository;this.signer=signer;this.digest=digest;this.revision=revision;this.issuedAt=issuedAt;this.expiresAt=expiresAt;this.entries=Collections.unmodifiableList(entries);this.revoked=Collections.unmodifiableSet(revoked);this.envelope=envelope.clone();}
    public byte[] encoded(){return envelope.clone();}
    private static void require(boolean b){if(!b)throw new IllegalArgumentException("distribution metadata refused");}
    public static String repository(String s){require(s!=null&&s.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})/[A-Za-z0-9_][A-Za-z0-9_.-]{0,99}")&&!s.endsWith("/.")&&!s.endsWith("/.."));return s;}
    public static Map<?,?> object(Object value,String...keys){require(value instanceof Map);Map<?,?> m=(Map<?,?>)value;if(keys.length>0)require(m.keySet().equals(Set.of(keys)));return m;}
    public static Map<?,?> parse(byte[] b){require(b!=null&&b.length>0&&b.length<=MAX_BYTES);return object(HookSdk.StrictJson.read(b));}
    public static String text(Map<?,?> m,String k){Object v=m.get(k);require(v instanceof String);return (String)v;}
    public static long number(Map<?,?> m,String k){Object v=m.get(k);require(v instanceof Long);return (Long)v;}
    public static long positive(Map<?,?> m,String k,long max){long v=number(m,k);require(v>0&&v<=max);return v;}
    public static String match(Map<?,?> m,String k,String pattern){String s=text(m,k);require(s.matches(pattern));return s;}
    public static String hash(Map<?,?> m,String k,int len){return match(m,k,"[a-f0-9]{"+len+"}");}
    public static List<?> list(Map<?,?> m,String k,int max){Object v=m.get(k);require(v instanceof List&&((List<?>)v).size()<=max);return (List<?>)v;}
    private static byte[] decode(String b64,int max){byte[] b=Base64.getDecoder().decode(b64);require(b.length>0&&b.length<=max&&Base64.getEncoder().encodeToString(b).equals(b64));return b;}
    public static final class Entry {
        public final String module, version, tag, sha256, source, tree, qualificationSha256, inputSha256, changelog;
        public final long assetId, size, qualificationAssetId, qualificationSize, workflowId, run, attempt, sdkMin, sdkMax, settingsSchema;
        private final Map<?,?> manifest,provenance;
        private Entry(Object raw){Map<?,?> m=object(raw,"manifest","provenance","tag","asset_id","sha256","size","qualification_asset_id","qualification_sha256","qualification_size","workflow_id","input_sha256","changelog");
            manifest=object(m.get("manifest"),"schema","module_id","version","sdk_min","sdk_max","api","entrypoint","hooks");
            require("hooklab.module.manifest.v1".equals(text(manifest,"schema")));module=match(manifest,"module_id","[a-z][a-z0-9_]{0,63}");version=text(manifest,"version");PackPolicy.compareVersions(version,version);
            sdkMin=positive(manifest,"sdk_min",Integer.MAX_VALUE);sdkMax=positive(manifest,"sdk_max",Integer.MAX_VALUE);require(sdkMax>=sdkMin&&number(manifest,"api")==102);
            require(("com.mikeyphw.hooklab.modules."+module+".Entrypoint").equals(text(manifest,"entrypoint")));
            Set<String> ids=new HashSet<>();List<?> hooks=list(manifest,"hooks",32);require(!hooks.isEmpty());for(Object item:hooks){Map<?,?> h=object(item,"id","package","min_version","max_version");require(ids.add(match(h,"id","[a-z][a-z0-9_.-]{0,127}")));match(h,"package","[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");require(positive(h,"max_version",Long.MAX_VALUE)>=positive(h,"min_version",Long.MAX_VALUE));}
            provenance=object(m.get("provenance"),"schema","source_sha","source_tree","run_id","run_attempt","settings_schema","channel");require("hooklab.pack.provenance.v1".equals(text(provenance,"schema"))&&"personal".equals(text(provenance,"channel")));
            source=hash(provenance,"source_sha",40);tree=hash(provenance,"source_tree",40);run=positive(provenance,"run_id",Long.MAX_VALUE);attempt=positive(provenance,"run_attempt",Integer.MAX_VALUE);settingsSchema=positive(provenance,"settings_schema",Integer.MAX_VALUE);
            tag=text(m,"tag");require(tag.equals("hooklab-module-"+module+"-v"+version));assetId=positive(m,"asset_id",Long.MAX_VALUE);size=positive(m,"size",HookPack.MAX_ARCHIVE);sha256=hash(m,"sha256",64);
            qualificationAssetId=positive(m,"qualification_asset_id",Long.MAX_VALUE);require(qualificationAssetId!=assetId);qualificationSha256=hash(m,"qualification_sha256",64);qualificationSize=positive(m,"qualification_size",MAX_RECEIPT);
            workflowId=positive(m,"workflow_id",Long.MAX_VALUE);inputSha256=hash(m,"input_sha256",64);changelog=text(m,"changelog");require(changelog.length()<=1000&&changelog.chars().allMatch(c->c>=32&&c<=126||c==10));
        }
        public boolean compatible(Map<String,Long> versions){
            if(!PackPolicy.MODULES.contains(module)||sdkMin>HookSdk.VERSION||sdkMax<HookSdk.VERSION||settingsSchema>2||settingsSchema==2&&!(module.equals("chatgpt")||module.equals("wordmaster")))return false;
            Map<String,HookSdk.HookDescriptor> expected=new TreeMap<>();for(HookSdk.HookDescriptor h:PackPolicy.hooks().values())if(h.moduleId.equals(module))expected.put(h.id,h);
            for(Object item:list(manifest,"hooks",32)){Map<?,?> h=object(item);HookSdk.HookDescriptor descriptor=expected.remove(text(h,"id"));if(descriptor==null||!descriptor.packageName.equals(text(h,"package"))||descriptor.minVersion!=number(h,"min_version")||descriptor.maxVersion!=number(h,"max_version"))return false;
                Long version=versions.get(descriptor.packageName);if(version==null||version<descriptor.minVersion||version>descriptor.maxVersion)return false;}
            return expected.isEmpty();
        }
        public HookPack.Verified verifyPack(byte[] bytes,byte[] publicKey)throws GeneralSecurityException {
            require(bytes!=null&&bytes.length==size&&sha256.equals(HookPack.digest(bytes)));HookPack.Verified pack=HookPack.verify(bytes,publicKey);PackPolicy.verifyMigration(pack);
            require(manifest.equals(parse(pack.manifest()))&&provenance.equals(parse(pack.provenance())));return pack;
        }
        public void verifyQualification(byte[] bytes,String repository)throws GeneralSecurityException {
            require(bytes!=null&&bytes.length==qualificationSize&&qualificationSha256.equals(HookPack.digest(bytes)));qualification(bytes,repository,source,tree,run,attempt);
        }
    }
    /** Validate the exact independently qualified source and gate roster, not a self-asserted status alone. */
    public static void qualification(byte[] bytes,String repo,String source,String tree,long run,long attempt){
        Map<?,?> q=parse(bytes);require("hooklab.qualification.receipt.v1".equals(text(q,"schema"))&&repo.equals(text(q,"repository"))&&source.equals(hash(q,"source_sha",40))&&tree.equals(hash(q,"source_tree",40))&&Long.toString(run).equals(text(q,"run_id"))&&Long.toString(attempt).equals(text(q,"run_attempt"))&&"PASS_TARGETED_HOSTED".equals(text(q,"overall")));
        Map<?,?> gates=object(q.get("gates"));require(gates.keySet().equals(Set.of("source","android","ci24","terminal","design","visual","functional","adaptive","go")));
        for(String id:REQUIRED_GATES){Map<?,?> g=object(gates.get(id));require("PASS".equals(text(g,"verdict"))&&"success".equals(text(g,"job_result")));}
        Map<?,?> functional=object(gates.get("functional"));require("NOT_APPLICABLE".equals(text(functional,"verdict"))&&"skipped".equals(text(functional,"job_result")));
        List<?> cases=list(q,"adaptive_cases",10);require(cases.size()==10&&new HashSet<>(cases).equals(CASES));
        require(Boolean.FALSE.equals(q.get("physical_device_qualified"))&&Boolean.FALSE.equals(q.get("release_qualified"))&&Boolean.FALSE.equals(q.get("visual_approved")));
    }
    public static DistributionIndex verify(byte[] encoded,byte[] key,String repo)throws GeneralSecurityException {
        repository(repo);String signer=HookPack.signerFingerprint(key);Map<?,?> e=object(parse(encoded),"schema","payload","signature","signer_sha256");require(ENVELOPE.equals(text(e,"schema"))&&signer.equals(hash(e,"signer_sha256",64)));
        byte[] payload=decode(text(e,"payload"),48000),sig=decode(text(e,"signature"),384);require(sig.length==384);Signature verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(key)));verifier.update(DOMAIN);verifier.update(payload);require(verifier.verify(sig));
        Map<?,?> m=object(parse(payload),"schema","repository","revision","issued_at","expires_at","modules","revoked");require(SCHEMA.equals(text(m,"schema"))&&repo.equals(text(m,"repository")));long revision=positive(m,"revision",Long.MAX_VALUE),issued=positive(m,"issued_at",Long.MAX_VALUE),expires=positive(m,"expires_at",Long.MAX_VALUE);require(expires>issued&&expires-issued<=MAX_AGE_SECONDS);
        Set<String> revoked=new HashSet<>();for(Object item:list(m,"revoked",128)){require(item instanceof String&&((String)item).matches("[0-9a-f]{64}")&&revoked.add((String)item));}
        Set<String> identities=new HashSet<>(),tags=new HashSet<>(),digests=new HashSet<>();Set<Long> assetIds=new HashSet<>();List<Entry> entries=new ArrayList<>();for(Object row:list(m,"modules",24)){Entry v=new Entry(row);require(identities.add(v.module+":"+v.version)&&tags.add(v.tag)&&digests.add(v.sha256)&&assetIds.add(v.assetId)&&assetIds.add(v.qualificationAssetId));entries.add(v);}
        return new DistributionIndex(repo,signer,HookPack.digest(payload),revision,issued,expires,entries,revoked,encoded);
    }
    public boolean fresh(long now){return now>=issuedAt&&now<expiresAt;}
    public void successor(DistributionIndex old){if(old==null)return;require(repository.equals(old.repository)&&signer.equals(old.signer)&&revision>=old.revision&&(revision!=old.revision||digest.equals(old.digest))&&revoked.containsAll(old.revoked));
        Map<String,Entry> prior=new TreeMap<>(), high=new TreeMap<>(), next=new TreeMap<>();
        for(Entry e:old.entries){prior.put(e.module+":"+e.version,e);if(!high.containsKey(e.module)||PackPolicy.compareVersions(e.version,high.get(e.module).version)>0)high.put(e.module,e);}
        for(Entry e:entries){Entry p=prior.get(e.module+":"+e.version);if(p!=null)require(p.sha256.equals(e.sha256)&&p.tag.equals(e.tag)&&p.assetId==e.assetId&&p.qualificationSha256.equals(e.qualificationSha256)&&p.qualificationAssetId==e.qualificationAssetId&&p.inputSha256.equals(e.inputSha256)&&p.manifest.equals(e.manifest)&&p.provenance.equals(e.provenance));else if(high.containsKey(e.module))require(PackPolicy.compareVersions(e.version,high.get(e.module).version)>0);
            if(!next.containsKey(e.module)||PackPolicy.compareVersions(e.version,next.get(e.module).version)>0)next.put(e.module,e);}
        for(Entry e:high.values())require(next.containsKey(e.module)&&PackPolicy.compareVersions(next.get(e.module).version,e.version)>=0);
    }
    public Entry newest(String module,Map<String,Long> versions,String installed,String pin){Entry best=null;for(Entry e:entries){if(revoked.contains(e.sha256)||!e.module.equals(module)||!e.compatible(versions)||pin!=null&&!pin.equals(e.version)||installed!=null&&PackPolicy.compareVersions(e.version,installed)<=0)continue;if(best==null||PackPolicy.compareVersions(e.version,best.version)>0)best=e;}return best;}
}
