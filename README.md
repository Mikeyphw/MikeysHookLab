# Mikey's HookLab

Small hooks. Better apps.

Android Material UI + **modern libxposed API 102** module, with independent opt-in, source-controlled package allowlists, framework-managed scope, read-only host preferences, and M4 fail-closed process quarantine.

## M5 · First app: Meu Cronograma Capilar (MCC)

M5 imports selected behaviors from **[Mikeyphw/MCC-VIP-Lab](https://github.com/Mikeyphw/MCC-VIP-Lab)** at exact source `59d02beede7335acc13db6adc9f6a96ebf1d63ee` into the HookLab registry (no dependency on the old API 82 module):

| Tweak ID | Behavior | Default |
| --- | --- | --- |
| `mcc.vip.local` | Local RevenueCat `CustomerInfoMapperKt.map(CustomerInfo)` and `PurchasesFlutterPlugin.getCustomerInfo` proxy adapted to interceptor chains | **Off** |
| `mcc.diamond.control` | In-app `♦ 9` Flutter diamond button opening HookLab's explicit diamond control Activity | **Off** |
| `mcc.diamond.auto` | Wait for exactly one ASCII `0` in a single account balance file; only verified foreground handoff can trigger root refill | **Off** |
| `mcc.banner.hide` | Hide specifically recognized native Flutter Google Mobile Ads banner views | **Off** |

Exact target package: `br.art.code.meucronogramacapilar`; only its **main process** is eligible. No system or other-app scope. No app-specific hook runs until the user independently enables a tweak and receives framework scope approval. The old module's `xposed_init` and API 82 stubs are **not** imported.

**Last manually tested MCC app version:** `20.2609.3009` in upstream. The upstream source does **not** include a reliable Android `versionCode` for that version. To preserve the upstream version-agnostic behavior, HookLab accepts any positive installed `versionCode` **but does not claim newer versions have been tested**. The UI displays that warning. Revisit the version gate once an exact device-tested version code is available.

## Diamond safety contract

- Root is requested only by the HookLab application UID after an explicit manual button tap, or an Android 14+ verified launch from MCC when `mcc.diamond.auto` is also enabled in the framework preferences and MCC has approved scope.
- The target app process only adds UI and observes its own data; it **never** executes `su`, writes another app's data, or hosts a root IPC endpoint.
- The root operation requires one non-symlink account-local `*-DiamondBalance` file, one digit in ASCII, and a valid ASCII account filename. It force-stops MCC, writes **9**, verifies the result and separately reports app relaunch dispatch. Auto mode refuses nonzero values.
- Android below 14 can open manual control but **cannot automatically execute root from the target**. The module never silently accepts a caller-supplied auto intent.
- Both local balance state and mock RevenueCat entitlements are **local changes only**, not real purchase authorization, backend approval or verified revenue. MCC might synchronize local data and later versions may change the behavior.
- The overlay does **not** use blanket APK permissions, install anything on the device, remove an existing module, migrate its signing key, or grant root permission automatically.

## Deliberate limitations

The diagnostic-only upstream probes (`P02`–`P07`, e.g. verbose ad callback, file I/O and purchase call tracing) are **not** imported into M5, because they capture more target data than HookLab's sanitized diagnostics policy permits. No claim of full source parity, real-device UX validation or compatibility with newer MCC releases is made. Legacy module settings and signing identity are not transferred; disable the old MCC-VIP-Lab module when validating HookLab to avoid double-hooking.

## CI, native delivery and reviews

Only GitHub Actions may execute Android Gradle build, `testDebugUnitTest`, lint and APK signature/provenance checks. GitHub SDK setup remains `android-actions/setup-android@v4.0.4` + the published `platforms;android-37.0` package, Gradle 9.3.1 and AGP 9.1.1; `targetSdk 36` is unchanged.

Native Devtool performs source-pinned checks, a committed immutable overlay, and a durable delivery session, with a draft PR and independent exact-head CI verification. Device writes, signing, release qualification, automatic merge, force-push or any host Gradle execution are excluded.

Details: [MCC port mapping and acceptance](docs/mcc_port.md).

## Stable signing (M5.1)

See [stable signing guide](docs/signing.md) for the protected GitHub Environment, local key bootstrap, exact-source manual release, and certificate continuity. PR/debug APK artifacts do not share the stable signing certificate.
