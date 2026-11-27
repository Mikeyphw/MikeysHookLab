"""F08 source route checks; actual ART/window assertions belong to hosted instrumentation."""
from pathlib import Path
import inspect, unittest, tempfile, json, os, hashlib
from subprocess import CompletedProcess
from unittest.mock import patch
import native_smoke
ROOT=Path(__file__).resolve().parents[2]
class ProbeDeviceRepairTests(unittest.TestCase):
    def test_loader_uses_verified_direct_buffer_and_rejection_stages(self):
        bridge=(ROOT/'app/src/debug/java/com/mikeyphw/hooklab/hook/ProbeBridge.java').read_text()
        loader=(ROOT/'app/src/debug/java/com/mikeyphw/hooklab/probe/ProbeDexLoader.java').read_text()
        self.assertIn('ProbeDexLoader.loadVerified(expected,bytes,claimed)',bridge)
        self.assertIn('ProbeProtocol.verifiedDexBuffer(expected,observed,claimed)',loader)
        self.assertNotIn('asReadOnlyBuffer()',bridge+loader)
        for stage in ('TARGET_IDENTITY','GENERATION','OWNED_ASSET','REMOTE_FILE','VERIFY_REMOTE_BYTES','DEX_LOAD','SDK_INSTALL','HOOK_REGISTER','CALLBACK'):
            self.assertIn('"'+stage+'"',bridge)
        self.assertIn('getRemotePreferences',bridge);self.assertIn('checkSignatures',bridge)
        self.assertIn('getClassLoader()!=dex',loader)
    def test_both_windows_use_actual_system_and_cutout_insets(self):
        for path in ('app/src/debug/java/com/mikeyphw/hooklab/probe/TransportProbeActivity.java','probe-target/src/main/java/com/mikeyphw/hooklab/probetarget/ProbeTargetActivity.java'):
            text=(ROOT/path).read_text()
            for expected in ('setDecorFitsSystemWindows(false)','WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()','getInsetsIgnoringVisibility','view.setPadding(safe.left,safe.top,safe.right,safe.bottom)','root.requestApplyInsets()','Build.VERSION_CODES.R'):
                self.assertIn(expected,text)
    def test_hosted_actual_art_and_geometry_are_required(self):
        self.assertEqual(native_smoke.TESTS[-1],native_smoke.PROBE_TEST)
        text=inspect.getsource(native_smoke.run)
        self.assertLess(text.index('probe_check(Path.cwd(),app,release,target)'),text.index('install_apk(apk)'))
        self.assertLess(text.index('instrument(PROBE_TEST)'),text.index('repeat=compare_native_repeats'))
        wf=(ROOT/'.github/workflows/hooklab-native-ui.yml').read_text()
        self.assertIn('--probe-dir "$RUNNER_TEMP/mod01-probe"',wf)
        self.assertIn('hooklab-mod01-probe-${{ github.run_id }}-${{ github.run_attempt }}',wf)
        java=(ROOT/'app/src/androidTest/java/com/mikeyphw/hooklab/qa/ProbeArtFixtureTest.java').read_text()
        for expected in ('ProbeDexLoader.loadVerified(payload,payload.clone(),digest)','MOD01_PROBE_OK','tampered DEX accepted','getRootWindowInsets()','safeBounds.get().contains(b)','text("BASE")'):
            self.assertIn(expected,java)

    def test_exact_run_reaches_post_install_boundary(self):
        # Exercise real run() and installed_digest() with simulated ADB responses.
        # Stops before any capture/instrumentation: no emulator acceptance claimed.
        class ReachedDisplaySetup(Exception): pass
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);probes=root/'probes';probes.mkdir()
            app=root/'app.apk';test=root/'test.apk';release=root/'release.apk';target=probes/'target.apk'
            for apk in (app,test,release,target):apk.write_bytes(apk.name.encode())
            expected={'source_sha':'a'*40};(probes/'probe-receipt.json').write_text(json.dumps(expected))
            packages={'com.mikeyphw.hooklab':app,'com.mikeyphw.hooklab.test':test,'com.mikeyphw.hooklab.probetarget':target}
            installed=set();checked=[]
            def fake_run(argv,**kwargs):
                if argv[3]=='install':
                    installed.add(Path(argv[-1]));return CompletedProcess(argv,0,'Success\n','')
                if argv[3:6]==['shell','pm','path']:
                    self.assertNotIn(packages[argv[-1]],installed);return CompletedProcess(argv,1,'','')
                if argv[3:7]==['shell','-T','pm','path']:
                    package=argv[-1];self.assertIn(packages[package],installed)
                    return CompletedProcess(argv,0,'package:/data/app/~~fixture/'+package+'-abc/base.apk\n','')
                if argv[3:6]==['shell','-T','sha256sum']:
                    path=argv[-1];package=next(p for p in packages if path.endswith('/'+p+'-abc/base.apk'))
                    checked.append(package);return CompletedProcess(argv,0,hashlib.sha256(packages[package].read_bytes()).hexdigest()+'  '+path+'\n','')
                self.fail('Unexpected ADB command: '+repr(argv))
            def find_apk(pattern):return app if '/debug/' in pattern and '/androidTest/' not in pattern else test if '/androidTest/' in pattern else release
            with patch.object(native_smoke,'expected_identity',return_value=expected),patch.object(native_smoke,'guard'),patch.object(native_smoke,'configure_renderer'),patch.object(native_smoke,'one',side_effect=find_apk),patch.object(native_smoke.subprocess,'check_output',return_value='Signer #1 certificate SHA-256 digest: '+'b'*64),patch.object(native_smoke.subprocess,'run',side_effect=fake_run),patch.object(native_smoke,'adb',side_effect=ReachedDisplaySetup),patch('mod01_probe_apk.check',return_value=expected),patch.dict(os.environ,{'ANDROID_HOME':str(root)}):
                with self.assertRaises(ReachedDisplaySetup):native_smoke.run(root/'out',probes)
            self.assertEqual(installed,{app,test,target})
            self.assertEqual(checked,['com.mikeyphw.hooklab.probetarget','com.mikeyphw.hooklab','com.mikeyphw.hooklab.test'])
