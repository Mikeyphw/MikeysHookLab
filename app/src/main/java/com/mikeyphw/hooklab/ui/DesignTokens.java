package com.mikeyphw.hooklab.ui;

/** Fixed native geometry derived from the frozen UX reference. Android text scaling
 * and measured insets take priority over fixed prototype dimensions. */
public final class DesignTokens {
    private DesignTokens() { }
    public static final int SPACE=8, PANEL_PADDING=16, PANEL_RADIUS=16;
    public static final int HERO_SP=34, SECTION_SP=17, ROW_SP=15, DETAIL_SP=14;
    public static final int TOOLBAR_DP=64, ROW_DP=64, COMPACT_ROW_DP=52, BUTTON_RADIUS=24;
    public static int columns(int availableDp,float fontScale){
        return availableDp>=300 && fontScale<=1.3f?3:1;
    }
}
