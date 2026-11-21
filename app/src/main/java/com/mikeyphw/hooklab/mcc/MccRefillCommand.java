package com.mikeyphw.hooklab.mcc;

/**
 * Adapted from MCC-VIP-Lab P09/P10. The command is run only by the HookLab
 * application UID after a user action or verified and explicitly opted-in auto request.
 * No commands are executed in the target app process.
 */
public final class MccRefillCommand {
    private MccRefillCommand() { }
    public static final String ACTION_FROM_TARGET = "com.mikeyphw.hooklab.mcc.action.OPEN_CONTROL";
    public static final String EXTRA_AUTO = "com.mikeyphw.hooklab.mcc.extra.AUTO";

    public static String script(boolean onlyWhenZero) {
        return "set -eu\n"
            + "[ \"$(id -u)\" = '0' ] || { echo MCC_REFILL_NOT_ROOT; exit 70; }\n"
            + "pkg='" + MccIds.PACKAGE + "'\n"
            + "base='/data/user/0/" + MccIds.PACKAGE + "/databases'\n"
            + "[ -d \"$base\" ] && [ ! -L \"$base\" ] || { echo MCC_REFILL_NO_DATABASE; exit 51; }\n"
            + "count=0\nf=''\n"
            + "for candidate in \"$base\"/*-DiamondBalance; do\n"
            + "  [ -f \"$candidate\" ] || continue\n"
            + "  [ ! -L \"$candidate\" ] || { echo MCC_REFILL_SYMLINK_REFUSED; exit 52; }\n"
            + "  name=${candidate##*/}\n"
            + "  prefix=${name%-DiamondBalance}\n"
            + "  [ ${#prefix} -ge 8 ] && [ ${#prefix} -le 128 ] || { echo MCC_REFILL_NAME_REFUSED; exit 56; }\n"
            + "  case \"$prefix\" in *[!A-Za-z0-9_-]*) echo MCC_REFILL_NAME_REFUSED; exit 56 ;; esac\n"
            + "  count=$((count + 1))\nf=$candidate\n"
            + "done\n"
            + "[ \"$count\" -eq 1 ] || { echo MCC_REFILL_ACCOUNT_COUNT_REFUSED; exit 53; }\n"
            + "bytes=$(wc -c < \"$f\")\n"
            + "[ \"$bytes\" -eq 1 ] || { echo MCC_REFILL_BAD_SIZE; exit 54; }\n"
            + "v=$(cat \"$f\")\n"
            + "case \"$v\" in [0-9]) : ;; *) echo MCC_REFILL_BAD_CONTENT; exit 55 ;; esac\n"
            + (onlyWhenZero ? "[ \"$v\" = '0' ] || { echo MCC_REFILL_AUTO_SKIP_NONZERO; exit 63; }\n" : "")
            + "am force-stop \"$pkg\"\n"
            + "printf '%s' '9' > \"$f\"\n"
            + "[ \"$(wc -c < \"$f\")\" -eq 1 ] && [ \"$(cat \"$f\")\" = '9' ] || { echo MCC_REFILL_VERIFY_FAILED; exit 59; }\n"
            + "printf 'MCC_REFILL_WRITE_VERIFIED\\n'\n";
    }
}
