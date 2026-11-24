#!/usr/bin/env python3
"""Exact DEX call-site verifier. No APK execution or modifications."""
import zipfile,io,struct,hashlib,json,argparse
class Dex:
 def __init__(self,d):
  self.d=d;self.u16=lambda o:struct.unpack_from('<H',d,o)[0];self.u32=lambda o:struct.unpack_from('<I',d,o)[0];u16=self.u16;u32=self.u32
  self.s=[]
  for i in range(u32(56)):
   _,o=self.leb(u32(u32(60)+i*4));self.s.append(d[o:d.index(0,o)].decode('utf8','replace'))
  self.t=[self.s[u32(u32(68)+i*4)] for i in range(u32(64))];self.p=[]
  for i in range(u32(72)):
   o=u32(76)+i*12;off=u32(o+8);self.p.append((self.t[u32(o+4)],tuple(self.t[u16(off+4+j*2)] for j in range(u32(off))) if off else ()))
  self.f=[(self.t[u16(o)],self.s[u32(o+4)],self.t[u16(o+2)]) for o in range(u32(84),u32(84)+8*u32(80),8)]
  self.m=[(self.t[u16(o)],self.s[u32(o+4)],*self.p[u16(o+2)]) for o in range(u32(92),u32(92)+8*u32(88),8)]
  self.classes={self.t[u32(o)]:o for o in range(u32(100),u32(100)+32*u32(96),32)}
 def leb(self,o):
  n=0
  for k in range(5):
   b=self.d[o];o+=1;n|=(b&127)<<(7*k)
   if b<128:return n,o
  raise ValueError('leb')
 def members(self,name):
  o=self.u32(self.classes[name]+24);counts=[]
  for _ in range(4):n,o=self.leb(o);counts.append(n)
  fs=[];ms=[]
  for k,c in enumerate(counts):
   idx=0
   for _ in range(c):
    n,o=self.leb(o);idx+=n;flags,o=self.leb(o)
    if k<2:fs.append((self.f[idx],flags))
    else:co,o=self.leb(o);ms.append((self.m[idx],flags,co))
  return fs,ms
 def words(self,co):return struct.unpack_from('<'+'H'*self.u32(co+12),self.d,co+16)
 def refs(self,co):
  w=self.words(co);i=0
  # Standard Dalvik widths (including payloads); stop on unknown opcodes.
  while i<len(w):
   v=w[i];op=v&255;size=1;ref=None
   if op==0 and v>>8:
    if v==0x100:size=4+w[i+1]*2
    elif v==0x200:size=2+w[i+1]*4
    elif v==0x300:size=4+(w[i+1]*(w[i+2]+65536*w[i+3])+1)//2
    else:raise ValueError((i,v))
   elif op in (0x02,0x05,0x08,0x13,0x15,0x16,0x19,0x1a,0x1c,0x1f,0x20,0x22,0x23,0x29) or 0x2d<=op<=0x3d or 0x44<=op<=0x6d or 0x90<=op<=0xaf or 0xd0<=op<=0xe2 or op in (0xfe,0xff):size=2
   elif op in (0x03,0x06,0x09,0x14,0x17,0x1b,0x24,0x25,0x26,0x2a,0x2b,0x2c) or 0x6e<=op<=0x72 or 0x74<=op<=0x78 or op in (0xfc,0xfd):size=3
   elif op==0x18:size=5
   elif op in (0xfa,0xfb):size=4
   if 0x52<=op<=0x6d:ref=self.f[w[i+1]]
   if 0x6e<=op<=0x72 or 0x74<=op<=0x78:ref=self.m[w[i+1]]
   if op in (0x1c,0x1f,0x20,0x22,0x23,0x24,0x25):ref=self.t[w[i+1]]
   if ref is not None:yield i,hex(op),ref,w[i:i+size]
   i+=size

HASHES={
 'classes.dex':'4916a262b3d5997065a69874a65484c2c6454cce7f60da4439189baca8704d3d',
 'classes2.dex':'f5613536a13eea447364c92d3ee54d22cddbe204833c8df8763ebbf96af3ca8b',
 'classes3.dex':'1d196abb5c796a842a3def1f6fb5ba7ec6bccb537e467276bc5634ea20a70c3c',
 'classes4.dex':'a2d2b9725d0cc5eceeb466bdbd736eb91b7a78b2288040d5b466af04ce70d76d',
 'classes5.dex':'c257cf018f184939b71fa666e87286d6e97405bc09230f023f13cdcc3d53bb24'}
def check(path):
 with zipfile.ZipFile(path) as bundle:
  if bundle.namelist().count('base.apk')!=1:raise ValueError('BASE_APK_AMBIGUOUS')
  base=bundle.read('base.apk')
 if hashlib.sha256(base).hexdigest()!='3ef776e51c81b47a16938a027b03e3dc0c059fa5825f746ba35955709f6d63ad':raise ValueError('BASE_HASH_MISMATCH')
 with zipfile.ZipFile(io.BytesIO(base)) as z:
  dex={}
  for name,h in HASHES.items():
   raw=z.read(name)
   if hashlib.sha256(raw).hexdigest()!=h:raise ValueError('DEX_HASH_MISMATCH '+name)
   dex[name]=Dex(raw)
 def code(dexname,cls,method,args=None,ret=None,stat=None):
  d=dex[dexname];fs,ms=d.members(cls);matches=[(m,f,c) for m,f,c in ms if m[1]==method and (args is None or m[3]==tuple(args)) and (ret is None or m[2]==ret)]
  if len(matches)!=1 or not matches[0][2] or (stat is not None and bool(matches[0][1]&8)!=stat):raise ValueError('METHOD_MISMATCH '+cls+'.'+method)
  return d,matches[0][2]
 def at(d,c,offset,expected):
  refs={i:r for i,op,r,w in d.refs(c)}
  if refs.get(offset)!=expected:raise ValueError('CALL_SITE_MISMATCH '+str(offset))
 def field(name,cls,f,ty):
  fs,_=dex[name].members(cls)
  if not any(x==(cls,f,ty) for x,_ in fs):raise ValueError('FIELD_MISMATCH '+cls+'.'+f)
 o='Ljava/lang/Object;';st='Ljava/lang/String;';cont='Ll0u;';f2='Lkotlin/jvm/functions/Function2;';f0='Lkotlin/jvm/functions/Function0;';cl='Landroid/content/ClipData;';co='Lco3;'
 twargs=(o,o,st,o,o,'Lbho0;','B');ixargs=(o,st,o,o,o,o,cont,'B')
 d,c=code('classes4.dex','Llxl;','a',(st,st,'Lxpm0;','Lr0o0;','Lr0o0;','F','Z','Z','Z','Z',f0,f2,'Z','Z',f0,'Z','Lva41;','Lrqr;','I','I'),'V',True)
 at(d,c,484,('Lmfk1;','u','Ld0m;',(st,st,'Lxpm0;','Z','Ljava/lang/Integer;','Lrqr;','I','I')));at(d,c,488,('Ld0m;','a',co))
 d,c=code('classes4.dex','Llxl;','c',('I','I',co,'Lrqr;','Lr0o0;'),'V',True);at(d,c,228,'Ltwl;');at(d,c,242,('Ltwl;','<init>','V',twargs));assert d.words(c)[241]==0x12
 code('classes5.dex','Ltwl;','<init>',twargs,'V',False)
 d,c=code('classes5.dex','Ltwl;','invoke',(),o,False);at(d,c,177,'Lixl;');at(d,c,185,('Lixl;','<init>','V',ixargs));at(d,c,189,('Lzx2;','t0','Lrn71;',('Lqqw;','Lbqw;','I',f2,'I')));assert d.words(c)[180]==0xe12
 d,c=code('classes4.dex','Lixl;','<init>',ixargs,'V',False)
 # In the pinned ctor p0=v0; String argument p2=v2; annotated argument p3=v3.
 at(d,c,4,('Lixl;','r',o));at(d,c,6,('Lixl;','s',o))
 if d.u16(c)!=9 or d.u16(c+2)!=9 or d.words(c)[4]!=0x025b or d.words(c)[6]!=0x035b:raise ValueError('IXL_CONSTRUCTOR_FIELD_FLOW_MISMATCH')
 d,c=code('classes4.dex','Lixl;','create',(o,cont),cont,False);at(d,c,129,('Lixl;','<init>','V',ixargs));assert d.words(c)[128]==0x12
 # Default branch loads s into v5 then moves v5 to v10 (argument three); r is String.
 at(d,c,10,('Lixl;','s',o));at(d,c,109,('Lixl;','r',o));at(d,c,112,st);at(d,c,115,co)
 if tuple(d.words(c)[114:115])!=(0x5a07,) or d.words(c)[131]!=7:raise ValueError('IXL_CREATE_ANNOTATED_FLOW_MISMATCH')
 d,c=code('classes4.dex','Lixl;','invokeSuspend',(o,),o,False);at(d,c,11,('Lixl;','s',o));at(d,c,729,('Lixl;','r',o));at(d,c,731,st);at(d,c,733,co);at(d,c,735,('Landroid/content/ClipData;','newPlainText',cl,('Ljava/lang/CharSequence;','Ljava/lang/CharSequence;')));
 if d.words(c)[11]!=0x1654 or d.words(c)[729]!=0x1554 or d.words(c)[737]!=0x0065:raise ValueError('IXL_CLIPDATA_LABEL_TEXT_FLOW_MISMATCH')
 at(d,c,741,('Lt7l;','<init>','V',(cl,)));at(d,c,746,(f2,'invoke',o,(o,o)))
 code('classes3.dex','Lt7l;','<init>',(cl,),'V',False)
 d,c=code('classes2.dex','Lvm90;','create',(o,cont),cont,False);at(d,c,24,('Lvm90;','<init>','V',(o,o,o,cont,'B')));assert d.words(c)[22]==0x2812
 d,c=code('classes2.dex','Lvm90;','invokeSuspend',(o,),o,False);at(d,c,54,('Lv7l;','b',o,('Lt7l;',cont)))
 d,c=code('classes.dex','Lay2;','b',('Lt7l;',cont),o,False);at(d,c,78,('Lv7l;','b',o,('Lt7l;',cont)))
 d,c=code('classes.dex','Lvv2;','b',('Lt7l;',cont),o,False);at(d,c,40,('Landroid/content/ClipboardManager;','setPrimaryClip','V',(cl,)))
 for n,cls,f,ty in [('classes.dex',co,'b',st),('classes5.dex','Ltwl;','a','B'),('classes5.dex','Ltwl;','e',o),('classes4.dex','Lixl;','n','B'),('classes4.dex','Lixl;','r',o),('classes4.dex','Lixl;','s',o),('classes2.dex','Lvm90;','n','B'),('classes2.dex','Lvm90;','p',o),('classes3.dex','Lt7l;','a',cl)]:field(n,cls,f,ty)
 # Find annotation helper/return field in exact base, including other DEX if necessary.
 with zipfile.ZipFile(io.BytesIO(base)) as z:
  found=set()
  for n in z.namelist():
   if not n.endswith('.dex'):continue
   d=dex.get(n) or Dex(z.read(n))
   if 'Lmfk1;' in d.classes:
    _,ms=d.members('Lmfk1;');assert any(m==('Lmfk1;','u','Ld0m;',(st,st,'Lxpm0;','Z','Ljava/lang/Integer;','Lrqr;','I','I')) and f&8 and c for m,f,c in ms);found.add('annotation_helper')
   if 'Ld0m;' in d.classes:
    fs,_=d.members('Ld0m;');assert any(f==('Ld0m;','a',co) for f,_ in fs);found.add('annotation_field')
  assert found=={'annotation_helper','annotation_field'}
 return {'verdict':'P06_EXACT_STATIC_PATH_PASS','build':2632128,'verified_dex_hashes':len(HASHES),'ixl_annotated_field':'s','ixl_label_field':'r','physical_device':'NOT_RUN','visual_identity':'UNVERIFIED'}
if __name__=='__main__':
 a=argparse.ArgumentParser();a.add_argument('apkm');args=a.parse_args();print(json.dumps(check(args.apkm),sort_keys=True))
