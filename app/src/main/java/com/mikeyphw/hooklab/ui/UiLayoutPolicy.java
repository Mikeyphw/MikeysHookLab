package com.mikeyphw.hooklab.ui;

/** Pure layout rules: deterministic and testable without Android UI runtime. */
public final class UiLayoutPolicy {
    private UiLayoutPolicy() { }
    public static final int RAIL_BREAKPOINT_DP = 760;
    public static final int RAIL_WIDTH_DP = 88;
    public static final int MAX_CONTENT_WIDTH_DP = 900;

    public static boolean useRail(int screenWidthDp) {
        return screenWidthDp >= RAIL_BREAKPOINT_DP;
    }
    public static int contentInsetDp(int screenWidthDp) {
        return useRail(screenWidthDp) ? 32 : screenWidthDp <= 350 ? 12 : 20;
    }
    public static int contentWidthDp(int screenWidthDp) {
        if (!useRail(screenWidthDp)) return Math.max(0, screenWidthDp);
        return Math.min(MAX_CONTENT_WIDTH_DP,
                Math.max(0, screenWidthDp - RAIL_WIDTH_DP));
    }
}
