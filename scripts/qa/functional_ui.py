"""Run reviewed production Activity fixtures independently of screenshot comparison."""
import argparse,json,os,re,subprocess,sys
from pathlib import Path
import native_smoke as n
import adaptive_matrix as a
from qualification import FUNCTIONS
METHODS={'diagnostics':n.FIXTURE,'framework':n.FRAMEWORK_TEST,'termux':n.TERMUX_TEST}

def selected(raw):
    names=raw.split(',');n.require(names and len(set(names))==len(names) and set(names)<=set(FUNCTIONS),'invalid functional roster');return names

def run(out,apks,names):
    ident=n.expected_identity();ident['schema']='hooklab.functional.receipt.v1';roster=[METHODS[k] for k in names]
    n.require(not out.exists(),'fresh output required');out.mkdir(parents=True)
    (out/'receipt.json').write_text(json.dumps({**ident,'status':'IN_PROGRESS','completed_tests':[]}))
    n.guard();n.configure_renderer();build=a.shared_apks(apks,ident)
    n.require(not any(os.environ.get(k) for k in ('HOOKLAB_SIGNING_KEYSTORE_PATH','HOOKLAB_SIGNING_STORE_PASSWORD','HOOKLAB_SIGNING_KEY_ALIAS','HOOKLAB_SIGNING_KEY_PASSWORD')),'release credentials refused')
    for name in (n.PKG,n.PKG+'.test'):
        res=subprocess.run(['adb','-s',n.SERIAL,'shell','pm','path',name],capture_output=True,text=True,timeout=20);n.require(not res.stdout.strip(),'preexisting app refused')
    for apk in (apks/'app.apk',apks/'android-test.apk'):n.install_apk(apk)
    ah=n.installed_digest(n.PKG,build['apks']['app']['sha256']);th=n.installed_digest(n.PKG+'.test',build['apks']['test']['sha256'])
    n.adb('shell','wm','size','780x1688');n.adb('shell','wm','density','320');n.adb('shell','settings','put','system','font_scale','1.0');n.adb('shell','cmd','uimode','night','yes')
    for field in ('window_animation_scale','transition_animation_scale','animator_duration_scale'):n.adb('shell','settings','put','global',field,'0')
    completed=[]
    for method in roster:n.run_instrumentation(out,ident,method,completed,roster=roster)
    files={}
    for key,filename,expect in [('diagnostics','fixture.json',n.FIXTURE_EVIDENCE),('framework','framework-fixture.json',n.FRAMEWORK_EVIDENCE)]:
        if key in names:
            p=out/filename;p.write_bytes(n.raw('exec-out','run-as',n.PKG,'cat','files/uxr00-native-smoke/'+filename));n.require(json.loads(p.read_text())==expect,'fixture evidence drift');files[filename]=n.digest(p)
    result={**ident,'status':'PASS_FUNCTIONAL_NOT_VISUAL','completed_tests':completed,'selected':names,'apk_build':build,'installed_app_sha256':ah,'installed_test_sha256':th,'files':files}
    (out/'receipt.json').write_text(json.dumps(result,sort_keys=True)+'\n');verify(out,apks,names,ident)

def verify(out,apks,names,ident):
    build=a.shared_apks(apks,ident);p=out/'receipt.json';n.require(out.is_dir() and not out.is_symlink() and p.is_file() and not p.is_symlink() and p.stat().st_size<65536,'unsafe functional evidence');r=json.loads(p.read_text())
    names_files={'fixture.json':n.FIXTURE_EVIDENCE} if 'diagnostics' in names else {}
    if 'framework' in names:names_files['framework-fixture.json']=n.FRAMEWORK_EVIDENCE
    n.require(set(x.name for x in out.iterdir())=={'receipt.json'}|set(names_files),'functional evidence roster drift')
    for filename,expect in names_files.items():
        q=out/filename;n.require(q.is_file() and not q.is_symlink() and q.stat().st_size<65536 and json.loads(q.read_text())==expect,'fixture evidence mismatch')
    expected={**ident,'status':'PASS_FUNCTIONAL_NOT_VISUAL','completed_tests':[METHODS[k] for k in names],'selected':names,'apk_build':build,'installed_app_sha256':build['apks']['app']['sha256'],'installed_test_sha256':build['apks']['test']['sha256'],'files':{name:n.digest(out/name) for name in names_files}}
    n.require(r==expected,'functional receipt identity/roster mismatch');return r

def main():
    p=argparse.ArgumentParser();p.add_argument('mode',choices=['run','verify']);p.add_argument('--out',required=True,type=Path);p.add_argument('--apks',required=True,type=Path);p.add_argument('--tests',required=True);args=p.parse_args()
    try:
        names=selected(args.tests)
        if args.mode=='run':run(args.out,args.apks,names)
        else:
            ident=n.expected_identity();ident['schema']='hooklab.functional.receipt.v1';verify(args.out,args.apks,names,ident)
        print('FUNCTIONAL_PASS_NOT_VISUAL');return 0
    except (ValueError,TypeError,KeyError,OSError,subprocess.SubprocessError) as exc:print('FUNCTIONAL_REFUSED',str(exc),file=sys.stderr);return 2
if __name__=='__main__':raise SystemExit(main())
