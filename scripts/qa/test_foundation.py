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
    def test_browser_geometry_negative(self):
        r=reference.browser_roster(ROOT);c=r['VIS-001'];g={'viewport':[390,844],'items':[dict(i,index=n,bounds=[0,0,10,10],visible=True) for n,i in enumerate(c['controls'])],'route':c['route'],'theme':c['theme'],'dialog':c['dialog'],'tokens':c['tokens']}
        reference.verify_browser_case(r,'VIS-001',g)
        for field,value in [('bounds',[0,0,-1,10]),('visible',False),('index',99)]:
            bad=copy.deepcopy(g);bad['items'][0][field]=value
            with self.assertRaises(ValueError):reference.verify_browser_case(r,'VIS-001',bad)
    def test_fixture_roster_and_deferred_cases(self):
        visual.validate_contract(ROOT);f=json.loads((ROOT/'design/hooklab/02_CONTRACT/fixtures.json').read_text());self.assertEqual(len(f['fixtures']),15);self.assertEqual(len(f['cases']),24);self.assertTrue(all(not x['native_equivalence'] for x in f['cases']))
if __name__=='__main__':unittest.main()
