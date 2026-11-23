package com.mikeyphw.hooklab.ui;

import android.content.SharedPreferences;
import com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline;
import android.widget.FrameLayout;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigationrail.NavigationRailView;
import android.content.pm.PackageManager;
import android.os.Build;
import com.google.android.material.color.DynamicColors;
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
import com.mikeyphw.hooklab.diagnostics.CaptureSession;
import com.mikeyphw.hooklab.ui.HomeDashboardModel;
import com.mikeyphw.hooklab.core.HookContext;
import com.mikeyphw.hooklab.core.TweakCatalog;
import com.mikeyphw.hooklab.core.HookGroupCatalog;
import com.mikeyphw.hooklab.core.HookDetailRoute;
import com.mikeyphw.hooklab.core.TweakEngine;
import com.mikeyphw.hooklab.core.TweakCompatibility;
import com.mikeyphw.hooklab.core.TweakSpec;
import com.mikeyphw.hooklab.mcc.MccIds;
import com.mikeyphw.hooklab.mcc.MccControlActivity;
import android.content.Intent;
import io.github.libxposed.service.XposedService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Framework-truth UI: no fake activation or optimistic preference writes. */
public final class MainActivity extends AppCompatActivity {
    private LinearLayout body;
    private ScrollView scroll;
    private SharedPreferences appearance;
    private static final int HOME = 0, APPS = 1, DIAGNOSTICS = 2;
    private static final int SETTINGS = 3, ABOUT = 4, SCOPE = 5, APP_DETAIL = 6, GROUP_DETAIL = 7, CAPTURE = 8;
    private static final int MENU_SETTINGS = 100, MENU_SCOPE = 101, MENU_ABOUT = 102;
    private static final int NAV_HOME = 201, NAV_APPS = 202, NAV_DIAGNOSTICS = 203;
    private static final int THEME_AMOLED = 100;
    private int page;
    private static final String ACTION_OPEN_HOOK = "com.mikeyphw.hooklab.action.OPEN_HOOK";
    private String selectedHookId;
    private String selectedGroupId;
    private int detailReturnPage = APPS;
    private static final String UI04_VIEW_KEY = "catalog.view";
    private static final String UI04_QUERY_KEY = "catalog.search";
    private static final String UI04_EXPANDED_GROUP_KEY = "catalog.expanded.group";
    private static final String UI04_EXPANDED_APP_KEY = "catalog.expanded.app";
    private static final int UI04_GROUPS = 0, UI04_BY_APP = 1;
    private int catalogView = UI04_GROUPS;
    private String catalogQuery = "";
    private String expandedGroupId;
    private String expandedAppPackage;
    private LinearLayout catalogResults;

    private LinearLayout cardTo(LinearLayout parent, CharSequence title, CharSequence description) {
        MaterialCardView surface = new MaterialCardView(this);
        FlatUi.section(surface);
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(18), dp(18), dp(18), dp(18));
        inner.addView(text(title, 20));
        TextView detail = text(description, 14);
        detail.setPadding(0, dp(8), 0, 0);
        inner.addView(detail);
        surface.addView(inner);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        parent.addView(surface, params);
        return inner;
    }
    private String groupName(HookGroupCatalog.Group group) {
        switch (group.id) {
            case "group.obtainx": return getString(R.string.ui_group_obtainx);
            case "group.chatgpt_termux": return getString(R.string.ui_group_chatgpt);
            case "group.mcc": return getString(R.string.ui_group_mcc);
            case "group.wordmaster": return getString(R.string.ui_group_wordmaster);
            default: return group.title;
        }
    }
    private TweakEngine.Entry<HookContext> entryForId(String id) {
        if (id == null) return null;
        for (TweakEngine.Entry<HookContext> entry : TweakCatalog.entries())
            if (id.equals(entry.spec.id)) return entry;
        return null;
    }
    private void catalogCard(LinearLayout parent, TweakEngine.Entry<HookContext> entry) {
        LinearLayout inner = cardTo(parent, entry.title,
                statusText(eligibility(entry.spec, service())));
        LinearLayout row = horizontal();
        row.addView(hostIcon(entry.spec.packageName));
        row.addView(text(entry.description, 14), new LinearLayout.LayoutParams(0, -2, 1));
        inner.addView(row);
        button(inner, R.string.ui_configure, v -> showApp(entry));
    }
    private Chip catalogChip(int label, int mode) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setCheckable(true);
        FlatUi.chip(chip);
        chip.setChecked(catalogView == mode);
        chip.setOnClickListener(v -> {
            if (catalogView == mode) return;
            catalogView = mode;
            appearance.edit().putInt(UI04_VIEW_KEY, mode).apply();
            render(); // UI-only state: never call stateStore or touch any scope/tweak preference.
        });
        return chip;
    }
    private void renderCatalogResults() {
        if (catalogResults == null) return;
        catalogResults.removeAllViews();
        int visible = 0;
        if (catalogView == UI04_GROUPS) {
            for (HookGroupCatalog.Group group : HookGroupCatalog.groups(TweakCatalog.all())) {
                String name = groupName(group);
                boolean groupMatch = HookGroupCatalog.matches(catalogQuery, name, group.description, group.id);
                List<TweakEngine.Entry<HookContext>> matches = new java.util.ArrayList<>();
                for (TweakSpec spec : group.members) {
                    TweakEngine.Entry<HookContext> entry = entryForId(spec.id);
                    if (entry == null) continue; // Only the live registered catalog owns UI entries.
                    if (groupMatch || HookGroupCatalog.matches(catalogQuery,
                            entry.title, entry.description, spec.packageName, spec.id))
                        matches.add(entry);
                }
                if (matches.isEmpty()) continue;
                visible++;
                java.util.Set<String> matchingPackages = new java.util.HashSet<>();
                for (TweakEngine.Entry<HookContext> item : matches)
                    matchingPackages.add(item.spec.packageName);
                LinearLayout card = cardTo(catalogResults, name,
                        getString(R.string.ui_group_members,
                                matches.size(), matchingPackages.size()));
                card.addView(text(getString(R.string.ui_group_no_toggle), 12));
                button(card, R.string.ui05_group_details, v -> showGroup(group));
                boolean searching = !catalogQuery.trim().isEmpty();
                boolean expanded = searching || group.id.equals(expandedGroupId);
                if (!searching) button(card, expanded ? R.string.ui_catalog_collapse : R.string.ui_catalog_expand,
                        v -> {
                            expandedGroupId = group.id.equals(expandedGroupId) ? null : group.id;
                            appearance.edit().putString(UI04_EXPANDED_GROUP_KEY, expandedGroupId).apply();
                            renderCatalogResults();
                        });
                if (expanded) for (TweakEngine.Entry<HookContext> entry : matches)
                    catalogCard(card, entry);
            }
        } else {
            java.util.Map<String,List<TweakSpec>> byPackage =
                    new java.util.TreeMap<>(HookGroupCatalog.byPackage(TweakCatalog.all()));
            for (java.util.Map.Entry<String,List<TweakSpec>> group : byPackage.entrySet()) {
                List<TweakEngine.Entry<HookContext>> matches = new java.util.ArrayList<>();
                String pkg = group.getKey();
                boolean pkgMatch = HookGroupCatalog.matches(catalogQuery, pkg);
                TweakEngine.Entry<HookContext> first = null;
                for (TweakSpec spec : group.getValue()) {
                    TweakEngine.Entry<HookContext> entry = entryForId(spec.id);
                    if (entry == null) continue;
                    if (first == null) first = entry;
                    if (pkgMatch || HookGroupCatalog.matches(catalogQuery,
                            entry.title, entry.description, entry.spec.id))
                        matches.add(entry);
                }
                if (matches.isEmpty() || first == null) continue;
                visible++;
                String title = first.title;
                int delimiter = title.indexOf(" · ");
                String appName = delimiter > 0 ? title.substring(0, delimiter) : pkg;
                LinearLayout card = cardTo(catalogResults, appName,
                        pkg + " · " + getString(R.string.ui_app_members, matches.size()));
                boolean searching = !catalogQuery.trim().isEmpty();
                boolean expanded = searching || pkg.equals(expandedAppPackage);
                if (!searching) button(card, expanded ? R.string.ui_catalog_collapse : R.string.ui_catalog_expand,
                        v -> {
                            expandedAppPackage = pkg.equals(expandedAppPackage) ? null : pkg;
                            appearance.edit().putString(UI04_EXPANDED_APP_KEY, expandedAppPackage).apply();
                            renderCatalogResults();
                        });
                if (expanded) for (TweakEngine.Entry<HookContext> entry : matches)
                    catalogCard(card, entry);
            }
        }
        if (visible == 0) catalogResults.addView(text(getString(R.string.ui_catalog_empty), 16));
    }
    private void hooksCatalog() {
        heading(R.string.ui_hooks);
        body.addView(text(getString(R.string.ui_catalog_intro), 14));
        ChipGroup modes = new ChipGroup(this);
        modes.setSingleSelection(true);
        modes.addView(catalogChip(R.string.ui_groups, UI04_GROUPS));
        modes.addView(catalogChip(R.string.ui_by_app, UI04_BY_APP));
        body.addView(modes);
        android.widget.EditText search = new android.widget.EditText(this);
        search.setSingleLine(true);
        search.setHint(R.string.ui_search_hooks);
        search.setText(catalogQuery);
        search.setTextColor(MaterialColors.getColor(search, com.google.android.material.R.attr.colorOnSurface));
        search.setHintTextColor(MaterialColors.getColor(search,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(MaterialColors.getColor(search,
                com.google.android.material.R.attr.colorSurfaceContainer));
        background.setCornerRadius(dp(12));
        search.setBackground(background);
        search.setPadding(dp(16), dp(10), dp(16), dp(10));
        search.setMinHeight(dp(48));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        body.addView(search, lp);
        catalogResults = new LinearLayout(this);
        catalogResults.setOrientation(LinearLayout.VERTICAL);
        body.addView(catalogResults, new LinearLayout.LayoutParams(-1, -2));
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                catalogQuery = s.toString();
                appearance.edit().putString(UI04_QUERY_KEY, catalogQuery).apply();
                renderCatalogResults(); // No full render: focus, cursor, and IME remain stable.
            }
            @Override public void afterTextChanged(android.text.Editable editable) { }
        });
        renderCatalogResults();
    }


    // DX-01: SAF import/export only. Imported log lines are immediately normalized
    // into a bounded redacted in-memory store; no implicit log access or root.
    private final java.util.concurrent.ExecutorService diagnosticWorker =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline.Filter diagnosticFilter =
            new com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline.Filter();
    private LinearLayout diagnosticRows;
    private int diagnosticPageIndex;
    private String diagnosticImportSource = "LSPOSED_MODULE";
    private static final int DX01_PAGE_SIZE = 30;
    private final androidx.activity.result.ActivityResultLauncher<String[]> diagnosticImportLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
                    uri -> { if(uri != null) importDiagnosticDocument(uri); });
    private final androidx.activity.result.ActivityResultLauncher<String> diagnosticExportLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
                    uri -> { if(uri != null) exportDiagnosticDocument(uri); });

    // DX-02: explicit bounded reproduction. Only SAF imported files can add events.
    private static final int DX02_MAX_CHOICES=32;
    private final android.os.Handler captureDeadlineHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable captureDeadlineRefresh = () -> {
        if(page==CAPTURE && !isFinishing() && !isDestroyed())render();
    };
    private final androidx.activity.result.ActivityResultLauncher<String> captureExportLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
                    uri -> { if(uri!=null) exportCaptureDocument(uri); });
    private HookLabApp captureApp(){return (HookLabApp)getApplication();}
    private long captureClock(){return android.os.SystemClock.elapsedRealtime();}
    private void selectCaptureSource(TweakSpec hook,String researchPackage){
        final String[] ids={"LSPOSED_MODULE","LSPOSED_FRAMEWORK","ANDROID_LOGCAT_IMPORT"};
        final String[] labels={getString(R.string.dx01_module),getString(R.string.dx01_framework),
                getString(R.string.dx01_logcat_import)};
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.dx02_choose_source)
            .setItems(labels,(dialog,which) -> {
                if(which<0 || which>=ids.length)return;
                com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline timeline=diagnosticsStore();
                long mark=com.mikeyphw.hooklab.diagnostics.CaptureSession.latestOrdinal(timeline);
                long elapsed=captureClock(),wall=System.currentTimeMillis();
                com.mikeyphw.hooklab.diagnostics.CaptureSession next;
                try {
                    next=hook!=null
                        ? com.mikeyphw.hooklab.diagnostics.CaptureSession.forHook(hook,ids[which],elapsed,wall,mark,timeline.dropped())
                        : com.mikeyphw.hooklab.diagnostics.CaptureSession.forResearch(researchPackage,ids[which],elapsed,wall,mark,timeline.dropped());
                }catch(IllegalArgumentException rejected){toast(R.string.dx02_target_invalid);return;}
                if(!captureApp().startCapture(next)){toast(R.string.dx02_active_exists);return;}
                goTo(CAPTURE);
            }).show();
    }
    private void selectCaptureTarget(){
        final List<TweakSpec> specs=TweakCatalog.all();
        if(specs.size()>DX02_MAX_CHOICES) {toast(R.string.dx02_target_invalid);return;}
        final String[] titles=new String[specs.size()+1];
        for(int i=0;i<specs.size();i++){
            final TweakSpec spec=specs.get(i);
            TweakEngine.Entry<HookContext> entry=entryForId(spec.id);
            titles[i]=(entry==null?spec.id:entry.title)+" · "+spec.packageName;
        }
        titles[specs.size()]=getString(R.string.dx02_research_target);
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.dx02_choose_target)
            .setItems(titles,(dialog,index) -> {
                if(index<specs.size()) {selectCaptureSource(specs.get(index),null);return;}
                android.widget.EditText input=new android.widget.EditText(this);
                input.setSingleLine(true);input.setHint(R.string.dx02_package_hint);
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.dx02_research_target)
                    .setView(input)
                    .setNegativeButton(android.R.string.cancel,null)
                    .setPositiveButton(android.R.string.ok,(confirm,button) -> {
                        String pkg=input.getText().toString().trim();
                        if(TweakCatalog.knowsPackage(pkg)){
                            toast(R.string.dx02_research_registered);return;
                        }
                        if(!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")){
                            toast(R.string.dx02_target_invalid);return;
                        }
                        selectCaptureSource(null,pkg);
                    }).show();
            }).show();
    }
    private void captureMarker(com.mikeyphw.hooklab.diagnostics.CaptureSession.MarkerCode code){
        com.mikeyphw.hooklab.diagnostics.CaptureSession capture=captureApp().capture();
        if(capture==null)return;
        try {capture.mark(code,captureClock(),
                    com.mikeyphw.hooklab.diagnostics.CaptureSession.latestOrdinal(diagnosticsStore()));
            render();
        }catch(IllegalArgumentException | IllegalStateException rejected){toast(R.string.dx02_marker_refused);}
    }
    private void exportCaptureDocument(android.net.Uri uri){
        com.mikeyphw.hooklab.diagnostics.CaptureSession capture=captureApp().capture();
        if(capture==null){toast(R.string.dx02_export_failed);return;}
        final com.mikeyphw.hooklab.diagnostics.CaptureSession.Report report;
        try {report=capture.report(diagnosticsStore(),captureClock());}
        catch(IllegalStateException | IllegalArgumentException rejected){toast(R.string.dx02_export_failed);return;}
        diagnosticWorker.execute(() -> {
            try {com.mikeyphw.hooklab.diagnostics.CaptureSaf.export(getContentResolver(),uri,report);
                runOnUiThread(() -> {if(!isFinishing()&&!isDestroyed())toast(R.string.dx02_exported);});
            }catch(java.io.IOException | IllegalArgumentException rejected){
                runOnUiThread(() -> {if(!isFinishing()&&!isDestroyed())toast(R.string.dx02_export_failed);});
            }
        });
    }
    private void capturePage(){
        heading(R.string.dx02_title);
        body.addView(text(getString(R.string.dx02_disclaimer),14));
        com.mikeyphw.hooklab.diagnostics.CaptureSession capture=captureApp().capture();
        if(capture==null){
            if(captureApp().interruptedCapture())
                card(getString(R.string.dx02_interrupted),getString(R.string.dx02_process_loss));
            button(card(getString(R.string.dx02_new),getString(R.string.dx02_instruction)),
                    R.string.dx02_choose_target,v -> selectCaptureTarget());
            return;
        }
        long now=captureClock();
        com.mikeyphw.hooklab.diagnostics.CaptureSession.State state=capture.expireIfNeeded(
                now,com.mikeyphw.hooklab.diagnostics.CaptureSession.latestOrdinal(diagnosticsStore()));
        if(state!=com.mikeyphw.hooklab.diagnostics.CaptureSession.State.ACTIVE)captureApp().closeCapture();
        LinearLayout header=card(capture.targetHookId.isEmpty()?capture.targetPackage:capture.targetHookId,
                getString(R.string.dx02_state,capture.state().name(),capture.source,
                        capture.elapsed(now)/1000));
        header.addView(text(capture.probeSummary(),13));
        header.addView(text(getString(R.string.dx02_metadata_only),14));
        captureDeadlineHandler.removeCallbacks(captureDeadlineRefresh);
        if(state==com.mikeyphw.hooklab.diagnostics.CaptureSession.State.ACTIVE){
            long delay=Math.max(1,com.mikeyphw.hooklab.diagnostics.CaptureSession.MAX_DURATION_MS-capture.elapsed(now));
            captureDeadlineHandler.postDelayed(captureDeadlineRefresh,delay);
            button(header,R.string.dx02_import,v -> {
                if(captureApp().captureImportRunning()){toast(R.string.dx02_import_pending);return;}
                diagnosticImportSource=capture.source;
                diagnosticImportLauncher.launch(new String[]{"text/plain","application/octet-stream"});
            });
            button(header,R.string.dx02_mark_repro,v -> captureMarker(
                com.mikeyphw.hooklab.diagnostics.CaptureSession.MarkerCode.REPRODUCE_ACTION));
            button(header,R.string.dx02_mark_problem,v -> captureMarker(
                com.mikeyphw.hooklab.diagnostics.CaptureSession.MarkerCode.OBSERVED_PROBLEM));
            button(header,R.string.dx02_mark_restart,v -> captureMarker(
                com.mikeyphw.hooklab.diagnostics.CaptureSession.MarkerCode.APP_RESTARTED));
            button(header,R.string.dx02_mark_permission,v -> captureMarker(
                com.mikeyphw.hooklab.diagnostics.CaptureSession.MarkerCode.PERMISSION_PROMPT));
            button(header,R.string.dx02_stop,v -> {
                if(captureApp().captureImportRunning()){toast(R.string.dx02_import_pending);return;}
                capture.stop(captureClock(),com.mikeyphw.hooklab.diagnostics.CaptureSession.latestOrdinal(diagnosticsStore()));
                captureApp().closeCapture();render();
            }).setEnabled(!captureApp().captureImportRunning());
            button(header,R.string.dx02_cancel,v ->
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.dx02_cancel).setMessage(R.string.dx02_cancel_warning)
                    .setNegativeButton(android.R.string.cancel,null)
                    .setPositiveButton(android.R.string.ok,(dialog,which)->{
                        if(captureApp().captureImportRunning()){toast(R.string.dx02_import_pending);return;}
                        capture.cancel();captureApp().closeCapture();render();
                    }).show());
        } else if(state!=com.mikeyphw.hooklab.diagnostics.CaptureSession.State.CANCELLED){
            com.mikeyphw.hooklab.diagnostics.CaptureSession.Report report=capture.report(diagnosticsStore(),now);
            card(getString(R.string.dx02_summary),getString(R.string.dx02_summary_count,
                    report.observed,capture.markersCount(),report.missingDueToRetention));
            button(header,R.string.dx02_export,v -> captureExportLauncher.launch("hooklab-capture-redacted.zip"));
        }else card(getString(R.string.dx02_cancel),getString(R.string.dx02_cancelled));
        button(card(getString(R.string.dx02_next),getString(R.string.dx02_post_capture)),
                R.string.dx02_new,v -> {
                    if(captureApp().captureImportRunning()){toast(R.string.dx02_import_pending);return;}
                    if(capture.state()==com.mikeyphw.hooklab.diagnostics.CaptureSession.State.ACTIVE){
                        toast(R.string.dx02_active_exists);return;
                    }
                    selectCaptureTarget();
                });
    }

    private com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline diagnosticsStore() {
        return ((HookLabApp)getApplication()).diagnostics();
    }
    private void importDiagnosticDocument(android.net.Uri uri) {
        final String source = diagnosticImportSource;
        final boolean requestedCapture = page==CAPTURE;
        final boolean captured = requestedCapture && captureApp().beginCaptureImport();
        if(requestedCapture && !captured){toast(R.string.dx02_import_pending);return;}
        final com.mikeyphw.hooklab.diagnostics.CaptureSession currentCapture =
                captured ? captureApp().capture() : null;
        final long capturePriorOrdinal = captured ?
                com.mikeyphw.hooklab.diagnostics.CaptureSession.latestOrdinal(diagnosticsStore()) : 0;
        diagnosticWorker.execute(() -> {
            try {
                com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline.ImportReceipt r =
                    com.mikeyphw.hooklab.diagnostics.DiagnosticSaf.importDocument(
                        getContentResolver(),uri,diagnosticsStore(),source);
                if(captured && currentCapture!=null){
                    boolean counted=currentCapture.registerImport(source,capturePriorOrdinal,
                            com.mikeyphw.hooklab.diagnostics.CaptureSession.latestOrdinal(diagnosticsStore()),captureClock());
                    if(!counted)captureApp().closeCapture();
                }
                runOnUiThread(() -> {
                    if(isFinishing() || isDestroyed())return;
                    diagnosticPageIndex=0;
                    toast(getString(R.string.dx01_import_success, r.accepted));
                    if(page==DIAGNOSTICS)renderDiagnosticRows();
                    if(page==CAPTURE)render();
                });
            }catch(java.io.IOException | IllegalArgumentException | IllegalStateException error){
                runOnUiThread(() -> {if(!isFinishing()&&!isDestroyed())toast(R.string.dx01_import_failed);});
            }finally {if(captured)captureApp().endCaptureImport();}
        });
    }
    private void exportDiagnosticDocument(android.net.Uri uri) {
        com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline store=diagnosticsStore();
        final List<com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline.Event> selected =
                store.filtered(diagnosticFilter);
        final int dropped=store.dropped();
        diagnosticWorker.execute(() -> {
            try {
                com.mikeyphw.hooklab.diagnostics.DiagnosticSaf.exportRedactedZip(
                        getContentResolver(),uri,selected,dropped);
                runOnUiThread(() -> {if(!isFinishing()&&!isDestroyed())toast(R.string.dx01_export_success);});
            }catch(java.io.IOException | IllegalArgumentException error){
                runOnUiThread(() -> {if(!isFinishing()&&!isDestroyed())toast(R.string.dx01_export_failed);});
            }
        });
    }
    private void copyDiagnosticText(String redacted) {
        android.content.ClipboardManager clipboard=(android.content.ClipboardManager)
                getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if(clipboard==null) {toast(R.string.dx01_copy_failed);return;}
        try {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                    getString(R.string.dx01_export_redacted),redacted));
            toast(R.string.dx01_copy_success);
        }catch(RuntimeException failure){toast(R.string.dx01_copy_failed);}
    }
    private void showDiagnosticDetail(com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline.Event e) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(e.code)
            .setMessage(getString(R.string.dx01_inspector, e.source, e.collector,
                    e.fileSha256, e.lineNumber, e.timestamp.isEmpty()?"unknown":e.timestamp,
                    e.correlation, e.message))
            .setNeutralButton(R.string.dx01_copy_event,
                    (dialog,which) -> copyDiagnosticText(
                        com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline.toJson(e)))
            .setPositiveButton(android.R.string.ok,null).show();
    }
    private void renderDiagnosticRows() {
        if(diagnosticRows==null || page!=DIAGNOSTICS)return;
        diagnosticRows.removeAllViews();
        com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline store=diagnosticsStore();
        List<com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline.Event> visible =
                store.filtered(diagnosticFilter);
        diagnosticRows.addView(text(getString(R.string.dx01_event_counts,
                visible.size(),store.size(),store.dropped()),14));
        if(visible.isEmpty()) {
            diagnosticRows.addView(text(getString(R.string.dx01_no_imports),16));
            return;
        }
        int pages=(visible.size()+DX01_PAGE_SIZE-1)/DX01_PAGE_SIZE;
        diagnosticPageIndex=Math.min(diagnosticPageIndex,pages-1);
        int begin=diagnosticPageIndex*DX01_PAGE_SIZE;
        int end=Math.min(visible.size(),begin+DX01_PAGE_SIZE);
        for(int i=begin;i<end;i++){
            com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline.Event e=visible.get(i);
            LinearLayout row=cardTo(diagnosticRows,e.code,e.severity+" · "+e.source+" · "+
                (e.timestamp.isEmpty()?getString(R.string.dx01_clock_unknown):e.timestamp));
            row.addView(text(e.message,14));
            button(row,R.string.dx01_inspect,v -> showDiagnosticDetail(e));
        }
        LinearLayout pagination=new LinearLayout(this);
        pagination.setOrientation(LinearLayout.HORIZONTAL);
        com.google.android.material.button.MaterialButton previous=new com.google.android.material.button.MaterialButton(this);
        previous.setText(R.string.dx01_previous);FlatUi.button(previous);
        previous.setEnabled(diagnosticPageIndex>0);
        previous.setOnClickListener(v -> {diagnosticPageIndex--;renderDiagnosticRows();});
        pagination.addView(previous,new LinearLayout.LayoutParams(0,dp(48),1));
        com.google.android.material.button.MaterialButton next=new com.google.android.material.button.MaterialButton(this);
        next.setText(R.string.dx01_next);FlatUi.button(next);
        next.setEnabled(diagnosticPageIndex<pages-1);
        next.setOnClickListener(v -> {diagnosticPageIndex++;renderDiagnosticRows();});
        pagination.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));
        diagnosticRows.addView(pagination);
    }
    private android.widget.EditText diagnosticTextFilter(LinearLayout parent,int hint,
            String initial,java.util.function.Consumer<String> update) {
        android.widget.EditText search=new android.widget.EditText(this);
        search.setSingleLine(true);search.setText(initial);search.setHint(hint);
        search.setMinHeight(dp(48));search.setTextSize(14);
        search.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                update.accept(s.toString());diagnosticPageIndex=0;renderDiagnosticRows();
            }
            @Override public void afterTextChanged(android.text.Editable e){}
        });
        parent.addView(search,new LinearLayout.LayoutParams(-1,-2));return search;
    }
    private void diagnosticsTimelinePage() {
        LinearLayout panel=card(getString(R.string.dx01_title),getString(R.string.dx01_import_only));
        button(panel,R.string.dx02_capture,v -> goTo(CAPTURE));
        button(panel,R.string.dx01_import, v -> {
            final String[] types={"LSPOSED_MODULE","LSPOSED_FRAMEWORK","ANDROID_LOGCAT_IMPORT"};
            final String[] labels={getString(R.string.dx01_module),getString(R.string.dx01_framework),
                getString(R.string.dx01_logcat_import)};
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.dx01_choose_source)
                .setItems(labels,(dialog,which) -> {
                    diagnosticImportSource=types[which];
                    diagnosticImportLauncher.launch(new String[]{"text/plain","application/octet-stream"});
                }).show();
        });
        button(panel,R.string.dx01_export_redacted,v ->
                diagnosticExportLauncher.launch("hooklab-redacted-diagnostics.zip"));
        button(panel,R.string.dx01_copy_filtered,v -> copyDiagnosticText(
                com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline.toJsonl(
                        diagnosticsStore().filtered(diagnosticFilter))));
        diagnosticTextFilter(panel,R.string.dx01_filter_search,diagnosticFilter.query,
                s -> diagnosticFilter.query=s);
        diagnosticTextFilter(panel,R.string.dx01_filter_package,diagnosticFilter.packageName,
                s -> diagnosticFilter.packageName=s.trim());
        diagnosticTextFilter(panel,R.string.dx01_filter_hook,diagnosticFilter.hookId,
                s -> diagnosticFilter.hookId=s.trim());
        com.google.android.material.chip.ChipGroup sources=new com.google.android.material.chip.ChipGroup(this);
        sources.setSingleSelection(true);
        final String[] ids={"","LSPOSED_MODULE","LSPOSED_FRAMEWORK","ANDROID_LOGCAT_IMPORT"};
        final int[] names={R.string.dx01_all,R.string.dx01_module,R.string.dx01_framework,R.string.dx01_logcat_import};
        for(int i=0;i<ids.length;i++){
            final String value=ids[i];
            com.google.android.material.chip.Chip chip=new com.google.android.material.chip.Chip(this);
            FlatUi.chip(chip);chip.setCheckable(true);chip.setText(names[i]);
            chip.setChecked(value.equals(diagnosticFilter.source));
            chip.setOnClickListener(v -> {diagnosticFilter.source=value;diagnosticPageIndex=0;renderDiagnosticRows();});
            sources.addView(chip);
        }
        panel.addView(sources);
        com.google.android.material.chip.ChipGroup severity=new com.google.android.material.chip.ChipGroup(this);
        severity.setSingleSelection(true);
        final String[] levels={"","INFO","WARN","ERROR"};
        for(String value:levels){
            com.google.android.material.chip.Chip chip=new com.google.android.material.chip.Chip(this);
            FlatUi.chip(chip);chip.setCheckable(true);
            chip.setText(value.isEmpty()?getString(R.string.dx01_all):value);
            chip.setChecked(value.equals(diagnosticFilter.severity));
            chip.setOnClickListener(v -> {diagnosticFilter.severity=value;diagnosticPageIndex=0;renderDiagnosticRows();});
            severity.addView(chip);
        }
        panel.addView(severity);
        diagnosticRows=new LinearLayout(this);diagnosticRows.setOrientation(LinearLayout.VERTICAL);
        body.addView(diagnosticRows,new LinearLayout.LayoutParams(-1,-2));
        renderDiagnosticRows();
    }

    private MaterialToolbar toolbar;
    private NavigationBarView navigation;
    private FrameworkStateStore stateStore;
    private FrameworkSnapshot snapshot = FrameworkSnapshot.loading();
    private final Set<String> pendingPreferences = new HashSet<>();
    private final Set<String> pendingScope = new HashSet<>();
    private final Runnable frameworkObserver = () -> stateStore.refresh();

    @Override public void onCreate(Bundle state) {
        appearance = getSharedPreferences("appearance", MODE_PRIVATE);
        int night = appearance.getInt("night", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (night == THEME_AMOLED) {
            setTheme(R.style.Theme_HookLab_Amoled);
            night = AppCompatDelegate.MODE_NIGHT_YES;
        }
        getDelegate().setLocalNightMode(night);
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 31
                && appearance.getInt("night", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) != THEME_AMOLED
                && appearance.getBoolean("dynamicColors", true)) {
            try { DynamicColors.applyToActivityIfAvailable(this); }
            catch (RuntimeException ignored) { /* Fallback to packaged Material 3 palette. */ }
        }
        stateStore = new FrameworkStateStore((HookLabApp) getApplication());
        page = state == null ? HOME : Math.max(HOME, Math.min(CAPTURE, state.getInt("page")));
        selectedHookId = state == null ? null : state.getString("selectedHookId");
        selectedGroupId = state == null ? null : state.getString("selectedGroupId");
        detailReturnPage = state == null ? APPS : state.getInt("detailReturnPage", APPS);
        if (detailReturnPage != APPS && detailReturnPage != HOME
                && detailReturnPage != GROUP_DETAIL) detailReturnPage = APPS;
        // Migrate a pre-UI-04 activity state by package once; never use the
        // package as the permanent selection key (multiple hooks may share it).
        if (selectedHookId == null && state != null) {
            String oldPackage = state.getString("selectedPackage");
            for (TweakEngine.Entry<HookContext> entry : TweakCatalog.entries()) {
                if (oldPackage != null && oldPackage.equals(entry.spec.packageName)) {
                    selectedHookId = entry.spec.id;
                    break;
                }
            }
        }
        catalogView = appearance.getInt(UI04_VIEW_KEY, UI04_GROUPS) == UI04_BY_APP
                ? UI04_BY_APP : UI04_GROUPS;
        catalogQuery = appearance.getString(UI04_QUERY_KEY, "");
        if (catalogQuery == null) catalogQuery = "";
        expandedGroupId = appearance.getString(UI04_EXPANDED_GROUP_KEY, null);
        expandedAppPackage = appearance.getString(UI04_EXPANDED_APP_KEY, null);
        // Explicit-component deep link accepts only an exact registered hook ID.
        if (state == null && getIntent() != null
                && ACTION_OPEN_HOOK.equals(getIntent().getAction())) {
            try {
                String requested = getIntent().getStringExtra("hook_id");
                if (HookDetailRoute.hook(TweakCatalog.all(), requested) != null) {
                    selectedHookId = requested;
                    detailReturnPage = APPS;
                    page = APP_DETAIL;
                }
            } catch (RuntimeException ignored) { /* Reject malformed untrusted extras. */ }
        }
        if (page == GROUP_DETAIL && selectedGroup() == null) page = APPS;
        if (page == APP_DETAIL && selectedEntry() == null) page = APPS;
        if (detailReturnPage == GROUP_DETAIL && selectedGroup() == null) detailReturnPage = APPS;
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        // DS-01: the same content/behavior on phone and tablet, with a native rail
        // on wide windows. Never tie navigation choices to hook preference writes.
        int widthDp = getResources().getConfiguration().screenWidthDp;
        boolean wide = UiLayoutPolicy.useRail(widthDp);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setBackgroundColor(FlatUi.canvas(this));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, w) -> {
            Insets i = w.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return w;
        });
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        if (wide) {
            NavigationRailView rail = new NavigationRailView(this);
            navigation = rail;
            root.addView(rail, new LinearLayout.LayoutParams(dp(UiLayoutPolicy.RAIL_WIDTH_DP), -1));
            FrameLayout center = new FrameLayout(this);
            FrameLayout.LayoutParams centered = new FrameLayout.LayoutParams(
                dp(UiLayoutPolicy.contentWidthDp(widthDp)), -1, android.view.Gravity.CENTER_HORIZONTAL);
            center.addView(content, centered);
            root.addView(center, new LinearLayout.LayoutParams(0, -1, 1));
        } else {
            root.addView(content, new LinearLayout.LayoutParams(-1, -1));
        }

        toolbar = new MaterialToolbar(this);
        FlatUi.toolbar(toolbar);
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
        content.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(56)));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int insetDp = UiLayoutPolicy.contentInsetDp(widthDp);
        body.setPadding(dp(insetDp), dp(12), dp(insetDp), dp(32));
        scroll.addView(body);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        if (!wide) navigation = new BottomNavigationView(this);
        navigation.getMenu().add(Menu.NONE, NAV_HOME, 0, R.string.home).setIcon(R.drawable.ic_nav_home);
        navigation.getMenu().add(Menu.NONE, NAV_APPS, 1, R.string.ui_hooks).setIcon(R.drawable.ic_nav_apps);
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
        FlatUi.navigation(navigation);
        if (!wide) content.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
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
        if (page == APP_DETAIL) {
            int destination = detailReturnPage == GROUP_DETAIL && selectedGroup() == null
                    ? APPS : detailReturnPage;
            goTo(destination);
        } else goTo(page == CAPTURE ? DIAGNOSTICS : page == GROUP_DETAIL ? APPS : HOME);
    }
    private void goTo(int target) {
        if (target == page) return;
        page = target;
        render();
        scroll.scrollTo(0, 0);
    }
    @Override protected void onStart() {
        super.onStart();
        pendingPreferences.clear();
        pendingScope.clear();
        ((HookLabApp) getApplication()).addObserver(frameworkObserver);
        stateStore.start(next -> {
            if (isFinishing() || isDestroyed() || snapshot.equals(next)) return;
            int previousScroll = scroll.getScrollY();
            snapshot = next;
            // On the Hooks catalog, update only results so a framework status
            // refresh cannot steal search keyboard focus or cursor position.
            if (page == APPS && catalogResults != null) renderCatalogResults();
            else render();
            scroll.post(() -> scroll.scrollTo(0, previousScroll));
        });
    }
    @Override protected void onStop() {
        captureDeadlineHandler.removeCallbacks(captureDeadlineRefresh);
        ((HookLabApp) getApplication()).removeObserver(frameworkObserver);
        stateStore.stop();
        super.onStop();
    }
    @Override protected void onDestroy() {
        // Let an already selected SAF import finish and clear the app-level import gate.
        diagnosticWorker.shutdown();
        stateStore.close();
        super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("page", page); out.putString("selectedHookId", selectedHookId);
        out.putString("selectedGroupId", selectedGroupId);
        out.putInt("detailReturnPage", detailReturnPage);
        super.onSaveInstanceState(out);
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private XposedService service() { return ((HookLabApp) getApplication()).currentService(); }
    private boolean ready(XposedService s) {
        return s != null && s == service() && snapshot.ready();
    }
    private MaterialTextView text(CharSequence value, int sp) {
        MaterialTextView t = new MaterialTextView(this); t.setText(value); t.setTextSize(sp);
        t.setTextColor(MaterialColors.getColor(t, com.google.android.material.R.attr.colorOnSurface));
        return t;
    }
    private void heading(int id) {
        TextView t = text(getString(id), 32); t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setAccessibilityHeading(true); body.addView(t);
    }
    private LinearLayout card(CharSequence title, CharSequence description) {
        MaterialCardView card = new MaterialCardView(this); FlatUi.section(card);
        LinearLayout inner = new LinearLayout(this); inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(18), dp(18), dp(18), dp(18));
        inner.addView(text(title, 20));
        TextView detail = text(description, 16); detail.setPadding(0, dp(8), 0, 0); inner.addView(detail);
        card.addView(inner);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16); body.addView(card, lp);
        return inner;
    }
    private MaterialButton button(LinearLayout parent, int label, View.OnClickListener click) {
        MaterialButton b = new MaterialButton(this); b.setText(label); b.setMinHeight(dp(48));
        FlatUi.button(b);
        b.setOnClickListener(click); parent.addView(b, new ViewGroup.LayoutParams(-1, -2));
        return b;
    }
    private void toast(int message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private String connection(XposedService s) {
        switch (snapshot.connection) {
            case LOADING: return getString(R.string.ui_loading_framework);
            case UNSUPPORTED: return getString(R.string.status_unsupported, snapshot.apiVersion);
            case CONNECTED:
                return getString(R.string.status_connected,
                        snapshot.frameworkName == null ? "LSPosed" : snapshot.frameworkName,
                        snapshot.frameworkVersion == null ? "?" : snapshot.frameworkVersion,
                        snapshot.apiVersion);
            case UNAVAILABLE: return getString(R.string.ui_framework_read_failed);
            default: return getString(R.string.status_disconnected);
        }
    }
    private Set<String> scopedPackages(XposedService s) {
        return ready(s) ? snapshot.scope : null;
    }
    private TweakEngine.Entry<HookContext> selectedEntry() {
        TweakSpec verified = HookDetailRoute.hook(TweakCatalog.all(), selectedHookId);
        return verified == null ? null : entryForId(verified.id);
    }

    /** Framework-observable configuration only; never assert that a hook ran. */
    private TweakCompatibility.State eligibility(TweakSpec spec, XposedService s) {
        return snapshot.eligibility(spec);
    }
    private String statusText(TweakCompatibility.State state) {
        if (snapshot.connection == FrameworkSnapshot.Connection.LOADING)
            return getString(R.string.ui_loading_framework);
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
        if (entry == null || entryForId(entry.spec.id) == null) return;
        detailReturnPage = page == GROUP_DETAIL ? GROUP_DETAIL : page == HOME ? HOME : APPS;
        selectedHookId = entry.spec.id;
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
        hooksCatalog();
    }



    private HookGroupCatalog.Group selectedGroup() {
        return HookDetailRoute.group(HookGroupCatalog.groups(TweakCatalog.all()), selectedGroupId);
    }
    private void showGroup(HookGroupCatalog.Group group) {
        if (group == null || HookDetailRoute.group(
                HookGroupCatalog.groups(TweakCatalog.all()), group.id) == null) return;
        selectedGroupId = group.id;
        goTo(GROUP_DETAIL); // Navigation only, never a group-wide preference write.
    }
    private void groupDetail() {
        HookGroupCatalog.Group group = selectedGroup();
        if (group == null) {
            heading(R.string.ui_hooks);
            card(getString(R.string.ui05_group_unavailable), getString(R.string.ui_status_caution));
            return;
        }
        heading(R.string.ui05_group_setup);
        card(groupName(group), group.description + "\n" + getString(R.string.ui_group_no_toggle));
        XposedService expected = service();
        Set<String> scope = scopedPackages(expected);
        body.addView(text(getString(R.string.ui05_scope_targets), 22));
        for (String pkg : group.packages) {
            boolean approved = scope != null && scope.contains(pkg);
            LinearLayout target = card(pkg,
                    scope == null ? getString(R.string.ui_scope_unknown) :
                    approved ? getString(R.string.ui_scope_approved) : getString(R.string.ui_scope_needed));
            if (ready(expected) && scope != null) {
                button(target, approved ? R.string.scope_remove : R.string.scope_add,
                        v -> { if (approved) removeScope(expected, pkg);
                               else requestScope(expected, pkg); })
                        .setEnabled(!pendingScope.contains(pkg));
            } else target.addView(text(getString(R.string.scope_unavailable), 14));
        }
        body.addView(text(getString(R.string.ui05_group_hooks), 22));
        for (TweakSpec spec : group.members) {
            TweakEngine.Entry<HookContext> entry = entryForId(spec.id);
            if (entry == null) continue;
            LinearLayout detail = card(entry.title,
                    statusText(eligibility(spec, expected)));
            detail.addView(text(spec.packageName, 14));
            detail.addView(text(getString(R.string.ui05_independent_config), 14));
            button(detail, R.string.ui05_open_hook, v -> showApp(entry));
        }
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
        HookGroupCatalog.Group owningGroup = HookDetailRoute.groupForHook(
                HookGroupCatalog.groups(TweakCatalog.all()), spec.id);
        if (owningGroup != null) {
            button(overview, R.string.ui05_group_details, v -> showGroup(owningGroup));
        }
        FrameworkSnapshot.Flag observedFlag = snapshot.flag(spec.id);
        String versionText = observedFlag == null || observedFlag.installedVersion == null
                ? getString(R.string.ui05_version_unknown)
                : Long.toString(observedFlag.installedVersion);
        LinearLayout safety = card(getString(R.string.ui05_hook_evidence),
                getString(R.string.ui05_hook_identity, spec.id, spec.packageName) + "\n" +
                getString(R.string.ui05_version_observed, versionText) + "\n" +
                getString(R.string.ui05_main_process_only) + "\n" +
                getString(R.string.ui_host_log_note));
        if (observedFlag == null || observedFlag.locked == null || !ready(s)) {
            safety.addView(text(getString(R.string.ui05_safety_unknown), 14));
        } else {
            boolean lock = observedFlag.locked;
            safety.addView(text(getString(lock ? R.string.ui_safety_locked :
                    R.string.diagnostics_unlocked), 14));
            button(safety, lock ? R.string.diagnostics_reset_quarantine :
                    R.string.diagnostics_enable_lock,
                    v -> confirmSafetyLock(s, spec.id, !lock))
                    .setEnabled(!pendingPreferences.contains("lock:" + spec.id));
        }


        Set<String> scope = scopedPackages(s);
        boolean scoped = scope != null && scope.contains(spec.packageName);
        LinearLayout scopeCard = card(getString(R.string.scope),
                scope == null ? getString(R.string.ui_scope_unknown) :
                scoped ? getString(R.string.ui_scope_approved) : getString(R.string.ui_scope_needed));
        if (ready(s) && scope != null) {
            button(scopeCard, scoped ? R.string.scope_remove : R.string.scope_add,
                    v -> { if (scoped) removeScope(s, spec.packageName);
                           else requestScope(s, spec.packageName); })
                    .setEnabled(!pendingScope.contains(spec.packageName));
        }

        LinearLayout tweakCard = card(getString(R.string.ui_tweak_setting), entry.description);
        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setText(R.string.ui_enable_tweak);
        FrameworkSnapshot.Flag tweakFlag = snapshot.flag(spec.id);
        boolean available = ready(s) && tweakFlag != null && tweakFlag.enabled != null
                && !pendingPreferences.contains(spec.id);
        sw.setChecked(Boolean.TRUE.equals(tweakFlag == null ? null : tweakFlag.enabled));
        sw.setVisibility(tweakFlag == null || tweakFlag.enabled == null
                ? View.GONE : View.VISIBLE); // Unknown is not a false OFF state.
        sw.setEnabled(available);
        sw.setOnCheckedChangeListener((view, wanted) -> {
            sw.setEnabled(false);
            saveTweak(s, spec.id, wanted);
        });
        tweakCard.addView(sw, new ViewGroup.LayoutParams(-1, -2));
        if (!available) tweakCard.addView(text(getString(pendingPreferences.contains(spec.id)
                ? R.string.ui_saving : R.string.settings_unavailable), 14));
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
        // UI-06: truthful projection of the framework snapshot and DX-01 imports.
        // No read or write path here can install, toggle, scope or start hooks.
        HomeDashboardModel.Summary summary = HomeDashboardModel.project(
                TweakCatalog.all(), HookGroupCatalog.groups(TweakCatalog.all()),
                snapshot, diagnosticsStore());
        card(getString(R.string.ui06_overview), getString(R.string.ui06_counts,
                summary.hookCount, summary.groupCount, summary.packageCount)
                + "\n" + getString(R.string.ui06_scoped,
                    summary.scopedPackageCount < 0 ? getString(R.string.ui_unknown)
                        : Integer.toString(summary.scopedPackageCount)));
        LinearLayout readiness = card(getString(R.string.ui06_readiness),
                connection(service()) + "\n" + getString(R.string.ui06_readiness_counts,
                    summary.eligibleConfigurations, summary.problems,
                    summary.disabled, summary.unknown));
        readiness.addView(text(getString(R.string.ui06_not_runtime), 14));
        if (!ready(service()))
            readiness.addView(text(getString(R.string.ui_framework_help), 14));
        if (summary.attentionHookId != null) {
            TweakEngine.Entry<HookContext> attention = entryForId(summary.attentionHookId);
            if (attention != null) {
                LinearLayout issue = card(getString(R.string.ui06_needs_attention),
                        attention.title + ": " + statusText(eligibility(attention.spec, service())));
                button(issue, R.string.ui06_review_hook, v -> showApp(attention));
            }
        } else if (summary.unknown > 0) {
            card(getString(R.string.ui06_unknown_title), getString(R.string.ui06_unknown_detail));
        }
        if (summary.latestImported == null) {
            card(getString(R.string.ui06_recent), getString(R.string.ui06_no_imports));
        } else {
            com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline.Event event = summary.latestImported;
            String when = event.timestamp.isEmpty() ? getString(R.string.dx01_clock_unknown) : event.timestamp;
            LinearLayout recent = card(getString(R.string.ui06_recent),
                    getString(R.string.ui06_recent_format, event.source, event.code, event.correlation, when));
            recent.addView(text(getString(R.string.ui06_import_warning), 14));
            recent.addView(text(getString(R.string.ui06_import_retention,
                    summary.importedCount, summary.evictedCount), 14));
        }
        LinearLayout shortcuts = card(getString(R.string.ui06_quick_actions),
                getString(R.string.ui06_quick_help));
        button(shortcuts, R.string.ui06_browse_hooks, v -> goTo(APPS));
        button(shortcuts, R.string.ui06_open_diagnostics, v -> goTo(DIAGNOSTICS));
        button(shortcuts, R.string.scope, v -> goTo(SCOPE));
        // DX-02 capture exists under Diagnostics; Home quick actions remain navigation-only.
    }
    private void saveTweak(XposedService s, String id, boolean enabled) {
        if (!ready(s) || !pendingPreferences.add(id)) return;
        render(); // Show pending state; no optimistic preference change.
        stateStore.writeTweak(id, enabled, success -> {
            pendingPreferences.remove(id);
            toast(success ? R.string.tweak_saved : R.string.tweak_failed);
        });
    }
    private void requestScope(XposedService s, String pkg) {
        if (!ready(s) || !TweakCatalog.knowsPackage(pkg) || !pendingScope.add(pkg)) return;
        render();
        stateStore.requestScope(pkg, confirmed -> {
            pendingScope.remove(pkg);
            toast(confirmed ? R.string.scope_added : R.string.ui_scope_request_unconfirmed);
        });
    }
    private void removeScope(XposedService s, String pkg) {
        if (!ready(s) || !TweakCatalog.knowsPackage(pkg) || pendingScope.contains(pkg)) return;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.scope_remove).setMessage(pkg)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (!pendingScope.add(pkg)) return;
                    render();
                    stateStore.removeScope(pkg, requested -> {
                        pendingScope.remove(pkg);
                        toast(requested ? R.string.scope_removed : R.string.ui_scope_request_unconfirmed);
                    });
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
                           else requestScope(s, spec.packageName); })
                    .setEnabled(!pendingScope.contains(spec.packageName));
        }
    }
    /** Framework-truth diagnostics. Per-process events live only in Xposed logs. */
    private void diagnosticsPage() {
        heading(R.string.diagnostics);
        diagnosticsTimelinePage();
        XposedService s = service();
        card(getString(R.string.status), connection(s));
        if (!ready(s)) {
            card(getString(R.string.diagnostics), snapshot.connection == FrameworkSnapshot.Connection.LOADING
                    ? getString(R.string.ui_loading_framework) : getString(R.string.diagnostics_disconnected));
            return;
        }
        List<TweakEngine.Entry<HookContext>> entries = TweakCatalog.entries();
        if (entries.isEmpty()) {
            card(getString(R.string.diagnostics_empty), getString(R.string.diagnostics_inert));
            return;
        }
        for (TweakEngine.Entry<HookContext> entry : entries) {
            String id = entry.spec.id;
            FrameworkSnapshot.Flag flag = snapshot.flag(id);
            if (flag == null || flag.enabled == null || flag.locked == null) {
                card(entry.title, getString(R.string.diagnostics_unavailable));
                continue;
            }
            boolean locked = flag.locked;
            TweakCompatibility.State assessed = eligibility(entry.spec, s);
            LinearLayout inner = card(entry.title,
                    getString(R.string.diagnostics_compatibility, statusText(assessed))
                    + "\n" + getString(R.string.diagnostics_safety_state,
                          getString(locked ? R.string.diagnostics_locked : R.string.diagnostics_unlocked)));
            boolean nextLock = !locked;
            button(inner, nextLock ? R.string.diagnostics_enable_lock : R.string.diagnostics_reset_quarantine,
                    v -> confirmSafetyLock(s, id, nextLock))
                    .setEnabled(!pendingPreferences.contains("lock:" + id));
        }
        card(getString(R.string.diagnostics_note_title), getString(R.string.diagnostics_note));
    }

    private void confirmSafetyLock(XposedService s, String id, boolean locked) {
        if (!ready(s) || pendingPreferences.contains("lock:" + id)) return;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(locked ? R.string.diagnostics_enable_lock : R.string.diagnostics_reset_quarantine)
                .setMessage(locked ? R.string.diagnostics_lock_confirm : R.string.diagnostics_reset_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    pendingPreferences.add("lock:" + id);
                    render();
                    stateStore.writeSafetyLock(id, locked, success -> {
                        pendingPreferences.remove("lock:" + id);
                        toast(success ? R.string.diagnostics_reset_success : R.string.diagnostics_reset_failed);
                    });
                }).show();
    }

    private void themeChip(ChipGroup group, int label, int mode, int current) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setCheckable(true);
        FlatUi.chip(chip);
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
        MaterialSwitch sw = new MaterialSwitch(this);
        boolean available = ready(service()) && snapshot.verboseLogging != null
                && !pendingPreferences.contains("verbose");
        sw.setText(R.string.verbose_lsposed_logs);
        sw.setChecked(Boolean.TRUE.equals(snapshot.verboseLogging));
        sw.setEnabled(available);
        sw.setOnCheckedChangeListener((view, wanted) -> {
            sw.setEnabled(false);
            writeVerboseLogging(service(), wanted);
        });
        panel.addView(sw, new ViewGroup.LayoutParams(-1, -2));
        panel.addView(text(getString(R.string.verbose_lsposed_description), 14));
        if (!available) panel.addView(text(getString(pendingPreferences.contains("verbose")
                ? R.string.ui_saving : R.string.settings_unavailable), 14));
    }
    private void writeVerboseLogging(XposedService expected, boolean value) {
        if (!ready(expected) || !pendingPreferences.add("verbose")) return;
        render();
        stateStore.writeVerbose(value, success -> {
            pendingPreferences.remove("verbose");
            toast(success ? R.string.verbose_lsposed_saved : R.string.tweak_failed);
        });
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
        if (Build.VERSION.SDK_INT >= 31) {
            MaterialSwitch dynamic = new MaterialSwitch(this);
            dynamic.setText(R.string.ui_dynamic_colors);
            dynamic.setChecked(appearance.getBoolean("dynamicColors", true));
            dynamic.setEnabled(current != THEME_AMOLED);
            dynamic.setOnCheckedChangeListener((view, wanted) -> {
                appearance.edit().putBoolean("dynamicColors", wanted).apply();
                recreate();
            });
            appearanceCard.addView(dynamic);
            appearanceCard.addView(text(getString(R.string.ui_dynamic_colors_detail), 14));
        }
        verboseLogging();
    }
    private void render() {
        if (body == null) return;
        int navId = page == APP_DETAIL || page == APPS || page == GROUP_DETAIL ? NAV_APPS
                : (page == DIAGNOSTICS || page == CAPTURE) ? NAV_DIAGNOSTICS : NAV_HOME;
        navigation.getMenu().findItem(navId).setChecked(true);
        boolean secondary = page >= SETTINGS;
        if (secondary) {
            toolbar.setNavigationIcon(R.drawable.ic_nav_back);
            toolbar.setNavigationContentDescription(R.string.ui_back);
        } else {
            toolbar.setNavigationIcon((android.graphics.drawable.Drawable) null);
        }
        switch (page) {
            case APPS: toolbar.setTitle(R.string.ui_hooks); break;
            case DIAGNOSTICS: toolbar.setTitle(R.string.diagnostics); break;
            case CAPTURE: toolbar.setTitle(R.string.dx02_title); break;
            case SETTINGS: toolbar.setTitle(R.string.settings); break;
            case ABOUT: toolbar.setTitle(R.string.about); break;
            case SCOPE: toolbar.setTitle(R.string.scope); break;
            case GROUP_DETAIL:
                HookGroupCatalog.Group group = selectedGroup();
                toolbar.setTitle(group == null ? getString(R.string.ui_hooks) : groupName(group));
                break;
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
            case CAPTURE: capturePage(); break;
            case SETTINGS: settings(); break;
            case ABOUT:
                heading(R.string.about);
                card(getString(R.string.app_name), getString(R.string.about_detail));
                break;
            case SCOPE: scopePage(); break;
            case APP_DETAIL: appDetail(); break;
            case GROUP_DETAIL: groupDetail(); break;
            default: home();
        }
    }

}
