package com.mikeyphw.hooklab.mcc;

/** Pure, bounded positioning for an in-Activity control, independent of Android. */
public final class MccDiamondPlacement {
    private MccDiamondPlacement() { }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
    /** Bounds are [left, top, right, bottom] in the root view coordinate system. */
    public static int[] position(int width, int height, int buttonWidth, int buttonHeight,
                                 int margin, int fallbackTop, int[] bounds) {
        int x = width - buttonWidth - margin;
        int y = fallbackTop;
        if (bounds != null && bounds.length == 4 &&
                bounds[2] > bounds[0] && bounds[3] > bounds[1]) {
            int before = bounds[0] - buttonWidth - margin;
            int after = bounds[2] + margin;
            x = before >= margin ? before : after;
            y = bounds[1] + (bounds[3] - bounds[1] - buttonHeight) / 2;
        }
        return new int[]{
            clamp(x, margin, Math.max(margin, width - buttonWidth - margin)),
            clamp(y, margin, Math.max(margin, height - buttonHeight - margin))
        };
    }
}
