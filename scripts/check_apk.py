#!/usr/bin/env python3
"""Inspect actual packaged modern-libxposed APK metadata; fail closed."""
from __future__ import annotations

import sys
import zipfile
from pathlib import Path

PREFIX = "META-INF/xposed/"
EXPECTED_SCOPE = (
    b"br.art.code.meucronogramacapilar\n"
    b"com.machiav3lli.fdroid\n"
    b"com.looker.droidify\n"
    b"com.fugo.wowsolitaire\n"
)
EXPECTED_PROPS = {
    "minApiVersion": "102",
    "targetApiVersion": "102",
    "staticScope": "false",
    "exceptionMode": "protective",
    "autoHotReload": "false",
}
ENTRY = "com.mikeyphw.hooklab.hook.HookLabModule"


def verify(apk: Path) -> None:
    with zipfile.ZipFile(apk) as z:
        names = z.namelist()
        assert len(names) == len(set(names)), "Duplicate APK ZIP members"
        assert z.testzip() is None, "APK CRC failure"
        assert z.read(PREFIX + "java_init.list").decode("utf-8").strip() == ENTRY, "Module entry mismatch"
        props = dict(line.split("=", 1) for line in z.read(PREFIX + "module.prop").decode("utf-8").splitlines())
        assert props == EXPECTED_PROPS, "Modern libxposed API102 properties mismatch"
        # M8 recommended scope plus WM01 opt-in Word Master; still not static approval.
        # Byte-for-byte comparison rejects extras, duplicates, reordering, CRLF,
        # Unicode tricks, and accidentally scoped ObtainX itself.
        assert names.count(PREFIX + "scope.list") == 1, "Expected one modern scope.list"
        assert z.read(PREFIX + "scope.list") == EXPECTED_SCOPE, "Unexpected recommended scope"
        assert "assets/xposed_init" not in names, "Legacy Xposed entry forbidden"
        assert "classes.dex" in names, "DEX missing"


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python3 -B scripts/check_apk.py PATH_TO_APK")
    verify(Path(sys.argv[1]))
    print("HOOKLAB_APK_METADATA_PASS")
