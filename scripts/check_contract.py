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
check([p.get(ns+'name') for p in root.findall('uses-permission')]==['com.termux.permission.RUN_COMMAND'], 'only explicit Termux command permission')
query_list = root.findall('queries')
check(len(query_list)==1 and all(x.tag=='package' for x in query_list[0])
      and [x.get(ns+'name') for x in query_list[0]]==[
          'br.art.code.meucronogramacapilar','com.machiav3lli.fdroid',
          'com.looker.droidify','com.fugo.wowsolitaire','com.openai.chatgpt','dev.bikram.obtainx','com.termux'],
      'exact MCC/Neo Store/Droid-ify/Word Master/ChatGPT/ObtainX visibility only')
check(app.get(ns+'allowBackup')=='false' and app.get(ns+'name')=='.HookLabApp', 'lifecycle application')
act = app.findall('activity')
check(len(act)==3 and
      {a.get(ns+'name') for a in act}=={'.ui.MainActivity','.mcc.MccControlActivity','.termux.TermuxBridgeActivity'}
      and all(a.get(ns+'exported')=='true' for a in act)
      and len(act[0].findall('intent-filter'))==1
      and len(act[1].findall('intent-filter'))==0
      and len(act[2].findall('intent-filter'))==0,
      'one launcher and two explicit-only activities')
receivers=app.findall('receiver')
check(len(receivers)==1 and receivers[0].get(ns+'name')=='.termux.TermuxSessionReceipt' and receivers[0].get(ns+'exported')=='false' and not receivers[0].findall('intent-filter'),'private Termux session callback only')
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
                                  'stateStore.writeVerbose(', 'environment.knowsPackage('))
      and all(term not in ui for term in ('getRemotePreferences(', 'getScope(', 'getApiVersion('))
      and 'DynamicColors.applyToActivityIfAvailable(this)' in ui
      and 'snapshot.eligibility(spec)' in ui
      and all(term in state_store for term in ('getRemotePreferences(TweakKeys.GROUP)',
             'getRemotePreferences(TweakDiagnostics.GROUP)', 'worker.execute(',
             'ticket != generation', 'expected == environment.currentService()',
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
check(all(t in ui for t in ('diagnosticsPage()', 'confirmSafetyLock(port,spec.id,lock)',
      'R.string.diagnostics_note', 'R.string.diagnostics_reset_confirm',
      's == service()', 'stateStore.writeSafetyLock(s, id, locked,'))
      and all(t in state_store for t in ('writeSafetyLock(UiEnvironment.Port expected, String id, boolean locked, Completion cb)',
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
      and 'new ProcessBuilder("su","-c",script)' in worker
      and 'WRITE_9_VERIFIED_' in worker and 'return "BALANCE_FILE_NOT_FOUND"' in worker,
      'manual root operation under HookLab UID with explicit failure truth')
check('checkedDataDir(verifiedDir)' in command,
      'source-checked data-directory input')
# MCC-R01-F01: the scanner now builds direct paths beneath a source-checked root.
# Normalize Java-escaped quotation marks ONLY for source verification; never
# change the command, its target package, its strict path scope or the write.
scanner_source = command.replace(chr(92) + chr(34), chr(34))
check(all(term in scanner_source for term in (
      'for dir in "$root/databases" "$root/files" "$root/app_flutter"; do',
      'for candidate in "$dir"/*-DiamondBalance; do',
      'scan_base "$data"',
      'if [ "$n" -eq 0 ]',
      'MCC_MULTIPLE_ACCOUNTS',
      'MCC_BALANCE_WRITE_VERIFIED',
      'MCC_BALANCE_CHANGED',
      'MCC_BALANCE_WRITE_FAILED',
      'am force-stop --user',
      'MCC_STORAGE_READY')),
      'strict bounded MCC-only balance discovery and verified manual write')
# Run executable root-free negative/positive fixtures in hosted CI BEFORE Gradle.
subprocess.run(['python3','-B',str(r/'scripts/check_mcc_r01.py')],check=True)
subprocess.run(['python3','-B',str(r/'scripts/check_hl_diag_r02.py')],check=True)
subprocess.run(['python3','-B',str(r/'scripts/check_hl_diag_r03.py')],check=True)
subprocess.run(['python3','-B',str(r/'scripts/check_hl_diag_r05.py')],check=True)
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

# DS-01: catch resource-namespace drift before hosted Gradle compilation.
flat_ui = read('app/src/main/java/com/mikeyphw/hooklab/ui/FlatUi.java')
check('MaterialColors.getColor(nav, androidx.appcompat.R.attr.colorPrimary)' in flat_ui
      and 'MaterialColors.getColor(nav, R.attr.colorPrimary)' not in flat_ui,
      'DS-01 AppCompat primary-color attribute resolves in hosted compile')

# UI-04 catalog: every registered stable ID must have a reviewed grouping;
# no UI-only view switch may call LSPosed preference or scope mutation.
group_source = read('app/src/main/java/com/mikeyphw/hooklab/core/HookGroupCatalog.java')
main_source = read('app/src/main/java/com/mikeyphw/hooklab/ui/MainActivity.java')
check(all(hook in group_source for hook in (
    'mcc.diamond.control', 'neostore.obtainx.manual', 'droidify.obtainx.manual',
    'wordmaster.night.filter', 'chatgpt.codeblock.copy.probe')),
    'UI-04 every reviewed registered hook grouped')
check('selectedHookId = entry.spec.id' in main_source
    and 'HookDetailRoute.hook(environment.all(), selectedHookId)' in main_source
    and 'return verified == null ? null : entryForId(verified.id);' in main_source
    and 'HookDetailRoute.hook(environment.all(), requested) != null' in main_source
    and (r/'app/src/test/java/com/mikeyphw/hooklab/core/HookDetailRouteTest.java').is_file(),
    'UI-04/UI-05 independent hook-ID routing and invalid-selection rejection')
check('appearance.edit().putInt(UI04_VIEW_KEY, mode).apply()' in main_source
    and 'appearance.edit().putString(UI04_QUERY_KEY, catalogQuery).apply()' in main_source
    and 'HookGroupCatalog.groups(environment.all())' in main_source,
    'UI-04 local group/app persistence and catalog discovery')
check((r/'app/src/test/java/com/mikeyphw/hooklab/core/HookGroupCatalogTest.java').is_file(),
    'UI-04 pure Java catalog invariants tested in hosted CI')

# UI-05: detail/group navigation must preserve per-hook identities and LSPosed truth.
route = read('app/src/main/java/com/mikeyphw/hooklab/core/HookDetailRoute.java')
main_ui05 = read('app/src/main/java/com/mikeyphw/hooklab/ui/MainActivity.java')
store_ui05 = read('app/src/main/java/com/mikeyphw/hooklab/ui/FrameworkStateStore.java')
check('HookDetailRoute.hook(environment.all(), selectedHookId)' in main_ui05
      and 'HookDetailRoute.group(HookGroupCatalog.groups(environment.all()), selectedGroupId)' in main_ui05
      and 'page == APP_DETAIL || page == APPS || page == GROUP_DETAIL ? NAV_APPS' in main_ui05,
      'UI-05 exact hook/group identity and Hooks nav')
check('group.packages' in main_ui05 and 'removeScope(expected, pkg)' in main_ui05
      and 'requestScope(expected, pkg)' in main_ui05
      and 'confirmSafetyLock(s, spec.id, !lock)' in main_ui05,
      'UI-05 independent per-target scope and per-hook safety control')
check('saved = prefs != null && prefs.edit().putBoolean(key, value).commit()' in store_ui05
      and 'prefs.getBoolean(key, !value) == value' in store_ui05
      and 'refresh(); // Re-read framework truth' in store_ui05,
      'UI-05 framework remote preference commit/readback source contract')
check('showGroup(HookGroupCatalog.Group group)' in main_ui05
      and 'goTo(GROUP_DETAIL); // Navigation only' in main_ui05
      and 'selectedHookId = entry.spec.id' in main_ui05
      and 'HookDetailRoute.hook(environment.all(), requested) != null' in main_ui05
      and 'new androidx.appcompat.app.AlertDialog.Builder(this)' in main_ui05,
      'UI-05 safe group navigation and preserved scope removal confirmation')
check('groupForHook' in route and 'return null;' in route
      and (r/'app/src/test/java/com/mikeyphw/hooklab/core/HookDetailRouteTest.java').is_file(),
      'UI-05 failed hook/group routes and same-package identity regression tests')

# DX-01: imported provenance with bounded, redacted-only events; no root/logcat
# collection or ordinary app permissions. SAF import/export and real UI integration.
dx=read('app/src/main/java/com/mikeyphw/hooklab/diagnostics/DiagnosticTimeline.java')
saf=read('app/src/main/java/com/mikeyphw/hooklab/diagnostics/DiagnosticSaf.java')
appdx=read('app/src/main/java/com/mikeyphw/hooklab/HookLabApp.java')
uidebug=read('app/src/main/java/com/mikeyphw/hooklab/ui/MainActivity.java')
check('MAX_BYTES=2*1024*1024' in dx and 'MAX_EVENTS=4000' in dx
      and 'DX01_IMPORT_EXCEEDS_LIMIT' in dx and 'raw third-party text' in dx
      and 'ASSOCIATED' in dx and 'UNCLASSIFIED' in dx,
      'DX-01 bounded non-authenticated redacted source timeline')
check('openInputStream(uri)' in saf and 'openOutputStream(uri,"w")' in saf
      and 'DiagnosticExport.writeEvents' in saf
      and 'ZipOutputStream' in read('app/src/main/java/com/mikeyphw/hooklab/diagnostics/DiagnosticExport.java')
      and 'events_sha256' in read('app/src/main/java/com/mikeyphw/hooklab/diagnostics/DiagnosticExport.java'),
      'DX-01 explicit SAF file import and redacted evidence export')
check('public com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline diagnostics()' in appdx
      and 'diagnosticsTimelinePage();' in uidebug
      and 'diagnosticImportLauncher.launch(' in uidebug
      and 'diagnosticExportLauncher.launch(' in uidebug,
      'DX-01 in-memory lifecycle and real Diagnostics screen integration')
check('READ_LOGS' not in uidebug and 'Runtime.getRuntime().exec' not in saf
      and (r/'app/src/test/java/com/mikeyphw/hooklab/diagnostics/DiagnosticTimelineTest.java').is_file(),
      'DX-01 no stealth/root collector; JVM provenance/privacy regression present')

# UI-06: all Home information is a read-only projection of registered hooks,
# framework state and imported (non-authenticated) diagnostic evidence.
home_model = read('app/src/main/java/com/mikeyphw/hooklab/ui/HomeDashboardModel.java')
home_activity = read('app/src/main/java/com/mikeyphw/hooklab/ui/MainActivity.java')
check('Set<String> ids=new HashSet<>()' in home_model
      and 'state.eligibility(spec)' in home_model
      and 'scoped=0;' in home_model and 'for(String pkg:packages)' in home_model
      and 'e.ordinal>latest.ordinal' in home_model,
      'UI-06 unique counts, unknown readiness and imported evidence projection')
check('HomeDashboardModel.project(' in home_activity
      and 'diagnosticsStore()' in home_activity
      and 'summary.latestImported == null' in home_activity
      and 'R.string.ui06_import_warning' in home_activity
      and 'goTo(DIAGNOSTICS)' in home_activity
      and 'goTo(APPS)' in home_activity,
      'UI-06 real Home routes and explicit imported-event provenance')
check('capturePage()' in home_activity and 'R.string.dx02_capture' in home_activity
      and (r/'app/src/test/java/com/mikeyphw/hooklab/ui/HomeDashboardModelTest.java').is_file(),
      'UI-06 no fictional capture route and standalone model regression tests')

# DX-02: bounded, explicit SAF import-only capture. Host runtime instrumentation,
# unreviewed probes and automatic scope/opt-in writes remain absent.
capture = read('app/src/main/java/com/mikeyphw/hooklab/diagnostics/CaptureSession.java')
capture_saf = read('app/src/main/java/com/mikeyphw/hooklab/diagnostics/CaptureSaf.java')
capture_app = read('app/src/main/java/com/mikeyphw/hooklab/HookLabApp.java')
capture_ui = read('app/src/main/java/com/mikeyphw/hooklab/ui/MainActivity.java')
check('MAX_DURATION_MS=10*60*1000L' in capture and 'MAX_MARKERS=64' in capture
      and 'expireIfNeeded' in capture and 'State.INTERRUPTED' in capture
      and 'captureApp().interruptedCapture()' in capture_ui,
      'DX-02 bounded stop, timeout, interruption, cancellation')
check('MarkerCode' in capture and 'REPRODUCE_ACTION' in capture_ui
      and 'OBSERVED_PROBLEM' in capture_ui and 'source.equals(e.source)' in capture
      and 'acceptedImportRanges' in capture and 'current.registerImport(' in capture_ui,
      'DX-02 fixed action markers and source/ordinal evidence attribution')
check('runtime_methods=NOT_ACCESSED; injection=DISABLED' in capture
      and 'researchOnly' in capture and 'environment.knowsPackage(pkg)' in capture_ui,
      'DX-02 metadata-only safe discovery; no implicit hook installation')
check('events_sha256' in capture and 'markers_sha256' in capture
      and 'ZipOutputStream' in capture_saf and '"content".equals(uri.getScheme())' in capture_saf
      and 'captureExportLauncher.launch(' in capture_ui,
      'DX-02 explicit SAF redacted capture export with content hashes')
check('getSharedPreferences("hooklab.dx02.capture", MODE_PRIVATE)' in capture_app
      and 'captureImportRunning()' in capture_ui
      and 'captureApp().closeCapture()' in capture_ui
      and (r/'app/src/test/java/com/mikeyphw/hooklab/diagnostics/CaptureSessionTest.java').is_file(),
      'DX-02 process loss and pending-import safety with Java tests')

# DX-03: five independent framework axes, exact hook attribution, no false
# execution; UI actions are navigation/copy only, not scope or opt-in writes.
health_model=read('app/src/main/java/com/mikeyphw/hooklab/ui/HookHealthModel.java')
health_ui=read('app/src/main/java/com/mikeyphw/hooklab/ui/MainActivity.java')
check('snapshot.inScope(spec.packageName)' in health_model
      and 'snapshot.flag(spec.id)' in health_model
      and 'flag.installedVersion' in health_model
      and 'snapshot.eligibility(spec)' in health_model
      and 'Axis.OPTED_OUT' in health_model,
      'DX-03 independent connection/scope/opt-in/lock/version model')
check('spec.id.equals(event.hookId)' in health_model
      and 'event.fileSha256' in health_ui
      and 'event.lineNumber' in health_ui
      and 'unrelatedErrors' in health_model
      and 'retainedDropped' in health_model,
      'DX-03 exact-ID source/error attribution and collector-gap disclosure')
check('v->goTo(CAPTURE)' in health_ui
      and 'openHookHealth(TweakSpec spec)' in health_ui
      and 'goTo(HEALTH_REPORT)' in health_ui
      and 'showApp(entry)' in health_ui
      and 'copyDiagnosticText(redacted)' in health_ui,
      'DX-03 real safe setup/capture/evidence navigation and redacted copy')
check('public boolean executionVerified(){return false;}' in health_model
      and 'runtime_execution_verified' in health_model
      and 'imported_evidence_authenticated' in health_model
      and 'NO_EXACT_HOOK_ASSOCIATION' in health_model
      and (r/'app/src/test/java/com/mikeyphw/hooklab/ui/HookHealthModelTest.java').is_file(),
      'DX-03 no claimed ACTIVE status and Java negative evidence regressions')

# UI-07: unified native settings/scope/help/MCC, truthful identities and
# single-flight manual root. Existing remote store/permissions remain unchanged.
ui07_scope=read('app/src/main/java/com/mikeyphw/hooklab/ui/Ui07ScopeModel.java')
ui07_main=read('app/src/main/java/com/mikeyphw/hooklab/ui/MainActivity.java')
ui07_mcc=read('app/src/main/java/com/mikeyphw/hooklab/mcc/MccControlActivity.java')
ui07_work=read('app/src/main/java/com/mikeyphw/hooklab/mcc/MccManualOperation.java')
ui07_identity=read('app/src/main/java/com/mikeyphw/hooklab/ui/Ui07Identity.java')
check('Ui07ScopeModel.project(environment.all()' in ui07_main
      and 'ready(expected),scopedPackages(expected),pendingScope' in ui07_main
      and 'row.canRequest()' in ui07_main and 'row.canRemove()' in ui07_main
      and 'removeScope(expected,row.packageName)' in ui07_main
      and 'State.SCOPE_UNKNOWN' in ui07_scope and 'State.PENDING' in ui07_scope,
      'UI-07 P01 framework-authoritative deduped scope with permission denial')
check('MccManualOperation.shared().start(readOnly,work)' in ui07_mcc
      and 'setPositiveButton("Set to 9"' in ui07_mcc
      and 'MccRefillWorker.inspect(appContext)' in ui07_mcc
      and 'MccRefillWorker.setNine(appContext)' in ui07_mcc
      and 'if(snapshot.stage==Stage.RUNNING)return false;' in ui07_work
      and 'removeListener(operationObserver)' in ui07_mcc,
      'UI-07 P02 MCC manual confirmation and cross-rotation single-flight worker')
check('getPackageInfo(' in ui07_main and 'GET_SIGNING_CERTIFICATES' in ui07_main
      and 'getApkContentsSigners()' in ui07_main and 'Ui07Identity.signerFingerprint' in ui07_main
      and 'getString(R.string.ui07_privacy_note)' in ui07_main
      and 'getString(R.string.ui07_help_logs_body)' in ui07_main
      and 'MessageDigest.getInstance("SHA-256")' in ui07_identity,
      'UI-07 P03 runtime app identity, signing fingerprint, privacy and native Help')
check('case HELP: helpPage(); break;' in ui07_main
      and 'out.putInt("page", page)' in ui07_main
      and 'Math.min(EVENT_REPORT, state.getInt("page"))' in ui07_main
      and 'out.putInt("helpReturnPage", helpReturnPage)' in ui07_main
      and 'status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE)' in ui07_mcc
      and (r/'app/src/test/java/com/mikeyphw/hooklab/ui/Ui07ScopeModelTest.java').is_file()
      and (r/'app/src/test/java/com/mikeyphw/hooklab/ui/Ui07IdentityTest.java').is_file()
      and (r/'app/src/test/java/com/mikeyphw/hooklab/mcc/MccManualOperationTest.java').is_file(),
      'UI-07 P04 restoration, accessible manual status and Java regression tests')

# UI-08: unified accessible sections, safe legacy ID migration, no dead apps path.
ui08=read('app/src/main/java/com/mikeyphw/hooklab/ui/MainActivity.java')
flat08=read('app/src/main/java/com/mikeyphw/hooklab/ui/FlatUi.java')
legacy08=read('app/src/main/java/com/mikeyphw/hooklab/ui/Ui08LegacySelection.java')
quality08=read('app/src/main/java/com/mikeyphw/hooklab/ui/Ui08QualityPolicy.java')
workflow08=read('.github/workflows/android.yml')
check('private void appCard(' not in ui08 and 'hooksCatalog();' in ui08
      and 'FlatUi.sectionHeading(titleView);' in ui08,
      'UI-08 P01 single flat native catalog, accessible section headings')
check('Ui08LegacySelection.resolveUnique(environment.all(),oldPackage)' in ui08
      and 'if (unique != null) return null;' in legacy08
      and (r/'app/src/test/java/com/mikeyphw/hooklab/ui/Ui08LegacySelectionTest.java').is_file(),
      'UI-08 P02 deterministic unambiguous legacy selection only')
check('MIN_TOUCH_DP=48' in quality08 and 'contrast(int foreground,int background)' in quality08
      and 'setAccessibilityHeading(true)' in flat08
      and 'python3 -B scripts/check_ui08_qa.py' in workflow08
      and (r/'app/src/test/java/com/mikeyphw/hooklab/ui/Ui08QualityPolicyTest.java').is_file(),
      'UI-08 P03 accessible touch/contrast and hosted regression source')
check((r/'docs/qa/hooklab-flat-ux-acceptance.md').is_file()
      and 'RELEASE_NOT_AUTHORIZED' in read('docs/qa/hooklab-flat-ux-acceptance.md')
      and 'DEVICE_NOT_RUN' in read('docs/qa/hooklab-flat-ux-acceptance.md'),
      'UI-08 P04/P05 release/device stay explicitly unqualified in acceptance matrix')
