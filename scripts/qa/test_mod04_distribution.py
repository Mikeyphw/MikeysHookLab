"""Local exact-byte interop fixtures, not hosted or physical acceptance."""
import copy, hashlib, json, pathlib, subprocess, tempfile, unittest, zipfile, io
from test_mod02_pack import fixture_dex
from module_sources import manifest, settings_schema, ROSTER
from qualification import canonical, CASES, REPO
import module_release as release
ROOT=pathlib.Path(__file__).resolve().parents[2]

def digest(b):return hashlib.sha256(b).hexdigest()

class DistributionChecks(unittest.TestCase):
    def test_release_plan(self):
        a={m:'a'*64 for m in ROSTER};first=release.release_plan(a,None);self.assertEqual(set(first),set(ROSTER))
        rows=[{'manifest':{'module_id':m,'version':v['version']},'input_sha256':v['input_sha256'],'sha256':'b'*64} for m,v in first.items()]
        old={'modules':rows,'revoked':[]};self.assertEqual(release.release_plan(a,old),{})
        self.assertEqual(set(release.release_plan({**a,'chatgpt':'c'*64},old)),{'chatgpt'})
        self.assertEqual(set(release.release_plan({m:'c'*64 for m in a},old)),set(ROSTER))
        self.assertEqual(set(release.release_plan(a,{**old,'revoked':['b'*64]})),set(ROSTER))

    def test_signed_index_and_store(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=pathlib.Path(tmp);inputs=p/'inputs';inputs.mkdir();classes=p/'classes';classes.mkdir();cli=p/'hooklabctl';key=p/'key.pem';private=p/'private.der';public=p/'public.der'
            def run(*args,ok=True,cwd=ROOT):
                r=subprocess.run(list(map(str,args)),cwd=cwd,capture_output=True,text=True,timeout=120);self.assertEqual(r.returncode==0,ok,r.stdout+r.stderr);return r.stdout
            run('openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:3072','-out',key)
            run('openssl','pkcs8','-topk8','-nocrypt','-in',key,'-outform','DER','-out',private);private.chmod(0o600)
            run('openssl','pkey','-in',key,'-pubout','-outform','DER','-out',public)
            run('go','build','-o',cli,'./cmd/hooklabctl',cwd=ROOT/'tools/hooklabctl')
            sources=sorted(ROOT.glob('sdk/src/main/java/**/*.java'))+sorted(ROOT.glob('runtime/src/main/java/**/*.java'))+[ROOT/'scripts/qa/Mod04DistributionChecks.java',ROOT/'scripts/qa/Mod02PackChecks.java',ROOT/'app/src/main/java/com/mikeyphw/hooklab/packs/distribution/GithubRepository.java']
            run('javac','--release','17','-Xlint:all','-Werror','-d',classes,*sources)
            gates={g:{'verdict':'PASS','job_result':'success'} for g in ('source','android','ci24','terminal','design','visual','adaptive','go')};gates['functional']={'verdict':'NOT_APPLICABLE','job_result':'skipped'}
            q={'schema':'hooklab.qualification.receipt.v1','repository':REPO,'source_sha':'a'*40,'source_tree':'b'*40,'run_id':'123','run_attempt':'1','overall':'PASS_TARGETED_HOSTED','plan_sha256':'e'*64,'gates':gates,'adaptive_cases':sorted(CASES),'physical_device_qualified':False,'release_qualified':False,'visual_approved':False}
            qb=canonical(q);qh=digest(qb);(inputs/(qh+'.json')).write_bytes(qb)
            def row(module,version,asset):
                m=manifest(module,version=version);pr={'schema':'hooklab.pack.provenance.v1','source_sha':'a'*40,'source_tree':'b'*40,'run_id':123,'run_attempt':1,'settings_schema':settings_schema(module),'channel':'personal'}
                mf=p/'manifest.json';mf.write_bytes(canonical(m));pf=p/'provenance.json';pf.write_bytes(canonical(pr));dex=p/'classes.dex';dex.write_bytes(fixture_dex(('L'+m['entrypoint'].replace('.','/')+';').encode()));pack=p/(str(asset)+'.hookpack')
                run(cli,'module','pack',mf,pf,dex,private,pack);b=pack.read_bytes();h=digest(b);(inputs/(h+'.hookpack')).write_bytes(b)
                return {'manifest':m,'provenance':pr,'tag':f'hooklab-module-{module}-v{version}','asset_id':asset,'sha256':h,'size':len(b),'qualification_asset_id':asset+100,'qualification_sha256':qh,'qualification_size':len(qb),'workflow_id':425729236,'input_sha256':'c'*64,'changelog':'Local fixture'}
            a=row('chatgpt','1.2.0',1);b=row('wordmaster','1.2.0',2);c=row('chatgpt','1.2.1',3)
            base={'schema':'hooklab.distribution.index.v1','repository':REPO,'revision':1,'issued_at':1000,'expires_at':2000,'modules':[a,b],'revoked':[]}
            def sign(name,obj,ok=True):
                f=p/(name+'.json');raw=p/(name+'-payload.json');raw.write_bytes(canonical(obj));run(cli,'index','sign',raw,private,inputs,f,ok=ok);return f
            i=sign('first',base);j=sign('second',{**base,'revision':2,'modules':[a,b,c]});removed=sign('removed',{**base,'revision':3,'modules':[a,b]});equiv=sign('equiv',{**base,'revision':2,'modules':[a,b,c],'issued_at':1001});missing=sign('missing',{**base,'revision':3,'modules':[a,c]})
            sign('duplicate',{**base,'modules':[a,a]},False);sign('wrong-hash',{**base,'modules':[{**a,'sha256':'d'*64}]},False)
            run(cli,'index','verify',i,public);run('java','-cp',f'{classes}:{ROOT/"runtime/src/main/resources"}','Mod04DistributionChecks','fixture',i,public,REPO,inputs,j,removed,equiv,missing,p/'store')
            bad=p/'tamper.json';x=json.loads(i.read_bytes());x['signature']='A'+x['signature'][1:];bad.write_bytes(canonical(x));run(cli,'index','verify',bad,public,ok=False)
            print('MOD04_GO_JAVA_INDEX_AND_TRANSACTION_PASS local synthetic payloads')

            # Exercise the production release coordinator through a remote boundary
            # that cannot publish outside this process, including interrupted resume.
            personal=p/'personal';personal.mkdir();signed=p/'signed';signed.mkdir();(signed/'publisher-test.der').write_bytes(public.read_bytes())
            ident={'source_sha':'a'*40,'source_tree':'b'*40,'run_id':123,'run_attempt':1}
            for m in sorted(ROSTER):
                d=personal/m;d.mkdir();md=manifest(m);(d/'manifest.json').write_bytes(canonical(md));(d/'classes.dex').write_bytes(fixture_dex(('L'+md['entrypoint'].replace('.','/')+';').encode()))
                prov={'schema':'hooklab.pack.provenance.v1',**ident,'settings_schema':settings_schema(m),'channel':'personal'};(d/'provenance.json').write_bytes(canonical(prov));tp=d/'test.json';tp.write_bytes(canonical({**prov,'channel':'test'}));args=[]
                if m in ('chatgpt','wordmaster'):
                    (d/'ui.dex').write_bytes(fixture_dex(('Lcom/mikeyphw/hooklab/moduleui/'+m+'/Provider;').encode()));(d/'ui-resources.json').write_bytes((ROOT/'modules'/m/'ui/resources.json').read_bytes());args=['--ui-dex',d/'ui.dex','--ui-resources',d/'ui-resources.json']
                run(cli,'module','pack',d/'manifest.json',tp,d/'classes.dex',private,signed/(m+'.hookpack'),*args);tp.unlink()
            (signed/'receipt.json').write_bytes(canonical({'schema':'hooklab.mod02.hosted.v1',**ident,'status':'SIGNED_TEST_PACKS_VERIFIED_NOT_ACTIVATED','files':{f.name:digest(f.read_bytes()) for f in signed.iterdir()}}))
            class Remote:
                def __init__(self):self.rows=[];self.data={};self.events=[];self.fail=True
                def releases(self):return copy.deepcopy(self.rows)
                def tag(self,tag):
                    for row in self.rows:
                        if row['tag_name']==tag and not row['draft']:return {'object':{'type':'commit','sha':ident['source_sha'],'url':'fixture'}}
                def asset(self,aid,size,sha):
                    b=self.data[aid];assert len(b)==size and digest(b)==sha;return b
                def mutate(self,path,body,method='POST'):
                    if method=='POST':
                        row={**body,'id':len(self.rows)+1,'assets':[]};self.rows.append(row);return copy.deepcopy(row)
                    row=self.rows[int(path.rsplit('/',1)[1])-1];assert row['draft'];row['draft']=False;self.events.append(row['tag_name']);return copy.deepcopy(row)
                def upload(self,rid,name,path):
                    if name=='hooklab-index.json' and self.fail:self.fail=False;raise ValueError('injected index upload interruption')
                    b=path.read_bytes();aid=len(self.data)+100;self.data[aid]=b;row={'id':aid,'name':name,'state':'uploaded','size':len(b),'digest':'sha256:'+digest(b)};self.rows[rid-1]['assets'].append(row);return copy.deepcopy(row)
                def json(self,path):return {'object':{'sha':ident['source_sha']}}
            remote=Remote();qual=inputs/(qh+'.json')
            def publish(name):
                work=p/name;work.mkdir();release.sign_and_publish(remote,ident,personal,qual,classes,work,cli,private,public)
            with self.assertRaisesRegex(ValueError,'injected index upload'):publish('release-first')
            self.assertEqual(len(remote.events),3);self.assertTrue(all('module-' in e for e in remote.events));self.assertTrue(remote.rows[-1]['draft'])
            publish('release-resume');self.assertEqual(len(remote.events),4);self.assertEqual(remote.events[-1],'hooklab-index-r1');self.assertEqual(len(remote.data),7)
            publish('release-noop');self.assertEqual(len(remote.events),4);self.assertEqual(len(remote.data),7)
            # Exact provider job and artifact responses for testing-lane admission.
            fixtures=p/'testing';fixtures.mkdir();routes={}
            def add(path,obj):
                f=fixtures/str(len(routes));f.write_bytes(obj if isinstance(obj,bytes) else canonical(obj));routes[path]=f.name
            def zipped(files):
                b=io.BytesIO()
                with zipfile.ZipFile(b,'w',zipfile.ZIP_DEFLATED) as z:
                    for n,data in files.items():z.writestr(n,data)
                return b.getvalue()
            repo={'id':42,'full_name':REPO};add('/actions/runs/123',{'id':123,'run_attempt':1,'workflow_id':425729236,'path':'.github/workflows/android.yml','head_sha':'a'*40,'head_repository':repo,'repository':repo,'status':'completed','conclusion':'success'})
            jobs=['HookLab / impact plan','HookLab / targeted qualification','HookLab / source contracts','Android build, unit tests and lint','HookLab / terminal contract','HookLab / Go qualification','design / Frozen browser reference and semantic gate','visual / Real Activity native visual candidate gate','ci24 / Summarize all 24 shards (fail closed)','adaptive / Extended UI / exact matrix aggregate']+['ci24 / JVM / shard '+str(i)+'/24' for i in range(1,25)]+['adaptive / Adaptive UI / '+c for c in sorted(CASES)]
            skipped=['functional','ci24 / Standalone dependency preparation','adaptive / Extended UI / exact shared APK build'];all_jobs=[{'name':n,'run_id':123,'run_attempt':1,'head_sha':'a'*40,'status':'completed','conclusion':'skipped' if n in skipped else 'success'} for n in jobs+skipped]
            for start in range(0,len(all_jobs),5):add('/actions/runs/123/attempts/1/jobs?per_page=5&page='+str(start//5+1),{'jobs':all_jobs[start:start+5],'total_count':len(all_jobs)})
            add('/git/commits/'+'a'*40,{'sha':'a'*40,'tree':{'sha':'b'*40}})
            archives=[('hooklab-qualification-123-1',zipped({'plan.json':canonical({'source_sha':'a'*40,'source_tree':'b'*40,'plan_sha256':'e'*64}),'receipt.json':qb})),('hooklab-mod02-test-packs-123-1',zipped({f.name:f.read_bytes() for f in signed.iterdir()}))];inventory=[]
            for aid,(name,data) in enumerate(archives,101):
                inventory.append({'id':aid,'name':name,'size_in_bytes':len(data),'digest':'sha256:'+digest(data),'expired':False,'expires_at':'2099-01-01T00:00:00Z','workflow_run':{'id':123,'head_sha':'a'*40,'repository_id':42,'head_repository_id':42}});add('/actions/artifacts/'+str(aid)+'/zip',data)
            add('/actions/runs/123/artifacts?per_page=10&page=1',{'artifacts':inventory});(fixtures/'routes.json').write_bytes(canonical(routes))
            run('java','-cp',f'{classes}:{ROOT/"runtime/src/main/resources"}','Mod04DistributionChecks','testing',fixtures,public,REPO)
            print('MOD04_PROTECTED_RELEASE_RESUME_AND_TESTING_LANE_PASS local synthetic provider')
