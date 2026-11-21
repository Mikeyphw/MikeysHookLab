import hashlib, pathlib, re, subprocess, xml.etree.ElementTree as ET
r=pathlib.Path(__file__).resolve().parents[1]
def read(p): return (r/p).read_text()
def check(ok,name):
    if not ok: raise RuntimeError(name)
    print('PASS',name,flush=True)
for f in (r/'app/src/main').rglob('*.xml'): ET.parse(f)
check(True,'Android XML well-formed')
manifest=ET.parse(r/'app/src/main/AndroidManifest.xml').getroot()
check(not manifest.findall('uses-permission') and not manifest.findall('queries'),'no permissions or broad app queries')
app=manifest.find('application'); ns='{http://schemas.android.com/apk/res/android}'
check(app.get(ns+'allowBackup')=='false' and len(app.findall('activity'))==1 and len(app)==1,'minimal exported surface')
wf=read('.github/workflows/android.yml')
check('setup-android@v4.0.4' in wf and "packages: 'platform-tools'" in wf and "cmdline-tools-version: '15859902'" in wf and 'sdkmanager tools' not in wf,'retired SDK tools regression guard')
check(all(x in wf for x in (':app:assembleDebug',':app:testDebugUnitTest',':app:lintDebug','apksigner','sha256sum','source-commit.txt')),'hosted acceptance stages')
check("compileOnly 'io.github.libxposed:api:102.0.0'" in read('app/build.gradle'),'compile-only modern API')
meta=r/'app/src/main/resources/META-INF/xposed'
check((meta/'java_init.list').read_text().strip()=='com.mikeyphw.hooklab.hook.HookLabModule','entry identity')
check((meta/'module.prop').read_text().splitlines()==['minApiVersion=102','targetApiVersion=102','staticScope=false','exceptionMode=protective','autoHotReload=false'],'module properties')
check(not (meta/'scope.list').exists(),'no preselected scope')
entry=read('app/src/main/java/com/mikeyphw/hooklab/hook/HookLabModule.java')
check(re.search(r'extends XposedModule\s*\{\s*(?://[^\n]*\n\s*)*\}',entry) is not None,'inert entry')
check('return Collections.emptyList();' in read('app/src/main/java/com/mikeyphw/hooklab/core/TweakCatalog.java'),'empty registry')
jar=r/'gradle/wrapper/gradle-wrapper.jar'
check(jar.is_file() and hashlib.sha256(jar.read_bytes()).hexdigest()=='81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f','pinned Gradle wrapper JAR')
check('distributionSha256Sum=20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78' in read('gradle/wrapper/gradle-wrapper.properties'),'pinned Gradle distribution')
subprocess.run(['bash','-n',str(r/'gradlew')],check=True)
print('HOOKLAB_M0_SOURCE_CONTRACT_PASS')
