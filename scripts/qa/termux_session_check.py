#!/usr/bin/env python3
"""Execute the production Java argv. Local Linux fixtures relocate only Termux's prefix.
The login fixture exercises the actual two-stage shell handoff and compatibility
probe; it does NOT pretend to load the Android termux-exec linker or certify a device.
"""
import argparse, errno, os, pathlib, pty, select, shutil, subprocess, tempfile, time
ROOT=pathlib.Path(__file__).resolve().parents[2]
POLICY=ROOT/'app/src/main/java/com/mikeyphw/hooklab/termux/TermuxCommandPolicy.java'
PREFIX='/data/data/com.termux/files/usr'

def require(value,reason):
    if not value:raise RuntimeError(reason)

def compile_policy(root):
    helper=root/'Args.java'
    helper.write_text('import com.mikeyphw.hooklab.termux.TermuxCommandPolicy; public class Args { public static void main(String[] args) { for(String s:TermuxCommandPolicy.arguments(args[0],args[1])) { System.out.print(s);System.out.print((char)0); } }}')
    subprocess.run(['javac','-encoding','UTF-8','-d',str(root),str(POLICY),str(helper)],check=True,timeout=30)

def args(root,prefix,source,shell):
    raw=subprocess.check_output(['java','-Dfile.encoding=UTF-8','-cp',str(root),'Args',source,shell],timeout=10)
    values=raw.decode().split('\0');require(values[-1]=='' and len(values)==10,'invalid production argv');values.pop()
    require(values[3]==source and values[4]==shell and values[2]=='hooklab','payload argv drift')
    for i in (1,5,6,7,8):values[i]=values[i].replace(PREFIX,str(prefix))
    return ['/bin/sh',*values]

def executable(path,text):
    path.write_text(text);path.chmod(0o700)

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

def real_case(root,prefix,custom,shell,source,status):
    home=root/('home-'+shell+('-custom' if custom else ''));home.mkdir();dot=home/'dot' if custom else home;dot.mkdir(exist_ok=True)
    # Isolate only synthetic user's global RC paths; never edit production user RCs.
    (dot/'.zshenv').write_text('unsetopt GLOBAL_RCS\nprint -r -- "ENV_NOART=$NOART" >> "$HOME/startup"\n')
    (dot/'.zprofile').write_text('print -r -- "PROFILE_NOART=$NOART" >> "$HOME/startup"\n')
    (dot/'.zshrc').write_text('print -r -- "RC_NOART=$NOART" >> "$HOME/startup"\n[[ $NOART == 1 ]] || print -r -- ART_MUST_NOT_APPEAR\nfunction fixture_fn() { print -r -- FN_OK; }\nalias fixture_alias="print -r -- ALIAS_OK"\nPROMPT="HL_READY> "\n')
    (dot/'.zlogin').write_text('print -r -- "LOGIN_NOART=$NOART" >> "$HOME/startup"\n')
    env={'HOME':str(home),'PATH':str(prefix/'bin')+':/usr/bin:/bin','TERM':'dumb','LANG':'C.UTF-8','TMPDIR':str(prefix/'tmp')}
    if custom:env['ZDOTDIR']=str(dot)
    argv=args(root,prefix,source,shell)
    pid,fd=pty.fork()
    if pid==0:os.execve('/bin/sh',argv,env)
    try:
        first=read_until(fd,b'HL_READY> ',time.monotonic()+20)
        require(f'[HookLab] Command exit: {status}'.encode() in first,'exit status missing')
        require(b'ART_MUST_NOT_APPEAR' not in first,'startup art leaked')
        require((home/'startup').read_text().splitlines()==['ENV_NOART=1','PROFILE_NOART=1','RC_NOART=1','LOGIN_NOART=1'],'startup not exactly once in normal order')
        require((home/'login').read_text().splitlines()==['LOGIN_ENTRY'],'login entry missing or repeated')
        require(b'LOGIN_ENV_READY' in first,'command did not inherit initialized environment')
        if shell=='zsh':require(b'ALIAS_OK' in first and b'FN_OK' in first and b'PAYLOAD_UNEXPORTED' in first,'user configuration or payload isolation lost')
        require(not list((prefix/'tmp').iterdir()),'startup shim not removed')
        # After sent command exits, continue in the SAME shell; login must remain initialized.
        os.write(fd,b'[[ $HOOKLAB_FIXTURE_LOGIN_READY == 1 && $NOART == 1 && -z ${HOOKLAB_R06_SOURCE-} && -z ${HOOKLAB_R06_SHIM-} ]] && print -r -- CONTINUED_OK; exit 0\n')
        read_until(fd,b'\r\nCONTINUED_OK\r\n',time.monotonic()+10)
        _,rc=os.waitpid(pid,0);pid=None;require(os.waitstatus_to_exitcode(rc)==0,'continuation failed')
    finally:
        os.close(fd)
        if pid is not None:
            try:os.kill(pid,9);os.waitpid(pid,0)
            except ProcessLookupError:pass
    print('TERMUX_LOGIN_REAL_ZSH_PTY_PASS',shell,'custom_zdotdir='+str(custom),'exit='+str(status),flush=True)

def main():
    a=argparse.ArgumentParser();a.add_argument('--require-zsh',action='store_true');a.add_argument('--bootstrap-only',action='store_true');o=a.parse_args()
    with tempfile.TemporaryDirectory(prefix='hooklab-session-fixture-') as td:
        root=pathlib.Path(td);compile_policy(root);prefix=root/'usr';(prefix/'bin').mkdir(parents=True);(prefix/'tmp').mkdir()
        for name in ('sh','env','mktemp','rm','rmdir'):
            exe=shutil.which(name);require(exe,'fixture executable missing '+name);(prefix/'bin'/name).symlink_to(exe)
        login=prefix/'bin/login'
        executable(login,'''#!/bin/sh
[ "$NOART" = 1 ] || exit 86
printf '%s\n' LOGIN_ENTRY > "$HOME/login"
export HOOKLAB_FIXTURE_LOGIN_READY=1
exec /bin/sh "$@"
''')
        sentinel="printf '%s\\n' 'quote, \\\" and 💡'\nexit 7\n";home=root/'home';home.mkdir()
        mock=prefix/'bin/zsh'
        executable(mock,'''#!/bin/sh
[ "$NOART" = 1 ] && [ "$1" = -il ] && [ "$HOOKLAB_FIXTURE_LOGIN_READY" = 1 ] && [ "$HOOKLAB_R06_SOURCE" = "$EXPECTED" ] && [ "$HOOKLAB_R06_SHELL" = zsh ] && [ -f "$ZDOTDIR/.zshenv" ] && [ -f "$ZDOTDIR/.zprofile" ] && [ -f "$ZDOTDIR/.zshrc" ] && [ -f "$ZDOTDIR/.zlogin" ] || exit 87
case "$(stat -c %a "$ZDOTDIR")" in 700|2700) ;; *) exit 88 ;; esac
! grep -F -- 'quote,' "$ZDOTDIR/.zshenv" "$ZDOTDIR/.zprofile" "$ZDOTDIR/.zshrc" "$ZDOTDIR/.zlogin" >/dev/null || exit 89
''')
        env={'HOME':str(home),'PATH':'/usr/bin:/bin','EXPECTED':sentinel,'TERM':'dumb','TMPDIR':str(prefix/'tmp')}
        cmd=args(root,prefix,sentinel,'zsh');subprocess.run(cmd,env=env,check=True,timeout=10)
        require((home/'login').read_text().splitlines()==['LOGIN_ENTRY'],'login skipped')
        require(not list((prefix/'tmp').iterdir()),'fixture bootstrap leaked files')
        original=login.read_text();login.unlink();missing=subprocess.run(cmd,env=env,capture_output=True,timeout=10);require(missing.returncode==127 and b'Termux login is required' in missing.stderr,'missing login not refused');executable(login,original)
        executable(login,'#!/bin/sh\nexit 73\n');failed=subprocess.run(cmd,env=env,capture_output=True,timeout=10);require(failed.returncode==73 and not list((prefix/'tmp').iterdir()),'failed login status or cleanup lost');executable(login,original)
        mock.unlink();missing=subprocess.run(cmd,env=env,capture_output=True,timeout=10);require(missing.returncode==127 and b'zsh is required' in missing.stderr,'missing Zsh not refused')
        print('TERMUX_LOGIN_BOOTSTRAP_PASS exact argv, private shims, login inheritance, refusal and cleanup',flush=True)
        if o.bootstrap_only:return
        zsh=shutil.which('zsh');require(zsh or not o.require_zsh,'real Zsh required but unavailable')
        if not zsh:print('TERMUX_LOGIN_REAL_ZSH_PTY_NOT_RUN');return
        mock.symlink_to(zsh)
        # Model Termux login's noninteractive termux-exec probe and its -l exec.
        executable(login,'''#!/bin/sh
[ "$NOART" = 1 ] || exit 86
printf '%s\n' LOGIN_ENTRY > "$HOME/login"
export HOOKLAB_FIXTURE_LOGIN_READY=1
"$TMPDIR/../bin/zsh" -c true || exit 90
exec "$TMPDIR/../bin/zsh" -l "$@"
''')
        for name in ('bash','python3'):
            exe=shutil.which(name);require(exe,'fixture interpreter missing '+name);(prefix/'bin'/name).symlink_to(exe)
        payload='fixture_alias\nfixture_fn\nif ! env | grep -q "^HOOKLAB_R06_"; then print -r -- PAYLOAD_UNEXPORTED; fi\n[[ $HOOKLAB_FIXTURE_LOGIN_READY == 1 ]] && print -r -- LOGIN_ENV_READY\nexit 7'
        real_case(root,prefix,False,'zsh',payload,7);real_case(root,prefix,True,'zsh',payload,7)
        real_case(root,prefix,False,'python3','import sys, os; print("LOGIN_ENV_READY" if os.environ.get("HOOKLAB_FIXTURE_LOGIN_READY")=="1" else "LOGIN_MISSING"); sys.exit(9)',9)
if __name__=='__main__':main()
