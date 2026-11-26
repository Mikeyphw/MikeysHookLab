package com.mikeyphw.hooklab.probetarget;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** No personal data or privileged operations; only this fixed method can be probed. */
public final class ProbeTargetActivity extends Activity {
    public String sample() { return "BASE"; }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL);
        TextView result=new TextView(this); result.setText(R.string.not_run);
        Button run=new Button(this);run.setText(R.string.run_marker);run.setOnClickListener(v->{try{result.setText((String)getClass().getDeclaredMethod("sample").invoke(this));}catch(ReflectiveOperationException failure){result.setText(R.string.refused);}});
        column.addView(run);column.addView(result);setContentView(column);
    }
}
