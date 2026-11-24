package com.mikeyphw.hooklab.mcc;

/** Only manual, module-UID root commands; target cannot send paths or balance amounts. */
public final class MccRefillCommand {
    private MccRefillCommand() { }
    public static final String ACTION_FROM_TARGET = "com.mikeyphw.hooklab.mcc.action.OPEN_CONTROL";
    // Retained for binary/source compatibility of dormant old classes, never used for dispatch.
    public static final String EXTRA_AUTO = "com.mikeyphw.hooklab.mcc.extra.AUTO";

    public static String checkedDataDir(String path) {
        if (path == null || !path.matches(
                "(?:/data/data|/data/user/[0-9]{1,6})/br\\.art\\.code\\.meucronogramacapilar"))
            throw new IllegalArgumentException("MCC data directory was not verified");
        return path;
    }

    /**
     * probe=true is read-only and never force-stops. With probe=false, the command
     * validates the sole existing account-local file and its one ASCII digit BEFORE
     * stopping MCC; the write is limited to ASCII 9 and then verified separately.
     *
     * No recursive scan, file creation, arbitrary user input, sync-marker writes,
     * backend requests or synthetic rewarded-ad callbacks.
     */
    public static String script(String verifiedDir, boolean probe) {
        String data = checkedDataDir(verifiedDir);
        return "set -eu\n"
            + "[ \"$(id -u)\" = '0' ] || { echo MCC_ROOT_NOT_GRANTED; exit 70; }\n"
            + "pkg='" + MccIds.PACKAGE + "'\n"
            + "data='" + data + "'\n"
            + "case \"$data\" in /data/data/*) user=0 ;; "
            + "/data/user/*) user=${data#/data/user/}; user=${user%%/*} ;; esac\n"
            + "n=0\n"
            + "f=''\n"
            + "seen=0\n"
            + "primary_visible=0\n"
            + "alias_visible=0\n"
            + "in_alias=0\n"
            + "used_fallback=0\n"
            + "# Only direct package-owned directories; never scan recursively or accept caller paths.\n"
            + "scan_base() {\n"
            + "  root=$1\n"
            + "  for dir in \"$root/databases\" \"$root/files\" \"$root/app_flutter\"; do\n"
            + "    [ -e \"$dir\" ] || continue\n"
            + "    [ -d \"$dir\" ] && [ ! -L \"$dir\" ] || { echo MCC_STORAGE_UNSAFE; exit 52; }\n"
            + "    [ -r \"$dir\" ] && [ -x \"$dir\" ] || { echo MCC_STORAGE_INACCESSIBLE; exit 58; }\n"
            + "    seen=$((seen+1))\n"
            + "    if [ \"$in_alias\" -eq 0 ]; then primary_visible=$((primary_visible+1));\n"
            + "    else alias_visible=$((alias_visible+1)); fi\n"
            + "    for candidate in \"$dir\"/*-DiamondBalance; do\n"
            + "      [ -e \"$candidate\" ] || continue\n"
            + "      [ -f \"$candidate\" ] && [ ! -L \"$candidate\" ] || { echo MCC_BALANCE_UNSAFE; exit 52; }\n"
            + "      name=${candidate##*/}\n"
            + "      prefix=${name%-DiamondBalance}\n"
            + "      [ ${#prefix} -ge 8 ] && [ ${#prefix} -le 128 ] || { echo MCC_NAME_UNRECOGNIZED; exit 56; }\n"
            + "      case \"$prefix\" in *[!A-Za-z0-9_-]*) echo MCC_NAME_UNRECOGNIZED; exit 56 ;; esac\n"
            + "      n=$((n+1))\n"
            + "      f=$candidate\n"
            + "      [ \"$n\" -le 1 ] || { echo MCC_MULTIPLE_ACCOUNTS; exit 53; }\n"
            + "    done\n"
            + "  done\n"
            + "}\n"
            + "scan_base \"$data\"\n"
            + "# User 0 has two known platform representations. Only probe alternatives after\n"
            + "# an empty primary scan; never count the same file through both aliases.\n"
            + "if [ \"$n\" -eq 0 ] && [ \"$user\" = '0' ]; then\n"
            + "  in_alias=1\n"
            + "  for alternate in \"/data/user/0/$pkg\" \"/data/data/$pkg\"; do\n"
            + "    [ \"$alternate\" = \"$data\" ] && continue\n"
            + "    scan_base \"$alternate\"\n"
            + "    if [ \"$n\" -gt 0 ]; then used_fallback=1; break; fi\n"
            + "  done\n"
            + "fi\n"
            + "printf 'MCC_SCAN_VISIBLE_PRIMARY=%s\\n' \"$primary_visible\"\n"
            + "printf 'MCC_SCAN_VISIBLE_ALIAS=%s\\n' \"$alias_visible\"\n"
            + "[ \"$seen\" -gt 0 ] || { echo MCC_STORAGE_DIRECTORIES_UNAVAILABLE; exit 58; }\n"
            + "[ \"$n\" -eq 1 ] || { echo MCC_BALANCE_FILE_NOT_FOUND; exit 51; }\n"
            + "[ \"$(wc -c < \"$f\")\" -eq 1 ] || { echo MCC_BALANCE_FORMAT_UNKNOWN; exit 54; }\n"
            + "old=$(cat \"$f\")\n"
            + "case \"$old\" in [0-9]) : ;; *) echo MCC_BALANCE_FORMAT_UNKNOWN; exit 55 ;; esac\n"
            + "[ \"$used_fallback\" -eq 0 ] || printf 'MCC_STORAGE_FALLBACK_USED\\n'\n"
            + (probe
                ? "printf 'MCC_STORAGE_READY\\n'\n"
                : "[ \"$old\" != 9 ] || { echo MCC_BALANCE_ALREADY_NINE; exit 0; }\n"
                + "am force-stop --user \"$user\" \"$pkg\"\n"
                + "[ -f \"$f\" ] && [ ! -L \"$f\" ] && [ \"$(wc -c < \"$f\")\" -eq 1 ] "
                + "&& [ \"$(cat \"$f\")\" = \"$old\" ] || { echo MCC_BALANCE_CHANGED; exit 57; }\n"
                + "printf '%s' '9' > \"$f\"\n"
                + "[ \"$(wc -c < \"$f\")\" -eq 1 ] && [ \"$(cat \"$f\")\" = 9 ] "
                + "|| { echo MCC_BALANCE_WRITE_FAILED; exit 59; }\n"
                + "printf 'MCC_BALANCE_WRITE_VERIFIED\\n'\n");
    }
}
