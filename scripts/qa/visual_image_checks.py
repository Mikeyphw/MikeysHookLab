"""Real image regression fixtures, run only where pinned Pillow is available."""
import copy,tempfile,unittest
from pathlib import Path
from PIL import Image,ImageDraw
import visual
class VisualImageTest(unittest.TestCase):
    def setUp(self):
        self.a=Image.new('RGB',(100,120),(13,18,20));ImageDraw.Draw(self.a).rectangle((10,10,90,30),fill=(183,236,203));self.tol={'bounds_dp':2,'font_sp':1,'rgb_delta':0,'native_ssim_min':.99,'native_changed_pixel_max':.01};self.c={'title':{'label':'Hooks','bounds':[10,10,80,20],'font_sp':21,'color':[183,236,203]}}
    def test_vectorized_metrics_match_original_formula_on_partial_windows(self):
        import random
        from PIL import ImageChops,ImageStat
        rng=random.Random(18)
        for size in [(1,1),(9,17),(80,80),(31,32),(8,9)]:
            a=Image.frombytes('RGB',size,rng.randbytes(size[0]*size[1]*3));b=Image.frombytes('RGB',size,rng.randbytes(size[0]*size[1]*3));d=ImageChops.difference(a,b);scores=[]
            for y in range(0,size[1],8):
                for x in range(0,size[0],8):
                    crop=(x,y,min(x+8,size[0]),min(y+8,size[1]));u=list(a.convert('L').crop(crop).getdata());v=list(b.convert('L').crop(crop).getdata());n=len(u);mu=sum(u)/n;mv=sum(v)/n;vu=sum((z-mu)**2 for z in u)/n;vv=sum((z-mv)**2 for z in v)/n;cov=sum((p-mu)*(q-mv) for p,q in zip(u,v))/n
                    scores.append(((2*mu*mv+6.5025)*(2*cov+58.5225))/((mu*mu+mv*mv+6.5025)*(vu+vv+58.5225)))
            changed=sum(1 for pixel in d.getdata() if pixel!=(0,0,0))/(a.width*a.height)
            self.assertEqual(visual.metrics(a,b),{'changed_pixel_ratio':round(changed,8),'ssim':round(sum(scores)/len(scores),8),'mean_abs_rgb':round(sum(ImageStat.Stat(d).mean)/3,5),'max_rgb_delta':max(v[1] for v in d.getextrema())})
    def test_identical_pass(self):
        m=visual.strict_regression(self.c,self.c,self.a,self.a.copy(),self.tol);self.assertEqual(m['ssim'],1);self.assertEqual(m['changed_pixel_ratio'],0)
    def test_shifted_card_refused(self):
        b=Image.new('RGB',self.a.size,(13,18,20));ImageDraw.Draw(b).rectangle((10,18,90,38),fill=(183,236,203));self.assertRaisesRegex(ValueError,'image regression',visual.strict_regression,self.c,self.c,self.a,b,self.tol)
    def test_changed_color_refused(self):
        b=self.a.copy();ImageDraw.Draw(b).rectangle((10,10,90,30),fill=(1,2,3));self.assertRaisesRegex(ValueError,'image regression',visual.strict_regression,self.c,self.c,self.a,b,self.tol)
    def test_subtle_raster_drift_is_not_a_pass(self):
        b=self.a.copy();ImageDraw.Draw(b).rectangle((10,10,40,30),fill=(184,237,204))
        m=visual.metrics(self.a,b);self.assertGreater(m['changed_pixel_ratio'],.01);self.assertGreater(m['ssim'],.99);self.assertEqual(m['max_rgb_delta'],1)
        self.assertRaisesRegex(ValueError,'image regression',visual.strict_regression,self.c,self.c,self.a,b,self.tol)
    def test_wrong_size_refused(self):self.assertRaisesRegex(ValueError,'dimensions',visual.metrics,self.a,Image.new('RGB',(99,120)))
    def test_geometry_fails_even_if_image_same(self):
        c=copy.deepcopy(self.c);c['title']['bounds'][1]+=3;self.assertRaisesRegex(ValueError,'geometry',visual.strict_regression,self.c,c,self.a,self.a,self.tol)
    def test_triptych_dimensions(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'report.png';visual.triptych(self.a,self.a,p)
            with Image.open(p) as image:self.assertEqual(image.size,(300,144))
if __name__=='__main__':unittest.main(verbosity=2)
