#!/usr/bin/env python3
"""Source contract for framework-managed, default-off LSPosed debug events."""
from pathlib import Path
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]
def read(p):return (r/p).read_text(encoding='utf-8')
def must(test,name):
    assert test, name
    print('PASS verbose '+name,flush=True)
keys=read('app/src/main/java/com/mikeyphw/hooklab/core/TweakKeys.java')
logs=read('app/src/main/java/com/mikeyphw/hooklab/core/VerboseLogs.java')
module=read('app/src/main/java/com/mikeyphw/hooklab/hook/HookLabModule.java')
ui=read('app/src/main/java/com/mikeyphw/hooklab/ui/MainActivity.java')
strings=read('app/src/main/res/values/strings.xml')
root=ET.fromstring(strings)
must('diagnostics.verbose_logs' in keys and 'hooklab.tweaks.v1' in keys,'framework-only preference key')
store=read('app/src/main/java/com/mikeyphw/hooklab/ui/FrameworkStateStore.java')
must('getBoolean(TweakKeys.VERBOSE_LSPOSED_LOGS, false)' in logs
     and 'getBoolean(TweakKeys.VERBOSE_LSPOSED_LOGS, false)' in store
     and 'Boolean.TRUE.equals(snapshot.verboseLogging)' in ui,
     'default-off toggle both app and host')
must('write(TweakKeys.GROUP, TweakKeys.VERBOSE_LSPOSED_LOGS, enabled, cb)' in store
     and 'prefs.edit().putBoolean(key, value).commit()' in store
     and 'prefs.getBoolean(key, !value) == value' in store
     and 'expected == app.currentService()' in store
     and 'ready(expected)' in ui and 'stateStore.writeVerbose(value,' in ui,
     'framework preference write with connection gate and verified commit')
must('VerboseLogs.bind(settings, this)' in module and
     'getRemotePreferences(TweakKeys.GROUP)' in module,
     'host logs bound to framework prefs after verified identity')
must('xp.log(Log.DEBUG, "HookLabVerbose", "event=" + event.name())' in logs,
     'LSPosed framework logger, fixed stage vocabulary')
must('AtomicLongArray' in logs and 'MIN_GAP_MS = 3000L' in logs and
     'LAST.compareAndSet(' in logs,'bounded events with rate limiting')
must('remotePreferences = preferences' in logs and
     'return p.getBoolean(TweakKeys.VERBOSE_LSPOSED_LOGS, false)' in logs,
     'host preference read without modification')
must('logcat' not in logs.split('public static void emit(Event event)')[1] and
     'FileOutputStream' not in logs and 'getStackTrace' not in logs and
     'getMessage()' not in logs,'no logcat, host file, stacktrace or exception message writing')
must('new MaterialSwitch(this)' in ui and 'verboseLogging();' in ui and
     'writeVerboseLogging(service(), wanted)' in ui and 'sw.setEnabled(available)' in ui
     and 'snapshot.verboseLogging != null' in ui,
     'settings control disabled until framework snapshot confirms preference')
must(all(root.find("./string[@name='%s']"%n) is not None for n in
     ('verbose_lsposed_heading','verbose_lsposed_logs','verbose_lsposed_description','verbose_lsposed_saved')),
     'translatable UI resources exist')
for name, tokens in {
  'mcc/MccDiamondUi.java':['MCC_BUTTON_ATTACHED','MCC_CONTROL_OPEN','MCC_HOOK_INSTALLED'],
  'obtainx/NeoStoreHook.java':['NEO_PAGE_READY','NEO_TAP_REFUSED','NEO_PREVIEW_REQUESTED'],
  'obtainx/DroidifyHook.java':['DROID_TOOLBAR_ADDED','DROID_SOURCE_REFUSED','DROID_HOOK_INSTALLED'],
}.items():
    c=read('app/src/main/java/com/mikeyphw/hooklab/'+name)
    must(all('VerboseLogs.Event.'+t in c for t in tokens),'safe events '+name)
must((r/'app/src/test/java/com/mikeyphw/hooklab/core/VerboseLogsTest.java').is_file(),
     'hosted pure-Java throttle and default-off JUnit tests')
print('HOOKLAB_M8_VERBOSE_SOURCE_CONTRACT_PASS',flush=True)
