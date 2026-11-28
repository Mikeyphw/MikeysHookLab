"""Negative source regressions for frozen UX-M design, semantic and raster gates."""
import copy,json,tempfile,unittest,shutil
from pathlib import Path
import uxm_contract as u
import qualification as q

class UxmContractTests(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name);self.c=u.validate()
  manifest=self.c['manifest'];transition=u.load(u.ROOT,u.REL+'transition.json');paths={u.REL+'manifest.json',u.REL+'activation.json',u.REL+'platform-deltas.json','docs/roadmaps/hooklab-uxm-plan.md'}|{u.REL+x for x in manifest['files']}|set(transition['legacy_reference_hashes'])
  for name in paths:
   p=self.root/name;p.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(u.ROOT/name,p)
 def change(self,name,fn):
  p=self.root/(u.REL+name);d=json.loads(p.read_text());fn(d);p.write_text(json.dumps(d))
 def test_exact_frozen_contract(self):
  c=u.validate(self.root);self.assertEqual(len(c['cases']),50);self.assertEqual(len(c['extensions']),33);self.assertFalse(c['activation']['goldens'])
 def test_reference_mutation(self):
  p=self.root/(u.REL+'hooklab-ux-interactive.html');p.write_bytes(p.read_bytes()+b' ');self.assertRaises(ValueError,u.validate,self.root)
 def test_missing_case(self):
  self.change('cases.json',lambda d:d['cases'].pop());self.assertRaises(ValueError,u.validate,self.root)
 def test_legacy_change(self):
  p=self.root/'design/hooklab/01_REFERENCE/hooklab_ux_revamp.html';p.write_bytes(p.read_bytes()+b' ');self.assertRaises(ValueError,u.validate,self.root)
 def test_admit_without_native(self):
  self.change('activation.json',lambda d:d['admitted'].append('UX-M01'));self.assertRaises(ValueError,u.validate,self.root)
 def test_unapproved_raster(self):
  self.change('platform-deltas.json',lambda d:d['raster_limits'].append({'case':'home','region':'full','ssim_min':0,'changed_pixel_max':1,'review':'','calibration':'','sha256':''}));self.assertRaises(ValueError,u.validate,self.root)
 def test_golden_without_admission(self):
  self.change('activation.json',lambda d:d['goldens'].update(home={}));self.assertRaises(ValueError,u.validate,self.root)
 def test_transition_removal(self):
  self.assertRaises(ValueError,u.transition_guard,self.root,{u.REL+'manifest.json':['100644','blob','a'*40]},{},[],[])
 def test_browser_wrong_journey(self):
  c=self.c['cases'][0];s=self.c['semantics'][c['id']];rows=[{**x,'bounds':[0,0,48,48],'font_sp':14,'radius':0,'visible':True} for x in s['components']];d={'case':c['id'],'viewport':c['viewport'],'theme':c['theme'],'overflow':False,'journey_steps':len(c['steps']),'components':rows};u.verify_browser_case(self.c,d)
  self.assertRaises(ValueError,u.verify_browser_case,self.c,{**d,'journey_steps':0});self.assertRaises(ValueError,u.verify_browser_case,self.c,{**d,'components':rows[:-1]})
if __name__=='__main__':unittest.main()
