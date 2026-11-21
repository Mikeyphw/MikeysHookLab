package com.mikeyphw.hooklab.ui;

import android.os.Bundle;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textview.MaterialTextView;
import com.mikeyphw.hooklab.R;
import com.mikeyphw.hooklab.core.TweakCatalog;

public final class MainActivity extends AppCompatActivity {
    private LinearLayout body;
    private SharedPreferences prefs;
    private int page;
    @Override public void onCreate(Bundle state) {
        prefs = getSharedPreferences("appearance", MODE_PRIVATE);
        getDelegate().setLocalNightMode(prefs.getInt("night", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
        super.onCreate(state);
        page = state == null ? 0 : Math.max(0, Math.min(3, state.getInt("page")));
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurface));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, w) -> {
            Insets i = w.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(i.left, i.top, i.right, i.bottom); return w;
        });
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(16), dp(20), dp(24));
        scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout nav = new LinearLayout(this); nav.setOrientation(LinearLayout.VERTICAL);
        int[] names = {R.string.home, R.string.tweaks, R.string.settings, R.string.about};
        // Two rows avoid clipping on compact screens and at large font sizes.
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(this);
            for (int col = 0; col < 2; col++) {
                final int target = row * 2 + col;
                MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                b.setText(names[target]); b.setMinHeight(dp(48)); b.setOnClickListener(v -> { page = target; render(); scroll.scrollTo(0,0); });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,-2,1); lp.setMargins(dp(4),0,dp(4),0); line.addView(b,lp);
            }
            nav.addView(line);
        }
        root.addView(nav); setContentView(root); ViewCompat.requestApplyInsets(root); render();
    }
    @Override protected void onSaveInstanceState(Bundle out) { out.putInt("page",page); super.onSaveInstanceState(out); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private MaterialTextView text(CharSequence value, int sp) {
        MaterialTextView t = new MaterialTextView(this); t.setText(value); t.setTextSize(sp);
        t.setTextColor(MaterialColors.getColor(t, com.google.android.material.R.attr.colorOnSurface));
        return t;
    }
    private void heading(int id) { TextView t=text(getString(id),28); t.setAccessibilityHeading(true); body.addView(t); }
    private void card(CharSequence title, CharSequence description) {
        MaterialCardView c = new MaterialCardView(this); c.setRadius(dp(20));
        LinearLayout inner = new LinearLayout(this); inner.setOrientation(LinearLayout.VERTICAL); inner.setPadding(dp(18),dp(18),dp(18),dp(18));
        inner.addView(text(title,20)); TextView detail = text(description,16); detail.setPadding(0,dp(8),0,0); inner.addView(detail);
        c.addView(inner); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(16); body.addView(c,lp);
    }
    private void themeButton(int label, int mode) {
        MaterialButton b=new MaterialButton(this); b.setText(label); b.setMinHeight(dp(48));
        b.setEnabled(prefs.getInt("night",AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)!=mode);
        b.setOnClickListener(v -> { prefs.edit().putInt("night", mode).apply(); getDelegate().setLocalNightMode(mode); render(); });
        body.addView(b,new ViewGroup.LayoutParams(-1,-2));
    }
    private void render() {
        body.removeAllViews();
        switch(page) {
            case 1: heading(R.string.tweaks); card(getString(R.string.registered_count,TweakCatalog.all().size()),getString(R.string.catalog_empty)); break;
            case 2: heading(R.string.settings); body.addView(text(getString(R.string.appearance),20));
                themeButton(R.string.system_theme,AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                themeButton(R.string.light_theme,AppCompatDelegate.MODE_NIGHT_NO);
                themeButton(R.string.dark_theme,AppCompatDelegate.MODE_NIGHT_YES); break;
            case 3: heading(R.string.about); card(getString(R.string.app_name),getString(R.string.about_detail)); break;
            default: heading(R.string.app_name); body.addView(text(getString(R.string.tagline),18));
                card(getString(R.string.status),getString(R.string.status_detail));
                card(getString(R.string.tweaks),getString(R.string.registered_count,TweakCatalog.all().size()));
        }
    }
}
