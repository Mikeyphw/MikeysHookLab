package com.mikeyphw.hooklab.mcc;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.mikeyphw.hooklab.HookLabApp;
import com.mikeyphw.hooklab.core.TweakKeys;
import io.github.libxposed.service.XposedService;

/**
 * User-visible module-owned control, not an exported silent root executor.
 * Automatic requests require Android 14+ verified caller and framework opt-in.
 */
public final class MccControlActivity extends AppCompatActivity {
    private MaterialButton perform;
    private TextView status;
    private boolean running;
    private boolean autoStarted;

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    private boolean callerVerified(Intent intent) {
        if (intent == null || !MccRefillCommand.ACTION_FROM_TARGET.equals(intent.getAction())
                || Build.VERSION.SDK_INT < 34) return false;
        try {
            return getLaunchedFromUid() == getPackageManager().getPackageUid(MccIds.PACKAGE, 0)
                    && MccIds.PACKAGE.equals(getLaunchedFromPackage());
        } catch (PackageManager.NameNotFoundException | SecurityException ex) {
            return false;
        }
    }

    private boolean autoEnabledInFramework() {
        XposedService s = ((HookLabApp)getApplication()).currentService();
        if (s == null) return false;
        try {
            return s.getApiVersion() >= 102 && s.getScope().contains(MccIds.PACKAGE)
                && s.getRemotePreferences(TweakKeys.GROUP)
                    .getBoolean(TweakKeys.enabled(MccIds.DIAMOND_AUTO), false);
        } catch (RuntimeException ex) { return false; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout panel = new LinearLayout(this);
        panel.setPadding(dp(20), dp(28), dp(20), dp(20));
        panel.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(this);
        heading.setText("MCC — Diamond control");
        heading.setTextSize(24f);
        panel.addView(heading);
        TextView detail = new TextView(this);
        detail.setPadding(0, dp(16), 0, dp(16));
        detail.setText("Local balance only. Root force-stops Meu Cronograma Capilar, verifies the single account-local balance file, writes ASCII 9 and tries to reopen the app. No purchase or backend authorization is implied. Last manually tested: " + MccIds.LAST_MANUALLY_TESTED + ".");
        panel.addView(detail);
        perform = new MaterialButton(this);
        perform.setText("Set diamonds to 9 and reopen app");
        panel.addView(perform, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        status = new TextView(this);
        status.setPadding(0, dp(16), 0, 0);
        status.setText("Manual action requires root permission. Automatic refill is configured separately in HookLab Tweaks.");
        panel.addView(status);
        setContentView(panel);
        perform.setOnClickListener(v -> startRefill(false));
        // Do not automatically rerun after an orientation change.
        if (state == null && callerVerified(getIntent()) &&
                getIntent().getBooleanExtra(MccRefillCommand.EXTRA_AUTO, false) &&
                autoEnabledInFramework()) {
            autoStarted = true;
            startRefill(true);
        }
    }

    private void startRefill(boolean onlyWhenZero) {
        if (running) return;
        running = true;
        perform.setEnabled(false);
        status.setText("Root operation requested; awaiting verified result…");
        new Thread(() -> {
            String result = MccRefillWorker.execute(getApplicationContext(), onlyWhenZero);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                running = false;
                perform.setEnabled(true);
                status.setText("Result: " + result);
                if (autoStarted && result.startsWith("WRITE_9_VERIFIED_LAUNCH_DISPATCHED")) finish();
            });
        }, "hooklab-mcc-refill").start();
    }
}
