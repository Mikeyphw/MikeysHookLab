package com.mikeyphw.hooklab.probetarget;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.view.WindowInsets;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** No personal data or privileged operations; only this fixed method can be probed. */
public final class ProbeTargetActivity extends Activity {
    public String sample() { return "BASE"; }
    private void applySystemInsets(ScrollView root) {
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view,insets)->{
                Insets safe=insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                view.setPadding(safe.left,safe.top,safe.right,safe.bottom);
                return WindowInsets.CONSUMED;
            });
            root.requestApplyInsets();
        }
        // API 29 retains the platform's default decor-fitting window.
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL);
        TextView result=new TextView(this); result.setText(R.string.not_run);
        Button run=new Button(this);run.setText(R.string.run_marker);run.setOnClickListener(v->{try{result.setText((String)getClass().getDeclaredMethod("sample").invoke(this));}catch(ReflectiveOperationException failure){result.setText(R.string.refused);}});
        int spacing=Math.round(16*getResources().getDisplayMetrics().density);
        column.setPadding(spacing,spacing,spacing,spacing);
        column.addView(run);column.addView(result);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(column);
        setContentView(scroll);
        applySystemInsets(scroll);
    }
}
