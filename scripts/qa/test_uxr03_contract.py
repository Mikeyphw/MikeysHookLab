"""R03 production model, palette and evidence negatives; stdlib and JDK only."""
import pathlib, subprocess, tempfile, unittest, xml.etree.ElementTree as ET
from visual_routes import MAP, BODY, THEME
from visual_body import PALETTE, validate
ROOT=pathlib.Path(__file__).resolve().parents[2]
JAVA=ROOT/'app/src/main/java/com/mikeyphw/hooklab'
class R03Contract(unittest.TestCase):
    def test_fixed_palette_and_contrast(self):
        fields={'accent':'accent','bg':'canvas','error':'error','ink':'on_surface','line':'divider','muted':'muted','on':'accent_ink','surface':'surface','surface2':'surface_2','warn':'warning'}
        def lum(hex):
            values=[int(hex[i:i+2],16)/255 for i in (1,3,5)]
            return sum(w*(c/12.92 if c<=.04045 else ((c+.055)/1.055)**2.4) for w,c in zip((.2126,.7152,.0722),values))
        for theme,folder in [('light','values'),('dark','values-night')]:
            values={e.get('name'):e.text.lower() for e in ET.parse(ROOT/f'app/src/main/res/{folder}/colors.xml').getroot()}
            self.assertEqual({k:values['hooklab_'+v] for k,v in fields.items()},PALETTE[theme])
            for foreground,background in [('ink','bg'),('muted','surface'),('on','accent'),('warn','surface'),('error','surface')]:
                a,b=sorted((lum(PALETTE[theme][foreground]),lum(PALETTE[theme][background])));self.assertGreaterEqual((b+.05)/(a+.05),4.5)
    def test_owned_cases_and_body_roster(self):
        self.assertEqual(len(MAP),14);self.assertEqual(set(['VIS-001','VIS-015','VIS-016','VIS-017','VIS-018','VIS-019','VIS-020','VIS-021','VIS-024'])-set(MAP.values()),set())
        self.assertEqual(set(MAP)-set(BODY),{'diagnostics'})
    def test_body_evidence_refuses_missing_and_wrong_palette(self):
        route='settings'; controls={k:{'label':v['label'],'instances':[{'clickable':True,'enabled':True,'visible':False,'geometry':None}]} for k,v in BODY[route].items()}
        g={'palette':PALETTE[THEME[route]],'body_controls':controls};validate(g,route)
        g['palette']={};self.assertRaises(ValueError,validate,g,route);g['palette']=PALETTE[THEME[route]];controls['privacy']['instances'][0]['clickable']=False;self.assertRaises(ValueError,validate,g,route);controls.pop('privacy');self.assertRaises(ValueError,validate,g,route)
    def test_exact_group_and_hook_evidence_and_privacy(self):
        s=(JAVA/'ui/MainActivity.java').read_text()
        self.assertIn('filter.groupHooks=java.util.Collections.unmodifiableSet(members)',s)
        self.assertIn('diagnosticState.filter.hookId=spec.id',s)
        self.assertIn('private void privacyPage()',s)
        self.assertIn('getString(R.string.hooklab_build_source)',s)
        body=s.split('private void privacyPage()',1)[1].split('private void settings()',1)[0]
        self.assertNotRegex(body,r'requestScope\(|saveTweak\(|removeScope\(|Runtime\.getRuntime')
    def test_production_geometry_and_home_model(self):
        names=('TweakSpec','HookGroupCatalog','TweakCompatibility','FrameworkSnapshot','DiagnosticTimeline','HomeDashboardModel','DesignTokens','UiLayoutPolicy','PendingFrameworkCommands')
        paths=[p for p in JAVA.rglob('*.java') if p.stem in names]
        tests=ROOT/'app/src/test/java/com/mikeyphw/hooklab/ui'
        with tempfile.TemporaryDirectory() as tmp:
            t=pathlib.Path(tmp);j=t/'org/junit';j.mkdir(parents=True)
            (j/'Test.java').write_text('package org.junit; @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Test {}')
            (j/'Assert.java').write_text('package org.junit; public class Assert { public static void fail(String s){throw new AssertionError(s);} public static void assertTrue(boolean b){if(!b)fail("false");} public static void assertFalse(boolean b){assertTrue(!b);} public static void assertNull(Object a){assertEquals(null,a);} public static void assertEquals(Object a,Object b){if(!java.util.Objects.equals(a,b))fail(a+" != "+b);} public static void assertEquals(long a,long b){if(a!=b)fail(a+" != "+b);} }')
            runner=t/'Run.java';runner.write_text('public class Run { public static void main(String[] a)throws Exception{int n=0;for(String c:a){Object o=Class.forName(c).getConstructor().newInstance();for(var m:o.getClass().getMethods())if(m.isAnnotationPresent(org.junit.Test.class)){m.invoke(o);n++;}}if(n!=17)throw new AssertionError(n);System.out.println("R03_JVM_TESTS="+n);}}')
            subprocess.run(['javac','--release','17','-d',tmp,*map(str,paths),str(tests/'HomeDashboardModelTest.java'),str(tests/'UiLayoutPolicyTest.java'),str(tests/'PendingFrameworkCommandsTest.java'),*map(str,j.glob('*.java')),str(runner)],check=True,timeout=30)
            subprocess.run(['java','-cp',tmp,'Run','com.mikeyphw.hooklab.ui.HomeDashboardModelTest','com.mikeyphw.hooklab.ui.UiLayoutPolicyTest','com.mikeyphw.hooklab.ui.PendingFrameworkCommandsTest'],check=True,timeout=30)
