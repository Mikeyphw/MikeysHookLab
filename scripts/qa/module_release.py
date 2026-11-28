#!/usr/bin/env python3
"""Qualified module distribution. Never invokes Gradle or generates a publisher key.
The read-only verifier runs before the protected signing job and again in it.
Publication is serialized: verified module assets first, signed index last.
"""
from __future__ import annotations
import argparse,base64,datetime as dt,hashlib,io,json,os,re,stat,subprocess,sys,tempfile,time,urllib.error,urllib.parse,urllib.request,zipfile
from pathlib import Path
import qualification as q
import tree_reuse as tr
from module_sources import ROOT,ROSTER,manifest,project,settings_schema
from mod02_hosted_packs import verify_artifacts

MAX_PACK=9*1024*1024
MAX_JSON=65536
SCHEMA='hooklab.distribution.index.v1'
INDEX=re.compile(r'hooklab-index-r([1-9][0-9]{0,17})\Z')
MINIMUM={'chatgpt':'1.2.0','wordmaster':'1.2.0','obtainx':'1.1.0'}

def require(ok,reason):q.require(ok,reason)
def digest(b):return q.sha(b)
def run(*args,cwd=ROOT,**kw):return subprocess.run([str(x) for x in args],cwd=cwd,check=True,capture_output=True,timeout=180,**kw).stdout

def input_digests(root=ROOT):
    """Hash actual projected module sources, shared SDK, UI ABI, and producer inputs."""
    common={str(p.relative_to(root)):digest(p.read_bytes()) for p in sorted((root/'sdk/src/main').rglob('*.java'))}
    for name in ('scripts/qa/module_sources.py','scripts/qa/mod02_hosted_packs.py','app/src/main/java/com/mikeyphw/hooklab/packs/ModuleUiProvider.java','runtime/src/main/java/com/mikeyphw/hooklab/runtime/pack/PackPolicy.java','tools/hooklabctl/cmd/hooklabctl/pack.go'):
        common[name]=digest((root/name).read_bytes())
    result={}
    for m in sorted(ROSTER):
        own={k:digest(v.encode()) for k,v in project(m,root).items()}
        if m in ('chatgpt','wordmaster'):
            for p in sorted((root/'modules'/m/'ui').glob('*')):
                require(p.is_file() and not p.is_symlink(),'unsafe UI source');own[str(p.relative_to(root))]=digest(p.read_bytes())
        result[m]=digest(q.canonical({'shared':common,'module':own,'manifest':manifest(m,version='0.0.0',root=root),'settings_schema':settings_schema(m),'d8':'36.0.0','android':'37.0','java_release':17,'min_api':29}))
    return result

def version_tuple(s):
    require(isinstance(s,str) and re.fullmatch(r'(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})',s),'invalid module version');return tuple(map(int,s.split('.')))

def release_plan(hashes,previous):
    require(set(hashes)==set(ROSTER),'module roster');rows=previous.get('modules',[]) if previous else []
    result={}
    for m in sorted(ROSTER):
        prior=[x for x in rows if x['manifest']['module_id']==m];current=max(prior,key=lambda x:version_tuple(x['manifest']['version']),default=None)
        if current and current['input_sha256']==hashes[m] and current['sha256'] not in previous['revoked']:continue
        if current:
            a,b,c=version_tuple(current['manifest']['version']);require(c<999999999,'version exhausted');version='.'.join(map(str,(a,b,c+1)))
        else:version=MINIMUM[m]
        result[m]={'version':version,'input_sha256':hashes[m]}
    return result

class Api(tr.Api):
    def releases(self):
        result=[]
        for page in range(1,11):
            rows=self.json(f'/repos/{q.REPO}/releases?per_page=100&page={page}')
            require(isinstance(rows,list) and len(rows)<=100,'release pagination');result.extend(rows)
            if len(rows)<100:
                require(len({x['tag_name'] for x in result})==len(result),'duplicate releases');return result
        raise ValueError('release pagination limit')
    def asset(self,asset_id,size,sha):
        require(type(asset_id) is int and asset_id>0 and type(size) is int and 0<size<=MAX_PACK and re.fullmatch('[a-f0-9]{64}',sha),'asset identity')
        try:response=self.opener.open(self._request(f'/repos/{q.REPO}/releases/assets/{asset_id}','application/octet-stream'),timeout=30)
        except urllib.error.HTTPError as error:
            require(error.code in (301,302,303,307,308),'asset redirect refused');u=urllib.parse.urlsplit(error.headers.get('Location',''))
            require(u.scheme=='https' and u.hostname in ('release-assets.githubusercontent.com','objects.githubusercontent.com') and not u.username and not u.password and not u.fragment and u.port in (None,443),'asset origin refused')
            response=self.opener.open(urllib.request.Request(u.geturl(),headers={'User-Agent':'HookLab-module-release'}),timeout=60)
        with response:b=response.read(size+1)
        require(len(b)==size and digest(b)==sha,'release asset hash mismatch');return b
    def mutate(self,path,body,method='POST'):
        require(path.startswith(f'/repos/{q.REPO}/releases') and '\n' not in path,'write route refused')
        return q.strict_json(run('gh','api','--method',method,path,'--input','-',input=q.canonical(body)))
    def upload(self,release_id,name,path):
        require(type(release_id) is int and release_id>0 and re.fullmatch(r'[A-Za-z0-9_.-]+',name),'asset upload identity')
        url=f'https://uploads.github.com/repos/{q.REPO}/releases/{release_id}/assets?name={name}'
        return q.strict_json(run('gh','api','--method','POST',url,'-H','Content-Type: application/octet-stream','--input',path))
    def tag(self,tag):
        require(re.fullmatch(r'hooklab-(?:index-r[1-9][0-9]*|module-[a-z]+-v[0-9]+\.[0-9]+\.[0-9]+)',tag),'tag route')
        try:return self.json(f'/repos/{q.REPO}/git/ref/tags/{tag}')
        except urllib.error.HTTPError as e:
            if e.code==404:return None
            raise

def zip_members(raw,expected,dest):
    dest.mkdir();seen=set();total=0
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        for x in z.infolist():
            name=x.filename;kind=stat.S_IFMT(x.external_attr>>16)
            require(name in expected and name not in seen and not x.is_dir() and kind in (0,stat.S_IFREG) and 0<x.file_size<=MAX_PACK,'artifact member refused');seen.add(name);total+=x.file_size;require(total<=32*1024*1024,'artifact expansion limit')
            path=dest/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(z.read(x))
        require(seen==set(expected),'artifact inventory mismatch')

def verified_run(api,rid,root,work,cli):
    """Require a fresh successful main run with every exact gate and real ART inputs."""
    head=q.git(root,'rev-parse','HEAD');tree=q.git(root,'rev-parse','HEAD^{tree}');prefix='/repos/'+q.REPO
    runinfo=api.json(f'{prefix}/actions/runs/{rid}');attempt=runinfo.get('run_attempt');repo=runinfo.get('repository') or {};repoid=repo.get('id')
    require(type(rid) is int and rid>0 and tr.integer(attempt) and tr.integer(repoid) and runinfo.get('id')==rid and runinfo.get('workflow_id')==tr.WORKFLOW_ID and runinfo.get('path','').split('@')[0]==tr.WORKFLOW and runinfo.get('head_sha')==head and runinfo.get('head_branch')=='main' and runinfo.get('event') in ('push','workflow_dispatch') and runinfo.get('status')=='completed' and runinfo.get('conclusion')=='success' and repo.get('full_name')==q.REPO and (runinfo.get('head_repository') or {}).get('id')==repoid,'fresh exact main qualification required')
    require(api.json(prefix+'/git/ref/heads/main')['object']['sha']==head,'main advanced')
    suffix=f'{rid}-{attempt}';names={f'hooklab-qualification-{suffix}',f'hooklab-mod02-art-{suffix}',f'hooklab-mod02-personal-signing-inputs-{suffix}',f'hooklab-mod02-test-packs-{suffix}'}
    inv=tr.artifact_inventory(api,rid,attempt,head,repoid,names)
    qual=f'hooklab-qualification-{suffix}';raw=tr.archive(api,inv[qual]);qual_dir=work/'qualification';zip_members(raw,{'plan.json','receipt.json'},qual_dir)
    plan=q.strict_json((qual_dir/'plan.json').read_bytes());receipt=q.strict_json((qual_dir/'receipt.json').read_bytes())
    expected=q.plan(root,head,plan['requested_base'] or '',runinfo['event'],rid,attempt);require(expected==plan,'independent plan mismatch')
    require(set(k for k,v in expected['gates'].items() if v['required'])==set(q.GATES)-{'functional'} and expected['adaptive_cases']==sorted(q.CASES),'full fresh qualification required')
    jobs=tr.provider_jobs(expected,api.json(f'{prefix}/actions/runs/{rid}/attempts/{attempt}/jobs?per_page=100'),rid,attempt,head)
    require(receipt==q.aggregate(expected,plan,jobs) and receipt['overall']=='PASS_TARGETED_HOSTED','qualification receipt mismatch')
    require((qual_dir/'receipt.json').stat().st_size<=MAX_JSON,'qualification exceeds signed metadata bound')
    ident={'source_sha':head,'source_tree':tree,'run_id':rid,'run_attempt':attempt}
    signed=work/'signed';personal=work/'personal';art=work/'art'
    zip_members(tr.archive(api,inv[f'hooklab-mod02-test-packs-{suffix}']),{'receipt.json','publisher-test.der'}|{m+'.hookpack' for m in ROSTER},signed)
    personal_names={'receipt.json'}|{m+'/'+n for m in ROSTER for n in ('manifest.json','provenance.json','classes.dex')}|{m+'/'+n for m in ('wordmaster','chatgpt') for n in ('ui.dex','ui-resources.json')}
    zip_members(tr.archive(api,inv[f'hooklab-mod02-personal-signing-inputs-{suffix}']),personal_names,personal)
    zip_members(tr.archive(api,inv[f'hooklab-mod02-art-{suffix}']),{'mod02-art.json'},art)
    verify_artifacts(signed,personal,art/'mod02-art.json',ident,cli)
    # Re-run independent Java signature/manifest checks on every admitted DEX.
    classes=work/'classes';classes.mkdir();sources=sorted(root.glob('sdk/src/main/java/**/*.java'))+sorted(root.glob('runtime/src/main/java/**/*.java'))+[root/'scripts/qa/Mod02PackChecks.java',root/'scripts/qa/Mod04DistributionChecks.java']
    run('javac','--release','17','-Xlint:all','-Werror','-d',classes,*sources,*sorted(root.glob('app/src/main/java/com/mikeyphw/hooklab/packs/distribution/*.java')))
    for m in ROSTER:run('java','-cp',f'{classes}:{root/"runtime/src/main/resources"}','Mod02PackChecks',signed/(m+'.hookpack'),signed/'publisher-test.der')
    return ident,personal,qual_dir/'receipt.json',classes

def asset_row(row,name):
    require(row['name']==name and row.get('state')=='uploaded' and type(row.get('id')) is int and row['id']>0 and type(row.get('size')) is int and row['size']>0 and isinstance(row.get('digest'),str) and re.fullmatch(r'sha256:[a-f0-9]{64}',row['digest']),'uploaded asset identity');return row['id'],row['size'],row['digest'][7:]

def prior_index(api,releases,public,cli,work):
    rows=[r for r in releases if INDEX.fullmatch(r['tag_name']) and r.get('draft') is False and r.get('prerelease') is False]
    if not rows:return None
    r=max(rows,key=lambda x:int(INDEX.fullmatch(x['tag_name']).group(1)));require(len(r['assets'])==1,'index asset inventory');aid,size,sha=asset_row(r['assets'][0],'hooklab-index.json');p=work/'previous-index.json';p.write_bytes(api.asset(aid,size,sha));data=q.strict_json(run(cli,'index','inspect',p,public));require(data['repository']==q.REPO and data['revision']==int(INDEX.fullmatch(r['tag_name']).group(1)),'index identity mismatch');return data

def ensure_asset(api,release,name,path):
    assets=[a for a in release['assets'] if a['name']==name];require(len(assets)<=1,'duplicate release asset')
    if not assets:
        require(release['draft'] is True,'published release cannot be changed');a=api.upload(release['id'],name,path);release['assets'].append(a)
    else:a=assets[0]
    aid,size,sha=asset_row(a,name);data=path.read_bytes();require(size==len(data) and sha==digest(data) and api.asset(aid,size,sha)==data,'release asset readback mismatch');return aid,size,sha

def ensure_draft(api,releases,tag,source,body):
    match=[r for r in releases if r['tag_name']==tag];require(len(match)<=1,'duplicate release tag')
    ref=api.tag(tag)
    if ref:require(ref['object']=={'type':'commit','sha':source,'url':ref['object']['url']},'tag source mismatch')
    if match:r=match[0];require(r.get('target_commitish')==source and r.get('prerelease') is False and r.get('body')==body,'existing release identity mismatch')
    else:
        require(ref is None,'unowned preexisting tag');r=api.mutate(f'/repos/{q.REPO}/releases',{'tag_name':tag,'target_commitish':source,'name':tag,'body':body,'draft':True,'prerelease':False,'make_latest':'false'});releases.append(r)
    require(type(r.get('id')) is int and r['id']>0 and isinstance(r.get('assets'),list),'release identity');return r

def publish(api,r,tag,source,names):
    require({a['name'] for a in r['assets']}==set(names),'release asset roster')
    if r['draft']:r=api.mutate(f'/repos/{q.REPO}/releases/{r["id"]}',{'draft':False,'make_latest':'false'},'PATCH')
    require(r['tag_name']==tag and r['draft'] is False and {a['name'] for a in r['assets']}==set(names),'release readback mismatch')
    ref=api.tag(tag);require(ref and ref['object'].get('type')=='commit' and ref['object'].get('sha')==source,'published tag source mismatch')

def sign_and_publish(api,ident,personal,qualification,classes,work,cli,private,public):
    releases=api.releases();previous=prior_index(api,releases,public,cli,work);now=int(time.time());hashes=input_digests();plan=release_plan(hashes,previous);revision=1 if previous is None else previous['revision']+1
    # Reuse all still-listed, exact signed assets; retain highest version and previous
    # per module, including a revoked highest version as anti-replay tombstone.
    rows=[];inputs=work/'verified-inputs';inputs.mkdir()
    if previous:
        for module in sorted(ROSTER):
            old=sorted((x for x in previous['modules'] if x['manifest']['module_id']==module),key=lambda x:version_tuple(x['manifest']['version']),reverse=True)
            for row in old[:1 if module in plan else 2]:
                for kind,suffix in (('', '.hookpack'),('qualification_', '.json')):
                    sha=row[kind+'sha256'];data=api.asset(row[kind+'asset_id'],row[kind+'size'],sha);p=inputs/(sha+suffix)
                    if p.exists():require(p.read_bytes()==data,'prior digest collision')
                    else:p.write_bytes(data)
                rows.append(row)
    if not plan and previous and previous['expires_at']>now+86400*7:
        print('MODULE_RELEASE_NO_CHANGE');return
    source=ident['source_sha'];qual_data=qualification.read_bytes();qh=digest(qual_data);(inputs/(qh+'.json')).write_bytes(qual_data)
    for m,change in plan.items():
        home=work/m;home.mkdir();mf=home/'manifest.json';mf.write_bytes(q.canonical(manifest(m,change['version'])));provenance=personal/m/'provenance.json';pack=home/(m+'.hookpack');args=[]
        if m in ('chatgpt','wordmaster'):args=['--ui-dex',personal/m/'ui.dex','--ui-resources',personal/m/'ui-resources.json']
        run(cli,'module','pack',mf,provenance,personal/m/'classes.dex',private,pack,*args);run(cli,'module','verify',pack,public)
        run('java','-cp',f'{classes}:{ROOT/"runtime/src/main/resources"}','Mod02PackChecks',pack,public)
        sha=digest(pack.read_bytes());(inputs/(sha+'.hookpack')).write_bytes(pack.read_bytes());tag=f'hooklab-module-{m}-v{change["version"]}'
        body=f'Qualified source {source}, run {ident["run_id"]} attempt {ident["run_attempt"]}. Module input {change["input_sha256"]}.'
        r=ensure_draft(api,releases,tag,source,body);aid,size,sha=ensure_asset(api,r,pack.name,pack);qid,qsize,qsha=ensure_asset(api,r,'qualification.json',qualification)
        row={'manifest':q.strict_json(mf.read_bytes()),'provenance':q.strict_json(provenance.read_bytes()),'tag':tag,'asset_id':aid,'sha256':sha,'size':size,'qualification_asset_id':qid,'qualification_sha256':qsha,'qualification_size':qsize,'workflow_id':tr.WORKFLOW_ID,'input_sha256':change['input_sha256'],'changelog':body};rows.append(row)
        publish(api,r,tag,source,{pack.name,'qualification.json'})
    # Sign only after all module asset bytes have independent readback. The index
    # itself is never exposed before the same full verification succeeds.
    payload={'schema':SCHEMA,'repository':q.REPO,'revision':revision,'issued_at':now,'expires_at':now+30*86400,'modules':sorted(rows,key=lambda x:(x['manifest']['module_id'],version_tuple(x['manifest']['version']))),'revoked':previous['revoked'] if previous else []}
    # Resume the exact draft index when an earlier publication was interrupted.
    tag=f'hooklab-index-r{revision}';existing=[r for r in releases if r['tag_name']==tag];index=work/'hooklab-index.json';body=f'Verified module index revision {revision} for {source}.'
    if existing and existing[0].get('assets'):
        require(existing[0]['draft'] is True and len(existing[0]['assets'])==1,'index resume refused');aid,size,sha=asset_row(existing[0]['assets'][0],'hooklab-index.json');index.write_bytes(api.asset(aid,size,sha));old=q.strict_json(run(cli,'index','inspect',index,public));require(old['issued_at']<=now<old['expires_at'],'draft index expired');payload['issued_at']=old['issued_at'];payload['expires_at']=old['expires_at'];require(payload==old,'draft index content changed')
    else:
        p=work/'payload.json';p.write_bytes(q.canonical(payload));run(cli,'index','sign',p,private,inputs,index)
    run(cli,'index','verify',index,public)
    command=['java','-cp',f'{classes}:{ROOT/"runtime/src/main/resources"}','Mod04DistributionChecks','verify',index,public,q.REPO,inputs]
    if previous:command.append(work/'previous-index.json')
    run(*command)
    require(api.json('/repos/'+q.REPO+'/git/ref/heads/main')['object']['sha']==source,'main advanced before index publication')
    r=ensure_draft(api,releases,tag,source,body);ensure_asset(api,r,'hooklab-index.json',index);publish(api,r,tag,source,{'hooklab-index.json'});print('SIGNED_MODULE_INDEX_PUBLISHED revision='+str(revision))

def main():
    p=argparse.ArgumentParser();p.add_argument('mode',choices=['verify','publish']);p.add_argument('--run',type=int,required=True);a=p.parse_args()
    try:
        require(os.getenv('GITHUB_ACTIONS')=='true' and os.getenv('RUNNER_OS')=='Linux' and os.getenv('GITHUB_REF')=='refs/heads/main' and os.getenv('GITHUB_REPOSITORY')==q.REPO and os.getenv('GITHUB_EVENT_NAME')=='workflow_dispatch','protected main workflow required')
        token=os.environ['GH_TOKEN'];api=Api(token);require(q.git(ROOT,'status','--porcelain','--untracked-files=no')=='','tracked source drift')
        with tempfile.TemporaryDirectory(prefix='hooklab-module-release-') as tmp:
            work=Path(tmp);cli=work/'hooklabctl';run('go','build','-o',cli,'./cmd/hooklabctl',cwd=ROOT/'tools/hooklabctl');ident,personal,qualification,classes=verified_run(api,a.run,ROOT,work,cli)
            if a.mode=='verify':print('MODULE_RELEASE_INPUTS_VERIFIED_NOT_RELEASED');return 0
            require(os.getenv('HOOKLAB_MODULE_SIGNING_APPROVED')=='true','protected signing job required');expected=os.environ.pop('HOOKLAB_MODULE_SIGNER_SHA256');key=base64.b64decode(os.environ.pop('HOOKLAB_MODULE_SIGNING_KEY_B64'),validate=True);require(0<len(key)<=8192 and re.fullmatch('[a-f0-9]{64}',expected),'signer identity')
            private=work/'publisher.der';fd=os.open(private,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
            with os.fdopen(fd,'wb') as f:f.write(key)
            public=work/'publisher-public.der';public.write_bytes(run('openssl','pkey','-inform','DER','-in',private,'-pubout','-outform','DER'));require(digest(public.read_bytes())==expected,'publisher fingerprint mismatch');del key
            sign_and_publish(api,ident,personal,qualification,classes,work,cli,private,public)
        return 0
    except (ValueError,KeyError,TypeError,OSError,subprocess.SubprocessError,zipfile.BadZipFile):
        print('MODULE_RELEASE_REFUSED (no credential or untrusted response logged)',file=sys.stderr);return 2
if __name__=='__main__':raise SystemExit(main())
