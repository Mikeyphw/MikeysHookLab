"""Pinned UX-M reference and monotonic, fail-closed route admission."""
from __future__ import annotations
import hashlib,math,re
from pathlib import Path
from qualification import strict_json,require,clean_path
ROOT=Path(__file__).resolve().parents[2]
REL='design/hooklab/03_UXM/'
MANIFEST_SHA='728ed19b6583a50bff8f290436445eaa5845efeab27c38910513a7820b8ae858'
HTML_SHA='2010fca484ad65f78f7a4cb688fb2fdba80aa8b119546762e6943e44aefc0b58'
OWNERS=tuple(f'UX-M0{i}' for i in range(1,6))
H64=re.compile(r'[0-9a-f]{64}\Z');H40=re.compile(r'[0-9a-f]{40}\Z');KEY=re.compile(r'[a-z0-9][a-z0-9-]{0,80}\Z')
TOL={'bounds_dp':2,'font_sp':1,'rgb_delta':0,'native_ssim_min':.99,'native_changed_pixel_max':.01}
SEMANTIC_KEYS=('id','role','label','state','action')
def digest(raw):return hashlib.sha256(raw).hexdigest()
def read(root,name):
 clean_path(name);path=root/name
 require(path.is_file() and not path.is_symlink() and path.resolve().is_relative_to(root.resolve()) and 0<path.stat().st_size<=16*1024*1024,'missing/unsafe UX-M input '+name)
 return path.read_bytes()
def load(root,name):return strict_json(read(root,name))
def number(value,lo,hi):return type(value) in (int,float) and math.isfinite(value) and lo<=value<=hi
def validate(root=ROOT):
 raw=read(root,REL+'manifest.json');require(digest(raw)==MANIFEST_SHA,'frozen UX-M reference manifest changed; prospective reviewed successor required');m=strict_json(raw)
 require(m['files']['hooklab-ux-interactive.html']==HTML_SHA and m['native_approval'] is False,'reference identity/approval drift')
 for path,expected in m['files'].items():require(digest(read(root,REL+path))==expected,'UX-M reference drift '+path)
 require(digest(read(root,'docs/roadmaps/hooklab-uxm-plan.md'))==m['plan_sha256'],'UX-M adopted plan drift')
 transition=load(root,REL+'transition.json')
 for path,expected in transition['legacy_reference_hashes'].items():require(digest(read(root,path))==expected,'historical reference drift '+path)
 cases=load(root,REL+'cases.json')['cases'];semantic=load(root,REL+'semantics.json')['cases'];extensions=load(root,REL+'extensions.json')['pending']
 require(len(cases)==len(semantic)==m['case_count']==50 and len(extensions)==m['extension_count']==33,'UX-M roster incomplete')
 ids=[c['id'] for c in cases];require(len(set(ids))==len(ids) and all(KEY.fullmatch(x) for x in ids),'invalid case identity');require([s['id'] for s in semantic]==ids,'semantic roster order drift')
 for c,s in zip(cases,semantic):
  require(c['owner'] in OWNERS and c['steps'] and c['viewport']==s['viewport'] and c['theme']==s['theme'],'case fixture drift '+c['id'])
  components=s['components'];require(components and len({x['id'] for x in components})==len(components),'missing/duplicate component IDs '+c['id'])
 require(len({e['id'] for e in extensions})==len(extensions) and all(e['owner'] in (*OWNERS,'MOD-05') and e['state']=='PENDING_REFERENCE' for e in extensions),'extension roster drift')
 a=load(root,REL+'activation.json');d=load(root,REL+'platform-deltas.json')
 require(set(a)=={'schema','admitted','native_cases','extension_admissions','goldens'} and a['schema']=='hooklab.uxm.activation.v1','invalid admission ledger')
 require(type(a['admitted']) is list and a['admitted']==list(OWNERS[:len(a['admitted'])]),'noncontiguous UX-M admission')
 expected={c['id'] for c in cases if c['owner'] in a['admitted']}
 require(set(a['extension_admissions'])=={e['id'] for e in extensions if e['owner'] in a['admitted']},'extension reference admissions incomplete')
 # An extension cannot be admitted by merely listing a reference file. The owning
 # overlay must add its executable reference cases and supported fixture adapters.
 # Until then, fail closed; no fabricated proof or legacy substitution.
 require(not a['extension_admissions'],'extension runner requires reviewed successor implementation before extension admission')
 require(type(a['native_cases']) is dict and set(a['native_cases'])==expected,'native case roster missing or unexpected');require(set(a['goldens'])<=expected,'golden without admitted case')
 from qualification import canonical
 require(digest(canonical(d['global_rules']))=='f8511fddd450a40c62227c4393dc5fc99e0c5d4559739e3f887a690c6e5aaf44','global platform rules drift')
 require(d['schema']=='hooklab.uxm.platform-deltas.v1' and set(d)=={'schema','global_rules','approved_case_deltas','raster_limits'},'invalid platform delta ledger')
 for c in a['native_cases'].values():
  require(set(c)=={'fixture','steps','viewport','theme','environment','region_bounds','capture_root'} and c['fixture'] and c['steps'],'native journey missing')
  require(isinstance(c['steps'],list) and all(type(s) is dict and s.get('op') in ('click','text','check','expect','back','recreate') for s in c['steps']),'invalid native journey operation')
 raster={}
 for row in d['raster_limits']:
  require(set(row)=={'case','region','ssim_min','changed_pixel_max','calibration','sha256','review'} and row['case'] in expected and row['review'],'unreviewed raster limit')
  k=(row['case'],row['region']);require(k not in raster and number(row['ssim_min'],0,1) and number(row['changed_pixel_max'],0,1),'invalid raster threshold')
  require(row['calibration'].startswith(REL+'calibration/') and digest(read(root,row['calibration']))==row['sha256'],'calibration evidence missing');raster[k]=row
 for key,row in a['native_cases'].items():
  require(row['region_bounds'] and {r for c,r in raster if c==key}==set(row['region_bounds']),'missing independently calibrated region '+key)
  require(row['viewport']==next(c['viewport'] for c in cases if c['id']==key) and row['theme']==next(c['theme'] for c in cases if c['id']==key),'native fixture identity drift '+key)
 for g,row in a['goldens'].items():
  require(set(row)=={'png','sha256','geometry','geometry_sha256','source','tree','apk_sha256','reference_sha256','environment','review','fidelity_receipt','fidelity_sha256'},'incomplete golden admission '+g)
  require(row['review'] and H40.fullmatch(row['source']) and H40.fullmatch(row['tree']) and H64.fullmatch(row['apk_sha256']) and row['reference_sha256']==HTML_SHA,'unreviewed/stale golden '+g)
  for p,h in [('png','sha256'),('geometry','geometry_sha256'),('fidelity_receipt','fidelity_sha256')]:require(row[p].startswith(REL+'goldens/') and digest(read(root,row[p]))==row[h],'golden tamper '+g)
  receipt=load(root,row['fidelity_receipt']);require(receipt['case']==g and receipt['source']==row['source'] and receipt['tree']==row['tree'] and receipt['apk_sha256']==row['apk_sha256'] and receipt['status']=='PASS_FIDELITY' and receipt['native_png_sha256']==row['sha256'] and receipt['native_geometry_sha256']==row['geometry_sha256'],'golden fidelity binding mismatch '+g)
 for row in d['approved_case_deltas']:
  require(set(row)=={'case','component','field','reference','native','review','reason'} and row['case'] in expected and row['field'] in ('label','state','background','radius') and row['review'] and row['reason'],'invalid/unreviewed semantic delta')
 return {'manifest':m,'cases':cases,'semantics':{s['id']:s for s in semantic},'extensions':extensions,'activation':a,'raster':raster,'deltas':d['approved_case_deltas']}
def transition_guard(root,before,after,changes,intents):
 from qualification import git
 anchor=REL+'manifest.json'
 if anchor not in after:
  require(anchor not in before and not any(x.startswith('UX-M') for x in intents),'UX-M contract removal or missing anchor refused');return
 data=validate(root);a=data['activation'];t=load(root,REL+'transition.json')
 require(git(root,'rev-parse',t['predecessor_commit']+'^{tree}')==t['predecessor_tree'],'historical roadmap tree unavailable or drifted')
 for path,row in t['prior_roadmap_files'].items():require(digest(git(root,'show',t['predecessor_commit']+':'+path,raw=True))==row['sha256'],'historical roadmap definition drift '+path)
 expected=[owner for owner in OWNERS if f'config/qualification-intents/{owner}.json' in after]
 require(a['admitted']==expected,'declared UI overlay must admit every assigned route and extension')
 if anchor in before:
  require(before[anchor]==after[anchor],'frozen reference replacement refused')
  old=strict_json(git(root,'show',before[REL+'activation.json'][2],raw=True));require(set(old['admitted'])<=set(a['admitted']) and set(old['goldens'])<=set(a['goldens']),'admission/golden removal refused')
  prior=strict_json(git(root,'show',before[REL+'platform-deltas.json'][2],raw=True));current=load(root,REL+'platform-deltas.json')
  for field in ('approved_case_deltas','raster_limits'):require(all(row in current[field] for row in prior[field]),'approved platform calibration removed or modified')
  for k,v in old['native_cases'].items():require(a['native_cases'][k]==v,'admitted native journey changed without reviewed successor')
  for k,v in old['goldens'].items():require(a['goldens'][k]==v,'golden replacement requires reviewed successor protocol')
 final=any(f'config/qualification-intents/{x}.json' in after for x in ('UX-M06','UX-R05'))
 if final:require(a['admitted']==list(OWNERS) and set(a['goldens'])==set(a['native_cases']),'final acceptance requires all routes and reviewed goldens')
 if any(x.startswith('UX-M') and x!='UX-M00' for x in intents):require(any(x in OWNERS for x in intents) or final,'unknown UX-M transition')
def verify_browser_case(contract,data):
 key=data['case'];expected=contract['semantics'][key];require(data['overflow'] is False and data['viewport']==expected['viewport'] and data['theme']==expected['theme'],'browser fixture mismatch '+key)
 require(data['journey_steps']==len(next(c['steps'] for c in contract['cases'] if c['id']==key)),'browser journey incomplete '+key)
 require([{k:x[k] for k in SEMANTIC_KEYS} for x in data['components']]==expected['components'],'browser semantic drift '+key)
 for row in data['components']:require(len(row['bounds'])==4 and all(number(v,-100000,100000) for v in row['bounds']) and min(row['bounds'][2:])>0 and number(row['font_sp'],0,100) and number(row['radius'],0,10000) and type(row['visible']) is bool,'invalid browser geometry '+key)
if __name__=='__main__':
 c=validate();print(f'UXM_REFERENCE_CONTRACT_PASS {len(c["cases"])} frozen journeys; {len(c["extensions"])} extension requirements; {len(c["activation"]["admitted"])} admitted owners')
