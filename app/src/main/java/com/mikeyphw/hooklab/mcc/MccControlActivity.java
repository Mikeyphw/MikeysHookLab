package com.mikeyphw.hooklab.mcc;

import android.os.Bundle;
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
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(28),dp(20),dp(24));
        TextView title=new TextView(this);
        title.setText("MCC · Manual diamond control");
        title.setTextSize(23f);
        root.addView(title);
        TextView description=new TextView(this);
        description.setPadding(0,dp(12),0,dp(18));
        description.setText("Only a manual request can change MCC's local diamond balance to 9. " +
            "The operation requires HookLab root authorization and exactly one recognized " +
            "account balance file. It may restart MCC after a verified change. " +
            "No automatic refill, Premium simulation or ad hooks. " +
            "Changes are local and may be overwritten by synchronization.");
        root.addView(description);
        inspect=new MaterialButton(this);
        inspect.setText("Check local balance storage (read-only)");
        root.addView(inspect,new LinearLayout.LayoutParams(-1,-2));
        setNine=new MaterialButton(this);
        setNine.setText("Set local diamonds to 9");
        root.addView(setNine,new LinearLayout.LayoutParams(-1,-2));
        status=new TextView(this);
        status.setPadding(0,dp(16),0,0);
        status.setText("No operation started. The ♦ 9 button only opens this screen.");
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));
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
