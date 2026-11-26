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
import com.google.android.material.appbar.MaterialToolbar;

/** Visible, manual control. Opening this Activity never executes root or redirects MCC. */
public final class MccControlActivity extends AppCompatActivity {
    private MaterialButton inspect;
    private MaterialButton setNine;
    private MaterialButton rootInspect;
    private TextView status;
    private boolean running;
    private boolean started;

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
        content.setPadding(dp(gutter),dp(8),dp(gutter),dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1,-2));
        FrameLayout centered = new com.mikeyphw.hooklab.ui.AdaptiveContentFrame(this,720);
        centered.addView(scroll, new FrameLayout.LayoutParams(
            -1, -1, android.view.Gravity.CENTER_HORIZONTAL));
        MaterialToolbar bar=new MaterialToolbar(this);
        FlatUi.toolbar(bar);
        bar.setTitle(R.string.r03_manual_control);
        bar.setNavigationIcon(R.drawable.ic_nav_back);
        bar.setNavigationContentDescription(R.string.ui_back);
        bar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        root.addView(bar,new LinearLayout.LayoutParams(-1,-2));
        root.addView(centered, new LinearLayout.LayoutParams(-1,0,1));
        TextView title=new TextView(this);
        title.setText(R.string.ui07_mcc_heading);
        title.setTextSize(com.mikeyphw.hooklab.ui.DesignTokens.HERO_SP);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setTextColor(MaterialColors.getColor(title, com.google.android.material.R.attr.colorOnSurface));
        title.setAccessibilityHeading(true);
        content.addView(title);
        TextView description=new TextView(this);
        description.setPadding(0,dp(12),0,dp(18));
        description.setText(R.string.ui07_mcc_description);
        description.setTextColor(MaterialColors.getColor(description,
            com.google.android.material.R.attr.colorOnSurfaceVariant));
        content.addView(description);
        inspect=new MaterialButton(this);
        inspect.setText(R.string.ui07_mcc_inspect);
        FlatUi.button(inspect);
        content.addView(inspect,new LinearLayout.LayoutParams(-1,-2));
        rootInspect=new MaterialButton(this);
        rootInspect.setText(R.string.root_platform_inspect);
        FlatUi.button(rootInspect);
        content.addView(rootInspect,new LinearLayout.LayoutParams(-1,-2));
        setNine=new MaterialButton(this);
        setNine.setText(R.string.ui07_mcc_write);
        FlatUi.button(setNine);
        content.addView(setNine,new LinearLayout.LayoutParams(-1,-2));
        status=new TextView(this);
        status.setPadding(0,dp(16),0,0);
        status.setText(R.string.ui07_mcc_idle);
        status.setTextColor(MaterialColors.getColor(status,
            com.google.android.material.R.attr.colorOnSurface));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        com.google.android.material.card.MaterialCardView statePanel=new com.google.android.material.card.MaterialCardView(this);
        FlatUi.section(statePanel);LinearLayout stateContent=new LinearLayout(this);stateContent.setOrientation(LinearLayout.VERTICAL);stateContent.setPadding(dp(16),dp(16),dp(16),dp(16));
        TextView stateHeading=new TextView(this);stateHeading.setText(R.string.r03_manual_state);stateHeading.setTextSize(17);FlatUi.sectionHeading(stateHeading);stateContent.addView(stateHeading);
        stateContent.addView(status,new LinearLayout.LayoutParams(-1,-2));statePanel.addView(stateContent);content.addView(statePanel,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
        inspect.setOnClickListener(v -> operation(true));
        rootInspect.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.root_platform_inspect)
            .setMessage(R.string.root_platform_confirmation)
            .setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(R.string.root_platform_start,(dialog,which) -> inspectRootPlatform())
            .show());
        setNine.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.ui07_mcc_confirm_title)
            .setMessage(R.string.ui07_mcc_confirm_body)
            .setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton("Set to 9",(dialog,which) -> operation(false))
            .show());
    }

    /** Never initiate a root worker from Activity lifecycle methods. */
    private void operation(boolean readOnly) {
        // Capture only Application context, not this Activity across rotation.
        final android.content.Context appContext=getApplicationContext();
        MccManualOperation.Task work=readOnly
            ? () -> MccRefillWorker.inspect(appContext)
            : () -> MccRefillWorker.setNine(appContext);
        MccManualOperation.shared().start(readOnly,work);
    }
    private void inspectRootPlatform() {
        final android.content.Context appContext=getApplicationContext();
        MccManualOperation.shared().start(true,() -> com.mikeyphw.hooklab.root.RootPlatform.inspect(appContext));
    }
    private final MccManualOperation.Listener operationObserver = snapshot ->
        runOnUiThread(() -> {
            if(!started||isFinishing()||isDestroyed()||inspect==null||rootInspect==null||setNine==null||status==null)return;
            boolean active=snapshot.stage==MccManualOperation.Stage.RUNNING;
            running=active;
            inspect.setEnabled(!active);rootInspect.setEnabled(!active);setNine.setEnabled(!active);
            if(active){
                status.setText(snapshot.inspectionOnly?R.string.ui07_mcc_inspecting:R.string.ui07_mcc_requesting);
            }else if(snapshot.stage==MccManualOperation.Stage.COMPLETE){
                status.setText(getString(R.string.ui07_mcc_result,snapshot.code));
            }else status.setText(R.string.ui07_mcc_idle);
        });
    @Override protected void onStart(){
        super.onStart();
        started=true;
        MccManualOperation.shared().addListener(operationObserver);
    }
    @Override protected void onStop(){
        started=false;
        MccManualOperation.shared().removeListener(operationObserver);
        super.onStop();
    }
}
