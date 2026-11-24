#!/usr/bin/env python3
"""Read-only executable shell fixture + source guard for MCC-R01; no Gradle/root/network."""
from __future__ import annotations
import json, os, pathlib, re, subprocess, sys, tempfile

CMD='app/src/main/java/com/mikeyphw/hooklab/mcc/MccRefillCommand.java'
WORKER='app/src/main/java/com/mikeyphw/hooklab/mcc/MccRefillWorker.java'
TEST='app/src/test/java/com/mikeyphw/hooklab/mcc/MccRootDiscoveryR01Test.java'
DATA='/data/user/0/br.art.code.meucronogramacapilar'


def generated_probe(text: str) -> str:
    start='return "set -eu\\n"'
    assert text.count(start)==1, 'MCC R01 generated shell entry drift'
    t=text.split(start,1)[1]
    t=t.split('            + (probe',1)[0]
    pkgexpr=re.compile(r'"pkg=\'"\s*\+\s*MccIds\.PACKAGE\s*\+\s*"\'\\n"')
    assert len(pkgexpr.findall(t))==1, 'MCC package expression drift'
    t=pkgexpr.sub(lambda m: '"pkg=\'br.art.code.meucronogramacapilar\'\\n"',t,count=1)
    dynamic=re.compile(r'"data=\'"\s*\+\s*data\s*\+\s*"\'\\n"')
    assert len(dynamic.findall(t))==1, 'MCC data directory expression drift'
    t=dynamic.sub(lambda m: '+ "data=\''+DATA+'\'\\n"', t, count=1)
    segments=re.findall(r'"(?:\\.|[^"\\])*"',t)
    assert len(segments)>35, 'MCC R01 scan shell truncated'
    script='set -eu\n'+''.join(json.loads(s) for s in segments)
    assert 'scan_base "$data"' in script, 'MCC R01 has no primary scanner'
    assert 'MCC_STORAGE_FALLBACK_USED' in script, 'MCC R01 lacks fallback receipt'
    assert 'MCC_SCAN_VISIBLE_PRIMARY=' in script and 'MCC_SCAN_VISIBLE_ALIAS=' in script
    assert '[ "$n" -eq 1 ]' in script, 'MCC R01 account ambiguity guard missing'
    assert 'MCC_STORAGE_DIRECTORIES_UNAVAILABLE' in script
    assert "printf '%s' '9' >" not in script, 'MCC probe must not write'
    return script+'printf "MCC_STORAGE_READY\\n"\n'


def scenario(probe: str, temp: pathlib.Path, mode: str) -> tuple[int,str]:
    primary=temp/'primary'; user0=temp/'user0'; alias=temp/'alias'
    for folder in (primary,user0,alias):folder.mkdir()
    def balance(root, prefix, content):
        d=root/'databases';d.mkdir(exist_ok=True)
        (d/(prefix+'-DiamondBalance')).write_bytes(content)
    if mode=='primary':balance(primary,'accountuser123',b'7')
    elif mode=='fallback':
        (primary/'databases').mkdir()
        balance(user0,'accountuser123',b'3')
    elif mode=='alias':balance(alias,'accountuser123',b'3')
    elif mode=='empty':(primary/'databases').mkdir()
    elif mode=='badname':balance(primary,'bad.dot',b'9')
    elif mode=='double':
        balance(primary,'accountuser123',b'4')
        balance(primary,'accountuser456',b'7')
    elif mode=='symlink':(primary/'databases').symlink_to(alias,target_is_directory=True)
    elif mode=='format':balance(primary,'accountuser123',b'123')
    elif mode=='missing':pass
    elif mode=='unreadable':
        d=primary/'databases';d.mkdir();d.chmod(0)
    elif mode=='user10':
        (primary/'databases').mkdir()
        balance(user0,'accountuser123',b'3')
    else:raise AssertionError(mode)
    s=probe.replace("data='"+DATA+"'", "data='"+str(primary)+"'")
    assert s!=probe, 'primary data rewrite failed'
    s=s.replace('[ "$(id -u)" = \'0\' ]','[ "0" = \'0\' ]')
    old='case "$data" in /data/data/*) user=0 ;; /data/user/*) user=${data#/data/user/}; user=${user%%/*} ;; esac'
    assert old in s,'user context rewrite preflight failed'
    s=s.replace(old,'user=10' if mode=='user10' else 'user=0')
    s=s.replace('/data/user/0/$pkg',str(user0))
    s=s.replace('/data/data/$pkg',str(alias))
    p=subprocess.run(['sh','-c',s],capture_output=True,text=True,timeout=10)
    if mode=='unreadable': (primary/'databases').chmod(0o700)
    # Never display stdout/stderr in success path to avoid accidental data leaks.
    return p.returncode,(p.stdout+'\n'+p.stderr).strip()


def main(root:pathlib.Path)->int:
    src=(root/CMD).read_text(encoding='utf8')
    worker=(root/WORKER).read_text(encoding='utf8')
    tests=(root/TEST).read_text(encoding='utf8')
    assert 'STORAGE_DIRECTORY_UNAVAILABLE_FROM_HOOKLAB_ROOT' in worker
    assert 'BALANCE_FILE_READY_USER0_ALIAS' in worker
    assert 'MCC_STORAGE_FALLBACK_USED' in worker
    assert 'public void fallbackAfterPrimaryEmptyIsClearlyIdentified' in tests
    assert 'public void otherAndroidUserNeverUsesUserZeroFallback' in tests
    assert 'MCC_ROOT_NOT_GRANTED' in src
    assert 'MCC_STORAGE_UNSAFE' in src
    probe=generated_probe(src)
    cases={'primary':(0,'MCC_STORAGE_READY'),'fallback':(0,'MCC_STORAGE_FALLBACK_USED'),
           'alias':(0,'MCC_STORAGE_FALLBACK_USED'),'empty':(51,'MCC_BALANCE_FILE_NOT_FOUND'),
           'missing':(58,'MCC_STORAGE_DIRECTORIES_UNAVAILABLE'),
           'unreadable':(58,'MCC_STORAGE_INACCESSIBLE'),
           'user10':(51,'MCC_BALANCE_FILE_NOT_FOUND'),
           'badname':(56,'MCC_NAME_UNRECOGNIZED'),'double':(53,'MCC_MULTIPLE_ACCOUNTS'),
           'symlink':(52,'MCC_STORAGE_UNSAFE'),'format':(54,'MCC_BALANCE_FORMAT_UNKNOWN')}
    with tempfile.TemporaryDirectory(prefix='mcc-r01-fixtures-') as dirname:
        for name,(expected,marker) in cases.items():
            if name=='unreadable' and os.geteuid()==0:
                print('MCC_R01_SCENARIO=unreadable SKIP reason=root-uid-ignores-mode-bits')
                continue
            cwd=pathlib.Path(dirname)/name;cwd.mkdir()
            rc,output=scenario(probe,cwd,name)
            assert rc==expected and marker in output, (name,rc,output[-350:])
            if name not in ('unreadable','badname','double','symlink','format'):
                assert 'MCC_SCAN_VISIBLE_PRIMARY=' in output and 'MCC_SCAN_VISIBLE_ALIAS=' in output, name
            print('MCC_R01_SCENARIO='+name+' PASS exit='+str(rc))
    print('MCC_R01_SOURCE_CONTRACT=PASS root_mutation=NOT_RUN device=NOT_RUN')
    return 0
if __name__=='__main__':
    try:raise SystemExit(main(pathlib.Path(__file__).resolve().parents[1]))
    except (OSError,RuntimeError,AssertionError,ValueError) as e:
        print('MCC_R01_SOURCE_CONTRACT=FAIL '+type(e).__name__+' '+str(e)[:180],file=sys.stderr)
        raise SystemExit(1)
