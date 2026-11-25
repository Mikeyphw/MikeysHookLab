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
        bar.setMinimumHeight(dp(bar,DesignTokens.TOOLBAR_DP));
    }
    public static void section(MaterialCardView section) {
        section.setRadius(dp(section, DesignTokens.PANEL_RADIUS));
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
    /** Native compact navigation row: one action, no preference mutation. */
    public static void actionRow(View row,View.OnClickListener click){
        android.util.TypedValue value=new android.util.TypedValue();
        if(row.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground,value,true)&&value.resourceId!=0)
            row.setForeground(androidx.appcompat.content.res.AppCompatResources.getDrawable(row.getContext(),value.resourceId));
        row.setMinimumHeight(dp(row,Ui08QualityPolicy.MIN_TOUCH_DP));
        androidx.core.view.ViewCompat.setScreenReaderFocusable(row,true);
        row.setFocusable(true);row.setClickable(true);row.setOnClickListener(click);
    }
    public static void button(MaterialButton action) {
        action.setElevation(0f);
        action.setStrokeWidth(0);
        action.setCornerRadius(dp(action, DesignTokens.BUTTON_RADIUS));
        action.setMinHeight(dp(action, Ui08QualityPolicy.MIN_TOUCH_DP));
        action.setMinimumWidth(dp(action,Ui08QualityPolicy.MIN_TOUCH_DP));
        action.setSingleLine(false);
        action.setMaxLines(Integer.MAX_VALUE);
        action.setEllipsize(null);
    }
    public static void chip(Chip chip) {
        chip.setChipStrokeWidth(0f);
        chip.setElevation(0f);
        chip.setEnsureMinTouchTargetSize(true);
        chip.setChipMinHeight(dp(chip, Ui08QualityPolicy.MIN_TOUCH_DP));
        chip.setMinimumWidth(dp(chip,Ui08QualityPolicy.MIN_TOUCH_DP));
    }
    public static void adaptiveNavigationLabels(NavigationBarView nav) {
        if(nav.getResources().getConfiguration().fontScale<1.6f)return;
        wrapNavigationLabels(nav);
        nav.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->wrapNavigationLabels(nav));
    }
    private static void wrapNavigationLabels(View v){
        if(v instanceof TextView){TextView label=(TextView)v;
            if(label.getMaxLines()!=3){label.setSingleLine(false);label.setMaxLines(3);label.setEllipsize(null);}
        }
        if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)wrapNavigationLabels(g.getChildAt(i));}
    }
    public static void navigation(NavigationBarView nav) {
        nav.setBackgroundColor(MaterialColors.getColor(nav, R.attr.colorSurfaceContainer));
        nav.setElevation(0f);
        int on = MaterialColors.getColor(nav, androidx.appcompat.R.attr.colorPrimary);
        int off = MaterialColors.getColor(nav, R.attr.colorOnSurfaceVariant);
        ColorStateList tint = new ColorStateList(
            new int[][] { new int[]{android.R.attr.state_checked}, new int[]{} },
            new int[]{MaterialColors.getColor(nav, R.attr.colorOnSurface), off});
        nav.setItemIconTintList(new ColorStateList(
            new int[][] {new int[]{android.R.attr.state_checked}, new int[]{}},
            new int[]{MaterialColors.getColor(nav, R.attr.colorOnPrimary), off}));
        nav.setItemTextColor(tint);
        nav.setItemActiveIndicatorColor(ColorStateList.valueOf(
            on));
    }
}
