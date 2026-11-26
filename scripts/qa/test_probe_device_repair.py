"""F08 source route checks; actual ART/window assertions belong to hosted instrumentation."""
from pathlib import Path
import inspect, unittest
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
