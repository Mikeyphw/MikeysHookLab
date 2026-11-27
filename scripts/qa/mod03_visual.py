"""Hosted candidate-only browser and native module evidence; not visual approval."""
import argparse, hashlib, json, os, subprocess, sys
from pathlib import Path
from mod02_hosted_packs import identity, sha, run
from qualification import canonical, require, strict_json
from native_smoke import guard, PKG, SERIAL, raw, installed_digest, one, parse_instrumentation
ROOT=Path(__file__).resolve().parents[2]
REFERENCE=ROOT/'design/hooklab/01_REFERENCE/mod03-module-reference.html'
CONTRACT=ROOT/'design/hooklab/02_CONTRACT/mod03-module-contract.json'
TEST='com.mikeyphw.hooklab.qa.Mod03ModuleUiTest#realModuleScreensAndExplicitProvider'
ROUTES=('overview','configure','logs','versions')
REFERENCES={'':REFERENCE,'chatgpt-':ROOT/'design/hooklab/01_REFERENCE/mod03-chatgpt-reference.html'}
CONTRACTS={'':CONTRACT,'chatgpt-':ROOT/'design/hooklab/02_CONTRACT/mod03-chatgpt-contract.json'}
def contract():
    values={}
    for prefix,path in CONTRACTS.items():
        value=strict_json(path.read_bytes())
        require(value=={'schema':'hooklab.mod03.visual-candidate.v1','status':'CANDIDATE_NOT_APPROVED','reference_sha256':sha(REFERENCES[prefix]),'routes':list(ROUTES),'browser_widths':[320,390,840],'minimum_touch_dp':48,'native_dimensions':[780,1688],'historical_contract_unchanged':True},'module reference contract drift')
        values[prefix or 'wordmaster']={'contract_sha256':sha(path),'reference_sha256':sha(REFERENCES[prefix])}
    return values

def validate_images(out, hosted=False):
    from verify_ui_artifacts import png
    if hosted:from PIL import Image
    expected={f'native-{prefix}{r}.png':(780,1688) for prefix in REFERENCES for r in ROUTES}|{f'browser-{prefix}{w}-{r}.png':(w,844) for prefix in REFERENCES for w in (320,390,840) for r in ROUTES}
    require(set(p.name for p in out.glob('*.png'))==set(expected),'module screenshot roster')
    for name,dimensions in expected.items():
        p=out/name;require(p.is_file() and not p.is_symlink() and 0<p.stat().st_size<=12*1024*1024,'invalid screenshot')
        png(p,dimensions)
        if hosted:
            with Image.open(p) as im:
                require(any(lo!=hi for lo,hi in im.convert('RGB').getextrema()),'blank screenshot')
    return {name:sha(out/name) for name in sorted(expected)}

def capture(out,fixture):
    ident=identity();contract();guard();require(not out.exists(),'fresh evidence directory required');out.mkdir(parents=True)
    require({p.name for p in fixture.iterdir()}=={'publisher.der','wordmaster.hookpack','chatgpt.hookpack'},'private fixture inventory')
    app_hash=sha(one('app/build/outputs/apk/debug/*.apk'));test_hash=sha(one('app/build/outputs/apk/androidTest/debug/*.apk'))
    installed_digest(PKG,app_hash);installed_digest(PKG+'.test',test_hash)
    for name in ('publisher.der','wordmaster.hookpack','chatgpt.hookpack'):
        with (fixture/name).open('rb') as source:run('adb','-s',SERIAL,'shell','-T','run-as',PKG,'sh','-c',f"'mkdir -p files/mod03-test && cat > files/mod03-test/{name}'",stdin=source)
        require(hashlib.sha256(raw('exec-out','run-as',PKG,'cat','files/mod03-test/'+name)).hexdigest()==sha(fixture/name),'UI fixture digest mismatch')
    result=run('adb','-s',SERIAL,'shell','am','instrument','-w','-r','-e','class',TEST,PKG+'.test/androidx.test.runner.AndroidJUnitRunner',capture_output=True,text=True)
    parse_instrumentation(result.stdout,TEST)
    for prefix in REFERENCES:
        for route in ROUTES:(out/f'native-{prefix}{route}.png').write_bytes(raw('exec-out','run-as',PKG,'cat',f'files/mod03-test/{prefix}{route}.png'))
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        browser=p.chromium.launch()
        for prefix,reference in REFERENCES.items():
            for width in (320,390,840):
                page=browser.new_page(viewport={'width':width,'height':844},device_scale_factor=1)
                page.goto(reference.as_uri());page.evaluate('document.fonts.ready')
                for route in ROUTES:
                    tab=page.get_by_role('tab',name=route.capitalize());tab.click();panel=page.locator('#'+route)
                    require(tab.get_attribute('aria-selected')=='true' and panel.is_visible(),'browser module navigation')
                    require(all(box['height']>=48 and box['width']>=48 for box in [item.bounding_box() for item in page.get_by_role('tab').all()]),'undersized browser touch target')
                    require(page.evaluate('document.documentElement.scrollWidth<=innerWidth'),'browser overflow')
                    page.screenshot(path=out/f'browser-{prefix}{width}-{route}.png')
                page.close()
        browser.close()
    files=validate_images(out,hosted=True);receipt={'schema':'hooklab.mod03.visual-evidence.v2',**ident,'status':'CANDIDATE_PASS_NOT_APPROVAL','test':TEST,'references':contract(),'manager_apk_sha256':app_hash,'instrumentation_apk_sha256':test_hash,'images':files}
    (out/'receipt.json').write_bytes(canonical(receipt)+b'\n');verify(out)

def verify_artifacts(out,ident,app_hash,test_hash):
    contract();receipt=strict_json((out/'receipt.json').read_bytes())
    require(set(receipt)=={'schema','source_sha','source_tree','run_id','run_attempt','status','test','references','manager_apk_sha256','instrumentation_apk_sha256','images'},'module evidence shape')
    require(all(receipt[k]==v for k,v in ident.items()) and receipt['schema']=='hooklab.mod03.visual-evidence.v2' and receipt['status']=='CANDIDATE_PASS_NOT_APPROVAL' and receipt['test']==TEST,'module evidence identity')
    require(receipt['references']==contract() and receipt['images']==validate_images(out),'module evidence digest')
    require(receipt['manager_apk_sha256']==app_hash and receipt['instrumentation_apk_sha256']==test_hash,'module APK digest')
    require({p.name for p in out.iterdir()}==set(receipt['images'])|{'receipt.json'},'module evidence inventory')
    print('MOD03_MODULE_VISUAL_CANDIDATE_PASS_NOT_APPROVAL')

def verify(out):
    verify_artifacts(out,identity(),sha(one('app/build/outputs/apk/debug/*.apk')),sha(one('app/build/outputs/apk/androidTest/debug/*.apk')))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('command',choices=('capture','verify','contract','verify-artifacts'));p.add_argument('--out',type=Path);p.add_argument('--fixture',type=Path);p.add_argument('--source');p.add_argument('--tree');p.add_argument('--run-id',type=int);p.add_argument('--attempt',type=int);p.add_argument('--apks',type=Path);a=p.parse_args()
    if a.command=='contract':contract();print('MOD03_REFERENCE_CONTRACT_PASS')
    elif a.command=='capture':capture(a.out,a.fixture)
    elif a.command=='verify-artifacts':
        from qualification import git
        require(git(ROOT,'rev-parse','HEAD')==a.source and git(ROOT,'rev-parse','HEAD^{tree}')==a.tree and git(ROOT,'status','--porcelain','--untracked-files=no')=='','external source identity mismatch')
        verify_artifacts(a.out,{'source_sha':a.source,'source_tree':a.tree,'run_id':a.run_id,'run_attempt':a.attempt},sha(a.apks/'app.apk'),sha(a.apks/'android-test.apk'))
    else:verify(a.out)
