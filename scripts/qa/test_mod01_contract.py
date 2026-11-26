"""Compile actual SDK/runtime and isolated owned payload, never Android Gradle."""
import pathlib, subprocess, tempfile, unittest
ROOT=pathlib.Path(__file__).resolve().parents[2]
class ModularContractTest(unittest.TestCase):
    def test_java_sdk_registry_and_shared_vectors(self):
        with tempfile.TemporaryDirectory() as work:
            work=pathlib.Path(work); classes=work/'classes'; payload=work/'payload'; classes.mkdir();payload.mkdir()
            sources=sorted(ROOT.glob('sdk/src/main/java/**/*.java'))+sorted(ROOT.glob('runtime/src/main/java/**/*.java'))
            sources += [ROOT/'app/src/main/java/com/mikeyphw/hooklab/core'/name for name in ('TweakSpec.java','TweakEngine.java','TweakDiagnostics.java','TweakKeys.java','HostFlagSnapshot.java')]
            sources += [ROOT/'app/src/debug/java/com/mikeyphw/hooklab/probe/ProbeProtocol.java',ROOT/'scripts/qa/Mod01Checks.java']
            subprocess.run(['javac','--release','17','-Xlint:all','-Werror','-d',str(classes),*map(str,sources)],check=True,timeout=90)
            subprocess.run(['javac','--release','17','-Xlint:all','-Werror','-cp',str(classes),'-d',str(payload),*map(str,ROOT.glob('modules/transport-probe/src/main/java/**/*.java'))],check=True,timeout=90)
            resource=ROOT/'runtime/src/main/resources'; registry=resource/'META-INF/hooklab/builtin-registry.json'
            result=subprocess.run(['java','-cp',f'{classes}:{resource}','Mod01Checks',str(registry),str(ROOT/'contracts/vectors'),str(payload)],check=True,capture_output=True,text=True,timeout=90)
            print(result.stdout);self.assertIn('MOD01_CHECKS_PASS ',result.stdout)
    def test_release_probe_boundary(self):
        release=(ROOT/'app/src/release/java/com/mikeyphw/hooklab/hook/ProbeBridge.java').read_text()
        self.assertIn('return false;',release);self.assertNotIn('openRemoteFile',release);self.assertNotIn('InMemoryDexClassLoader',release)
        self.assertNotIn('probetarget',(ROOT/'app/src/main/resources/META-INF/xposed/scope.list').read_text())
        self.assertFalse((ROOT/'app/src/main/java/com/mikeyphw/hooklab/probe').exists())

"""Exercise the real verifier CLI with synthetic DEX tables, not Android evidence."""
import hashlib,json,os,pathlib,struct,subprocess,sys,tempfile,unittest,zipfile
from mod01_probe_apk import ASSET,ENTRY,SDK,ACTIVITY,TARGET,dex_classes
ROOT=pathlib.Path(__file__).resolve().parents[2]
def dex(names,refs=()):
    strings=list(names)+list(refs);sc=len(strings);cc=len(names);so=112;to=so+4*sc;co=to+4*sc;data=bytearray(co+32*cc)
    data[:8]=b'dex\n035\0'
    for pos,value in [(36,112),(40,0x12345678),(56,sc),(60,so),(64,sc),(68,to),(96,cc),(100,co)]:struct.pack_into('<I',data,pos,value)
    for i,s in enumerate(strings):
        struct.pack_into('<I',data,so+4*i,len(data));struct.pack_into('<I',data,to+4*i,i);data.extend(bytes([len(s)])+s.encode()+b'\0')
        if i<cc:struct.pack_into('<I',data,co+32*i,i)
    struct.pack_into('<I',data,32,len(data));return bytes(data)
class ProbeApkTest(unittest.TestCase):
    def setUp(self):
        self.t=tempfile.TemporaryDirectory();self.addCleanup(self.t.cleanup);self.p=pathlib.Path(self.t.name)
        self.d=self.p/'debug.apk';self.r=self.p/'release.apk';self.target=self.p/'target.apk';self.receipt=self.p/'receipt.json'
        self.payload=dex([ENTRY],refs=[SDK]);self.apk(self.d,[SDK,ACTIVITY],self.payload,refs=[ENTRY]);self.apk(self.r,[SDK]);self.apk(self.target,[TARGET]);self.env={**os.environ,'GITHUB_REPOSITORY':'Mikeyphw/MikeysHookLab','SOURCE_SHA':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'GITHUB_RUN_ID':'1','GITHUB_RUN_ATTEMPT':'1'}
    def apk(self,path,classes,payload=None,refs=()):
        with zipfile.ZipFile(path,'w') as z:
            z.writestr('classes.dex',dex(classes,refs))
            if payload is not None:z.writestr(ASSET,payload)
    def cli(self,mode,ok=True):
        r=subprocess.run([sys.executable,'-B',str(ROOT/'scripts/qa/mod01_probe_apk.py'),mode,'--root',str(ROOT),'--debug',str(self.d),'--release',str(self.r),'--target',str(self.target),'--receipt',str(self.receipt)],env=self.env,capture_output=True,text=True,timeout=10)
        self.assertEqual(r.returncode,0 if ok else 2,r.stdout+r.stderr)
    def test_exact_tables_not_string_presence(self):
        self.assertEqual(dex_classes(self.payload),{ENTRY});self.cli('publish');self.cli('verify');r=json.loads(self.receipt.read_text());self.assertFalse(r['device_qualified']);self.assertFalse(r['release_qualified'])
    def test_release_leak_rejected(self):
        self.apk(self.r,[SDK,ACTIVITY]);self.cli('publish',False);self.apk(self.r,[SDK],self.payload);self.cli('publish',False)
    def test_duplicate_sdk_or_executable_rejected(self):
        self.apk(self.d,[SDK,ACTIVITY],dex([ENTRY,SDK]));self.cli('publish',False);self.apk(self.d,[SDK,ACTIVITY,ENTRY],self.payload);self.cli('publish',False)
    def test_receipt_tamper_and_attempt_refused(self):
        self.cli('publish');self.env['GITHUB_RUN_ATTEMPT']='2';self.cli('verify',False);self.env['GITHUB_RUN_ATTEMPT']='1';r=json.loads(self.receipt.read_text());r['device_qualified']=True;self.receipt.write_text(json.dumps(r));self.cli('verify',False)
    def test_malformed_dex_refused(self):
        b=bytearray(self.payload);struct.pack_into('<I',b,100,0xFFFFFFFF)
        self.apk(self.d,[SDK,ACTIVITY],b);self.cli('publish',False)

if __name__=="__main__":unittest.main()
