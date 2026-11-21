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
import com.mikeyphw.hooklab.core.TweakDiagnostics;
import com.mikeyphw.hooklab.core.TweakCompatibility;
import com.mikeyphw.hooklab.core.TweakSpec;
import com.mikeyphw.hooklab.mcc.MccIds;
import com.mikeyphw.hooklab.mcc.MccControlActivity;
import android.content.Intent;
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
        page = state == null ? 0 : Math.max(0, Math.min(5, state.getInt("page")));
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
        int[] names = {R.string.home, R.string.tweaks, R.string.scope,
                R.string.diagnostics, R.string.settings, R.string.about};
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
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
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
            String compatibility = MccIds.PACKAGE.equals(spec.packageName)
                    ? getString(R.string.mcc_target_package, spec.packageName)
                    : getString(R.string.tweak_version, spec.packageName, spec.minVersion, spec.maxVersion);
            LinearLayout inner = card(entry.title, entry.description + "\n" + compatibility);
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
            if (MccIds.DIAMOND_MANUAL.equals(spec.id)) {
                button(inner, R.string.mcc_open_control, v ->
                    startActivity(new Intent(this, MccControlActivity.class)));
            }
            if (MccIds.PACKAGE.equals(spec.packageName)) {
                inner.addView(text(getString(R.string.mcc_version_warning), 14));
            }
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
        Set<String> listedPackages = new HashSet<>();
        for (TweakSpec spec : TweakCatalog.all()) {
            if (!listedPackages.add(spec.packageName)) continue;
            boolean present = current.contains(spec.packageName);
            LinearLayout inner = card(spec.packageName, getString(R.string.scope_approval));
            button(inner, present ? R.string.scope_remove : R.string.scope_add,
                    v -> { if (present) removeScope(s, spec.packageName);
                           else requestScope(s, spec.packageName); });
        }
    }
    /** Framework-truth diagnostics. Per-process events live only in Xposed logs. */
    private void diagnosticsPage() {
        heading(R.string.diagnostics);
        XposedService s = service();
        boolean connected = ready(s);
        card(getString(R.string.status), connection(s));
        if (!connected) {
            card(getString(R.string.diagnostics), getString(R.string.diagnostics_disconnected));
            return;
        }
        List<TweakEngine.Entry<HookContext>> entries = TweakCatalog.entries();
        if (entries.isEmpty()) {
            card(getString(R.string.diagnostics_empty), getString(R.string.diagnostics_inert));
            return;
        }
        Set<String> scope = scopedPackages(s);
        for (TweakEngine.Entry<HookContext> entry : entries) {
            String id = entry.spec.id;
            Boolean inScope = scope == null ? null : scope.contains(entry.spec.packageName);
            boolean enabled = false, locked = false, readable = true;
            try {
                SharedPreferences flags = s.getRemotePreferences(TweakKeys.GROUP);
                SharedPreferences safety = s.getRemotePreferences(TweakDiagnostics.GROUP);
                enabled = flags.getBoolean(TweakKeys.enabled(id), false);
                locked = safety.getBoolean(TweakDiagnostics.quarantineKey(id), false);
            } catch (RuntimeException ex) { readable = false; }
            if (!readable) {
                card(entry.title, getString(R.string.diagnostics_unavailable));
                continue;
            }
            // A package/version read in the UI would be subject to visibility limits;
            // null means unverified, never infer compatibility from historical logs.
            TweakCompatibility.State eligibility = TweakCompatibility.assess(entry.spec,
                    connected, inScope, enabled, locked, null);
            LinearLayout inner = card(entry.title,
                    getString(R.string.diagnostics_compatibility, eligibility.name())
                    + "\n" + getString(R.string.diagnostics_safety_state,
                          getString(locked ? R.string.diagnostics_locked : R.string.diagnostics_unlocked)));
            final boolean nextLock = !locked;
            button(inner, nextLock ? R.string.diagnostics_enable_lock : R.string.diagnostics_reset_quarantine,
                    v -> confirmSafetyLock(s, id, nextLock));
        }
        card(getString(R.string.diagnostics_note_title), getString(R.string.diagnostics_note));
    }

    private void confirmSafetyLock(XposedService s, String id, boolean locked) {
        if (!ready(s)) return;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(locked ? R.string.diagnostics_enable_lock : R.string.diagnostics_reset_quarantine)
                .setMessage(locked ? R.string.diagnostics_lock_confirm : R.string.diagnostics_reset_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    new Thread(() -> {
                        boolean success;
                        try {
                            // Only the module app's XposedService may WRITE remote preferences.
                            success = s == service() && s.getRemotePreferences(TweakDiagnostics.GROUP)
                                    .edit().putBoolean(TweakDiagnostics.quarantineKey(id), locked).commit();
                        } catch (RuntimeException ex) { success = false; }
                        final boolean result = success;
                        runOnUiThread(() -> {
                            toast(result ? R.string.diagnostics_reset_success : R.string.diagnostics_reset_failed);
                            render();
                        });
                    }, "hooklab-update-safety-lock").start();
                }).show();
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
    private void verboseLogging() {
        XposedService s = service();
        boolean available = ready(s);
        boolean current = false;
        if (available) {
            try {
                current = s.getRemotePreferences(TweakKeys.GROUP)
                        .getBoolean(TweakKeys.VERBOSE_LSPOSED_LOGS, false);
            } catch (RuntimeException ex) { available = false; }
        }
        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setText(R.string.verbose_lsposed_logs);
        sw.setChecked(current);
        sw.setEnabled(available);
        sw.setOnCheckedChangeListener((view, wanted) -> {
            sw.setEnabled(false);
            writeVerboseLogging(s, wanted);
        });
        body.addView(sw, new ViewGroup.LayoutParams(-1, -2));
        body.addView(text(getString(R.string.verbose_lsposed_description), 14));
        if (!available) body.addView(text(getString(R.string.settings_unavailable), 14));
    }
    private void writeVerboseLogging(XposedService expected, boolean value) {
        new Thread(() -> {
            boolean saved;
            try {
                saved = ready(expected) && expected == service() &&
                    expected.getRemotePreferences(TweakKeys.GROUP).edit()
                        .putBoolean(TweakKeys.VERBOSE_LSPOSED_LOGS, value).commit();
            } catch (RuntimeException error) { saved = false; }
            final boolean result = saved;
            runOnUiThread(() -> {
                toast(result ? R.string.verbose_lsposed_saved : R.string.tweak_failed);
                render();
            });
        }, "hooklab-verbose-preference").start();
    }
    private void settings() {
        heading(R.string.settings); body.addView(text(getString(R.string.appearance), 20));
        themeButton(R.string.system_theme, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        themeButton(R.string.light_theme, AppCompatDelegate.MODE_NIGHT_NO);
        themeButton(R.string.dark_theme, AppCompatDelegate.MODE_NIGHT_YES);
        body.addView(text(getString(R.string.verbose_lsposed_heading), 20));
        verboseLogging();
    }
    private void render() {
        if (body == null) return;
        body.removeAllViews();
        switch (page) {
            case 1: tweaks(); break;
            case 2: scopePage(); break;
            case 3: diagnosticsPage(); break;
            case 4: settings(); break;
            case 5: heading(R.string.about); card(getString(R.string.app_name), getString(R.string.about_detail)); break;
            default: home();
        }
    }
}
