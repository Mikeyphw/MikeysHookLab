"""Cross-language exact signed archive and adversarial corpus, no Android SDK."""
import hashlib, json, os, pathlib, platform, struct, subprocess, sys, tempfile, unittest, zipfile, zlib
ROOT=pathlib.Path(__file__).resolve().parents[2]

def fixture_dex(name=b'Lcom/mikeyphw/hooklab/modules/probe/ProbeEntrypoint;', *extra):
    names=(name,)+extra;n=len(names);so,to,co=112,112+4*n,112+8*n
    b=bytearray(112+40*n);b[:8]=b'dex\n039\0'
    for p,v in [(36,112),(40,0x12345678),(56,n),(60,so),(64,n),(68,to),(96,n),(100,co)]:struct.pack_into('<I',b,p,v)
    for i,descriptor in enumerate(names):
        assert 0<len(descriptor)<128
        struct.pack_into('<I',b,so+4*i,len(b));struct.pack_into('<I',b,to+4*i,i);struct.pack_into('<I',b,co+32*i,i)
        b.extend(bytes([len(descriptor)])+descriptor+b'\0')
    struct.pack_into('<I',b,32,len(b));b[12:32]=hashlib.sha1(b[32:]).digest();struct.pack_into('<I',b,8,zlib.adler32(b[12:])&0xffffffff)
    return bytes(b)

# Run an actual process with Linux hard-link syscalls denied, reproducing the
# Termux publication restriction without replacing a Go function or changing
# filesystem permissions. Android itself exercises the normal CLI below.
DENY_LINKS = r"""
import ctypes, errno, os, sys
class Filter(ctypes.Structure):
    _fields_ = [('code', ctypes.c_ushort), ('jt', ctypes.c_ubyte), ('jf', ctypes.c_ubyte), ('k', ctypes.c_uint32)]
class Program(ctypes.Structure):
    _fields_ = [('len', ctypes.c_ushort), ('filter', ctypes.POINTER(Filter))]
libc = ctypes.CDLL(None, use_errno=True)
# x86_64 Linux: link=86, linkat=265. All other syscalls are unchanged.
filters = (Filter*5)(Filter(0x20,0,0,0), Filter(0x15,2,0,86), Filter(0x15,1,0,265), Filter(0x06,0,0,0x7fff0000), Filter(0x06,0,0,0x00050000|errno.EACCES))
program = Program(len(filters), filters)
if libc.prctl(38,1,0,0,0) != 0 or libc.prctl(22,2,ctypes.byref(program),0,0) != 0:
    raise SystemExit('HARDLINK_RESTRICTION_SETUP_REFUSED')
os.execv(sys.argv[1], sys.argv[1:])
"""

class PackInterop(unittest.TestCase):
    def test_actual_go_and_java_production_paths(self):
        with tempfile.TemporaryDirectory() as t:
            p=pathlib.Path(t);classes=p/'classes';classes.mkdir();key=p/'test-key.pem';private=p/'test-key.der';public=p/'test-public.der';cli=p/'hooklabctl'
            def run(*args,ok=True,cwd=ROOT):
                r=subprocess.run(list(map(str,args)),cwd=cwd,capture_output=True,text=True,timeout=120)
                self.assertEqual(r.returncode==0,ok,r.stdout+r.stderr);return r.stdout
            run('openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:3072','-out',key)
            run('openssl','pkcs8','-topk8','-nocrypt','-in',key,'-outform','DER','-out',private);private.chmod(0o600)
            run('openssl','pkey','-in',key,'-pubout','-outform','DER','-out',public)
            run('go','build','-o',cli,'./cmd/hooklabctl',cwd=ROOT/'tools/hooklabctl')
            sources=sorted(ROOT.glob('sdk/src/main/java/**/*.java'))+sorted(ROOT.glob('runtime/src/main/java/**/*.java'))+[ROOT/'scripts/qa/Mod02PackChecks.java',ROOT/'scripts/qa/Mod02RegistryChecks.java',ROOT/'scripts/qa/Mod02HookTransactionChecks.java']
            run('javac','--release','17','-Xlint:all','-Werror','-d',classes,*sources)
            manifest=p/'manifest.json';manifest.write_text(json.dumps({'schema':'hooklab.module.manifest.v1','module_id':'probe','version':'1.0.0','sdk_min':1,'sdk_max':1,'api':102,'entrypoint':'com.mikeyphw.hooklab.modules.probe.ProbeEntrypoint','hooks':[{'id':'hooklab.probe.marker','package':'com.mikeyphw.hooklab.probetarget','min_version':1,'max_version':1}]}))
            provenance=p/'provenance.json';provenance.write_text(json.dumps({'schema':'hooklab.pack.provenance.v1','source_sha':'a'*40,'source_tree':'b'*40,'run_id':1,'run_attempt':1,'settings_schema':1,'channel':'test'}));dex=p/'classes.dex';dex.write_bytes(fixture_dex(b'Lcom/mikeyphw/hooklab/modules/probe/ProbeEntrypoint;',b'Lcom/mikeyphw/hooklab/modules/probe/Callback-IA;',b'Lcom/mikeyphw/hooklab/modules/probe/Callback-CC;'))
            a=p/'a.hookpack';b=p/'b.hookpack'
            for dst in (a,b):run(cli,'module','pack',manifest,provenance,dex,private,dst)
            self.assertEqual(a.read_bytes(),b.read_bytes());self.assertIn('HOOKPACK_SIGNATURE_VERIFIED_NOT_ACTIVATED',run(cli,'module','verify',a,public));self.assertIn('JAVA_PACK_VERIFIED probe 1.0.0',run('java','-cp',classes,'Mod02PackChecks',a,public))
            self.assertEqual(json.loads(run(cli,'module','inspect',a,public))['archive_sha256'],hashlib.sha256(a.read_bytes()).hexdigest())
            before=a.read_bytes()
            result=subprocess.run(list(map(str,[cli,'module','pack',manifest,provenance,dex,private,a])),cwd=ROOT,capture_output=True,text=True,timeout=120)
            self.assertEqual(result.returncode,2);self.assertIn('stage=publish category=already_exists',result.stderr);self.assertEqual(a.read_bytes(),before);self.assertNotIn(str(p),result.stderr)
            if sys.platform=='linux' and platform.machine()=='x86_64' and not hasattr(sys,'getandroidapilevel'):
                probe=subprocess.run([sys.executable,'-B','-c',DENY_LINKS,sys.executable,'-B','-c','import os,sys; os.link(sys.argv[1],sys.argv[2])',str(a),str(p/'forbidden-link')],capture_output=True,text=True,timeout=30)
                self.assertNotEqual(probe.returncode,0);self.assertIn('PermissionError',probe.stderr)
                restricted=p/'restricted.hookpack'
                run(sys.executable,'-B','-c',DENY_LINKS,cli,'module','pack',manifest,provenance,dex,private,restricted)
                self.assertEqual(restricted.read_bytes(),before)
                self.assertIn('HOOKPACK_SIGNATURE_VERIFIED_NOT_ACTIVATED',run(cli,'module','verify',restricted,public))
                print('MOD02_HARDLINK_DENIAL_REGRESSION_PASS actual restricted process')
            self.assertFalse(list(p.glob('.hookpack-*')))
            with zipfile.ZipFile(a) as z:
                self.assertIsNone(z.testzip());self.assertEqual(len(z.infolist()),5)
                raw=a.read_bytes();mutations=[]
                for entry in z.infolist():
                    m=bytearray(raw);data=entry.header_offset+30+len(entry.filename);m[data+entry.file_size//2]^=1;mutations.append(m)
                # ZIP mode/symlink, method, local extra, truncation and trailing data.
                for off in (6,8,28,raw.index(b'PK\x01\x02')+38):
                    m=bytearray(raw);m[off]^=1;mutations.append(m)
                mutations.extend((raw[:-1],raw+b'\0'))
            bad=p/'bad.hookpack'
            for m in mutations:
                bad.write_bytes(m);run(cli,'module','verify',bad,public,ok=False);run('java','-cp',classes,'Mod02PackChecks',bad,public,ok=False)
            print('MOD02_PACK_INTEROP_PASS deterministic signed ZIP, actual Java/Go and negative fixtures')
            from mod02_hosted_packs import verify_artifacts, TEST, sha
            from module_sources import ROSTER, manifest as module_manifest
            from qualification import canonical
            ident={'source_sha':'c'*40,'source_tree':'d'*40,'run_id':123,'run_attempt':2}
            signed=p/'signed';personal=p/'personal';signed.mkdir();personal.mkdir();(signed/'publisher-test.der').write_bytes(public.read_bytes())
            for module in ROSTER:
                mp=personal/module;mp.mkdir();md=module_manifest(module)
                (mp/'manifest.json').write_bytes(canonical(md))
                (mp/'classes.dex').write_bytes(fixture_dex(('L'+md['entrypoint'].replace('.','/')+';').encode()))
                prov={'schema':'hooklab.pack.provenance.v1',**ident,'settings_schema':1,'channel':'test'}
                provfile=p/'fixture-provenance.json';provfile.write_bytes(canonical(prov))
                (mp/'provenance.json').write_bytes(canonical({**prov,'channel':'personal'}))
                run(cli,'module','pack',mp/'manifest.json',provfile,mp/'classes.dex',private,signed/(module+'.hookpack'))
            (signed/'receipt.json').write_bytes(canonical({'schema':'hooklab.mod02.hosted.v1',**ident,'status':'SIGNED_TEST_PACKS_VERIFIED_NOT_ACTIVATED','files':{f.name:sha(f) for f in signed.iterdir()}}))
            (personal/'receipt.json').write_bytes(canonical({'schema':'hooklab.mod02.personal-inputs.v1',**ident,'status':'UNSIGNED_NOT_INSTALLABLE','files':{f.relative_to(personal).as_posix():sha(f) for f in personal.rglob('*') if f.is_file()}}))
            evidence=p/'art-fixture.json';evidence.write_bytes(canonical({'schema':'hooklab.mod02.art.v1',**ident,'status':'ART_LOAD_PASS_NOT_VECTOR_ACCEPTANCE','test':TEST,'pack_receipt_sha256':sha(signed/'receipt.json')}))
            verify_artifacts(signed,personal,evidence,ident,cli)
            with self.assertRaises(ValueError):verify_artifacts(signed,personal,evidence,{**ident,'run_attempt':3},cli)
            dexfile=personal/'wordmaster/classes.dex';dexfile.write_bytes(dexfile.read_bytes()+b'!')
            # Even a recomputed unsigned receipt cannot authorize different DEX bytes.
            receipt=json.loads((personal/'receipt.json').read_text());receipt['files']['wordmaster/classes.dex']=sha(dexfile);(personal/'receipt.json').write_bytes(canonical(receipt))
            with self.assertRaisesRegex(ValueError,'personal DEX differs'):verify_artifacts(signed,personal,evidence,ident,cli)
            print('MOD02_ARTIFACT_VERIFIER_FIXTURE_PASS not real ART evidence')


            # Real deterministic signed migrated payloads exercise production registry.
            registry=ROOT/'runtime/src/main/resources/META-INF/hooklab/builtin-registry.json'
            rows=json.loads(registry.read_text())['hooks']
            entry='com.mikeyphw.hooklab.modules.wordmaster.Entrypoint'
            d={'schema':'hooklab.module.manifest.v1','module_id':'wordmaster','version':'1.0.0','sdk_min':1,'sdk_max':1,'api':102,'entrypoint':entry,'hooks':[{k:r[k] for k in ('id','package','min_version','max_version')} for r in rows if r['module']=='wordmaster']}
            dex.write_bytes(fixture_dex(('L'+entry.replace('.','/')+';').encode()))
            wa=p/'word-a.hookpack';wb=p/'word-b.hookpack';wc=p/'wrong-key.hookpack'
            manifest.write_text(json.dumps(d));run(cli,'module','pack',manifest,provenance,dex,private,wa)
            d['version']='1.1.0';manifest.write_text(json.dumps(d));run(cli,'module','pack',manifest,provenance,dex,private,wb)
            other=p/'other.pem';otherpriv=p/'other.der';otherpub=p/'other-public.der'
            run('openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:3072','-out',other)
            run('openssl','pkcs8','-topk8','-nocrypt','-in',other,'-outform','DER','-out',otherpriv);otherpriv.chmod(0o600)
            run('openssl','pkey','-in',other,'-pubout','-outform','DER','-out',otherpub)
            run(cli,'module','pack',manifest,provenance,dex,otherpriv,wc)
            # Build real signed successive versions to exercise the bounded store.
            versions=[]
            for minor in range(2,18):
                d['version']=f'1.{minor}.0';manifest.write_text(json.dumps(d));version=p/f'word-{minor}.hookpack'
                run(cli,'module','pack',manifest,provenance,dex,private,version);versions.append(version)
            output=run('java','-cp',f'{classes}:{ROOT/"runtime/src/main/resources"}','Mod02RegistryChecks',p/'store',public,otherpub,wa,wb,wc,*versions)
            self.assertIn('MOD02_REGISTRY_CHECKS_PASS',output);print(output.strip())

            output=run('java','-cp',f'{classes}:{ROOT/"runtime/src/main/resources"}','Mod02HookTransactionChecks')
            self.assertIn('MOD02_HOOK_TRANSACTION_CHECKS_PASS',output);print(output.strip())
    def test_reviewed_source_projection_is_deterministic(self):
        from module_sources import project, manifest, ROSTER
        roster=set()
        for module in ROSTER:
            a=project(module);self.assertEqual(a,project(module));self.assertIn('Entrypoint.java',a)
            self.assertTrue(all('io.github.libxposed' not in v for v in a.values()))
            self.assertEqual({r['id'] for r in manifest(module)['hooks']},set(ROSTER[module]['install']))
            roster.update(ROSTER[module]['install'])
        self.assertEqual(len(roster),5);self.assertFalse(any('mcc' in x for x in roster))
