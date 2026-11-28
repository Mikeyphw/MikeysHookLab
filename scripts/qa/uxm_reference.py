"""Exercise frozen HTML via real browser interactions and capture measured evidence."""
import argparse,json
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
import uxm_contract as contract
from qualification import canonical
ROOT=Path(__file__).resolve().parents[2];DESIGN=ROOT/'design/hooklab/03_UXM'
COLLECT=r'''() => {
const root=document.querySelector('dialog[open]') || document;
const nodes=[...root.querySelectorAll('h1,h2,h3,button,input,select,summary,output,[role="status"],.row,.panel,.tabs,.profilebar,.preview')];
let seen={};const out=[];
for(const e of nodes){const r=e.getBoundingClientRect(),s=getComputedStyle(e);
 if(!r.width||!r.height||s.visibility==='hidden'||!e.checkVisibility())continue;
 const key=e.dataset.action||e.dataset.change||e.dataset.range||e.id||e.getAttribute('aria-label')||e.tagName.toLowerCase()+':'+(e.innerText||'').trim().slice(0,80);
 const n=seen[key]=(seen[key]||0)+1;
 const role=e.getAttribute('role')||({BUTTON:'button',INPUT:e.type==='checkbox'?'switch':e.type==='range'?'slider':'textbox',SELECT:'combobox',SUMMARY:'disclosure',H1:'heading',H2:'heading',H3:'heading',OUTPUT:'status'}[e.tagName]||'group');
 const state={};for(const a of ['aria-current','aria-selected','aria-expanded','aria-checked'])if(e.hasAttribute(a))state[a]=e.getAttribute(a);
 if(e instanceof HTMLInputElement){if(e.type==='checkbox')state.checked=e.checked;else state.value=e.value;}
 if(e instanceof HTMLSelectElement)state.value=e.value;if(e.disabled)state.disabled=true;
 const label=(e.getAttribute('aria-label')||e.innerText||e.getAttribute('placeholder')||'').trim().replace(/\s+/g,' ');
 const bounds=[r.x,r.y,r.width,r.height].map(x=>Math.round(x*1000)/1000);
 out.push({id:key+'#'+n,role,label,state,action:e.dataset.action||e.dataset.change||e.dataset.range||null,bounds,font_sp:parseFloat(s.fontSize),color:s.color,background:s.backgroundColor,radius:parseFloat(s.borderTopLeftRadius)||0,clickable:['BUTTON','INPUT','SELECT','SUMMARY'].includes(e.tagName)||Boolean(e.dataset.action),visible:r.bottom>0&&r.right>0&&r.top<innerHeight&&r.left<innerWidth});
}return {viewport:[innerWidth,innerHeight],theme:document.documentElement.dataset.theme||document.body.dataset.theme||'',components:out,overflow:document.documentElement.scrollWidth>innerWidth};
}'''
def exercise(page,case):
 for i,step in enumerate(case['steps']):
  el=page.locator(step['selector']);op=step['op']
  try:
   if op=='click':el.click()
   elif op=='fill':el.fill(step['value'])
   elif op=='check':el.set_checked(step['value'])
   elif op=='select':el.select_option(step['value'])
   elif op=='expect':
    for k,v in step.items():
     if k in ('op','selector'):continue
     if k=='text':expect(el).to_have_text(v)
     elif k=='contains':expect(el).to_contain_text(v)
     elif k=='visible':(expect(el).to_be_visible() if v else expect(el).to_be_hidden())
     elif k=='checked':(expect(el).to_be_checked() if v else expect(el).not_to_be_checked())
     elif k=='focused':(expect(el).to_be_focused() if v else expect(el).not_to_be_focused())
     elif k=='count':expect(el).to_have_count(v)
     elif k=='attr':expect(el).to_have_attribute(*v)
     elif k=='value':expect(el).to_have_value(v)
     else:raise ValueError('unknown predicate '+k)
   else:raise ValueError('unknown operation '+op)
  except Exception as exc:raise RuntimeError(f'{case["id"]} step {i}: {step}') from exc
def capture(out,executable=None,only=None):
 out.mkdir(parents=True,exist_ok=False);data_contract=contract.validate();cases=data_contract['cases'];records=[]
 with sync_playwright() as p:
  browser=p.chromium.launch(executable_path=executable,headless=True,args=['--no-sandbox'])
  for c in cases:
   if only and c['id'] not in only:continue
   w,h=c['viewport'];ctx=browser.new_context(viewport={'width':w,'height':h},device_scale_factor=1,locale='en-US',timezone_id='UTC',color_scheme='dark',reduced_motion='reduce')
   ctx.route('**/*',lambda r:r.continue_() if r.request.url.startswith('file:') else r.abort())
   page=ctx.new_page();page.set_default_timeout(5500);expect.set_options(timeout=3000)
   page.set_content((DESIGN/'hooklab-ux-interactive.html').read_text(encoding='utf-8'));page.evaluate('document.fonts.ready');exercise(page,c)
   if page.locator('#toast').is_visible():page.locator('#toast').wait_for(state='hidden',timeout=5000)
   page.mouse.move(0,0);page.evaluate('() => new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))')
   data=page.evaluate(COLLECT);data.update(case=c['id'],journey_steps=len(c['steps']))
   contract.verify_browser_case(data_contract,data)
   page.screenshot(path=out/(c['id']+'.png'),animations='disabled',caret='hide');(out/(c['id']+'.json')).write_text(json.dumps(data,sort_keys=True,indent=2)+'\n')
   records.append(c['id']);ctx.close();print('UXM_BROWSER_PASS',c['id'],flush=True)
  browser.close()
 (out/'roster.json').write_text(json.dumps(records,indent=2)+'\n')
def main():
 p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);p.add_argument('--browser-path');p.add_argument('--only',nargs='+');p.add_argument('--repeat',action='store_true');a=p.parse_args()
 if a.repeat:
  if a.only:raise ValueError('partial browser capture cannot qualify')
  a.out.mkdir(parents=True,exist_ok=False);capture(a.out/'first',a.browser_path);capture(a.out/'second',a.browser_path)
  from uxm_visual import verify_repeat
  records=verify_repeat(a.out/'first',a.out/'second',contract.validate())
  if a.browser_path:binding={'engine':'LOCAL_BROWSER_OVERRIDE','reference_sha256':contract.HTML_SHA,'records':records}
  else:
   import native_smoke as n
   binding={**n.expected_identity(),'engine':'PINNED_PLAYWRIGHT','reference_sha256':contract.HTML_SHA,'records':records}
  (a.out/'binding.json').write_bytes(canonical(binding)+b'\n')
 else:capture(a.out,a.browser_path,a.only)
if __name__=='__main__':main()
