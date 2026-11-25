"""End-to-end verification tests with explicitly synthetic screenshots/receipts.
Uses genuine frozen browser captures; never emits native evidence outside a test temp dir.
"""
import argparse,copy,json,shutil,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
from PIL import Image,ImageDraw
import visual,native_smoke,verify_ui_artifacts
ROOT=Path(__file__).resolve().parents[2]
REF=None
class VisualReportChecks(unittest.TestCase):
    def test_source_bound_report_and_tamper_rejection(self):
        ident={'schema':'hooklab.native.smoke.v1','source_sha':'1'*40,'source_tree':'2'*40,'run_id':'123','run_attempt':'1','repository':'Mikeyphw/MikeysHookLab','hosted_emulator_only':True,'physical_device_qualified':False,'release_qualified':False,'visual_approved':False}
        with tempfile.TemporaryDirectory() as tmp:
            d=Path(tmp);ref=d/'reference';shutil.copytree(REF,ref);nat=d/'synthetic-native';nat.mkdir();out=d/'candidate-report'
            # Simulate hosted manifest field only inside the synthetic test.
            manifest=visual.load(ref/'manifest.json');manifest['engine']='playwright-pinned';visual.dump(ref/'manifest.json',manifest)
            visual.dump(d/'reference-binding.json',{**ident,'reference_manifest_sha256':visual.digest(ref/'manifest.json')});records={}
            for route,case in visual.MAP.items():
                with Image.open(ref/(case+'.png')) as a:
                    b=a.convert('RGB').resize((780,1688));ImageDraw.Draw(b).rectangle((10,10,70,70),fill=(20,200,100));b.save(nat/(route+'.png'))
                controls=visual.browser_controls(visual.load(ref/(case+'.json')),route)
                controls['title']['bounds'][0]+=4
                geo={'route':route,'selected_menu':visual.NAV[route],'real_activity':True,'disconnected':True,'width_dp':390,'density':2,'font_scale':1,'insets_px':[0,48,0,48],'views':[{'class':'View','clickable':False,'enabled':True,'focusable':False,'heading':False,'bounds_px':[0,48,100,148],'width_dp':50,'height_dp':50} for _ in range(11)],'semantic_controls':controls}
                visual.dump(nat/(route+'.json'),geo);records[route]={'png':visual.digest(nat/(route+'.png')),'geometry':visual.digest(nat/(route+'.json'))}
            for route in visual.MAP:
                for ext in ('.png','.json'):shutil.copy2(nat/(route+ext),nat/(route+'-repeat'+ext))
            visual.dump(nat/'fixture.json',native_smoke.FIXTURE_EVIDENCE)
            visual.dump(nat/'framework-fixture.json',native_smoke.FRAMEWORK_EVIDENCE)
            def dex(h,f,m,t):return {'apk_sha256':h*64,'dex_sha256':{'classes.dex':'f'*64},'factory':f,'fixture_marker':m,'test_class':t}
            visual.dump(nat/'release-isolation.json',{'schema':'hooklab.release.fixture-exclusion.v1','debug':dex('a',True,True,False),'release':dex('d',True,False,False),'instrumentation':dex('b',False,True,True),'release_signed_or_published':False,'release_qualified':False})
            visual.dump(nat/'environment.json',{'api':'36','abi':'x86_64','serial':'emulator-5554','qemu':'1','fingerprint':'synthetic','emulator_version':'sdk-package:37.3.10','platform_tools_revision':'36.0','system_image_revision':'1','width_px':780,'height_px':1688,'density_dpi':320,'font_scale':1.0,'offline':True,'renderer_profile':native_smoke.RENDERER_PROFILE,'hwui_renderer':'skiagl','gles_backend':'swiftshader'})
            calibrations={route:{'changed_pixel_ratio':0.0,'ssim':1.0,'mean_abs_rgb':0.0,'max_rgb_delta':0} for route in visual.MAP}
            visual.dump(nat/'receipt.json',{**ident,'status':'BASELINE_PASS_NOT_VISUAL_APPROVAL','tests':native_smoke.TESTS,**{n.replace('-','_')+'_sha256':visual.digest(nat/(n+'.json')) for n in ('fixture','framework-fixture','release-isolation','environment')},'app_apk_sha256':'a'*64,'installed_apk_sha256':'a'*64,'android_test_apk_sha256':'b'*64,'installed_test_apk_sha256':'b'*64,'debug_certificate_sha256':'c'*64,'screenshots':records,'repeat_screenshots':records,'repeat_calibration':calibrations})
            with patch.object(native_smoke,'expected_identity',return_value=ident):
                report=visual.evaluate(ROOT,ref,nat,out);self.assertEqual(report['coverage'],{'reference_cases':24,'candidate_comparisons':3,'approved_cases':0});self.assertFalse(report['visual_approved']);self.assertTrue(all(not x['semantic_controls'][0]['within_candidate_tolerance'] for x in report['cases']))
                self.assertEqual(visual.evaluate(ROOT,ref,nat,out,True),report)
                # Same stdlib rehash function that the operator invokes after exact
                # GitHub run/job/attempt observation, no Pillow in verifier itself.
                download=d/'download';download.mkdir();rf=download/'hooklab-native-reference-123-1';rf.mkdir();shutil.copytree(ref,rf/'first');shutil.copytree(ref,rf/'second');shutil.copy2(d/'reference-binding.json',rf/'reference-binding.json');shutil.copytree(nat,download/'hooklab-native-baseline-1');shutil.copytree(out,download/'hooklab-visual-candidate-123-1');result=verify_ui_artifacts.native(ROOT,download,'1'*40,'2'*40,'123','1');self.assertEqual(result['approved_cases'],0)
                dsg=d/'design-artifact';dsg.mkdir();shutil.copytree(ref,dsg/'first');shutil.copytree(ref,dsg/'second');(dsg/'source-commit.txt').write_text('1'*40+'\n');self.assertEqual(verify_ui_artifacts.design(ROOT,dsg,'1'*40)['reference_cases'],24)
                with self.assertRaisesRegex(ValueError,'design source drift'):verify_ui_artifacts.design(ROOT,dsg,'2'*40)
                bad=download/'hooklab-native-baseline-1'/'home.png';raw=bad.read_bytes();bad.write_bytes(raw[:-4]);self.assertRaisesRegex(ValueError,'PNG|native screenshot hash drift',verify_ui_artifacts.native,ROOT,download,'1'*40,'2'*40,'123','1');bad.write_bytes(raw)

                p=out/'visual-report.json';x=visual.load(p);x['visual_approved']=True;visual.dump(p,x)
                with self.assertRaisesRegex(ValueError,'comparison report'):visual.evaluate(ROOT,ref,nat,out,True)
                visual.dump(p,report);binding=d/'reference-binding.json';x=visual.load(binding);x['run_attempt']='2';visual.dump(binding,x)
                with self.assertRaisesRegex(ValueError,'source/run binding'):visual.evaluate(ROOT,ref,nat,out,True)
                x['run_attempt']='1';visual.dump(binding,x)
                f=nat/'fixture.json';f.unlink()
                with self.assertRaisesRegex(ValueError,'unexpected native evidence'):visual.evaluate(ROOT,ref,nat,out,True)
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--reference',required=True,type=Path);a=p.parse_args();REF=a.reference
    unittest.main(argv=[__file__],verbosity=2)
