#!/usr/bin/env python3
"""Exercise production Java argv, isolated shell bootstrap and real Zsh PTY (hosted Linux).
Only the constant Termux filesystem prefix is relocated in fixture scripts. No Gradle,
Android or user shell configuration is touched. No real command is taken from a user.
"""
import argparse, os, pathlib, pty, select, shutil, subprocess, tempfile, time, errno
ROOT=pathlib.Path(__file__).resolve().parents[2]
POLICY=ROOT/'app/src/main/java/com/mikeyphw/hooklab/termux/TermuxCommandPolicy.java'
PREFIX='/data/data/com.termux/files/usr'

def require(value,reason):
    if not value: raise RuntimeError(reason)

def compile_policy(root):
    helper=root/'Args.java'
    helper.write_text('import com.mikeyphw.hooklab.termux.TermuxCommandPolicy; public class Args { public static void main(String[] args) { for(String s:TermuxCommandPolicy.arguments(args[0],args[1])) { System.out.print(s);System.out.print((char)0); } }}')
    subprocess.run(['javac','-encoding','UTF-8','-d',str(root),str(POLICY),str(helper)],check=True,timeout=30)

def args(root,prefix,source,shell):
    raw=subprocess.check_output(['java','-Dfile.encoding=UTF-8','-cp',str(root),'Args',source,shell],timeout=10)
    values=raw.decode().split('\0');require(values[-1]=='' and len(values)==8,'invalid production argv');values.pop()
    require(values[3]==source and values[4]==shell and values[2]=='hooklab','payload argv drift')
    for i in (1,5,6):values[i]=values[i].replace(PREFIX,str(prefix))
    return ['/bin/sh',*values]

def read_until(fd,token,deadline):
    data=b''
    while time.monotonic()<deadline:
        if select.select([fd],[],[],.2)[0]:
            try:b=os.read(fd,65536)
            except OSError as e:
                if e.errno==errno.EIO:break
                raise
            if not b:break
            data+=b
            if token in data:return data
    raise RuntimeError('PTY output missing '+repr(token)+'; received '+repr(data[-1000:]))

def real_case(root,prefix,zsh,custom,shell,source,status):
    home=root/('home-'+shell+('-custom' if custom else ''));home.mkdir();dot=home/'dot' if custom else home;dot.mkdir(exist_ok=True)
    (dot/'.zshenv').write_text('print -r -- "ENV_NOART=$NOART" >> "$HOME/startup"\n')
    (dot/'.zshrc').write_text('print -r -- "RC_NOART=$NOART" >> "$HOME/startup"\n[[ $NOART == 1 ]] || print -r -- ART_MUST_NOT_APPEAR\nfunction fixture_fn() { print -r -- FN_OK; }\nalias fixture_alias="print -r -- ALIAS_OK"\nPROMPT="HL_READY> "\n')
    env={'HOME':str(home),'PATH':str(prefix/'bin')+':/usr/bin:/bin','TERM':'dumb','LANG':'C.UTF-8'}
    if custom:env['ZDOTDIR']=str(dot)
    pid,fd=pty.fork()
    if pid==0:os.execve('/bin/sh',args(root,prefix,source,shell),env)
    try:
        first=read_until(fd,b'HL_READY> ',time.monotonic()+20)
        require(f'[HookLab] Command exit: {status}'.encode() in first,'exit status missing')
        require(b'ART_MUST_NOT_APPEAR' not in first,'startup art leaked')
        require((home/'startup').read_text().splitlines()==['ENV_NOART=1','RC_NOART=1'],'startup not exactly once')
        if shell=='zsh':require(b'ALIAS_OK' in first and b'FN_OK' in first and b'PAYLOAD_UNEXPORTED' in first,'user configuration or payload isolation lost')
        require(not list((prefix/'tmp').iterdir()),'startup shim not removed')
        os.write(fd,b'print -r -- CONTINUED_OK; exit 0\n')
        rest=read_until(fd,b'\r\nCONTINUED_OK\r\n',time.monotonic()+10)
        _,rc=os.waitpid(pid,0);pid=None;require(os.waitstatus_to_exitcode(rc)==0,'continuation failed')
    finally:
        os.close(fd)
        if pid is not None:
            try:os.kill(pid,9);os.waitpid(pid,0)
            except ProcessLookupError:pass
    print('TERMUX_REAL_ZSH_PTY_PASS',shell,'custom_zdotdir='+str(custom),'exit='+str(status),flush=True)

def main():
    a=argparse.ArgumentParser();a.add_argument('--require-zsh',action='store_true');a.add_argument('--bootstrap-only',action='store_true');o=a.parse_args()
    with tempfile.TemporaryDirectory(prefix='hooklab-session-fixture-') as td:
        root=pathlib.Path(td);compile_policy(root);prefix=root/'usr';(prefix/'bin').mkdir(parents=True);(prefix/'tmp').mkdir()
        sentinel="printf '%s\\n' 'quote, \\\" and 💡'\nexit 7\n";home=root/'home';home.mkdir()
        mock=prefix/'bin/zsh';mock.write_text('#!/bin/sh\n[ "$NOART" = 1 ] && [ "$1" = -i ] && [ "$HOOKLAB_R06_SOURCE" = "$EXPECTED" ] && [ "$HOOKLAB_R06_SHELL" = zsh ] && [ -f "$ZDOTDIR/.zshenv" ] && [ -f "$ZDOTDIR/.zshrc" ] || exit 87\n[ "$(stat -c %a "$ZDOTDIR")" = 700 ] || exit 88\n! grep -F -- "quote," "$ZDOTDIR/.zshenv" "$ZDOTDIR/.zshrc" >/dev/null || exit 89\nrm -f -- "$ZDOTDIR/.zshenv" "$ZDOTDIR/.zshrc"; rmdir -- "$ZDOTDIR"\n');mock.chmod(0o700)
        env={'HOME':str(home),'PATH':'/usr/bin:/bin','EXPECTED':sentinel,'TERM':'dumb'}
        cmd=args(root,prefix,sentinel,'zsh')
        subprocess.run(cmd,env=env,check=True,timeout=10)
        require(not list((prefix/'tmp').iterdir()),'fixture bootstrap leaked files')
        mock.unlink();missing=subprocess.run(cmd,env=env,capture_output=True,timeout=10);require(missing.returncode==127 and b'zsh is required' in missing.stderr,'missing Zsh not refused')
        print('TERMUX_BOOTSTRAP_PASS exact argv, private shim, NOART before startup, missing Zsh refusal',flush=True)
        if o.bootstrap_only:return
        zsh=shutil.which('zsh');require(zsh or not o.require_zsh,'real Zsh required but unavailable')
        if not zsh:print('TERMUX_REAL_ZSH_PTY_NOT_RUN');return
        mock.symlink_to(zsh)
        for name in ('sh','bash','python3'):
            exe=shutil.which(name);require(exe,'fixture interpreter missing '+name);(prefix/'bin'/name).symlink_to(exe)
        payload='fixture_alias\nfixture_fn\nif ! env | grep -q "^HOOKLAB_R06_SOURCE="; then print -r -- PAYLOAD_UNEXPORTED; fi\nexit 7'
        real_case(root,prefix,zsh,False,'zsh',payload,7)
        real_case(root,prefix,zsh,True,'zsh',payload,7)
        real_case(root,prefix,zsh,False,'python3','import sys; print("PYTHON_OK"); sys.exit(9)',9)
if __name__=='__main__':main()
