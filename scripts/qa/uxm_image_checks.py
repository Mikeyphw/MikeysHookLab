"""Hosted/pinned raster and semantic negative tests (Pillow required)."""
import unittest
from PIL import Image
import uxm_visual as v
class UxmImageChecks(unittest.TestCase):
 def test_semantic_geometry_and_touch(self):
  row={'id':'action#1','role':'button','label':'Go','state':{},'action':'go','bounds':[0,0,48,48],'font_sp':15,'color':'rgb(1, 2, 3)','background':'rgba(0, 0, 0, 0)','radius':0,'visible':True,'clickable':True,'focusable':True,'touch_dp':[48,48]}
  self.assertEqual(len(v.compare_semantics('home',[row],[row])),1)
  for key,value in [('id','different'),('label','Wrong'),('bounds',[3,0,48,48]),('font_sp',17),('color','rgb(2, 2, 3)'),('touch_dp',[47,48]),('clickable',False),('state',{'checked':True})]:
   with self.subTest(key=key),self.assertRaises(ValueError):v.compare_semantics('home',[row],[{**row,key:value}])
  self.assertRaises(ValueError,v.compare_semantics,'home',[row],[])
 def test_raster_strict_and_unreviewed(self):
  a=Image.new('RGB',(32,32),'white');b=a.copy();b.paste('black',(0,0,16,16));regions={'full':[0,0,32,32]};limits={('home','full'):{'ssim_min':.99,'changed_pixel_max':.01}}
  self.assertIn('full',v.compare_raster('home',a,a,regions,limits));self.assertRaises(ValueError,v.compare_raster,'home',a,b,regions,limits);self.assertRaises(ValueError,v.compare_raster,'home',a,a,regions,{})
if __name__=='__main__':unittest.main()
