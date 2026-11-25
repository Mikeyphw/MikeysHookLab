"""Validate real production caller integration after native source apply."""
from pathlib import Path
import sys
root=Path(__file__).resolve().parents[2]
def check(ok,why):
    if not ok:raise SystemExit('HOOKLAB_SOURCE_CONTRACT_REFUSED: '+why)
ui=root/'app/src/main/java/com/mikeyphw/hooklab/ui'
a=(ui/'MainActivity.java').read_text();b=(ui/'FrameworkStateStore.java').read_text();env=(ui/'UiEnvironment.java').read_text()
for s in ('UiEnvironmentFactory.create((HookLabApp) getApplication())','new FrameworkStateStore(environment)','environment.currentService()','stateStore.ownsReadySnapshot(s)','stateStore.writeTweak(s, id, enabled,','stateStore.requestScope(s, pkg,','stateStore.removeScope(s, pkg,','stateStore.writeSafetyLock(s, id, locked,','stateStore.writeVerbose(expected, value,','environment.elapsedRealtime()','environment.wallTime()','environment.entries()','environment.all()','environment.knowsPackage('):check(s in a,s)
for s in ('expected == environment.currentService()','!ownsReadySnapshot(expected)','epoch==lifecycleEpoch','ticket != generation','publishedPort = resolved.ready() ? bound : null','prefs.edit().putBoolean(key, value).commit()','prefs.getBoolean(key, !value) == value','once.compareAndSet(false, true)','environment.installedVersion(entry.spec)'):check(s in b,s)
app=(root/'app/src/main/java/com/mikeyphw/hooklab/HookLabApp.java').read_text()
check('environment.app.frameworkTask(' in b and 'frameworkWorker.execute(task)' in app and 'newSingleThreadExecutor' in app,'process-wide serialized worker')
check('pending().complete(ticket)' in b and 'completions.clear()' in b and 'environment.app.frameworkCommandsChanged()' in b,'terminal token cleanup and live observer refresh')
check('installInstrumentationFixture' not in a+b+env,'test entrypoint in production code')
check('TweakCatalog.entries()' in env and 'binder.requestScope(packages' in env and 'binder.removeScope(packages)' in env,'real transport missing')
print('HOOKLAB_ACTUAL_CALLER_CONTRACT_PASS')
