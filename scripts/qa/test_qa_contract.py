"""Host-only negative and positive regression tests; never call Gradle or ADB."""
import copy, hashlib, json, os, subprocess, tempfile, unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from unittest.mock import patch
import ci24, reference

ROOT=Path(__file__).resolve().parents[2]
class ReferenceTest(unittest.TestCase):
    def test_exact_roster(self):
        _,cases=reference.verify(ROOT)
        self.assertEqual(len(cases),24);self.assertEqual(cases[21]['svg_label'],'22 · AMOLED / Empty timeline')
    def test_hash_tamper(self):
        with tempfile.TemporaryDirectory() as t:
            dest=Path(t)/'design/hooklab/01_REFERENCE';dest.mkdir(parents=True)
            base=ROOT/'design/hooklab/01_REFERENCE'
            for p in base.iterdir(): (dest/p.name).write_bytes(p.read_bytes())
            p=dest/'hooklab_ux_revamp.html';p.write_bytes(p.read_bytes()+b'!')
            with self.assertRaisesRegex(ValueError,'reference drift'):reference.verify(Path(t))
    def test_mask_and_approval_refused(self):
        with tempfile.TemporaryDirectory() as t:
            dest=Path(t)/'design/hooklab/01_REFERENCE';dest.mkdir(parents=True)
            for p in (ROOT/'design/hooklab/01_REFERENCE').iterdir(): (dest/p.name).write_bytes(p.read_bytes())
            m=dest/'reference-manifest.json';o=json.loads(m.read_text());o['cases'][0]['native_approved']=True;m.write_text(json.dumps(o))
            with self.assertRaisesRegex(ValueError,'unapproved'):reference.verify(Path(t))
class Ci24Test(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)/'repo';self.root.mkdir();self.art=Path(self.tmp.name)/'artifacts';self.art.mkdir()
        subprocess.run(['git','init','-q',str(self.root)],check=True)
        for i in range(28):
            p=self.root/f'app/src/test/java/example/T{i:02}Test.java';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(f'package example; public class T{i:02}Test {{ @Test public void a() {{}} @Test(expected=IllegalArgumentException.class) public void b() {{}} }}')
        subprocess.run(['git','-C',str(self.root),'add','.'],check=True);subprocess.run(['git','-C',str(self.root),'-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','-qm','test fixtures'],check=True)
        self.source=ci24.git(self.root,'rev-parse','HEAD');self.inv=ci24.inventory(self.root);self.ident=ci24.identity(self.root,self.source,'123','1')
    def seed(self):
        for i in range(1,25):
            folder=self.art/f'hooklab-ci24-123-1-shard-{i}';j=folder/'junit';j.mkdir(parents=True);expected=ci24.assignment(self.inv,i)
            for name,v in expected.items():
                e=ET.Element('testsuite',{'tests':str(len(v['methods'])),'failures':'0','errors':'0','skipped':'0'})
                for m in v['methods']:ET.SubElement(e,'testcase',{'classname':name,'name':m})
                ET.ElementTree(e).write(j/('TEST-'+name+'.xml'))
            hashes,cases=ci24.parse_xml(j,expected)
            ci24.dump(folder/'receipt.json',{'schema':ci24.SCHEMA,**self.ident,'shard':i,'shards':24,'classes':expected,'status':'PASS','gradle_exit':0,'release_qualified':False,'physical_device_qualified':False,'junit_sha256':hashes,'tests':cases,'test_count':len(cases)})
    def test_assignment_partition(self):
        rows=[ci24.assignment(self.inv,i) for i in range(1,25)];self.assertTrue(all(rows));self.assertEqual(sum(map(len,rows)),28);self.assertEqual(set().union(*map(set,rows)),set(self.inv))
    def test_complete_summary_and_verify(self):
        self.seed();out=Path(self.tmp.name)/'summary.json';self.assertEqual(ci24.summarize(self.root,self.art,out,self.source,'123','1'),0);self.assertEqual(json.loads(out.read_text())['test_count'],56);self.assertEqual(ci24.summarize(self.root,self.art,out,self.source,'123','1',True),0)
    def test_missing_shard_refused(self):
        self.seed();(self.art/'hooklab-ci24-123-1-shard-24').rename(self.art/'lost')
        with self.assertRaisesRegex(ValueError,'missing/extra'):ci24.summarize(self.root,self.art,Path(self.tmp.name)/'s.json',self.source,'123','1')
    def test_wrong_attempt_refused(self):
        self.seed();p=self.art/'hooklab-ci24-123-1-shard-1'/'receipt.json';r=json.loads(p.read_text());r['run_attempt']='2';p.write_text(json.dumps(r))
        with self.assertRaisesRegex(ValueError,'invalid shard'):ci24.summarize(self.root,self.art,Path(self.tmp.name)/'s.json',self.source,'123','1')
    def test_skipped_or_missing_test_refused(self):
        self.seed();f=self.art/'hooklab-ci24-123-1-shard-1'/'junit';p=next(f.glob('*.xml'));e=ET.parse(p);e.getroot().set('skipped','1');e.write(p)
        with self.assertRaisesRegex(ValueError,'failed/skipped'):ci24.summarize(self.root,self.art,Path(self.tmp.name)/'s.json',self.source,'123','1')
    def test_no_local_gradle(self):
        with patch.dict(os.environ,{'GITHUB_ACTIONS':'false'},clear=True):
            with self.assertRaisesRegex(ValueError,'HOSTED_ONLY'):ci24.run(self.root,Path(self.tmp.name)/'out',1,self.source,'123','1')
    def test_failed_summary_preserves_verified_shards(self):
        self.seed();(self.art/'hooklab-ci24-123-1-shard-24').rename(self.art/'lost')
        out=Path(self.tmp.name)/'summary.json';ci24.failed_summary(self.root,self.art,out,self.source,'123','1','failure')
        data=json.loads(out.read_text());self.assertEqual(data['overall'],'FAIL_CI24_VERIFICATION');self.assertEqual(len(data['shards']),24);self.assertEqual(data['shards'][-1]['status'],'MISSING');self.assertEqual(data['shards'][0]['status'],'VERIFIED_PASS')
    def test_ci24_retry_adapter_and_timeout(self):
        out=Path(self.tmp.name)/'out';original=subprocess.run
        def execute(argv,*args,**kwargs):
            if argv[0]=='git':return original(argv,*args,**kwargs)
            self.assertEqual(argv[:3],['bash','scripts/ci_gradle_retry.sh','bash']);self.assertEqual(kwargs['env']['HOOKLAB_CI_RETRY_PROFILE'],'native');raise subprocess.TimeoutExpired(argv,3000)
        with patch.dict(os.environ,{'GITHUB_ACTIONS':'true','RUNNER_OS':'Linux','SOURCE_SHA':self.source,'GITHUB_RUN_ID':'123','GITHUB_RUN_ATTEMPT':'1'}),patch.object(ci24.subprocess,'run',side_effect=execute):
            self.assertEqual(ci24.run(self.root,out,1,self.source,'123','1'),124)
        self.assertEqual(json.loads((out/'receipt.json').read_text())['status'],'FAIL_TIMEOUT')
    def test_source_drift(self):
        with self.assertRaisesRegex(ValueError,'source drift'):ci24.identity(self.root,'1'*40,'123','1')
if __name__=='__main__':unittest.main()
