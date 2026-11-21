package com.mikeyphw.hooklab.ui;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import androidx.activity.OnBackPressedCallback;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
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
    private static final int HOME = 0, APPS = 1, DIAGNOSTICS = 2;
    private static final int SETTINGS = 3, ABOUT = 4, SCOPE = 5, APP_DETAIL = 6;
    private static final int MENU_SETTINGS = 100, MENU_SCOPE = 101, MENU_ABOUT = 102;
    private static final int NAV_HOME = 201, NAV_APPS = 202, NAV_DIAGNOSTICS = 203;
    private static final int THEME_AMOLED = 100;
    private int page;
    private String selectedPackage;
    private MaterialToolbar toolbar;
    private BottomNavigationView navigation;
    private final Runnable frameworkObserver = this::render;

    @Override public void onCreate(Bundle state) {
        appearance = getSharedPreferences("appearance", MODE_PRIVATE);
        int night = appearance.getInt("night", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (night == THEME_AMOLED) {
            setTheme(R.style.Theme_HookLab_Amoled);
            night = AppCompatDelegate.MODE_NIGHT_YES;
        }
        getDelegate().setLocalNightMode(night);
        super.onCreate(state);
        page = state == null ? HOME : Math.max(HOME, Math.min(APP_DETAIL, state.getInt("page")));
        selectedPackage = state == null ? null : state.getString("selectedPackage");
        if (page == APP_DETAIL && selectedEntry() == null) page = APPS;
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurface));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, w) -> {
            Insets i = w.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return w;
        });

        toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.app_name);
        toolbar.getMenu().add(Menu.NONE, MENU_SETTINGS, 0, R.string.settings)
                .setIcon(R.drawable.ic_nav_settings).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.getMenu().add(Menu.NONE, MENU_SCOPE, 1, R.string.scope);
        toolbar.getMenu().add(Menu.NONE, MENU_ABOUT, 2, R.string.about);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_SETTINGS) goTo(SETTINGS);
            else if (item.getItemId() == MENU_SCOPE) goTo(SCOPE);
            else if (item.getItemId() == MENU_ABOUT) goTo(ABOUT);
            else return false;
            return true;
        });
        toolbar.setNavigationOnClickListener(v -> navigateBack());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(56)));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(12), dp(16), dp(32));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        navigation = new BottomNavigationView(this);
        navigation.getMenu().add(Menu.NONE, NAV_HOME, 0, R.string.home).setIcon(R.drawable.ic_nav_home);
        navigation.getMenu().add(Menu.NONE, NAV_APPS, 1, R.string.apps).setIcon(R.drawable.ic_nav_apps);
        navigation.getMenu().add(Menu.NONE, NAV_DIAGNOSTICS, 2, R.string.diagnostics)
                .setIcon(R.drawable.ic_nav_diagnostics);
        navigation.setLabelVisibilityMode(
                com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED);
        navigation.setOnItemSelectedListener(item -> {
            int target = item.getItemId() == NAV_HOME ? HOME
                    : item.getItemId() == NAV_APPS ? APPS
                    : item.getItemId() == NAV_DIAGNOSTICS ? DIAGNOSTICS : -1;
            if (target < 0) return false;
            goTo(target);
            return true;
        });
        root.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        ViewCompat.requestApplyInsets(root);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (page == HOME) finish();
                else navigateBack();
            }
        });
        render();
    }
    private void navigateBack() {
        goTo(page == APP_DETAIL ? APPS : HOME);
    }
    private void goTo(int target) {
        if (target == page) return;
        page = target;
        render();
        scroll.scrollTo(0, 0);
    }
    @Override protected void onStart() {
        super.onStart(); ((HookLabApp) getApplication()).addObserver(frameworkObserver); render();
    }
    @Override protected void onStop() {
        ((HookLabApp) getApplication()).removeObserver(frameworkObserver); super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("page", page); out.putString("selectedPackage", selectedPackage);
        super.onSaveInstanceState(out);
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
    private TweakEngine.Entry<HookContext> selectedEntry() {
        for (TweakEngine.Entry<HookContext> entry : TweakCatalog.entries()) {
            if (entry.spec.packageName.equals(selectedPackage)) return entry;
        }
        return null;
    }

    /** Framework-observable configuration only; never assert that a hook ran. */
    private TweakCompatibility.State eligibility(TweakSpec spec, XposedService s) {
        Boolean inScope = null;
        Set<String> scope = scopedPackages(s);
        if (scope != null) inScope = scope.contains(spec.packageName);
        boolean enabled = false, locked = false;
        if (ready(s)) {
            try {
                enabled = s.getRemotePreferences(TweakKeys.GROUP)
                        .getBoolean(TweakKeys.enabled(spec.id), false);
                locked = s.getRemotePreferences(TweakDiagnostics.GROUP)
                        .getBoolean(TweakDiagnostics.quarantineKey(spec.id), false);
            } catch (RuntimeException error) {
                return TweakCompatibility.State.SCOPE_UNKNOWN;
            }
        }
        Long version = null;
        // MCC's broad installer range does NOT establish storage-format compatibility.
        if (!MccIds.PACKAGE.equals(spec.packageName)) {
            try {
                version = getPackageManager().getPackageInfo(spec.packageName, 0).getLongVersionCode();
            } catch (PackageManager.NameNotFoundException | SecurityException ignored) {
                // Host not installed or not visible. Never assume a version.
            }
        }
        return TweakCompatibility.assess(spec, ready(s), inScope, enabled, locked, version);
    }
    private String statusText(TweakCompatibility.State state) {
        switch (state) {
            case FRAMEWORK_UNAVAILABLE: return getString(R.string.ui_framework_unavailable);
            case SCOPE_UNKNOWN: return getString(R.string.ui_scope_unknown);
            case OUT_OF_SCOPE: return getString(R.string.ui_scope_needed);
            case QUARANTINED: return getString(R.string.ui_safety_locked);
            case DISABLED: return getString(R.string.ui_disabled);
            case VERSION_UNVERIFIED: return getString(R.string.ui_version_unverified);
            case VERSION_UNSUPPORTED: return getString(R.string.ui_version_unsupported);
            case ELIGIBLE: return getString(R.string.ui_eligible);
            default: return getString(R.string.ui_unknown);
        }
    }
    private LinearLayout horizontal() {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return line;
    }
    private ImageView hostIcon(String pkg) {
        ImageView icon = new ImageView(this);
        try {
            icon.setImageDrawable(getPackageManager().getApplicationIcon(pkg));
        } catch (PackageManager.NameNotFoundException | SecurityException ex) {
            icon.setImageResource(R.drawable.ic_hooklab);
        }
        icon.setContentDescription(null); // Adjacent app name is announced instead.
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(44), dp(44));
        size.rightMargin = dp(14);
        icon.setLayoutParams(size);
        return icon;
    }
    private void showApp(TweakEngine.Entry<HookContext> entry) {
        selectedPackage = entry.spec.packageName;
        goTo(APP_DETAIL);
    }
    private void appCard(TweakEngine.Entry<HookContext> entry) {
        LinearLayout inner = card(entry.title, statusText(eligibility(entry.spec, service())));
        LinearLayout row = horizontal();
        row.addView(hostIcon(entry.spec.packageName));
        MaterialTextView description = text(entry.description, 14);
        row.addView(description, new LinearLayout.LayoutParams(0, -2, 1));
        inner.addView(row);
        button(inner, R.string.ui_configure, v -> showApp(entry));
    }
    private void apps() {
        heading(R.string.apps);
        body.addView(text(getString(R.string.ui_apps_intro), 14));
        for (TweakEngine.Entry<HookContext> entry : TweakCatalog.entries()) appCard(entry);
    }
    private void appDetail() {
        TweakEngine.Entry<HookContext> entry = selectedEntry();
        if (entry == null) { apps(); return; }
        TweakSpec spec = entry.spec;
        XposedService s = service();
        heading(R.string.ui_app_configuration);
        LinearLayout overview = card(entry.title, entry.description);
        LinearLayout iconLine = horizontal();
        iconLine.addView(hostIcon(spec.packageName));
        iconLine.addView(text(spec.packageName, 14),
                new LinearLayout.LayoutParams(0, -2, 1));
        overview.addView(iconLine);
        overview.addView(text(getString(R.string.ui_current_status) + ": " +
                statusText(eligibility(spec, s)), 16));
        overview.addView(text(getString(R.string.ui_status_caution), 14));

        Set<String> scope = scopedPackages(s);
        boolean scoped = scope != null && scope.contains(spec.packageName);
        LinearLayout scopeCard = card(getString(R.string.scope),
                scope == null ? getString(R.string.ui_scope_unknown) :
                scoped ? getString(R.string.ui_scope_approved) : getString(R.string.ui_scope_needed));
        if (ready(s) && scope != null) {
            button(scopeCard, scoped ? R.string.scope_remove : R.string.scope_add,
                    v -> { if (scoped) removeScope(s, spec.packageName);
                           else requestScope(s, spec.packageName); });
        }

        LinearLayout tweakCard = card(getString(R.string.ui_tweak_setting), entry.description);
        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setText(R.string.ui_enable_tweak);
        boolean available = ready(s), checked = false;
        if (available) {
            try {
                checked = s.getRemotePreferences(TweakKeys.GROUP)
                        .getBoolean(TweakKeys.enabled(spec.id), false);
            } catch (RuntimeException error) { available = false; }
        }
        sw.setChecked(checked);
        sw.setEnabled(available);
        sw.setOnCheckedChangeListener((view, wanted) -> {
            sw.setEnabled(false);
            saveTweak(s, spec.id, wanted);
        });
        tweakCard.addView(sw, new ViewGroup.LayoutParams(-1, -2));
        if (!available) tweakCard.addView(text(getString(R.string.settings_unavailable), 14));
        if (MccIds.DIAMOND_MANUAL.equals(spec.id)) {
            button(tweakCard, R.string.mcc_open_control, v ->
                    startActivity(new Intent(this, MccControlActivity.class)));
            tweakCard.addView(text(getString(R.string.mcc_version_warning), 14));
        } else {
            tweakCard.addView(text(getString(R.string.tweak_version,
                    spec.packageName, spec.minVersion, spec.maxVersion), 14));
        }
        LinearLayout next = card(getString(R.string.ui_next_steps),
                getString(R.string.ui_restart_target));
        next.addView(text(getString(R.string.ui_host_log_note), 14));
    }

    private void home() {
        heading(R.string.app_name);
        body.addView(text(getString(R.string.tagline), 18));
        XposedService s = service();
        LinearLayout status = card(getString(R.string.status), connection(s));
        if (!ready(s)) {
            status.addView(text(getString(R.string.ui_framework_help), 14));
        }
        int scoped = 0;
        Set<String> approved = scopedPackages(s);
        if (approved != null) {
            for (TweakSpec spec : TweakCatalog.all()) {
                if (approved.contains(spec.packageName)) scoped++;
            }
        }
        card(getString(R.string.ui_overview), getString(R.string.ui_overview_summary,
                TweakCatalog.all().size(), approved == null ? "?" :
                        Integer.toString(scoped)));
        body.addView(text(getString(R.string.ui_status_caution), 14));
        body.addView(text(getString(R.string.ui_integrations_heading), 20));
        for (TweakEngine.Entry<HookContext> entry : TweakCatalog.entries()) appCard(entry);
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
    // Tweaks and scope are configured together on each application's detail page.
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
                    getString(R.string.diagnostics_compatibility, statusText(eligibility))
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

    private void themeChip(ChipGroup group, int label, int mode, int current) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setCheckable(true);
        chip.setChecked(mode == current);
        chip.setOnClickListener(v -> {
            if (mode == appearance.getInt("night", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
                return;
            appearance.edit().putInt("night", mode).apply();
            recreate();
        });
        group.addView(chip);
    }
    private void verboseLogging() {
        LinearLayout panel = card(getString(R.string.verbose_lsposed_heading),
                getString(R.string.ui_logging_summary));
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
        panel.addView(sw, new ViewGroup.LayoutParams(-1, -2));
        panel.addView(text(getString(R.string.verbose_lsposed_description), 14));
        if (!available) panel.addView(text(getString(R.string.settings_unavailable), 14));
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
        heading(R.string.settings);
        LinearLayout appearanceCard = card(getString(R.string.appearance),
                getString(R.string.ui_theme_description));
        ChipGroup themes = new ChipGroup(this);
        themes.setSingleSelection(true);
        int current = appearance.getInt("night", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        themeChip(themes, R.string.system_theme, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, current);
        themeChip(themes, R.string.light_theme, AppCompatDelegate.MODE_NIGHT_NO, current);
        themeChip(themes, R.string.dark_theme, AppCompatDelegate.MODE_NIGHT_YES, current);
        themeChip(themes, R.string.ui_amoled_theme, THEME_AMOLED, current);
        appearanceCard.addView(themes);
        verboseLogging();
    }
    private void render() {
        if (body == null) return;
        int navId = page == APP_DETAIL || page == APPS ? NAV_APPS
                : page == DIAGNOSTICS ? NAV_DIAGNOSTICS : NAV_HOME;
        navigation.getMenu().findItem(navId).setChecked(true);
        boolean secondary = page >= SETTINGS;
        toolbar.setNavigationIcon(secondary ? R.drawable.ic_nav_back : 0);
        if (secondary) toolbar.setNavigationContentDescription(R.string.ui_back);
        switch (page) {
            case APPS: toolbar.setTitle(R.string.apps); break;
            case DIAGNOSTICS: toolbar.setTitle(R.string.diagnostics); break;
            case SETTINGS: toolbar.setTitle(R.string.settings); break;
            case ABOUT: toolbar.setTitle(R.string.about); break;
            case SCOPE: toolbar.setTitle(R.string.scope); break;
            case APP_DETAIL:
                TweakEngine.Entry<HookContext> entry = selectedEntry();
                toolbar.setTitle(entry == null ? getString(R.string.apps) : entry.title);
                break;
            default: toolbar.setTitle(R.string.app_name);
        }
        body.removeAllViews();
        switch (page) {
            case APPS: apps(); break;
            case DIAGNOSTICS: diagnosticsPage(); break;
            case SETTINGS: settings(); break;
            case ABOUT:
                heading(R.string.about);
                card(getString(R.string.app_name), getString(R.string.about_detail));
                break;
            case SCOPE: scopePage(); break;
            case APP_DETAIL: appDetail(); break;
            default: home();
        }
    }

}
