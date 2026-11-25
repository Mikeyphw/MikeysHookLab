#!/usr/bin/env python3
"""Candidate visual comparison, independent from native smoke and golden approval.
No screenshot becomes a golden on failure. The reference design stays frozen.
"""
from __future__ import annotations
import argparse, hashlib, json, math, re, sys
from pathlib import Path
import reference

SCHEMA='hooklab.visual.candidate.v1'
MAP={'home':'VIS-024','hooks':'VIS-002','by-app':'VIS-003','group-detail':'VIS-004','hook-detail':'VIS-005','diagnostics':'VIS-006'}
NAV={'home':201,'hooks':202,'by-app':202,'group-detail':202,'hook-detail':202,'diagnostics':203}
DEEP={'group-detail','hook-detail'}
def root_route(route):return 'hooks' if route=='by-app' else route
LABELS={'home':'Home','hooks':'Hooks','diagnostics':'Diagnostics'}
TITLE={'home':'HookLab','hooks':'Hooks','by-app':'Hooks','group-detail':'Group details','hook-detail':'Hook configuration','diagnostics':'Diagnostics'}

def need(ok,msg):
    if not ok:raise ValueError(msg)
def digest(p):
    need(p.is_file() and not p.is_symlink(),'missing or unsafe evidence '+p.name)
    return hashlib.sha256(p.read_bytes()).hexdigest()
def load(p):
    digest(p);need(p.stat().st_size<=8*1024*1024,'oversized JSON');return json.loads(p.read_text())
def dump(p,x):p.write_text(json.dumps(x,sort_keys=True,indent=2)+'\n')
def numeric(v):return type(v) in (int,float) and math.isfinite(v)
def box(b,viewport=(390,844)):
    need(isinstance(b,list) and len(b)==4 and all(numeric(x) for x in b),'invalid bounds')
    x,y,w,h=b;need(w>0 and h>0 and x>=0 and y>=0 and x+w<=viewport[0]+.05 and y+h<=viewport[1]+.05,'out-of-viewport bounds');return b

def validate_contract(root):
    _,cases=reference.verify(root);reference.browser_roster(root)
    d=load(root/'design/hooklab/02_CONTRACT/visual-contract.json')
    need(d['schema']=='hooklab.visual.contract.v1' and d['reference_files']==reference.FILES,'reference identity drift')
    need(d['viewport']==[390,844] and d['baseline_map']==MAP and d['masks']==[],'unsupported mapping or mask')
    need(d['tolerances']=={'bounds_dp':2,'font_sp':1,'rgb_delta':0,'native_ssim_min':.99,'native_changed_pixel_max':.01},'threshold drift')
    need(d['approval']=='CANDIDATE_ONLY' and d['native_goldens']==[],'approval requires separate reviewed revision')
    need(d['cases']==[{'id':c['id'],'owner':c['owner'],'admission':'CANDIDATE','native_approved':False} for c in cases],'coverage drift')
    need(d['regions']=={'header':[0,0,390,98],'body':[0,98,390,760],'bottom':[0,760,390,844]} and d['native_repeat_policy']=='ONLY_MEASURED_SYSTEM_BAR_INSETS','region/repeat policy drift')
    need(d['fixture_contract']=='fixtures.json','fixture contract path drift')
    fixture=load(root/'design/hooklab/02_CONTRACT/fixtures.json')
    ids=[f'F{i:02}' for i in range(1,16)];need(fixture['schema']=='hooklab.fixture.contract.v1' and [f['id'] for f in fixture['fixtures']]==ids and all(f['runtime_qualified'] is False for f in fixture['fixtures']),'fixture roster/runtime claim drift')
    need([c['id'] for c in fixture['cases']]==[c['id'] for c in cases] and all(c['native_equivalence'] is False and c['fixtures'] and len(set(c['fixtures']))==len(c['fixtures']) and set(c['fixtures'])<=set(ids) for c in fixture['cases']),'fixture case coverage drift')
    need(fixture['profiles'][0]=={'name':'primary','api':36,'width_dp':390,'height_dp':844,'font_scale':1.0,'admission':'FOUNDATION_CANDIDATE'} and all(p['admission']=='UX-R04_PENDING' for p in fixture['profiles'][1:]),'profile admission drift')
    need(d['required_navigation']==LABELS and d['required_title']==TITLE,'semantic contract drift')
    return d

def ref_manifest(folder,root):
    from PIL import Image
    m=load(folder/'manifest.json');need(m['schema']=='hooklab.browser.reference.v1' and m['reference_files']==reference.FILES and m['native_approved'] is False,'invalid reference manifest')
    rows=m['cases'];need([x['id'] for x in rows]==[f'VIS-{i:03}' for i in range(1,25)],'reference roster drift')
    expected={'manifest.json'}|{r['id']+suffix for r in rows for suffix in ('.png','.json')}
    need({p.name for p in folder.iterdir()}==expected,'reference file roster drift')
    seen=set();roster=reference.browser_roster(root)
    for row in rows:
        p=folder/(row['id']+'.png');g=folder/(row['id']+'.json');h=digest(p)
        need(h not in seen and h==row['png_sha256'] and digest(g)==row['geometry_sha256'],'reference hash or duplicate drift');seen.add(h)
        with Image.open(p) as im:need(im.size==(390,844) and im.format=='PNG','reference PNG geometry')
        j=load(g);need(j['viewport']==[390,844] and len(j['items'])==row['elements'],'reference geometry drift');reference.verify_browser_case(roster,row['id'],j)
    return m

def browser_controls(g,route):
    # Semantic IDs are defined by the actual frozen prototype's action and argument.
    controls={}
    for i in g['items']:
        if i['tag']=='h1' and i['visible']:
            need('title' not in controls and i['label']==TITLE[route],'reference title mismatch')
            controls['title']={'label':i['label'],'bounds':box(i['bounds']),'font_sp':float(i['font_size'].removesuffix('px')),'color':rgb(i['color'])}
        if i['tag']=='button' and i['action']=='root' and i['visible'] and i['class_name'] in ('active',''):
            key=i['argument'];need(key in LABELS and key not in controls and i['label']==LABELS[key],'reference navigation mismatch')
            controls[key]={'label':i['label'],'bounds':box(i['bounds']),'selected':'active' in i['class_name'].split(),'font_sp':float(i['font_size'].removesuffix('px')),'color':rgb(i['color'])}
    if route in DEEP:need(set(controls)=={'title'},'deep reference controls mismatch')
    else:need(set(controls)=={'title',*LABELS} and [k for k in LABELS if controls[k]['selected']]==[root_route(route)],'reference controls missing or wrong selection')
    return controls

def rgb(s):
    m=re.fullmatch(r'rgb\((\d+),\s*(\d+),\s*(\d+)\)',s);need(m is not None,'invalid RGB');r=list(map(int,m.groups()));need(all(0<=x<=255 for x in r),'invalid RGB channels');return r

def native_controls(g,route):
    need(g['route']==route and g['selected_menu']==NAV[route] and g['real_activity'] is True and g['density']==2,'native route/density mismatch')
    v=g['semantic_controls'];need(isinstance(v,dict) and set(v)==({'title'} if route in DEEP else {'title',*LABELS}),'missing/extra native semantic controls')
    for k in v:
        r=v[k];need(set(r)==({'label','bounds','font_sp','color'} if k=='title' else {'label','bounds','font_sp','color','selected'}),'invalid native semantic fields')
        box(r['bounds']);need(numeric(r['font_sp']) and 0<r['font_sp']<=100 and isinstance(r['color'],list) and len(r['color'])==3 and all(type(x) is int and 0<=x<=255 for x in r['color']),'invalid native typography')
        need(isinstance(r['label'],str) and r['label'] in ({TITLE[route],"Mikey's HookLab"} if k=='title' and route=='home' else {TITLE[route] if k=='title' else LABELS[k]}),'unapproved native label '+k)
        if k!='title':need(type(r['selected']) is bool and r['selected']==(k==root_route(route)),'wrong native selection')
    return v

def metrics(a,b):
    from PIL import ImageChops, ImageStat
    need(a.size==b.size,'image dimensions differ');a=a.convert('RGB');b=b.convert('RGB');d=ImageChops.difference(a,b);h=d.getextrema()
    # Exact changed pixel ratio, not channel ratio. SSIM averages independent
    # local luminance windows; no masking or alignment optimizations.
    changed=sum(1 for pixel in d.getdata() if pixel!=(0,0,0))/(a.width*a.height)
    la=a.convert('L');lb=b.convert('L');scores=[]
    for y in range(0,a.height,8):
        for x in range(0,a.width,8):
            crop=(x,y,min(x+8,a.width),min(y+8,a.height));u=list(la.crop(crop).getdata());v=list(lb.crop(crop).getdata());n=len(u);mu=sum(u)/n;mv=sum(v)/n;vu=sum((z-mu)**2 for z in u)/n;vv=sum((z-mv)**2 for z in v)/n;cov=sum((p-mu)*(q-mv) for p,q in zip(u,v))/n
            scores.append(((2*mu*mv+6.5025)*(2*cov+58.5225))/((mu*mu+mv*mv+6.5025)*(vu+vv+58.5225)))
    return {'changed_pixel_ratio':round(changed,8),'ssim':round(sum(scores)/len(scores),8),'mean_abs_rgb':round(sum(ImageStat.Stat(d).mean)/3,5),'max_rgb_delta':max(v[1] for v in h)}

def compare_controls(expected,actual,tol):
    results=[]
    for k,e in expected.items():
        a=actual.get(k);need(a is not None,'missing native control '+k)
        need(a.get('selected')==e.get('selected'),'wrong native selection '+k)
        label_match=a['label']==e['label']
        delta=[round(y-x,3) for x,y in zip(e['bounds'],a['bounds'])];font=round(a['font_sp']-e['font_sp'],3);color=max(abs(x-y) for x,y in zip(a['color'],e['color']))
        ok=label_match and max(map(abs,delta))<=tol['bounds_dp'] and abs(font)<=tol['font_sp'] and color<=tol['rgb_delta']
        results.append({'id':k,'label_match':label_match,'delta_dp':delta,'font_delta_sp':font,'max_rgb_delta':color,'within_candidate_tolerance':ok})
    return results

def strict_regression(expected,actual,a,b,tolerances):
    rows=compare_controls(expected,actual,tolerances)
    need(all(x['within_candidate_tolerance'] for x in rows),'geometry/typography/color/label regression')
    m=metrics(a,b)
    need(m['ssim']>=tolerances['native_ssim_min'] and m['changed_pixel_ratio']<=tolerances['native_changed_pixel_max'],'native image regression')
    return m

def triptych(a,b,path):
    from PIL import Image, ImageChops, ImageDraw
    a=a.convert('RGB');b=b.convert('RGB');d=ImageChops.difference(a,b);heat=d.convert('L').point(lambda x:min(255,x*3));red=Image.new('RGB',a.size,(255,50,40));black=Image.new('RGB',a.size,(18,18,18));heat=Image.composite(red,black,heat)
    im=Image.new('RGB',(a.width*3,a.height+24),(18,18,18));draw=ImageDraw.Draw(im)
    for i,(label,panel) in enumerate(zip(('DESIGN','NATIVE CANDIDATE','DIFFERENCE'),(a,b,heat))):draw.text((i*a.width+8,5),label,fill='white');im.paste(panel,(i*a.width,24))
    im.save(path,format='PNG')

def evaluate(root,ref,native,out,verify=False):
    import native_smoke
    from PIL import Image, ImageChops
    contract=validate_contract(root);bm=ref_manifest(ref,root);ident=native_smoke.expected_identity();nr=native_smoke.verify(native,ident)
    # Ref captures are bound to same source and exact run/attempt by workflow.
    binding=load(ref.parent/'reference-binding.json');need(binding=={**ident,'reference_manifest_sha256':digest(ref/'manifest.json')},'browser source/run binding mismatch')
    if not verify:need(not out.exists(),'fresh comparison output required');out.mkdir(parents=True)
    need(out.is_dir() and not out.is_symlink(),'comparison directory missing')
    records=[]
    for route,case in MAP.items():
        rg=load(ref/(case+'.json'));ng=load(native/(route+'.json'));exp=browser_controls(rg,route);actual=native_controls(ng,route);rows=compare_controls(exp,actual,contract['tolerances'])
        with Image.open(ref/(case+'.png')) as r,Image.open(native/(route+'.png')) as n:
            # One fixed 2x density mapping; no crop, resize-to-fit, or dynamic masks.
            n=n.convert('RGB').resize((390,844),Image.Resampling.BOX);m=metrics(r,n);regions={k:metrics(r.crop(tuple(b)),n.crop(tuple(b))) for k,b in contract['regions'].items()}
            p=out/(route+'-triptych.png')
            if verify:
                # Reproduce image in memory; compare decoded pixels, not encoder metadata.
                import tempfile
                with tempfile.TemporaryDirectory(dir=out.parent) as tmp:
                    q=Path(tmp)/'reproduced.png';triptych(r,n,q)
                    with Image.open(q) as expected,Image.open(p) as actual_img:need(expected.size==actual_img.size and ImageChops.difference(expected.convert('RGB'),actual_img.convert('RGB')).getbbox() is None,'triptych drift')
            else:triptych(r,n,p)
        records.append({'route':route,'reference_case':case,'native_sha256':digest(native/(route+'.png')),'reference_sha256':digest(ref/(case+'.png')),'candidate_metrics':m,'region_metrics':regions,'semantic_controls':rows,'triptych_sha256':digest(p),'fixture_equivalence':False,'visual_approved':False})
    result={**ident,'schema':SCHEMA,'reference_manifest_sha256':digest(ref/'manifest.json'),'native_receipt_sha256':digest(native/'receipt.json'),'contract_sha256':digest(root/'design/hooklab/02_CONTRACT/visual-contract.json'),'browser_semantic_roster_sha256':reference.ROSTER_SHA256,'coverage':{'reference_cases':24,'candidate_comparisons':len(MAP),'approved_cases':0},'uncompared_cases':sorted(set(f'VIS-{i:03}' for i in range(1,25))-set(MAP.values())),'status':'CANDIDATE_COMPARISON_ONLY','native_goldens_approved':False,'repeat_calibration':nr['repeat_calibration'],'cases':records}
    path=out/'visual-report.json'
    if verify:need(load(path)==result and {p.name for p in out.iterdir()}=={'visual-report.json',*(r+'-triptych.png' for r in MAP)},'comparison report/file drift')
    else:dump(path,result)
    print('CANDIDATE_COMPARISON_VERIFIED 6/24; 0 APPROVED; NO VISUAL ACCEPTANCE',flush=True)
    return result

def main():
    p=argparse.ArgumentParser();p.add_argument('command',choices=['contract','bind','compare','verify']);p.add_argument('--root',type=Path,default=Path.cwd());p.add_argument('--reference',type=Path);p.add_argument('--native',type=Path);p.add_argument('--out',type=Path);a=p.parse_args()
    try:
        if a.command=='contract':validate_contract(a.root);print('VISUAL_CONTRACT_PASS 24 CANDIDATE CASES');return 0
        if a.command=='bind':
            import native_smoke
            validate_contract(a.root);ref_manifest(a.reference,a.root)
            p=a.reference.parent/'reference-binding.json';need(not p.exists(),'reference already bound')
            dump(p,{**native_smoke.expected_identity(),'reference_manifest_sha256':digest(a.reference/'manifest.json')});return 0
        evaluate(a.root,a.reference,a.native,a.out,a.command=='verify');return 0
    except (ValueError,OSError,KeyError,TypeError) as e:print('VISUAL_REFUSED',e,file=sys.stderr);return 2
if __name__=='__main__':raise SystemExit(main())
