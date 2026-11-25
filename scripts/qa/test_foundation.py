"""Source contract and negative tests; never invokes Gradle or a device."""
import copy, hashlib, json, tempfile, unittest, zipfile
from pathlib import Path
import native_smoke, reference, release_isolation, visual
ROOT=Path(__file__).resolve().parents[2]
P='app/src/'
UI='java/com/mikeyphw/hooklab/ui/'
class FoundationTests(unittest.TestCase):
    def test_release_source_isolation(self):
        release=(ROOT/(P+'release/'+UI+'UiEnvironmentFactory.java')).read_text()
        debug=(ROOT/(P+'debug/'+UI+'UiEnvironmentFactory.java')).read_text()
        main=(ROOT/(P+'main/'+UI+'UiEnvironment.java')).read_text()
        self.assertNotIn('installInstrumentationFixture',release+main)
        self.assertIn('new UiEnvironment(app)',release)
        for term in ('FLAG_DEBUGGABLE','SDK_INT != 36','currentService() != null','installed = null','installInstrumentationFixture'):
            self.assertIn(term,debug)
        for term in ('TweakCatalog.entries()','binder.getRemotePreferences(group)','binder.requestScope(packages','binder.removeScope(packages)','SystemClock.elapsedRealtime()','app.getPackageManager()','lastBinder'):
            self.assertIn(term,main)
        self.assertNotIn('androidTestImplementation',main)
    def test_apk_exclusion_and_negative(self):
        # Explicit synthetic DEX marker fixtures exercise the real verifier, not APK evidence.
        with tempfile.TemporaryDirectory() as tmp:
            def make(n,markers):
                p=Path(tmp)/(n+'.apk')
                with zipfile.ZipFile(p,'w') as z:z.writestr('classes.dex',b'dex\n'+b'\0'*112+b'\0'.join(markers))
                return p
            d=make('debug',[release_isolation.FACTORY,release_isolation.MARK]);r=make('release',[release_isolation.FACTORY]);t=make('test',[release_isolation.TEST])
            proof=release_isolation.check(d,r,t);release_isolation.verify(proof,release_isolation.sha(d),release_isolation.sha(t))
            bad=make('bad-release',[release_isolation.FACTORY,release_isolation.MARK])
            with self.assertRaisesRegex(ValueError,'isolation'):release_isolation.check(d,bad,t)
            proof['release_qualified']=True
            with self.assertRaisesRegex(ValueError,'qualification'):release_isolation.verify(proof,release_isolation.sha(d),release_isolation.sha(t))
    def test_instrumentation_exact_roster(self):
        t=native_smoke.TEST;k,m=t.split('#');record=f'INSTRUMENTATION_STATUS: class={k}\nINSTRUMENTATION_STATUS: test={m}\n'
        ok=record+'INSTRUMENTATION_STATUS_CODE: 1\n'+record+'INSTRUMENTATION_STATUS_CODE: 0\nOK (1 test)\nINSTRUMENTATION_CODE: -1\n'
        native_smoke.parse_instrumentation(ok,t)
        for wrong in (ok.replace('STATUS_CODE: 0','STATUS_CODE: -2'),ok.replace(m,'other'),ok.replace('OK (1 test)','OK (0 tests)'),ok+'FAILURES!!!'):
            with self.assertRaises(ValueError):native_smoke.parse_instrumentation(wrong,t)
    def test_settings_lifecycle_identity_and_payload_scrub(self):
        bridge=(ROOT/'app/src/main/java/com/mikeyphw/hooklab/termux/TermuxBridgeActivity.java').read_text()
        native=(ROOT/'app/src/androidTest/java/com/mikeyphw/hooklab/qa/NativeBaselineTest.java').read_text()
        self.assertIn('setIntent(new Intent(this, TermuxBridgeActivity.class)',bridge)
        self.assertIn('.setAction(TermuxCommandPolicy.ACTION_SETTINGS));',bridge)
        self.assertIn('setIntent(new Intent());',bridge)
        self.assertNotIn('setIntent(inbound)',bridge)
        for term in ('ActivityScenario.launch(settings)','synthetic_unused_payload','assertNull(a.getIntent().getExtras())','assertNull(a.getIntent().getClipData())','assertNull(a.getIntent().getData())','assertEquals(0,a.getIntent().getFlags())','UXR00_STAGE_'):
            self.assertIn(term,native)
    def test_instrumentation_failure_safe_source_bound_receipt(self):
        from unittest.mock import patch
        from subprocess import CompletedProcess, TimeoutExpired
        ident={'source_sha':'a'*40,'source_tree':'b'*40,'run_id':'42','run_attempt':'1','repository':'Mikeyphw/MikeysHookLab','physical_device_qualified':False}
        klass=native_smoke.TEST.split('#')[0]
        output='\n'.join(['INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: UXR00_STAGE_SETTINGS_LAUNCH','Caused by: java.lang.AssertionError: private-token=DO_NOT_EXPORT',f' at {klass}.realActivityOfflineNavigation(NativeBaselineTest.java:146)',' at attacker.secret.Bad.private(Bad.java:42)','INSTRUMENTATION_STATUS_CODE: -2','INSTRUMENTATION_CODE: -1'])
        with tempfile.TemporaryDirectory() as d:
            path=Path(d);completed=[]
            with patch.object(native_smoke.subprocess,'run',return_value=CompletedProcess([],0,output,'secret stderr')):
                with self.assertRaisesRegex(ValueError,'TEST_FAILED'):native_smoke.run_instrumentation(path,ident,native_smoke.TEST,completed)
            record=json.loads((path/'receipt.json').read_text());self.assertEqual(record['status'],'INSTRUMENTATION_FAILED');self.assertEqual(record['source_sha'],ident['source_sha']);self.assertEqual(record['failure']['stage'],'SETTINGS_LAUNCH');self.assertEqual(record['failure']['source_frames'],[{'class':klass,'method':'realActivityOfflineNavigation','file':'NativeBaselineTest.java','line':146}]);self.assertEqual(completed,[])
            self.assertNotIn('DO_NOT_EXPORT',json.dumps(record));self.assertNotIn('attacker.secret',json.dumps(record));self.assertNotIn('secret stderr',json.dumps(record))
            with patch.object(native_smoke.subprocess,'run',side_effect=TimeoutExpired(['adb'],240,output=b'secret')):
                with self.assertRaisesRegex(ValueError,'TIMEOUT'):native_smoke.run_instrumentation(path,ident,native_smoke.TEST,completed)
            self.assertEqual(json.loads((path/'receipt.json').read_text())['failure']['reason'],'TIMEOUT')
            with patch.object(native_smoke.subprocess,'run',side_effect=OSError('secret')):
                with self.assertRaisesRegex(ValueError,'SPAWN_FAILED'):native_smoke.run_instrumentation(path,ident,native_smoke.TEST,completed)
            self.assertEqual(json.loads((path/'receipt.json').read_text())['failure']['reason'],'SPAWN_FAILED')
            with patch.object(native_smoke.subprocess,'run',return_value=CompletedProcess([],7,output,'')):
                with self.assertRaisesRegex(ValueError,'TRANSPORT_FAILED'):native_smoke.run_instrumentation(path,ident,native_smoke.TEST,completed)
            self.assertEqual(json.loads((path/'receipt.json').read_text())['failure']['child_exit_code'],7)
            record=f'INSTRUMENTATION_STATUS: class={klass}\nINSTRUMENTATION_STATUS: test=realActivityOfflineNavigation\n'
            ok=record+'INSTRUMENTATION_STATUS_CODE: 1\n'+record+'INSTRUMENTATION_STATUS_CODE: 0\nOK (1 test)\nINSTRUMENTATION_CODE: -1\n'
            with patch.object(native_smoke.subprocess,'run',return_value=CompletedProcess([],0,ok,'')):
                native_smoke.run_instrumentation(path,ident,native_smoke.TEST,completed)
                native_smoke.run_instrumentation(path,ident,native_smoke.TEST,completed)
            self.assertEqual(completed,[native_smoke.TEST,native_smoke.TEST]);self.assertEqual(json.loads((path/'receipt.json').read_text())['status'],'IN_PROGRESS')
            with self.assertRaisesRegex(ValueError,'roster drift'):native_smoke.failure_details(output,native_smoke.TEST,3,completed,0,'TEST_FAILED')
            wrong=output.replace('SETTINGS_LAUNCH','PRIVATE_VALUE')
            self.assertEqual(native_smoke.failure_details(wrong,native_smoke.TEST,1,[],0,'TEST_FAILED')['stage'],'UNKNOWN')
    def test_instrumentation_sdk_filter(self):
        source=(ROOT/'app/src/androidTest/java/com/mikeyphw/hooklab/qa/NativeBaselineTest.java').read_text()
        self.assertIn('@androidx.test.filters.SdkSuppress(minSdkVersion = 36, maxSdkVersion = 36)',source)
        self.assertNotIn('@androidx.annotation.RequiresApi',source)
    def test_install_acknowledgement(self):
        from unittest.mock import patch
        from subprocess import CompletedProcess
        for output in ('Success','Performing Streamed Install\nSuccess','Performing Push Install\nSuccess'):
            with patch.object(native_smoke.subprocess,'run',return_value=CompletedProcess([],0,output,'')) as call:
                native_smoke.install_apk(Path('fixture.apk'))
                self.assertIn('--no-incremental',call.call_args.args[0])
        for rc,output,stderr in ((0,'Failure [INSTALL_FAILED]',''),(0,'Success\nFailure',''),(0,'unexpected\nSuccess',''),(0,'',''),(1,'Success',''),(0,'Success','unverified transport warning'),(0,'Performing Incremental Install\nSuccess',''),(0,'Performing Push Install\nPerforming Push Install\nSuccess','')):
            with self.subTest(rc=rc,output=output),patch.object(native_smoke.subprocess,'run',return_value=CompletedProcess([],rc,output,stderr)):
                with self.assertRaises(ValueError):native_smoke.install_apk(Path('fixture.apk'))
    def test_installed_apk_identity(self):
        from unittest.mock import patch
        from subprocess import CompletedProcess
        pkg=native_smoke.PKG; path='/data/app/~~abc==/'+pkg+'-a_b==/base.apk'; h='a'*64
        def response(rc=0,stdout='',stderr=''):return CompletedProcess([],rc,stdout,stderr)
        p=response(stdout='package:'+path+'\n'); good=response(stdout=h+'  '+path+'\n')
        with patch.object(native_smoke.subprocess,'run',side_effect=[p,good,p]) as call:
            self.assertEqual(native_smoke.installed_digest(pkg,h),h)
            self.assertEqual(call.call_args_list[1].args[0][-2:],['sha256sum',path])
        with patch.object(native_smoke.subprocess,'run',side_effect=[p,response(1,stderr='Permission denied'),good,p]) as call:
            self.assertEqual(native_smoke.installed_digest(pkg,h),h)
            self.assertEqual(call.call_args_list[2].args[0][-4:],['run-as',pkg,'sha256sum',path])
        for results in ([p,response(stdout='cat: Permission denied')], [p,response(1,stderr='transport offline')], [p,response(1,stderr='Permission denied'),response(1,stderr='run-as failed')], [p,response(stdout='b'*64+'  '+path),p], [p,good,response(stdout='package:'+path.replace('~~abc','~~xyz'))], [response(stdout='package:'+path+'\npackage:'+path.replace('base.apk','split.apk'))], [p,response(stdout=h+'  /wrong.apk')], [response(stdout='package:'+path.replace(pkg,'com.other.app'))], [response(stdout='package:'+path.replace('/base.apk','/../base.apk'))]):
            with self.subTest(results=results),patch.object(native_smoke.subprocess,'run',side_effect=results) as call:
                with self.assertRaises(ValueError):native_smoke.installed_digest(pkg,h)
                self.assertEqual(call.call_count,len(results))
    def test_sdk_package_metadata_without_emulator_process(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as tmp:
            sdk=Path(tmp)
            for key,package in native_smoke.SDK_PACKAGES.items():
                folder=sdk/package;folder.mkdir(parents=True)
                (folder/'source.properties').write_text('Pkg.Desc=SDK fixture\nPkg.Revision=37.3.10\nPkg.Path='+package.replace('/',';')+'\n')
            with patch.object(native_smoke.subprocess,'check_output',side_effect=AssertionError('emulator process forbidden')),patch.object(native_smoke,'txt',side_effect=lambda *a:{'ro.build.version.sdk':'36','ro.product.cpu.abi':'x86_64','ro.kernel.qemu':'1','ro.build.fingerprint':'fixture/fingerprint'}[a[-1]]),patch.dict(native_smoke.os.environ,{'ANDROID_HOME':str(sdk)}):
                proof=native_smoke.environment()
            self.assertEqual(proof['emulator_version'],'sdk-package:37.3.10')
            native_smoke.validate_environment(proof)
            for value in ('synthetic','Android emulator version 37.3.10','sdk-package:','sdk-package:...','sdk-package:37.3.10 unknown'):
                with self.subTest(value=value),self.assertRaises(ValueError):native_smoke.validate_environment(dict(proof,emulator_version=value))
            file=sdk/'emulator/source.properties'
            for content in ('','Pkg.Revision=...\n','Pkg.Revision=37.3.10\nPkg.Revision=37.3.10\n','Pkg.Revision=37.3.10\nPkg.Path=platform-tools\n','Pkg.Revision=garbage\n','Pkg.Revision=37.3.10\nPkg.Path=emulator\nPkg.Path=emulator\n'):
                file.write_text(content)
                with self.subTest(content=content),self.assertRaises(ValueError):native_smoke.sdk_environment(sdk)
            file.unlink()
            with self.assertRaises(ValueError):native_smoke.sdk_environment(sdk)
            file.symlink_to(sdk/'platform-tools/source.properties')
            with self.assertRaises(ValueError):native_smoke.sdk_environment(sdk)
    def test_sdk_revision_accepts_standard_sdk_revisions(self):
        with tempfile.TemporaryDirectory() as tmp:
            sdk=Path(tmp);folder=sdk/'emulator';folder.mkdir();file=folder/'source.properties'
            for rev in ('1','36.0.0','37.3.10','37.3.10.0','37.3.10-rc1','37.3.10 rc 1'):
                file.write_text('Pkg.Revision='+rev+'\n')
                self.assertEqual(native_smoke.sdk_revision(sdk,'emulator'),rev)
    def test_browser_geometry_negative(self):
        r=reference.browser_roster(ROOT);c=r['VIS-001'];g={'viewport':[390,844],'items':[dict(i,index=n,bounds=[0,0,10,10],visible=True) for n,i in enumerate(c['controls'])],'route':c['route'],'theme':c['theme'],'dialog':c['dialog'],'tokens':c['tokens']}
        reference.verify_browser_case(r,'VIS-001',g)
        for field,value in [('bounds',[0,0,-1,10]),('visible',False),('index',99)]:
            bad=copy.deepcopy(g);bad['items'][0][field]=value
            with self.assertRaises(ValueError):reference.verify_browser_case(r,'VIS-001',bad)
    def test_fixture_roster_and_deferred_cases(self):
        visual.validate_contract(ROOT);f=json.loads((ROOT/'design/hooklab/02_CONTRACT/fixtures.json').read_text());self.assertEqual(len(f['fixtures']),15);self.assertEqual(len(f['cases']),24);self.assertTrue(all(not x['native_equivalence'] for x in f['cases']))
if __name__=='__main__':unittest.main()
