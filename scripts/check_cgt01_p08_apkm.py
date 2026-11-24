#!/usr/bin/env python3
"""Verify exact host DEX and P08 composition anchors, without modifying or executing APKs."""
import argparse,hashlib,io,struct,zipfile
from check_cgt01_p06_apkm import Dex,HASHES,check
EXTRA={'classes6.dex':'b7699cc0a2f22e120f11b935e02324a316380469e069427faeeb9eacb509eaab','classes7.dex':'50ddda6e056c9fa056226efd58d8127886ee00ef71f63226b89a1e7e0c9404ea','classes8.dex':'09d0e1ecc48e4abfb92b14279f6704f01222a3d2fb50ed8a5cd47b7c4cc7abd7','classes9.dex':'faddbe9409723547fc8a1400b213f24511c105a89acef6842b1bb9638e2f61ba','classes10.dex':'9266c4a306d2e8c8305531a98189f58eab6ca367dae1e3692c77f84a51542fd7'}
KEYS=range(0x484c0501,0x484c0509)
def verify(path):
 check(path)
 with zipfile.ZipFile(path) as bundle,zipfile.ZipFile(io.BytesIO(bundle.read('base.apk'))) as apk:
  raw={name:apk.read(name) for name in {**HASHES,**EXTRA}}
  for n,h in {**HASHES,**EXTRA}.items():assert hashlib.sha256(raw[n]).hexdigest()==h,n
  # Search conservatively for a raw 32-bit literal in every DEX. This is not a proof
  # against computed/hash-derived runtime collisions.
  for n,data in raw.items():
   for key in KEYS:assert struct.pack('<I',key) not in data,(n,hex(key))
  ds=[Dex(raw[n]) for n in HASHES]
 def cls(name):
  tag='L'+name+';';matches=[d for d in ds if tag in d.classes];assert len(matches)==1,tag;return matches[0],tag
 def member(name,method,args=None,ret=None):
  d,tag=cls(name);matches=[(m,f,c) for m,f,c in d.members(tag)[1] if m[1]==method and (args is None or m[3]==args) and (ret is None or m[2]==ret)];assert len(matches)==1,(name,method,matches);return d,matches[0]
 def field(name,member,ty,flags=0):
  d,tag=cls(name);assert any(x==(tag,member,ty) and f&flags==flags for x,f in d.members(tag)[0]),(name,member,ty)
 def at(d,c,off,expected):assert {i:r for i,op,r,w in d.refs(c)}.get(off)==expected,(off,expected)
 obj='Ljava/lang/Object;';f0='Lkotlin/jvm/functions/Function0;';f2='Lkotlin/jvm/functions/Function2;';f3='Lkotlin/jvm/functions/Function3;';r='Lrqr;';mod='Lr0o0;';v='V'
 for name,f,ty in [('cxl','p','Lco3;'),('q12','a','B'),('q12','b',obj),('co3','b','Ljava/lang/String;'),('oba0','S','Z'),('oba0','T','J'),('pgq','a','Lorn0;'),('by1','l','Lmqa;'),('o0o0','a','Lo0o0;'),('by1','s','Lqro0;'),('androidx/compose/ui/node/LayoutNode','a2','Ly850;'),('gqr','f','Lkfr;'),('gqr','e','Ldgd;'),('gqr','g','Lz4e;'),('gqr','d','Lx4e;'),('mec1','a','Lmec1;')]:field(name,f,ty,16 if name=='co3' else 0)
 for name,method,args,ret in [('txy0','N',('I','Z',obj,r),'Lzdr;'),('oba0','n0',('I',),v),('oba0','s',('Z',),v),('oba0','r0',(),v),('oba0','m',(f0,),v),('oba0','A0',(),v),('oba0','X',(),obj),('oba0','x0',(obj,),v),('oba0','n',(),'Lont0;'),('sf11','a',('Lx37;','Lmqa;',r,'I'),'Luf11;'),('bq81','X',(r,mod),mod),('hn61','f',(r,obj,f2),v),('bqg','u',('I','Loba0;','Lz4e;','Loba0;'),v),('zdr','a',(r,'I'),obj),('zdr','b',(obj,r,'I'),obj),('g3v','m',(f0,mod,'Ll5d0;','Z','Z','Ljava/lang/String;',f0,'Lnfo0;','Lecc;',f3,r,'I','I'),v),('cg51','u',('Ljava/lang/String;',mod,'Li9;','I','I','Z','I','I','J','I','Lcbj0;','I','Lkotlin/jvm/functions/Function1;','Lb3a1;',r,'I','I','I'),v)]:member(name,method,args,ret)
 for method in ('f','m','n'):member('q4z0',method,(),v)
 d,(_,_,c)=member('cxl','invoke',(obj,obj),obj)
 for offset,ref in [(376,('Lcxl;','p','Lco3;')),(1599,('Lsf11;','a','Luf11;',('Lx37;','Lmqa;',r,'I'))),(1603,('Loba0;','T','J')),(1609,('Loba0;','n','Lont0;',())),(1613,('Lbq81;','X',mod,(r,mod))),(1619,('Loba0;','r0',v,())),(1622,('Loba0;','S','Z')),(1626,('Loba0;','m',v,(f0,))),(1630,('Loba0;','A0',v,())),(1633,('Lhn61;','f',v,(r,obj,f2))),(1636,('Lhn61;','f',v,(r,obj,f2))),(1639,('Lbqg;','u',v,('I','Loba0;','Lz4e;','Loba0;'))),(1644,('Lhn61;','f',v,(r,obj,f2))),(1676,'Lq12;'),(1682,('Lq12;','<init>',v,(obj,'B'))),(1689,('Ltxy0;','N','Lzdr;',('I','Z',obj,r))),(1706,('Lx8w;','f',v,('Lvf11;','Z',mod,'Lml60;','Lub70;','Ljava/lang/String;','Lzdr;',r,'I','I')))]:at(d,c,offset,ref)
 w=d.words(c);assert tuple(w[1678:1680])==(0x0213,8);assert tuple(w[1685:1688])==(0x0214,0xbe4d,0xd01e)
 d,(_,_,c)=member('q12','invoke',(obj,obj,obj),obj);at(d,c,1868,'Lco3;');at(d,c,1908,('Llxl;','c',v,('I','I','Lco3;',r,mod)))
 d,(_,_,c)=member('x3z0','b');at(d,c,38,('Lq4z0;','m',v,()))
 d,(_,_,c)=member('x3z0','c');at(d,c,47,('Lq4z0;','n',v,()));at(d,c,135,('Lq4z0;','f',v,()))
 print('P08_D​EX_PASS: 10 pinned DEX; Row/Copy factory, host signatures, lifecycle offsets; raw key scan clear'.replace('\u200b',''))
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('apkm');verify(p.parse_args().apkm)
