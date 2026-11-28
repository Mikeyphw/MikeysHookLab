"""Host-side source-bound evidence rehash; hosted job recomputes actual raster metrics."""
import argparse,json
from pathlib import Path
import uxm_contract as u
from qualification import strict_json,require,git
from verify_ui_artifacts import png,roster,read,sha

def obj(p):return strict_json(read(p))
def pair(path,c,identity):
 roster(path,('first','second','binding.json'));ids=[x['id'] for x in c['cases']];records={}
 for part in ('first','second'):
  folder=path/part;roster(folder,['roster.json',*(k+ext for k in ids for ext in ('.json','.png'))]);require(obj(folder/'roster.json')==ids,'browser roster mismatch');observed={}
  for spec in c['cases']:
   k=spec['id'];g=folder/(k+'.json');u.verify_browser_case(c,obj(g));observed[k]={'geometry':sha(g),'png':png(folder/(k+'.png'),tuple(spec['viewport']))}
  if part=='first':records=observed
  else:require(records==observed,'browser repeat bytes drift')
 require(obj(path/'binding.json')=={**identity,'engine':'PINNED_PLAYWRIGHT','reference_sha256':u.HTML_SHA,'records':records},'browser binding drift');return records

def verify(root,design,evidence,independent,native,module,identity):
 c=u.validate(root);a=c['activation'];require(git(root,'rev-parse','HEAD')==identity['source_sha'] and git(root,'rev-parse','HEAD^{tree}')==identity['source_tree'],'checkout drift');pair(design,c,identity);roster(evidence,('browser','native','report'));records=pair(evidence/'browser',c,identity);nf=evidence/'native';rf=evidence/'report';r=obj(rf/'receipt.json');require(read(independent)==read(rf/'receipt.json'),'independent hosted recomputation differs');nr=obj(native/'receipt.json');ah=nr['app_apk_sha256'];th=nr['android_test_apk_sha256'];require(all(nr[k]==v for k,v in identity.items()),'legacy identity drift');require(all(r[k]==v for k,v in identity.items() if k!='schema') and r['schema']=='hooklab.uxm.evidence.v1' and r['reference_sha256']==u.HTML_SHA and r['activation_sha256']==sha(root/u.REL/'activation.json') and r['legacy_acceptance_unchanged'] is True,'report identity drift')
 f=obj(nf/'foundation.json');require(f['real_activity'] is True and f['route']=='legacy-catalog' and f['source']==identity['source_sha'] and f['search']['role']=='textbox' and f['search']['visible'] is True,'foundation witness drift');require(obj(nf/'foundation-receipt.json')=={**identity,'apk_sha256':ah,'test_apk_sha256':th,'geometry_sha256':sha(nf/'foundation.json'),'status':'PASS_REAL_LEGACY_ACTIVITY'} and r['foundation_sha256']==sha(nf/'foundation.json'),'foundation binding drift')
 roster(nf,['foundation.json','foundation-receipt.json',*(k+ext for k in a['native_cases'] for ext in ('.json','.png','-receipt.json'))]);roster(rf,['receipt.json',*(k+'-triptych.png' for k in a['native_cases'])]);require([x['case'] for x in r['cases']]==[x['id'] for x in c['cases']] and r['extensions']==c['extensions'],'report roster drift')
 for spec,row in zip(c['cases'],r['cases']):
  k=spec['id'];w=spec['legacy_witness'];h=sha(module/('native-'+w.split(':',1)[1]+'.png') if w.startswith('module:') else native/(w+'.png')) if w else None;require(row['owner']==spec['owner'] and row['browser']==records[k] and row['legacy_witness_sha256']==h,'case evidence drift '+k)
  if k not in a['native_cases']:require(row['status']=='NOT_MIGRATED' and row['ux_journey']=='PENDING_NATIVE' and row['visual_approval']=='PENDING' and row['native_regression'] is None,'false acceptance '+k);continue
  binding=obj(nf/(k+'-receipt.json'));require(binding['geometry_sha256']==sha(nf/(k+'.json'))==row['native_geometry_sha256'] and binding['png_sha256']==sha(nf/(k+'.png'))==row['native_png_sha256'] and row['triptych_sha256']==sha(rf/(k+'-triptych.png')) and row['status']=='PASS_FIDELITY' and row['ux_journey']=='PASS' and row['source']==identity['source_sha'] and row['tree']==identity['source_tree'] and row['apk_sha256']==ah and row['visual_approval']==('PASS_APPROVED_GOLDEN' if k in a['goldens'] else 'PENDING_REVIEW'),'admitted proof drift '+k)
 require(r['reference_contract']=='PASS' and r['native_fidelity']==('PASS_ADMITTED' if a['native_cases'] else 'PENDING_MIGRATION') and r['ux_journeys']==('PASS_ADMITTED' if a['native_cases'] else 'PENDING_NATIVE') and r['native_regression']==('PASS_APPROVED' if a['goldens'] else 'PENDING_GOLDENS') and r['visual_approval']==('PASS_APPROVED' if set(a['goldens'])==set(c['semantics']) else 'PENDING'),'aggregate acceptance drift');print('UXM_HOSTED_EVIDENCE_REHASH_PASS',r['native_fidelity'],r['visual_approval'])
if __name__=='__main__':
 p=argparse.ArgumentParser()
 for name in ('root','design','evidence','independent','native','module'):p.add_argument('--'+name,type=Path,required=True)
 for name in ('source','tree','run-id','attempt'):p.add_argument('--'+name,required=True)
 x=p.parse_args();verify(x.root,x.design,x.evidence,x.independent,x.native,x.module,{'schema':'hooklab.native.smoke.v1','source_sha':x.source,'source_tree':x.tree,'run_id':x.run_id,'run_attempt':x.attempt,'repository':'Mikeyphw/MikeysHookLab','hosted_emulator_only':True,'physical_device_qualified':False,'release_qualified':False,'visual_approved':False})
