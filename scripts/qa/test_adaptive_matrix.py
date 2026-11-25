"""Synthetic verifier regression tests. These are not emulator receipts."""
import json,tempfile,unittest
from pathlib import Path
import adaptive_matrix as a

class AdaptiveMatrixTests(unittest.TestCase):
 def test_matrix_covers_required_configurations_and_ci_paths(self):
  cases=list(a.CASES.values());self.assertEqual({x[0] for x in cases},{29,36});self.assertTrue({320,360,390,393,600,840}<={x[1] for x in cases});self.assertEqual({x[3] for x in cases},{1.,1.6,2.});self.assertEqual({x[4] for x in cases},{'dark','light','system','amoled','dynamic'});self.assertTrue(any(x[1]>x[2] for x in cases))
  root=Path(__file__).resolve().parents[2];workflow=(root/'.github/workflows/hooklab-ui-extended.yml').read_text();self.assertEqual(workflow.count('- case:'),len(cases));self.assertIn('needs: adaptive',workflow);self.assertIn('if: always()',workflow)
  self.assertIn("- 'app/**'",(root/'.github/workflows/hooklab-ci24.yml').read_text())
  for case,api,*_ in [(k,*v) for k,v in a.CASES.items()]:self.assertIn(f'- case: {case}\n            api: {api}',workflow)
 def test_frame_measurement_rejects_empty_and_bad_rows(self):
  text='---PROFILEDATA---\nFlags,IntendedVsync,FrameCompleted,\n0,1000000,5000000,\n1,1000000,9000000,\n0,2000000,12000000,\n---PROFILEDATA---'
  self.assertEqual(a.frame_durations(text),[4.,10.]);self.assertEqual(a.p95([1,2,3,4,5]),5)
  for s in ('',text.replace('0,2000000,12000000,','0,2000000,')):
   with self.assertRaises(ValueError):a.frame_durations(s)
  with self.assertRaises(ValueError):a.p95([float('nan')])
 def test_hierarchy_projection_does_not_export_text(self):
  row='<hierarchy><node package="com.mikeyphw.hooklab" text="PRIVATE" content-desc="PRIVATE" bounds="[0,0][100,100]" clickable="true" enabled="true" focusable="true" checked="false"/></hierarchy>'
  nodes=a.hierarchy(row,a.config('api29-compact'));self.assertEqual(nodes,[{'bounds':[0,0,100,100],'clickable':True,'enabled':True,'focusable':True,'checked':False,'labelled':True}]);self.assertNotIn('PRIVATE',json.dumps(nodes))
  for bad in (row.replace('[100,100]','[9999,100]'),row.replace('clickable="true"','clickable="false"'),row.replace('com.mikeyphw.hooklab','com.other.app')):
   with self.assertRaises(ValueError):a.hierarchy(bad,a.config('api29-compact'))
 def test_missing_and_extra_shards_refused(self):
  with tempfile.TemporaryDirectory() as t:
   root=Path(t)
   with self.assertRaisesRegex(ValueError,'missing or extra'):a.verify_all(root,{})
   for case in a.CASES:(root/case).mkdir()
   (root/'extra').mkdir()
   with self.assertRaisesRegex(ValueError,'missing or extra'):a.verify_all(root,{})
 def test_skipped_or_wrong_attempt_refused_before_file_validation(self):
  with tempfile.TemporaryDirectory() as t:
   out=Path(t);case='api29-compact';expected={'source_sha':'a'*40,'run_attempt':'2'}
   r={**expected,'case':case,'configuration':a.config(case),'status':'PASS_EXTENDED_NOT_DEVICE_APPROVAL','completed_tests':a.roster(case)[:-1]};a.dump(out/'receipt.json',r)
   with self.assertRaisesRegex(ValueError,'roster incomplete'):a.validate(out,case,expected)
   r['completed_tests']=a.roster(case);r['run_attempt']='1';a.dump(out/'receipt.json',r)
   with self.assertRaisesRegex(ValueError,'identity mismatch'):a.validate(out,case,expected)

 def test_complete_case_and_independent_tamper_rejection(self):
  from PIL import Image
  with tempfile.TemporaryDirectory() as t:
   out=Path(t);case='api29-compact';cfg=a.config(case);expected={'source_sha':'a'*40,'source_tree':'b'*40,'run_id':'7','run_attempt':'2','repository':'Mikeyphw/MikeysHookLab','schema':'hooklab.uxr04.case.v1','hosted_emulator_only':True,'physical_device_qualified':False,'release_qualified':False,'visual_approved':False}
   routes=[]
   for i,route in enumerate(a.ROUTES):
    row={'route':route,'width_dp':320,'height_dp':660,'font_scale':2.,'orientation':1,'headings':1,'actions':2,'focusable':2,'visible_actions':1,'text_contrast_min':5.,'primary_contrast':8.}
    if route in ('home','hooks','diagnostics'):row['rail']=False
    routes.append(row)
    image=Image.new('RGB',(cfg['width_px'],cfg['height_px']),(10+i,20,30));image.putpixel((0,0),(255,0,0));image.save(out/(route+'.png'))
    a.dump(out/(route+'.hierarchy.json'),[{'bounds':[0,0,100,100],'clickable':True,'enabled':True,'focusable':True,'checked':False,'labelled':True}])
   a.dump(out/'journey.json',{'schema':'hooklab.uxr04.adaptive.v1','api':29,'theme':'light','source':expected['source_sha'],'framework_disconnected':True,'physical_device_qualified':False,'routes':routes,'ime_observed':True,'orientation_round_trip':True,'separate_catalog_targets':True,'scenario_launch_ms':200})
   a.dump(out/'performance.json',{'schema':'hooklab.uxr04.performance.v1','events':4000,'dropped':1,'page_size':30,'budget_enforced':False,'import_ms':23,'page_action_ms':[1,2,3,4,5],'sampled_pss_kb':[100,102,103,101,100]})
   a.dump(out/'launch-performance.json',{'cold_launch_ms':[100,200,300,400,500],'cold_launch_p95_ms':500,'frame_ms':[2.,4.,6.],'frame_p95_ms':6.,'budget_enforced':False})
   r={**expected,'case':case,'configuration':cfg,'status':'PASS_EXTENDED_NOT_DEVICE_APPROVAL','completed_tests':a.roster(case),**{k:'a'*64 for k in ('app_apk_sha256','android_test_apk_sha256','installed_apk_sha256','installed_test_apk_sha256')},'performance_summary':{'scenario_launch_ms':200,'page_action_p95_ms':5,'sampled_peak_pss_kb':103,'cold_launch_p95_ms':500,'frame_p95_ms':6.,'budget_enforced':False}}
   def receipt():
    r['files']={f.name:a.sha(f) for f in out.iterdir() if f.name!='receipt.json'};a.dump(out/'receipt.json',r)
   receipt();self.assertEqual(a.validate(out,case,expected),r)
   f=out/'home.hierarchy.json';old=f.read_bytes();f.write_bytes(old+b' ')
   with self.assertRaisesRegex(ValueError,'digest drift'):a.validate(out,case,expected)
   a.dump(f,[{'bounds':[0,0,9999,100],'clickable':True,'enabled':True,'focusable':True,'checked':False,'labelled':True}]);receipt()
   with self.assertRaisesRegex(ValueError,'node bounds'):a.validate(out,case,expected)
   f.write_bytes(old);receipt();r['installed_test_apk_sha256']='b'*64;a.dump(out/'receipt.json',r)
   with self.assertRaisesRegex(ValueError,'installed APK drift'):a.validate(out,case,expected)
