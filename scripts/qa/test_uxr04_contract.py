"""Locally executable F01 and R04 source guards; no Android runtime claims."""
import pathlib,re,unittest
ROOT=pathlib.Path(__file__).resolve().parents[2]
class R04SourceContract(unittest.TestCase):
    def test_generated_provenance_resource_enabled(self):
        build=(ROOT/'app/build.gradle').read_text()
        self.assertRegex(build,r'(?s)android\s*\{.*?buildFeatures\s*\{\s*resValues\s*=\s*true\s*\}')
        self.assertIn("resValue 'string', 'hooklab_build_source', hooklabBuildSource",build)
        self.assertIn("commandLine 'git', 'rev-parse', '--verify', 'HEAD'",build)
        self.assertIn("commandLine 'git', 'status', '--porcelain', '--untracked-files=all'",build)
        self.assertIn("def hooklabBuildSource = 'unknown'",build)
        fixture=(ROOT/'app/src/androidTest/java/com/mikeyphw/hooklab/ui/FrameworkFixtureTest.java').read_text()
        self.assertIn('getString(R.string.hooklab_build_source)',fixture)
        self.assertIn('a.getString(R.string.r03_source,source)',fixture)
    def test_process_owned_pending_commands(self):
        app=(ROOT/'app/src/main/java/com/mikeyphw/hooklab/HookLabApp.java').read_text()
        store=(ROOT/'app/src/main/java/com/mikeyphw/hooklab/ui/FrameworkStateStore.java').read_text()
        self.assertIn('new com.mikeyphw.hooklab.ui.PendingFrameworkCommands()',app)
        self.assertIn('environment.app.frameworkCommands()',store)
        stop=store.split('public void stop()',1)[1].split('public void close()',1)[0]
        self.assertIn('completions.clear()',stop);self.assertNotIn('pending().complete',stop)
        self.assertIn('epoch==lifecycleEpoch',store)
        self.assertIn('pending().complete(ticket)',store)
        self.assertIn('expected.getScope().contains(pkg)',store)
        self.assertIn('prefs.getBoolean(key, !value) == value',store)
        self.assertIn('once.compareAndSet(false, true)',store)
    def test_adaptive_production_callers(self):
        ui=(ROOT/'app/src/main/java/com/mikeyphw/hooklab/ui/MainActivity.java').read_text()
        self.assertIn('new AdaptiveContentFrame(this,UiLayoutPolicy.MAX_CONTENT_WIDTH_DP)',ui)
        self.assertIn('UiLayoutPolicy.stackActions(',ui)
        self.assertIn('WindowInsetsCompat.Type.ime()',ui)
        self.assertIn('if(page<HOME||page>PRIVACY)page=HOME',ui)
        self.assertIn('boundedScroll(state.getInt("r04.scroll"))',ui)
        self.assertIn('R.string.r04_dynamic_unavailable',ui)
        self.assertIn('pending.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE)',ui)
