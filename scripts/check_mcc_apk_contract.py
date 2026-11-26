#!/usr/bin/env python3
"""MCC/HookLab host contract: source-only CI guard or independent pinned-APK recheck.

Usage: python3 -B scripts/check_mcc_apk_contract.py [--apk FILE] [--json]
No APK bytes are committed to HookLab. Source-only mode does not prove runtime behavior.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import zipfile

EXPECTED_APK_SHA256 = 'd37ad349c80c8d2d2999b4175d11ba6863f1436e05181be1a0492a52229bee4e'
EXPECTED_PACKAGE = 'br.art.code.meucronogramacapilar'
EXPECTED_VERSION_NAME = '20.2609.3009'
EXPECTED_VERSION_CODE = 2026093009
EXPECTED_ACTIVITY = 'br.art.code.meucronogramacapilar.MainActivity'
MAIN_CLASS = 'Lbr/art/code/meucronogramacapilar/MainActivity;'
FLUTTER_FRAGMENT = 'Lio/flutter/embedding/android/FlutterFragmentActivity;'
FLUTTER_VIEW = 'Lio/flutter/embedding/android/FlutterView;'
ANDROID_ACTIVITY = 'Landroid/app/Activity;'


class ContractMismatch(ValueError):
    pass


def require(ok: bool, label: str) -> None:
    if not ok:
        raise ContractMismatch(label)


def checked_u16(data: bytes, pos: int) -> int:
    require(0 <= pos <= len(data) - 2, 'AXML_TRUNCATED_U16')
    return struct.unpack_from('<H', data, pos)[0]


def checked_u32(data: bytes, pos: int) -> int:
    require(0 <= pos <= len(data) - 4, 'AXML_TRUNCATED_U32')
    return struct.unpack_from('<I', data, pos)[0]


def leb128(data: bytes, pos: int) -> tuple[int, int]:
    value = 0
    for shift in range(0, 35, 7):
        require(pos < len(data), 'DEX_ULEB_TRUNCATED')
        b = data[pos]
        pos += 1
        value |= (b & 0x7f) << shift
        if b < 0x80:
            return value, pos
    raise ContractMismatch('DEX_ULEB_TOO_LONG')


def axml_len8(data: bytes, pos: int) -> tuple[int, int]:
    require(pos < len(data), 'AXML_STRING_TRUNCATED')
    value = data[pos]
    if value & 0x80:
        require(pos + 1 < len(data), 'AXML_STRING_TRUNCATED')
        return ((value & 0x7f) << 8) | data[pos + 1], pos + 2
    return value, pos + 1


def axml_len16(data: bytes, pos: int) -> tuple[int, int]:
    value = checked_u16(data, pos)
    if value & 0x8000:
        return ((value & 0x7fff) << 16) | checked_u16(data, pos + 2), pos + 4
    return value, pos + 2


def axml_manifest(data: bytes) -> dict:
    require(len(data) >= 8 and checked_u16(data, 0) == 0x0003, 'NOT_BINARY_ANDROID_MANIFEST')
    require(checked_u32(data, 4) == len(data), 'BAD_AXML_TOTAL_SIZE')
    strings: list[str] = []
    manifest = None
    uses_sdk = None
    activities: list[dict] = []
    pos = 8
    while pos < len(data):
        require(pos + 8 <= len(data), 'AXML_BAD_CHUNK')
        kind, hsize, size = struct.unpack_from('<HHI', data, pos)
        require(hsize >= 8 and size >= hsize and pos + size <= len(data), 'AXML_BAD_CHUNK')
        if kind == 0x0001:
            require(hsize >= 28, 'AXML_BAD_STRING_POOL')
            count = checked_u32(data, pos + 8)
            flags = checked_u32(data, pos + 16)
            start = checked_u32(data, pos + 20)
            require(count < 100000 and hsize + count * 4 <= size, 'AXML_BAD_STRING_COUNT')
            utf8 = bool(flags & 0x100)
            for n in range(count):
                off = pos + start + checked_u32(data, pos + hsize + n * 4)
                require(pos <= off < pos + size, 'AXML_STRING_OFF_RANGE')
                if utf8:
                    _, off = axml_len8(data, off)
                    length, off = axml_len8(data, off)
                    require(off + length <= pos + size, 'AXML_STRING_RANGE')
                    strings.append(data[off:off + length].decode('utf8', 'replace'))
                else:
                    length, off = axml_len16(data, off)
                    require(off + length * 2 <= pos + size, 'AXML_STRING_RANGE')
                    strings.append(data[off:off + length * 2].decode('utf-16le', 'replace'))
        elif kind == 0x0102:
            require(strings and hsize >= 16, 'AXML_START_NO_POOL')
            name_index = checked_u32(data, pos + 20)
            require(name_index < len(strings), 'AXML_BAD_TAG_NAME')
            tag = strings[name_index]
            attr_start = checked_u16(data, pos + 24)
            attr_size = checked_u16(data, pos + 26)
            attr_count = checked_u16(data, pos + 28)
            require(attr_size >= 20 and attr_count < 1000, 'AXML_BAD_ATTRIBUTE_HEADER')
            attrs = {}
            for n in range(attr_count):
                off = pos + 16 + attr_start + attr_size * n
                require(off + 20 <= pos + size, 'AXML_BAD_ATTRIBUTE_RANGE')
                _, attr_name, raw = struct.unpack_from('<III', data, off)
                _, _, typ, value = struct.unpack_from('<HBBI', data, off + 12)
                require(attr_name < len(strings), 'AXML_BAD_ATTRIBUTE_NAME')
                key = strings[attr_name]
                if raw != 0xffffffff:
                    require(raw < len(strings), 'AXML_BAD_RAW_STRING')
                    val = strings[raw]
                elif typ == 3:
                    require(value < len(strings), 'AXML_BAD_TYPED_STRING')
                    val = strings[value]
                else:
                    val = value
                attrs[key] = val
            if tag == 'manifest':
                manifest = attrs
            elif tag == 'uses-sdk':
                uses_sdk = attrs
            elif tag == 'activity':
                activities.append(attrs)
        pos += size
    require(pos == len(data) and manifest is not None and uses_sdk is not None, 'AXML_INCOMPLETE')
    return {'manifest': manifest, 'uses_sdk': uses_sdk, 'activities': activities}


def dex_classes(data: bytes) -> tuple[dict[str, str | None], set[tuple[str, str]]]:
    require(data.startswith(b'dex\n') and len(data) > 0x70, 'NOT_DEX')
    u32 = lambda pos: checked_u32(data, pos)
    ss, so, ts, to, ms, mo, cs, co = [u32(p) for p in (0x38, 0x3c, 0x40, 0x44, 0x58, 0x5c, 0x60, 0x64)]
    require(ss < 500000 and ts < 500000 and ms < 500000 and cs < 500000, 'DEX_BAD_COUNTS')
    require(so + ss * 4 <= len(data) and to + ts * 4 <= len(data)
            and mo + ms * 8 <= len(data) and co + cs * 32 <= len(data), 'DEX_BAD_TABLES')
    strings: list[str] = []
    for n in range(ss):
        string_off = u32(so + 4 * n)
        _, off = leb128(data, string_off)
        end = data.find(b'\0', off)
        require(0 <= end and end - off <= 1000000, 'DEX_BAD_STRING_RANGE')
        strings.append(data[off:end].decode('utf8', 'replace'))
    types = [strings[u32(to + 4 * n)] for n in range(ts)]
    methods = [struct.unpack_from('<HHI', data, mo + n * 8) for n in range(ms)]
    found_classes: dict[str, str | None] = {}
    found_methods: set[tuple[str, str]] = set()
    for n in range(cs):
        idx, _, super_idx, _, _, _, class_data, _ = struct.unpack_from('<IIIIIIII', data, co + 32 * n)
        require(idx < len(types), 'DEX_BAD_CLASS_IDX')
        name = types[idx]
        parent = types[super_idx] if super_idx < len(types) else None
        found_classes[name] = parent
        if not class_data:
            continue
        sizes = []
        for _ in range(4):
            count, class_data = leb128(data, class_data)
            sizes.append(count)
        require(max(sizes) < 100000, 'DEX_BAD_CLASS_SIZE')
        for count in sizes[:2]:
            for _ in range(count):
                _, class_data = leb128(data, class_data)
                _, class_data = leb128(data, class_data)
        for count in sizes[2:]:
            idx = 0
            for _ in range(count):
                diff, class_data = leb128(data, class_data)
                idx += diff
                _, class_data = leb128(data, class_data)
                _, class_data = leb128(data, class_data)
                require(idx < len(methods), 'DEX_BAD_METHOD_IDX')
                cls, _, name_idx = methods[idx]
                require(cls < len(types) and name_idx < len(strings), 'DEX_BAD_METHOD_REF')
                found_methods.add((name, strings[name_idx]))
    return found_classes, found_methods


def assert_expected(metadata: dict) -> list[str]:
    m = metadata['manifest']
    sdk = metadata['uses_sdk']
    checks = []
    def check(cond: bool, name: str) -> None:
        require(cond, name)
        checks.append(name)
    check(m.get('package') == EXPECTED_PACKAGE, 'MCC_PACKAGE_EXACT')
    check(m.get('versionName') == EXPECTED_VERSION_NAME, 'MCC_VERSION_NAME_EXACT')
    check(m.get('versionCode') == EXPECTED_VERSION_CODE, 'MCC_VERSION_CODE_EXACT')
    check(sdk.get('minSdkVersion') == 32 and sdk.get('targetSdkVersion') == 36,
          'MCC_MIN32_TARGET36')
    check('base__abi' in str(m.get('requiredSplitTypes', '')),
          'MCC_BASE_APK_REQUIRES_SPLITS')
    check(any(a.get('name') == EXPECTED_ACTIVITY for a in metadata['activities']),
          'MCC_ACTIVITY_MANIFEST')
    classes: dict = metadata['classes']
    methods: set = metadata['methods']
    check(classes.get(MAIN_CLASS) == FLUTTER_FRAGMENT,
          'MCC_MAIN_EXTENDS_FLUTTER_FRAGMENT_ACTIVITY')
    check(FLUTTER_VIEW in classes and classes[FLUTTER_VIEW] == 'Landroid/widget/FrameLayout;',
          'MCC_FLUTTER_VIEW_IS_FRAMELAYOUT')
    check((MAIN_CLASS, 'onCreate') in methods, 'MCC_MAIN_ON_CREATE_DECLARED')
    ancestors = []
    current = MAIN_CLASS
    for _ in range(12):
        parent = classes.get(current)
        if not parent:
            break
        ancestors.append(parent)
        current = parent
        if current == ANDROID_ACTIVITY:
            break
    check(ancestors and ancestors[-1] == ANDROID_ACTIVITY,
          'MCC_LIFECYCLE_INHERITS_ANDROID_ACTIVITY')
    check(any((c, 'onResume') in methods for c in [MAIN_CLASS] + ancestors),
          'MCC_LIFECYCLE_ON_RESUME_IN_ANCESTRY')
    check(not metadata.get('native_libs'), 'MCC_BASE_MISSING_NATIVE_DART_SPLIT')
    return checks


def inspect_apk(apk: Path) -> dict:
    require(apk.is_file() and not apk.is_symlink(), 'APK_NOT_REGULAR_FILE')
    require(apk.stat().st_size <= 800_000_000, 'APK_TOO_LARGE')
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    require(digest == EXPECTED_APK_SHA256, 'APK_SHA256_MISMATCH')
    with zipfile.ZipFile(apk) as z:
        require(z.testzip() is None, 'APK_ZIP_CRC_FAILURE')
        names = z.namelist()
        require(len(names) == len(set(names)), 'APK_DUPLICATE_MEMBERS')
        require('AndroidManifest.xml' in names, 'APK_MANIFEST_MISSING')
        metadata = axml_manifest(z.read('AndroidManifest.xml'))
        metadata['classes'] = {}
        metadata['methods'] = set()
        dex = [s for s in names if re.fullmatch(r'classes[1-9]*\.dex', s)]
        require(dex, 'APK_DEX_MISSING')
        for name in dex:
            cls, methods = dex_classes(z.read(name))
            for c, sup in cls.items():
                require(c not in metadata['classes'], 'APK_DUPLICATE_DEX_CLASS')
                metadata['classes'][c] = sup
            metadata['methods'].update(methods)
        metadata['native_libs'] = [s for s in names if s.startswith('lib/') and s.endswith('.so')]
    checks = assert_expected(metadata)
    return {
        'sha256': digest,
        'package': metadata['manifest']['package'],
        'version_name': metadata['manifest']['versionName'],
        'version_code': metadata['manifest']['versionCode'],
        'main_activity': EXPECTED_ACTIVITY,
        'flutter_superclass': FLUTTER_FRAGMENT,
        'checks': checks,
        'native_libs_in_base': len(metadata['native_libs']),
        'runtime_acceptance': 'NOT_RUN',
        'diamond_file_layout': 'NOT_ESTABLISHED_FROM_BASE_APK',
    }


def source_contract(root: Path) -> list[str]:
    def read(path: str) -> str:
        return (root / path).read_text(encoding='utf8')
    catalog = read('app/src/main/java/com/mikeyphw/hooklab/core/TweakCatalog.java')
    ids = read('app/src/main/java/com/mikeyphw/hooklab/mcc/MccIds.java')
    ui = read('app/src/main/java/com/mikeyphw/hooklab/mcc/MccDiamondUi.java')
    control = read('app/src/main/java/com/mikeyphw/hooklab/mcc/MccControlActivity.java')
    worker = read('app/src/main/java/com/mikeyphw/hooklab/mcc/MccRefillWorker.java')
    unit = read('app/src/test/java/com/mikeyphw/hooklab/mcc/MccCatalogTest.java')
    checks = []
    def check(cond: bool, name: str):
        require(cond, name)
        checks.append(name)
    check('PACKAGE = "' + EXPECTED_PACKAGE + '"' in ids
          and 'LAST_MANUALLY_TESTED = "' + EXPECTED_VERSION_NAME + '"' in ids,
          'MCC_SOURCE_IDENTITY_MATCHES_APK')
    check(catalog.count('MccDiamondUi::install') == 1
          and catalog.count('case MccIds.DIAMOND_MANUAL: return MccDiamondUi::install;') == 1
          and 'MccIds.VIP' not in catalog and 'MccIds.DIAMOND_AUTO' not in catalog
          and 'MccIds.BANNER_HIDE' not in catalog,
          'MCC_ONLY_REVIEWED_MANUAL_INSTALLER')
    check('Activity.class.getDeclaredMethod("onResume")' in ui
          and 'Activity.class.getDeclaredMethod("onWindowFocusChanged", boolean.class)' in ui
          and '"io.flutter.embedding.android.FlutterFragmentActivity".equals(name)' in ui
          and '"io.flutter.embedding.android.FlutterView".equals(next.getClass().getName())' in ui,
          'MCC_ACTIVITY_AND_FLUTTER_HOOK_POINTS')
    check('chain.proceed()' in ui
          and 'XposedInterface.ExceptionMode.PROTECTIVE' in ui
          and 'INSTALLED.compareAndSet(false,true)' in ui,
          'MCC_HOOK_FAIL_CLOSED_AND_IDEMPOTENT')
    check('setOnClickListener(v -> openControl(activity))' in ui
          and 'setPositiveButton("Set to 9"' in control
          and 'MccRefillWorker.setNine(' in control,
          'MCC_REQUIRES_MANUAL_CONFIRMATION')
    check('String dir=installedDataDir(context);if(dir==null)return "MCC_DATA_DIRECTORY_UNVERIFIED";' in worker
          and 'String script=(probe?MccRootContextProbe.script(dir,android.os.Process.myPid()):"")+MccRefillCommand.script(dir,probe);' in worker
          and worker.count('new ProcessBuilder("su","-c",script)') == 1
          and worker.count('new ProcessBuilder(') == 1
          and 'return MccRefillCommand.checkedDataDir(info.dataDir);' in worker
          and 'MccRootContextProbe' not in ui and 'ProcessBuilder(' not in ui,
          'MCC_ROOT_ONLY_FROM_COMPANION_AND_FAIL_CLOSED')
    check('assertEquals(3,TweakCatalog.entries().size())' not in unit
          and 'assertNull("Duplicate MCC installer", mccEntry)' in unit
          and 'assertNotNull("MCC manual installer missing", mccEntry)' in unit,
          'MCC_TEST_INVARIANT_NOT_GLOBAL_COUNT')
    return checks


def negative_tests() -> list[str]:
    valid = {
        'manifest': {'package': EXPECTED_PACKAGE, 'versionCode': EXPECTED_VERSION_CODE,
                     'versionName': EXPECTED_VERSION_NAME, 'requiredSplitTypes': 'base__abi,base__density'},
        'uses_sdk': {'minSdkVersion': 32, 'targetSdkVersion': 36},
        'activities': [{'name': EXPECTED_ACTIVITY}],
        'classes': {MAIN_CLASS: FLUTTER_FRAGMENT, FLUTTER_FRAGMENT: 'Landroidx/fragment/app/t;',
                    'Landroidx/fragment/app/t;': ANDROID_ACTIVITY, FLUTTER_VIEW: 'Landroid/widget/FrameLayout;'},
        'methods': {(MAIN_CLASS, 'onCreate'), (FLUTTER_FRAGMENT, 'onResume')},
        'native_libs': []
    }
    assert_expected(valid)
    import copy
    for key, corrupt in [
        ('PACKAGE_CHANGED', lambda d: d['manifest'].update(package='com.untrusted.other')),
        ('VERSION_CHANGED', lambda d: d['manifest'].update(versionCode=0)),
        ('ACTIVITY_CHANGED', lambda d: d['activities'].clear()),
        ('SUPER_CHANGED', lambda d: d['classes'].update({MAIN_CLASS: 'Landroid/app/Service;'})),
        ('FLUTTER_VIEW_REMOVED', lambda d: d['classes'].pop(FLUTTER_VIEW)),
        ('ON_RESUME_MISSING', lambda d: d['methods'].clear()),
        ('NATIVE_SPLIT_FALSIFIED', lambda d: d.update(native_libs=['lib/arm64-v8a/libapp.so']))
    ]:
        broken = copy.deepcopy(valid)
        corrupt(broken)
        try:
            assert_expected(broken)
        except ContractMismatch:
            pass
        else:
            raise ContractMismatch('NEGATIVE_TEST_FAILED_' + key)
    return ['BASE_FIXTURE_ACCEPTED', 'SEVEN_MUTATED_FIXTURES_REJECTED']


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--apk', type=Path, help='Full pinned APK verification (does not modify APK)')
    ap.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[1])
    ap.add_argument('--json', action='store_true')
    args = ap.parse_args(argv)
    checks = source_contract(args.repo)
    negatives = negative_tests()
    result = {'source_checks': checks, 'negative_tests': negatives, 'apk_recheck': 'NOT_RUN'}
    if args.apk:
        result['apk_evidence'] = inspect_apk(args.apk)
        result['apk_recheck'] = 'PASS'
    if args.json:
        print(json.dumps(result, indent=2, sort_keys=True))
    else:
        print('MCC_SOURCE_CONTRACT=PASS checks=' + str(len(checks)))
        print('MCC_NEGATIVE_FIXTURES=PASS rejected=7')
        print('MCC_UPLOADED_APK_RECHECK=' + result['apk_recheck'])
        if args.apk:
            print('MCC_APK_SHA256=' + result['apk_evidence']['sha256'])
            print('MCC_APK_STATIC_CHECKS=PASS count=' + str(len(result['apk_evidence']['checks'])))
        print('MCC_DEVICE_RUNTIME=NOT_PERFORMED')
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (ContractMismatch, OSError, zipfile.BadZipFile, ValueError, IndexError, struct.error) as exc:
        print('MCC_STATIC_CONTRACT_FAILED=' + str(exc), file=sys.stderr)
        raise SystemExit(1)
