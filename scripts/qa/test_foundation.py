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
    def test_browser_geometry_negative(self):
        r=reference.browser_roster(ROOT);c=r['VIS-001'];g={'viewport':[390,844],'items':[dict(i,index=n,bounds=[0,0,10,10],visible=True) for n,i in enumerate(c['controls'])],'route':c['route'],'theme':c['theme'],'dialog':c['dialog'],'tokens':c['tokens']}
        reference.verify_browser_case(r,'VIS-001',g)
        for field,value in [('bounds',[0,0,-1,10]),('visible',False),('index',99)]:
            bad=copy.deepcopy(g);bad['items'][0][field]=value
            with self.assertRaises(ValueError):reference.verify_browser_case(r,'VIS-001',bad)
    def test_fixture_roster_and_deferred_cases(self):
        visual.validate_contract(ROOT);f=json.loads((ROOT/'design/hooklab/02_CONTRACT/fixtures.json').read_text());self.assertEqual(len(f['fixtures']),15);self.assertEqual(len(f['cases']),24);self.assertTrue(all(not x['native_equivalence'] for x in f['cases']))
if __name__=='__main__':unittest.main()
