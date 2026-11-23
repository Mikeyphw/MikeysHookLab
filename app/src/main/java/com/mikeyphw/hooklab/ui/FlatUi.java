package com.mikeyphw.hooklab.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.TextView;
import com.google.android.material.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.navigation.NavigationBarView;

/** DS-01 shared flat surfaces; Material You themes resolve through attrs. */
public final class FlatUi {
    private FlatUi() { }
    private static int dp(View v, int n) {
        return Math.round(v.getResources().getDisplayMetrics().density * n);
    }
    public static int canvas(Context context) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (!context.getTheme().resolveAttribute(R.attr.colorSurface, value, true))
            throw new IllegalStateException("Missing colorSurface theme attribute");
        if (value.resourceId != 0) return androidx.core.content.ContextCompat.getColor(context, value.resourceId);
        return value.data;
    }
    public static void toolbar(MaterialToolbar bar) {
        bar.setBackgroundColor(MaterialColors.getColor(bar, R.attr.colorSurface));
        bar.setTitleTextColor(MaterialColors.getColor(bar, R.attr.colorOnSurface));
        bar.setElevation(0f);
    }
    public static void section(MaterialCardView section) {
        section.setRadius(dp(section, 12));
        section.setCardElevation(0f);
        section.setUseCompatPadding(false);
        section.setStrokeWidth(0);
        section.setCardBackgroundColor(MaterialColors.getColor(section, R.attr.colorSurfaceContainer));
    }
    /** Mark every native section title as a TalkBack-recognized heading. */
    public static void sectionHeading(TextView title) {
        if (title == null) throw new IllegalArgumentException("Missing section title");
        title.setAccessibilityHeading(true);
    }
    public static void button(MaterialButton action) {
        action.setElevation(0f);
        action.setStrokeWidth(0);
        action.setCornerRadius(dp(action, 12));
        action.setMinHeight(dp(action, Ui08QualityPolicy.MIN_TOUCH_DP));
    }
    public static void chip(Chip chip) {
        chip.setChipStrokeWidth(0f);
        chip.setElevation(0f);
        chip.setEnsureMinTouchTargetSize(true);
        chip.setChipMinHeight(dp(chip, Ui08QualityPolicy.MIN_TOUCH_DP));
    }
    public static void navigation(NavigationBarView nav) {
        nav.setBackgroundColor(MaterialColors.getColor(nav, R.attr.colorSurface));
        nav.setElevation(0f);
        int on = MaterialColors.getColor(nav, androidx.appcompat.R.attr.colorPrimary);
        int off = MaterialColors.getColor(nav, R.attr.colorOnSurfaceVariant);
        ColorStateList tint = new ColorStateList(
            new int[][] { new int[]{android.R.attr.state_checked}, new int[]{} },
            new int[]{on, off});
        nav.setItemIconTintList(tint);
        nav.setItemTextColor(tint);
        nav.setItemActiveIndicatorColor(ColorStateList.valueOf(
            MaterialColors.getColor(nav, R.attr.colorSurfaceVariant)));
    }
}
