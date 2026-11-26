"""Source-bound debug probe packaging check, never a device or release verdict."""
import argparse, hashlib, json, os, re, struct, subprocess, zipfile
from pathlib import Path
ASSET='assets/mod01-probe/classes.dex'
ENTRY='Lcom/mikeyphw/hooklab/modules/probe/ProbeEntrypoint;'
SDK='Lcom/mikeyphw/hooklab/sdk/HookSdk;'
ACTIVITY='Lcom/mikeyphw/hooklab/probe/TransportProbeActivity;'
LOADER='Lcom/mikeyphw/hooklab/probe/ProbeDexLoader;'
TARGET='Lcom/mikeyphw/hooklab/probetarget/ProbeTargetActivity;'
LIMIT=128*1024*1024

def require(ok,msg):
    if not ok:raise ValueError(msg)
def u32(b,p):
    require(0<=p<=len(b)-4,'DEX integer bounds');return struct.unpack_from('<I',b,p)[0]
def dex_classes(b):
    require(112<=len(b)<=LIMIT and b.startswith(b'dex\n') and u32(b,32)==len(b) and u32(b,36)==112 and u32(b,40)==0x12345678,'DEX header')
    sc,so,tc,to,cc,co=(u32(b,i) for i in (56,60,64,68,96,100))
    for count,offset,size in ((sc,so,4),(tc,to,4),(cc,co,32)):
        require(count<=1000000 and offset<=len(b) and count*size<=len(b)-offset,'DEX table bounds')
    result=set()
    for i in range(cc):
        ti=u32(b,co+i*32);require(ti<tc,'DEX type index');si=u32(b,to+ti*4);require(si<sc,'DEX string index');p=u32(b,so+si*4)
        for _ in range(5):
            require(p<len(b),'DEX string offset');v=b[p];p+=1
            if not v&128:break
        else:raise ValueError('DEX string length')
        end=b.find(b'\0',p,min(p+4096,len(b)));require(end>=p,'DEX descriptor termination')
        # Only ASCII class names are compared; other legal Unicode names are irrelevant.
        descriptor=b[p:end].decode('ascii',errors='replace');require(descriptor not in result,'duplicate DEX class');result.add(descriptor)
    return result

def inspect(apk):
    require(apk.is_file() and not apk.is_symlink() and apk.stat().st_size<=LIMIT,'invalid APK')
    with zipfile.ZipFile(apk) as z:
        names=z.namelist();require(len(names)==len(set(names)),'duplicate APK entry')
        files=[x for x in z.infolist() if re.fullmatch(r'classes(?:[2-9]|[1-9][0-9]+)?\.dex',x.filename)]
        require(0<len(files)<=32 and sum(x.file_size for x in files)<=LIMIT,'invalid DEX roster')
        classes=set()
        for x in files:
            c=dex_classes(z.read(x));require(not classes&c,'duplicate APK classes');classes|=c
        payload=None
        if ASSET in names:
            require(z.getinfo(ASSET).file_size<=256*1024,'probe payload too large');payload=z.read(ASSET)
        return classes,payload

def sha(b):return hashlib.sha256(b).hexdigest()
def identity(root):
    head=subprocess.check_output(['git','-C',str(root),'rev-parse','HEAD'],text=True).strip()
    tree=subprocess.check_output(['git','-C',str(root),'rev-parse','HEAD^{tree}'],text=True).strip()
    require(head==os.environ.get('SOURCE_SHA') and os.environ.get('GITHUB_REPOSITORY')=='Mikeyphw/MikeysHookLab','source identity mismatch')
    run=os.environ['GITHUB_RUN_ID'];attempt=os.environ['GITHUB_RUN_ATTEMPT'];require(re.fullmatch('[1-9][0-9]*',run) and re.fullmatch('[1-9][0-9]*',attempt),'run identity')
    return dict(source_sha=head,source_tree=tree,repository='Mikeyphw/MikeysHookLab',run_id=run,run_attempt=attempt)
def check(root,debug,release,target):
    d,p=inspect(debug);r,pr=inspect(release);t,pt=inspect(target)
    require(p is not None and pr is None and pt is None,'debug-only payload drift')
    require(ENTRY not in d and ENTRY not in r and SDK in d and SDK in r and ACTIVITY in d and ACTIVITY not in r and LOADER in d and LOADER not in r and TARGET in t,'manager/SDK/target boundary')
    c=dex_classes(p);require(ENTRY in c and all(x.startswith('Lcom/mikeyphw/hooklab/modules/probe/') for x in c),'payload contains unreviewed classes or duplicated SDK')
    return {'schema':'hooklab.mod01.probe.apks.v1',**identity(root),'debug_sha256':sha(debug.read_bytes()),'release_sha256':sha(release.read_bytes()),'target_sha256':sha(target.read_bytes()),'payload_sha256':sha(p),'device_qualified':False,'release_qualified':False}
def main():
    p=argparse.ArgumentParser();p.add_argument('mode',choices=['publish','verify']);p.add_argument('--root',type=Path,default=Path.cwd());p.add_argument('--debug',type=Path,required=True);p.add_argument('--release',type=Path,required=True);p.add_argument('--target',type=Path,required=True);p.add_argument('--receipt',type=Path,required=True);a=p.parse_args()
    try:
        value=check(a.root,a.debug,a.release,a.target)
        if a.mode=='publish':a.receipt.write_text(json.dumps(value,sort_keys=True,indent=2)+'\n')
        else:require(json.loads(a.receipt.read_text())==value,'probe receipt mismatch')
        print('MOD01_PROBE_APK_BOUNDARY_PASS');return 0
    except (ValueError,OSError,KeyError,subprocess.SubprocessError,zipfile.BadZipFile) as ex:print('MOD01_PROBE_APK_REFUSED',ex);return 2
if __name__=='__main__':raise SystemExit(main())
