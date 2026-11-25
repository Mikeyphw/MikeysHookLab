"""Structural guard for R01 production routes; real Android behavior requires hosted instrumentation."""
import re,unittest,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
JAVA=ROOT/'app/src/main/java/com/mikeyphw/hooklab'
class DiagnosticsContract(unittest.TestCase):
    def setUp(self):
        self.main=(JAVA/'ui/MainActivity.java').read_text()
        self.state=(JAVA/'ui/DiagnosticWorkspaceState.java').read_text()
    def test_disjoint_dispatch_and_navigation(self):
        for name,call in [('DIAGNOSTICS','diagnosticsPage'),('CAPTURE','capturePage'),('HEALTH','healthPage'),('HEALTH_REPORT','healthReportPage'),('EVENT_REPORT','eventReport')]:
            self.assertRegex(self.main,r'case '+name+r':\s*'+call+r'\(\);\s*break;')
        self.assertIn('private void diagnosticsPage(){diagnosticHeader(DIAGNOSTICS);diagnosticsTimelinePage();}',self.main)
        self.assertIn('private void healthPage(){diagnosticHeader(HEALTH);hookHealthPanel();}',self.main)
        self.assertIn('goTo(eventReturnPage)',self.main)
        self.assertIn('goTo(healthReturnPage)',self.main)
    def test_bound_pickers_and_explicit_copy(self):
        for marker in ('new DiagnosticWorkspaceState.ImportRequest(source,capture)','final DiagnosticWorkspaceState.ImportRequest request=diagnosticState.pendingImport','current!=app.capture()','current.registerImport(request.source','pendingExport=selection','pendingCaptureExport=report','pendingHealthExport=report','setPositiveButton(export?','copyDiagnosticText(selection.jsonl())','diagnosticsStore().selectEvent(e.id())'):
            self.assertIn(marker,self.main)
        self.assertEqual(self.main.count('clipboard.setPrimaryClip('),1)
        self.assertIn('extends ViewModel',self.state)
        self.assertIn('diagnosticGeneration',self.main)
        app=(JAVA/'HookLabApp.java').read_text()
        self.assertIn('Executors.newSingleThreadExecutor()',app)
        self.assertIn('captureImportRunning.get()',app)
    def test_import_export_authority(self):
        saf=(JAVA/'diagnostics/DiagnosticSaf.java').read_text();export=(JAVA/'diagnostics/DiagnosticExport.java').read_text()
        self.assertEqual(saf.count('"content".equals(uri.getScheme())'),3)
        self.assertIn('HookHealthModel.Report report',export)
        self.assertIn('HookHealthModel.redactedJson(report)',export)
        self.assertIn('events_sha256',export);self.assertIn('report_sha256',export)
        self.assertIn('raw_import_lines_included',export)
        self.assertNotIn('Runtime.getRuntime()',saf+export)
    def test_resources_and_fixtures(self):
        resources={el.attrib['name'] for p in (ROOT/'app/src/main/res/values').glob('*.xml') for el in ET.parse(p).getroot() if 'name' in el.attrib}
        used=set(re.findall(r'R\.string\.(r01_\w+)',self.main))
        self.assertTrue(used);self.assertFalse(used-resources)
        fixture=(ROOT/'app/src/androidTest/java/com/mikeyphw/hooklab/qa/DiagnosticFixtureTest.java').read_text()
        for marker in ('stable_inspector_identity_recreate','isolated_workspaces','single_import_sheet_cancel','redaction_preview_cancel_clipboard_unchanged'):
            self.assertIn(marker,fixture)
        self.assertIn('health_exact_same_package_routes',(ROOT/'app/src/androidTest/java/com/mikeyphw/hooklab/ui/FrameworkFixtureTest.java').read_text())
if __name__=='__main__':unittest.main()
