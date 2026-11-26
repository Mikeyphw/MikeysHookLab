#!/usr/bin/env python3
"""Frozen reference admission, deterministic browser geometry and PNG evidence."""
from __future__ import annotations
import argparse, hashlib, json, re, sys, xml.etree.ElementTree as ET
from pathlib import Path
from datetime import datetime, timezone

FILES={'hooklab_ux_revamp.html':'8df26c67823ad29bae220ff18ae86d685f5ad27ed87d94ba6bc607d6e0cb3a2d','hooklab_ux_revamp.svg':'59ca31cb43ed09fdd10de3739837f85c3ab522e85e32474831e8285021e578af'}

def require(ok,msg):
    if not ok:raise ValueError(msg)
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def verify(root):
    base=root/'design/hooklab/01_REFERENCE'; manifest=json.loads((base/'reference-manifest.json').read_text())
    require(manifest['schema']=='hooklab.design.reference.v1' and manifest['revision']=='1.0','reference version drift')
    require(manifest['files']==FILES,'unapproved reference manifest revision')
    for n,h in FILES.items():require((base/n).is_file() and not (base/n).is_symlink() and digest(base/n)==h,'frozen reference drift '+n)
    svg=ET.parse(base/'hooklab_ux_revamp.svg').getroot()
    labels=[e.text for e in svg if e.tag.endswith('}text') and e.text and re.match(r'^\d\d · ',e.text)]
    cases=manifest['cases']; require(len(cases)==24 and [c['id'] for c in cases]==[f'VIS-{i:03}' for i in range(1,25)],'missing/duplicate case')
    require([c['svg_label'] for c in cases]==labels,'SVG screen roster mismatch')
    for c in cases:
        require(c['admission']=='CANDIDATE' and c['native_approved'] is False and c['mask_regions']==[] and c['viewport']=={'width':390,'height':844},'unapproved case policy')
        require(c['owner'] in ('UX-R01','UX-R02','UX-R03') and c['theme'] in ('dark','light','amoled'),'invalid ownership/theme')
        require(c['route'] in ('home','hooks','group','hook','diagnostics','event','health','session','report','settings','scope','mcc','about','help','privacy'),'unknown route')
    require(digest(base/'reference-manifest.json')=='692e90366afdc24ae4e5213a9376435503bab5d275d30a1eb51cff6a04421648','unapproved reference manifest bytes')
    return base,cases

FIXTURE='''c=>{S.theme=c.theme; S.root=c.root; S.route=c.route; S.arg=c.arg; S.stack=[];
if(c.route==='hooks') S.view=c.arg==='apps'?'apps':'groups';
if(c.route==='diagnostics') S.diag=c.arg;
if(c.id==='VIS-022') S.imported=false;
if(c.id==='VIS-024') S.disconnected=true;
if(c.id==='VIS-013') S.session={target:'Neo Store',start:Date.now()-12000,markers:['21:40:00 · Opened app']};
if(c.id==='VIS-014'){S.arg=0;S.reports=[{target:'Neo Store',status:'Stopped',markers:['21:40:00 · Opened app']}]}
render();
if(c.sheet==='filters') filterSheet();
if(c.sheet==='capture_setup') actions.capturefor('neo');
if(c.sheet==='import') actions.import();
}'''
GEOMETRY='''()=>{let app=document.getElementById('app'), ar=app.getBoundingClientRect();const css=getComputedStyle(app);
const tokens={};for(const n of ['bg','surface','surface2','ink','muted','accent','on','warn','error','line'])tokens[n]=css.getPropertyValue('--'+n).trim();
let items=[];for(const el of app.querySelectorAll('h1,h2,h3,.hero,.row,.panel,.btn,.icon,.key,[role="tab"],nav button,input,select,.sheet')){let r=el.getBoundingClientRect(),s=getComputedStyle(el);if(r.width===0||r.height===0||s.visibility==='hidden'||s.display==='none')continue;const visible=r.bottom>ar.top&&r.top<ar.bottom&&r.right>ar.left&&r.left<ar.right;
items.push({index:items.length,tag:el.tagName.toLowerCase(),class_name:el.className,role:el.getAttribute('role'),action:el.dataset.act||null,argument:el.dataset.arg||null,label:el.getAttribute('aria-label')||el.innerText||'',visible,bounds:[r.x,r.y,r.width,r.height].map(x=>Math.round(x*100)/100),color:s.color,background:s.backgroundColor,font_size:s.fontSize,radius:s.borderRadius,padding:s.padding});}
return {viewport:[ar.width,ar.height],tokens,items,route:S.route,theme:S.theme,diagnostics:S.diag,dialog:document.querySelector('.sheet')?.getAttribute('aria-label')||null};}'''

ROSTER_SHA256='b8e5016e9ebe0fd7026b5cb2ceeb72df7180b418db35226b885ca38cc448e96d'
def browser_roster(root):
    p=root/'design/hooklab/02_CONTRACT/browser-semantic-roster.json';require(p.is_file() and not p.is_symlink() and digest(p)==ROSTER_SHA256,'browser semantic roster drift');r=json.loads(p.read_text());require(r['schema']=='hooklab.browser.semantic.v1' and [x['id'] for x in r['cases']]==[f'VIS-{i:03}' for i in range(1,25)],'invalid semantic roster');return {x['id']:x for x in r['cases']}
def verify_browser_case(roster,case_id,g):
    import math
    require(g['viewport']==[390,844] and 0<len(g['items'])<=1000,'invalid geometry viewport/roster')
    for index,row in enumerate(g['items']):
        b=row['bounds'];require(row['index']==index and isinstance(b,list) and len(b)==4 and all(type(x) in (int,float) and math.isfinite(x) and abs(x)<100000 for x in b) and b[2]>0 and b[3]>0 and type(row['visible']) is bool,'contradictory browser geometry')
        x,y,w,h=b
        # Rounded subpixel rectangles can be on a boundary; allow only 0.02 CSS px uncertainty.
        if x+w<-.02 or y+h<-.02 or x>390.02 or y>844.02:require(not row['visible'],'contradictory browser visibility')
        if x+w>.02 and y+h>.02 and x<389.98 and y<843.98:require(row['visible'],'contradictory browser visibility')
    controls=[{k:i[k] for k in ('tag','role','action','argument','label')} for i in g['items'] if i['tag'] in ('button','input','select') or i['role']=='tab']
    actual={'id':case_id,'route':g['route'],'theme':g['theme'],'dialog':g['dialog'],'tokens':g['tokens'],'controls':controls}
    require(actual==roster[case_id],'browser semantic/control/token drift '+case_id)

class CaptureUnavailable(Exception):
    """Only a classified screenshot protocol failure may restart a browser."""


def is_transient_screenshot(error):
    text=str(error)
    return ('Page.screenshot: Protocol error (Page.captureScreenshot): Unable to capture screenshot' in text)


def capture_case(browser, base, roster, c, screenshot_error):
    from io import BytesIO
    from PIL import Image
    context=browser.new_context(viewport=c['viewport'],device_scale_factor=1,locale='en-US',timezone_id='UTC',color_scheme='dark',reduced_motion='reduce')
    try:
        page=context.new_page(); errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
        page.clock.install(time=datetime(2026,11,23,21,42,tzinfo=timezone.utc));page.clock.pause_at(datetime(2026,11,23,21,42,1,tzinfo=timezone.utc));page.set_content((base/'hooklab_ux_revamp.html').read_text(encoding='utf-8'));page.evaluate('document.fonts.ready');page.evaluate(FIXTURE,c)
        geometry=page.evaluate(GEOMETRY);require(not errors,'browser errors '+str(errors));require(geometry['viewport']==[390,844] and geometry['route']==c['route'] and geometry['theme']==c['theme'] and geometry['items'],'incorrect/empty reference state')
        if c['sheet']:require(geometry['dialog'] is not None,'sheet missing')
        verify_browser_case(roster,c['id'],geometry)
        try:
            png=page.screenshot(animations='disabled',caret='hide')
        except screenshot_error as exc:
            if not is_transient_screenshot(exc):raise
            raise CaptureUnavailable(str(exc)) from exc
        require(not errors,'browser errors '+str(errors))
        require(page.evaluate(GEOMETRY)==geometry,'geometry changed during screenshot')
        with Image.open(BytesIO(png)) as im:require(im.size==(390,844) and im.getextrema()[0][0]!=im.getextrema()[0][1],'blank/wrong-sized screenshot')
        return png,geometry
    finally:context.close()


def capture_with_recovery(launch, base, roster, cases, out, screenshot_error):
    records=[];seen=set();browser=launch();version=browser.version
    try:
        for c in cases:
            for attempt in range(1,4):
                try:png,geometry=capture_case(browser,base,roster,c,screenshot_error)
                except CaptureUnavailable as exc:
                    print(f'BROWSER_CAPTURE_TRANSIENT case={c["id"]} attempt={attempt}/3 chromium={version}: {exc}',file=sys.stderr,flush=True)
                    if attempt==3:raise ValueError('screenshot recovery exhausted for '+c['id']) from exc
                    browser.close();browser=None;browser=launch()
                    require(browser.version==version,'browser version drift during recovery')
                    continue
                except (ValueError,screenshot_error) as exc:
                    raise ValueError('reference case '+c['id']+': '+str(exc)) from exc
                break
            h=hashlib.sha256(png).hexdigest();require(h not in seen,'duplicate screenshot '+c['id']);seen.add(h)
            (out/(c['id']+'.png')).write_bytes(png)
            geo=out/(c['id']+'.json');geo.write_text(json.dumps(geometry,sort_keys=True,indent=2)+'\n')
            records.append({'id':c['id'],'png_sha256':h,'geometry_sha256':digest(geo),'elements':len(geometry['items'])})
    finally:
        if browser is not None:browser.close()
    return records,version


def capture(root,out,browser_path=None):
    from playwright.sync_api import sync_playwright, Error
    base,cases=verify(root); roster=browser_roster(root); require(not out.exists(),'fresh reference output required');out.mkdir(parents=True)
    with sync_playwright() as pw:
        records,version=capture_with_recovery(lambda:pw.chromium.launch(headless=True,executable_path=browser_path),base,roster,cases,out,Error)
    result={'schema':'hooklab.browser.reference.v1','reference_files':FILES,'browser_version':version,'engine':'local_override' if browser_path else 'playwright-pinned','viewport':[390,844],'cases':records,'native_approved':False}
    (out/'manifest.json').write_text(json.dumps(result,indent=2,sort_keys=True)+'\n');print('BROWSER_REFERENCE_CAPTURED',len(records),version,flush=True);return result

def main():
    p=argparse.ArgumentParser();p.add_argument('command',choices=['verify','capture','repeat']);p.add_argument('--root',type=Path,default=Path.cwd());p.add_argument('--out',type=Path);p.add_argument('--browser-path');a=p.parse_args()
    try:
        if a.command=='verify':verify(a.root);print('FROZEN_REFERENCE_PASS 24/24');return 0
        if a.command=='capture':capture(a.root,a.out,a.browser_path);return 0
        a.out.mkdir(parents=True,exist_ok=False);first=capture(a.root,a.out/'first',a.browser_path);second=capture(a.root,a.out/'second',a.browser_path);require(first==second,'browser capture/geometry nondeterministic');print('BROWSER_REPEAT_PASS 24/24');return 0
    except (OSError,ValueError,KeyError,TypeError,ET.ParseError) as e:print('REFERENCE_REFUSED',e,file=sys.stderr);return 2
if __name__=='__main__':raise SystemExit(main())
