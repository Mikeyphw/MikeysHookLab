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
    public static int railWidthDp(float fontScale){return fontScale>=2f?144:fontScale>=1.6f?112:RAIL_WIDTH_DP;}
    public static int contentInsetDp(int screenWidthDp) {
        return useRail(screenWidthDp) ? 32 : screenWidthDp <= 350 ? 12 : 20;
    }
    /** Stack labelled actions before they compete with large text or a compact window. */
    public static boolean stackActions(int widthDp,float fontScale) {
        return widthDp < 360 || fontScale >= 1.6f;
    }
    public static int contentWidthDp(int screenWidthDp) {
        if (!useRail(screenWidthDp)) return Math.max(0, screenWidthDp);
        return Math.min(MAX_CONTENT_WIDTH_DP,
                Math.max(0, screenWidthDp - RAIL_WIDTH_DP));
    }
}
