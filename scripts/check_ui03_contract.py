#!/usr/bin/env python3
"""UI-03 source checks: binder reads run off UI thread; no activation claims."""
from __future__ import annotations
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")
def check(what: bool, name: str) -> None:
    if not what:
        raise RuntimeError("HOOKLAB_UI03_FAIL=" + name)
    print("HOOKLAB_UI03_PASS=" + name, flush=True)

ui = read("app/src/main/java/com/mikeyphw/hooklab/ui/MainActivity.java")

# A JavaSyntaxCheck parser does not resolve Android SDK imports.
# Guard the verified CI regression from run 44973294781 before Gradle compiles.
check("import android.content.pm.PackageManager;" in ui
      and "PackageManager.NameNotFoundException" in ui,
      "android_package_manager_import_for_host_icon")

store = read("app/src/main/java/com/mikeyphw/hooklab/ui/FrameworkStateStore.java")
snapshot = read("app/src/main/java/com/mikeyphw/hooklab/ui/FrameworkSnapshot.java")
strings = ET.fromstring(read("app/src/main/res/values/strings.xml"))
check("getRemotePreferences(" not in ui and "getScope(" not in ui
      and "getApiVersion(" not in ui, "no_framework_binder_reads_on_render")
check("private void render()" in ui and "stateStore.start(next ->" in ui
      and "snapshot.equals(next)" in ui and "previousScroll" in ui,
      "snapshot_driven_current_screen_updates")
check("newSingleThreadExecutor" in store and "ticket != generation" in store
      and "bound != app.currentService()" in store and "observer == null" in store,
      "single_worker_generation_and_lifecycle_gates")
check("prefs.edit().putBoolean(key, value).commit()" in store
      and "prefs.getBoolean(key, !value) == value" in store
      and "if (isReady(expected))" in store,
      "framework_confirmed_writes_not_optimistic")
check("writeSafetyLock(String id" in store
      and "TweakDiagnostics.quarantineKey(id)" in store
      and "stateStore.writeSafetyLock(id, locked" in ui,
      "existing_safety_locks_remain_framework_owned")
check("requestScope(Collections.singletonList(pkg)" in store
      and "removeScope(Collections.singletonList(pkg)" in store
      and "staticScope" not in ui,
      "scope_requests_not_local_mutations")
check("Collections.unmodifiableMap" in snapshot and "Collections.unmodifiableSet" in snapshot
      and "Boolean enabled" in snapshot and "Connection.LOADING" in snapshot
      and "version == null" not in snapshot,
      "explicit_immutable_unknown_states")
check("DynamicColors.applyToActivityIfAvailable(this)" in ui
      and "Build.VERSION.SDK_INT >= 31" in ui
      and 'getInt("night", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) != THEME_AMOLED' in ui,
      "material_you_respects_android_and_amoled")
check("setNavigationIcon((android.graphics.drawable.Drawable) null);" in ui
      and "setNavigationIcon(0)" not in ui and "setNavigationIcon(secondary ?" not in ui,
      "android16_toolbar_regression_guard")
names = {e.attrib.get("name") for e in strings.findall("string")}
check({"ui_loading_framework", "ui_framework_read_failed", "ui_scope_request_unconfirmed",
       "ui_dynamic_colors", "ui_dynamic_colors_detail", "ui_saving"}.issubset(names),
      "translated_status_resource_names")
test = read("app/src/test/java/com/mikeyphw/hooklab/ui/FrameworkSnapshotTest.java")
check(test.count("@Test") >= 10 and "unreadablePreferenceIsUnknownNotOff" in test
      and "snapshotCopiesInputsAndCannotBeMutated" in test,
      "pure_jvm_snapshot_tests_registered")
print("HOOKLAB_UI03_SOURCE_CONTRACT_PASS", flush=True)
