package com.mikeyphw.hooklab.termux;

/** TB01 command validation, extended for the native inline action. Never executes code. */
public final class TermuxCommandPolicy {
    public static final String HOME="/data/data/com.termux/files/home";
    public static final String PREFIX="/data/data/com.termux/files/usr/bin/";
    public static final String PERMISSION="com.termux.permission.RUN_COMMAND";
    public static final String ACTION_PREVIEW="com.mikeyphw.hooklab.termux.PREVIEW";
    public static final String ACTION_SETTINGS="com.mikeyphw.hooklab.termux.SETTINGS";
    public static final String EXTRA_SOURCE="com.mikeyphw.hooklab.termux.SOURCE";
    public static final String EXTRA_REQUEST_ID="com.mikeyphw.hooklab.termux.REQUEST_ID";
    public static final String EXTRA_REQUEST_TIME="com.mikeyphw.hooklab.termux.REQUEST_TIME";
    public static final String EXTRA_REQUEST_WALL="com.mikeyphw.hooklab.termux.REQUEST_WALL";
    public static final String HOST_CERT_SHA256="b24f4bfbb3cf293f938703b9d87027c1102cc36dc4fa206910e08927db40473c";
    public static final long REQUEST_TTL_MS=15000;
    public static final String EXECUTABLE=PREFIX+"sh";
    // Run the installed Termux login script before launching persistent Zsh.
    // The login script selects termux-exec and performs its own compatibility probe.
    // Only fixed code is written to private startup shims; payload stays a separate argument.
    private static final String BOOTSTRAP="""
        umask 077
        export NOART=1
        if [ ! -x /data/data/com.termux/files/usr/bin/zsh ]; then
          printf '%s\\n' '[HookLab] zsh is required; install it with pkg install zsh' >&2
          exit 127
        fi
        if [ ! -r /data/data/com.termux/files/usr/bin/login ]; then
          printf '%s\\n' '[HookLab] Termux login is required; repair termux-tools' >&2
          exit 127
        fi
        _hl_dir=$(/data/data/com.termux/files/usr/bin/mktemp -d "${TMPDIR:-/data/data/com.termux/files/usr/tmp}/hooklab.XXXXXXXX") || exit 1
        trap 'if [ -d "$_hl_dir" ]; then /data/data/com.termux/files/usr/bin/rm -f -- "$_hl_dir/.zshenv" "$_hl_dir/.zprofile" "$_hl_dir/.zshrc" "$_hl_dir/.zlogin"; /data/data/com.termux/files/usr/bin/rmdir -- "$_hl_dir"; fi' EXIT
        printf '%s\\n' "$3" > "$_hl_dir/.zshenv" || exit 1
        printf '%s\\n' "$4" > "$_hl_dir/.zprofile" || exit 1
        printf '%s\\n' "$5" > "$_hl_dir/.zshrc" || exit 1
        printf '%s\\n' "$6" > "$_hl_dir/.zlogin" || exit 1
        export HOOKLAB_R06_SOURCE="$1" HOOKLAB_R06_SHELL="$2"
        export HOOKLAB_R06_ORIG_SET="${ZDOTDIR+x}" HOOKLAB_R06_ORIG="${ZDOTDIR-}"
        export HOOKLAB_R06_SHIM="$_hl_dir" ZDOTDIR="$_hl_dir"
        /data/data/com.termux/files/usr/bin/sh /data/data/com.termux/files/usr/bin/login -c 'exec /data/data/com.termux/files/usr/bin/env ZDOTDIR="$HOOKLAB_R06_SHIM" /data/data/com.termux/files/usr/bin/zsh -il'
        """;
    private static final String ENV_INIT="""
        # Login's noninteractive termux-exec probe and intermediate shell must
        # never consume the payload, execute user RCs twice or remove the shim.
        [[ -o interactive ]] || return 0
        typeset -g _hl_r06_dir="$HOOKLAB_R06_SHIM" _hl_r06_code="$HOOKLAB_R06_SOURCE" _hl_r06_shell="$HOOKLAB_R06_SHELL"
        if [[ $HOOKLAB_R06_ORIG_SET == x ]]; then export ZDOTDIR="$HOOKLAB_R06_ORIG"; else unset ZDOTDIR; fi
        typeset +x _hl_r06_code _hl_r06_shell
        unset HOOKLAB_R06_SOURCE HOOKLAB_R06_SHELL HOOKLAB_R06_ORIG_SET HOOKLAB_R06_ORIG HOOKLAB_R06_SHIM
        export NOART=1
        if [[ -r ${ZDOTDIR-$HOME}/.zshenv ]]; then builtin source "${ZDOTDIR-$HOME}/.zshenv"; fi
        typeset -g _hl_r06_orig_set=${ZDOTDIR+x} _hl_r06_orig=${ZDOTDIR-}
        export ZDOTDIR="$_hl_r06_dir"
        """;
    private static final String PROFILE_INIT="""
        [[ -o interactive ]] || return 0
        if [[ $_hl_r06_orig_set == x ]]; then export ZDOTDIR="$_hl_r06_orig"; else unset ZDOTDIR; fi
        if [[ -r ${ZDOTDIR-$HOME}/.zprofile ]]; then builtin source "${ZDOTDIR-$HOME}/.zprofile"; fi
        typeset -g _hl_r06_orig_set=${ZDOTDIR+x} _hl_r06_orig=${ZDOTDIR-}
        export ZDOTDIR="$_hl_r06_dir"
        """;
    private static final String RC_INIT="""
        [[ -o interactive ]] || return 0
        if [[ $_hl_r06_orig_set == x ]]; then export ZDOTDIR="$_hl_r06_orig"; else unset ZDOTDIR; fi
        if [[ -r ${ZDOTDIR-$HOME}/.zshrc ]]; then builtin source "${ZDOTDIR-$HOME}/.zshrc"; fi
        typeset -g _hl_r06_orig_set=${ZDOTDIR+x} _hl_r06_orig=${ZDOTDIR-}
        export ZDOTDIR="$_hl_r06_dir"
        """;
    private static final String LOGIN_INIT="""
        [[ -o interactive ]] || return 0
        if [[ $_hl_r06_orig_set == x ]]; then export ZDOTDIR="$_hl_r06_orig"; else unset ZDOTDIR; fi
        /data/data/com.termux/files/usr/bin/rm -f -- "$_hl_r06_dir/.zshenv" "$_hl_r06_dir/.zprofile" "$_hl_r06_dir/.zshrc" "$_hl_r06_dir/.zlogin"
        /data/data/com.termux/files/usr/bin/rmdir -- "$_hl_r06_dir"
        unset _hl_r06_dir _hl_r06_orig_set _hl_r06_orig
        if [[ -r ${ZDOTDIR-$HOME}/.zlogin ]]; then builtin source "${ZDOTDIR-$HOME}/.zlogin"; fi
        function _hooklab_r06_run_once() {
          setopt localoptions noerrexit noerrreturn
          local _hl_r06_status
          if [[ $_hl_r06_shell == zsh ]]; then
            ( builtin eval "$_hl_r06_code" )
          else
            ( "/data/data/com.termux/files/usr/bin/$_hl_r06_shell" -c "$_hl_r06_code" )
          fi
          _hl_r06_status=$?
          unset _hl_r06_code _hl_r06_shell
          builtin printf '\\n[HookLab] Command exit: %d\\n' "$_hl_r06_status"
        }
        _hooklab_r06_run_once
        unfunction _hooklab_r06_run_once
        """;
    public static final int MAX_BYTES=com.mikeyphw.hooklab.sdk.PackPreviewContract.MAX_BYTES;
    private TermuxCommandPolicy() {}
    public static String checkedCommand(String text) {return com.mikeyphw.hooklab.sdk.PackPreviewContract.checkedCommand(text);}
    public static String checkedWorkingDirectory(String input) {
        if(input==null||input.trim().isEmpty()||input.trim().equals("~"))return HOME;
        String p=input.trim();if(p.startsWith("~/"))p=HOME+p.substring(1);
        if(!(p.equals(HOME)||p.startsWith(HOME+"/"))||p.length()>1024)throw new IllegalArgumentException("Working directory must be under Termux home");
        for(int i=0;i<p.length();i++)if(Character.isISOControl(p.charAt(i))||Character.getType(p.charAt(i))==Character.FORMAT)throw new IllegalArgumentException("Invalid working directory");
        if(!p.equals(HOME))for(String part:p.substring(HOME.length()+1).split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))throw new IllegalArgumentException("Invalid working directory segment");
        return p;
    }
    public static String checkedShell(String name) {
        if(!"zsh".equals(name)&&!"bash".equals(name)&&!"sh".equals(name)&&!"python3".equals(name))throw new IllegalArgumentException("Unsupported interpreter");
        return name;
    }
    public static String[] arguments(String source,String shell) {
        return new String[]{"-c",BOOTSTRAP,"hooklab",checkedCommand(source),checkedShell(shell),ENV_INIT,PROFILE_INIT,RC_INIT,LOGIN_INIT};
    }
    public static boolean freshWall(long sent,long now){return sent>0&&now>=sent&&now-sent<=REQUEST_TTL_MS;}
    public static boolean freshRequest(String id,long sent,long now) {
        return id!=null&&id.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                &&sent>=0&&now>=sent&&now-sent<=REQUEST_TTL_MS;
    }
}
