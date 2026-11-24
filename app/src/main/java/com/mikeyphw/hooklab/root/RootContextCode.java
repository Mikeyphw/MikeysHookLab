package com.mikeyphw.hooklab.root;

import java.util.regex.Pattern;

/** Fixed, metadata-only protocol. No paths, arguments, content, or arbitrary commands. */
public final class RootContextCode {
    private static final Pattern CONTEXT = Pattern.compile("ROOT_PLATFORM_U[RYX]_G[RYX]_K[NZX]_I[SDX]_C[SDX]_S[RMUX]");
    private static final Pattern FAILURE = Pattern.compile("ROOT_PLATFORM_(?:BUSY|RESTART_REQUIRED|NOT_ROOT|AUTH_OR_BIND_TIMEOUT|BIND_REFUSED|BINDER_DIED|NULL_BINDING|IPC_REFUSED|IPC_TIMEOUT|INTERRUPTED|RESULT_UNVERIFIED|MAIN_THREAD_REFUSED|START_FAILED)");
    private RootContextCode() {}
    static String id(int value) { return value == 0 ? "R" : value > 0 ? "Y" : "X"; }
    static String compare(String a, String b) {
        return a == null || b == null || !a.matches("mnt:\\[[0-9]+\\]") || !b.matches("mnt:\\[[0-9]+\\]") ? "X" : a.equals(b) ? "S" : "D";
    }
    static String capability(String s) {
        if (s == null || !s.matches("[0-9a-fA-F]{1,32}")) return "X";
        return s.matches("0+") ? "Z" : "N";
    }
    static String security(String s) {
        if (s == null) return "X";
        if (s.startsWith("u:r:su:") || s.startsWith("u:r:magisk:")) return "R";
        if (s.startsWith("u:r:kernel:") || s.startsWith("u:r:ksu:") || s.startsWith("u:r:apatch:")) return "M";
        if (s.startsWith("u:r:untrusted_app")) return "U";
        return "X";
    }
    public static String checked(String value) {
        return value != null && (CONTEXT.matcher(value).matches() || FAILURE.matcher(value).matches()) ? value : "ROOT_PLATFORM_RESULT_UNVERIFIED";
    }
}
