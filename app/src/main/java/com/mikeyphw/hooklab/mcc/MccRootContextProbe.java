package com.mikeyphw.hooklab.mcc;

import java.util.regex.Pattern;

/** Same root subprocess as the real read-only scanner; emits fixed metadata only. */
public final class MccRootContextProbe {
    private MccRootContextProbe(){}
    private static final String PREFIX="MCC_R02_CONTEXT=";
    private static final Pattern CODE=Pattern.compile("U[RYX]_G[RYX]_K[ZNX]_I[SDX]_C[SDX]_S[RMUX]_A[DENX]{4}_B[DENX]{4}");
    public static String parse(String line){if(line==null||!line.startsWith(PREFIX))return null;String code=line.substring(PREFIX.length());return CODE.matcher(code).matches()?code:null;}
    public static String script(String verifiedDir,int callerPid){
        String dir=MccRefillCommand.checkedDataDir(verifiedDir);
        if(callerPid<=0)throw new IllegalArgumentException("caller PID required");
        String alias=dir.startsWith("/data/user/0/")?"/data/data/"+MccIds.PACKAGE:dir.startsWith("/data/data/")?"/data/user/0/"+MccIds.PACKAGE:dir;
        String aliasRoster=alias.equals(dir)?"XXXX":"$(mcc_roster '"+alias+"')";
        return "# MCC R02 read-only root context metadata; never change namespace, mode, or storage.\n"
            + "mcc_cmp() { if [ -z \"$1\" ] || [ -z \"$2\" ]; then printf X; elif [ \"$1\" = \"$2\" ]; then printf S; else printf D; fi; }\n"
            + "mcc_dir() {\n"
            + "  if [ -d \"$1\" ]; then if [ -r \"$1\" ] && [ -x \"$1\" ]; then printf D; else printf E; fi; return; fi\n"
            + "  err=$(LC_ALL=C /system/bin/stat \"$1\" 2>&1 >/dev/null); rc=$?\n"
            + "  if [ \"$rc\" -eq 0 ]; then printf X; else case \"$err\" in *'Permission denied'*|*'Operation not permitted'*) printf E ;; *'No such file or directory'*) printf N ;; *) printf X ;; esac; fi\n"
            + "}\n"
            + "mcc_roster() { mcc_dir \"$1\"; mcc_dir \"$1/databases\"; mcc_dir \"$1/files\"; mcc_dir \"$1/app_flutter\"; }\n"
            + "mcc_u=$(id -u 2>/dev/null || :); case \"$mcc_u\" in 0) mcc_u=R ;; '') mcc_u=X ;; *) mcc_u=Y ;; esac\n"
            + "mcc_g=$(id -g 2>/dev/null || :); case \"$mcc_g\" in 0) mcc_g=R ;; '') mcc_g=X ;; *) mcc_g=Y ;; esac\n"
            + "mcc_k=X; while read -r mcc_key mcc_cap mcc_rest; do case \"$mcc_key\" in CapEff:) case \"$mcc_cap\" in ''|*[!0-9a-fA-F]*) mcc_k=X ;; *[1-9a-fA-F]*) mcc_k=N ;; *) mcc_k=Z ;; esac; break ;; esac; done < /proc/$$/status 2>/dev/null\n"
            + "mcc_self=$(/system/bin/readlink /proc/$$/ns/mnt 2>/dev/null || :)\n"
            + "mcc_init=$(/system/bin/readlink /proc/1/ns/mnt 2>/dev/null || :)\n"
            + "mcc_caller=$(/system/bin/readlink /proc/"+callerPid+"/ns/mnt 2>/dev/null || :)\n"
            + "mcc_sec=$(cat /proc/$$/attr/current 2>/dev/null || :); case \"$mcc_sec\" in u:r:su:*|u:r:magisk:*) mcc_sec=R ;; u:r:kernel:*|u:r:ksu:*|u:r:apatch:*) mcc_sec=M ;; u:r:untrusted_app*:*) mcc_sec=U ;; *) mcc_sec=X ;; esac\n"
            + "printf 'MCC_R02_CONTEXT=U%s_G%s_K%s_I%s_C%s_S%s_A%s_B%s\\n' \"$mcc_u\" \"$mcc_g\" \"$mcc_k\" \"$(mcc_cmp \"$mcc_self\" \"$mcc_init\")\" \"$(mcc_cmp \"$mcc_self\" \"$mcc_caller\")\" \"$mcc_sec\" \"$(mcc_roster '"+dir+"')\" \""+aliasRoster+"\"\n";
    }
}
