"""Run real Go build, vet and race tests for the isolated tool module."""
import argparse, json, os, subprocess, sys
from pathlib import Path
from qualification import require,git,sha,canonical,is_go

def run(root,source,run_id,attempt,out):
    require(os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('RUNNER_OS')=='Linux','hosted Go gate required')
    require(git(root,'rev-parse','HEAD')==source and git(root,'status','--porcelain','--untracked-files=no')=='','source drift')
    module=root/'tools/hooklabctl';require(module.is_dir() and not module.is_symlink() and (module/'go.mod').is_file() and not (module/'go.mod').is_symlink(),'Go source without supported module root')
    tracked=git(root,'ls-files','tools/hooklabctl').splitlines();require(all(not (root/p).is_symlink() for p in tracked),'symlinked Go tool source')
    require([p for p in tracked if p.endswith('.go') and not p.endswith('_test.go')],'no Go production source')
    require([p for p in tracked if p.endswith('_test.go')],'no Go tests')
    require([p for p in tracked if p.endswith('/go.mod')]==['tools/hooklabctl/go.mod'],'nested Go module requires reviewed gate')
    env={**os.environ,'GOTOOLCHAIN':'local','GOWORK':'off','GOFLAGS':'-mod=readonly'}
    subprocess.run(['go','vet','./...'],cwd=module,env=env,check=True,timeout=900)
    result=subprocess.run(['go','test','-json','-race','-count=1','./...'],cwd=module,env=env,check=True,timeout=900,capture_output=True,text=True)
    events=[json.loads(line) for line in result.stdout.splitlines()]
    require(not any(e.get('Action')=='fail' for e in events),'Go test failure')
    passed=sorted({e['Package']+'::'+e['Test'] for e in events if e.get('Action')=='pass' and e.get('Test')})
    require(passed,'No executed Go tests passed')
    subprocess.run(['go','build','./...'],cwd=module,env=env,check=True,timeout=900)
    require(git(root,'status','--porcelain','--untracked-files=no')=='','Go validation mutated tracked source')
    value={'schema':'hooklab.go.qualification.v1','source_sha':source,'source_tree':git(root,'rev-parse','HEAD^{tree}'),'run_id':run_id,'run_attempt':attempt,'repository':'Mikeyphw/MikeysHookLab','module':'tools/hooklabctl','files':{p:sha((root/p).read_bytes()) for p in tracked if is_go(p)},'vet':'PASS','test_race':'PASS','tests':passed,'build':'PASS'}
    out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(canonical(value)+b'\n')

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    try:run(Path.cwd(),os.environ['SOURCE_SHA'],os.environ['GITHUB_RUN_ID'],os.environ['GITHUB_RUN_ATTEMPT'],a.out);return 0
    except (ValueError,KeyError,TypeError,OSError,subprocess.SubprocessError) as exc:print('GO_GATE_REFUSED',str(exc),file=sys.stderr);return 2
if __name__=='__main__':raise SystemExit(main())
