package com.mikeyphw.hooklab.ui;

import android.content.Context;
import android.widget.FrameLayout;
import android.view.View;
import android.view.Gravity;

/** One centered content child, capped against the measured viewport, not screen dimensions. */
public final class AdaptiveContentFrame extends FrameLayout {
    private final int maximumPx;
    public AdaptiveContentFrame(Context context,int maximumDp) {
        super(context);
        maximumPx=Math.round(maximumDp*context.getResources().getDisplayMetrics().density);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        if(getChildCount()!=1) throw new IllegalStateException("Expected one content child");
        View child=getChildAt(0);
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)child.getLayoutParams();
        int available=MeasureSpec.getMode(widthSpec)==MeasureSpec.UNSPECIFIED?maximumPx:
            Math.max(0,MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight());
        lp.width=Math.min(maximumPx,available);lp.gravity=Gravity.CENTER_HORIZONTAL;
        super.onMeasure(widthSpec,heightSpec);
    }
}
