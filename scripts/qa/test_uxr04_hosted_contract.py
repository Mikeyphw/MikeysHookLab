"""Hosted build/dependency regressions for the exact R04 failure paths."""
import pathlib,re,unittest,xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[2]

class HostedRepairTests(unittest.TestCase):
    def test_python_dependencies_precede_source_suite(self):
        workflow=(ROOT/'.github/workflows/android.yml').read_text().split('  source:\n',1)[1].split('  terminal:\n',1)[0]
        steps=re.split(r'^      - (?=name:|uses:)',workflow,flags=re.M)[1:]
        install=[i for i,s in enumerate(steps) if "python -m pip install" in s]
        self.assertEqual(len(install),1)
        for dep in ("'Pillow==11.3.0'","'numpy==2.3.5'","'playwright==1.57.0'"):
            self.assertIn(dep,steps[install[0]])
        tests=[i for i,s in enumerate(steps) if 'unittest discover -s scripts/qa' in s]
        self.assertEqual(len(tests),1)
        self.assertLess(install[0],tests[0])
        self.assertNotIn('continue-on-error',steps[install[0]])
        self.assertNotIn('continue-on-error',steps[tests[0]])

    def test_instrumentation_required_in_android_ci(self):
        workflow=(ROOT/'.github/workflows/android.yml').read_text()
        self.assertIn('uses: ./.github/actions/hooklab-dependency-prepare',workflow)
        self.assertIn('profile: full',workflow)
        task=[line.replace('tasks=(', ' ').replace(');;', ' ') for line in (ROOT/'scripts/qa/dependency_prepare.sh').read_text().splitlines() if 'full) tasks=(' in line]
        self.assertEqual(len(task),1)
        for name in ('assembleDebug','assembleDebugAndroidTest','testDebugUnitTest','lintDebug'):
            self.assertIn(':app:'+name,task[0].split())

    def test_actual_error_palette_attribute_and_no_silent_fallback(self):
        native=(ROOT/'app/src/androidTest/java/com/mikeyphw/hooklab/qa/NativeBaselineTest.java').read_text()
        self.assertNotIn('com.google.android.material.R.attr.colorError',native)
        self.assertIn('androidx.appcompat.R.attr.colorError',native)
        self.assertIn('c.getTheme().resolveAttribute(attrs[i],new android.util.TypedValue(),true)',native)
        self.assertIn('MaterialColors.getColor(c,attrs[i],0)',native)
        styles=ET.parse(ROOT/'app/src/main/res/values/styles.xml')
        error=styles.find(".//style[@name='Theme.HookLab']/item[@name='colorError']")
        self.assertIsNotNone(error)
        self.assertEqual(error.text,'@color/hooklab_error')
