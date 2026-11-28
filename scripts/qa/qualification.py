#!/usr/bin/env python3
"""Exact-source impact planning and fail-closed aggregate verification.
No network, device mutations or local Gradle. Unknown impact expands coverage.
The same planner is used by the hosted coordinator and the Ordenator observer.
"""
from __future__ import annotations
import argparse, hashlib, json, os, re, subprocess, sys
from pathlib import Path, PurePosixPath
from typing import Any

SCHEMA = 'hooklab.qualification.plan.v1'
RECEIPT = 'hooklab.qualification.receipt.v1'
REPO = 'Mikeyphw/MikeysHookLab'
POLICY = 'config/qualification-impact.json'
GATES = ('source', 'android', 'ci24', 'terminal', 'design', 'visual', 'functional', 'adaptive', 'go')
ANDROID = {'android', 'ci24'}
UI = {'design', 'visual', 'adaptive'}
FULL = set(GATES) - {'go', 'functional'}  # visual includes all functional fixtures
CASES = {'api29-compact':29, 'api29-standard':29, 'api36-compact':36, 'api36-360':36,
         'api36-390':36, 'api36-393':36, 'api36-600':36, 'api36-840':36,
         'api36-landscape':36, 'api36-tablet-landscape':36}
FUNCTIONS = ('diagnostics', 'framework', 'termux')
HEX = re.compile(r'[a-f0-9]{40}\Z')
DIGITS = re.compile(r'[1-9][0-9]*\Z')
ID = re.compile(r'(?:MOD-0[0-5]|UX-R0[0-5]|UX-M0[0-6])(?:-F[0-9]{2})?\Z')

def require(condition, reason):
    if not condition:
        raise ValueError(reason)

def canonical(value):
    return json.dumps(value, ensure_ascii=True, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()

def sha(data): return hashlib.sha256(data).hexdigest()

def strict_json(raw):
    def unique(pairs):
        result={}
        for k,v in pairs:
            require(k not in result, 'duplicate JSON key')
            result[k]=v
        return result
    def invalid(value): raise ValueError('non-finite JSON constant')
    return json.loads(raw, object_pairs_hook=unique, parse_constant=invalid)

def git(root,*args,raw=False):
    env={**os.environ,'GIT_NO_REPLACE_OBJECTS':'1','GIT_TERMINAL_PROMPT':'0','GIT_CONFIG_NOSYSTEM':'1'}
    result=subprocess.run(['git','-C',str(root),*args],capture_output=True,timeout=60,env=env,check=False)
    require(result.returncode==0,'git operation failed: '+args[0])
    return result.stdout if raw else result.stdout.decode('utf-8').strip()

def clean_path(name):
    require(isinstance(name,str) and name,"invalid path type")
    p=PurePosixPath(name)
    require( not p.is_absolute() and str(p)==name and '..' not in p.parts and '\\' not in name and all(ord(c)>=32 for c in name),'unsafe path')
    return name

def tree_inventory(root,commit):
    out={}
    for item in git(root,'ls-tree','-rz','--full-tree',commit,raw=True).split(b'\0'):
        if not item: continue
        meta,name=item.split(b'\t',1);mode,kind,blob=meta.decode('ascii').split()
        name=clean_path(name.decode('utf-8'));require(name not in out,'duplicate tree path')
        out[name]=(mode,kind,blob)
    return out

def diff_paths(before,after):
    # Comparing exact recursive trees covers rename/delete/mode changes, even when
    # Git rename heuristics or the GitHub changed-files API would miss a path.
    return [{'path':p,'change':'A' if p not in before else 'D' if p not in after else 'M',
             'before':list(before[p]) if p in before else None,'after':list(after[p]) if p in after else None}
            for p in sorted(set(before)|set(after)) if before.get(p)!=after.get(p)]

def load_policy(root, head):
    raw=git(root,'show',head+':'+POLICY,raw=True)
    data=strict_json(raw)
    require(set(data)=={'schema','runtime_only','functional_only','overlay_minimums'} and data['schema']=='hooklab.qualification.policy.v1','policy schema drift')
    known=set()
    for group in ('runtime_only','functional_only'):
        require(isinstance(data[group],dict),'policy boundary must be an object')
        for name,row in data[group].items():
            clean_path(name);require(name not in known and name.startswith('app/src/') and name.endswith('.java'),'invalid boundary');known.add(name)
            require(isinstance(row,dict) and set(row)=={'reason','tests','cases','terminal'} and isinstance(row['reason'],str) and row['reason'] and isinstance(row['tests'],list) and len(row['tests'])==len(set(row['tests'])) and set(row['tests'])<=set(FUNCTIONS) and isinstance(row['cases'],list) and len(row['cases'])==len(set(row['cases'])) and set(row['cases'])<=set(CASES) and type(row['terminal']) is bool,'invalid boundary row')
            if group=='runtime_only':require(not row['tests'] and not row['cases'] and not row['terminal'],'runtime-only boundary has UI obligations')
            else:require(row['tests'] or row['cases'],'functional boundary lacks selected tests')
    require(isinstance(data['overlay_minimums'],dict) and set(data['overlay_minimums'])=={f'MOD-0{i}' for i in range(6)}|{f'UX-R0{i}' for i in range(6)}|{f'UX-M0{i}' for i in range(7)},'overlay minimum roster incomplete')
    for row in data['overlay_minimums'].values():require(isinstance(row,list) and len(row)==len(set(row)) and set(row)<=set(GATES) and 'source' in row,'unknown minimum gate')
    return data,sha(raw)

def is_go(path):
    return path.startswith('tools/hooklabctl/') and (path.endswith('.go') or PurePosixPath(path).name in ('go.mod','go.sum'))

def is_doc(path):
    # Only narrative files, not executable HTML, design references, fixtures,
    # policy, manifests, or Markdown inputs outside the docs directory.
    return path=='README.md' or (path.startswith('docs/') and path.endswith('.md'))

def intent(path):
    prefix='config/qualification-intents/'
    if not path.startswith(prefix) or not path.endswith('.json'):return None
    value=path[len(prefix):-5]
    return value if ID.fullmatch(value) else None

def classify(changes,policy,go_present,*,full_reason=None,intents=()):
    reasons={g:[] for g in GATES};cases=set();tests=set()
    def add(gates,reason):
        for g in gates:
            if reason not in reasons[g]:reasons[g].append(reason)
    def full(reason):
        add(FULL,reason);cases.update(CASES)
        if go_present:add({'go'},reason)
    add({'source'},'Every delivery verifies source contracts and qualification policy')
    if full_reason:full(full_reason)
    for row in changes:
        p=row['path'];change=row['change'];mode_ok=all(x is None or (x[0] in ('100644','100755') and x[1]=='blob') for x in (row['before'],row['after']))
        if not mode_ok or (change=='M' and row['before'][0]!=row['after'][0]):full('Non-regular Git entry or mode change: '+p);continue
        if is_go(p):add({'go'},'Isolated Go tool change: '+p);continue
        if is_doc(p):continue
        if intent(p) and change=='A':continue  # validated below; minimums only widen selection
        if change=='M' and row['before'][0]==row['after'][0]:
            entry=policy['runtime_only'].get(p)
            if entry:add(ANDROID,'Reviewed runtime boundary: '+p+' — '+entry['reason']);continue
            entry=policy['functional_only'].get(p)
            if entry:
                add(ANDROID,'Reviewed functional boundary: '+p+' — '+entry['reason']);tests.update(entry['tests']);cases.update(entry['cases'])
                if entry['tests']:add({'functional'},'Selected production Activity fixtures: '+p)
                if entry['cases']:add({'adaptive'},'Selected configurations: '+p)
                if entry['terminal']:add({'terminal'},'Terminal contract affected: '+p)
                continue
        # Unknown path, addition/removal, shared model, SDK, resource, build,
        # workflow, policy, test roster or dependency: broaden rather than guess.
        full('Shared or unclassified change: '+p)
    for overlay in intents:
        key=overlay.split('-F',1)[0]; minimum=set(policy['overlay_minimums'][key]);add(minimum-{'go'},'Declared overlay obligation: '+overlay)
        if 'go' in minimum and go_present:add({'go'},'Declared overlay obligation: '+overlay)
        if 'adaptive' in minimum:cases.update(CASES)
        if 'functional' in minimum:tests.update(FUNCTIONS)
    if reasons['visual']:
        # Full visual baseline executes all functional fixtures. Record coverage
        # rather than spending a second emulator on the same test methods.
        reasons['functional']=[];tests.clear();add({'design'},'Visual baseline requires frozen design reference')
    if reasons['functional']:add({'android'},'Functional instrumentation uses verified shared APKs')
    if reasons['visual'] or reasons['adaptive']:add({'android'},'UI verification uses verified shared APKs')
    if reasons['ci24']:add({'android'},'Android source/roster changed')
    out={g:{'required':bool(reasons[g]),'reasons':reasons[g] or ['No relevant change or dependency; positive path classification' if g!='functional' or not reasons['visual'] else 'Covered by full visual baseline']} for g in GATES}
    return out,sorted(cases),sorted(tests)

def plan(root,head,base,event,run_id,attempt):
    require(HEX.fullmatch(head or '') and git(root,'rev-parse','HEAD')==head,'exact checkout required')
    require(DIGITS.fullmatch(str(run_id)) and DIGITS.fullmatch(str(attempt)),'invalid run identity')
    require(event in ('pull_request','push','workflow_dispatch'),'unsupported event')
    require(git(root,'status','--porcelain','--untracked-files=no')=='','tracked source drift')
    policy,policy_digest=load_policy(root,head);after=tree_inventory(root,head)
    full_reason=None;before={};base_observed=None
    if event=='workflow_dispatch':full_reason='Explicit full qualification';
    elif not HEX.fullmatch(base or '') or base=='0'*40:full_reason='Missing source base; conservative full qualification'
    else:
        try:
            before=tree_inventory(root,base);base_observed=base
            ancestor=git(root,'merge-base',base,head)
            if ancestor!=base:full_reason='Divergent base; conservative full qualification'
        except (ValueError,subprocess.SubprocessError,UnicodeError):full_reason='Unavailable base; conservative full qualification'
    changes=diff_paths(before,after) if base_observed else []
    intents=[]
    for row in changes:
        ident=intent(row['path'])
        if ident and row['change']=='A':
            obj=strict_json(git(root,'show',head+':'+row['path'],raw=True))
            require(obj=={'schema':'hooklab.qualification.intent.v1','overlay':ident},'overlay intent identity mismatch')
            intents.append(ident)
    import uxm_contract
    uxm_contract.transition_guard(root,before,after,changes,intents)
    # New Go content must not be silently skipped even without a go.mod.
    go_present=any(is_go(p) for p in after) or any(is_go(r['path']) for r in changes)
    require(not any((p.endswith('.go') or PurePosixPath(p).name in ('go.mod','go.sum')) and not is_go(p) for p in after),'Unregistered Go module boundary; review required')
    gates,cases,tests=classify(changes,policy,go_present,full_reason=full_reason,intents=intents)
    value={'schema':SCHEMA,'repository':REPO,'source_sha':head,'source_tree':git(root,'rev-parse',head+'^{tree}'),'base_sha':base_observed,'requested_base':base,'event':event,'run_id':str(run_id),'run_attempt':str(attempt),'policy_sha256':policy_digest,'changes':changes,'overlays':intents,'gates':gates,'adaptive_cases':cases,'functional_tests':tests,'full_reason':full_reason,'physical_device_qualified':False,'release_qualified':False,'visual_approved':False}
    value['plan_sha256']=sha(canonical(value));return value

def aggregate(expected, observed, jobs, reuse=None):
    require(expected==observed,'plan differs from independent exact-source recomputation')
    require(isinstance(jobs,dict) and set(jobs)==set(GATES),'missing or extra gate results')
    rows={}
    reused=set(reuse['gates']) if reuse else set()
    if reuse:
        import tree_reuse
        require(reuse['current_plan_sha256']==expected['plan_sha256'] and reused and reused<=tree_reuse.REUSE and all(expected['gates'][g]['required'] for g in reused),'reuse is not bound to required gates')
    for gate in GATES:
        needed=expected['gates'][gate]['required'];result=jobs[gate]
        if gate in reused:
            require(result=='skipped','reused gate unexpectedly executed: '+gate)
            rows[gate]={'verdict':'REUSED_TREE_QUALIFIED','job_result':result,'reasons':expected['gates'][gate]['reasons']}
            continue
        require(result==('success' if needed else 'skipped'),'gate '+gate+' expected '+('success' if needed else 'skipped')+' but observed '+str(result))
        rows[gate]={'verdict':'PASS' if needed else 'NOT_APPLICABLE','job_result':result,'reasons':expected['gates'][gate]['reasons']}
    receipt={'schema':RECEIPT,'repository':REPO,'source_sha':expected['source_sha'],'source_tree':expected['source_tree'],'base_sha':expected['base_sha'],'run_id':expected['run_id'],'run_attempt':expected['run_attempt'],'plan_sha256':expected['plan_sha256'],'policy_sha256':expected['policy_sha256'],'gates':rows,'adaptive_cases':expected['adaptive_cases'],'functional_tests':expected['functional_tests'],'overall':'PASS_TARGETED_HOSTED','physical_device_qualified':False,'release_qualified':False,'visual_approved':False}
    if reuse:
        receipt.update(overall='PASS_TARGETED_HOSTED_WITH_REUSE',tree_reuse=reuse)
    return receipt

def cli():
    p=argparse.ArgumentParser();p.add_argument('mode',choices=['plan','aggregate','verify']);p.add_argument('--root',type=Path,default=Path.cwd());p.add_argument('--head',required=True);p.add_argument('--base',default='');p.add_argument('--event',required=True);p.add_argument('--run-id',required=True);p.add_argument('--attempt',required=True);p.add_argument('--plan',type=Path,required=True);p.add_argument('--receipt',type=Path);p.add_argument('--jobs');p.add_argument('--github-output',type=Path);p.add_argument('--reuse',type=Path);a=p.parse_args()
    try:
        expected=plan(a.root,a.head,a.base,a.event,a.run_id,a.attempt)
        if a.mode=='plan':
            a.plan.parent.mkdir(parents=True,exist_ok=True);a.plan.write_bytes(canonical(expected)+b'\n')
            reuse=None
            if a.reuse is not None and a.event=='push' and os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('GH_TOKEN'):
                import tree_reuse
                reuse=tree_reuse.decide(a.root,expected,tree_reuse.Api(os.environ['GH_TOKEN']),int(a.run_id))
                if reuse:
                    require(not a.reuse.exists(),'fresh reuse receipt required')
                    a.reuse.write_bytes(canonical(reuse)+b'\n')
            if a.github_output:
                values={k:str(v['required']).lower() for k,v in expected['gates'].items()}
                if reuse:
                    for g in reuse['gates']:values[g]='false'
                values.update(adaptive_cases=json.dumps([{'case':c,'api':CASES[c]} for c in expected['adaptive_cases']],separators=(',',':')),functional_tests=','.join(expected['functional_tests']))
                with a.github_output.open('a') as f:
                    for k,v in values.items():f.write(k+'='+v+'\n')
            print('QUALIFICATION_PLAN',expected['plan_sha256'])
        else:
            require(a.receipt is not None and a.jobs is not None,'receipt and independent jobs required')
            require(a.plan.is_file() and not a.plan.is_symlink() and a.plan.stat().st_size<=8*1024*1024,'unsafe plan artifact')
            reuse=None
            if a.reuse is not None and a.reuse.exists():
                require(a.event=='push' and a.reuse.is_file() and not a.reuse.is_symlink() and a.reuse.stat().st_size<=8*1024*1024,'invalid reuse proof')
                import tree_reuse
                reuse=tree_reuse.admit(a.root,expected,tree_reuse.Api(os.environ.get('GH_TOKEN')),int(a.run_id))
                require(strict_json(a.reuse.read_bytes())==reuse,'reuse evidence changed since impact planning')
            result=aggregate(expected,strict_json(a.plan.read_bytes()),strict_json(a.jobs),reuse)
            if a.mode=='verify':require(a.receipt.is_file() and not a.receipt.is_symlink() and strict_json(a.receipt.read_bytes())==result,'aggregate receipt differs')
            else:a.receipt.write_bytes(canonical(result)+b'\n')
            print(result['overall'],result['plan_sha256'])
        return 0
    except (ValueError,TypeError,KeyError,OSError,subprocess.SubprocessError) as exc:
        print('QUALIFICATION_REFUSED',str(exc),file=sys.stderr);return 2
if __name__=='__main__':raise SystemExit(cli())
