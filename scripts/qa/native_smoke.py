#!/usr/bin/env python3
"""Source-bound baseline on a disposable hosted Android API36 emulator only."""
from __future__ import annotations
import argparse, hashlib, json, os, re, subprocess, sys, time
from pathlib import Path
PKG='com.mikeyphw.hooklab'; SERIAL='emulator-5554'
ROUTES={'home':201,'hooks':202,'diagnostics':203}
TEST='com.mikeyphw.hooklab.qa.NativeBaselineTest#realActivityOfflineNavigation'
FIXTURE='com.mikeyphw.hooklab.qa.DiagnosticFixtureTest#importedFixtureProjectionAndFilter'
FRAMEWORK_TEST='com.mikeyphw.hooklab.ui.FrameworkFixtureTest#connectedCommandsStaleCallbackAndClock'
TESTS=[TEST,TEST,FIXTURE,FRAMEWORK_TEST]
FRAMEWORK_EVIDENCE={'schema':'hooklab.framework.fixture.v1','synthetic':True,'runtime_qualified':False,'real_activity':True,'assertions':['connected_actual_activity','confirmed_write_readback','denied_write_no_optimism','scope_denial','stale_callback_rejected','scope_approval_readback','same_package_independent_hooks','injected_clock_capture_marker','no_real_framework']}
FIXTURE_EVIDENCE={'schema':'hooklab.diagnostics.fixture.v1','synthetic':True,'runtime_qualified':False,'real_activity':True,'framework_disconnected':True,'assertions':['three_normalized_events','actual_timeline_projection','filter_one_of_three','raw_marker_not_rendered','store_unchanged','no_framework_service']}

def require(ok,msg):
    if not ok:raise ValueError(msg)
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def adb(*args):return subprocess.check_output(['adb','-s',SERIAL,*args],timeout=30).strip().replace(b'\r',b'')
def txt(*args):return adb(*args).decode('utf-8')
def raw(*args):return subprocess.check_output(['adb','-s',SERIAL,*args],timeout=30)
def git(*args):return subprocess.check_output(['git',*args],text=True,timeout=25).strip()
def guard():
    require(os.getenv('GITHUB_ACTIONS')=='true' and os.getenv('RUNNER_OS')=='Linux','HOSTED_ONLY')
    devices=subprocess.check_output(['adb','devices'],text=True,timeout=20).splitlines();require([x.split() for x in devices[1:] if x.strip()]==[[SERIAL,'device']],'unexpected ADB device inventory')
    require(txt('shell','getprop','ro.kernel.qemu')=='1' and txt('shell','getprop','ro.build.version.sdk')=='36','API36_EMULATOR_REQUIRED')
    require(txt('shell','getprop','sys.boot_completed')=='1','EMULATOR_NOT_BOOTED')
    # Adapted from Cinderbox R48 offline gate: ConnectivityService and setting
    # readback, Wi-Fi independent, and explicit absence of default network.
    adb('shell','cmd','connectivity','airplane-mode','enable');adb('shell','svc','wifi','disable')
    require(txt('shell','cmd','connectivity','airplane-mode')=='enabled','airplane mode not enabled')
    require(txt('shell','settings','get','global','airplane_mode_on')=='1' and txt('shell','settings','get','global','wifi_on')=='0','radio setting mismatch')
    for _ in range(12):
        m=re.search(r'(?m)^\s*Active default network:\s*(\S+)\s*$',txt('shell','dumpsys','connectivity'))
        require(m is not None,'default network unobservable')
        if m.group(1).lower() in ('none','null'):return
        time.sleep(1)
    raise ValueError('active default network')

def install_apk(apk):
    # adb may emit its install-mode heading before the final Success line.
    result=subprocess.run(['adb','-s',SERIAL,'install','--no-incremental','-t',str(apk)],capture_output=True,text=True,timeout=180)
    require(result.returncode==0,'APK installation command failed rc='+str(result.returncode))
    output=result.stdout.strip().splitlines()
    require(not result.stderr.strip() and output in (['Success'],['Performing Streamed Install','Success'],['Performing Push Install','Success']),'APK installation did not report unambiguous success')

def installed_path(package):
    require(package in (PKG, PKG+'.test'),'unexpected package identity')
    result=subprocess.run(['adb','-s',SERIAL,'shell','-T','pm','path','--user','0',package],capture_output=True,text=True,timeout=30)
    require(result.returncode==0 and not result.stderr.strip(),'installed package path query failed '+package)
    path=result.stdout.strip()
    require(re.fullmatch(r'package:/data/app/(?:~~[A-Za-z0-9_+=-]+/)?'+re.escape(package)+r'-[A-Za-z0-9_+=-]+/base\.apk',path) is not None,'unexpected installed APK path or splits '+package)
    return path.removeprefix('package:')

def installed_digest(package, expected):
    # Hash bytes on the emulator; do not hash exec-out diagnostics as APK bytes.
    # Modern adb shell protocol preserves the remote exit status and separates stderr.
    require(re.fullmatch('[a-f0-9]{64}',expected) is not None,'invalid expected APK digest')
    path=installed_path(package)
    def checksum(prefix):
        return subprocess.run(['adb','-s',SERIAL,'shell','-T',*prefix,'sha256sum',path],capture_output=True,text=True,timeout=90)
    result=checksum([]);method='shell'
    if result.returncode!=0 and re.search(r'permission denied|operation not permitted',result.stderr+'\n'+result.stdout,re.I):
        # Debug/test APK owner may read its own installed APK when shell cannot.
        # No root, no chmod, and never retry a successfully computed mismatch.
        result=checksum(['run-as',package]);method='run-as'
    require(result.returncode==0 and not result.stderr.strip(),'installed APK hash command failed '+package+' method='+method+' rc='+str(result.returncode))
    match=re.fullmatch(r'([a-fA-F0-9]{64})[ \t]+\*?'+re.escape(path),result.stdout.strip())
    require(match is not None,'invalid installed APK hash response '+package+' method='+method)
    observed=match.group(1).lower()
    require(installed_path(package)==path,'installed APK path changed '+package)
    require(observed==expected,'installed APK hash mismatch '+package+' expected='+expected+' observed='+observed+' method='+method)
    print('INSTALLED_APK_VERIFIED '+package+' sha256='+observed+' method='+method,flush=True)
    return observed

def one(pattern):
    paths=list(Path('.').glob(pattern));require(len(paths)==1 and paths[0].is_file(),'APK missing/ambiguous');return paths[0]
def verify(out,expected):
    from PIL import Image
    require(out.is_dir() and not out.is_symlink(),'native evidence directory missing/unsafe')
    receipt_path=out/'receipt.json';require(receipt_path.is_file() and not receipt_path.is_symlink() and receipt_path.stat().st_size<65536,'native receipt missing/unsafe')
    receipt=json.loads(receipt_path.read_text());require(all(receipt.get(k)==v for k,v in expected.items()),'native receipt mismatch')
    require(receipt.get('status')=='BASELINE_PASS_NOT_VISUAL_APPROVAL' and receipt.get('tests')==TESTS,'test not qualified')
    require(receipt.get('app_apk_sha256')==receipt.get('installed_apk_sha256') and receipt.get('android_test_apk_sha256')==receipt.get('installed_test_apk_sha256'),'installed APK differs')
    for k in ('app_apk_sha256','android_test_apk_sha256','debug_certificate_sha256','installed_apk_sha256','installed_test_apk_sha256'):
        require(re.fullmatch('[a-f0-9]{64}',receipt.get(k,'')),'invalid identity '+k)
    validate_receipt_files(out,receipt)
    from visual import metrics,strict_regression,native_controls
    tolerances={'bounds_dp':2,'font_sp':1,'rgb_delta':0,'native_ssim_min':.99,'native_changed_pixel_max':.01}
    repeat={}
    for name in ROUTES:
        a=out/(name+'.png');b=out/(name+'-repeat.png');g=json.loads((out/(name+'.json')).read_text());other=json.loads((out/(name+'-repeat.json')).read_text())
        require(g['insets_px']==other['insets_px'],'system insets changed')
        left,top,right,bottom=g['insets_px'];area=(left,top,780-right,1688-bottom)
        with Image.open(a) as im,Image.open(b) as im2:
            for img in (im,im2):require(img.size==(780,1688) and img.format=='PNG' and any(lo!=hi for lo,hi in img.convert('RGB').getextrema()),'wrong/blank screenshot')
            repeat[name]=strict_regression(native_controls(g,name),native_controls(other,name),im.crop(area),im2.crop(area),tolerances)
    require(repeat==receipt['repeat_calibration'],'native repeat calibration drift')
    return receipt

def validate_geometry(g,route):
    require(set(g)=={'route','selected_menu','real_activity','disconnected','density','width_dp','font_scale','insets_px','views','semantic_controls'},'unexpected native hierarchy metadata')
    require(g['route']==route and g['selected_menu']==ROUTES[route] and g['real_activity'] is True and g['disconnected'] is True and g['width_dp']==390 and g['density']==2 and g['font_scale']==1 and 10<len(g['views'])<=4000,'wrong native geometry')
    insets=g['insets_px'];require(isinstance(insets,list) and len(insets)==4 and all(type(x) is int and 0<=x<=200 for x in insets),'invalid system insets')
    for row in g['views']:
        keys={'class','clickable','enabled','focusable','heading','bounds_px','width_dp','height_dp'}
        require(set(row) in (keys,keys|{'text_size_px','text_color'}),'non-allowlisted hierarchy field')
        require(re.fullmatch('[A-Za-z0-9_$]+',row['class']) and all(type(row[k]) is bool for k in ('clickable','enabled','focusable','heading')),'invalid hierarchy identity')
        b=row['bounds_px'];require(len(b)==4 and all(type(x) is int for x in b) and 0<=b[0]<b[2]<=780 and 0<=b[1]<b[3]<=1688,'invalid visible native bounds')
    from visual import native_controls
    native_controls(g,route)

def validate_receipt_files(out,receipt):
    from release_isolation import verify as verify_release
    expected_files={'receipt.json','fixture.json','framework-fixture.json','release-isolation.json','environment.json'}|{n+s for n in ROUTES for s in ('.png','.json','-repeat.png','-repeat.json')}
    require(set(p.name for p in out.iterdir())==expected_files and all(p.is_file() and not p.is_symlink() for p in out.iterdir()),'unexpected native evidence files')
    for name,expected in [('fixture',FIXTURE_EVIDENCE),('framework-fixture',FRAMEWORK_EVIDENCE)]:
        p=out/(name+'.json');require(p.stat().st_size<=4096 and json.loads(p.read_text())==expected and receipt.get(name.replace('-','_')+'_sha256')==digest(p),'fixture evidence mismatch '+name)
    isolation=out/'release-isolation.json';require(digest(isolation)==receipt['release_isolation_sha256'],'release isolation hash drift');verify_release(json.loads(isolation.read_text()),receipt['app_apk_sha256'],receipt['android_test_apk_sha256'])
    env=out/'environment.json';e=json.loads(env.read_text());require(digest(env)==receipt['environment_sha256'] and set(e)=={'api','abi','serial','qemu','fingerprint','emulator_version','platform_tools_revision','system_image_revision','width_px','height_px','density_dpi','font_scale','offline'} and e['api']=='36' and e['abi']=='x86_64' and e['qemu']=='1' and e['serial']==SERIAL and e['offline'] is True and [e['width_px'],e['height_px'],e['density_dpi'],e['font_scale']]==[780,1688,320,1.0] and all(isinstance(e[k],str) and 0<len(e[k])<=256 for k in ('fingerprint','emulator_version','platform_tools_revision','system_image_revision')),'environment evidence drift')
    for suffix,field in [('', 'screenshots'),('-repeat','repeat_screenshots')]:
        seen=set();records={}
        for route in ROUTES:
            image=out/(route+suffix+'.png');geo=out/(route+suffix+'.json');require(0<image.stat().st_size<=12*1024*1024 and 0<geo.stat().st_size<=2*1024*1024,'invalid evidence size');h=digest(image);require(h not in seen,'duplicate screenshot');seen.add(h)
            validate_geometry(json.loads(geo.read_text()),route);records[route]={'png':h,'geometry':digest(geo)}
        require(records==receipt[field],'native screenshot hash drift')
    require(set(receipt['repeat_calibration'])==set(ROUTES),'repeat calibration missing')
    for result in receipt['repeat_calibration'].values():require(result['ssim']>=.99 and result['changed_pixel_ratio']<=.01,'native repeat not within strict thresholds')

def parse_instrumentation(output,test_id):
    require('OK (1 test)' in output and 'INSTRUMENTATION_CODE: -1' in output and not any(x in output for x in ('FAILURES!!!','INSTRUMENTATION_FAILED','INSTRUMENTATION_ABORTED')),'instrumentation terminal failure')
    klass,method=test_id.split('#');records=[];current={}
    for line in output.splitlines():
        m=re.fullmatch(r'INSTRUMENTATION_STATUS: (class|test)=(.*)',line.strip())
        if m:current[m.group(1)]=m.group(2)
        m=re.fullmatch(r'INSTRUMENTATION_STATUS_CODE: (-?\d+)',line.strip())
        if m:records.append((int(m.group(1)),current.get('class'),current.get('test')));current={}
    require(records==[(1,klass,method),(0,klass,method)],'instrumentation exact test roster failed')

def environment():
    def revision(path):
        line=next((x.split('=',1)[1].strip() for x in path.read_text().splitlines() if x.split('=',1)[0].strip()=='Pkg.Revision' and '=' in x),'');require(bool(re.fullmatch(r'[0-9.]+(?:[-a-zA-Z0-9. ]*)?',line)),'invalid SDK revision');return line
    sdk=Path(os.environ['ANDROID_HOME']);emulator=subprocess.check_output([str(sdk/'emulator/emulator'),'-version'],text=True,stderr=subprocess.STDOUT,timeout=30).splitlines();version=next((x.strip()[:256] for x in emulator if 'emulator version' in x.lower()),'');require(bool(version),'emulator revision missing')
    return {'api':txt('shell','getprop','ro.build.version.sdk'),'abi':txt('shell','getprop','ro.product.cpu.abi'),'serial':SERIAL,'qemu':txt('shell','getprop','ro.kernel.qemu'),'fingerprint':txt('shell','getprop','ro.build.fingerprint'),'emulator_version':version,'platform_tools_revision':revision(sdk/'platform-tools/source.properties'),'system_image_revision':revision(sdk/'system-images/android-36/google_apis/x86_64/source.properties'),'width_px':780,'height_px':1688,'density_dpi':320,'font_scale':1.0,'offline':True}

def expected_identity():
    source=os.environ['SOURCE_SHA'];require(re.fullmatch('[0-9a-f]{40}',source) and git('rev-parse','HEAD')==source,'source drift')
    require(os.environ.get('GITHUB_REPOSITORY')=='Mikeyphw/MikeysHookLab','wrong repository')
    require(all(re.fullmatch('[1-9][0-9]*',os.environ.get(k,'')) for k in ('GITHUB_RUN_ID','GITHUB_RUN_ATTEMPT')),'invalid run identity')
    return {'schema':'hooklab.native.smoke.v1','source_sha':source,'source_tree':git('rev-parse','HEAD^{tree}'),'run_id':os.environ['GITHUB_RUN_ID'],'run_attempt':os.environ['GITHUB_RUN_ATTEMPT'],'repository':'Mikeyphw/MikeysHookLab','hosted_emulator_only':True,'physical_device_qualified':False,'release_qualified':False,'visual_approved':False}

def run(out):
    ident=expected_identity();require(not out.exists(),'fresh output required');out.mkdir(parents=True);(out/'receipt.json').write_text(json.dumps({**ident,'status':'NOT_RUN'}));guard()
    for name in (PKG,PKG+'.test'):
        res=subprocess.run(['adb','-s',SERIAL,'shell','pm','path',name],capture_output=True,text=True,timeout=20);require(not res.stdout.strip(),'preexisting app refused')
    app=one('app/build/outputs/apk/debug/*.apk');test=one('app/build/outputs/apk/androidTest/debug/*.apk')
    cert=subprocess.check_output([str(Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0/apksigner'),'verify','--print-certs',str(app)],text=True,timeout=30)
    m=re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})',cert);require(m is not None,'debug signer unavailable')
    for apk in (app,test):install_apk(apk)
    ah,th=digest(app),digest(test)
    installed_digest(PKG,ah);installed_digest(PKG+'.test',th)
    adb('shell','wm','size','780x1688');adb('shell','wm','density','320');adb('shell','settings','put','system','font_scale','1.0');adb('shell','cmd','uimode','night','yes')
    for name in ('window_animation_scale','transition_animation_scale','animator_duration_scale'):adb('shell','settings','put','global',name,'0')
    require(not any(os.environ.get(k) for k in ('HOOKLAB_SIGNING_KEYSTORE_PATH','HOOKLAB_SIGNING_STORE_PASSWORD','HOOKLAB_SIGNING_KEY_ALIAS','HOOKLAB_SIGNING_KEY_PASSWORD')),'release credentials refused in UI workflow')
    from release_isolation import check as release_check
    release=one('app/build/outputs/apk/release/*.apk');isolation=release_check(app,release,test)
    def dump(path,value):path.write_text(json.dumps(value,indent=2,sort_keys=True)+'\n')
    dump(out/'release-isolation.json',isolation);dump(out/'environment.json',environment())
    def instrument(test_id):
        result=subprocess.run(['adb','-s',SERIAL,'shell','am','instrument','-w','-r','-e','class',test_id,PKG+'.test/androidx.test.runner.AndroidJUnitRunner'],capture_output=True,text=True,timeout=240)
        try:
            require(result.returncode==0,'instrumentation child failed '+test_id);parse_instrumentation(result.stdout,test_id)
        except ValueError:
            print('INSTRUMENTATION_FAILED_TEST '+test_id+' rc='+str(result.returncode),file=sys.stderr,flush=True)
            for line in result.stdout.splitlines():
                if re.fullmatch(r'INSTRUMENTATION_(?:STATUS_CODE|CODE): -?\d+',line.strip()) or re.fullmatch(r'INSTRUMENTATION_STATUS: (?:class|test)=[A-Za-z0-9_.$]+',line.strip()):
                    print(line.strip(),file=sys.stderr,flush=True)
            raise
        print('INSTRUMENTATION_PASS',test_id,flush=True)
    def capture(suffix):
        records={}
        for name in ROUTES:
            for ext in ('.json','.png'):
                p=out/(name+suffix+ext);p.write_bytes(raw('exec-out','run-as',PKG,'cat','files/uxr00-native-smoke/'+name+ext))
            records[name]={'png':digest(out/(name+suffix+'.png')),'geometry':digest(out/(name+suffix+'.json'))}
        return records
    instrument(TEST);records=capture('');instrument(TEST);repeat_records=capture('-repeat')
    from PIL import Image
    from visual import strict_regression,native_controls
    repeat={};tolerances={'bounds_dp':2,'font_sp':1,'rgb_delta':0,'native_ssim_min':.99,'native_changed_pixel_max':.01}
    for name in ROUTES:
        g=json.loads((out/(name+'.json')).read_text());other=json.loads((out/(name+'-repeat.json')).read_text());require(g['insets_px']==other['insets_px'],'system insets changed');l,t,r,b=g['insets_px'];area=(l,t,780-r,1688-b)
        with Image.open(out/(name+'.png')) as a,Image.open(out/(name+'-repeat.png')) as b:
            repeat[name]=strict_regression(native_controls(g,name),native_controls(other,name),a.crop(area),b.crop(area),tolerances)
    instrument(FIXTURE);instrument(FRAMEWORK_TEST)
    for name in ('fixture','framework-fixture'):
        f=out/(name+'.json');f.write_bytes(raw('exec-out','run-as',PKG,'cat','files/uxr00-native-smoke/'+f.name))
    receipt={**ident,'status':'BASELINE_PASS_NOT_VISUAL_APPROVAL','tests':TESTS,'fixture_sha256':digest(out/'fixture.json'),'framework_fixture_sha256':digest(out/'framework-fixture.json'),'release_isolation_sha256':digest(out/'release-isolation.json'),'environment_sha256':digest(out/'environment.json'),'app_apk_sha256':ah,'android_test_apk_sha256':th,'installed_apk_sha256':installed_digest(PKG,ah),'installed_test_apk_sha256':installed_digest(PKG+'.test',th),'debug_certificate_sha256':m.group(1).lower(),'screenshots':records,'repeat_screenshots':repeat_records,'repeat_calibration':repeat}
    dump(out/'receipt.json',receipt);verify(out,ident);print('NATIVE_BASELINE_PASS_NOT_VISUAL_APPROVAL')

def main():
    p=argparse.ArgumentParser();p.add_argument('command',choices=['run','verify']);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    try:
        if a.command=='run':run(a.out)
        else:verify(a.out,expected_identity());print('NATIVE_BASELINE_INDEPENDENT_VERIFY_PASS')
        return 0
    except (OSError,ValueError,KeyError,TypeError,subprocess.SubprocessError) as e:print('NATIVE_SMOKE_REFUSED',e,file=sys.stderr);return 2
if __name__=='__main__':raise SystemExit(main())
