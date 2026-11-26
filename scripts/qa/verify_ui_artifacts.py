#!/usr/bin/env python3
"""Read-only host-side evidence rehash, no Gradle/ADB/Pillow dependency.
The hosted visual job independently recomputes metrics; this verifier checks
that the exact downloaded bytes and source/run/attempt agree with its report.
"""
from __future__ import annotations
import argparse,hashlib,json,re,struct,sys,zlib
from pathlib import Path
import reference

from visual_routes import MAP
from visual_body import compare as compare_body
def need(ok,msg):
    if not ok:raise ValueError(msg)
def read(p,max_bytes=12*1024*1024):
    need(p.is_file() and not p.is_symlink() and 0<p.stat().st_size<=max_bytes,'missing/unsafe evidence '+p.name);return p.read_bytes()
def sha(p):return hashlib.sha256(read(p)).hexdigest()
def obj(p):return json.loads(read(p,2*1024*1024))
def roster(folder,names):need(folder.is_dir() and not folder.is_symlink() and {p.name for p in folder.iterdir()}==set(names),'unexpected evidence file set '+folder.name)
def png(p,size):
    b=read(p);need(b[:8]==b'\x89PNG\r\n\x1a\n','invalid PNG');pos=8;header=False;ended=False;idat=False
    while pos+12<=len(b):
        n=int.from_bytes(b[pos:pos+4],'big');kind=b[pos+4:pos+8];end=pos+12+n;need(end<=len(b),'truncated PNG');payload=b[pos+8:pos+8+n]
        need(zlib.crc32(kind+payload)&0xffffffff==int.from_bytes(b[pos+8+n:end],'big'),'PNG checksum mismatch')
        if not header:need(kind==b'IHDR' and n==13 and struct.unpack('>II',payload[:8])==size,'wrong PNG geometry');header=True
        if kind==b'IDAT':idat=True
        pos=end
        if kind==b'IEND':need(n==0,'bad PNG end');ended=True;break
    need(header and idat and ended and pos==len(b),'incomplete PNG')
    return hashlib.sha256(b).hexdigest()
def browser(folder,root):
    m=obj(folder/'manifest.json');need(m['schema']=='hooklab.browser.reference.v1' and m['reference_files']==reference.FILES and m['native_approved'] is False and m['engine']=='playwright-pinned','unqualified hosted reference')
    rows=m['cases'];ids=[f'VIS-{i:03}' for i in range(1,25)];need([r['id'] for r in rows]==ids,'wrong browser roster');roster(folder,['manifest.json',*(i+s for i in ids for s in ('.json','.png'))]);seen=set();contracts=reference.browser_roster(root)
    for row in rows:
        i=row['id'];g=obj(folder/(i+'.json'));h=png(folder/(i+'.png'),(390,844));need(h==row['png_sha256'] and h not in seen and sha(folder/(i+'.json'))==row['geometry_sha256'] and len(g['items'])==row['elements'] and g['viewport']==[390,844],'browser evidence drift');seen.add(h);reference.verify_browser_case(contracts,i,g)
    return m

def pair(folder,root,extra):
    roster(folder,['first','second',extra]);a=browser(folder/'first',root);b=browser(folder/'second',root);need(a==b,'browser repeats differ');return a

def design(root,folder,source):
    reference.verify(root);pair(folder,root,'source-commit.txt');need(read(folder/'source-commit.txt',128).decode().strip()==source,'design source drift');return {'status':'DESIGN_REFERENCE_EVIDENCE_VERIFIED','source_sha':source,'reference_cases':24,'native_visual_approved':False,'reference_manifest_sha256':sha(folder/'first/manifest.json')}
def native(root,folder,source,tree,run,attempt):
    from native_smoke import TESTS,validate_receipt_files
    reference.verify(root);ref=folder/f'hooklab-native-reference-{run}-{attempt}';nat=folder/f'hooklab-native-baseline-{attempt}';report=folder/f'hooklab-visual-candidate-{run}-{attempt}'
    roster(folder,[ref.name,nat.name,report.name]);pair(ref,root,'reference-binding.json');r=obj(nat/'receipt.json');v=obj(report/'visual-report.json');b=obj(ref/'reference-binding.json')
    ident={'schema':'hooklab.native.smoke.v1','source_sha':source,'source_tree':tree,'run_id':str(run),'run_attempt':str(attempt),'repository':'Mikeyphw/MikeysHookLab','hosted_emulator_only':True,'physical_device_qualified':False,'release_qualified':False,'visual_approved':False}
    need(all(r.get(k)==x for k,x in ident.items()),'native source/run/attempt drift');need(r.get('status')=='BASELINE_PASS_NOT_VISUAL_APPROVAL' and r.get('tests')==TESTS,'native test roster drift')
    need(b=={**ident,'reference_manifest_sha256':sha(ref/'first/manifest.json')},'native reference binding drift')
    for k in ('app_apk_sha256','installed_apk_sha256','android_test_apk_sha256','installed_test_apk_sha256','debug_certificate_sha256'):need(re.fullmatch('[a-f0-9]{64}',r.get(k,'')),'invalid APK receipt')
    need(r['app_apk_sha256']==r['installed_apk_sha256'] and r['android_test_apk_sha256']==r['installed_test_apk_sha256'],'installed APK drift');validate_receipt_files(nat,r)
    need(v['schema']=='hooklab.visual.candidate.v1' and all(v.get(k)==x for k,x in ident.items() if k!='schema') and v['status']=='CANDIDATE_COMPARISON_ONLY' and v['native_goldens_approved'] is False,'visual receipt drift')
    need(v['coverage']=={'reference_cases':24,'candidate_comparisons':len(MAP),'approved_cases':0} and v['uncompared_cases']==sorted(set(f'VIS-{i:03}' for i in range(1,25))-set(MAP.values())),'coverage/approval drift')
    need(v['reference_manifest_sha256']==sha(ref/'first/manifest.json') and v['native_receipt_sha256']==sha(nat/'receipt.json') and v['contract_sha256']==sha(root/'design/hooklab/02_CONTRACT/visual-contract.json') and v['browser_semantic_roster_sha256']==reference.ROSTER_SHA256,'report binding drift')
    need(v['repeat_calibration']==r['repeat_calibration'],'repeat report binding drift');need([x['route'] for x in v['cases']]==list(MAP),'native report roster drift');roster(report,['visual-report.json',*(i+'-triptych.png' for i in MAP)])
    seen=set()
    for row in v['cases']:
        route=row['route'];png(nat/(route+'-repeat.png'),(780,1688));h=png(nat/(route+'.png'),(780,1688));need(h not in seen and r['screenshots'][route]=={'png':h,'geometry':sha(nat/(route+'.json'))},'native screenshot/geometry drift');seen.add(h)
        need(row['reference_case']==MAP[route] and row['native_sha256']==h and row['reference_sha256']==sha(ref/'first'/(MAP[route]+'.png')) and row['triptych_sha256']==png(report/(route+'-triptych.png'),(1170,868)) and row['visual_approved'] is False and row['fixture_equivalence'] is False,'triptych/approval drift')
        need(row.get('body_comparison')==compare_body(obj(ref/'first'/(MAP[route]+'.json')),obj(nat/(route+'.json')),route),'body comparison drift')
    return {'status':'NATIVE_CANDIDATE_EVIDENCE_REHASHED','source_sha':source,'source_tree':tree,'run_id':str(run),'run_attempt':str(attempt),'candidate_comparisons':len(MAP),'approved_cases':0,'native_visual_approved':False,'physical_device_qualified':False,'release_qualified':False,'report_sha256':sha(report/'visual-report.json')}

def main():
    p=argparse.ArgumentParser();p.add_argument('command',choices=['design','native']);p.add_argument('--root',type=Path,required=True);p.add_argument('--artifacts',type=Path,required=True);p.add_argument('--source',required=True);p.add_argument('--tree');p.add_argument('--run-id');p.add_argument('--attempt');a=p.parse_args()
    try:
        need(re.fullmatch('[a-f0-9]{40}',a.source),'invalid source');result=design(a.root,a.artifacts,a.source) if a.command=='design' else native(a.root,a.artifacts,a.source,a.tree,a.run_id,a.attempt);print(json.dumps(result,sort_keys=True));return 0
    except (OSError,ValueError,KeyError,TypeError) as e:print('UI_ARTIFACT_REFUSED',str(e),file=sys.stderr);return 2
if __name__=='__main__':raise SystemExit(main())
