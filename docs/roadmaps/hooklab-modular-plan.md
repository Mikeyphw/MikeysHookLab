> Historical proposal preserved verbatim. Current authoritative status and MOD-00 scope: `hooklab-modular.json` and `hooklab-mod00.md`.

# HookLab — Private Modular Platform: implementation plan

**Program:** HOOKLAB-MODULAR-PRIVATE · **Revision:** 1.0-proposed · **Snapshot:** 2026-11-25 (America/Sao_Paulo)

**Status:** Planning document; not an adopted roadmap, source patch, operator, device acceptance or release authorization.

## Objective and decisions

One personally maintained LSPosed API 102 app with independently installable/updatable hook packs, native custom configuration pages, GitHub distribution, internal SDK and Go tooling. Keep current Java Android UI and three-tab revamp; no public marketplace, third-party publisher federation, cloud account, dependency solver or Go rewrite. Personal use does not imply the existing GitHub repository is private: visibility stays unchanged unless separately authorized. No new privileged behavior, entitlement changes, or automatic command execution is authorized by this plan.

## Architecture and repository layout

```text
MikeysHookLab/
  app/                    # native manager, repository UI, credentials boundary
  sdk/                    # stable Java interfaces, typed settings, UI components
  runtime/                # API 102 dispatcher, verified host loader
  modules/{obtainx,chatgpt,wordmaster}/  # independent source/builds
  contracts/              # JSON schemas, shared Go/Java test vectors
  repository/             # signed index generation inputs
  tools/hooklabctl/       # Go CLI (go.mod, cmd, internal, tests)
  docs/roadmaps/          # these documents, after adoption
  .github/workflows/     # hosted APK and independent module pipelines
```

Manager owns network, GitHub authentication, imports, registry and configuration writes. Host receives only verified module runtime payloads and bounded immutable snapshots via framework-supported transport. Gate package/process/version, scope, opt-in and safety lock before DEX loading; read-only host settings, no host-side HTTP, no Go runtime or UI classes in target processes. Keep SDK classloader identity stable and host classloader explicit for reflection. Candidate loader: bounded verified bytes from `openRemoteFile` → `InMemoryDexClassLoader` with exact SDK parent; prove actual manager-side publish/host-side read, process lifecycle and Vector behavior on device. Do not assume API availability alone establishes end-to-end transport. If not viable, use built-in SDK modules as a qualified interim architecture only by revised scope/approval; never label independent runtime loading complete.

## Package and state contracts

`.hookpack` is a ZIP container with `manifest.json`, `runtime/classes.dex`, optional `configuration/schema.json`, `configuration/defaults.json`, `ui/classes.dex`, bounded `ui/resources/` and `assets/`, optional localization, changelog, signed digest manifest and signature. Separate runtime from manager-side UI. Manifest declares schema, stable module ID, semantic version, SDK min/max, API 102, each existing stable hook ID and target package/process/version codes, entrypoints, settings schema version, dependencies (initially SDK-only or explicit bundled requirements), capabilities, source/build provenance and activation semantics. All executable code defaults off. Verify exact signed bytes, not a reserialized JSON assumption; choose one canonical format and signature algorithm (proposed Ed25519 subject to Android minSdk 29/provider compatibility tests). Reject unknown schemas, duplicate keys/IDs/entries, traversal, symlink, oversized/decompression bomb, missing/extra unsigned payloads, revoked signer and unsupported versions. Test Go/Java interoperability on min API. No native library payloads in v1 unless separately approved and qualified.

Registry: immutable generation with module ID/version/digest/signer, resolved targets, per-hook configuration and previous generation. Manager durable journal stages `STAGED → VERIFIED → READY → ACTIVE` or `FAILED/ROLLED_BACK`; publish only after validation and settings migration, verify actual framework readback. Preserve last usable generation; interruptions resume/reconcile rather than replay. Removal prevents new loads; already installed hooks may persist until host restart. Hot configuration only when supported and tested; never promise arbitrary DEX unloading. Disable/safe mode independent of module UI. Distinguish install state, framework scope, opt-in, compatibility, observed hook status and evidence freshness.

## Native configuration and UI

Keep Home / Hooks / Diagnostics; settings top action and adaptive rail. Hooks Installed / By app / Repository (only enabled once functional). Module details Overview / Configure / Logs / Versions; per-hook activation independent from module installation. Custom native Java/Kotlin views via internal `ModuleUiProvider` with create/resume/pause/destroy and manager-owned route IDs. Reuse FlatUi, native controls, color/theme, 48dp touch targets, state restoration and accessibility. Simple settings use reusable typed controls and optional small schema; avoid a complex form-language engine. Module owns custom content, manager owns lifecycle, top-level navigation and sensitive authorization. Namespaced typed settings, per-app overrides, profiles, reset/import/export, versioned migrations, restart indicator, local previews, module help. No raw plugin code loaded just to list metadata. Imported UI failure falls back to core management; user can disable/remove it. Existing MCC confirmation/root-sensitive manager workflow remains built in until a safe explicit migration is designed and qualified. No reactivation of retired hooks.

## GitHub distribution and credentials

Default `Mikeyphw/MikeysHookLab`; optional additional personal repo. Distinct module tags such as `module/wordmaster/v1.0.0`; do not mix with stable APK signer/release. Signed index includes module/version, target compatibility, exact source SHA/tree, build run/attempt, asset ID/hash/size, channel and revocation state. Index monotonic revision and trusted signature prevent silent stale replay; explicit local rollback uses already verified retained artifact. Offline metadata marked stale. Use latest compatible, pin/rollback, stable/testing/local lanes, review updates; never auto-install code. Artifact imports verify exact qualified workflow head/attempt and hash and testing trust, not filename. Token optional for public releases; use repo-limited fine-grained PAT (Contents read; Actions read for artifacts). Keystore encryption, exclude backups/logs/remote preferences, strip Authorization on cross-origin redirects, redact URLs. **Important:** arbitrary same-UID custom native UI can access manager data or invoke same-UID Keystore operations; a secondary process with same UID is not isolation. Prefer a distinct-UID credential broker with narrow repository operations or keep PAT gated and rely on public access until verified; if user accepts trust-only same-UID design, record that explicitly and never claim credential isolation. No raw token returned to modules/hosts, no token in CLI args or receipts. One personal signing root; separate release signing identity and test trust; no secrets in pack or Git history.

## Go tooling

Go only for `hooklabctl` packaging, verification, index generation, compatibility fixtures, release orchestration helpers and authorized Termux management. Android manager/SDK/runtime remain Java (Kotlin optional for module pages); no gomobile/JNI dependency initially. Go and Java verify same signed bytes and shared corpus; each independent implementation must reject malformed inputs. CLI build-plan routes Android build to hosted CI, never local Gradle. Go unit/fuzz/build Linux and Termux native execution are allowed when available. Proposed verbs: `module init`, `module pack`, `module verify`, `module inspect`, `repo index`, `repo verify`, `device status`, `module list/install/update/rollback/disable`, `doctor`, `logs`, `evidence export`. Device mutations require authenticated narrow interface and explicit consent, not exported arbitrary command execution. Devtool remains source delivery authority, not replaced by hooklabctl.

## Delivery and testing strategy

1. Repair and merge current PR #33 through existing native workflow; confirm exact-head extended adaptive matrix, independent native and CI24 results. Reconcile pending F05 host state, never replay predecessor. Preserve all old source and acceptance debt.
2. Adopt roadmap amendment and agreed design contract; MOD-01 introduces stable SDK/registry plus real-device feasibility gate early. If it fails, investigate and revise architecture before runtime promise is accepted.
3. MOD-02 package verification, loader, recovery and eligible migration; MOD-03 integrated native UI/config. MOD-04 repository and release pipeline after trust boundary is established. MOD-05 Go tooling/device bridge, complete migration/performance/security proof. UX-R05 final whole-app visual/regression approval after module UI integrated; all existing checks stay enforced during development.
4. Each overlay: current source/roadmap/Devtool/ledgers and validators → actual end-to-end implementation → non-Gradle local tests → qualified cumulative `devop-hooklab` → authorized native preflight/check/apply/validate/commit/session/push/draft PR → exact-head hosted checks + separate CI24 → independent artifact/receipt review → device/release gates and approved merge. Preserve history/receipts; reconcile after interruption. No local Gradle directly or indirectly; hosted Android build/lint/instrumentation. No automatic clipboard helpers; private sanitized invocation log, JSON receipt, toast and final stderr manual copy command only. Clean validated operator replacement with rollback on failure and superseded operator-owned runnable cleanup; preserve durable state. No implementation artifacts before complete local qualification.
5. Rollback: core through reviewed native revert; module through immutable known-good generation and settings compatibility; disable imported hooks and restart target; keep diagnostics evidence. No force reset, automatic root, automatic merge/release, signing rotation or silent scope grant.

See roadmap for overlay dependencies and checklist for every new promise; ledger tracks decisions, risks, gaps and evidence.

## Source snapshot and authority

- Repository: [https://github.com/Mikeyphw/MikeysHookLab](https://github.com/Mikeyphw/MikeysHookLab); default branch `main`, SHA `a76361eacad31ec072c3ef13563ea26be5b380a2`, Git tree `26fa5c749deb9b0a1d7168d946030a7da6688b62`.
- Active [draft PR #33](https://github.com/Mikeyphw/MikeysHookLab/pull/33): head `ccb228f7faaaf94dc55c12c52a34c87b398be4f5`, base `a76361eacad31ec072c3ef13563ea26be5b380a2`. Branch roadmap blob `4c26ce8d54dc34580be1c038d660fea7c53790a0`; historical flat UX blob `d7424293500431917e0732db7e975a881be18cbd`. Main revamp blob observed earlier `47e5479b7dd845a109dc4a0bc271a5b165bf6caa`.
- [Current revamp roadmap at PR head](https://github.com/Mikeyphw/MikeysHookLab/blob/ccb228f7faaaf94dc55c12c52a34c87b398be4f5/docs/roadmaps/hooklab-ux-revamp.md) and [historical flat UX roadmap](https://github.com/Mikeyphw/MikeysHookLab/blob/ccb228f7faaaf94dc55c12c52a34c87b398be4f5/docs/roadmaps/hooklab-flat-ux.md) remain authoritative for existing scope. This proposal supplements them only after adoption. Re-fetch before each overlay.
- PR #30/R00, #31/R01, #32/R02 merged. Branch roadmap reports R00–R02 source-complete/hosted-qualified (historical claims, not independently re-audited here). PR #33 exact-head check snapshot: Android build/unit/lint, native visual candidate, frozen design, shared APK build and CI24 shard summary report success; extended aggregate fails; api36-840, api36-390, api36-landscape, api36-tablet-landscape fail. Other six listed adaptive cases pass. These are check-run conclusions, not independent receipt/artifact qualification. F05 appears in prior host context but no published successor was observed; reconcile real native transaction before resuming.
- Prior nine-overlay tranche merged; device-inclusive acceptance not established. Broader HookLab project coverage inconclusive; do not count unrelated programs in this denominator.
- Contract `/Agent Contracts/ai_agent_operating_contract_cg.md`, AGENT-OPERATIONS-DEVTOOL v1.25, Library item `file_00000000b5b0820e82a6bff5cb1ba2b5`, Library version 25, SHA-256 `850a858e863bb2715da7f9e09bd26ede3d7f81231f7da05db3b381b6cfb7df76`; raw file hash matches V125 receipt. Operator v1.8, delivery v1.15, roadmap v1.1 hashes also matched. Full byte materialization and relevant sections inspected; exhaustive line-by-line contract/validator/Devtool ledger review not completed in this planning session. Mandatory before implementation.

## Technical references (design constraints, not device proof)

- [libxposed API 102 XposedInterface](https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.html): framework remote preferences and `openRemoteFile`; actual installed service capabilities must be qualified.
- [Android InMemoryDexClassLoader](https://developer.android.com/reference/dalvik/system/InMemoryDexClassLoader), [Android 14 dynamic code restrictions](https://developer.android.com/about/versions/14/behavior-changes-14), [Android dynamic loading risks](https://developer.android.com/privacy-and-security/risks/dynamic-code-loading): verified bounded DEX; classloader is not a security sandbox.
- [GitHub release asset API](https://docs.github.com/en/rest/releases/assets), [Actions artifact API](https://docs.github.com/en/rest/actions/artifacts): optional repository-restricted read-only PAT; Actions read for artifact endpoints; handle redirects and expiration.
