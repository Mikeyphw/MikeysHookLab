#!/usr/bin/env python3
"""R06 exact ChatGPT native terminal drawable and renderer verification.
Static source identity only; actual Compose rendering requires the target device.
"""
import argparse, io, zipfile, struct
from check_cgt01_p08_apkm import verify, HASHES
from check_cgt01_p06_apkm import Dex
ASSET='composeResources/com.openai.kmp.platform.design.resources/drawable/ds_terminal_regular_24.xml'
def check(path):
    verify(path)
    with zipfile.ZipFile(path) as bundle, zipfile.ZipFile(io.BytesIO(bundle.read('base.apk'))) as apk:
        assert 'assets/'+ASSET in apk.namelist() and 'res/drawable/ds_terminal_regular_24.xml' in apk.namelist()
        ds=[Dex(apk.read(n)) for n in HASHES]
    def find(tag):
        matches=[d for d in ds if tag in d.classes];assert len(matches)==1,tag;return matches[0]
    def method(tag,name,ret,args,static):
        d=find(tag);matches=[(m,f,c) for m,f,c in d.members(tag)[1] if m==(tag,name,ret,args)]
        assert len(matches)==1 and matches[0][2] and bool(matches[0][1]&8)==static,(tag,name)
        return d,matches[0][2]
    method('Le950;','<init>','V',('B',),False)
    d,c=method('Le950;','invoke','Ljava/lang/Object;',(),False);w=d.words(c)
    assert w[3]&255==0x2b
    payload=3+struct.unpack('<i',struct.pack('<HH',w[4],w[5]))[0]
    assert w[payload]==0x100 and w[payload+1]>12 and tuple(w[payload+2:payload+4])==(0,0)
    offset=payload+4+2*12
    target=3+struct.unpack('<i',struct.pack('<HH',w[offset],w[offset+1]))[0]
    # DEX switch targets are relative to the switch instruction.
    assert target==183,(payload,target)
    assert w[193]&255==0x1a and d.s[w[194]]==ASSET
    assert w[202]&255==0x1a and d.s[w[203]]=='drawable:ds_terminal_regular_24'
    refs={i:r for i,op,r,words in d.refs(c)}
    assert any(183<=i<208 and r=='Lda50;' for i,r in refs.items())
    method('Llje0;','a','Lf1s0;',('Lda50;','Lrqr;','I'),True)
    method('Lt6d0;','a','V',('Lf1s0;','Ljava/lang/String;','Lr0o0;','J','Lrqr;','I','I'),True)
    print('P09_TERMINAL_DRAWABLE_D​EX_PASS; actual host composition pending'.replace('\u200b',''))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('apkm');check(p.parse_args().apkm)
