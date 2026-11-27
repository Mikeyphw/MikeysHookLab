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
    def test_switch_fixture_checks_actual_toggle_and_rebuilt_state(self):
        source=(ROOT/'app/src/androidTest/java/com/mikeyphw/hooklab/packs/ChatGptProfileFixture.java').read_text()
        self.assertNotIn('assertTrue(show.performClick())',source)
        for expected in ('show.performClick();','assertFalse(show.isChecked())','assertFalse(editor.draft().profiles().get("quiet").showRun)','assertNotSame(show,refreshed)','assertFalse(refreshed.isChecked())','assertFalse(save.isEnabled())','assertNotNull(reviewed.get())','assertTrue(original.profiles().get("quiet").showRun)'):
            self.assertIn(expected,source)
    def test_module_instrumentation_failure_retains_only_safe_bound_evidence(self):
        from unittest.mock import patch
        from types import SimpleNamespace
        import contextlib,io,json,mod03_visual as v
        ident={'source_sha':'a'*40,'source_tree':'b'*40,'run_id':7,'run_attempt':1}
        row={'schema':'hooklab.qa.failure.v1','source':'a'*40,'test':v.TEST.split('#')[-1],'phase':'CHATGPT_EDITOR','exception_classes':['java.lang.AssertionError'],'accessibility_checks':[],'source_frames':['com.mikeyphw.hooklab.packs.ChatGptProfileFixture.exercise(ChatGptProfileFixture.java:30)']}
        for reason,result in [('TEST_FAILED',SimpleNamespace(returncode=0,stdout='PRIVATE\nINSTRUMENTATION_STATUS_CODE: -2\nINSTRUMENTATION_CODE: -1')),('TRANSPORT_FAILED',SimpleNamespace(returncode=2,stdout='PRIVATE')),('TIMEOUT',subprocess.TimeoutExpired('adb',240,output=b'PRIVATE')),('SPAWN_FAILED',OSError('PRIVATE'))]:
            with tempfile.TemporaryDirectory() as temp,patch.object(v.n,'clear_failure_checkpoint'),patch.object(v.n,'raw',return_value=json.dumps(row).encode()),patch.object(v.subprocess,'run',side_effect=result if isinstance(result,Exception) else None,return_value=result) as launch,contextlib.redirect_stderr(io.StringIO()) as stderr:
                with self.assertRaisesRegex(ValueError,reason):v.run_module_instrumentation(Path(temp),ident,'c'*64,'d'*64)
                report=json.loads((Path(temp)/'receipt.json').read_text());self.assertEqual(report['reason'],reason);self.assertEqual(report['cause_checkpoint'],row);self.assertEqual(report['source_sha'],ident['source_sha']);self.assertEqual(report['status'],'FAILED_NOT_APPROVAL');self.assertEqual(launch.call_count,1);self.assertNotIn('PRIVATE',stderr.getvalue()+json.dumps(report))
        with patch.object(v.n,'raw',side_effect=lambda *args:json.dumps(row).encode()):
            self.assertIsNone(v.n.failure_checkpoint('z'*40,v.TEST,v.MODULE_PHASES));row['source_frames']=['com.mikeyphw.hooklab.packs.Other.dump(Other.java:1)'];self.assertIsNone(v.n.failure_checkpoint('a'*40,v.TEST,v.MODULE_PHASES))
    def test_module_success_requires_exact_terminal_and_does_not_emit_failure(self):
        from unittest.mock import patch
        from types import SimpleNamespace
        import mod03_visual as v
        klass,method=v.TEST.split('#');status=f'INSTRUMENTATION_STATUS: class={klass}\nINSTRUMENTATION_STATUS: test={method}\n'
        output=status+'INSTRUMENTATION_STATUS_CODE: 1\n'+status+'INSTRUMENTATION_STATUS_CODE: 0\nOK (1 test)\nINSTRUMENTATION_CODE: -1\n'
        with tempfile.TemporaryDirectory() as tmp,patch.object(v.n,'clear_failure_checkpoint') as clear,patch.object(v.subprocess,'run',return_value=SimpleNamespace(returncode=0,stdout=output)) as launch:
            v.run_module_instrumentation(Path(tmp),{'source_sha':'a'*40},'b'*64,'c'*64);clear.assert_called_once();launch.assert_called_once();self.assertFalse((Path(tmp)/'receipt.json').exists())
if __name__=='__main__':unittest.main()
