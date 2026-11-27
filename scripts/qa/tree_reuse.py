"""Fail-closed admission of completed same-tree PR evidence on main merge pushes.

No artifact is executed or relabelled. Only explicitly tree-bound gates can be
reused; the original PR SHA, run, attempt, artifact digests and gate roster are
retained. Any ambiguity returns a fresh plan, not successful evidence.
"""
from __future__ import annotations
import argparse
import datetime as dt
import io
import json
import os
import re
import stat
import tempfile
import urllib.error
import subprocess
import sys
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path
import qualification as q

REUSE = frozenset(('ci24', 'terminal', 'adaptive', 'go'))
WORKFLOW = '.github/workflows/android.yml'
WORKFLOW_ID = 425729236
MAX_JSON = 16 * 1024 * 1024
MAX_ZIP = 256 * 1024 * 1024
MAX_TOTAL = 512 * 1024 * 1024
DIRECT = {'source':'HookLab / source contracts','android':'Android build, unit tests and lint',
          'terminal':'HookLab / terminal contract','go':'HookLab / Go qualification'}
BASE_JOBS = {'HookLab / impact plan','HookLab / targeted qualification'}
HEX256 = re.compile(r'[a-f0-9]{64}\Z')


def require(ok, reason):
    q.require(ok, reason)


def integer(value):
    return type(value) is int and value > 0


def when(value):
    require(isinstance(value, str), 'invalid timestamp')
    out = dt.datetime.fromisoformat(value.replace('Z', '+00:00'))
    require(out.tzinfo is not None, 'timestamp lacks timezone')
    return out


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Api:
    """Fixed GitHub API origin; no authorization header on artifact redirects."""
    def __init__(self, token):
        require(bool(token), 'missing read-only GitHub token')
        self.token = token
        self.opener = urllib.request.build_opener(NoRedirect())

    def _request(self, path, accept):
        require(path.startswith('/repos/'+q.REPO+'/') and not any(c in path for c in ('\r','\n','#','\\')), 'unexpected API path')
        return urllib.request.Request('https://api.github.com'+path, headers={
            'Authorization':'Bearer '+self.token, 'Accept':accept,
            'X-GitHub-Api-Version':'2022-11-28', 'User-Agent':'HookLab-tree-qualification'})

    def json(self, path):
        with self.opener.open(self._request(path,'application/vnd.github+json'), timeout=30) as response:
            data=response.read(MAX_JSON+1)
        require(len(data)<=MAX_JSON,'oversized API response')
        return q.strict_json(data)

    def artifact(self, aid, size):
        require(integer(aid) and integer(size) and size<=MAX_ZIP,'artifact bounds')
        try:
            response=self.opener.open(self._request(f'/repos/{q.REPO}/actions/artifacts/{aid}/zip','application/octet-stream'),timeout=30)
        except urllib.error.HTTPError as error:
            require(error.code in (301,302,303,307,308),'artifact download refused')
            target=urllib.parse.urlsplit(error.headers.get('Location',''))
            require(target.scheme=='https' and target.hostname and not target.username and not target.password and target.port in (None,443),'unsafe download redirect')
            # GitHub controls the signed download URL. Explicitly restrict known
            # Actions storage domains and never forward the GitHub bearer token.
            host=target.hostname.lower()
            require(host.endswith(('.blob.core.windows.net','.actions.githubusercontent.com','.githubusercontent.com')),'unrecognized artifact storage')
            response=self.opener.open(urllib.request.Request(target.geturl(),headers={'User-Agent':'HookLab-tree-qualification'}),timeout=60)
        with response:
            raw=response.read(size+1)
        require(len(raw)==size,'artifact length mismatch')
        return raw


def collection(obj, key):
    rows=obj.get(key)
    require(isinstance(rows,list) and obj.get('total_count')==len(rows) and len(rows)<=100,'incomplete API inventory: '+key)
    return rows


def provider_jobs(plan, listing, rid, attempt, head):
    actual={}
    for row in collection(listing,'jobs'):
        name=row.get('name')
        require(isinstance(name,str) and name not in actual and row.get('status')=='completed' and row.get('run_id')==rid and row.get('run_attempt')==attempt and row.get('head_sha')==head,'job identity or uniqueness mismatch')
        require(row.get('conclusion') in ('success','skipped'),'prior job was not successful or intentionally skipped')
        actual[name]=row['conclusion']
    require(all(actual.get(name)=='success' for name in BASE_JOBS),'prior final gate not successful')
    expected={g:{n} for g,n in DIRECT.items()}
    expected.update(ci24={f'ci24 / JVM / shard {i}/24' for i in range(1,25)}|{'ci24 / Summarize all 24 shards (fail closed)'},
        adaptive={'adaptive / Adaptive UI / '+c for c in plan['adaptive_cases']}|{'adaptive / Extended UI / exact matrix aggregate'},
        design={'design / Frozen browser reference and semantic gate'},visual={'visual / Real Activity native visual candidate gate'},
        functional={'functional / Production Activity selected fixtures'})
    used=set(BASE_JOBS); jobs={}
    for gate,names in expected.items():
        relevant={n for n in actual if n in names or n==gate or n.startswith(gate+' / ')}
        for optional in {'ci24':{'ci24 / Standalone dependency preparation'},'adaptive':{'adaptive / Extended UI / exact shared APK build'}}.get(gate,set()) & relevant:
            require(actual[optional]=='skipped','redundant prior build executed');relevant.remove(optional);used.add(optional)
        if plan['gates'][gate]['required']:
            require(relevant==names and all(actual[n]=='success' for n in names),'incomplete prior required gate '+gate)
            jobs[gate]='success'
        else:
            require(all(actual[n]=='skipped' for n in relevant) and (gate not in DIRECT or relevant==names),'prior excluded gate mismatch '+gate)
            jobs[gate]='skipped'
        used.update(relevant)
    require(used==set(actual),'unexpected prior provider job')
    return jobs


def read_json_zip(raw, wanted):
    """Read a fixed small JSON member set, never extract files."""
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        names=set(); result={}
        for item in z.infolist():
            name=item.filename;kind=stat.S_IFMT(item.external_attr>>16)
            require(name not in names and name in wanted and not item.is_dir() and kind in (0,stat.S_IFREG) and 0<item.file_size<=MAX_JSON,'unexpected or unsafe evidence member')
            names.add(name);result[name]=q.strict_json(z.read(item))
        require(names==set(wanted),'missing evidence member')
        return result


def artifact_inventory(api, rid, attempt, head, repo_id, names):
    prefix=f'/repos/{q.REPO}'
    rows=collection(api.json(f'{prefix}/actions/runs/{rid}/artifacts?per_page=100'),'artifacts')
    out={}
    for row in rows:
        name=row.get('name')
        if name not in names:continue
        binding=row.get('workflow_run') or {};digest=row.get('digest','')
        require(name not in out and integer(row.get('id')) and integer(row.get('size_in_bytes')) and row['size_in_bytes']<=MAX_ZIP and row.get('expired') is False and isinstance(digest,str) and digest.startswith('sha256:') and HEX256.fullmatch(digest[7:]),'artifact admission failed')
        require(binding.get('id')==rid and binding.get('head_sha')==head and binding.get('repository_id')==repo_id and binding.get('head_repository_id')==repo_id,'artifact run or repository mismatch')
        require(when(row['expires_at'])>dt.datetime.now(dt.timezone.utc),'expired artifact')
        out[name]={'id':row['id'],'sha256':digest[7:],'size':row['size_in_bytes']}
    require(set(out)==set(names),'missing prior artifacts')
    return out


def archive(api, row):
    raw=api.artifact(row['id'],row['size']);require(q.sha(raw)==row['sha256'],'artifact digest mismatch');return raw


def unpack_evidence(raw, target, budget):
    """Only data files into a fresh temporary directory; never execute contents."""
    target.mkdir()
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        seen=set()
        for item in z.infolist():
            name=item.filename; kind=stat.S_IFMT(item.external_attr>>16)
            parts=name.rstrip('/').split('/')
            require(len(parts)<=3 and all(re.fullmatch(r'[A-Za-z0-9_.-]{1,200}',part) and part not in ('.','..') for part in parts) and name not in seen,'unsafe evidence entry')
            require(kind in ((0,stat.S_IFDIR) if item.is_dir() else (0,stat.S_IFREG)) and len(seen)<2048,'unsafe evidence member type/count')
            seen.add(name);dest=target.joinpath(*parts)
            if item.is_dir():
                require(item.file_size==0,'invalid directory entry');dest.mkdir(parents=True,exist_ok=True);continue
            require(0<item.file_size<=MAX_ZIP and item.file_size<=budget[0],'expanded evidence bounds')
            budget[0]-=item.file_size;dest.parent.mkdir(parents=True,exist_ok=True)
            with z.open(item) as src,dest.open('xb') as dst:
                data=src.read(item.file_size+1);require(len(data)==item.file_size,'expanded evidence length');dst.write(data)
        require(seen,'empty evidence archive')


def verify_payloads(root, expected, eligible, api, inv, qual):
    """Re-run the same read-only validators over the admitted PR bytes.

    The checkout is the identical merged tree; source identity in evidence
    deliberately stays the original PR commit. No artifact becomes main output.
    """
    import ci24
    import adaptive_matrix as adaptive
    rid=expected['run_id'];attempt=expected['run_attempt'];suffix=f'{rid}-{attempt}'
    ident={k:expected[k] for k in ('source_sha','source_tree','run_id','run_attempt','repository')}
    with tempfile.TemporaryDirectory(prefix='hooklab-reuse-') as folder:
        dest=Path(folder);budget=[MAX_TOTAL]
        require(sum(x['size'] for x in inv.values())<=MAX_TOTAL,'compressed evidence bounds')
        for name,row in sorted(inv.items()):
            if name!=qual:unpack_evidence(archive(api,row),dest/name,budget)
        if 'ci24' in eligible:
            dep=dest/f'hooklab-dependencies-{suffix}'/'ready.json'
            require({p.name for p in dep.parent.iterdir()}=={'ready.json'},'dependency evidence roster')
            dh=ci24.ready_digest(root,dep,ident);classes=ci24.inventory(root)
            shards=[ci24.verify_shard(dest/f'hooklab-ci24-{suffix}-shard-{i}',ci24.assignment(classes,i),ident,i,dh) for i in range(1,25)]
            summary={'schema':ci24.SUMMARY,**ident,'dependency_receipt_sha256':dh,'overall':'PASS_CI24_VERIFIED','shard_count':24,'class_count':len(classes),'test_count':sum(x['test_count'] for x in shards),'shards':shards,'release_qualified':False,'physical_device_qualified':False,'emulator_ui_qualified':False}
            f=dest/f'hooklab-ci24-{suffix}-summary'
            require({p.name for p in f.iterdir()}=={'CI24_SUMMARY.json'} and q.strict_json((f/'CI24_SUMMARY.json').read_bytes())==summary,'independent CI24 summary mismatch')
        if 'adaptive' in eligible:
            aid={'schema':'hooklab.uxr04.case.v1',**ident,'hosted_emulator_only':True,'physical_device_qualified':False,'release_qualified':False,'visual_approved':False}
            build=adaptive.shared_apks(dest/f'hooklab-matrix-apks-{suffix}',aid)
            cases={case:adaptive.validate(dest/f'hooklab-extended-{suffix}-{case}',case,aid,build)['performance_summary'] for case in expected['adaptive_cases']}
            f=dest/f'hooklab-extended-summary-{suffix}'
            require({p.name for p in f.iterdir()}=={'extended-summary.json'} and q.strict_json((f/'extended-summary.json').read_bytes())==cases,'independent adaptive summary mismatch')
        if 'go' in eligible:
            f=dest/f'hooklab-go-{suffix}'
            require({p.name for p in f.iterdir()}=={'go-receipt.json'},'Go evidence roster')
            data=q.strict_json((f/'go-receipt.json').read_bytes())
            files={p:q.sha((root/p).read_bytes()) for p in q.git(root,'ls-files','tools/hooklabctl').splitlines() if q.is_go(p)}
            wanted={'schema':'hooklab.go.qualification.v1',**ident,'module':'tools/hooklabctl','files':files,'vet':'PASS','test_race':'PASS','build':'PASS'}
            require(set(data)==set(wanted)|{'tests'} and all(data[k]==v for k,v in wanted.items()),'independent Go receipt mismatch')
            tests=data['tests'];require(isinstance(tests,list) and tests and all(isinstance(t,str) and len(t)<=512 and '::' in t for t in tests) and tests==sorted(set(tests)),'invalid Go test roster')


def admit(root, plan, api, current_run):
    require(plan['event']=='push' and plan['base_sha'] and plan['requested_base']==plan['base_sha'] and plan['full_reason'] is None,'fresh qualification event/base')
    head=plan['source_sha'];base=plan['base_sha'];tree=plan['source_tree']
    parents=q.git(root,'rev-list','--parents','-n','1',head).split()
    require(len(parents)==3 and parents[:2]==[head,base],'not an exact two-parent merge')
    prior=parents[2];require(q.git(root,'rev-parse',prior+'^{tree}')==tree,'different merged tree')
    prefix='/repos/'+q.REPO
    current=api.json(f'{prefix}/actions/runs/{current_run}')
    repo=current.get('repository') or {};rid_repo=repo.get('id')
    require(integer(rid_repo) and repo.get('full_name')==q.REPO and current.get('id')==int(plan['run_id']) and current.get('run_attempt')==int(plan['run_attempt']) and current.get('event')=='push' and current.get('head_sha')==head and current.get('head_branch')=='main' and current.get('workflow_id')==WORKFLOW_ID and current.get('path','').split('@')[0]==WORKFLOW,'current run identity mismatch')
    pulls=api.json(f'{prefix}/commits/{head}/pulls?per_page=100')
    require(isinstance(pulls,list) and len(pulls)==1,'ambiguous merge PR')
    pr=pulls[0];number=pr.get('number');require(integer(number),'missing PR identity')
    pr=api.json(f'{prefix}/pulls/{number}');h=pr.get('head') or {};b=pr.get('base') or {}
    require(pr.get('merged') is True and pr.get('state')=='closed' and pr.get('merge_commit_sha')==head and h.get('sha')==prior and b.get('sha')==base and b.get('ref')=='main' and (h.get('repo') or {}).get('id')==rid_repo and (b.get('repo') or {}).get('id')==rid_repo,'not a trusted same-repository merged PR')
    merged_at=when(pr['merged_at'])
    rows=collection(api.json(f'{prefix}/actions/workflows/{WORKFLOW_ID}/runs?head_sha={prior}&event=pull_request&per_page=100'),'workflow_runs')
    require(len(rows)==1 and integer(rows[0].get('id')),'ambiguous prior workflow run')
    rid=rows[0]['id'];require(rid!=current_run,'current run cannot qualify itself')
    def prior_run():
        row=api.json(f'{prefix}/actions/runs/{rid}');attempt=row.get('run_attempt')
        require(row.get('id')==rid and integer(attempt) and row.get('workflow_id')==WORKFLOW_ID and row.get('path','').split('@')[0]==WORKFLOW and row.get('head_sha')==prior and row.get('head_branch')==h.get('ref') and row.get('event')=='pull_request' and row.get('status')=='completed' and row.get('conclusion')=='success' and (row.get('repository') or {}).get('id')==rid_repo and (row.get('head_repository') or {}).get('id')==rid_repo,'prior run identity or conclusion mismatch')
        prs=row.get('pull_requests');require(isinstance(prs,list) and (not prs or any(p.get('number')==number for p in prs)),'prior PR mismatch')
        require(when(row['created_at'])<=when(row['updated_at'])<=merged_at,'prior qualification not completed before merge')
        return row
    row=prior_run();attempt=row['run_attempt']
    expected=q.plan(root,head,base,'pull_request',str(rid),str(attempt));expected['source_sha']=prior;expected.pop('plan_sha256');expected['plan_sha256']=q.sha(q.canonical(expected))
    # Plan equivalence is based on the exact same tree and base, not just a
    # successful job name, workflow ID or changed-files approximation.
    require(all(expected[k]==plan[k] for k in ('source_tree','base_sha','policy_sha256','changes','overlays','gates','adaptive_cases','functional_tests','full_reason')),'prior qualification coverage differs')
    listing=api.json(f'{prefix}/actions/runs/{rid}/attempts/{attempt}/jobs?per_page=100')
    jobs=provider_jobs(expected,listing,rid,attempt,prior)
    eligible=sorted(g for g in REUSE if expected['gates'][g]['required'])
    require(eligible,'no reusable gates')
    qual=f'hooklab-qualification-{rid}-{attempt}'
    names={qual}
    if 'ci24' in eligible:names|={f'hooklab-ci24-{rid}-{attempt}-shard-{i}' for i in range(1,25)}|{f'hooklab-ci24-{rid}-{attempt}-summary',f'hooklab-dependencies-{rid}-{attempt}'}
    if 'adaptive' in eligible:names|={f'hooklab-extended-{rid}-{attempt}-{c}' for c in expected['adaptive_cases']}|{f'hooklab-extended-summary-{rid}-{attempt}',f'hooklab-matrix-apks-{rid}-{attempt}'}
    if 'go' in eligible:names.add(f'hooklab-go-{rid}-{attempt}')
    inv=artifact_inventory(api,rid,attempt,prior,rid_repo,names)
    evidence=read_json_zip(archive(api,inv[qual]),{'plan.json','receipt.json'})
    require(evidence['plan.json']==expected and evidence['receipt.json']==q.aggregate(expected,expected,jobs),'prior qualification receipt differs from exact plan and provider results')
    verify_payloads(root,expected,eligible,api,inv,qual)
    # Preserve each artifact's independent provider identity/digest. The
    # aggregate and per-gate verification jobs in the admitted run succeeded.
    # No commit-bound artifact is copied to main or claimed rebuilt there.
    require(prior_run()==row and artifact_inventory(api,rid,attempt,prior,rid_repo,names)==inv,'prior evidence changed during admission')
    return {'schema':'hooklab.tree-reuse.v1','repository':q.REPO,'current_commit':head,'current_tree':tree,'current_base':base,'current_run':str(current_run),'current_attempt':plan['run_attempt'],'current_plan_sha256':plan['plan_sha256'],'prior_commit':prior,'prior_run':str(rid),'prior_attempt':str(attempt),'prior_pr':number,'prior_plan_sha256':expected['plan_sha256'],'prior_receipt_sha256':q.sha(q.canonical(evidence['receipt.json'])),'gates':eligible,'artifacts':inv}


def decide(root,plan,api,current_run):
    if plan['event']!='push':return None
    try:return admit(root,plan,api,current_run)
    except Exception:
        # Do not expose API errors or signed URLs. The original full gates run.
        print('TREE_REUSE_NOT_ADMITTED: fresh required qualification',flush=True)
        return None
