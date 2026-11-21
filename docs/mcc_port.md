# MCC-VIP-Lab → HookLab M5 mapping and acceptance

Upstream repository: `Mikeyphw/MCC-VIP-Lab`, pinned `main` source `59d02beede7335acc13db6adc9f6a96ebf1d63ee` (2026-11-13). Target app `br.art.code.meucronogramacapilar`. Last manually tested app version **20.2609.3009** (version **name**, not version code). Any newer app version is unverified.

## Files and conversion

| Original | HookLab M5 | Notes |
| --- | --- | --- |
| `VipPayload` | `MccVipPayload` | Shape checks, preserved original maps, synthetic RevenueCat fixture fields and offline fixture |
| `HookEntry` RevenueCat mapper/callback | `MccVipHooks` | API 82 `XposedBridge` replaced with API 102 `hook(Executable).intercept(chain)`, `chain.proceed()`, and `chain.proceed(Object[])`; no purchases or billing callbacks are forged |
| `InAppRefillButton`, `DiamondButtonPlacement` | `MccDiamondUi` | Flutter Activity button, semantic anchor with bounded traversal and on-screen fallback, explicit module Activity handoff |
| `RootRefillCommand`, `AutoRefillWorker`, `RefillControlActivity` | `MccRefillCommand`, `MccRefillWorker`, `MccControlActivity` | Root only in HookLab UID, Android 14+ verified source UID/package for auto, module framework opt-in check, accurate write vs. relaunch status |
| `AutoRefillPolicy`, `AutoRefillWatcher` | `MccAutoPolicy`, `MccAutoWatcher` | Single account, one-byte zero, 15s bounded cooldown, host observation only |
| `NativeBannerProbe` | `MccBannerHooks` | Explicit user toggle replaces old Settings.Global flag; class-specific banner view hiding |
| `AdEventProbe`, `PurchaseCallProbe`, etc. | **Deferred** | Legacy diagnostic probes could expose app data; need separate opt-in redesign |

## Review requirements

- Existing HookLab core M4 tests must continue to pass. The two pre-M5 tests expecting an empty production registry are updated for exactly 4 entries.
- New hosted tests cover synthetic CustomerInfo shape/immutability, exact one-byte zero, safe account filename, root script generation and no unrelated-package or secondary-process eligibility.
- Host libxposed API 102 preferences remain read-only. Local installer failures trigger the M4 in-process latch; manual persistent safety locks stay supported.
- A successful Android build **does not** establish that Flutter class names, Activity controls, root grants, or RevenueCat callbacks work on the target device. Real LSPosed device acceptance is still needed.
- Test manual root actions in a controlled MCC account only. Never describe balance changes as backend currency creation or real premium verification.
- Check source-pinned CI artifact and signature, then install manually only after conscious review. An installed old MCC-VIP-Lab module should be disabled in LSPosed/Vector to prevent duplicate hooks.

## M5.1 safety repair (error 51 and auto control loop)

- `MccAutoWatcher` performs at most one successful automatic control handoff per
  MCC process. It does **not** repeatedly interrupt the app while the zero balance
  remains unchanged. A missing foreground Activity does not consume the gate.
- HookLab's own control Activity writes `enabled.mcc.diamond.auto=false` through
  the framework service after an unsuccessful manual or automatic refill. Host-side
  remote preferences remain read-only. The user must restart MCC to remove hooks
  already installed in its process.
- The root script derives the per-user MCC `dataDir` from Android PackageManager,
  then enforces an exact allowlist and refuses unknown directories. It does not
  create a new database, guess alternate balance formats, or clear existing data.
- `MCC_DATABASES_DIRECTORY_NOT_FOUND` is a storage-layout/profile issue, not a
  confirmed root permission failure. Root-code-51 acceptance requires a read-only
  device inspection; CI cannot prove device storage matches the old source.
- Never use this repair as evidence of backend reward authorization, physical
  device acceptance, or release qualification.
- An automatic request with stale/disabled opt-in now closes its module Activity
  without executing root, instead of stranding the user in a control screen.

## M5.1 stable release signing

See `docs/signing.md`. The existing debug CI remains unsigned by stable certificate; the manual main-only signed workflow is the install/update source. Certificate mismatches require an explicitly backed-up migration, never an automatic uninstall.
