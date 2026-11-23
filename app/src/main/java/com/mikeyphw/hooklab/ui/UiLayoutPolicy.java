package com.mikeyphw.hooklab.ui;

/** Pure layout rules: deterministic and testable without Android UI runtime. */
public final class UiLayoutPolicy {
    private UiLayoutPolicy() { }
    public static final int RAIL_BREAKPOINT_DP = 840;
    public static final int RAIL_WIDTH_DP = 80;
    public static final int MAX_CONTENT_WIDTH_DP = 840;

    public static boolean useRail(int screenWidthDp) {
        return screenWidthDp >= RAIL_BREAKPOINT_DP;
    }
    public static int contentInsetDp(int screenWidthDp) {
        return screenWidthDp >= 600 ? 24 : 16;
    }
    public static int contentWidthDp(int screenWidthDp) {
        if (!useRail(screenWidthDp)) return Math.max(0, screenWidthDp);
        return Math.min(MAX_CONTENT_WIDTH_DP,
                Math.max(0, screenWidthDp - RAIL_WIDTH_DP));
    }
}
