"""Source guard and pure JVM behavior; no Android runtime or Gradle claim."""
from pathlib import Path
import re, shutil, subprocess, tempfile, unittest, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
JAVA=ROOT/'app/src/main/java/com/mikeyphw/hooklab'
class NavigationContract(unittest.TestCase):
    def test_pure_production_routes_and_cache(self):
        self.assertIsNotNone(shutil.which('javac'),'JDK required for pure navigation checks')
        with tempfile.TemporaryDirectory() as t:
            files=[JAVA/x for x in ('core/TweakSpec.java','core/HookGroupCatalog.java','core/HookDetailRoute.java','core/CatalogRoutes.java','ui/RouteTrail.java','ui/IconStateCache.java')]
            subprocess.run(['javac','--release','17','-d',t,*map(str,files),str(ROOT/'scripts/qa/NavigationChecks.java')],check=True,timeout=30)
            result=subprocess.check_output(['java','-cp',t,'com.mikeyphw.hooklab.ui.NavigationChecks'],text=True,timeout=30)
            self.assertIn('TOTAL=22',result);self.assertEqual(sum(l.startswith('PASS ') for l in result.splitlines()),22)
    def test_navigation_boundaries(self):
        s=(JAVA/'ui/MainActivity.java').read_text()
        for name in ('showApp','openCatalogGroup','openCatalogApp','showGroup','catalogHeader','catalogCard','packageDetail','renderCatalogResults'):
            m=re.search(r'private (?:void|LinearLayout) '+name+r'\(',s);self.assertIsNotNone(m,name)
            end=re.search(r'\n    (?:private|public|@Override|protected) ',s[m.end():]);body=s[m.start():m.end()+end.start()] if end else s[m.start():]
            self.assertNotRegex(body,r'saveTweak\(|requestScope\(|removeScope\(|setSafetyLock\(|Runtime\.getRuntime|setOnLongClickListener')
        self.assertIn('CatalogRoutes.app(environment.all(),pkg)',s)
        self.assertIn('CatalogRoutes.group(HookGroupCatalog.groups(environment.all()),group.id)',s)
        self.assertIn('catalogResults.findViewWithTag(tag)',s)
        self.assertIn('out.putString("catalogFocusedRow",catalogFocusedRow)',s)
    def test_visibility_and_shared_provider(self):
        manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot();a='{http://schemas.android.com/apk/res/android}'
        self.assertFalse(any(e.get(a+'name')=='android.permission.QUERY_ALL_PACKAGES' for e in manifest.findall('uses-permission')))
        s=(JAVA/'ui/AppIconProvider.java').read_text();self.assertIn('getApplicationIcon(app)',s);self.assertIn('Availability.UNKNOWN',s);self.assertIn('NameNotFoundException',s);self.assertIn('private static final int MAX=32',s)
        self.assertNotIn('java.net',s)
if __name__=='__main__':unittest.main()
