# Mikey's HookLab

Small hooks. Better apps. A modular LSPosed API 102 app with explicit, per-tweak opt-in, framework-managed scope, read-only host preferences, per-process fault quarantine, and stable GitHub-Environment signing.

## M6 — Meu Cronograma Capilar: manual diamonds only

The single registered tweak is **MCC · Manual diamond button** (`mcc.diamond.control`). When explicitly enabled for `br.art.code.meucronogramacapilar`, the Flutter Activity receives a compact **♦ 9** button. It opens HookLab's manual control screen; opening the screen never stops MCC or requests root. Root requires an additional explicit confirmation. Use the read-only storage inspection before attempting a change.

The original project [MCC-VIP-Lab](https://github.com/Mikeyphw/MCC-VIP-Lab) provided evidence for a single-digit, account-local `*-DiamondBalance` file under `databases` on app version `20.2609.3009`. This port checks only the target's own recognized storage directories and refuses missing, ambiguous, symlinked or unknown balance formats. It does not fabricate server-side diamonds. A successful local write can still be overwritten by MCC synchronization.

**Removed from active registration:** automatic refill, RevenueCat VIP spoofing, and ad/banner hooks. Legacy classes remain unregistered because native Devtool's current overlay format does not support deletion. The automatic handoff entry point itself is disabled. Previously persisted opt-in settings for retired tweak IDs have no effect.

## Distribution and safety

APK builds, unit tests, lint and provenance checks run on GitHub Actions with Android SDK 37.0. Stable releases use the existing manual `stable-release.yml` workflow and a preserved signing certificate in the `hooklab-signing` GitHub Environment; do not install debug APKs over stable releases. Version `0.5.0`, code `6` follows signed version `0.4.1`, code `5`.

Native Devtool creates a new isolated worktree and draft PR, verifies exact source and hosted CI, and does **not** merge, install APKs, call root, rotate signing identities, or qualify a device/release.

See [MCC manual port and device acceptance](docs/mcc_port.md) and [stable signing](docs/signing.md).


## M7 — NeoStoreObtainX PR #12 integration (candidate)

Two **independent**, disabled-by-default HookLab tweaks port selected behavior of
[`Mikeyphw/NeoStoreObtainX` PR #12](https://github.com/Mikeyphw/NeoStoreObtainX/pull/12)
from merged upstream source `c59f810699d47782f9a6710fb231ec66177b2efb`.
Both use **modern libxposed API 102**; none uses the original API 82 entry point.

- **Neo Store 1.3.3 (1304)**: guarded 48dp ObtainX header action. It observes
  the app-detail route and pinned R8 state fields and prefers the upstream
  source-code repository, with strict F-Droid/Izzy/custom repository fallback.
- **Droid-ify 0.7.8 (780)**: one native MaterialToolbar menu action on app details;
  uses PR #12's ContextWrapper Activity resolution and inherited-method lookup.
- On tap, re-validate package, selected source and repository identity; only
  open `obtainium://app/<encoded JSON>` with explicit target `dev.bikram.obtainx`.
  ObtainX owns the preview, duplicate checks and final save decision.
- **No** ObtainX fork/database hooks, automatic imports, account credentials,
  ad changes, MCC regression, automatic scope assignment or device writes.

Enable the two host packages separately in HookLab **Scope** and **Tweaks**.
The original legacy `@array/xposed_scope` is not copied: HookLab uses
framework-managed dynamic scope. The stable-signing workflow remains unchanged.
See `docs/obtainx_pr12_port.md` for source pin, risks and device acceptance.

## M8 — LSPosed recommended scope correction (0.6.1, versionCode 8)

The modern API 102 metadata now includes `META-INF/xposed/scope.list` with the
three supported host packages: Meu Cronograma Capilar, Neo Store and Droid-ify.
ObtainX is an external Intent destination and is **not** a scope target.
`staticScope=false` is intentionally retained, so this is a **recommended**
list rather than a fixed scope or automatic authorization. Approve the packages
in LSPosed Vector or use HookLab → Scope → Add, then enable individual tweaks
in HookLab → Tweaks and restart each target app. Existing user-selected scope
and disabled-by-default tweak settings are not overwritten.

This patch changes scope metadata, package version and source tests only; it
does not alter hooks, stable signing, account data or remote preferences.
Device acceptance and the new stable-signed 0.6.1 release are separate steps.

## M8 — Recommended LSPosed scope and verbose event logging

The scope recommendation file lists MCC, Neo Store and Droid-ify, while
`staticScope=false` keeps framework approval and user-selected scopes authoritative.
ObtainX is never a hooked package. The **Settings → Runtime logging → Verbose
LSPosed logging** switch is **off by default**. When enabled, a host process
reads a framework-owned preference and sends a bounded, rate-limited set of
static stage codes to the libxposed logger (`HookLabVerbose`). It does not log
app names, route IDs, account data, private file paths, source URLs, passwords,
exception messages or stack traces. Restart target apps after toggling to
ensure the framework has delivered updated preferences. No logging is enabled
if API 102 service or preferences are unavailable.


## WM01 — Word Master 0.6.10: opt-in night filter (experimental)

A fourth, independent API 102 integration targets exactly
`com.fugo.wowsolitaire` version `0.6.10` / versionCode `56240403`.
The **Word Master · Night filter** switch is off by default. LSPosed scope is
recommended but never automatically granted. When explicitly enabled, the
hook attaches a non-interactive, accessibility-hidden dark scrim over the
Unity Player Activity after `onResume`, once per activity window. Restart
Word Master after enabling or disabling it. It does not edit game data,
patch IL2CPP, change rewards, or hook advertising or payment paths.

**Limit:** This visibly dims the Unity viewport; it is **not** a true
in-game dark palette and cannot recolor Unity textures or text independently.
Source APK is a base split requiring its ABI split. Dark palette acceptance
is pending real-device tests. See `docs/WM01_ACCEPTANCE.md`.
