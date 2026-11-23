#!/usr/bin/env python3
"""TB01 static/source guard. Build, lint, Android delivery and device tests remain independent."""
import pathlib
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
base = ROOT / 'app/src/main'
manifest = ET.parse(base / 'AndroidManifest.xml').getroot()
ns = '{http://schemas.android.com/apk/res/android}'
assert [p.get(ns+'name') for p in manifest.findall('uses-permission')] == [
    'com.termux.permission.RUN_COMMAND'
], 'One explicitly gated Termux command permission only'
queries = manifest.find('queries')
assert queries is not None
assert len([p for p in queries.findall('package') if p.get(ns+'name') == 'com.termux']) == 1
app = manifest.find('application')
assert app is not None
activity = next((a for a in app.findall('activity') if a.get(ns+'name') ==
                 '.termux.TermuxBridgeActivity'), None)
assert activity is not None and activity.get(ns+'exported') == 'false'
assert not activity.findall('intent-filter')
alias = app.find('activity-alias')
assert alias is not None and alias.get(ns+'name') == '.termux.TermuxShareTarget'
assert alias.get(ns+'targetActivity') == '.termux.TermuxBridgeActivity'
assert alias.get(ns+'exported') == 'true'
assert alias.get(ns+'label') == '@string/termux_share_label'
filters = alias.findall('intent-filter')
assert len(filters) == 2
for node, expected in zip(filters, ('android.intent.action.SEND', 'android.intent.action.PROCESS_TEXT')):
    assert [x.get(ns+'name') for x in node.findall('action')] == [expected]
    assert [x.get(ns+'name') for x in node.findall('category')] == ['android.intent.category.DEFAULT']
    assert [x.get(ns+'mimeType') for x in node.findall('data')] == ['text/plain']
activity_source = (base / 'java/com/mikeyphw/hooklab/termux/TermuxBridgeActivity.java').read_text()
policy_source = (base / 'java/com/mikeyphw/hooklab/termux/TermuxCommandPolicy.java').read_text()
main_source = (base / 'java/com/mikeyphw/hooklab/ui/MainActivity.java').read_text()
assert 'TermuxBridgeActivity.class' in main_source and 'R.string.termux_bridge_open' in main_source and 'pm.setComponentEnabledSetting(shareAlias,' in main_source
assert all(x in activity_source for x in (
    'Intent.ACTION_SEND', 'Intent.ACTION_PROCESS_TEXT', 'Intent.EXTRA_TEXT',
    'Intent.EXTRA_PROCESS_TEXT', 'requestPermissions(', 'checkSelfPermission(PERMISSION)',
    'TermuxCommandPolicy.checkedCommand(', 'TermuxCommandPolicy.checkedWorkingDirectory(',
    'new MaterialAlertDialogBuilder(this)', 'android.R.string.cancel',
    'setPositiveButton(R.string.termux_bridge_execute',
    'request.setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")',
    '"com.termux.RUN_COMMAND_PATH"', '"com.termux.RUN_COMMAND_ARGUMENTS"',
    '"com.termux.RUN_COMMAND_WORKDIR"', '"com.termux.RUN_COMMAND_BACKGROUND", false',
    '"com.termux.RUN_COMMAND_SESSION_ACTION", "0"',
    'new String[]{"-l", "-i", "-c", command}',
    'return; // No automatic execution if permission is subsequently granted.',
    'status.setText(R.string.termux_bridge_dispatched);',
    'ClipboardManager', 'manager.getPrimaryClip()', 'getSharedPreferences(PREFS, MODE_PRIVATE)',
)), 'Missing mandatory native share / explicit dispatch implementation'
assert all(x in policy_source for x in ('public static String checkedCommand(',
         'public static String checkedWorkingDirectory(', 'MAX_BYTES', 'StandardCharsets.UTF_8'))
assert all(x not in activity_source for x in ('ProcessBuilder(', 'Runtime.getRuntime()',
                'System.out.println(command)', 'Log.d(', 'Log.e(', 'getRemotePreferences(', 'XposedService'))
assert (base / 'resources/META-INF/xposed/java_init.list').read_text().strip() == (
    'com.mikeyphw.hooklab.hook.HookLabModule')
assert 'termux' not in (base / 'resources/META-INF/xposed/scope.list').read_text().lower()
tests = (ROOT / 'app/src/test/java/com/mikeyphw/hooklab/termux/TermuxCommandPolicyTest.java').read_text()
assert tests.count('@Test') >= 12
build = (ROOT / 'app/build.gradle').read_text()
assert 'versionCode 9' in build and "versionName '0.7.0'" in build
assert 'targetSdk 36' in build
print('HOOKLAB_TB01_SOURCE_GUARD=PASS')
