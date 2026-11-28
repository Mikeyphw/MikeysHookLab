"""Strict admitted visual/behavior gate; no approval inferred from legacy witnesses."""
from __future__ import annotations
import argparse,re
from pathlib import Path
from PIL import Image,ImageChops
from qualification import canonical,strict_json,require
import uxm_contract as u
import visual
ROOT=u.ROOT
def sha(p):return u.digest(p.read_bytes())
def json_file(p):
 require(p.is_file() and not p.is_symlink() and 0<p.stat().st_size<=16*1024*1024,'missing/unsafe JSON '+p.name);return strict_json(p.read_bytes())
def image(p,size=None):
 require(p.is_file() and not p.is_symlink() and 0<p.stat().st_size<=16*1024*1024,'missing/unsafe image '+p.name)
 with Image.open(p) as im:
  require(im.format=='PNG' and im.width<=8192 and im.height<=8192 and (size is None or im.size==tuple(size)),'screenshot dimensions/format '+p.name);out=im.convert('RGB');require(any(a!=b for a,b in out.getextrema()),'blank image '+p.name);return out
def rgb(text):
 m=re.fullmatch(r'rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*(\d+(?:\.\d+)?))?\)',text);require(m is not None,'invalid observed color');r,g,b=map(int,m.groups()[:3]);a=float(m.group(4) or 1);require(max(r,g,b)<=255 and 0<=a<=1,'invalid observed color channel');return (r,g,b,round(a,3))
def compare_semantics(case,expected,observed,deltas=()):
 require([x['id'] for x in expected]==[x['id'] for x in observed],'missing/extra/reordered native components '+case)
 index={(x['component'],x['field']):x for x in deltas if x['case']==case};require(len(index)==len([x for x in deltas if x['case']==case]),'duplicate platform delta');used=set();results=[]
 for e,a in zip(expected,observed):
  ident=e['id']
  for field in ('role','label','state','visible','background','radius'):
   v,w=e[field],a[field];ok=(rgb(v)==rgb(w)) if field=='background' else (abs(v-w)<=2 if field=='radius' else v==w)
   if not ok:
    k=(ident,field);d=index.get(k);require(d is not None and d['reference']==v and d['native']==w,'UX-M semantic drift '+case+' '+ident+' '+field);used.add(k)
  require(len(a['bounds'])==4 and all(u.number(v,-100000,100000) for v in a['bounds']),'invalid native bounds');delta=max(abs(v-w) for v,w in zip(e['bounds'],a['bounds']));font=abs(e['font_sp']-a['font_sp']);require(delta<=u.TOL['bounds_dp'] and font<=u.TOL['font_sp'] and rgb(e['color'])==rgb(a['color']),'UX-M geometry/type/color mismatch '+case+' '+ident)
  if e.get('action') is not None or e.get('clickable'):
   require(a['clickable'] or e['role'] in ('textbox','slider','combobox') and a['focusable'],'native action missing '+ident);require(len(a['touch_dp'])==2 and all(u.number(v,48,10000) for v in a['touch_dp']),'undersized native action '+ident)
  results.append({'id':ident,'bounds_delta':round(delta,3),'font_delta':round(font,3)})
 require(used==set(index),'unused platform delta; stale admission '+case);return results
def compare_raster(case,reference,native,regions,limits):
 require(reference.size==native.size,'native/reference content viewport differs '+case);require('full' in regions and regions['full']==[0,0,*reference.size],'whole viewport comparison required');result={}
 for name,box in regions.items():
  require(len(box)==4 and all(type(v) is int for v in box) and 0<=box[0]<box[2]<=reference.width and 0<=box[1]<box[3]<=reference.height,'invalid comparison region');limit=limits.get((case,name));require(limit is not None,'unapproved raster region '+case+' '+name);m=visual.metrics(reference.crop(tuple(box)),native.crop(tuple(box)));require(m['ssim']>=limit['ssim_min'] and m['changed_pixel_ratio']<=limit['changed_pixel_max'],'UX-M raster fidelity failed '+case+' '+name);result[name]=m
 return result
def browser_folder(folder,contract):
 ids=[c['id'] for c in contract['cases']];require({p.name for p in folder.iterdir()}=={'roster.json'}|{k+ext for k in ids for ext in ('.png','.json')},'browser evidence roster mismatch');require(json_file(folder/'roster.json')==ids,'browser journey roster mismatch');records={}
 for c in contract['cases']:
  key=c['id'];data=json_file(folder/(key+'.json'));u.verify_browser_case(contract,data);image(folder/(key+'.png'),c['viewport']);records[key]={'geometry':sha(folder/(key+'.json')),'png':sha(folder/(key+'.png'))}
 return records
def verify_repeat(first,second,contract):
 a=browser_folder(first,contract);b=browser_folder(second,contract);require(a.keys()==b.keys(),'browser repeat missing case')
 for k in a:
  require(json_file(first/(k+'.json'))==json_file(second/(k+'.json')),'browser repeat geometry differs '+k);require(ImageChops.difference(image(first/(k+'.png')),image(second/(k+'.png'))).getbbox() is None,'browser repeat pixels differ '+k)
 return a
def normalize_native(png,geometry,viewport):
 raw=image(png);d=geometry['density'];b=geometry['insets_px'];require(type(d) in (int,float) and d==int(d) and 1<=d<=4 and len(b)==4 and all(type(v) is int and v>=0 for v in b),'invalid measured density/insets');l,t,r,bottom=b;box=(l,t,raw.width-r,raw.height-bottom);require(box[2]-l==viewport[0]*d and box[3]-t==viewport[1]*d,'native content viewport mismatch; no resize-to-fit');return raw,raw.crop(box).resize(tuple(viewport),Image.Resampling.BOX)
def verify_native_case(root,contract,case,browser_folder_path,native_folder_path,identity,apk_hash,test_hash,out):
 a=contract['activation'];spec=a['native_cases'][case];ref=json_file(browser_folder_path/(case+'.json'));native=json_file(native_folder_path/(case+'.json'));receipt=json_file(native_folder_path/(case+'-receipt.json'))
 require(receipt=={**identity,'case':case,'fixture':spec['fixture'],'apk_sha256':apk_hash,'test_apk_sha256':test_hash,'spec_sha256':u.digest(canonical(spec)),'geometry_sha256':sha(native_folder_path/(case+'.json')),'png_sha256':sha(native_folder_path/(case+'.png')),'journey_steps':len(spec['steps']),'status':'PASS_INSTRUMENTATION'},'native case evidence identity drift '+case)
 require(native['case']==case and native['source']==identity['source_sha'] and native['fixture']==spec['fixture'] and native['journey_steps']==len(spec['steps']),'native journey/fixture mismatch '+case);require(native.get('environment')==spec['environment'],'native environment not equivalent '+case)
 sem=compare_semantics(case,ref['components'],native['components'],contract['deltas']);raw,normalized=normalize_native(native_folder_path/(case+'.png'),native,spec['viewport']);reference=image(browser_folder_path/(case+'.png'),spec['viewport']);raster=compare_raster(case,reference,normalized,spec['region_bounds'],contract['raster']);visual.triptych(reference,normalized,out/(case+'-triptych.png'));status='PENDING_REVIEW';regression=None
 if case in a['goldens']:
  golden=a['goldens'][case];require(golden['environment']==spec['environment'],'golden environment drift '+case);old=json_file(root/golden['geometry']);compare_semantics(case,old['components'],native['components']);regression=visual.metrics(image(root/golden['png']),raw);require(regression['ssim']>=.99 and regression['changed_pixel_ratio']<=.01,'native golden regression '+case);status='PASS_APPROVED_GOLDEN'
 return {'case':case,'source':identity['source_sha'],'tree':identity['source_tree'],'apk_sha256':apk_hash,'native_png_sha256':receipt['png_sha256'],'native_geometry_sha256':receipt['geometry_sha256'],'status':'PASS_FIDELITY','ux_journey':'PASS','visual_approval':status,'semantic':sem,'raster':raster,'native_regression':regression,'triptych_sha256':sha(out/(case+'-triptych.png'))}
def evaluate(root,first,second,native,module,uxm,out,identity):
 import native_smoke as n
 import mod03_visual as m
 c=u.validate(root);a=c['activation'];browser=verify_repeat(first,second,c);require(first.parent==second.parent and json_file(first.parent/'binding.json')=={**identity,'engine':'PINNED_PLAYWRIGHT','reference_sha256':u.HTML_SHA,'records':browser},'browser source/run binding drift')
 n.verify(native,identity);nr=json_file(native/'receipt.json');ah=nr['app_apk_sha256'];th=nr['android_test_apk_sha256'];short={k:identity[k] for k in ('source_sha','source_tree','run_id','run_attempt')};short['run_id']=int(short['run_id']);short['run_attempt']=int(short['run_attempt']);m.verify_artifacts(module,short,ah,th)
 legacy=json_file(uxm/'foundation.json');require(legacy['real_activity'] is True and legacy['source']==identity['source_sha'] and legacy['route']=='legacy-catalog' and legacy['search']['role']=='textbox' and legacy['search']['visible'],'native foundation witness not proven');binding=json_file(uxm/'foundation-receipt.json');require(binding=={**identity,'apk_sha256':ah,'test_apk_sha256':th,'geometry_sha256':sha(uxm/'foundation.json'),'status':'PASS_REAL_LEGACY_ACTIVITY'},'foundation source binding drift')
 expected={'foundation.json','foundation-receipt.json'}|{k+ext for k in a['native_cases'] for ext in ('.json','.png','-receipt.json')};require({p.name for p in uxm.iterdir()}==expected,'native UX-M evidence roster drift');rows=[];out.mkdir(parents=True,exist_ok=False)
 for case in c['cases']:
  k=case['id'];w=case['legacy_witness'];record={'case':k,'owner':case['owner'],'browser':browser[k],'legacy_witness_sha256':None}
  if w:record['legacy_witness_sha256']=sha(module/('native-'+w.split(':',1)[1]+'.png') if w.startswith('module:') else native/(w+'.png'))
  if k in a['native_cases']:record.update(verify_native_case(root,c,k,first,uxm,identity,ah,th,out))
  else:record.update(status='NOT_MIGRATED',ux_journey='PENDING_NATIVE',visual_approval='PENDING',native_regression=None)
  rows.append(record)
 result={**identity,'schema':'hooklab.uxm.evidence.v1','reference_sha256':u.HTML_SHA,'activation_sha256':sha(root/u.REL/'activation.json'),'foundation_sha256':sha(uxm/'foundation.json'),'reference_contract':'PASS','native_fidelity':'PASS_ADMITTED' if a['native_cases'] else 'PENDING_MIGRATION','ux_journeys':'PASS_ADMITTED' if a['native_cases'] else 'PENDING_NATIVE','native_regression':'PASS_APPROVED' if a['goldens'] else 'PENDING_GOLDENS','visual_approval':'PENDING' if set(a['goldens'])!=set(c['semantics']) else 'PASS_APPROVED','cases':rows,'extensions':c['extensions'],'legacy_acceptance_unchanged':True};(out/'receipt.json').write_bytes(canonical(result)+b'\n');return result
def main():
 import tempfile
 import native_smoke as n
 p=argparse.ArgumentParser();p.add_argument('mode',choices=('browser-repeat','bind','compare','verify'));p.add_argument('--first',type=Path,required=True);p.add_argument('--second',type=Path,required=True);p.add_argument('--native',type=Path);p.add_argument('--module',type=Path);p.add_argument('--uxm',type=Path);p.add_argument('--out',type=Path);args=p.parse_args();c=u.validate()
 if args.mode=='browser-repeat':verify_repeat(args.first,args.second,c);print('UXM_BROWSER_REPEAT_PASS',len(c['cases']));return
 identity=n.expected_identity()
 if args.mode=='bind':
  require(args.first.parent==args.second.parent,'reference pair not co-located');records=verify_repeat(args.first,args.second,c);p=args.first.parent/'binding.json';require(not p.exists(),'fresh browser binding required');p.write_bytes(canonical({**identity,'engine':'PINNED_PLAYWRIGHT','reference_sha256':u.HTML_SHA,'records':records})+b'\n');return
 if args.mode=='compare':report=evaluate(ROOT,args.first,args.second,args.native,args.module,args.uxm,args.out,identity)
 else:
  with tempfile.TemporaryDirectory() as d:
   q=Path(d)/'recomputed';report=evaluate(ROOT,args.first,args.second,args.native,args.module,args.uxm,q,identity);require({x.name for x in q.iterdir()}=={x.name for x in args.out.iterdir()},'UX-M report roster drift');require(all(sha(x)==sha(args.out/x.name) for x in q.iterdir()),'UX-M report recomputation drift')
 print('UXM_QUALIFICATION',report['native_fidelity'],report['visual_approval'],flush=True)
if __name__=='__main__':main()
