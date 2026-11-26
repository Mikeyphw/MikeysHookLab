#!/usr/bin/env python3
"""HookLab 24-shard hosted JUnit evidence. Only the run subcommand invokes Gradle.
Inventory and verification are read-only Python; no physical device or release claim.
"""
from __future__ import annotations
import argparse, hashlib, json, os, re, subprocess, sys, xml.etree.ElementTree as ET
from pathlib import Path
import dependency_prep

SHARDS=24
SCHEMA='hooklab.ci24.shard.v1'
SUMMARY='hooklab.ci24.summary.v1'
CLASS=re.compile(r'(?:[A-Za-z_$][\w$]*\.)+[A-Za-z_$][\w$]*\Z',re.ASCII)
HEX=re.compile(r'[0-9a-f]{64}\Z')

def require(ok, why):
    if not ok: raise ValueError(why)

def git(root,*args):
    return subprocess.check_output(['git','-C',str(root),*args],text=True,timeout=30).strip()

def digest(p):
    require(p.is_file() and not p.is_symlink(),f'unsafe evidence: {p.name}')
    return hashlib.sha256(p.read_bytes()).hexdigest()

def dump(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2,sort_keys=True)+'\n',encoding='utf-8')

def inventory(root):
    # Fail rather than silently omit a new source language, suite, ignored case or
    # nested class. Current HookLab JVM suite uses ordinary JUnit4 @Test methods.
    files=git(root,'ls-files','app/src/test').splitlines()
    result={}
    for name in files:
        if not name.endswith(('.java','.kt')): continue
        p=root/name; require(p.is_file() and not p.is_symlink(),'test source unavailable')
        s=p.read_text(encoding='utf-8')
        # Strip strings/comments to avoid false annotation detection in fixtures.
        cleaned=re.sub(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'', ' ', s,flags=re.S)
        if not re.search(r'@(?:org\.junit\.)?Test\b',cleaned):
            require(not name.endswith('Test.java') and not name.endswith('Test.kt'),'empty test source '+name)
            continue
        require(name.endswith('.java') and not re.search(r'@(?:Ignore|RunWith|Parameterized)\b',cleaned),'unsupported test form '+name)
        pkg=re.search(r'\bpackage\s+([\w.]+)\s*;',cleaned)
        methods=re.findall(r'@(?:org\.junit\.)?Test\b(?:\s*\([^)]*\))?\s*(?:public\s+)?(?:final\s+)?void\s+(\w+)\s*\(',cleaned)
        require(pkg is not None and methods and len(methods)==len(set(methods)) and len(methods)==len(re.findall(r'@(?:org\.junit\.)?Test\b',cleaned)),'unsupported JUnit roster '+name)
        fq=pkg.group(1)+'.'+p.stem
        require(CLASS.fullmatch(fq) and fq not in result,'duplicate class')
        result[fq]={'path':name,'sha256':digest(p),'methods':sorted(methods)}
    require(len(result)>=SHARDS,'fewer than 24 nonempty test classes; review shard policy')
    return dict(sorted(result.items()))

def assignment(inv, shard):
    require(1<=shard<=SHARDS,'bad shard')
    return {k:v for i,(k,v) in enumerate(sorted(inv.items())) if i%SHARDS==shard-1}

def identity(root,source,run,attempt):
    require(re.fullmatch('[0-9a-f]{40}',source) and git(root,'rev-parse','HEAD')==source,'source drift')
    require(re.fullmatch('[1-9][0-9]*',str(run)) and re.fullmatch('[1-9][0-9]*',str(attempt)),'invalid run identity')
    return {'source_sha':source,'source_tree':git(root,'rev-parse','HEAD^{tree}'),'run_id':str(run),'run_attempt':str(attempt),'repository':'Mikeyphw/MikeysHookLab'}

def parse_xml(folder, expected):
    actual=set(); records={}
    paths=sorted(folder.glob('TEST-*.xml'))
    require(paths,'no JUnit XML')
    for p in paths:
        require(p.stat().st_size<=8*1024*1024,'oversized JUnit')
        b=p.read_bytes(); require(b'<!DOCTYPE' not in b and b'<!ENTITY' not in b,'unsafe XML')
        e=ET.fromstring(b); require(e.tag=='testsuite','invalid JUnit suite')
        cases=e.findall('testcase')
        require(int(e.get('tests','-1'))==len(cases) and len(cases)>0,'empty or incomplete suite')
        require(all(int(e.get(x,'0'))==0 for x in ('failures','errors','skipped','disabled')),'failed/skipped tests')
        for c in cases:
            k=(c.get('classname'),c.get('name'))
            require(k[0] in expected and k[1] in expected[k[0]]['methods'] and k not in actual,'unexpected or duplicate test '+str(k))
            require(not any(c.find(x) is not None for x in ('failure','error','skipped')),'failed/skipped testcase')
            actual.add(k)
        records[p.name]=digest(p)
    wanted={(k,m) for k,v in expected.items() for m in v['methods']}
    require(actual==wanted,'test roster mismatch missing='+str(sorted(wanted-actual))+' extra='+str(sorted(actual-wanted)))
    return records, sorted([list(k) for k in actual])

def ready_digest(root,path,ident):
    require(path is not None and path.is_file() and not path.is_symlink(),'verified dependency receipt required')
    data=json.loads(path.read_text()); profile=data.get('profile'); require(profile in dependency_prep.PROFILES,'invalid dependency profile')
    dependency_prep.verify_receipt(data,ident,dependency_prep.graph_digest(root),profile)
    return digest(path)

def run(root,out,shard,source,run_id,attempt,dependency_receipt=None):
    require(os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('RUNNER_OS')=='Linux' and os.environ.get('SOURCE_SHA')==source and os.environ.get('GITHUB_RUN_ID')==str(run_id) and os.environ.get('GITHUB_RUN_ATTEMPT')==str(attempt),'HOSTED_ONLY_GRADLE_REFUSED')
    ident=identity(root,source,run_id,attempt); dependency_hash=ready_digest(root,dependency_receipt,ident); inv=inventory(root); expected=assignment(inv,shard)
    require(not out.exists(),'fresh shard output required');out.mkdir(parents=True)
    receipt={'schema':SCHEMA,**ident,'dependency_receipt_sha256':dependency_hash,'shard':shard,'shards':SHARDS,'classes':expected,'status':'NOT_RUN','release_qualified':False,'physical_device_qualified':False}
    dump(out/'receipt.json',receipt)
    cmd=['bash','gradlew','--no-daemon','--console=plain','--offline','--init-script','gradle/ci/locks.gradle',':app:testDebugUnitTest']
    for name in expected:cmd+=['--tests',name]
    print('CI24 START',shard,', '.join(expected),flush=True)
    try:
        rc=subprocess.run(cmd,cwd=root,timeout=3000,check=False,env=os.environ.copy()).returncode
    except subprocess.TimeoutExpired:
        receipt['status']='FAIL_TIMEOUT';dump(out/'receipt.json',receipt);return 124
    receipt['gradle_exit']=rc
    if rc:
        receipt['status']='FAIL_GRADLE';dump(out/'receipt.json',receipt);return rc if rc>=0 else 128-rc
    # Sanitize successful XML by constructing a minimal canonical record. Raw
    # stdout/stderr/test output stay in the hosted job, never in downloadable evidence.
    junit=out/'junit';junit.mkdir()
    src=root/'app/build/test-results/testDebugUnitTest'
    for p in sorted(src.glob('TEST-*.xml')):
        b=p.read_bytes();require(len(b)<=8*1024*1024 and b'<!DOCTYPE' not in b and b'<!ENTITY' not in b,'unsafe XML')
        e=ET.fromstring(b)
        clean=ET.Element('testsuite',{k:e.get(k,'0') for k in ('tests','failures','errors','skipped','disabled')})
        for c in e.findall('testcase'):
            x=ET.SubElement(clean,'testcase',{'classname':c.get('classname',''),'name':c.get('name','')})
            for tag in ('failure','error','skipped'):
                if c.find(tag) is not None:ET.SubElement(x,tag)
        ET.ElementTree(clean).write(junit/p.name,encoding='utf-8',xml_declaration=True)
    files,cases=parse_xml(junit,expected)
    receipt.update(status='PASS',junit_sha256=files,tests=cases,test_count=len(cases))
    dump(out/'receipt.json',receipt);print('CI24 SHARD PASS',shard,len(cases),flush=True);return 0

def verify_shard(folder,expected,ident,shard,dependency_hash=None):
    p=folder/'receipt.json'; require(p.is_file() and not p.is_symlink() and p.stat().st_size<2*1024*1024,'missing/unsafe receipt')
    require({x.name for x in folder.iterdir()}=={'receipt.json','junit'},'unapproved shard evidence files')
    r=json.loads(p.read_text())
    wanted={'schema':SCHEMA,**ident,'shard':shard,'shards':SHARDS,'classes':expected,'status':'PASS','gradle_exit':0,'release_qualified':False,'physical_device_qualified':False}
    require(all(r.get(k)==v for k,v in wanted.items()),'invalid shard identity/status '+str(shard))
    require(HEX.fullmatch(str(r.get('dependency_receipt_sha256',''))) and (dependency_hash is None or r['dependency_receipt_sha256']==dependency_hash),'shard dependency receipt drift')
    junit=folder/'junit';require(junit.is_dir() and not junit.is_symlink(),'missing junit')
    files,cases=parse_xml(junit,expected)
    require(set(x.name for x in junit.iterdir())==set(files),'unexpected JUnit files')
    require(r.get('junit_sha256')==files and r.get('tests')==cases and r.get('test_count')==len(cases),'JUnit evidence drift')
    return {'shard':shard,'receipt_sha256':digest(p),'test_count':len(cases),'classes':sorted(expected),'junit_sha256':files}

def summarize(root,artifacts,out,source,run_id,attempt,verify_existing=False,dependency_receipt=None):
    ident=identity(root,source,run_id,attempt);inv=inventory(root);prefix=f'hooklab-ci24-{run_id}-{attempt}-shard-'
    expected_names={prefix+str(i) for i in range(1,SHARDS+1)}
    actual={p.name for p in artifacts.iterdir() if p.name.startswith('hooklab-ci24-') and '-shard-' in p.name}
    require(actual==expected_names,'missing/extra/mixed-attempt shards: '+str(sorted(expected_names-actual)))
    dependency_hash=ready_digest(root,dependency_receipt,ident)
    rows=[verify_shard(artifacts/(prefix+str(i)),assignment(inv,i),ident,i,dependency_hash) for i in range(1,SHARDS+1)]
    s={'schema':SUMMARY,**ident,'dependency_receipt_sha256':dependency_hash,'overall':'PASS_CI24_VERIFIED','shard_count':SHARDS,'class_count':len(inv),'test_count':sum(x['test_count'] for x in rows),'shards':rows,'release_qualified':False,'physical_device_qualified':False,'emulator_ui_qualified':False}
    if verify_existing:
        require(out.is_file() and not out.is_symlink() and json.loads(out.read_text())==s,'hosted summary mismatch')
    else:dump(out,s)
    print(json.dumps(s,sort_keys=True),flush=True);return 0

def failed_summary(root,artifacts,out,source,run_id,attempt,matrix_status,reason='CI24_VERIFICATION_FAILED'):
    ident=identity(root,source,run_id,attempt);inv=inventory(root)
    prefix=f'hooklab-ci24-{run_id}-{attempt}-shard-';rows=[]
    for shard in range(1,SHARDS+1):
        folder=artifacts/(prefix+str(shard))
        try:
            require(folder.is_dir() and not folder.is_symlink(),'missing shard')
            row=verify_shard(folder,assignment(inv,shard),ident,shard)
            rows.append({**row,'status':'VERIFIED_PASS'})
        except (ValueError,OSError,TypeError,KeyError,ET.ParseError):
            rows.append({'shard':shard,'status':'MISSING' if not folder.exists() else 'INVALID_OR_FAILED'})
    dump(out,{'schema':SUMMARY,**ident,'overall':'FAIL_CI24_VERIFICATION','reason':reason,'matrix_status':matrix_status if matrix_status in ('success','failure','cancelled','skipped') else 'unknown','shard_count':SHARDS,'shards':rows,'release_qualified':False,'physical_device_qualified':False,'emulator_ui_qualified':False})

def main():
    a=argparse.ArgumentParser();a.add_argument('command',choices=['inventory','run','summarize','verify']);a.add_argument('--root',type=Path,default=Path.cwd());a.add_argument('--out',type=Path);a.add_argument('--artifacts',type=Path);a.add_argument('--shard',type=int);a.add_argument('--source',default=os.getenv('GITHUB_SHA'));a.add_argument('--run-id',default=os.getenv('GITHUB_RUN_ID'));a.add_argument('--attempt',default=os.getenv('GITHUB_RUN_ATTEMPT'));a.add_argument('--matrix-status',default='success');a.add_argument('--dependency-receipt',type=Path);v=a.parse_args()
    try:
        if v.command=='inventory':print(json.dumps(inventory(v.root),indent=2));return 0
        if v.command=='run':return run(v.root,v.out,v.shard,v.source,v.run_id,v.attempt,v.dependency_receipt)
        if v.command=='summarize':
            require(v.dependency_receipt is not None and v.dependency_receipt.is_file() and not v.dependency_receipt.is_symlink(),'DEPENDENCY_READINESS_MISSING')
            require(v.matrix_status=='success','hosted matrix unsuccessful')
        return summarize(v.root,v.artifacts,v.out,v.source,v.run_id,v.attempt,v.command=='verify',v.dependency_receipt)
    except (ValueError,OSError,TypeError,KeyError,subprocess.SubprocessError,ET.ParseError) as e:
        if v.command=='summarize' and v.artifacts is not None and v.out is not None:
            try:failed_summary(v.root,v.artifacts,v.out,v.source,v.run_id,v.attempt,v.matrix_status,'DEPENDENCY_READINESS_MISSING' if str(e)=='DEPENDENCY_READINESS_MISSING' else 'CI24_VERIFICATION_FAILED')
            except (ValueError,OSError,TypeError,KeyError,subprocess.SubprocessError,ET.ParseError):pass
        print('CI24_REFUSED',str(e),file=sys.stderr);return 2
if __name__=='__main__':raise SystemExit(main())
