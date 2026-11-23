package com.mikeyphw.hooklab.mcc;

import android.os.Bundle;
import android.os.Build;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;
import com.mikeyphw.hooklab.R;
import android.view.View;
import android.widget.ScrollView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.color.MaterialColors;
import com.mikeyphw.hooklab.ui.FlatUi;
import com.mikeyphw.hooklab.ui.UiLayoutPolicy;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

/** Visible, manual control. Opening this Activity never executes root or redirects MCC. */
public final class MccControlActivity extends AppCompatActivity {
    private MaterialButton inspect;
    private MaterialButton setNine;
    private TextView status;
    private boolean running;

    private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density+.5f); }

    @Override public void onCreate(Bundle state) {
        SharedPreferences appearance = getSharedPreferences("appearance", MODE_PRIVATE);
        int night = appearance.getInt("night", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (night == 100) {
            setTheme(R.style.Theme_HookLab_Amoled);
            night = AppCompatDelegate.MODE_NIGHT_YES;
        }
        getDelegate().setLocalNightMode(night);
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 31 &&
                appearance.getInt("night", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) != 100 &&
                appearance.getBoolean("dynamicColors", true)) {
            try { DynamicColors.applyToActivityIfAvailable(this); }
            catch (RuntimeException ignored) { /* Preserve the bundled palette. */ }
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(FlatUi.canvas(this));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets i = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return insets;
        });
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int widthDp = getResources().getConfiguration().screenWidthDp;
        int gutter = UiLayoutPolicy.contentInsetDp(widthDp);
        content.setPadding(dp(gutter),dp(28),dp(gutter),dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1,-2));
        FrameLayout centered = new FrameLayout(this);
        centered.addView(scroll, new FrameLayout.LayoutParams(
            dp(Math.min(720, widthDp)), -1, android.view.Gravity.CENTER_HORIZONTAL));
        root.addView(centered, new LinearLayout.LayoutParams(-1,-1));
        TextView title=new TextView(this);
        title.setText("MCC · Manual diamond control");
        title.setTextSize(32f);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setTextColor(MaterialColors.getColor(title, com.google.android.material.R.attr.colorOnSurface));
        title.setAccessibilityHeading(true);
        content.addView(title);
        TextView description=new TextView(this);
        description.setPadding(0,dp(12),0,dp(18));
        description.setText("Only a manual request can change MCC's local diamond balance to 9. " +
            "The operation requires HookLab root authorization and exactly one recognized " +
            "account balance file. It may restart MCC after a verified change. " +
            "No automatic refill, Premium simulation or ad hooks. " +
            "Changes are local and may be overwritten by synchronization.");
        description.setTextColor(MaterialColors.getColor(description,
            com.google.android.material.R.attr.colorOnSurfaceVariant));
        content.addView(description);
        inspect=new MaterialButton(this);
        inspect.setText("Check local balance storage (read-only)");
        FlatUi.button(inspect);
        content.addView(inspect,new LinearLayout.LayoutParams(-1,-2));
        setNine=new MaterialButton(this);
        setNine.setText("Set local diamonds to 9");
        FlatUi.button(setNine);
        content.addView(setNine,new LinearLayout.LayoutParams(-1,-2));
        status=new TextView(this);
        status.setPadding(0,dp(16),0,0);
        status.setText("No operation started. The ♦ 9 button only opens this screen.");
        status.setTextColor(MaterialColors.getColor(status,
            com.google.android.material.R.attr.colorOnSurface));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(status,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
        inspect.setOnClickListener(v -> operation(true));
        setNine.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Modify MCC local balance?")
            .setMessage("This may stop and reopen Meu Cronograma Capilar. Only one " +
                "recognized account's existing balance file will be changed to 9. Continue?")
            .setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton("Set to 9",(dialog,which) -> operation(false))
            .show());
    }

    private void operation(boolean readOnly) {
        if (running) return;
        running=true;
        inspect.setEnabled(false);
        setNine.setEnabled(false);
        status.setText(readOnly ? "Inspecting MCC storage without changes…" :
            "Requesting HookLab root authorization and validating balance…");
        new Thread(() -> {
            String result=readOnly ? MccRefillWorker.inspect(getApplicationContext()) :
                MccRefillWorker.setNine(getApplicationContext());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                running=false;
                inspect.setEnabled(true);
                setNine.setEnabled(true);
                status.setText("Result: "+result+
                    "\nNo automatic retry. If no balance file is recognized, do not create one manually.");
            });
        },"hooklab-mcc-manual").start();
    }
}
