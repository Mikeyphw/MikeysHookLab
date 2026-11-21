package com.mikeyphw.hooklab.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textview.MaterialTextView;
import com.mikeyphw.hooklab.HookLabApp;
import com.mikeyphw.hooklab.R;
import com.mikeyphw.hooklab.core.HookContext;
import com.mikeyphw.hooklab.core.TweakCatalog;
import com.mikeyphw.hooklab.core.TweakEngine;
import com.mikeyphw.hooklab.core.TweakKeys;
import com.mikeyphw.hooklab.core.TweakSpec;
import io.github.libxposed.service.XposedService;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Framework-truth UI: no fake activation or optimistic preference writes. */
public final class MainActivity extends AppCompatActivity {
    private LinearLayout body;
    private ScrollView scroll;
    private SharedPreferences appearance;
    private int page;
    private final Runnable frameworkObserver = this::render;

    @Override public void onCreate(Bundle state) {
        appearance = getSharedPreferences("appearance", MODE_PRIVATE);
        getDelegate().setLocalNightMode(appearance.getInt("night", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
        super.onCreate(state);
        page = state == null ? 0 : Math.max(0, Math.min(4, state.getInt("page")));
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurface));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, w) -> {
            Insets i = w.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(i.left, i.top, i.right, i.bottom); return w;
        });
        scroll = new ScrollView(this); scroll.setFillViewport(true);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(16), dp(20), dp(24));
        scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout nav = new LinearLayout(this); nav.setOrientation(LinearLayout.VERTICAL);
        int[] names = {R.string.home, R.string.tweaks, R.string.scope, R.string.settings, R.string.about};
        // Compact two-column navigation scales better on narrow portrait screens.
        for (int row = 0; row < 3; row++) {
            LinearLayout line = new LinearLayout(this);
            for (int col = 0; col < 2; col++) {
                final int target = row * 2 + col;
                if (target >= names.length) break;
                MaterialButton b = new MaterialButton(this, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
                b.setText(names[target]); b.setMinHeight(dp(48));
                b.setOnClickListener(v -> { page = target; render(); scroll.scrollTo(0, 0); });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, target == 4 ? 2 : 1);
                lp.setMargins(dp(4), 0, dp(4), 0); line.addView(b, lp);
            }
            nav.addView(line);
        }
        root.addView(nav); setContentView(root); ViewCompat.requestApplyInsets(root); render();
    }
    @Override protected void onStart() {
        super.onStart(); ((HookLabApp) getApplication()).addObserver(frameworkObserver); render();
    }
    @Override protected void onStop() {
        ((HookLabApp) getApplication()).removeObserver(frameworkObserver); super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("page", page); super.onSaveInstanceState(out);
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private XposedService service() { return ((HookLabApp) getApplication()).currentService(); }
    private boolean ready(XposedService s) {
        try { return s != null && s.getApiVersion() >= 102; }
        catch (RuntimeException ex) { return false; }
    }
    private MaterialTextView text(CharSequence value, int sp) {
        MaterialTextView t = new MaterialTextView(this); t.setText(value); t.setTextSize(sp);
        t.setTextColor(MaterialColors.getColor(t, com.google.android.material.R.attr.colorOnSurface));
        return t;
    }
    private void heading(int id) {
        TextView t = text(getString(id), 28); t.setAccessibilityHeading(true); body.addView(t);
    }
    private LinearLayout card(CharSequence title, CharSequence description) {
        MaterialCardView card = new MaterialCardView(this); card.setRadius(dp(20));
        LinearLayout inner = new LinearLayout(this); inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(18), dp(18), dp(18), dp(18));
        inner.addView(text(title, 20));
        TextView detail = text(description, 16); detail.setPadding(0, dp(8), 0, 0); inner.addView(detail);
        card.addView(inner);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16); body.addView(card, lp);
        return inner;
    }
    private void button(LinearLayout parent, int label, View.OnClickListener click) {
        MaterialButton b = new MaterialButton(this); b.setText(label); b.setMinHeight(dp(48));
        b.setOnClickListener(click); parent.addView(b, new ViewGroup.LayoutParams(-1, -2));
    }
    private void toast(int message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private String connection(XposedService s) {
        if (s == null) return getString(R.string.status_disconnected);
        try {
            int api = s.getApiVersion();
            return api < 102 ? getString(R.string.status_unsupported, api)
                    : getString(R.string.status_connected, s.getFrameworkName(), s.getFrameworkVersion(), api);
        } catch (RuntimeException ex) { return getString(R.string.status_disconnected); }
    }
    private Set<String> scopedPackages(XposedService s) {
        if (!ready(s)) return null;
        try { return new HashSet<>(s.getScope()); }
        catch (RuntimeException ex) { return null; }
    }
    private void home() {
        heading(R.string.app_name); body.addView(text(getString(R.string.tagline), 18));
        card(getString(R.string.status), connection(service()));
        card(getString(R.string.tweaks), getString(R.string.registered_count, TweakCatalog.all().size()));
        card(getString(R.string.scope), ready(service())
                ? getString(R.string.scope_approval) : getString(R.string.scope_unavailable));
    }
    private void saveTweak(XposedService s, String id, boolean enabled) {
        new Thread(() -> {
            boolean ok;
            try {
                ok = s == service() && s.getRemotePreferences(TweakKeys.GROUP)
                        .edit().putBoolean(TweakKeys.enabled(id), enabled).commit();
            } catch (RuntimeException ex) { ok = false; }
            final boolean success = ok;
            runOnUiThread(() -> { toast(success ? R.string.tweak_saved : R.string.tweak_failed); render(); });
        }, "hooklab-prefs-write").start();
    }
    private void tweaks() {
        heading(R.string.tweaks);
        List<TweakEngine.Entry<HookContext>> entries = TweakCatalog.entries();
        if (entries.isEmpty()) {
            card(getString(R.string.registered_count, 0), getString(R.string.catalog_empty)); return;
        }
        card(getString(R.string.registered_count, entries.size()), getString(R.string.catalog_note));
        XposedService s = service(); Set<String> scope = scopedPackages(s);
        for (TweakEngine.Entry<HookContext> entry : entries) {
            TweakSpec spec = entry.spec;
            LinearLayout inner = card(entry.title, entry.description + "\n" +
                    getString(R.string.tweak_version, spec.packageName, spec.minVersion, spec.maxVersion));
            MaterialSwitch sw = new MaterialSwitch(this); sw.setText(spec.id);
            boolean available = ready(s); boolean checked = false;
            if (available) {
                try { checked = s.getRemotePreferences(TweakKeys.GROUP)
                        .getBoolean(TweakKeys.enabled(spec.id), false); }
                catch (RuntimeException ex) { available = false; }
            }
            sw.setChecked(checked); sw.setEnabled(available);
            sw.setOnCheckedChangeListener((view, wanted) -> {
                sw.setEnabled(false); saveTweak(s, spec.id, wanted);
            });
            inner.addView(sw, new ViewGroup.LayoutParams(-1, -2));
            if (!available) inner.addView(text(getString(R.string.settings_unavailable), 14));
            if (scope == null || !scope.contains(spec.packageName))
                inner.addView(text(getString(R.string.scope_approval), 14));
        }
    }
    private void requestScope(XposedService s, String pkg) {
        if (!ready(s) || !TweakCatalog.knowsPackage(pkg)) return;
        try {
            s.requestScope(Collections.singletonList(pkg), new XposedService.OnScopeEventListener() {
                @Override public void onScopeRequestApproved(List<String> approved) {
                    runOnUiThread(() -> { toast(R.string.scope_added); render(); });
                }
                @Override public void onScopeRequestFailed(String failure) {
                    runOnUiThread(() -> toast(getString(R.string.scope_request_failed, failure)));
                }
            });
        } catch (RuntimeException ex) { toast(getString(R.string.scope_change_failed, ex.getClass().getSimpleName())); }
    }
    private void removeScope(XposedService s, String pkg) {
        if (!ready(s) || !TweakCatalog.knowsPackage(pkg)) return;
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.scope_remove)
                .setMessage(pkg).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    try {
                        s.removeScope(Collections.singletonList(pkg)); toast(R.string.scope_removed); render();
                    } catch (RuntimeException ex) {
                        toast(getString(R.string.scope_change_failed, ex.getClass().getSimpleName()));
                    }
                }).show();
    }
    private void scopePage() {
        heading(R.string.scope);
        XposedService s = service();
        if (!ready(s)) { card(getString(R.string.status), getString(R.string.scope_unavailable)); return; }
        Set<String> current = scopedPackages(s);
        if (current == null) { card(getString(R.string.scope), getString(R.string.scope_unavailable)); return; }
        if (current.isEmpty()) card(getString(R.string.scope), getString(R.string.scope_empty));
        for (String pkg : current) card(getString(R.string.scope_entry, pkg), getString(R.string.scope_approval));
        for (TweakSpec spec : TweakCatalog.all()) {
            boolean present = current.contains(spec.packageName);
            LinearLayout inner = card(spec.packageName, getString(R.string.scope_approval));
            button(inner, present ? R.string.scope_remove : R.string.scope_add,
                    v -> { if (present) removeScope(s, spec.packageName);
                           else requestScope(s, spec.packageName); });
        }
    }
    private void themeButton(int label, int mode) {
        MaterialButton b = new MaterialButton(this); b.setText(label); b.setMinHeight(dp(48));
        b.setEnabled(appearance.getInt("night", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) != mode);
        b.setOnClickListener(v -> {
            appearance.edit().putInt("night", mode).apply();
            getDelegate().setLocalNightMode(mode); render();
        });
        body.addView(b, new ViewGroup.LayoutParams(-1, -2));
    }
    private void settings() {
        heading(R.string.settings); body.addView(text(getString(R.string.appearance), 20));
        themeButton(R.string.system_theme, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        themeButton(R.string.light_theme, AppCompatDelegate.MODE_NIGHT_NO);
        themeButton(R.string.dark_theme, AppCompatDelegate.MODE_NIGHT_YES);
    }
    private void render() {
        if (body == null) return;
        body.removeAllViews();
        switch (page) {
            case 1: tweaks(); break;
            case 2: scopePage(); break;
            case 3: settings(); break;
            case 4: heading(R.string.about); card(getString(R.string.app_name), getString(R.string.about_detail)); break;
            default: home();
        }
    }
}
