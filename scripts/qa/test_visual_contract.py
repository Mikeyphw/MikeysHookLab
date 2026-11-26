"""Static visual contract tests: no Android, Gradle or third-party deps."""
import copy,json,tempfile,unittest,shutil
from pathlib import Path
import visual
ROOT=Path(__file__).resolve().parents[2]
class VisualContractTest(unittest.TestCase):
    def setUp(self):self.contract=visual.validate_contract(ROOT)
    def test_exact_coverage_and_no_approval(self):
        self.assertEqual(len(self.contract['cases']),24);self.assertFalse(any(c['native_approved'] for c in self.contract['cases']));self.assertEqual(len(self.contract['baseline_map']),14)
    def altered(self,edit,match):
        with tempfile.TemporaryDirectory() as d:
            r=Path(d);shutil.copytree(ROOT/'design',r/'design');p=r/'design/hooklab/02_CONTRACT/visual-contract.json';v=json.loads(p.read_text());edit(v);p.write_text(json.dumps(v))
            with self.assertRaisesRegex(ValueError,match):visual.validate_contract(r)
    def test_blanket_mask_refused(self):self.altered(lambda c:c.update(masks=[[0,0,390,844]]),'mask')
    def test_relaxed_threshold_refused(self):self.altered(lambda c:c['tolerances'].update(bounds_dp=200),'threshold')
    def test_unreviewed_approval_refused(self):self.altered(lambda c:c.update(approval='APPROVED'),'approval')
    def test_missing_case_refused(self):self.altered(lambda c:c['cases'].pop(),'coverage')
    def test_duplicate_case_refused(self):self.altered(lambda c:c['cases'].__setitem__(1,c['cases'][0]),'coverage')
    def controls(self):
        result={}
        for k,label in {'title':'Hooks',**visual.LABELS}.items():
            result[k]={'label':label,'bounds':[5,20,100,50],'font_sp':12,'color':[1,2,3]}
            if k!='title':result[k]['selected']=k=='hooks'
        return result
    def test_missing_control_refused(self):
        a=self.controls();b=copy.deepcopy(a);b.pop('home');self.assertRaisesRegex(ValueError,'missing native control',visual.compare_controls,a,b,self.contract['tolerances'])
    def test_wrong_navigation_selection_refused(self):
        a=self.controls();b=copy.deepcopy(a);b['home']['selected']=True;self.assertRaisesRegex(ValueError,'wrong native selection',visual.compare_controls,a,b,self.contract['tolerances'])
    def test_label_geometry_and_color_regressions_reported(self):
        a=self.controls();b=copy.deepcopy(a);b['title']['label']='Different';b['hooks']['bounds'][1]+=8;b['home']['color']=[1,2,4];b['diagnostics']['font_sp']=16
        result=visual.compare_controls(a,b,self.contract['tolerances']);self.assertTrue(all(not r['within_candidate_tolerance'] for r in result))
    def test_invalid_bounds_refused(self):
        for bad in ([0,0,400,1],[0,0,1,float('nan')],[0,0,-1,20],[True,0,1,20]):self.assertRaises(ValueError,visual.box,bad)
if __name__=='__main__':unittest.main()
