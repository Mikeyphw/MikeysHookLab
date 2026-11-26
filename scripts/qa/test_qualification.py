"""Real Git-tree and negative qualification regressions, without Gradle/ADB."""
import copy, json, os, subprocess, sys, tempfile, unittest, zipfile
from pathlib import Path
from unittest.mock import patch
import qualification as q
import adaptive_matrix as a
import native_smoke as n
import functional_ui as f
import shared_native_apks as shared
from go_qualification import run as go_run
ROOT=Path(__file__).resolve().parents[2]
POLICY=json.loads((ROOT/q.POLICY).read_text())
RUNTIME=next(iter(POLICY['runtime_only']))
FUNCTIONAL=next(iter(POLICY['functional_only']))

def write(root,path,text):
    p=root/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text);return p

def commit(root):
    q.git(root,'add','-A');q.git(root,'-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','--allow-empty','-qm','fixture');return q.git(root,'rev-parse','HEAD')

def required(plan):return {g for g,v in plan['gates'].items() if v['required']}

def jobs(plan):return {g:'success' if v['required'] else 'skipped' for g,v in plan['gates'].items()}

class GitPlanTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
        q.git(self.root,'init','-q');write(self.root,q.POLICY,json.dumps(POLICY));write(self.root,'README.md','before');write(self.root,RUNTIME,'before');write(self.root,FUNCTIONAL,'before');self.base=commit(self.root)
    def plan(self,changes=None,base=None,event='pull_request',attempt='1'):
        for k,v in (changes or {}).items():
            if v is None:(self.root/k).unlink()
            else:write(self.root,k,v)
        head=commit(self.root);return q.plan(self.root,head,self.base if base is None else base,event,'456',attempt)
    def test_documentation_only(self):
        p=self.plan({'README.md':'after','docs/plan.md':'new'});self.assertEqual(required(p),{'source'});self.assertEqual(p['adaptive_cases'],[])
    def test_go_only(self):
        p=self.plan({'tools/hooklabctl/main.go':'package main','tools/hooklabctl/go.mod':'module fixture'});self.assertEqual(required(p),{'source','go'})
    def test_reviewed_runtime_boundary(self):
        p=self.plan({RUNTIME:'after'});self.assertEqual(required(p),{'source','android','ci24'});self.assertFalse(p['gates']['visual']['required'])
    def test_reviewed_functional_boundary(self):
        p=self.plan({FUNCTIONAL:'after'});self.assertEqual(required(p),{'source','android','ci24','terminal','functional','adaptive'});self.assertEqual(p['adaptive_cases'],['api36-390']);self.assertEqual(p['functional_tests'],['termux'])
    def test_shared_sdk_fans_out(self):
        p=self.plan({'sdk/src/Shared.java':'changed'});self.assertEqual(required(p),q.FULL);self.assertEqual(set(p['adaptive_cases']),set(q.CASES));self.assertFalse(p['gates']['functional']['required'])
    def test_policy_change_full_even_documentation(self):
        p=self.plan({q.POLICY:json.dumps(POLICY,indent=2),'README.md':'after'});self.assertEqual(required(p),q.FULL)
    def test_unknown_new_file_full(self):
        self.assertEqual(required(self.plan({'app/new-file':'new'})),q.FULL)
    def test_runtime_deletion_full(self):
        self.assertEqual(required(self.plan({RUNTIME:None})),q.FULL)
    def test_runtime_rename_full(self):
        self.assertEqual(required(self.plan({RUNTIME:None,'docs/renamed.md':'before'})),q.FULL)
    def test_symlink_full(self):
        (self.root/'docs').mkdir();(self.root/'docs/link.md').symlink_to('../README.md');self.assertEqual(required(self.plan()),q.FULL)
    def test_executable_mode_change_full(self):
        (self.root/RUNTIME).chmod(0o755);self.assertEqual(required(self.plan()),q.FULL)
    def test_missing_base_full(self):
        p=self.plan({'README.md':'after'},base='1'*40);self.assertEqual(required(p),q.FULL);self.assertIsNone(p['base_sha'])
    def test_divergent_base_full(self):
        write(self.root,'README.md','divergent');other=commit(self.root);q.git(self.root,'checkout','--detach',self.base);p=self.plan({'README.md':'other'},base=other);self.assertEqual(required(p),q.FULL);self.assertIn('Divergent',p['full_reason'])
    def test_manual_full(self):
        p=self.plan(event='workflow_dispatch');self.assertEqual(required(p),q.FULL);self.assertIsNotNone(p['full_reason'])
    def test_overlay_minimums_widen(self):
        p=self.plan({'config/qualification-intents/MOD-03.json':json.dumps({'schema':'hooklab.qualification.intent.v1','overlay':'MOD-03'}),'README.md':'after'});self.assertEqual(required(p),q.FULL);self.assertEqual(p['overlays'],['MOD-03'])
    def test_wrong_intent_refused(self):
        with self.assertRaises(ValueError):self.plan({'config/qualification-intents/MOD-03.json':'{}'})
    def test_dirty_checkout_refused(self):
        write(self.root,'README.md','dirty');
        with self.assertRaises(ValueError):q.plan(self.root,self.base,self.base,'pull_request','1','1')
    def test_actual_head_must_match(self):
        with self.assertRaises(ValueError):q.plan(self.root,'a'*40,self.base,'pull_request','1','1')
    def test_full_does_not_invent_go(self):
        p=self.plan(event='workflow_dispatch');self.assertFalse(p['gates']['go']['required'])
    def test_go_plus_shared_full(self):
        self.assertEqual(required(self.plan({'tools/hooklabctl/go.mod':'module fixture','app/src/main/AndroidManifest.xml':'new'})),q.FULL|{'go'})
    def test_aggregate_exact_results(self):
        p=self.plan({'README.md':'after'});r=q.aggregate(p,copy.deepcopy(p),jobs(p));self.assertEqual(r['gates']['source']['verdict'],'PASS');self.assertEqual(r['gates']['android']['verdict'],'NOT_APPLICABLE');self.assertFalse(r['physical_device_qualified'])
    def test_missing_or_unexpected_required_job(self):
        p=self.plan({'README.md':'after'});j=jobs(p)
        for k,v in [('source','skipped'),('source','failure'),('source','cancelled'),('android','success')]:
            with self.subTest(k=k,v=v),self.assertRaises(ValueError):q.aggregate(p,p,{**j,k:v})
        del j['go'];self.assertRaises(ValueError,q.aggregate,p,p,j)
    def test_stale_attempt_wrong_policy_or_plan(self):
        p=self.plan({'README.md':'after'});
        for k,v in [('run_attempt','2'),('source_sha','a'*40),('policy_sha256','a'*64),('gates',{}),('adaptive_cases',['api36-390'])]:
            with self.subTest(k=k),self.assertRaises(ValueError):q.aggregate(p,{**p,k:v},jobs(p))
    def test_intent_malformed_or_unknown_cannot_reduce(self):
        p=self.plan({'config/qualification-intents/UNKNOWN.json':'{}'});self.assertEqual(required(p),q.FULL)
    def test_invalid_policy_rejected(self):
        policy=copy.deepcopy(POLICY);policy['runtime_only'][RUNTIME]['tests']=['framework']
        with self.assertRaises(ValueError):self.plan({q.POLICY:json.dumps(policy)})
    def test_cli_plan_then_aggregate_then_verify(self):
        p=self.plan({'README.md':'after'});out=self.root/'output';out.mkdir();planfile=out/'plan.json';receipt=out/'receipt.json';outputs=out/'github-output'
        argv=[sys.executable,'-B',str(ROOT/'scripts/qa/qualification.py'),'plan','--root',str(self.root),'--head',p['source_sha'],'--base',self.base,'--event','pull_request','--run-id','456','--attempt','1','--plan',str(planfile)]
        subprocess.run([*argv,'--github-output',str(outputs)],check=True,capture_output=True);self.assertEqual(json.loads(planfile.read_text()),p);self.assertIn('android=false',outputs.read_text())
        for mode in ('aggregate','verify'):
            cmd=argv[:];cmd[3]=mode;subprocess.run([*cmd,'--receipt',str(receipt),'--jobs',json.dumps(jobs(p))],check=True,capture_output=True)
        self.assertEqual(json.loads(receipt.read_text())['overall'],'PASS_TARGETED_HOSTED')

class GateTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
    def test_selected_adaptive_exact_roster(self):
        (self.root/'api36-390').mkdir();
        with patch.object(a,'validate',return_value={'performance_summary':{}}) as check:
            self.assertEqual(a.verify_all(self.root,{},None,['api36-390']),{'api36-390':{}});check.assert_called_once()
            with self.assertRaises(ValueError):a.verify_all(self.root,{},None)
            (self.root/'api36-840').mkdir();self.assertRaises(ValueError,a.verify_all,self.root,{},None,['api36-390'])
    def test_invalid_adaptive_roster(self):
        for selection in ([],['bad'],['api36-390','api36-390']):
            self.assertRaises(ValueError,a.verify_all,self.root,{},None,selection)
    def test_functional_roster_and_failure_evidence(self):
        self.assertEqual(f.selected('framework,termux'),['framework','termux'])
        for bad in ('','other','termux,termux'):self.assertRaises(ValueError,f.selected,bad)
        details=n.failure_details('',n.TERMUX_TEST,1,[],2,'TRANSPORT_FAILED',[n.TERMUX_TEST]);self.assertEqual(details['test_id'],n.TERMUX_TEST)
        with self.assertRaises(ValueError):n.failure_details('',n.TERMUX_TEST,1,[],2,'TRANSPORT_FAILED')
    def test_shared_apk_identity_and_tampering(self):
        ident={'source_sha':'a'*40,'source_tree':'b'*40,'repository':q.REPO,'run_id':'1','run_attempt':'1'};folder=self.root/'apks';folder.mkdir()
        for name in a.APK_FILES.values():
            with zipfile.ZipFile(folder/name,'w') as z:z.writestr('AndroidManifest.xml','fixture');z.writestr('classes.dex','fixture')
        build={**a.apk_identity(ident),'apks':{name:a.apk_file(folder/file) for name,file in a.APK_FILES.items()}};(folder/'apk-build.json').write_text(json.dumps(build))
        self.assertEqual(a.shared_apks(folder,ident),build)
        self.assertRaises(ValueError,a.shared_apks,folder,{**ident,'run_attempt':'2'})
        (folder/'app.apk').write_bytes(b'bad');self.assertRaises(zipfile.BadZipFile,a.shared_apks,folder,ident)
    def test_functional_exact_receipt_and_negative(self):
        ident={'schema':'hooklab.functional.receipt.v1','source_sha':'a'*40,'source_tree':'b'*40,'repository':q.REPO,'run_id':'1','run_attempt':'1'};folder=self.root/'evidence';folder.mkdir()
        build={'apks':{'app':{'sha256':'a'*64},'test':{'sha256':'b'*64}}}
        result={**ident,'status':'PASS_FUNCTIONAL_NOT_VISUAL','completed_tests':[n.TERMUX_TEST],'selected':['termux'],'apk_build':build,'installed_app_sha256':'a'*64,'installed_test_sha256':'b'*64,'files':{}}
        (folder/'receipt.json').write_text(json.dumps(result));
        with patch.object(a,'shared_apks',return_value=build):
            self.assertEqual(f.verify(folder,self.root,['termux'],ident),result)
            self.assertRaises(ValueError,f.verify,folder,self.root,['framework'],ident)
            self.assertRaises(ValueError,f.verify,folder,self.root,['termux'],{**ident,'run_attempt':'2'})
    def test_no_go_gate_without_hosted_context(self):
        with patch.dict(os.environ,{'GITHUB_ACTIONS':'false'}):self.assertRaises(ValueError,go_run,self.root,'a'*40,'1','1',self.root/'out.json')
    def test_coordinator_wiring(self):
        w=(ROOT/'.github/workflows/android.yml').read_text()
        self.assertIn('name: HookLab / targeted qualification',w)
        self.assertIn('needs: [impact, source, android, ci24, terminal, design, visual, functional, adaptive, go]',w)
        self.assertIn('if: always()',w);self.assertIn('toJSON(needs)',w);self.assertIn('qualification.py aggregate',w)
        for gate in q.GATES:self.assertIn(f"needs.impact.outputs.{gate} == 'true'",w)
        for file in ('hooklab-design-contract.yml','hooklab-native-ui.yml','hooklab-ui-extended.yml','hooklab-ci24.yml'):
            text=(ROOT/'.github/workflows'/file).read_text();self.assertIn('workflow_call:',text);self.assertNotIn('  pull_request:',text);self.assertNotIn('  push:',text)
        self.assertIn('shared_apks: true',w);self.assertIn('shared_native_apks.py restore',(ROOT/'.github/workflows/hooklab-native-ui.yml').read_text())

if __name__=='__main__':unittest.main()
