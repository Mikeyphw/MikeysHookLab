"""Real production SDK and diagnostics JVM checks; no Gradle/ADB."""
import subprocess,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

class Mod03ConfigTest(unittest.TestCase):
    def test_production_config_and_imported_identity(self):
        with tempfile.TemporaryDirectory() as tmp:
            classes=Path(tmp)/'classes';classes.mkdir();src=sorted(ROOT.glob('sdk/src/main/java/**/*.java'))+sorted(ROOT.glob('runtime/src/main/java/**/*.java'))+[ROOT/'app/src/main/java/com/mikeyphw/hooklab/diagnostics/DiagnosticTimeline.java',ROOT/'scripts/qa/Mod03ConfigChecks.java',ROOT/'scripts/qa/ChatGptOfferChecks.java',ROOT/'app/src/main/java/com/mikeyphw/hooklab/chatgpt/TermuxOfferGate.java']
            subprocess.run(['javac','--release','17','-Xlint:all','-Werror','-d',str(classes),*map(str,src)],check=True,timeout=30)
            result=subprocess.run(['java','-cp',str(classes)+':'+str(ROOT/'runtime/src/main/resources'),'Mod03ConfigChecks'],check=True,capture_output=True,text=True,timeout=30)
            self.assertIn('MOD03_CONFIG_CHECKS_PASS=',result.stdout);print(result.stdout.strip())
    def test_projected_config_is_actual_runtime_code(self):
        from module_sources import project,manifest,settings_schema
        source=project('wordmaster')['WordMasterNightHook.java'];self.assertIn('context.wordMasterFilter().color()',source);self.assertIn('if(VerboseLogs.active())ensureShade(activity,filter)',source);self.assertEqual(manifest('wordmaster')['version'],'1.1.0');self.assertEqual(settings_schema('wordmaster'),2)
        self.assertEqual(settings_schema('obtainx'),1)
        chat=project('chatgpt');handoff=chat['ChatGptTermuxHandoff.java'];self.assertIn('ctx.chatGptOptions()',handoff);self.assertIn('!host.options.showRun',handoff);self.assertIn('new TermuxOfferGate(code,source,h.options.tapIntervalMs)',handoff);self.assertIn('if(!VerboseLogs.active())return;',handoff);self.assertIn('TermuxCommandPolicy.checkedCommand', (ROOT/'app/src/main/java/com/mikeyphw/hooklab/chatgpt/ChatGptTermuxHandoff.java').read_text());self.assertIn('PackPreviewContract.checkedCommand',handoff);self.assertEqual(manifest('chatgpt')['version'],'1.1.0');self.assertEqual(settings_schema('chatgpt'),2)
        self.assertIn('now-lastTap<minimumInterval',chat['TermuxOfferGate.java'])
if __name__=='__main__':unittest.main()
