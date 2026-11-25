"""Check actual built APK dex entries; never signs, installs or publishes release APKs."""
import hashlib,re,zipfile
from pathlib import Path
MARK=b'installInstrumentationFixture'
FACTORY=b'Lcom/mikeyphw/hooklab/ui/UiEnvironmentFactory;'
TEST=b'Lcom/mikeyphw/hooklab/ui/FrameworkFixtureTest;'
def require(ok,msg):
    if not ok:raise ValueError(msg)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def inspect(apk):
    require(apk.is_file() and not apk.is_symlink() and apk.stat().st_size<128*1024*1024,'invalid APK')
    with zipfile.ZipFile(apk) as z:
        names=[i.filename for i in z.infolist()];require(len(names)==len(set(names)),'duplicate APK members')
        dex=[i for i in z.infolist() if re.fullmatch(r'classes(?:[2-9]|[1-9][0-9]+)?\.dex',i.filename)]
        require(bool(dex) and len(dex)<=32 and sum(i.file_size for i in dex)<=128*1024*1024,'invalid DEX inventory')
        chunks=[z.read(i) for i in dex];require(all(b.startswith(b'dex\n') and len(b)>=112 for b in chunks),'invalid DEX header')
        return {'apk_sha256':sha(apk),'dex_sha256':{i.filename:hashlib.sha256(b).hexdigest() for i,b in zip(dex,chunks)},'factory':any(FACTORY in b for b in chunks),'fixture_marker':any(MARK in b for b in chunks),'test_class':any(TEST in b for b in chunks)}
def check(debug,release,test):
    d,r,t=map(inspect,(debug,release,test))
    require(d['factory'] and r['factory'] and d['fixture_marker'] and not r['fixture_marker'],'debug/release fixture isolation failed')
    require(not d['test_class'] and not r['test_class'] and t['test_class'],'instrumentation class packaging drift')
    return {'schema':'hooklab.release.fixture-exclusion.v1','debug':d,'release':r,'instrumentation':t,'release_signed_or_published':False,'release_qualified':False}
def verify(receipt,app_hash,test_hash):
    require(set(receipt)=={'schema','debug','release','instrumentation','release_signed_or_published','release_qualified'},'release exclusion fields')
    require(receipt['schema']=='hooklab.release.fixture-exclusion.v1' and receipt['release_signed_or_published'] is False and receipt['release_qualified'] is False,'release qualification claim')
    for row in (receipt['debug'],receipt['release'],receipt['instrumentation']):
        require(set(row)=={'apk_sha256','dex_sha256','factory','fixture_marker','test_class'} and re.fullmatch('[a-f0-9]{64}',row['apk_sha256']) and 0<len(row['dex_sha256'])<=32 and all(re.fullmatch('[a-f0-9]{64}',h) for h in row['dex_sha256'].values()),'invalid DEX receipt')
        require(all(type(row[k]) is bool for k in ('factory','fixture_marker','test_class')),'invalid DEX marker receipt')
    d,r,t=(receipt[k] for k in ('debug','release','instrumentation'))
    require(d['apk_sha256']==app_hash and t['apk_sha256']==test_hash and d['factory'] and r['factory'] and d['fixture_marker'] and not r['fixture_marker'] and not d['test_class'] and not r['test_class'] and t['test_class'],'invalid isolation proof')
