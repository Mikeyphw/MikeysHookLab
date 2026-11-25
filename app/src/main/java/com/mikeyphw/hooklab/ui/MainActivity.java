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
import com.mikeyphw.hooklab.ui.Ui07ScopeModel;
import com.mikeyphw.hooklab.ui.Ui07Identity;
import com.mikeyphw.hooklab.ui.HookHealthModel;
import com.mikeyphw.hooklab.diagnostics.CaptureSession;
import com.mikeyphw.hooklab.ui.HomeDashboardModel;
import com.mikeyphw.hooklab.core.HookContext;
import com.mikeyphw.hooklab.core.HookGroupCatalog;
import com.mikeyphw.hooklab.core.HookDetailRoute;
import com.mikeyphw.hooklab.core.TweakEngine;
import com.mikeyphw.hooklab.core.TweakCompatibility;
import com.mikeyphw.hooklab.core.TweakSpec;
import com.mikeyphw.hooklab.mcc.MccIds;
import com.mikeyphw.hooklab.mcc.MccControlActivity;
import android.content.Intent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Framework-truth UI: no fake activation or optimistic preference writes. */
public final class MainActivity extends AppCompatActivity {
    private LinearLayout body;
    private ScrollView scroll;
    private SharedPreferences appearance;
    private static final int HOME = 0, APPS = 1, DIAGNOSTICS = 2;
    private static final int SETTINGS = 3, ABOUT = 4, SCOPE = 5, APP_DETAIL = 6, GROUP_DETAIL = 7, CAPTURE = 8, HELP = 9, HEALTH = 10, HEALTH_REPORT = 11, EVENT_REPORT = 12;
    private static final int MENU_SETTINGS = 100, MENU_SCOPE = 101, MENU_ABOUT = 102;
    private static final int NAV_HOME = 201, NAV_APPS = 202, NAV_DIAGNOSTICS = 203;
    private static final int THEME_AMOLED = 100;
    private int page;
    private static final String ACTION_OPEN_HOOK = "com.mikeyphw.hooklab.action.OPEN_HOOK";
    private String selectedHookId;
    private int helpReturnPage=SETTINGS;
    private String healthFocusHookId;
    private int diagnosticTab=DIAGNOSTICS, healthReturnPage=HEALTH, eventReturnPage=DIAGNOSTICS;
    private DiagnosticWorkspaceState diagnosticState;
    private int diagnosticIndex(int route){return route==HEALTH?1:route==CAPTURE?2:0;}
    private boolean diagnosticRoot(int route){return route==DIAGNOSTICS||route==HEALTH||route==CAPTURE;}
    private boolean diagnosticRoute(int route){return diagnosticRoot(route)||route==HEALTH_REPORT||route==EVENT_REPORT;}
    private final Runnable diagnosticObserver=()->{
        if(!isFinishing()&&!isDestroyed()&&diagnosticRoute(page)){
            int previous=scroll.getScrollY();render();scroll.post(()->scroll.scrollTo(0,previous));
        }
    };
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
        TextView titleView = text(title, 20);
        FlatUi.sectionHeading(titleView);
        inner.addView(titleView);
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
        for (TweakEngine.Entry<HookContext> entry : environment.entries())
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
            for (HookGroupCatalog.Group group : HookGroupCatalog.groups(environment.all())) {
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
                    new java.util.TreeMap<>(HookGroupCatalog.byPackage(environment.all()));
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
    private LinearLayout diagnosticRows;
    private static final int DX01_PAGE_SIZE = 30;
    private final androidx.activity.result.ActivityResultLauncher<String[]> diagnosticImportLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
                    this::importDiagnosticDocument);
    private final androidx.activity.result.ActivityResultLauncher<String> diagnosticExportLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
                    this::exportDiagnosticDocument);

    // DX-02: explicit bounded reproduction. Only SAF imported files can add events.
    private static final int DX02_MAX_CHOICES=32;
    private final android.os.Handler captureDeadlineHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable captureDeadlineRefresh = () -> {
        if(isFinishing()||isDestroyed())return;
        CaptureSession current=captureApp().capture();
        if(current!=null && current.expireIfNeeded(captureClock(),CaptureSession.latestOrdinal(diagnosticsStore()))!=CaptureSession.State.ACTIVE)captureApp().closeCapture();
        if(page==CAPTURE)render();
        else scheduleCaptureDeadline();
    };
    private void scheduleCaptureDeadline(){
        captureDeadlineHandler.removeCallbacks(captureDeadlineRefresh);
        CaptureSession active=captureApp().capture();
        if(active==null)return;
        long now=captureClock();
        if(active.expireIfNeeded(now,CaptureSession.latestOrdinal(diagnosticsStore()))!=CaptureSession.State.ACTIVE){captureApp().closeCapture();return;}
        captureDeadlineHandler.postDelayed(captureDeadlineRefresh,Math.max(1,CaptureSession.MAX_DURATION_MS-active.elapsed(now)));
    }
    private final androidx.activity.result.ActivityResultLauncher<String> captureExportLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
                    this::exportCaptureDocument);
    private HookLabApp captureApp(){return (HookLabApp)getApplication();}
    private long captureClock(){return environment.elapsedRealtime();}
    private void selectCaptureSource(TweakSpec hook,String researchPackage){
        final String[] ids={"LSPOSED_MODULE","LSPOSED_FRAMEWORK","ANDROID_LOGCAT_IMPORT"};
        final String[] labels={getString(R.string.dx01_module),getString(R.string.dx01_framework),
                getString(R.string.dx01_logcat_import)};
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.dx02_choose_source)
            .setItems(labels,(dialog,which) -> {
                if(which<0 || which>=ids.length)return;
                com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline timeline=diagnosticsStore();
                long mark=com.mikeyphw.hooklab.diagnostics.CaptureSession.latestOrdinal(timeline);
                long elapsed=captureClock(),wall=environment.wallTime();
                com.mikeyphw.hooklab.diagnostics.CaptureSession next;
                try {
                    next=hook!=null
                        ? com.mikeyphw.hooklab.diagnostics.CaptureSession.forHook(hook,ids[which],elapsed,wall,mark,timeline.dropped())
                        : com.mikeyphw.hooklab.diagnostics.CaptureSession.forResearch(researchPackage,ids[which],elapsed,wall,mark,timeline.dropped());
                }catch(IllegalArgumentException rejected){toast(R.string.dx02_target_invalid);return;}
                if(captureApp().captureImportRunning()||!captureApp().startCapture(next)){toast(R.string.dx02_active_exists);return;}
                // Starting or replacing a capture changes the current page model.
                // A same-page navigation normally does nothing; explicitly render this transition.
                goTo(CAPTURE, true);
            }).show();
    }
    private void selectCaptureTarget(){
        final List<TweakSpec> specs=environment.all();
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
                        if(environment.knowsPackage(pkg)){
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
    private void previewCaptureExport(CaptureSession.Report report){
        if(diagnosticState.pickerPending()){toast(R.string.r01_picker_pending);return;}
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.r01_redaction_preview)
            .setMessage(getString(R.string.r01_capture_export_preview,report.observed,report.missingDueToRetention))
            .setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(R.string.dx02_export,(d,w)->{
                if(diagnosticState.pickerPending())return;
                diagnosticState.pendingCaptureExport=report;
                try{captureExportLauncher.launch("hooklab-capture-redacted.zip");}
                catch(RuntimeException failed){diagnosticState.pendingCaptureExport=null;toast(R.string.dx02_export_failed);}
            }).show();
    }
    private void exportCaptureDocument(android.net.Uri uri){
        final CaptureSession.Report report=diagnosticState.pendingCaptureExport;
        diagnosticState.pendingCaptureExport=null;
        if(uri==null)return;
        if(report==null){toast(R.string.r01_stale_request);return;}
        captureApp().diagnosticTask(() -> {
            try {com.mikeyphw.hooklab.diagnostics.CaptureSaf.export(getContentResolver(),uri,report);
                diagnosticNotice(R.string.dx02_exported);}
            catch(java.io.IOException | RuntimeException rejected){diagnosticNotice(R.string.dx02_export_failed);}
        });
    }
    private void capturePage(){
        diagnosticHeader(CAPTURE);
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
        scheduleCaptureDeadline();
        if(state==com.mikeyphw.hooklab.diagnostics.CaptureSession.State.ACTIVE){
            button(header,R.string.dx02_import,v -> {
                if(captureApp().captureImportRunning()){toast(R.string.dx02_import_pending);return;}
                launchDiagnosticImport(capture.source,capture);
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
            for(CaptureSession.Marker marker:capture.markers())
                body.addView(text(getString(R.string.r01_marker, marker.code.name(),marker.elapsedMillis/1000),14));
            button(header,R.string.dx02_export,v -> previewCaptureExport(report));
        }else card(getString(R.string.dx02_cancel),getString(R.string.dx02_cancelled));
        if(state!=CaptureSession.State.ACTIVE)button(card(getString(R.string.dx02_next),getString(R.string.dx02_post_capture)),
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
    private void diagnosticNotice(int message){
        // Application-context notice survives a configuration change; no raw data.
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->
            Toast.makeText(getApplicationContext(),message,Toast.LENGTH_SHORT).show());
    }
    private void launchDiagnosticImport(String source,CaptureSession capture){
        if(diagnosticState.pickerPending()){toast(R.string.r01_picker_pending);return;}
        if(capture!=null && (capture!=captureApp().capture()||!capture.canImport(captureClock(),CaptureSession.latestOrdinal(diagnosticsStore()))
                ||!captureApp().beginCaptureImport())){toast(R.string.dx02_import_pending);return;}
        diagnosticState.pendingImport=new DiagnosticWorkspaceState.ImportRequest(source,capture);
        try{diagnosticImportLauncher.launch(new String[]{"text/plain","application/octet-stream"});}
        catch(RuntimeException failed){diagnosticState.pendingImport=null;if(capture!=null)captureApp().endCaptureImport();toast(R.string.dx01_import_failed);}
    }
    private void importDiagnosticDocument(android.net.Uri uri){
        final DiagnosticWorkspaceState.ImportRequest request=diagnosticState.pendingImport;
        diagnosticState.pendingImport=null;
        if(request==null){if(uri!=null)toast(R.string.r01_stale_request);return;}
        final CaptureSession current=request.capture;
        if(uri==null){if(current!=null)captureApp().endCaptureImport();return;}
        final HookLabApp app=captureApp();
        final DiagnosticTimeline store=diagnosticsStore();
        final android.content.ContentResolver resolver=getContentResolver();
        app.diagnosticTask(()->{
            try{
                DiagnosticTimeline.ImportReceipt receipt;
                // The application-owned worker serializes all production imports.
                // Do not hold the timeline lock while acquiring a capture-session lock.
                {
                    if(current!=null && (current!=app.capture()||!current.canImport(captureClock(),CaptureSession.latestOrdinal(store)))){
                        if(current==app.capture()&&current.state()!=CaptureSession.State.ACTIVE)app.closeCapture();throw new IllegalStateException("Stale or full capture");
                    }
                    long before=CaptureSession.latestOrdinal(store);
                    receipt=com.mikeyphw.hooklab.diagnostics.DiagnosticSaf.importDocument(resolver,uri,store,request.source);
                    if(current!=null && !current.registerImport(request.source,before,
                            CaptureSession.latestOrdinal(store),captureClock())){
                        app.closeCapture();diagnosticNotice(R.string.r01_import_not_attributed);
                    }
                }
                final int count=receipt.accepted;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
                    diagnosticState.pageIndex=0;
                    Toast.makeText(app,getString(R.string.dx01_import_success,count),Toast.LENGTH_SHORT).show();
                });
            }catch(java.io.IOException|RuntimeException refused){diagnosticNotice(R.string.dx01_import_failed);}
            finally{if(current!=null)app.endCaptureImport();app.diagnosticChanged();}
        });
    }
    private void previewDiagnosticSelection(DiagnosticTimeline.Selection selection,boolean export){
        if(selection==null||selection.events.isEmpty()){toast(R.string.dx01_no_imports);return;}
        if(diagnosticState.pickerPending()){toast(R.string.r01_picker_pending);return;}
        String preview=getString(R.string.r01_selection_preview,selection.events.size(),selection.dropped);
        DiagnosticTimeline.Event first=selection.events.get(0);
        preview+="\n\n"+first.code+" · "+first.source+"\n"+first.message;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.r01_redaction_preview).setMessage(preview)
            .setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(export?R.string.dx01_export_redacted:R.string.r01_copy,(d,w)->{
                if(!export){copyDiagnosticText(selection.jsonl());return;}
                if(diagnosticState.pickerPending())return;
                diagnosticState.pendingExport=selection;
                try{diagnosticExportLauncher.launch("hooklab-redacted-diagnostics.zip");}
                catch(RuntimeException refused){diagnosticState.pendingExport=null;toast(R.string.dx01_export_failed);}
            }).show();
    }
    private void exportDiagnosticDocument(android.net.Uri uri){
        final DiagnosticTimeline.Selection selected=diagnosticState.pendingExport;
        final HookHealthModel.Report health=diagnosticState.pendingHealthExport;
        diagnosticState.pendingExport=null;diagnosticState.pendingHealthExport=null;
        if(uri==null)return;
        if((selected==null)==(health==null)){toast(R.string.r01_stale_request);return;}
        captureApp().diagnosticTask(()->{
            try{
                if(selected!=null)com.mikeyphw.hooklab.diagnostics.DiagnosticSaf.exportRedactedZip(getContentResolver(),uri,selected.events,selected.dropped);
                else com.mikeyphw.hooklab.diagnostics.DiagnosticSaf.exportHealthZip(getContentResolver(),uri,health);
                diagnosticNotice(R.string.dx01_export_success);
            }
            catch(java.io.IOException|RuntimeException failed){diagnosticNotice(R.string.dx01_export_failed);}
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
    private void showDiagnosticDetail(String id){
        if(diagnosticsStore().find(id)==null){toast(R.string.r01_stale_event);return;}
        diagnosticState.eventId=id;eventReturnPage=page==HEALTH_REPORT?HEALTH_REPORT:DIAGNOSTICS;
        goTo(EVENT_REPORT);
    }
    private void eventReport(){
        DiagnosticTimeline.Event e=diagnosticsStore().find(diagnosticState.eventId);
        heading(R.string.r01_event_inspector);
        if(e==null){body.addView(text(getString(R.string.r01_stale_event),16));return;}
        LinearLayout report=card(e.code,e.severity+" · "+e.source+" · "+getString(R.string.r01_import_qualifier));
        report.addView(text(getString(R.string.dx01_inspector,e.source,e.collector,e.fileSha256,e.lineNumber,
                e.timestamp.isEmpty()?getString(R.string.dx01_clock_unknown):e.timestamp,e.correlation,e.message),14));
        report.addView(text(e.id(),13));
        button(report,R.string.dx01_copy_event,v->previewDiagnosticSelection(diagnosticsStore().selectEvent(e.id()),false));
        button(report,R.string.dx01_export_redacted,v->previewDiagnosticSelection(diagnosticsStore().selectEvent(e.id()),true));
    }
    private LinearLayout compactDiagnosticRow(LinearLayout parent,CharSequence title,CharSequence detail,String pkg,View.OnClickListener action){
        LinearLayout row=horizontal();row.setPadding(dp(12),dp(10),dp(12),dp(10));
        if(pkg!=null&&!pkg.isEmpty())row.addView(hostIcon(pkg));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        TextView name=text(title,16);labels.addView(name);labels.addView(text(detail,13));
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        FlatUi.actionRow(row,action);parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
        return row;
    }
    private void renderDiagnosticRows(){
        if(diagnosticRows==null||page!=DIAGNOSTICS)return;
        diagnosticRows.removeAllViews();DiagnosticTimeline store=diagnosticsStore();
        DiagnosticTimeline.Selection projection=store.select(diagnosticState.filter);
        List<DiagnosticTimeline.Event> visible=projection.events;
        diagnosticRows.addView(text(getString(R.string.dx01_event_counts,visible.size(),projection.total,projection.dropped),14));
        if(visible.isEmpty()){diagnosticState.pageIndex=0;diagnosticRows.addView(text(getString(R.string.dx01_no_imports),16));return;}
        int pages=(visible.size()+DX01_PAGE_SIZE-1)/DX01_PAGE_SIZE;
        diagnosticState.pageIndex=Math.max(0,Math.min(diagnosticState.pageIndex,pages-1));
        int begin=diagnosticState.pageIndex*DX01_PAGE_SIZE,end=Math.min(visible.size(),begin+DX01_PAGE_SIZE);
        for(int i=begin;i<end;i++){
            DiagnosticTimeline.Event e=visible.get(i);
            compactDiagnosticRow(diagnosticRows,e.code,(e.timestamp.isEmpty()?getString(R.string.dx01_clock_unknown):e.timestamp)
                +" · "+e.severity+" · "+e.source+" · "+getString(R.string.r01_import_qualifier),null,v->showDiagnosticDetail(e.id()));
        }
        LinearLayout pagination=horizontal();
        MaterialButton previous=new MaterialButton(this);previous.setText(R.string.dx01_previous);FlatUi.button(previous);
        previous.setEnabled(diagnosticState.pageIndex>0);previous.setOnClickListener(v->{diagnosticState.pageIndex--;renderDiagnosticRows();scroll.smoothScrollTo(0,0);});
        pagination.addView(previous,new LinearLayout.LayoutParams(0,dp(48),1));
        pagination.addView(text(getString(R.string.r01_page,diagnosticState.pageIndex+1,pages),13));
        MaterialButton next=new MaterialButton(this);next.setText(R.string.dx01_next);FlatUi.button(next);
        next.setEnabled(diagnosticState.pageIndex<pages-1);next.setOnClickListener(v->{diagnosticState.pageIndex++;renderDiagnosticRows();scroll.smoothScrollTo(0,0);});
        pagination.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));diagnosticRows.addView(pagination);
    }
    private void diagnosticHeader(int current){
        heading(R.string.diagnostics);
        ChipGroup tabs=new ChipGroup(this);tabs.setSingleSelection(true);tabs.setSelectionRequired(true);
        final int[] routes={DIAGNOSTICS,HEALTH,CAPTURE};final int[] labels={R.string.r01_timeline,R.string.r01_health,R.string.dx02_capture};
        for(int i=0;i<routes.length;i++){
            int route=routes[i];Chip chip=new Chip(this);chip.setId(View.generateViewId());chip.setText(labels[i]);
            chip.setCheckable(true);FlatUi.chip(chip);tabs.addView(chip);chip.setChecked(current==route);
            chip.setOnClickListener(v->goTo(route));
        }
        body.addView(tabs,new LinearLayout.LayoutParams(-1,-2));
    }
    private android.widget.EditText filterInput(LinearLayout parent,int label,String initial){
        TextView heading=text(getString(label),14);parent.addView(heading);
        android.widget.EditText edit=new android.widget.EditText(this);edit.setId(View.generateViewId());heading.setLabelFor(edit.getId());
        edit.setHint(label);edit.setText(initial);edit.setTextSize(14);edit.setSingleLine(true);edit.setMinHeight(dp(48));
        edit.setSaveEnabled(false);edit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(256)});
        parent.addView(edit,new LinearLayout.LayoutParams(-1,-2));return edit;
    }
    private android.widget.Spinner filterChoice(LinearLayout parent,int title,String[] labels,int selected){
        TextView label=text(getString(title),14);parent.addView(label);android.widget.Spinner spinner=new android.widget.Spinner(this);
        spinner.setId(View.generateViewId());label.setLabelFor(spinner.getId());spinner.setMinimumHeight(dp(48));
        android.widget.ArrayAdapter<String> adapter=new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0,selected));parent.addView(spinner,new LinearLayout.LayoutParams(-1,-2));return spinner;
    }
    private int choiceIndex(String[] ids,String key){for(int i=0;i<ids.length;i++)if(ids[i].equals(key))return i;return 0;}
    private String iso(long time){return time==0?"":java.time.Instant.ofEpochMilli(time).toString();}
    private long filterTime(android.widget.EditText edit){String value=edit.getText().toString().trim();if(value.isEmpty())return 0;
        long utc=java.time.Instant.parse(value).toEpochMilli();if(utc<=0)throw new IllegalArgumentException("clock");return utc;}
    private void showDiagnosticFilters(){
        final DiagnosticTimeline.Filter initial=diagnosticState.filter.copy();
        com.google.android.material.bottomsheet.BottomSheetDialog sheet=new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        ScrollView sc=new ScrollView(this);LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),dp(16),dp(18),dp(24));sc.addView(panel);sheet.setContentView(sc);
        TextView title=text(getString(R.string.r01_filters),20);FlatUi.sectionHeading(title);panel.addView(title);
        android.widget.EditText query=filterInput(panel,R.string.dx01_filter_search,initial.query);
        android.widget.EditText pkg=filterInput(panel,R.string.dx01_filter_package,initial.packageName);
        android.widget.EditText hook=filterInput(panel,R.string.dx01_filter_hook,initial.hookId);
        List<HookGroupCatalog.Group> groups=HookGroupCatalog.groups(environment.all());String[] groupIds=new String[groups.size()+1], groupLabels=new String[groupIds.length];
        groupIds[0]="";groupLabels[0]=getString(R.string.dx01_all);for(int i=0;i<groups.size();i++){groupIds[i+1]=groups.get(i).id;groupLabels[i+1]=groupName(groups.get(i));}
        android.widget.Spinner group=filterChoice(panel,R.string.r01_filter_group,groupLabels,choiceIndex(groupIds,diagnosticState.groupId));
        String[] sourceIds={"","LSPOSED_MODULE","LSPOSED_FRAMEWORK","ANDROID_LOGCAT_IMPORT"};
        android.widget.Spinner source=filterChoice(panel,R.string.r01_filter_source,new String[]{getString(R.string.dx01_all),getString(R.string.dx01_module),getString(R.string.dx01_framework),getString(R.string.dx01_logcat_import)},choiceIndex(sourceIds,initial.source));
        String[] severityIds={"","INFO","WARN","ERROR"};android.widget.Spinner severity=filterChoice(panel,R.string.r01_filter_severity,new String[]{getString(R.string.dx01_all),"INFO","WARN","ERROR"},choiceIndex(severityIds,initial.severity));
        android.widget.EditText after=filterInput(panel,R.string.r01_after,iso(initial.afterUtc)),before=filterInput(panel,R.string.r01_before,iso(initial.beforeUtc));
        panel.addView(text(getString(R.string.r01_time_help),13));
        button(panel,R.string.r01_apply,v->{
            DiagnosticTimeline.Filter next=new DiagnosticTimeline.Filter();next.query=query.getText().toString();next.packageName=pkg.getText().toString().trim();next.hookId=hook.getText().toString().trim();
            int sourceIndex=source.getSelectedItemPosition(), severityIndex=severity.getSelectedItemPosition(),g=group.getSelectedItemPosition();
            if(sourceIndex<0||sourceIndex>=sourceIds.length||severityIndex<0||severityIndex>=severityIds.length||g<0||g>=groupIds.length)return;
            next.source=sourceIds[sourceIndex];next.severity=severityIds[severityIndex];
            if(g>0){Set<String> members=new HashSet<>();for(TweakSpec spec:groups.get(g-1).members)members.add(spec.id);next.groupHooks=java.util.Collections.unmodifiableSet(members);}
            try{next.afterUtc=filterTime(after);next.beforeUtc=filterTime(before);if(next.afterUtc>0&&next.beforeUtc>0&&next.afterUtc>next.beforeUtc)throw new IllegalArgumentException("range");}
            catch(RuntimeException invalid){after.setError(getString(R.string.r01_bad_time));return;}
            diagnosticState.groupId=groupIds[g];diagnosticState.filter=next;diagnosticState.pageIndex=0;sheet.dismiss();render();scroll.scrollTo(0,0);
        });
        button(panel,R.string.r01_clear,v->{diagnosticState.filter=new DiagnosticTimeline.Filter();diagnosticState.groupId="";diagnosticState.pageIndex=0;sheet.dismiss();render();scroll.scrollTo(0,0);});
        button(panel,android.R.string.cancel,v->sheet.dismiss());sheet.show();
        sheet.getBehavior().setSkipCollapsed(true);sheet.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
    }
    private void showImportSheet(){
        if(diagnosticState.pickerPending()){toast(R.string.r01_picker_pending);return;}
        com.google.android.material.bottomsheet.BottomSheetDialog sheet=new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        ScrollView sc=new ScrollView(this);LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),dp(16),dp(18),dp(24));sc.addView(panel);sheet.setContentView(sc);
        TextView title=text(getString(R.string.dx01_choose_source),20);FlatUi.sectionHeading(title);panel.addView(title);
        final String[] types={"LSPOSED_MODULE","LSPOSED_FRAMEWORK","ANDROID_LOGCAT_IMPORT"};final int[] labels={R.string.dx01_module,R.string.dx01_framework,R.string.dx01_logcat_import};
        for(int i=0;i<types.length;i++){String type=types[i];button(panel,labels[i],v->{sheet.dismiss();launchDiagnosticImport(type,null);});}
        button(panel,android.R.string.cancel,v->sheet.dismiss());sheet.show();
        sheet.getBehavior().setSkipCollapsed(true);sheet.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
    }
    private void diagnosticsTimelinePage(){
        body.addView(text(getString(R.string.dx01_import_only),14));
        LinearLayout actions=horizontal();MaterialButton imp=new MaterialButton(this);imp.setText(R.string.dx01_import);FlatUi.button(imp);imp.setOnClickListener(v->showImportSheet());actions.addView(imp,new LinearLayout.LayoutParams(0,-2,1));
        MaterialButton filter=new MaterialButton(this);filter.setText(getString(R.string.r01_filter_count,diagnosticState.filter.activeCount()));FlatUi.button(filter);filter.setOnClickListener(v->showDiagnosticFilters());actions.addView(filter,new LinearLayout.LayoutParams(0,-2,1));body.addView(actions);
        LinearLayout exports=horizontal();MaterialButton copy=new MaterialButton(this);copy.setText(R.string.r01_copy);FlatUi.button(copy);copy.setOnClickListener(v->previewDiagnosticSelection(diagnosticsStore().select(diagnosticState.filter),false));exports.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        MaterialButton save=new MaterialButton(this);save.setText(R.string.r01_export);FlatUi.button(save);save.setOnClickListener(v->previewDiagnosticSelection(diagnosticsStore().select(diagnosticState.filter),true));exports.addView(save,new LinearLayout.LayoutParams(0,-2,1));body.addView(exports);
        diagnosticRows=new LinearLayout(this);diagnosticRows.setOrientation(LinearLayout.VERTICAL);body.addView(diagnosticRows,new LinearLayout.LayoutParams(-1,-2));renderDiagnosticRows();
    }

    private MaterialToolbar toolbar;
    private NavigationBarView navigation;
    private UiEnvironment environment;
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
        environment = UiEnvironmentFactory.create((HookLabApp) getApplication());
        diagnosticState=new androidx.lifecycle.ViewModelProvider(this).get(DiagnosticWorkspaceState.class);
        stateStore = new FrameworkStateStore(environment);
        page = state == null ? HOME : Math.max(HOME, Math.min(EVENT_REPORT, state.getInt("page")));
        selectedHookId = state == null ? null : state.getString("selectedHookId");
        helpReturnPage = state == null ? SETTINGS : state.getInt("helpReturnPage", SETTINGS);
        if(helpReturnPage != SETTINGS && helpReturnPage != SCOPE && helpReturnPage != ABOUT) helpReturnPage=SETTINGS;
        healthFocusHookId=state==null?null:state.getString("healthFocusHookId");
        diagnosticTab=state==null?DIAGNOSTICS:state.getInt("diagnosticTab",DIAGNOSTICS);
        if(!diagnosticRoot(diagnosticTab))diagnosticTab=DIAGNOSTICS;
        healthReturnPage=state==null?HEALTH:state.getInt("healthReturnPage",HEALTH);
        if(healthReturnPage!=HEALTH&&healthReturnPage!=APP_DETAIL)healthReturnPage=HEALTH;
        eventReturnPage=state==null?DIAGNOSTICS:state.getInt("eventReturnPage",DIAGNOSTICS);
        if(eventReturnPage!=DIAGNOSTICS&&eventReturnPage!=HEALTH_REPORT)eventReturnPage=DIAGNOSTICS;
        if(state!=null&&!diagnosticState.generation.equals(state.getString("diagnosticGeneration"))&&diagnosticRoute(page)){
            Toast.makeText(this,R.string.r01_process_loss,Toast.LENGTH_LONG).show();
        }
        if(page==EVENT_REPORT && diagnosticsStore().find(diagnosticState.eventId)==null)page=DIAGNOSTICS;
        if(page==HEALTH_REPORT&&entryForId(healthFocusHookId)==null)page=HEALTH;
        selectedGroupId = state == null ? null : state.getString("selectedGroupId");
        detailReturnPage = state == null ? APPS : state.getInt("detailReturnPage", APPS);
        if (detailReturnPage != APPS && detailReturnPage != HOME
                && detailReturnPage != GROUP_DETAIL && detailReturnPage != HEALTH_REPORT) detailReturnPage = APPS;
        // UI-08: a pre-UI-04 package-only selection is safe ONLY if unique.
        // Never pick the first hook when multiple registered hooks share one app.
        if (selectedHookId == null && state != null) {
            String oldPackage = state.getString("selectedPackage");
            selectedHookId = Ui08LegacySelection.resolveUnique(environment.all(),oldPackage);
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
                if (HookDetailRoute.hook(environment.all(), requested) != null) {
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
            if(target==DIAGNOSTICS)target=diagnosticTab;
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
        restoreDiagnosticScroll(page);
    }
    private void restoreDiagnosticScroll(int target){
        if(!diagnosticRoot(target))return;
        final int position=diagnosticState.scroll[diagnosticIndex(target)];
        scroll.post(()->{if(page==target)scroll.scrollTo(0,position);});
    }
    private void navigateBack() {
        if(page==HELP){goTo(helpReturnPage);return;}
        if(page==EVENT_REPORT){diagnosticState.eventId=null;goTo(eventReturnPage);return;}
        if(page==HEALTH_REPORT){goTo(healthReturnPage);return;}
        if (page == APP_DETAIL) {
            int destination = detailReturnPage == GROUP_DETAIL && selectedGroup() == null
                    ? APPS : detailReturnPage;
            goTo(destination);
        } else goTo(page == GROUP_DETAIL ? APPS : HOME);
    }
    private void goTo(int target) {
        goTo(target, false);
    }
    private void goTo(int target, boolean refreshSamePage) {
        if (target == page && !refreshSamePage) return;
        if(diagnosticRoot(page)&&page!=target)diagnosticState.scroll[diagnosticIndex(page)]=scroll.getScrollY();
        page = target;
        if(diagnosticRoot(target))diagnosticTab=target;
        render();
        scroll.scrollTo(0, 0);

        // UI-08: keyboard/TalkBack navigation begins at the new page heading.
        // Only user-directed navigation changes focus; asynchronous framework
        // snapshot updates keep their existing search-focus/scroll preservation.
        scroll.post(() -> {
            if (page != target || body == null || body.getChildCount() == 0) return;
            View first = body.getChildAt(0);
            if (first instanceof TextView) {
                first.setFocusableInTouchMode(true);
                first.requestFocus();
            }
            if(diagnosticRoot(target)&&!refreshSamePage)restoreDiagnosticScroll(target);
        });
    }
    @Override protected void onStart() {
        super.onStart();
        pendingPreferences.clear();
        pendingScope.clear();
        ((HookLabApp) getApplication()).addObserver(frameworkObserver);
        captureApp().addDiagnosticObserver(diagnosticObserver);
        scheduleCaptureDeadline();
        if(diagnosticRoute(page)){render();restoreDiagnosticScroll(page);}
        stateStore.start(next -> {
            if (isFinishing() || isDestroyed() || snapshot.equals(next)) return;
            int previousScroll = scroll.getScrollY();
            snapshot = next;
            // On the Hooks catalog, update only results so a framework status
            // refresh cannot steal search keyboard focus or cursor position.
            if (page == APPS && catalogResults != null) renderCatalogResults();
            else render();
            scroll.post(() -> { if(scroll.getScrollY()!=previousScroll)scroll.scrollTo(0, previousScroll); });
        });
    }
    @Override protected void onStop() {
        if(diagnosticRoot(page))diagnosticState.scroll[diagnosticIndex(page)]=scroll.getScrollY();
        captureDeadlineHandler.removeCallbacks(captureDeadlineRefresh);
        ((HookLabApp) getApplication()).removeObserver(frameworkObserver);
        captureApp().removeDiagnosticObserver(diagnosticObserver);
        stateStore.stop();
        super.onStop();
    }
    @Override protected void onDestroy() {
        // Only final destruction cancels a still-unreturned picker; a rotation retains its request.
        if(!isChangingConfigurations()&&diagnosticState.pendingImport!=null){
            if(diagnosticState.pendingImport.capture!=null)captureApp().endCaptureImport();diagnosticState.pendingImport=null;
        }
        stateStore.close();
        super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("page", page); out.putString("selectedHookId", selectedHookId);
        out.putInt("helpReturnPage", helpReturnPage);
        out.putString("healthFocusHookId", healthFocusHookId);
        out.putInt("diagnosticTab",diagnosticTab);out.putInt("healthReturnPage",healthReturnPage);out.putInt("eventReturnPage",eventReturnPage);
        out.putString("diagnosticGeneration",diagnosticState.generation);
        if(diagnosticRoot(page))diagnosticState.scroll[diagnosticIndex(page)]=scroll.getScrollY();
        out.putString("selectedGroupId", selectedGroupId);
        out.putInt("detailReturnPage", detailReturnPage);
        super.onSaveInstanceState(out);
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private UiEnvironment.Port service() { return environment.currentService(); }
    private boolean ready(UiEnvironment.Port s) {
        return s != null && s == service() && snapshot.ready() && stateStore.ownsReadySnapshot(s);
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
        TextView titleView = text(title, 20);
        FlatUi.sectionHeading(titleView);
        inner.addView(titleView);
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
    private String connection(UiEnvironment.Port s) {
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
    private Set<String> scopedPackages(UiEnvironment.Port s) {
        return ready(s) ? snapshot.scope : null;
    }
    private TweakEngine.Entry<HookContext> selectedEntry() {
        TweakSpec verified = HookDetailRoute.hook(environment.all(), selectedHookId);
        return verified == null ? null : entryForId(verified.id);
    }

    /** Framework-observable configuration only; never assert that a hook ran. */
    private TweakCompatibility.State eligibility(TweakSpec spec, UiEnvironment.Port s) {
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
        if(page==HEALTH_REPORT&&healthReturnPage==APP_DETAIL&&entry.spec.id.equals(selectedHookId)){goTo(APP_DETAIL);return;}
        detailReturnPage = page == GROUP_DETAIL ? GROUP_DETAIL : page == HOME ? HOME : page==HEALTH_REPORT?HEALTH_REPORT:APPS;
        selectedHookId = entry.spec.id;
        goTo(APP_DETAIL);
    }
    private void apps() {
        hooksCatalog();
    }



    private HookGroupCatalog.Group selectedGroup() {
        return HookDetailRoute.group(HookGroupCatalog.groups(environment.all()), selectedGroupId);
    }
    private void showGroup(HookGroupCatalog.Group group) {
        if (group == null || HookDetailRoute.group(
                HookGroupCatalog.groups(environment.all()), group.id) == null) return;
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
        UiEnvironment.Port expected = service();
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
        UiEnvironment.Port s = service();
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
        button(overview,R.string.dx03_view_health,v->openHookHealth(spec));
        HookGroupCatalog.Group owningGroup = HookDetailRoute.groupForHook(
                HookGroupCatalog.groups(environment.all()), spec.id);
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
                environment.all(), HookGroupCatalog.groups(environment.all()),
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
    private void saveTweak(UiEnvironment.Port s, String id, boolean enabled) {
        if (!ready(s) || !pendingPreferences.add(id)) return;
        render(); // Show pending state; no optimistic preference change.
        stateStore.writeTweak(s, id, enabled, success -> {
            pendingPreferences.remove(id);
            toast(success ? R.string.tweak_saved : R.string.tweak_failed);
        });
    }
    private void requestScope(UiEnvironment.Port s, String pkg) {
        if (!ready(s) || !environment.knowsPackage(pkg) || !pendingScope.add(pkg)) return;
        render();
        stateStore.requestScope(s, pkg, confirmed -> {
            pendingScope.remove(pkg);
            toast(confirmed ? R.string.scope_added : R.string.ui_scope_request_unconfirmed);
        });
    }
    private void removeScope(UiEnvironment.Port s, String pkg) {
        if (!ready(s) || !environment.knowsPackage(pkg) || pendingScope.contains(pkg)) return;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.scope_remove).setMessage(pkg)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (!ready(s) || !pendingScope.add(pkg)) return;
                    render();
                    stateStore.removeScope(s, pkg, requested -> {
                        pendingScope.remove(pkg);
                        toast(requested ? R.string.scope_removed : R.string.ui_scope_request_unconfirmed);
                    });
                }).show();
    }
    private String scopeRowStatus(Ui07ScopeModel.State state){
        switch(state){
            case APPROVED:return getString(R.string.ui_scope_approved);
            case NEEDS_APPROVAL:return getString(R.string.ui_scope_needed);
            case PENDING:return getString(R.string.ui_saving);
            case FRAMEWORK_UNAVAILABLE:return getString(R.string.scope_unavailable);
            default:return getString(R.string.ui_scope_unknown);
        }
    }
    /** UI-07 uses only the immutable framework snapshot; never infer missing scope. */
    private void scopePage() {
        heading(R.string.scope);
        UiEnvironment.Port expected=service();
        Ui07ScopeModel.Listing listing=Ui07ScopeModel.project(environment.all(),
                ready(expected),scopedPackages(expected),pendingScope);
        LinearLayout summary=card(getString(R.string.ui07_scope_heading),
                getString(R.string.ui07_scope_summary,
                listing.targets.size(),listing.reviewedApproved<0?getString(R.string.ui_unknown):
                    Integer.toString(listing.reviewedApproved)));
        summary.addView(text(getString(R.string.ui07_scope_warning),14));
        if(listing.otherApproved>0)
            summary.addView(text(getString(R.string.ui07_scope_other,listing.otherApproved),14));
        if(!ready(expected) || listing.reviewedApproved<0){
            summary.addView(text(getString(R.string.ui07_scope_connection_help),14));
            button(summary,R.string.ui07_open_help,v -> openHelp());
        }
        for(Ui07ScopeModel.Row row:listing.targets){
            LinearLayout target=card(row.packageName,scopeRowStatus(row.state));
            if(row.canRequest()){
                button(target,R.string.scope_add,v -> requestScope(expected,row.packageName));
            } else if(row.canRemove()){
                // The existing removeScope() confirmation is never bypassed.
                button(target,R.string.scope_remove,v -> removeScope(expected,row.packageName));
            }
            if(row.state==Ui07ScopeModel.State.PENDING)
                target.addView(text(getString(R.string.ui_saving),14));
        }
        LinearLayout next=card(getString(R.string.ui07_next_steps),
                getString(R.string.ui_restart_target));
        button(next,R.string.ui07_open_hooks,v -> goTo(APPS));
        button(next,R.string.ui07_open_help,v -> openHelp());
    }
    /** Framework-truth diagnostics. Per-process events live only in Xposed logs. */
    /** DX-03: immutable framework truth plus sanitized imported evidence only. */
    private String healthAxis(HookHealthModel.Axis value) {
        switch(value){
            case OK: return getString(R.string.dx03_axis_ok);
            case PROBLEM: return getString(R.string.dx03_axis_problem);
            case OPTED_OUT: return getString(R.string.dx03_axis_opted_out);
            default: return getString(R.string.ui_unknown);
        }
    }
    private String healthAdvice(HookHealthModel.Advice value) {
        switch(value){
            case CONNECT_FRAMEWORK: return getString(R.string.dx03_advice_framework);
            case REVIEW_SCOPE: return getString(R.string.dx03_advice_scope);
            case INSPECT_SAFETY_LOCK: return getString(R.string.dx03_advice_safety);
            case ENABLE_OPT_IN: return getString(R.string.dx03_advice_optin);
            case VERIFY_TARGET_VERSION: return getString(R.string.dx03_advice_version);
            case IMPORT_EVIDENCE: return getString(R.string.dx03_advice_import);
            case INSPECT_REPORTED_ERRORS: return getString(R.string.dx03_advice_errors);
            default: return getString(R.string.dx03_advice_review);
        }
    }
    private String healthGap(String value) {
        switch(value){
            case "NO_IMPORTED_SOURCE": return getString(R.string.dx03_gap_no_source);
            case "NO_EXACT_HOOK_ASSOCIATION": return getString(R.string.dx03_gap_unattributed);
            case "EVIDENCE_RETAINED_WITH_EVICTIONS": return getString(R.string.dx03_gap_evictions);
            default: return getString(R.string.dx03_gap_imported);
        }
    }
    private void openHookHealth(TweakSpec spec){
        if(spec==null||entryForId(spec.id)==null)return;
        healthFocusHookId=spec.id;
        if(page==APP_DETAIL&&detailReturnPage==HEALTH_REPORT){goTo(HEALTH_REPORT);return;}
        healthReturnPage=page==APP_DETAIL?APP_DETAIL:HEALTH;goTo(HEALTH_REPORT);
    }
    private void hookHealthPanel(){
        body.addView(text(getString(R.string.dx03_truth),14));
        int globalUnknown=0;for(DiagnosticTimeline.Event event:diagnosticsStore().filtered(null))if("ERROR".equals(event.severity)&&event.hookId.isEmpty())globalUnknown++;
        body.addView(text(getString(R.string.dx03_global_unattributed,globalUnknown,diagnosticsStore().size(),diagnosticsStore().dropped()),13));
        for(TweakEngine.Entry<HookContext> entry:environment.entries()){
            HookHealthModel.Report report=HookHealthModel.project(entry.spec,snapshot,diagnosticsStore());
            compactDiagnosticRow(body,entry.title,statusText(report.eligibility)+" · "+getString(R.string.r01_health_counts,report.matchingImports,report.reportedErrors),entry.spec.packageName,v->openHookHealth(entry.spec));
        }
    }
    private void healthReportPage(){
        heading(R.string.dx03_title);TweakEngine.Entry<HookContext> entry=entryForId(healthFocusHookId);
        if(entry==null){body.addView(text(getString(R.string.dx03_invalid_selection),14));return;}
        TweakSpec spec=entry.spec;HookHealthModel.Report report=HookHealthModel.project(spec,snapshot,diagnosticsStore());
        LinearLayout details=card(entry.title,getString(R.string.dx03_truth));
        details.addView(text(getString(R.string.dx03_dimensions,healthAxis(report.framework),healthAxis(report.scope),healthAxis(report.optIn),healthAxis(report.quarantine),healthAxis(report.version),statusText(report.eligibility)),14));
        details.addView(text(getString(R.string.dx03_source_summary,report.matchingImports,report.reportedErrors,report.reportedWarnings,report.sourceSummary.isEmpty()?getString(R.string.dx03_none):report.sourceSummary,healthGap(report.collectorGap),report.retainedDropped),14));
        details.addView(text(getString(R.string.dx03_advice,healthAdvice(report.advice)),14));
        for(DiagnosticTimeline.Event event:report.recent)compactDiagnosticRow(details,event.code,getString(R.string.dx03_event_source,event.code,event.source,event.fileSha256,event.lineNumber),null,v->showDiagnosticDetail(event.id()));
        button(details,R.string.dx03_open_setup,v->showApp(entry));
        button(details,R.string.dx03_inspect,v->{diagnosticState.filter=new DiagnosticTimeline.Filter();diagnosticState.filter.hookId=spec.id;diagnosticState.groupId="";diagnosticState.pageIndex=0;diagnosticState.scroll[0]=0;goTo(DIAGNOSTICS);});
        button(details,R.string.dx03_capture,v->goTo(CAPTURE));
        button(details,R.string.dx03_copy,v->previewHealthReport(report,false));
        button(details,R.string.r01_export_health,v->previewHealthReport(report,true));
        UiEnvironment.Port port=service();FrameworkSnapshot.Flag flag=snapshot.flag(spec.id);
        if(ready(port)&&flag!=null&&flag.enabled!=null&&flag.locked!=null){
            boolean lock=!flag.locked;button(details,lock?R.string.diagnostics_enable_lock:R.string.diagnostics_reset_quarantine,v->confirmSafetyLock(port,spec.id,lock)).setEnabled(!pendingPreferences.contains("lock:"+spec.id));
        }else details.addView(text(getString(R.string.diagnostics_unavailable),14));
        details.addView(text(getString(R.string.diagnostics_note),14));
    }
    private void previewHealthReport(HookHealthModel.Report report,boolean export){
        if(diagnosticState.pickerPending()){toast(R.string.r01_picker_pending);return;}
        final String redacted=HookHealthModel.redactedJson(report);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.r01_redaction_preview).setMessage(redacted)
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(export?R.string.r01_export:R.string.r01_copy,(d,w)->{
                if(!export){copyDiagnosticText(redacted);return;}
                if(diagnosticState.pickerPending())return;
                diagnosticState.pendingHealthExport=report;
                try{diagnosticExportLauncher.launch("hooklab-health-redacted.zip");}
                catch(RuntimeException failed){diagnosticState.pendingHealthExport=null;toast(R.string.dx01_export_failed);}
            }).show();
    }
    private void diagnosticsPage(){diagnosticHeader(DIAGNOSTICS);diagnosticsTimelinePage();}
    private void healthPage(){diagnosticHeader(HEALTH);hookHealthPanel();}

    private void confirmSafetyLock(UiEnvironment.Port s, String id, boolean locked) {
        if (!ready(s) || pendingPreferences.contains("lock:" + id)) return;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(locked ? R.string.diagnostics_enable_lock : R.string.diagnostics_reset_quarantine)
                .setMessage(locked ? R.string.diagnostics_lock_confirm : R.string.diagnostics_reset_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (!ready(s) || !pendingPreferences.add("lock:" + id)) return;
                    render();
                    stateStore.writeSafetyLock(s, id, locked, success -> {
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
    private void writeVerboseLogging(UiEnvironment.Port expected, boolean value) {
        if (!ready(expected) || !pendingPreferences.add("verbose")) return;
        render();
        stateStore.writeVerbose(expected, value, success -> {
            pendingPreferences.remove("verbose");
            toast(success ? R.string.verbose_lsposed_saved : R.string.tweak_failed);
        });
    }
    /** UI-07: installed-package and signer identity, not compile-time claims. */
    private void aboutPage(){
        heading(R.string.about);
        String version=getString(R.string.ui07_identity_unavailable);
        String signer=getString(R.string.ui07_identity_unavailable);
        try{
            android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(
                    getPackageName(),android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
            version=Ui07Identity.versionDisplay(info.versionName,info.getLongVersionCode());
            if(info.signingInfo!=null){
                android.content.pm.Signature[] certificates=info.signingInfo.getApkContentsSigners();
                if(certificates!=null && certificates.length==1){
                    String digest=Ui07Identity.signerFingerprint(certificates[0].toByteArray());
                    if(!digest.isEmpty())signer=digest;
                }
            }
        }catch(android.content.pm.PackageManager.NameNotFoundException | RuntimeException error){
            // Missing identity is not reported as verification success.
        }
        LinearLayout identity=card(getString(R.string.ui07_about_identity),
                getString(R.string.ui07_app_identity,getPackageName(),version));
        identity.addView(text(getString(R.string.ui07_signer_fingerprint,signer),14));
        card(getString(R.string.ui07_framework_contract),
                getString(R.string.ui07_framework_help,connection(service())));
        card(getString(R.string.ui07_privacy_heading),getString(R.string.ui07_privacy_note));
        LinearLayout next=card(getString(R.string.ui07_next_steps),
                getString(R.string.ui07_about_limitations));
        button(next,R.string.ui07_open_help,v -> openHelp());
        button(next,R.string.ui07_open_diagnostics,v -> goTo(DIAGNOSTICS));
    }
    private void openHelp(){
        helpReturnPage=page==HELP?SETTINGS:page;
        goTo(HELP);
    }
    private void helpPage(){
        heading(R.string.ui07_help_title);
        card(getString(R.string.ui07_help_setup_title),getString(R.string.ui07_help_setup_body));
        card(getString(R.string.ui07_help_scope_title),getString(R.string.ui07_help_scope_body));
        card(getString(R.string.ui07_help_mcc_title),getString(R.string.ui07_help_mcc_body));
        card(getString(R.string.ui07_help_logs_title),getString(R.string.ui07_help_logs_body));
        card(getString(R.string.ui07_help_privacy_title),getString(R.string.ui07_help_privacy_body));
        LinearLayout actions=card(getString(R.string.ui07_next_steps),getString(R.string.ui07_help_action_warning));
        button(actions,R.string.ui07_open_hooks,v -> goTo(APPS));
        button(actions,R.string.ui07_open_scope,v -> goTo(SCOPE));
        button(actions,R.string.ui07_open_diagnostics,v -> goTo(DIAGNOSTICS));
    }
    private void settings() {
        heading(R.string.settings);
        LinearLayout appearanceCard=card(getString(R.string.appearance),
                getString(R.string.ui_theme_description));
        ChipGroup themes=new ChipGroup(this);
        themes.setSingleSelection(true);
        int current=appearance.getInt("night",AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        themeChip(themes,R.string.system_theme,AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,current);
        themeChip(themes,R.string.light_theme,AppCompatDelegate.MODE_NIGHT_NO,current);
        themeChip(themes,R.string.dark_theme,AppCompatDelegate.MODE_NIGHT_YES,current);
        themeChip(themes,R.string.ui_amoled_theme,THEME_AMOLED,current);
        appearanceCard.addView(themes);
        appearanceCard.addView(text(getString(R.string.ui07_appearance_scope),14));
        if(Build.VERSION.SDK_INT>=31){
            MaterialSwitch dynamic=new MaterialSwitch(this);
            dynamic.setText(R.string.ui_dynamic_colors);
            dynamic.setChecked(appearance.getBoolean("dynamicColors",true));
            dynamic.setEnabled(current!=THEME_AMOLED);
            dynamic.setOnCheckedChangeListener((view,wanted)->{
                appearance.edit().putBoolean("dynamicColors",wanted).apply();
                recreate();
            });
            appearanceCard.addView(dynamic);
            appearanceCard.addView(text(getString(R.string.ui_dynamic_colors_detail),14));
        }
        LinearLayout framework=card(getString(R.string.ui07_framework_section),
                connection(service())+"\n"+getString(R.string.ui07_framework_warning));
        button(framework,R.string.ui07_open_scope,v -> goTo(SCOPE));
        button(framework,R.string.ui07_open_hooks,v -> goTo(APPS));
        verboseLogging(); // Existing framework-owned preference + verified commit/readback.
        LinearLayout termux=card(getString(R.string.termux_r05_settings_label),
                getString(R.string.termux_r05_settings_description));
        button(termux,R.string.termux_r05_settings_label,v -> startActivity(
                new Intent(this,com.mikeyphw.hooklab.termux.TermuxBridgeActivity.class)
                    .setAction(com.mikeyphw.hooklab.termux.TermuxCommandPolicy.ACTION_SETTINGS)));
        LinearLayout support=card(getString(R.string.ui07_help_title),
                getString(R.string.ui07_settings_support));
        button(support,R.string.ui07_open_help,v -> openHelp());
        button(support,R.string.about,v -> goTo(ABOUT));
    }
    private void render() {
        if (body == null) return;
        int navId = page == APP_DETAIL || page == APPS || page == GROUP_DETAIL ? NAV_APPS
                : diagnosticRoute(page) ? NAV_DIAGNOSTICS : NAV_HOME;
        navigation.getMenu().findItem(navId).setChecked(true);
        boolean secondary = page >= SETTINGS && !diagnosticRoot(page);
        if (secondary) {
            toolbar.setNavigationIcon(R.drawable.ic_nav_back);
            toolbar.setNavigationContentDescription(R.string.ui_back);
        } else {
            toolbar.setNavigationIcon((android.graphics.drawable.Drawable) null);
        }
        switch (page) {
            case APPS: toolbar.setTitle(R.string.ui_hooks); break;
            case DIAGNOSTICS: toolbar.setTitle(R.string.diagnostics); break;
            case CAPTURE: case HEALTH: toolbar.setTitle(R.string.diagnostics); break;
            case HEALTH_REPORT: toolbar.setTitle(R.string.dx03_title);break;
            case EVENT_REPORT: toolbar.setTitle(R.string.r01_event_inspector);break;
            case SETTINGS: toolbar.setTitle(R.string.settings); break;
            case ABOUT: toolbar.setTitle(R.string.about); break;
            case SCOPE: toolbar.setTitle(R.string.scope); break;
            case HELP: toolbar.setTitle(R.string.ui07_help_title); break;
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
        diagnosticRows=null;body.removeAllViews();
        switch (page) {
            case APPS: apps(); break;
            case DIAGNOSTICS: diagnosticsPage(); break;
            case CAPTURE: capturePage(); break;
            case HEALTH: healthPage();break;
            case HEALTH_REPORT: healthReportPage();break;
            case EVENT_REPORT: eventReport();break;
            case SETTINGS: settings(); break;
            case ABOUT: aboutPage(); break;
            case HELP: helpPage(); break;
            case SCOPE: scopePage(); break;
            case APP_DETAIL: appDetail(); break;
            case GROUP_DETAIL: groupDetail(); break;
            default: home();
        }
    }

}
