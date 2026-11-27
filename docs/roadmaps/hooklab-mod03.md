# MOD-03 — Native Module UI & Configuration

Consolidated source work from merged PR #37 (`1c8ed7f2a1605fe47e7576e0855ecbdc083c8005`, tree `2cfec47fe369abf1448e5ea8dd5c8b38c7cf8774`). Hosted PR run 46002218234 succeeded; merged-main run 46004449579 failed API29 compact at the missing terminal result during `SEARCH_ROTATE_RETURN_SETTLE`. The root cause is not independently proven. Rotation requests now use a bounded host-mediated bridge with source/process/nonce/phase identity and strict terminal-result enforcement. No rerun masks the original failure.

## Production scope and evidence boundaries

| Promise | Source path | Qualification |
|---|---|---|
| P01 | MainActivity existing group/by-app catalog, installed module action and exact group/hook module routes | Existing adaptive checks plus module Activity fixture; device pending |
| P02 | PackManagerActivity Overview/Configure/Logs/Versions, PackStore verified catalog and independent hook flags | JVM registry tests and actual hosted module screens; device pending |
| P03 | ModuleUiProvider native ABI, VerifiedPackLoader and exact per-session reviewed archive, compiled Word Master fallback, sample personal provider | Signed parser tests, hosted real personal DEX/Activity approval/cancel/close/recreate; hosted pending |
| P04 | Trust-only alternative explicitly disclosed before executing personally authored code; test-channel UI cannot execute; no PAT feature | Negative admission tests; **not a sandbox or credential isolation**; PAT remains gated until separate UID/security decision |
| P05 | WordMasterUiProvider typed switch/choice/slider/preview and reviewed save | JVM model tests; actual remote readback and device pending |
| P06 | ModuleConfig strict versioned namespaced document, named profiles, per-app override and FrameworkPackRemote conflict/readback | JVM and device pending; only reviewed Word Master schema, no arbitrary setting writes |
| P07 | WordMasterNightHook uses selected typed settings at restart; legacy signed v1 defaults unchanged | Runtime projection tests; physical restart observation pending; no live apply claim |
| P08 | SAF reviewed import, export of sanitized config, reset and previous snapshot undo | JVM malformed/import tests; hosted and device pending |
| P09 | PackRuntime structured archive/version/generation/hook marker, DiagnosticTimeline parsing and module filtering | JVM negative/stale identity tests; signed execution on all target apps pending |
| P10 | ModuleUiResources bounded namespaced localized strings and explicitly typed assets, Go and Java independent signed ZIP admission | Cross-language digest/namespace/type/locale tests; native provider fixture pending |
| P11 | Separate MOD-03 candidate browser reference and native screenshot contract, exact source/run/APK binding | Existing historical thresholds remain unchanged; new candidate and eventual independent visual approval pending |

UI ZIP extension: legacy five-entry format remains supported by the updated manager. Optional `ui/classes.dex` and `ui/resources.json` are included in the signed digest inventory; UI classes must live in `com.mikeyphw.hooklab.moduleui.<id>` with the exact `Provider` entrypoint. Never execute test-channel UI. Older manager builds may reject extended archives: update manager first. `hooklabctl module pack` accepts optional `--ui-dex PATH --ui-resources PATH` after destination. Hosted Word Master 1.1.0 artifacts include unsigned personal UI inputs; review source and sign locally. The ephemeral personal emulator fixture is not distributed. Never approve unreviewed code; same UID permits access to manager data and privileges. No PAT or token storage/access is added by MOD-03.

Tree-bound main-push reuse is restricted to successful exact same-tree same-repository merged PRs, matching base, exact workflow/attempt/roster and independently verified artifact bytes. Source, Android, design and native visual jobs remain fresh; eligible Go, CI24, terminal and adaptive evidence may be reused with original PR identity, not relabeled. Any missing, failed, inconsistent or stale evidence falls back to a fresh plan. Final verification independently re-admits evidence. Direct/squash/rebase pushes without the required exact two-parent merge proof run fresh. Existing CI acceptance and device/release permissions are not weakened.

Physical predecessor evidence: user reported all hooks working after enabling individual toggles; supplied log proves signed Neo Store ObtainX 1.0.0 install only, other hook channel and remaining signed runtime, revoke/rollback/recovery and release evidence not established. Do not infer running status from local pack selection or tests.
