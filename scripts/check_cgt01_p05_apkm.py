#!/usr/bin/env python3
"""Independent exact-build DEX probe for P05's CopyButtonImpl callback; read-only."""
import argparse,hashlib,io,json,struct,zipfile
from check_cgt01_p04_apkm import check as check_p04, dex_symbols

CLASSES5_SHA256='c257cf018f184939b71fa666e87286d6e97405bc09230f023f13cdcc3d53bb24'
CTOR=['Ljava/lang/Object;','Ljava/lang/Object;','Ljava/lang/String;',
      'Ljava/lang/Object;','Ljava/lang/Object;','Lbho0;','B']

def table(d):
    if len(d)<112 or d[:4]!=b'dex\n':raise ValueError('MALFORMED_DEX')
    u16=lambda at:struct.unpack_from('<H',d,at)[0]
    u32=lambda at:struct.unpack_from('<I',d,at)[0]
    def leb(off):
        n=shift=0
        for _ in range(5):
            b=d[off];off+=1;n|=(b&127)<<shift;shift+=7
            if b<128:return n,off
        raise ValueError('MALFORMED_LEB128')
    ss=[]
    for i in range(u32(56)):
        at=u32(u32(60)+4*i);_,at=leb(at)
        ss.append(d[at:d.index(0,at)].decode('utf8','replace'))
    types=[ss[u32(u32(68)+4*i)] for i in range(u32(64))]
    prot=[]
    for i in range(u32(72)):
        at=u32(76)+12*i;start=u32(at+8)
        arguments=[types[u16(start+4+2*j)] for j in range(u32(start))] if start else []
        prot.append((types[u32(at+4)],arguments))
    methods=[]
    for i in range(u32(88)):
        at=u32(92)+8*i
        methods.append((types[u16(at)],ss[u32(at+4)],*prot[u16(at+2)]))
    classes={}
    for i in range(u32(96)):
        at=u32(100)+32*i;cls=types[u32(at)];iface=u32(at+12);data=u32(at+24)
        if cls!='Ltwl;':continue
        interfaces=[types[u16(iface+4+2*j)] for j in range(u32(iface))] if iface else []
        fields,at=leb(data);instance,at=leb(at);direct,at=leb(at);virtual,at=leb(at)
        for _ in range(fields+instance):_,at=leb(at);_,at=leb(at)
        found=[]
        for count in (direct,virtual):
            idx=0
            for _ in range(count):
                delta,at=leb(at);idx+=delta
                flags,at=leb(at);code_off,at=leb(at)
                found.append((methods[idx],flags,code_off))
        classes[cls]=(interfaces,found)
    return types,methods,classes

def check(apkm):
    base_result=check_p04(apkm)
    with zipfile.ZipFile(apkm) as package:base=package.read('base.apk')
    with zipfile.ZipFile(io.BytesIO(base)) as apk:
        dex4=apk.read('classes4.dex');dex5=apk.read('classes5.dex')
    if hashlib.sha256(dex5).hexdigest()!=CLASSES5_SHA256:
        raise ValueError('CLASSES5_SHA256_MISMATCH')
    _,codeblocks,_=dex_symbols(dex4)
    ty4,method4,_=table(dex4)
    ty5,method5,classes5=table(dex5)
    handler=classes5.get('Ltwl;')
    if not handler or 'Lkotlin/jvm/functions/Function0;' not in handler[0]:
        raise ValueError('PINNED_CALLBACK_NOT_FUNCTION0')
    found=handler[1]
    if not any(m==('Ltwl;','<init>','V',CTOR) and flags&0x10000 and code
               for m,flags,code in found):
        raise ValueError('PINNED_CALLBACK_CONSTRUCTOR_MISSING')
    if not any(m==('Ltwl;','invoke','Ljava/lang/Object;',[]) and code for m,flags,code in found):
        raise ValueError('PINNED_CALLBACK_INVOKE_MISSING')
    # Exact-build bounded code positions independently discovered from the
    # classes4.dex instruction stream. The SHA pin excludes a different DEX.
    render_code=codeblocks['c']['code_off']
    insns_off=render_code+16
    insns_count=struct.unpack_from('<I',dex4,render_code+12)[0]
    if insns_count<=244:raise ValueError('COPY_BUTTON_CODE_TOO_SHORT')
    def word(idx):return struct.unpack_from('<H',dex4,insns_off+2*idx)[0]
    if word(228)&255!=0x22 or ty4[word(229)]!='Ltwl;':
        raise ValueError('COPY_BUTTON_NEW_INSTANCE_NOT_PINNED')
    if word(242)&255!=0x76 or method4[word(243)]!=('Ltwl;','<init>','V',CTOR):
        raise ValueError('COPY_BUTTON_CONSTRUCTOR_CALL_NOT_PINNED')
    return {'verdict':'P05_PINNED_DEX_PATH_PASS_NOT_TAP',
            'package':base_result['apk_package'],'version_code':2632128,
            'copy_renderer':'lxl.c','constructor':'twl.<init>/7',
            'handler':'twl.invoke/0','class4_new_instance_offset':228,
            'class4_constructor_call_offset':242,'classes5_sha256':CLASSES5_SHA256,
            'device_handler_causality':'UNVERIFIED','termux':'NOT_IMPLEMENTED'}

def main():
    a=argparse.ArgumentParser(description=__doc__);a.add_argument('apkm');args=a.parse_args()
    try:result=check(args.apkm)
    except (OSError,ValueError,KeyError,IndexError,struct.error,zipfile.BadZipFile) as error:
        print(json.dumps({'verdict':'REFUSED','reason':str(error).split(':')[0]}));return 2
    print(json.dumps(result,sort_keys=True));return 0
if __name__=='__main__':raise SystemExit(main())
