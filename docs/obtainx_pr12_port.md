# HookLab M7 — NeoStoreObtainX PR #12 independent integrations

## Source provenance
- HookLab exact starting `main`: `ad40d5eb897597e60f65b896dfdf685d51bdb32d` (merged M6 manual MCC only).
- NeoStoreObtainX exact merged upstream `main`: `c59f810699d47782f9a6710fb231ec66177b2efb`.
- Original PR #12 exact head `2d5b604eee58e3747f8d527a00833473900be004`, merged on November 21, 2026.
- Droid-ify 0.7.8/780 source pin: `Droid-ify/client` `ff1453ed957f3b2382abce9a7eb9a9db8def3a3a`.
- Neo Store 1.3.3/1304 baseline from original M4 release DEX audit in NeoStoreObtainX.

## Boundary and intentional API migration
PR #12 by itself changes Droid-ify hook resilience and legacy LSPosed scope. HookLab additionally ports
NeoStoreObtainX's M2 source policy, M4 release-R8 Neo Store observer and M7 header button because
these dependencies are necessary to provide the existing ObtainX action as an integrated tweak.
Original legacy API 82 `XposedBridge` entry point and manifest scope array are NOT copied.
HookLab's modern API 102 hooks are explicitly registered in its existing `TweakCatalog` and
user approval of each host package is requested via `XposedService.requestScope()`.

## Included
- `neostore.obtainx.manual`: Neo Store 1.3.3/1304 only. Hook declared `NeoActivity.onResume`
  and the pinned six-argument `AppPageVM` constructor; sample original app-detail route,
  StateFlow package and product/repository at most every 600ms while the host Activity is resumed.
  Show 48dp icon if accessibility source anchor or guarded 365dp header fallback is safe.
- `droidify.obtainx.manual`: Droid-ify 0.7.8/780 only. Hook fragment `onViewCreated`, resolve
  Activity through the View ContextWrapper chain, find MaterialToolbar by superclass,
  reflect inherited getters without Xposed's best-match callMethod. Attach native menu item.
- On explicit tap only, re-read route, product ID, repository ID, repository address and
  authentication status; refuse unverified/stale/credentialed sources. Prefer supported
  upstream GitHub/GitLab/Codeberg/SourceHut/SourceForge repository root, otherwise map official
  F-Droid, IzzyOnDroid or selected custom HTTPS F-Droid repository.
- Prepare ObtainX-compatible JSON seed (additionalSettings is itself a JSON string) and send
  an explicit browsable `obtainium://app/` VIEW Intent to `dev.bikram.obtainx`. This opens
  an import/duplicate review and cannot by itself prove an import occurred.

## Excluded
- No auto-import or install. No ObtainX process hooks, shared account credentials, sync or
  database writes. No loosening of MCC M6 manual-only safety. No ad/VIP features.
- No static user scope assignment; opt-in and framework approval remain required.
- No unpinned host versions; no claim that another Neo Store or Droid-ify release is supported.
- No host SharedPreferences changes or persistent host database mutations.

## Validation gates
- [ ] Native source-bound Devtool build/check/apply, durable session and draft PR.
- [ ] Exact-head hosted Android compile, JUnit, lint, APK provenance.
- [ ] Confirm merged `main` and request **separate** protected stable-signed APK build.
- [ ] Independently verify 0.6.0/versionCode 7 and certificate continuity; install by explicit user choice.
- [ ] In HookLab, request scope for Neo Store and Droid-ify separately and enable desired tweaks.
- [ ] Verify Neo Store 1.3.3/1304: page READY, header icon visible without overlap, correct ObtianX preview.
- [ ] Verify Droid-ify 0.7.8/780: toolbar action visible/overflow, click preview; inspect failures by stage.
- [ ] Validate F-Droid, Izzy, custom, supported GitHub, non-supported source, credentials,
      changed route, duplicate tapping, rotation, keyboard, narrow portrait, RTL, TalkBack.
- [ ] Recheck MCC manual diamond button and confirmed no automatic refill.

DEVICE_ACCEPTANCE=NOT_PERFORMED
SIGNED_RELEASE_QUALIFICATION=NOT_PERFORMED
