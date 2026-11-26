"""Synthetic verifier regression tests. These are not emulator receipts."""
import json,tempfile,unittest
from pathlib import Path
import adaptive_matrix as a

class AdaptiveMatrixTests(unittest.TestCase):
 def test_matrix_covers_required_configurations_and_ci_paths(self):
  cases=list(a.CASES.values());self.assertEqual({x[0] for x in cases},{29,36});self.assertTrue({320,360,390,393,600,840}<={x[1] for x in cases});self.assertEqual({x[3] for x in cases},{1.,1.6,2.});self.assertEqual({x[4] for x in cases},{'dark','light','system','amoled','dynamic'});self.assertTrue(any(x[1]>x[2] for x in cases))
  from qualification import CASES as selected_cases
  import re
  root=Path(__file__).resolve().parents[2];workflow=(root/'.github/workflows/hooklab-ui-extended.yml').read_text();self.assertIn('needs: [build, adaptive]',workflow);self.assertIn('needs: build',workflow);self.assertEqual(workflow.count(':app:assembleDebugAndroidTest'),1);self.assertIn('publish-apks',workflow);self.assertIn('if: always()',workflow)
  matrix=json.loads(re.search(r"fromJSON\(inputs.cases \|\| '([^']+)'\)",workflow).group(1));self.assertEqual(matrix,[{'case':k,'api':v[0]} for k,v in a.CASES.items()]);self.assertEqual(selected_cases,{k:v[0] for k,v in a.CASES.items()})
  self.assertIn('workflow_call:',(root/'.github/workflows/hooklab-ci24.yml').read_text())
  coordinator=(root/'.github/workflows/android.yml').read_text();self.assertIn('needs.impact.outputs.ci24',coordinator);self.assertIn('uses: ./.github/workflows/hooklab-ci24.yml',coordinator);self.assertIn('--selected-json',workflow)
 @staticmethod
 def frames():
  return {'schema':'hooklab.uxr04.native-frames.v1','workload':'timeline_scroll_4000_events','requested_scroll_steps':80,'completed_scroll_steps':80,'dropped_callbacks':2,'samples':[{'intended_vsync_ns':i*16666667,'duration_ns':i*1000000} for i in range(1,21)]}
 def test_frame_measurement_rejects_missing_duplicate_and_invalid_records(self):
  import copy
  evidence=self.frames();self.assertEqual(a.frame_durations(evidence),[float(x) for x in range(1,21)]);self.assertEqual(a.p95(a.frame_durations(evidence)),19.)
  bad=[{},dict(evidence,samples=[]),dict(evidence,completed_scroll_steps=79),dict(evidence,dropped_callbacks=-1),dict(evidence,samples=evidence['samples'][:-1]),dict(evidence,workload='synthetic')]
  for key,value in [('duration_ns',0),('duration_ns',True),('intended_vsync_ns',evidence['samples'][0]['intended_vsync_ns']),('duration_ns',2**63-1)]:
   changed=copy.deepcopy(evidence);changed['samples'][1][key]=value;bad.append(changed)
  for x in bad:
   with self.assertRaises(ValueError):a.frame_durations(x)
  with self.assertRaises(ValueError):a.p95([float('nan')])
 def test_failure_evidence_allowlists_phase_classes_and_frames(self):
  raw='INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: ADAPTIVE_PHASE_SEARCH_CLOSE_IME\nCaused by: androidx.test.espresso.PerformException: PRIVATE VIEW DUMP\n  at com.mikeyphw.hooklab.qa.AdaptiveUiTest.adaptiveJourney(AdaptiveUiTest.java:147)\nINSTRUMENTATION_STATUS_CODE: -2\nADAPTIVE_PHASE_PRIVATE_PAYLOAD'
  report=a.failure_info(raw,a.JOURNEY,0)
  self.assertEqual(report['phases'],['SEARCH_CLOSE_IME']);self.assertEqual(report['exception_classes'],['java.lang.AssertionError','androidx.test.espresso.PerformException']);self.assertEqual(report['status_codes'],[['STATUS_CODE',-2]]);self.assertEqual(len(report['source_frames']),1);self.assertNotIn('PRIVATE',json.dumps(report))
 def test_checkpoint_phase_rejects_wrong_source_or_test(self):
  from unittest.mock import patch
  row={'schema':'hooklab.uxr04.phase.v1','phase':'VIEWPORT_READY','test':'adaptiveJourney','source':'a'*40}
  with patch.object(a.n,'raw',side_effect=lambda *args:json.dumps(row).encode()):
   self.assertEqual(a.checkpoint_phase('a'*40,a.JOURNEY),'VIEWPORT_READY')
   self.assertIsNone(a.checkpoint_phase('b'*40,a.JOURNEY))
   self.assertIsNone(a.checkpoint_phase('a'*40,a.PERF))
   row['phase']='untrusted diagnostic content';self.assertIsNone(a.checkpoint_phase('a'*40,a.JOURNEY))
 def test_cold_launch_is_distinct_from_native_frame_workload(self):
  from unittest.mock import patch
  from types import SimpleNamespace
  with patch.object(a.n,'adb') as adb,patch.object(a.n,'txt',side_effect=['101','102','103','104','105']),patch.object(a,'launch',side_effect=['Status: ok\nTotalTime: '+str(x) for x in [100,200,300,400,500]]),patch.object(a.subprocess,'run',return_value=SimpleNamespace(returncode=1,stdout='')):
   r=a.launch_performance({'native_frames':self.frames()});self.assertEqual(r['cold_launch_pids'],[101,102,103,104,105]);self.assertEqual(r['cold_launch_p95_ms'],500);self.assertEqual(r['frame_p95_ms'],19.);self.assertEqual(adb.call_count,5)
  with patch.object(a.n,'adb'),patch.object(a.subprocess,'run',return_value=SimpleNamespace(returncode=0,stdout='101')):
   with self.assertRaisesRegex(ValueError,'preexisting process'):a.launch_performance({'native_frames':self.frames()})
 def test_matrix_rotation_is_reasserted_at_every_instrumentation_boundary(self):
  from unittest.mock import patch
  from types import SimpleNamespace
  cfg=a.config('api36-landscape')
  with patch.object(a.n,'adb') as adb,patch.object(a.n,'txt',side_effect=lambda *args: 'lock 1' if args[:2]==('shell','wm') else '0' if args[-1]=='accelerometer_rotation' else '1'),patch.object(a.n,'clear_failure_checkpoint'),patch.object(a.n,'parse_instrumentation'),patch.object(a.subprocess,'run',return_value=SimpleNamespace(returncode=0,stdout='')):
   for method in (a.JOURNEY,a.PERF):a.instrument(method,cfg,'a'*40,Path('.'))
   self.assertEqual([x.args for x in adb.call_args_list if x.args[:3]==('shell','wm','user-rotation')],[('shell','wm','user-rotation','lock','1')]*2)
   self.assertEqual(sum(x.args[-1]=='files/uxr04-adaptive/viewport-failure.json' for x in adb.call_args_list),2)
  with patch.object(a.n,'adb'),patch.object(a.n,'txt',return_value='free'),patch.object(a.subprocess,'run') as child:
   with self.assertRaisesRegex(ValueError,'rotation lock'):a.instrument(a.PERF,cfg,'a'*40,Path('.'))
   child.assert_not_called()
  with patch.object(a.n,'adb') as adb,patch.object(a.n,'txt',return_value='0') as read:
   a.lock_matrix_rotation(a.config('api29-compact'));self.assertEqual([x.args for x in adb.call_args_list],[('shell','settings','put','system','accelerometer_rotation','0'),('shell','settings','put','system','user_rotation','0')]);self.assertEqual(read.call_count,2)
  with patch.object(a.n,'adb'),patch.object(a.n,'txt',return_value='1'):
   with self.assertRaisesRegex(ValueError,'rotation settings'):a.lock_matrix_rotation(a.config('api29-compact'))
 def test_home_uses_semantic_action_and_keeps_full_accessibility_gate(self):
  root=Path(__file__).resolve().parents[2];test=(root/'app/src/androidTest/java/com/mikeyphw/hooklab/qa/AdaptiveUiTest.java').read_text();activity=(root/'app/src/main/java/com/mikeyphw/hooklab/ui/MainActivity.java').read_text()
  home=test.split('phase("HOME_SCROLL")',1)[1].split('phase("OPEN_HOOKS")',1)[0]
  self.assertIn('"r04:action:"+R.string.ui06_browse_hooks',home);self.assertIn('isDisplayingAtLeast(90)',home);self.assertIn('target.isClickable()',home);self.assertIn('capture(s,"home",records)',home)
  self.assertIn('AccessibilityChecks.enable().setRunChecksFromRootView(true)',test);self.assertNotIn('setSuppressingResultMatcher',test)
  self.assertIn('row(shortcuts,R.string.ui06_browse_hooks',activity)
  fixture=(root/'app/src/androidTest/java/com/mikeyphw/hooklab/ui/FrameworkFixtureTest.java').read_text();section=fixture.split('stage="R02_CATALOG_SEARCH_REVEAL"',1)[1].split('stage="HEALTH_EXACT_ROUTE"',1)[0]
  self.assertIn('withId(R.id.hooklab_catalog_search)).perform(scrollTo(),click())',section);self.assertIn('replaceText("Neo Store")',section);self.assertIn('closeSoftKeyboard()',section);self.assertIn('s.recreate()',section);self.assertIn('assertEquals(beforeNavigation,replacement.writes.get())',section)
  mode=activity.split('private Chip catalogChip',1)[1].split('private boolean searchExpanded',1)[0];self.assertIn('catalogScroll=0;catalogFocusedRow=null',mode);self.assertIn('generation!=userInteractionGeneration',mode);self.assertIn('field!=catalogSearch',mode)
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
   a.dump(out/'performance.json',{'schema':'hooklab.uxr04.performance.v2','events':4000,'dropped':1,'page_size':30,'budget_enforced':False,'import_ms':23,'page_action_ms':[1,2,3,4,5],'sampled_pss_kb':[100,102,103,101,100],'native_frames':self.frames()})
   a.dump(out/'launch-performance.json',{'cold_launch_ms':[100,200,300,400,500],'cold_launch_pids':[101,102,103,104,105],'cold_launch_p95_ms':500,'frame_ms':a.frame_durations(self.frames()),'frame_p95_ms':19.,'frame_workload':'timeline_scroll_4000_events','budget_enforced':False})
   build={**a.apk_identity(expected),'apks':{'app':{'sha256':'a'*64,'size':100},'test':{'sha256':'a'*64,'size':100}}}
   r={**expected,'case':case,'configuration':cfg,'status':'PASS_EXTENDED_NOT_DEVICE_APPROVAL','completed_tests':a.roster(case),'apk_build':build,**{k:'a'*64 for k in ('app_apk_sha256','android_test_apk_sha256','installed_apk_sha256','installed_test_apk_sha256')},'performance_summary':{'scenario_launch_ms':200,'page_action_p95_ms':5,'sampled_peak_pss_kb':103,'cold_launch_p95_ms':500,'frame_p95_ms':19.,'budget_enforced':False}}
   def receipt():
    r['files']={f.name:a.sha(f) for f in out.iterdir() if f.name!='receipt.json'};a.dump(out/'receipt.json',r)
   receipt();self.assertEqual(a.validate(out,case,expected,build),r)
   lp=out/'launch-performance.json';good=lp.read_bytes();bad=json.loads(good);bad['frame_ms'][0]=999.;a.dump(lp,bad);receipt()
   with self.assertRaisesRegex(ValueError,'launch/frame evidence drift'):a.validate(out,case,expected,build)
   lp.write_bytes(good);receipt()
   f=out/'home.hierarchy.json';old=f.read_bytes();f.write_bytes(old+b' ')
   with self.assertRaisesRegex(ValueError,'digest drift'):a.validate(out,case,expected,build)
   a.dump(f,[{'bounds':[0,0,9999,100],'clickable':True,'enabled':True,'focusable':True,'checked':False,'labelled':True}]);receipt()
   with self.assertRaisesRegex(ValueError,'node bounds'):a.validate(out,case,expected,build)
   f.write_bytes(old);receipt();r['installed_test_apk_sha256']='b'*64;a.dump(out/'receipt.json',r)
   with self.assertRaisesRegex(ValueError,'installed APK drift'):a.validate(out,case,expected,build)
   r['installed_test_apk_sha256']='a'*64;receipt()
   with self.assertRaisesRegex(ValueError,'shared APK identity'):a.validate(out,case,expected,dict(build,run_attempt='1'))

 def test_shared_apk_artifact_is_source_attempt_and_byte_bound(self):
  import zipfile
  with tempfile.TemporaryDirectory() as t:
   root=Path(t);expected={'source_sha':'a'*40,'source_tree':'b'*40,'run_id':'77','run_attempt':'2','repository':a.n.PKG}
   for name in a.APK_FILES.values():
    with zipfile.ZipFile(root/name,'w') as z:z.writestr('AndroidManifest.xml','fixture');z.writestr('classes.dex',name)
   record={**a.apk_identity(expected),'apks':{k:a.apk_file(root/v) for k,v in a.APK_FILES.items()}}
   a.dump(root/'apk-build.json',record);self.assertEqual(a.shared_apks(root,expected),record)
   for key,value in [('source_sha','c'*40),('source_tree','c'*40),('run_attempt','1'),('run_id','78')]:
    with self.assertRaisesRegex(ValueError,'identity/digest'):a.shared_apks(root,dict(expected,**{key:value}))
   (root/'extra').touch()
   with self.assertRaisesRegex(ValueError,'roster'):a.shared_apks(root,expected)
   (root/'extra').unlink()
   with zipfile.ZipFile(root/'app.apk','a') as z:z.writestr('changed','bytes')
   with self.assertRaisesRegex(ValueError,'identity/digest'):a.shared_apks(root,expected)
 def test_action_ownership_and_resource_identity_are_unambiguous(self):
  def node(text='',desc='',rid='',click='false',bounds='[10,10][100,100]',children=''):
   return f'<node package="{a.n.PKG}" text="{text}" content-desc="{desc}" resource-id="{rid}" clickable="{click}" enabled="true" bounds="{bounds}">{children}</node>'
  label=node('Diagnostics');owner=node(desc='Diagnostics',click='true',children=label)
  navigation=node(rid=a.CONTROL_IDS['primary_navigation'],children=owner)
  root=a.ET.fromstring('<hierarchy>'+navigation+node('Diagnostics')+'</hierarchy>')
  self.assertEqual(a.action_nodes(root,'Diagnostics',scope='primary_navigation'),[(10,10,100,100)])
  root=a.ET.fromstring('<hierarchy>'+node('Choose target')+node('Choose target',rid=a.CONTROL_IDS['capture_target'],click='true')+'</hierarchy>')
  self.assertEqual(len(a.control_nodes(root,'capture_target',action=True)),1)
  root=a.ET.fromstring('<hierarchy>'+owner+owner+'</hierarchy>')
  self.assertEqual(len(a.action_nodes(root,'Diagnostics')),2)
  from unittest.mock import patch
  with patch.object(a,'live_xml',return_value=root),patch.object(a.n,'adb') as adb:
   with self.assertRaisesRegex(ValueError,'ambiguous.*Diagnostics'):a.tap_control('Diagnostics',a.config('api36-390'))
   adb.assert_not_called()
 def test_production_capture_ids_and_chrome_targets(self):
  root=Path(__file__).resolve().parents[2];activity=(root/'app/src/main/java/com/mikeyphw/hooklab/ui/MainActivity.java').read_text();flat=(root/'app/src/main/java/com/mikeyphw/hooklab/ui/FlatUi.java').read_text();test=(root/'app/src/androidTest/java/com/mikeyphw/hooklab/qa/AdaptiveUiTest.java').read_text();ids=(root/'app/src/main/res/values/uxr04_ids.xml').read_text()
  for resource in a.CONTROL_IDS.values():
   name=resource.split('/')[-1];self.assertIn('R.id.'+name,activity);self.assertIn('name="'+name+'"',ids)
  self.assertIn('FlatUi.enforceChromeTargets(toolbar,navigation)',activity);self.assertIn('setItemMinimumHeight(dp(nav,64))',flat);self.assertIn('view.setMinimumWidth(minimum)',flat);self.assertIn('view.setMinimumHeight(minimum)',flat)
  self.assertEqual(test.count('prepareMatrixOrientation();'),3);self.assertIn('ins.getUiAutomation().setRotation(width>height',test);self.assertIn('display.getRealSize(real)',test);self.assertIn('real.x==width&&real.y==height',test);self.assertIn('ViewCompat.setOnApplyWindowInsetsListener(navigation,(v,insets)->insets)',activity);self.assertIn('.isOverflowButton',flat);self.assertIn('params.width=dp(action,Ui08QualityPolicy.MIN_TOUCH_DP)',flat);self.assertIn('navigation visible target below 48dp',test);self.assertIn('toolbar visible target below 48dp',test);self.assertIn('captureChoiceDialog(R.string.dx02_choose_target',activity);self.assertIn('captureChoiceDialog(R.string.dx02_choose_source',activity);self.assertIn('FlatUi.actionRow(row,v->{dialog.dismiss();selected.accept(index);})',activity)
  self.assertIn('AccessibilityChecks.enable().setRunChecksFromRootView(true)',test);self.assertNotIn('setSuppressingResultMatcher',test)

 def test_home_geometry_checkpoint_rejects_private_and_stale_data(self):
  from unittest.mock import patch
  row={'schema':'hooklab.uxr04.home-targets.v1','source':'a'*40,'targets':[{'id':201,'class':'com.google.android.material.navigationrail.NavigationRailItemView','width_px':176,'height_px':128,'visible_width_px':176,'visible_height_px':128,'minimum_px':96}]}
  with patch.object(a.n,'raw',side_effect=lambda *args:json.dumps(row).encode()):
   self.assertEqual(a.home_geometry_checkpoint('a'*40),row)
   self.assertIsNone(a.home_geometry_checkpoint('b'*40))
   row['targets'][0]['text']='PRIVATE';self.assertIsNone(a.home_geometry_checkpoint('a'*40));row['targets'][0].pop('text')
   row['targets'][0]['class']='private.arbitrary.Payload';self.assertIsNone(a.home_geometry_checkpoint('a'*40))
 def test_publish_apks_uses_exact_built_pair_and_rejects_reuse(self):
  import zipfile
  from unittest.mock import patch
  with tempfile.TemporaryDirectory() as t:
   root=Path(t);app=root/'app-source.apk';test=root/'test-source.apk';out=root/'published'
   for apk in (app,test):
    with zipfile.ZipFile(apk,'w') as z:z.writestr('AndroidManifest.xml','fixture');z.writestr('classes.dex',apk.name)
   expected={'source_sha':'a'*40,'source_tree':'b'*40,'run_id':'77','run_attempt':'2','repository':'Mikeyphw/MikeysHookLab'}
   with patch.object(a.n,'one',side_effect=[app,test]):
    build=a.publish_apks(out,expected)
   self.assertEqual(build['apks']['app']['sha256'],a.sha(app));self.assertEqual(build['apks']['test']['sha256'],a.sha(test));self.assertEqual(a.shared_apks(out,expected),build)
   with self.assertRaisesRegex(ValueError,'fresh shared APK'):a.publish_apks(out,expected)

 def test_dialog_choice_uses_unique_real_resource_and_dialog_scroll_bounds(self):
  from unittest.mock import patch
  cfg=a.config('api36-390');rid=a.CONTROL_IDS['capture_research']
  viewport=f'<node package="{a.n.PKG}" scrollable="true" enabled="true" bounds="[100,200][680,1200]"/>'
  root=a.ET.fromstring('<hierarchy>'+viewport+'</hierarchy>')
  choice=a.ET.fromstring(f'<hierarchy><node package="{a.n.PKG}" resource-id="{rid}" clickable="true" enabled="true" bounds="[120,800][660,960]"/></hierarchy>')
  with patch.object(a,'live_xml',side_effect=[root,choice]),patch.object(a.n,'adb') as adb:
   a.tap_control('research',cfg,resource='capture_research',scroll=True)
   self.assertEqual(adb.call_args_list[0].args,('shell','input','swipe','390','950','390','450','400'))
   self.assertEqual(adb.call_args_list[1].args,('shell','input','tap','390','880'))
  duplicate=a.ET.fromstring('<hierarchy>'+a.ET.tostring(choice[0],encoding='unicode')*2+'</hierarchy>')
  with patch.object(a,'live_xml',return_value=duplicate),patch.object(a.n,'adb') as adb:
   with self.assertRaisesRegex(ValueError,'ambiguous'):a.tap_control('research',cfg,resource='capture_research')
   adb.assert_not_called()

 def test_viewport_failure_rejects_wrong_source_test_and_extra_private_data(self):
  from unittest.mock import patch
  row={'schema':'hooklab.uxr04.viewport-failure.v1','source':'a'*40,'test':'adaptiveJourney','width_px':1688,'height_px':780,'rotation':1,'density_dpi':320,'width_dp':844,'height_dp':390,'font_scale':1.6}
  with patch.object(a.n,'raw',side_effect=lambda *args:json.dumps(row).encode()):
   self.assertEqual(a.viewport_failure('a'*40,a.JOURNEY),row)
   self.assertIsNone(a.viewport_failure('b'*40,a.JOURNEY));self.assertIsNone(a.viewport_failure('a'*40,a.PERF))
   row['rotation']=True;self.assertIsNone(a.viewport_failure('a'*40,a.JOURNEY));row['rotation']=1
   row['text']='PRIVATE';self.assertIsNone(a.viewport_failure('a'*40,a.JOURNEY))
