# MCC 20.2609.3009 — HookLab static APK compatibility assertion

Source: user-supplied `Meu Cronograma Capilar_20.2609.3009.apk`.
Exact SHA-256: `d37ad349c80c8d2d2999b4175d11ba6863f1436e05181be1a0492a52229bee4e`.

## What the uploaded binary proves

- Binary AndroidManifest.xml: `br.art.code.meucronogramacapilar`, `versionName=20.2609.3009`, `versionCode=2026093009`, minSdk 32, targetSdk 36.
- Android manifest activity: `br.art.code.meucronogramacapilar.MainActivity`.
- DEX hierarchy: app MainActivity directly extends `io.flutter.embedding.android.FlutterFragmentActivity`; the hierarchy reaches Android `Activity` via AndroidX fragment/activity classes.
- DEX declares an `onResume` override in the activity ancestor chain; Android `Activity.onWindowFocusChanged(boolean)` remains the framework lifecycle hook target. Actual callback runtime invocation **has not been device-tested**.
- Flutter `FlutterView` class exists and extends `android.widget.FrameLayout` (its runtime accessibility tree remains unverified).
- The supplied base APK has `requiredSplitTypes=base__abi,base__density` but no `lib/` native libraries, and cannot establish installed ABI splits or Dart `libapp.so` storage semantics.
- ZIP CRC and content hash passed. The reproducible verifier re-reads Binary AXML and all DEX class definition tables; it rejects unexpected APK SHA/version/package/class hierarchy and includes seven negative metadata mutations.

## Assertions on HookLab's current MCC code

- Only `MccDiamondUi::install` is registered for MCC; no VIP, automatic refill, or banner installer is active. This is **independent of the number of other integrations**.
- `HookLabModule` requires main process, verified package identity, remote LSPosed opt-in, and non-quarantined framework safety state before dispatch.
- `MccDiamondUi` hooks `Activity.onResume` and `Activity.onWindowFocusChanged(boolean)` with protective exception mode, verifies the runtime activity extends FlutterActivity/FlutterFragmentActivity, and attaches an idempotent `♦ 9` button.
- Opening the button only launches HookLab's separate MCC control activity. The root storage inspect and write-to-9 paths require explicit user actions/confirmation in HookLab; host process cannot write remote LSPosed preferences.
- `MccRefillWorker` validates data directory identity; the on-disk diamond-balance format is **not** proven by APK class metadata and remains subject to real-device read-only inspection and user-confirmed write verification.

## Evidence gates

- Static APK metadata/class compatibility: **PASS on exact uploaded archive**.
- Source/registry matching and negative assertions: **PASS locally for the provided source snapshot**; CI after the corrective PR commit must independently rerun.
- DEX method hook execution, live Flutter semantics, root storage inspection, data-file format, MCC button placement, authenticated user action and balance write: **PENDING Android device**.
- Debug/stable APK, signing, CI24 and release acceptance: separate, not inferred from this evidence.

Recheck the exact APK against an actual checkout containing the verifier:

`python3 -B scripts/check_mcc_apk_contract.py --apk /sdcard/Download/'Meu Cronograma Capilar_20.2609.3009.apk'`

The file name is not trusted: the verifier checks the SHA-256 before parsing. Use the app's full split set when testing installation on device.
