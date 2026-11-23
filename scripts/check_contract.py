#!/usr/bin/env python3
"""M4 source guard; hosted build, lint and APK inspection remain separate gates."""
import hashlib
import pathlib
import subprocess
import xml.etree.ElementTree as ET
import tomllib

r = pathlib.Path(__file__).resolve().parents[1]
def read(p): return (r/p).read_text(encoding='utf-8')
def check(ok, name):
    if not ok: raise RuntimeError('CONTRACT_FAIL '+name)
    print('PASS '+name, flush=True)

for file in (r/'app/src/main').rglob('*.xml'): ET.parse(file)
check(True, 'Android XML syntax')
root = ET.parse(r/'app/src/main/AndroidManifest.xml').getroot()
ns = '{http://schemas.android.com/apk/res/android}'
app = root.find('application')
check(not root.findall('uses-permission'), 'no dangerous app permissions')
query_list = root.findall('queries')
check(len(query_list)==1 and all(x.tag=='package' for x in query_list[0])
      and [x.get(ns+'name') for x in query_list[0]]==[
          'br.art.code.meucronogramacapilar','com.machiav3lli.fdroid',
          'com.looker.droidify','com.fugo.wowsolitaire','com.openai.chatgpt','dev.bikram.obtainx'],
      'exact MCC/Neo Store/Droid-ify/Word Master/ChatGPT/ObtainX visibility only')
check(app.get(ns+'allowBackup')=='false' and app.get(ns+'name')=='.HookLabApp', 'lifecycle application')
act = app.findall('activity')
check(len(act)==2 and
      {a.get(ns+'name') for a in act}=={'.ui.MainActivity','.mcc.MccControlActivity'}
      and all(a.get(ns+'exported')=='true' for a in act)
      and len(act[0].findall('intent-filter'))==1
      and len(act[1].findall('intent-filter'))==0,
      'one launcher and one explicit-only MCC activity')
providers = app.findall('provider')
check(len(providers)==1 and providers[0].get(ns+'name')=='io.github.libxposed.service.XposedProvider'
      and providers[0].get(ns+'authorities')=='${applicationId}.XposedService'
      and providers[0].get(ns+'directBootAware')=='true', 'exact framework provider')
wf = read('.github/workflows/android.yml')
check('setup-android@v4.0.4' in wf and "packages: 'platform-tools'" in wf
      and "cmdline-tools-version: '15859902'" in wf and 'sdkmanager tools' not in wf,
      'no deprecated SDK tools setup')
check(all(term in wf for term in (':app:assembleDebug',':app:testDebugUnitTest',':app:lintDebug',
                                  'apksigner','sha256sum','source-commit.txt')),
      'hosted build/test/lint/provenance stages')
# AAR metadata for libxposed API 102 requires compileSdk 37. Do not upgrade targetSdk here.
check("version '9.1.1'" in read('build.gradle') and 'version = release(37)' in read('app/build.gradle')
      and 'minorApiLevel = 0' in read('app/build.gradle')
      and 'compileSdk 37' not in read('app/build.gradle')
      and 'targetSdk 36' in read('app/build.gradle'), 'supported AGP/API37 while preserving targetSdk36')
check("sdkmanager 'platforms;android-37.0' 'build-tools;36.0.0' 'platform-tools'" in wf
      and 'platforms/android-37.0/android.jar' in wf
      and 'sdkmanager tools' not in wf, 'CI installs published API37.0 with no deprecated tools package')
with (r/'.devtool.toml').open('rb') as fp:
    config = tomllib.load(fp)
for task in ('assemble_debug', 'unit_debug', 'lint_debug'):
    md = config['targets']['hooklab']['jobs'][task]['metadata']
    check('platforms;android-37.0' in md['ci_android_sdk_packages']
          and md['ci_android_wrapper_jar_sha256'] ==
          '81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f',
          'native Devtool Android environment '+task)
build = read('app/build.gradle')
check("compileOnly 'io.github.libxposed:api:102.0.0'" in build
      and "implementation 'io.github.libxposed:service:102.0.0'" in build,
      'published modern libxposed artifacts')
meta = r/'app/src/main/resources/META-INF/xposed'
check((meta/'java_init.list').read_text().strip()=='com.mikeyphw.hooklab.hook.HookLabModule'
      and (meta/'scope.list').read_text(encoding='utf-8').splitlines()==[
          'br.art.code.meucronogramacapilar','com.machiav3lli.fdroid',
          'com.looker.droidify','com.fugo.wowsolitaire','com.openai.chatgpt'],
      'modern entry identity and exact recommended host scope')
check((meta/'module.prop').read_text().splitlines()==[
      'minApiVersion=102','targetApiVersion=102','staticScope=false',
      'exceptionMode=protective','autoHotReload=false'], 'modern API 102 contract')
module = read('app/src/main/java/com/mikeyphw/hooklab/hook/HookLabModule.java')
check(all(term in module for term in ('extends XposedModule', 'onModuleLoaded(', 'onPackageLoaded(',
      'param.isFirstPackage()', 'getDeclaredMethod("attach", Context.class)',
      'getPackageInfo(pkg, 0).getLongVersionCode()',
      'getRemotePreferences(TweakKeys.GROUP)', 'settings.getBoolean(TweakKeys.enabled(id), false)')),
      'late verified process/version/opt-in dispatcher')
catalog = read('app/src/main/java/com/mikeyphw/hooklab/core/TweakCatalog.java')
check('MccIds.DIAMOND_MANUAL' in catalog and
      all(x not in catalog for x in ('MccIds.VIP','MccIds.DIAMOND_AUTO','MccIds.BANNER_HIDE')) and
      'MccDiamondUi::install' in catalog and 'NeoStoreHook::install' in catalog
      and 'DroidifyHook::install' in catalog and 'Arrays.<TweakEngine.Entry' in catalog,
      'manual MCC and two independent ObtainX adapters; no active VIP/auto/banner')
ui = read('app/src/main/java/com/mikeyphw/hooklab/ui/MainActivity.java')

# PR #8 Android 16 startup regression: Toolbar.setNavigationIcon(int) must not get 0.
render_source = ui.split('private void render() {', 1)[-1]
check('private void render() {' in ui
      and 'toolbar.setNavigationIcon(R.drawable.ic_nav_back);' in render_source
      and 'toolbar.setNavigationIcon((android.graphics.drawable.Drawable) null);' in render_source
      and 'setNavigationIcon(secondary ?' not in render_source
      and 'setNavigationIcon(0)' not in render_source,
      'toolbar navigation uses nullable Drawable; no invalid resource ID zero')
check('android:icon="@mipmap/ic_launcher"' in read('app/src/main/AndroidManifest.xml')
      and '@drawable/ic_hooklab_foreground' in read('app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml')
      and '@drawable/ic_hooklab_monochrome' in read('app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml')
      and 'M223 143' in read('app/src/main/res/drawable/ic_hooklab.xml')
      and 'M256 186' in read('app/src/main/res/drawable/ic_hooklab.xml'),
      'flat HookLab adaptive icon and SVG-backed hook glyph')

state_store = read('app/src/main/java/com/mikeyphw/hooklab/ui/FrameworkStateStore.java')
state_model = read('app/src/main/java/com/mikeyphw/hooklab/ui/FrameworkSnapshot.java')
check(all(term in ui for term in ('stateStore.start(', 'stateStore.refresh()',
                                  'stateStore.writeTweak(', 'stateStore.writeSafetyLock(',
                                  'stateStore.writeVerbose(', 'TweakCatalog.knowsPackage('))
      and all(term not in ui for term in ('getRemotePreferences(', 'getScope(', 'getApiVersion('))
      and 'DynamicColors.applyToActivityIfAvailable(this)' in ui
      and 'snapshot.eligibility(spec)' in ui
      and all(term in state_store for term in ('getRemotePreferences(TweakKeys.GROUP)',
             'getRemotePreferences(TweakDiagnostics.GROUP)', 'worker.execute(',
             'ticket != generation', 'expected == app.currentService()',
             'prefs.edit().putBoolean(key, value).commit()', 'prefs.getBoolean(key, !value) == value',
             'requestScope(', 'removeScope(', 'main.post(', 'FrameworkSnapshot.loading()'))
      and all(term in state_model for term in ('Connection.LOADING', 'Connection.UNAVAILABLE',
             'Collections.unmodifiableMap', 'Collections.unmodifiableSet', 'TweakCompatibility.assess(')),
      'UI-03 background-only binder, immutable snapshot, verified writes and Material You')
app_source = read('app/src/main/java/com/mikeyphw/hooklab/HookLabApp.java')
check('XposedServiceHelper.registerListener(this)' in app_source and 'onServiceDied(' in app_source,
      'service connection lifecycle')
check(not any('de.robv.android.xposed' in f.read_text() for f in
      (r/'app/src/main/java').rglob('*.java')), 'no legacy Xposed API')
jar = r/'gradle/wrapper/gradle-wrapper.jar'
check(jar.is_file() and hashlib.sha256(jar.read_bytes()).hexdigest()==
      '81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f',
      'pinned Gradle wrapper jar')
check('distributionSha256Sum=b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06'
      in read('gradle/wrapper/gradle-wrapper.properties') and 'gradle-9.3.1-bin.zip' in read('gradle/wrapper/gradle-wrapper.properties'), 'pinned Gradle distribution')
subprocess.run(['bash','-n',str(r/'gradlew')],check=True)
# M4: fail-closed and diagnostic truth; do not rely solely on existence of classes.
engine = read('app/src/main/java/com/mikeyphw/hooklab/core/TweakEngine.java')
diag = read('app/src/main/java/com/mikeyphw/hooklab/core/TweakDiagnostics.java')
compat = read('app/src/main/java/com/mikeyphw/hooklab/core/TweakCompatibility.java')
check(all(t in engine for t in (
      'TweakDiagnostics.rethrowFatal(failure)', 'policy.quarantined(id)',
      'policy.quarantine(id)', 'continue; // fail closed',
      'catch (Throwable failure)', 'record(statuses, id, TweakDiagnostics.Outcome.INSTALL_FAILED',
      'TweakDiagnostics.Outcome.PREFERENCE_UNAVAILABLE',
      'TweakDiagnostics.Outcome.QUARANTINE_UNAVAILABLE')),
      'per-tweak fail-closed guarded dispatcher and fault isolation')
check(all(t in module for t in ('getRemotePreferences(TweakDiagnostics.GROUP)',
      'TweakDiagnostics.quarantineKey(id)', 'failedInProcess.add(id)',
      'ConcurrentHashMap.newKeySet()', 'TweakDiagnostics.rethrowFatal(ex)',
      'TweakDiagnostics.failureCategory(failure)', 'outcome.name()'))
      and '.edit()' not in module,
      'read-only host preferences, process-local fault latch and sanitized framework logs')
check(all(t in diag for t in ('"hooklab.safety.v1"',
      'public static void rethrowFatal(Throwable failure)',
      'failure instanceof VirtualMachineError', 'failure instanceof ThreadDeath',
      'return "LINKAGE";', 'TweakKeys.enabled(id);')),
      'whitelisted diagnostic codes and preserved fatal JVM exceptions')
check(all(t in compat for t in ('FRAMEWORK_UNAVAILABLE', 'SCOPE_UNKNOWN',
      'OUT_OF_SCOPE', 'QUARANTINED', 'VERSION_UNVERIFIED',
      'VERSION_UNSUPPORTED', 'ELIGIBLE', 'verifiedVersion == null')),
      'honest three-valued compatibility model')
check(all(t in ui for t in ('diagnosticsPage()', 'confirmSafetyLock(s, id, nextLock)',
      'R.string.diagnostics_note', 'R.string.diagnostics_reset_confirm',
      's == service()', 'stateStore.writeSafetyLock(id, locked,'))
      and all(t in state_store for t in ('writeSafetyLock(String id, boolean locked, Completion cb)',
             'TweakDiagnostics.quarantineKey(id)', 'prefs.edit().putBoolean(key, value).commit()')),
      'framework-backed manual safety locks on background executor with UI confirmation')
check('targetSdk 36' in build, 'preserve Android target SDK 36')
tests = read('app/src/test/java/com/mikeyphw/hooklab/core/TweakEngineM4Test.java')
check(tests.count('@Test') >= 12 and all(t in tests for t in (
      'failedQuarantineReadFailsClosed', 'linkageErrorIsQuarantinedAndIndependentTweakRuns',
      'preferenceReadFailureDoesNotQuarantine', 'fatalVmErrorIsRethrown',
      'inProcessFailureLatchSkipsSecondAttempt',
      'compatibilityNeverAssumesCurrentVersion')),
      'M4 hosted fault-isolation and compatibility regression coverage')
mcc = r/'app/src/main/java/com/mikeyphw/hooklab/mcc'
for name in ('MccIds','MccDiamondUi','MccDiamondPlacement','MccRefillCommand',
             'MccRefillWorker','MccControlActivity'):
    check((mcc/(name+'.java')).is_file(), 'MCC manual adapter '+name)
button=read(mcc.relative_to(r)/'MccDiamondUi.java')
control=read(mcc.relative_to(r)/'MccControlActivity.java')
worker=read(mcc.relative_to(r)/'MccRefillWorker.java')
command=read(mcc.relative_to(r)/'MccRefillCommand.java')
check('setOnClickListener(v -> openControl(activity))' in button
      and 'MccDiamondPlacement.position(' in button
      and 'onWindowFocusChanged' in button and 'isFlutter(activity)' in button
      and 'ActivityOptions.makeBasic()' in button,
      'Flutter Activity button visible and solely user initiated')
check('MccRefillWorker.setNine(' in control
      and 'MccRefillWorker.inspect(' in control
      and 'setPositiveButton("Set to 9"' in control
      and '.setOnClickListener(v -> operation(' in control
      and 'onCreate(' in control and 'startRefill(true)' not in control,
      'no automatic navigation/root on MCC control Activity creation')
check('getApplicationInfo(MccIds.PACKAGE,0)' in worker
      and 'new ProcessBuilder("su","-c",MccRefillCommand.script(dir,probe))' in worker
      and 'WRITE_9_VERIFIED_' in worker and 'return "BALANCE_FILE_NOT_FOUND"' in worker,
      'manual root operation under HookLab UID with explicit failure truth')
check('checkedDataDir(verifiedDir)' in command,
      'source-checked data-directory input')
check('data/databases' in command
      and 'data/files' in command and 'data/app_flutter' in command
      and 'MCC_MULTIPLE_ACCOUNTS' in command and 'MCC_BALANCE_WRITE_VERIFIED' in command
      and 'am force-stop --user' in command and 'MCC_STORAGE_READY' in command,
      'strict bounded MCC-only balance discovery and verified manual write')
check('MCC_AUTO_' not in catalog and 'MccDiamondUi.requestAutomatic()' not in catalog
      and 'HANDED_OFF' not in button and 'requestAutomatic() { return false; }' in button,
      'legacy automatic process cannot install or navigate')
check('MccControlActivity.class' in ui and 'R.string.mcc_version_warning' in ui,
      'module companion control and unverified-version notice')
check('versionCode 10' in build and "versionName '0.7.1'" in build,
      'bump versionCode for stable signing updates')
check((r/'app/src/test/java/com/mikeyphw/hooklab/mcc/MccDiamondPlacementTest.java').is_file()
      and (r/'app/src/test/java/com/mikeyphw/hooklab/mcc/MccRefillCommandTest.java').is_file(),
      'hosted placement, storage and root contract tests')

subprocess.run(['python3','-B',str(r/'scripts/check_verbose_contract.py')],check=True)
apk_check = read('scripts/check_apk.py')
check('EXPECTED_SCOPE' in apk_check and
      'names.count(PREFIX + "scope.list") == 1' in apk_check and
      'z.read(PREFIX + "scope.list") == EXPECTED_SCOPE' in apk_check and
      'Unexpected recommended scope' in apk_check and
      'assert path+\'scope.list\' not in z.namelist()' not in apk_check,
      'APK metadata verifier requires exact M8 recommended scope')
check((r/'scripts/test_apk_scope.py').is_file(),
      'Synthetic positive and negative M8 packaged-scope tests available')
subprocess.run(['python3','-B',str(r/'scripts/test_apk_scope.py')],check=True)

print('HOOKLAB_M5_MCC_SOURCE_CONTRACT_PASS', flush=True)
print('HOOKLAB_M4_SOURCE_CONTRACT_PASS', flush=True)

subprocess.run(['python3','-B',str(r/'scripts/check_obtainx_contract.py')],check=True)

# M5.1 signed-release contract: no key material, main-only protected environment.
subprocess.run(['python3','-B',str(r/'scripts/check_signing_contract.py')],check=True)
subprocess.run(['python3','-B',str(r/'scripts/test_signing.py')],check=True)

# WM01: new Unity night appearance remains opt-in and scene-level proof requires device.
subprocess.run(['python3','-B',str(r/'scripts/check_wordmaster_contract.py')],check=True)
subprocess.run(['python3','-B',str(r/'scripts/check_cgt01_probe_contract.py')],check=True)

# MCC source/negative tests run in CI. APK binary is independently verified off-CI.
subprocess.run(['python3','-B',str(r/'scripts/check_mcc_apk_contract.py')],check=True)
