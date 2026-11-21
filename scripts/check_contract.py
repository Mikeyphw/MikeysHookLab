#!/usr/bin/env python3
"""M1-M3 source guard; hosted build, lint and APK inspection remain separate gates."""
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
check(not root.findall('uses-permission') and not root.findall('queries'), 'no app permissions or broad queries')
check(app.get(ns+'allowBackup')=='false' and app.get(ns+'name')=='.HookLabApp', 'lifecycle application')
act = app.findall('activity')
check(len(act)==1 and act[0].get(ns+'name')=='.ui.MainActivity'
      and act[0].get(ns+'exported')=='true', 'single launcher')
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
      and not (meta/'scope.list').exists(), 'entry identity and no default app scope')
check((meta/'module.prop').read_text().splitlines()==[
      'minApiVersion=102','targetApiVersion=102','staticScope=false',
      'exceptionMode=protective','autoHotReload=false'], 'modern API 102 contract')
module = read('app/src/main/java/com/mikeyphw/hooklab/hook/HookLabModule.java')
check(all(term in module for term in ('extends XposedModule', 'onModuleLoaded(', 'onPackageLoaded(',
      'param.isFirstPackage()', 'getDeclaredMethod("attach", Context.class)',
      'getPackageInfo(pkg, 0).getLongVersionCode()',
      'getRemotePreferences(TweakKeys.GROUP)', 'settings.getBoolean(TweakKeys.enabled(id), false)')),
      'late verified process/version/opt-in dispatcher')
check('Collections.<TweakEngine.Entry<HookContext>>emptyList()' in read(
      'app/src/main/java/com/mikeyphw/hooklab/core/TweakCatalog.java'), 'empty shipping registry')
ui = read('app/src/main/java/com/mikeyphw/hooklab/ui/MainActivity.java')
check(all(term in ui for term in ('requestScope(', 'removeScope(', 'getRemotePreferences(TweakKeys.GROUP)',
                                  '.commit()', 'runOnUiThread(', 'TweakCatalog.knowsPackage(')),
      'framework-backed UI with explicit scope and writes')
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
print('HOOKLAB_M123_SOURCE_CONTRACT_PASS', flush=True)
