#!/usr/bin/env python3
"""WM01 source guard. Real Unity-palette behavior requires Android device acceptance."""
from pathlib import Path
import xml.etree.ElementTree as ET
import re

ROOT = Path(__file__).resolve().parents[1]
read = lambda p: (ROOT / p).read_text(encoding="utf-8")
source = read("app/src/main/java/com/mikeyphw/hooklab/wordmaster/WordMasterNightHook.java")
catalog = read("app/src/main/java/com/mikeyphw/hooklab/core/TweakCatalog.java")
ns = "{http://schemas.android.com/apk/res/android}"
root = ET.fromstring(read("app/src/main/AndroidManifest.xml"))
actual = [x.get(ns + "name") for x in root.findall("./queries/package")]
assert actual == ["br.art.code.meucronogramacapilar", "com.machiav3lli.fdroid",
                  "com.looker.droidify", "com.fugo.wowsolitaire", "dev.bikram.obtainx"]
assert [x.text for x in []] == []
assert read("app/src/main/resources/META-INF/xposed/scope.list").splitlines() == [
    "br.art.code.meucronogramacapilar", "com.machiav3lli.fdroid",
    "com.looker.droidify", "com.fugo.wowsolitaire"]
for expected in (
    'PACKAGE = "com.fugo.wowsolitaire"', 'VERSION_NAME = "0.6.10"',
    'VERSION_CODE = 56240403L', 'TWEAK_ID = "wordmaster.night.filter"',
    'Class.forName(ACTIVITY, false, context.classLoader)',
    'getDeclaredMethod("onResume")', 'chain.proceed()',
    'getPackageInfo(PACKAGE, 0)', 'actual.getLongVersionCode()',
    'matches(context.packageName, actual.getLongVersionCode(), actual.versionName)',
    'context.framework(XposedInterface.class)',
    'setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)',
    'activity.runOnUiThread(', 'decor.findViewWithTag(TAG)',
    'shade.setClickable(false)', 'shade.setLongClickable(false)',
    'shade.setFocusable(false)', 'shade.setImportantForAccessibility(',
    'INSTALLED.compareAndSet(false, true)', 'INSTALLED.set(false)',
    'TweakDiagnostics.rethrowFatal(failure)',
    'WORD_MASTER_NIGHT_FILTER_REFUSED category='):
    assert expected in source, 'MISSING: ' + expected
for forbidden in ('su ', 'Runtime.getRuntime()', 'FileOutputStream',
                  'UnitySendMessage(', 'de.robv.android.xposed',
                  'startActivity(', 'getSharedPreferences('):
    assert forbidden not in source, 'FORBIDDEN: ' + forbidden
assert re.search(r'FILTER\s*=\s*0x64010712;', source)
assert 'WordMasterNightHook::install' in catalog
assert 'WordMasterNightHook.TWEAK_ID' in catalog
assert 'WordMasterNightHook.VERSION_CODE, WordMasterNightHook.VERSION_CODE' in catalog
assert 'Night filter' in catalog and 'Unity' in catalog
assert 'dev.bikram.obtainx' not in catalog
assert 'staticScope=false' in read("app/src/main/resources/META-INF/xposed/module.prop")
assert not root.findall("uses-permission")
test = read("app/src/test/java/com/mikeyphw/hooklab/wordmaster/WordMasterNightHookTest.java")
assert test.count("@Test") >= 3 and 'acceptsExactApk' in test and 'refusesNewerOrOlderBuilds' in test
print("HOOKLAB_WM01_SOURCE_CONTRACT_PASS", flush=True)
