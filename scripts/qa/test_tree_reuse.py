"""Exact Git merge and provider evidence admission; no live credentials."""
import copy
import datetime as dt
import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch
import qualification as q
import tree_reuse as r
from test_qualification import POLICY, write, commit, jobs


def archive(items):
    b=io.BytesIO()
    with zipfile.ZipFile(b,'w') as z:
        for key,val in items.items():z.writestr(key,q.canonical(val)+b'\n')
    return b.getvalue()


class FixtureApi:
    def __init__(self):self.rows={};self.blobs={}
    def json(self,path):return copy.deepcopy(self.rows[path])
    def artifact(self,aid,size):return self.blobs[aid]


class ReuseTests(unittest.TestCase):
    def setUp(self):
        tmp=tempfile.TemporaryDirectory();self.addCleanup(tmp.cleanup);self.root=Path(tmp.name)
        q.git(self.root,'init','-q');q.git(self.root,'config','user.name','Fixture');q.git(self.root,'config','user.email','fixture@example.invalid');write(self.root,q.POLICY,json.dumps(POLICY));write(self.root,'README.md','base');self.base=commit(self.root)
        q.git(self.root,'checkout','-qb','feature');write(self.root,'sdk/src/Changed.java','class Changed {}');write(self.root,'tools/hooklabctl/go.mod','module fixture');self.pr=commit(self.root)
        prior=q.plan(self.root,self.pr,self.base,'pull_request','11','1');self.prior=prior
        q.git(self.root,'checkout','--detach',self.base);q.git(self.root,'merge','--no-ff','-m','Fixture merge',self.pr);self.head=q.git(self.root,'rev-parse','HEAD');self.plan=q.plan(self.root,self.head,self.base,'push','12','1')
        self.api=FixtureApi();self.prefix='/repos/'+q.REPO;now=dt.datetime.now(dt.timezone.utc)
        repo={'id':1,'full_name':q.REPO};pr={'number':37,'state':'closed','merged':True,'merge_commit_sha':self.head,'head':{'sha':self.pr,'ref':'feature','repo':repo},'base':{'sha':self.base,'ref':'main','repo':repo},'merged_at':(now-dt.timedelta(minutes=1)).isoformat()}
        run={'id':11,'workflow_id':r.WORKFLOW_ID,'path':r.WORKFLOW,'head_sha':self.pr,'head_branch':'feature','event':'pull_request','run_attempt':1,'status':'completed','conclusion':'success','repository':repo,'head_repository':repo,'pull_requests':[{'number':37}],'created_at':(now-dt.timedelta(minutes=5)).isoformat(),'updated_at':(now-dt.timedelta(minutes=2)).isoformat()}
        self.runpath=self.prefix+'/actions/runs/11';self.jobpath=self.runpath+'/attempts/1/jobs?per_page=100';self.artpath=self.runpath+'/artifacts?per_page=100';self.prpath=self.prefix+'/pulls/37';self.api.rows.update({self.prefix+'/actions/runs/12':{**run,'id':12,'event':'push','head_sha':self.head,'head_branch':'main'},self.prefix+'/commits/'+self.head+'/pulls?per_page=100':[pr],self.prpath:pr,self.prefix+f'/actions/workflows/{r.WORKFLOW_ID}/runs?head_sha={self.pr}&event=pull_request&per_page=100':{'total_count':1,'workflow_runs':[run]},self.runpath:run})
        names={n:'success' for n in r.BASE_JOBS|set(r.DIRECT.values())|{'design / Frozen browser reference and semantic gate','visual / Real Activity native visual candidate gate','adaptive / Extended UI / exact matrix aggregate','ci24 / Summarize all 24 shards (fail closed)'}|{f'ci24 / JVM / shard {i}/24' for i in range(1,25)}|{'adaptive / Adaptive UI / '+c for c in q.CASES}}
        names.update({'functional':'skipped','adaptive / Extended UI / exact shared APK build':'skipped','ci24 / Standalone dependency preparation':'skipped'})
        rr=[{'name':n,'conclusion':v,'run_id':11,'run_attempt':1,'head_sha':self.pr,'status':'completed'} for n,v in names.items()];self.api.rows[self.jobpath]={'total_count':len(rr),'jobs':rr}
        self.qual='hooklab-qualification-11-1';artifacts={self.qual:archive({'plan.json':prior,'receipt.json':q.aggregate(prior,prior,jobs(prior))})}
        for n in {f'hooklab-ci24-11-1-shard-{i}' for i in range(1,25)}|{'hooklab-ci24-11-1-summary','hooklab-dependencies-11-1','hooklab-extended-summary-11-1','hooklab-matrix-apks-11-1','hooklab-go-11-1'}|{f'hooklab-extended-11-1-{c}' for c in q.CASES}:artifacts[n]=archive({'fixture.json':{}})
        aa=[]
        for i,(n,b) in enumerate(artifacts.items(),1):
            self.api.blobs[i]=b;aa.append({'id':i,'name':n,'digest':'sha256:'+q.sha(b),'size_in_bytes':len(b),'expired':False,'expires_at':(now+dt.timedelta(days=1)).isoformat(),'workflow_run':{'id':11,'head_sha':self.pr,'repository_id':1,'head_repository_id':1}})
        self.api.rows[self.artpath]={'total_count':len(aa),'artifacts':aa}
    def admit(self):
        # Provider identity tests isolate the payload validator. Its own full
        # synthetic fixture tests below exercise the real read-only validators.
        with patch.object(r,'verify_payloads'):
            return r.admit(self.root,self.plan,self.api,12)
    def fallback(self,plan=None):
        with patch.object(r,'verify_payloads'):
            return r.decide(self.root,self.plan if plan is None else plan,self.api,12)
    def test_exact_merge_and_receipt(self):
        proof=self.admit();self.assertEqual(proof['gates'],sorted(r.REUSE));self.assertEqual(proof['current_commit'],self.head);self.assertEqual(proof['prior_commit'],self.pr)
        j=jobs(self.plan);j.update({g:'skipped' for g in proof['gates']});receipt=q.aggregate(self.plan,self.plan,j,proof)
        self.assertEqual(receipt['overall'],'PASS_TARGETED_HOSTED_WITH_REUSE');self.assertEqual(receipt['gates']['adaptive']['verdict'],'REUSED_TREE_QUALIFIED');self.assertEqual(receipt['gates']['android']['verdict'],'PASS');self.assertFalse(receipt['physical_device_qualified']);self.assertEqual(receipt['tree_reuse']['prior_commit'],self.pr)
        with self.assertRaises(ValueError):q.aggregate(self.plan,self.plan,j)
    def test_wrong_event_and_direct_push(self):
        self.assertIsNone(self.fallback({**self.plan,'event':'workflow_dispatch'}));self.assertIsNone(self.fallback({**self.plan,'base_sha':self.pr,'requested_base':self.pr}))
    def test_unexpected_validator_exception_falls_back(self):
        import xml.etree.ElementTree as ET
        for error in (ET.ParseError("invalid XML"),AttributeError("invalid provider response")):
            with patch.object(r,"admit",side_effect=error):
                self.assertIsNone(r.decide(self.root,self.plan,self.api,12))
    def test_changed_tree_refused(self):
        self.plan['source_tree']='0'*40;self.assertIsNone(self.fallback())
    def test_failed_prior(self):
        self.api.rows[self.runpath]['conclusion']='failure';self.assertIsNone(self.fallback())
    def test_wrong_attempt(self):
        self.api.rows[self.runpath]['run_attempt']=2;self.assertIsNone(self.fallback())
    def test_fork(self):
        self.api.rows[self.prpath]['head']['repo']={'id':2};self.assertIsNone(self.fallback())
    def test_missing_shard(self):
        a=self.api.rows[self.jobpath];a['jobs']=[j for j in a['jobs'] if j['name']!='ci24 / JVM / shard 24/24'];a['total_count']=len(a['jobs']);self.assertIsNone(self.fallback())
    def test_incomplete_job_pagination(self):
        self.api.rows[self.jobpath]['total_count']+=1;self.assertIsNone(self.fallback())
    def test_failed_adaptive_case(self):
        next(j for j in self.api.rows[self.jobpath]['jobs'] if j['name']=='adaptive / Adaptive UI / api29-compact')['conclusion']='failure';self.assertIsNone(self.fallback())
    def test_missing_artifact(self):
        a=self.api.rows[self.artpath];a['artifacts'].pop();a['total_count']-=1;self.assertIsNone(self.fallback())
    def test_expired_artifact(self):
        self.api.rows[self.artpath]['artifacts'][0]['expired']=True;self.assertIsNone(self.fallback())
    def test_tampered_receipt_digest(self):
        self.api.blobs[1]=b'tampered';self.assertIsNone(self.fallback())
    def test_wrong_plan_even_with_matching_artifact_digest(self):
        b=archive({'plan.json':{**self.prior,'policy_sha256':'0'*64},'receipt.json':q.aggregate(self.prior,self.prior,jobs(self.prior))});self.api.blobs[1]=b;self.api.rows[self.artpath]['artifacts'][0].update(digest='sha256:'+q.sha(b),size_in_bytes=len(b));self.assertIsNone(self.fallback())
    def test_forbidden_gate_reuse_and_rerun_mismatch(self):
        proof=self.admit();j=jobs(self.plan);j.update({g:'skipped' for g in proof['gates']});self.assertRaises(ValueError,q.aggregate,self.plan,self.plan,j,{**proof,'gates':['android']});self.assertRaises(ValueError,q.aggregate,self.plan,self.plan,{**j,'adaptive':'success'},proof)
    def test_unsafe_zip_member(self):
        with self.assertRaises(ValueError):r.read_json_zip(archive({'../plan.json':{}}),{'plan.json'})
    def test_payload_admission_does_not_accept_provider_metadata_only(self):
        with self.assertRaises(ValueError):r.admit(self.root,self.plan,self.api,12)
    def test_tampered_underlying_artifact(self):
        proof=self.admit();item=proof['artifacts']['hooklab-go-11-1'];self.api.blobs[item['id']]=b'changed'
        with self.assertRaisesRegex(ValueError,'artifact digest mismatch'):r.archive(self.api,item)
    def test_full_payload_validation(self):
        import ci24, adaptive_matrix as a, dependency_prep as dp
        import xml.etree.ElementTree as ET
        from PIL import Image
        from test_adaptive_matrix import AdaptiveMatrixTests
        # Real source inventory, and deliberately synthetic prior artifacts.
        for i in range(24):write(self.root,f'app/src/test/java/example/T{i:02}Test.java',f'package example; public class T{i:02}Test {{ @Test public void works() {{}} }}')
        q.git(self.root,'add','-A');q.git(self.root,'commit','-qm','fixture tests')
        prior=dict(self.prior,source_tree=q.git(self.root,'rev-parse','HEAD^{tree}'))
        ident={k:prior[k] for k in ('source_sha','source_tree','run_id','run_attempt','repository')}
        expected={**ident,'schema':'hooklab.uxr04.case.v1','hosted_emulator_only':True,'physical_device_qualified':False,'release_qualified':False,'visual_approved':False}
        with tempfile.TemporaryDirectory() as t:
            dest=Path(t);d=dest/'hooklab-dependencies-11-1';d.mkdir();ready=d/'ready.json';h='a'*64
            ci24.dump(ready,{'schema':dp.SCHEMA,**ident,'graph_sha256':dp.graph_digest(self.root),'resolution_sha256':dp.resolution_digest(self.root),'profile':'jvm','tasks':dp.JVM_TASKS,'offline_result':'PASS','elapsed_seconds':0,'reused_ready':False,'locks':['app.lockfile'],'mirror':{'primary':dp.POLICY['primary_fingerprint'],'signer':dp.POLICY['signing_fingerprint'],'files':[]},'files':{'modules/a':h,'wrapper/a':h,'repository/.hooklab-attestation.json':h,'locks/app.lockfile':h}})
            classes=ci24.inventory(self.root);dh=ci24.digest(ready);shards=[]
            for i in range(1,25):
                folder=dest/f'hooklab-ci24-11-1-shard-{i}';j=folder/'junit';j.mkdir(parents=True);assigned=ci24.assignment(classes,i)
                for fq,record in assigned.items():
                    e=ET.Element('testsuite',{'tests':str(len(record['methods'])),'failures':'0','errors':'0','skipped':'0'})
                    for m in record['methods']:ET.SubElement(e,'testcase',{'classname':fq,'name':m})
                    ET.ElementTree(e).write(j/('TEST-'+fq+'.xml'))
                hashes,cases=ci24.parse_xml(j,assigned);ci24.dump(folder/'receipt.json',{'schema':ci24.SCHEMA,**ident,'dependency_receipt_sha256':dh,'shard':i,'shards':24,'classes':assigned,'status':'PASS','gradle_exit':0,'release_qualified':False,'physical_device_qualified':False,'junit_sha256':hashes,'tests':cases,'test_count':len(cases)})
                shards.append(ci24.verify_shard(folder,assigned,ident,i,dh))
            f=dest/'hooklab-ci24-11-1-summary';f.mkdir();ci24.dump(f/'CI24_SUMMARY.json',{'schema':ci24.SUMMARY,**ident,'dependency_receipt_sha256':dh,'overall':'PASS_CI24_VERIFIED','shard_count':24,'class_count':24,'test_count':24,'shards':shards,'release_qualified':False,'physical_device_qualified':False,'emulator_ui_qualified':False})
            f=dest/'hooklab-matrix-apks-11-1';f.mkdir()
            for name in a.APK_FILES.values():
                with zipfile.ZipFile(f/name,'w') as z:z.writestr('AndroidManifest.xml','fixture');z.writestr('classes.dex',name)
            build={**a.apk_identity(expected),'apks':{k:a.apk_file(f/v) for k,v in a.APK_FILES.items()}};a.dump(f/'apk-build.json',build)
            # Single selected case is sufficient to exercise actual independent
            # validation; provider roster tests independently require all ten.
            prior['adaptive_cases']=['api29-compact'];case='api29-compact';cfg=a.config(case);f=dest/f'hooklab-extended-11-1-{case}';f.mkdir();routes=[]
            for i,route in enumerate(a.ROUTES):
                row={'route':route,'width_dp':320,'height_dp':660,'font_scale':2.,'orientation':1,'headings':1,'actions':2,'focusable':2,'visible_actions':1,'text_contrast_min':5.,'primary_contrast':8.}
                if route in ('home','hooks','diagnostics'):row['rail']=False
                routes.append(row);im=Image.new('RGB',(cfg['width_px'],cfg['height_px']),(10+i,20,30));im.putpixel((0,0),(255,0,0));im.save(f/(route+'.png'))
                a.dump(f/(route+'.hierarchy.json'),[{'bounds':[0,0,100,100],'clickable':True,'enabled':True,'focusable':True,'checked':False,'labelled':True}])
            a.dump(f/'journey.json',{'schema':'hooklab.uxr04.adaptive.v1','api':29,'theme':'light','source':ident['source_sha'],'framework_disconnected':True,'physical_device_qualified':False,'routes':routes,'ime_observed':True,'orientation_round_trip':True,'separate_catalog_targets':True,'scenario_launch_ms':200})
            frames=AdaptiveMatrixTests.frames();a.dump(f/'performance.json',{'schema':'hooklab.uxr04.performance.v2','events':4000,'dropped':1,'page_size':30,'budget_enforced':False,'import_ms':23,'page_action_ms':[1,2,3,4,5],'sampled_pss_kb':[100,102,103,101,100],'native_frames':frames});a.dump(f/'launch-performance.json',{'cold_launch_ms':[100,200,300,400,500],'cold_launch_pids':[101,102,103,104,105],'cold_launch_p95_ms':500,'frame_ms':a.frame_durations(frames),'frame_p95_ms':19.,'frame_workload':frames['workload'],'budget_enforced':False})
            perf={'scenario_launch_ms':200,'page_action_p95_ms':5,'sampled_peak_pss_kb':103,'cold_launch_p95_ms':500,'frame_p95_ms':19.,'budget_enforced':False};ah=build['apks']['app']['sha256'];th=build['apks']['test']['sha256']
            a.dump(f/'receipt.json',{**expected,'case':case,'configuration':cfg,'status':'PASS_EXTENDED_NOT_DEVICE_APPROVAL','completed_tests':a.roster(case),'apk_build':build,'app_apk_sha256':ah,'installed_apk_sha256':ah,'android_test_apk_sha256':th,'installed_test_apk_sha256':th,'files':{x.name:a.sha(x) for x in f.iterdir()},'performance_summary':perf})
            f=dest/'hooklab-extended-summary-11-1';f.mkdir();a.dump(f/'extended-summary.json',{case:perf})
            f=dest/'hooklab-go-11-1';f.mkdir();a.dump(f/'go-receipt.json',{'schema':'hooklab.go.qualification.v1',**ident,'module':'tools/hooklabctl','files':{p:q.sha((self.root/p).read_bytes()) for p in q.git(self.root,'ls-files','tools/hooklabctl').splitlines() if q.is_go(p)},'vet':'PASS','test_race':'PASS','build':'PASS','tests':['example::TestFixture']})
            api=FixtureApi();inv={}
            def package():
                for i,folder in enumerate(sorted(dest.iterdir()),1):
                    b=io.BytesIO()
                    with zipfile.ZipFile(b,'w') as z:
                        for p in sorted(folder.rglob('*')):
                            if p.is_file():z.write(p,p.relative_to(folder).as_posix())
                    raw=b.getvalue();api.blobs[i]=raw;inv[folder.name]={'id':i,'sha256':q.sha(raw),'size':len(raw)}
            package();r.verify_payloads(self.root,prior,sorted(r.REUSE),api,inv,'not present')
            # Matching archive digest is insufficient when internal evidence drifts.
            p=dest/'hooklab-go-11-1'/'go-receipt.json';v=json.loads(p.read_text());v['run_attempt']='2';a.dump(p,v);package()
            with self.assertRaisesRegex(ValueError,'Go receipt mismatch'):r.verify_payloads(self.root,prior,sorted(r.REUSE),api,inv,'not present')
    def test_repo_api_origin_and_no_auth_redirect(self):
        api=r.Api('fixture');self.assertRaises(ValueError,api._request,'/repos/other/repo/actions/runs/12','application/json');self.assertIsNone(r.NoRedirect().redirect_request(None,None,302,None,None,'https://other.example/'))

if __name__=='__main__':unittest.main()
