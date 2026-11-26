# HookLab — Final UI/UX Revamp & Native Fidelity Roadmap

**Roadmap ID:** `HOOKLAB-UX-REVAMP` · **Revision:** 1.14-candidate · **Prepared:** 2026-11-23 (America/Sao_Paulo)
**Status:** UX-R00 merged PR #30 at `b82ba80020920cdc840968a726cf81b707dd09b3`; qualified F08 tree `e1e95913842dac6347a930b5a74baba87d6ee066`. UX-R01/F01 merged PR #31 at `b37a4a27447cbdcfaabda08da90942d1de83f5e3`; source-bound hosted qualification complete; physical acceptance pending. UX-R02 merged PR #32 at `a76361eacad31ec072c3ef13563ea26be5b380a2`; qualified tree `26fa5c749deb9b0a1d7168d946030a7da6688b62`; 4 hosted workflows and CI24 224 tests. UX-R03-F01 + UX-R04 delivered on PR #33 at `29644ecdf752f7fae405696f1477d3e8cf84b640`; Android and CI24 workflows succeeded, Design/native/extended failed. UX-R04-F01 was delivered on PR #33 at `d40521c183f43e97b6e3f14556409a3fc6ba1086`: Android, Design, Native UI and CI24 workflows succeeded; Extended UI failed (1/10 matrix cases). UX-R04-F02 delivered on PR #33 at `68a9269ef41cfdfb3d24ffbd38e1b57017e7848e`; Android, Design and CI24 succeeded; Native UI and Extended UI failed. UX-R04-F03 delivered on PR #33 at `20724a6995b7ba8c8bd3e7c08254831fc619509a`; Android, Design, Native UI and CI24 passed; Extended UI failed. UX-R04-F04 delivered on PR #33 at `ccb228f7faaaf94dc55c12c52a34c87b398be4f5`; four hosted workflows passed, extended 6/10. UX-R04-F05 is the forward repair; no successor acceptance inferred.
**Proposed repository path:** `docs/roadmaps/hooklab-ux-revamp.md` (retain `hooklab-flat-ux.md` and its historical acceptance matrix).  
**Operator:** continue the existing `devop-hooklab` cumulative command; do not create an independent executable for this roadmap.  

**2026-11-26 reconciliation:** PR #33 merged at `6306d05d369824ace13ce5fb746269acb4f844ef`, F05 head `424d8587efa3087c6b55f09fe2ccee3cd33b878a`; R00–R04 merged, device-inclusive acceptance pending. The combined tranche is tracked in `hooklab-modular.json`. Older status below is historical evidence, not the current dashboard.

## 1. Source identity and reconciliation

| Item | Verified identity |
|---|---|
| HookLab | [Mikeyphw/MikeysHookLab](https://github.com/Mikeyphw/MikeysHookLab), main `2f313c2664189713415f1986e723ce31c7559736` |
| HookLab tree | `30b534c7de3d750b99878753564bf34eeace12c4` |
| Existing roadmap | [HOOKLAB-FLAT-UX v1.0](https://github.com/Mikeyphw/MikeysHookLab/blob/2f313c2664189713415f1986e723ce31c7559736/docs/roadmaps/hooklab-flat-ux.md), Git blob `580075d35474bd3349f5796a9afd3c5612009e93` |
| Cinderbox workflow precedent | [Mikeyphw/Cinderbox-Companion](https://github.com/Mikeyphw/Cinderbox-Companion), master `2806231c51ad4060b5429abcc15dadaefe543a70` |
| Operating contract | `/Agent Contracts/ai_agent_operating_contract_cg.md`, AGENT-OPERATIONS-DEVTOOL v1.15, Library file version 15, SHA-256 `cb3963ba70dd90001d04b07eb11c12cb6f29fad3d9de5fb19dda3952e1596100` |
| Visual reference HTML | `hooklab_ux_revamp_cg.html`, SHA-256 `8df26c67823ad29bae220ff18ae86d685f5ad27ed87d94ba6bc607d6e0cb3a2d` |
| Vector board SVG | `hooklab_ux_revamp_cg.svg`, SHA-256 `59ca31cb43ed09fdd10de3739837f85c3ab522e85e32474831e8285021e578af` |

Both reference files were rehashed from the current workspace; XML inspection confirms the actual 24 labels and 390 × 844 dp concept. SVG is a design board, not a device capture. HTML contains simulated state, not native LSPosed operations. The newer design is proposed to replace the old visual reference prospectively after adoption; historical source and receipts are preserved.

**Observed source:** HookLab is Java + AppCompat/Material Views, minSdk 29, targetSdk 36, compileSdk 37.0, build tools 36.0.0, version 0.7.1/code 10 at baseline. `MainActivity` currently uses `hostIcon`, `catalogCard`, `renderCatalogResults`, `showApp`, `showGroup`, `groupDetail`, `appDetail`, `hookHealthPanel`; existing `hostIcon` uses PackageManager. `HookGroupCatalog` and `HookDetailRoute` supply stable IDs. `FrameworkStateStore` owns authoritative asynchronous framework reads/writes. The existing Android workflow builds/tests/lints and verifies debug APK provenance; it does not run an emulator. These facts are not a claim of full source completeness.

## 2. Progress and complete active pipeline

**Coverage:** complete for the six proposed revamp overlays; known subset of the wider HookLab project.  
**Source implementation:** 3/6 merged; R03/R04 delivered on PR #33; F04 delivered; F05 repairs measured chrome, landscape and capture-dialog failures; all fresh runtime gates pending `[████░░░░░░░░]`
**Independently hosted-qualified:** 3/6; full device-inclusive acceptance 0/6 `[░░░░░░░░░░░░]`
**Delivered/merged:** 3/6 delivered and merged (PR #30, #31, #32); **remaining source implementation:** 3 (R03 candidate included); **remaining device-inclusive acceptance:** 6. All promise counts below describe planned scope, not code coverage. Existing nine-overlay tranche: 9/9 merged, 0/9 fully accepted on evidence inspected; complete source count inconclusive (known DX gaps). Not a project-wide percentage.

| ID | Overlay | Implementation / acceptance | Dependency |
|---|---|---|---|
| UX-R00 | Design Contract & Emulator Infrastructure | SOURCE COMPLETE, MERGED / HOSTED FOUNDATION PASS; DEVICE PENDING | None; source and design identities must be confirmed. |
| UX-R01 | Diagnostics Consolidation | SOURCE COMPLETE, MERGED PR #31 / HOSTED QUALIFIED; DEVICE PENDING | UX-R00; shared row and route interface coordinated with UX-R02. |
| UX-R02 | Navigation & Interaction Redesign | SOURCE COMPLETE, MERGED PR #32 / HOSTED QUALIFIED; DEVICE PENDING | UX-R00; coordinate UX-R01 shared row/icon primitives. |
| UX-R03 | Home & Whole-App Visual Fidelity | DELIVERED; F01 ANDROID/NATIVE/DESIGN PASS; EXACT SUCCESSOR ACCEPTANCE PENDING; DEVICE PENDING | UX-R01 and UX-R02; UX-R00 reference gates. |
| UX-R04 | Accessibility, Adaptive Layout & Lifecycle | DELIVERED; F04 NATIVE BASELINE PASSED, EXTENDED 6/10 / F05 REPAIR | UX-R03; early checks run throughout R00–R03. |
| UX-R05 | Full Native Visual Acceptance & Regression Enforcement | PLANNED / PENDING | UX-R00–R04 source-complete and exact-source hosted results; reference changes reviewed. |

Current focus: **UX-R04-F05**, PR #33 exact F04 head `ccb228f7faaaf94dc55c12c52a34c87b398be4f5`, tree `83f22d0d891342a25dccd2bfa7aaccc8a2f5eecb`, main `a76361eacad31ec072c3ef13563ea26be5b380a2`. F04 Android 45735129813, Design 45735129781, Native UI 45735129752, CI24 45735129826 passed; Extended UI 45735129770 failed with six successful matrix cases and the shared build. Tablet landscape navigation rail targets measured 59px and overflow 80px against 96px minimum; 840dp overflow 80px; landscape launcher did not converge before Activity launch; API36-390 all five tests passed but process-death research choice was unavailable. Preserve all exact ten cases, shared APK build and twelve jobs/artifacts, full root accessibility checks, real process death and all six predecessor transactions. UX-R05 remains gated.

### Original tranche — retain all nine obligations

| ID | Exact name | PR | Remaining qualification |
|---|---|---|---|
| DS-01 | Flat Design System & Adaptive Native Shell | [#14](https://github.com/Mikeyphw/MikeysHookLab/pull/14) merged | Device visual/theme/accessibility |
| UI-04 | Hook Groups & Group/App Catalog | [#15](https://github.com/Mikeyphw/MikeysHookLab/pull/15) merged | Stable identity, restore, navigation |
| UI-05 | Group/Hook Detail & Independent LSPosed Setup | [#16](https://github.com/Mikeyphw/MikeysHookLab/pull/16) merged | Real framework and negative paths |
| UI-06 | Home Dashboard, Readiness & Recent Activity | [#18](https://github.com/Mikeyphw/MikeysHookLab/pull/18) merged | Device readiness and evidence |
| DX-01 | Unified Diagnostics Timeline, Log Adapters & Export | [#17](https://github.com/Mikeyphw/MikeysHookLab/pull/17) merged | Source gaps: live capability-gated adapters, persistence and full filtering; device proof |
| DX-02 | Guided Capture Sessions & Hook Discovery | [#19](https://github.com/Mikeyphw/MikeysHookLab/pull/19) merged | Import-only; missing deeper capability/metadata and persistence proof; runtime |
| DX-03 | Hook Health, Troubleshooting & Evidence Explorer | [#20](https://github.com/Mikeyphw/MikeysHookLab/pull/20) merged | Real evidence and collector gaps, device |
| UI-07 | Settings, Scope, MCC Control, About & Help Redesign | [#21](https://github.com/Mikeyphw/MikeysHookLab/pull/21) merged | Root denial, scope, signer, accessibility |
| UI-08 | Accessibility, Regression, Performance & Legacy UI Retirement | [#22](https://github.com/Mikeyphw/MikeysHookLab/pull/22) merged | Full device acceptance; source CI run 45285209237 succeeded for head 03a4bb75b45a5cdff4d3a91eae80094827f02fca; exact attempt qualification not independently audited here |

The old roadmap header and UI-08 candidate row are stale: PR #22 merged at `9dc3e14dbd69bcdcc4048984466b578a29c6455a`; CGT01-P04 PR #23 then merged at current main. Reconcile these in R00 without equating merge to acceptance. Re-read `docs/qa/hooklab-flat-ux-acceptance.md`, preserve all 38 old promise IDs, map each to new tests and actual evidence or open gap. The revamp is UI fidelity, not automatic completion of DX source gaps. Any further collector/persistence program needs explicitly scoped, safe implementation, independent authorization and its own source/runtime acceptance; if original promises remain open, leave them open rather than declaring them superseded.

### Known cross-project obligations, outside the six-overlay denominator

M6 — MCC Manual Diamond Control; M7 — Neo Store + Droid-ify ObtainX Integration; M8 — LSPosed Scope Recommendations & Optional Verbose Event Logging; UI-01/02 — Material 3 Control Center & Per-App Setup (historical visual layer superseded, behavior preserved); UI-03 — Asynchronous Framework State & Material You; WM01 — Word Master Experimental Night Filter; CGT01-P01 — Static ChatGPT Code-Block Renderer Mapping; CGT01-P02 — Read-Only Code-Block Clipboard Value-Parity Probe; CGT01-P03 — Read-Only Native Callback Identity Probe; CGT01-P04 — Valdi/DIL & Preview Identity Coverage (PR #23 merged, actual coverage not certified); CGT01-P05 — earlier roadmap called Open in Termux Action Confirmation, newer user direction is Copy Action Causality; reconcile authoritative integration roadmap before implementation; TB01 — Native Termux Command Bridge (PR #13 open, head `8a90331711e70af3489a6d89af3c65b851fdb17c`, base reconciliation required; no replay from missing XDG history). HOOKLAB-TINDER is an independent proposed program, not included. No invented installed integration or Termux execution UI. Wider roadmap coverage inconclusive.

## 3. Native product design contract

- Keep three main destinations Home / Hooks / Diagnostics; settings via top action; large displays use adaptive rail. Deep routes use stable IDs and Android back semantics. No WebView or screenshot-based implementation.
- Native Material views and reusable components: flat tonal panels, no elevation/ordinary outlines, touch ripple, separate expansion targets, meaningful status text. Extract exact tokens from HTML rather than copying older colors by assumption. Browser concept uses 390×844 dp; map app content bounds and insets explicitly. 393dp is an additional native configuration, not silently rescaled 390dp reference.
- Groups and app cards navigate; independent chevron expands. Single actual hook group/app may directly open hook; multi-hook app routes to an actual app list even if search leaves one match. Scope, toggles, root and safety locks remain independent explicit controls. Genuine PackageManager icons with bounded cache and truthful missing/unknown state; prototype vectors are illustrative.
- Diagnostics: three separate workspaces Timeline / Health / Capture, compact lists, contextual filters/import/export, dedicated reports. Import-only truth remains until a real capability-gated collector is implemented and qualified. No fake previous-session history, runtime event or claim of authenticated execution. Prototype scenario controls and design watermarks never ship.
- Settings, Scope, MCC, About, Help and Privacy preserve existing real behavior, state, source provenance and sensitive authorization. Native accessibility, font scaling, insets and keyboard take priority over fixed-size browser text when necessary; document narrow approved differences, not sweeping exemptions.

## 4. Overlay specification and per-promise plan

The tables below preserve the original planned promises; current implementation and evidence supersede historical status in the dated reconciliation at the end. Paths in original plans are intentions, not proof. Each row must eventually get exact source entry points, positive/negative/lifecycle tests, actual evidence and independent implementation/acceptance verdicts. Source-complete only if every promise is implemented through real callers and all accessible checks pass. No overlay source changes are authorized by this document alone.

### UX-R00 — Design Contract & Emulator Infrastructure

**Goal:** Establish a trustworthy, fail-closed test system before redesigning production UI.  
**Prerequisites:** None; source and design identities must be confirmed.  
**Integration:** design/hooklab/, app/build.gradle, app/src/androidTest/, scripts/qa/, .github/workflows/  
**Promise count:** 11 · 0 source-complete asserted · 0 accepted.

| Promise ID | Outcome | Production implementation plan | Required proof / negative paths |
|---|---|---|---|
| UXR00-P01 | Freeze HTML and SVG | Store exact original bytes, SHA-256, approved revision, licensing and screen roster; read-only CI hash check | Tamper either file or manifest; reject mismatch; verify all 24 SVG labels. |
| UXR00-P02 | Generate deterministic browser references | Pinned Chromium/Playwright/fonts, viewport 390×844 CSS px = target dp concept, deterministic locale/clock/theme; capture each approved state without editing design | Repeat captures twice; verify PNG dimensions, stable geometry, semantic IDs and zero missing/duplicate cases. |
| UXR00-P03 | Build a machine-readable contract | Extract layout bounds/tokens/text/control roles/order, explicit state and screen fixtures, native applicability, viewport mapping, masks and exceptions | Schema negative tests for unknown ID, absent controls, blanket masks, duplicated cases or contradictory geometry. |
| UXR00-P04 | Native instrumentation runner | Add AndroidJUnitRunner, Espresso, UI Automator and accessibility test dependencies compatible with Java/Material Views; launch actual MainActivity | Build debug + androidTest on GitHub; assert component and package identity; fail on missing tests or instrumentation failure. |
| UXR00-P05 | Hermetic hosted emulator | Reuse Cinderbox API36/KVM pattern; fail closed on wrong serial/API, boot failure, low disk, stale install, missing offline gate; no release secrets | Run fresh emulator API36, verify ro.kernel.qemu, source, SDK/emulator revisions, installed app bytes and test APK hashes. |
| UXR00-P06 | Real UI fixture seam | Inject deterministic framework snapshot/command adapter, clock, diagnostic data and target-package catalog via instrumentation/debug-only access to production state presenters; no alternative Activity or release backdoor | Exercise actual disconnected launch plus injected success/denial/stale fixtures; assert release APK excludes fixture provider and triggers. |
| UXR00-P07 | Native screenshot and hierarchy capture | Capture actual screen through instrumentation/ADB; export allowed PNG, semantic hierarchy and geometry after idle; include system/app insets separately | Reject blank or duplicate/wrong-screen screenshots; verify expected stable route and dimensions, no private logs. |
| UXR00-P08 | Comparison and independent verifier | Compare browser layout to native and calibrate region-based metrics; fail on missing evidence; report candidate fidelity without auto-approving goldens | Synthetic negative images, hidden control, wrong label, shifted card, altered colors, stale SHA, missing case or skipped test all fail. |
| UXR00-P09 | Workflow wiring and baseline audit | Add PR design and native smoke jobs plus extended/review workflow skeletons; register case ownership and strict staged admission | Exact-head baseline captures and test roster evidence. Pending screen parity is visible, cannot count as accepted; smoke regressions fail. |
| UXR00-P10 | Source-bound evidence and privacy | Manifest app/test APK hashes, installed APK, cert, reference hashes, commit/tree, run/attempt, environment, test roster, artifact hashes; allowlist evidence | Independently rehash PNGs, validate schema, reject stale attempts, logs with content or fake fixture runtime claims. |
| UXR00-P11 | Native delivery integration | Update sole cumulative devop-hooklab operator and registry; inspect current Devtool CLI/launcher/manifests; no local Gradle | Native preflight/check/apply/commit/session/PR/CI/watch with real receipts; failure/resume/duplicate prevention verified on host. |

**Exit gate:** exact-source local structural tests (no Gradle), native Devtool lifecycle, required hosted checks and independent evidence for every assigned test; device gates as applicable. Never mark future screens approved in this overlay. **Rollback:** revert cohesive commit through reviewed native workflow; preserve persisted hook IDs/preferences, prior approved references, native sessions and evidence; never force-reset or replay a confirmed transaction.

### UX-R01 — Diagnostics Consolidation

**Goal:** Separate three workspaces without losing the existing evidence and safety functions.  
**Prerequisites:** UX-R00; shared row and route interface coordinated with UX-R02.  
**Integration:** MainActivity diagnostics routes; diagnostic store/import/export, HookHealthModel, capture state; shared native components.  
**Promise count:** 12 · 0 source-complete asserted · 0 accepted.

| Promise ID | Outcome | Production implementation plan | Required proof / negative paths |
|---|---|---|---|
| UXR01-P01 | Three independent workspaces | Timeline, Health, Capture tabs with only selected content attached; stable selected tab | Switch all directions, assert other workspace controls absent; back, rotate, process death restore safe state. |
| UXR01-P02 | Consolidated import | Single Import logs action opens native sheet for supported source type and SAF picker | Select each source, cancel, deny, malformed/oversize import; no unapproved live collector or hidden root. |
| UXR01-P03 | Compact filter sheet | Group/app/hook, severity/source, time where supported and text query; draft vs applied state, clear and active count | Apply/cancel/reset, multi-criteria and zero results; compare real dataset IDs; unsupported filter must be implemented or clearly unavailable, never decorative. |
| UXR01-P04 | Chronological timeline | Compact event rows, time/source/severity and import qualifier, bounded paging or virtualized list | Order mixed times, missing clock, duplicate text, dropped events and 4000 retained events; no invented timestamps. |
| UXR01-P05 | Event inspector | Open selected stable event identity with source hash/line, correlation and redacted payload | Select equal-text records, verify correct ID and provenance; stale selection fails safely. |
| UXR01-P06 | Compact Health rows | Clickable per-hook summaries, shared icon component, no full dimensions inline | Single tap opens exact report; same-package hooks distinct, missing app/unknown status accurate. |
| UXR01-P07 | Individual health report | Independent framework/scope/prefs/version/lock/evidence/collector dimensions and safe suggested actions | Unknown, denied, disabled, incompatible, locked and no evidence paths; no false ACTIVE. |
| UXR01-P08 | Guided capture | Landing → target/source setup → one bounded session → report; real import-only sources | Start/stop/cancel, timeout, duplicate start, rotation, interruption, no silent collector. |
| UXR01-P09 | Action markers and capture report | Markers are operator annotations, bound to session; real evidence counts and attribution, safe export | Two sessions isolated, marker cap, elapsed clock, process loss, report consistency and no synthetic execution claims. |
| UXR01-P10 | Contextual copy/export | Selection-specific toolbar and redaction preview, SAF zip/jsonl with provenance/hash; explicit clipboard action only | Cancel, denied write, exact selection, manifest rehash, unrelated clipboard remains untouched; failure cannot report success. |
| UXR01-P11 | Navigation and state restoration | Preserve tab, filter, selected IDs, paging/scroll where safe; process death handles in-memory loss truthfully | Back from inspector/report/setup, recreate, invalid IDs, capture interrupted, stale results and IME focus. |
| UXR01-P12 | Retire clutter and audit actual call chains | Remove duplicated inline controls and dead navigation paths; keep existing diagnostic authority and explicit sensitive controls | UI hierarchy absence assertions; real existing import/capture/health regression, privacy and no-mutation spies. |

**Exit gate:** exact-source local structural tests (no Gradle), native Devtool lifecycle, required hosted checks and independent evidence for every assigned test; device gates as applicable. Never mark future screens approved in this overlay. **Rollback:** revert cohesive commit through reviewed native workflow; preserve persisted hook IDs/preferences, prior approved references, native sessions and evidence; never force-reset or replay a confirmed transaction.

### UX-R02 — Navigation & Interaction Redesign

**Goal:** Make catalog and cross-app navigation native, compact, stable-ID based and safe.  
**Prerequisites:** UX-R00; coordinate UX-R01 shared row/icon primitives.  
**Integration:** MainActivity catalogCard/renderCatalogResults/showApp/showGroup/groupDetail/appDetail/hostIcon; HookGroupCatalog, HookDetailRoute; shared FlatUi primitives.  
**Promise count:** 15 · 0 source-complete asserted · 0 accepted.

| Promise ID | Outcome | Production implementation plan | Required proof / negative paths |
|---|---|---|---|
| UXR02-P01 | Clickable group headers | All non-action area of group header routes to group details for multi-hook groups | Tap center/title/icon/whitespace, assert correct group and no state mutation. |
| UXR02-P02 | Independent expansion chevrons | Separate 48dp child action toggles expansion only, label announces expanded state | Tap chevron; route unchanged; parent tap does not expand; TalkBack focus distinct. |
| UXR02-P03 | Single-hook group shortcut | Actual registered group cardinality one routes to exact hook configuration; retain group context through detail link | Search narrowing multi-hook group does not trigger shortcut; invalid or unregistered ID fails closed. |
| UXR02-P04 | Clickable individual hook cards | Catalog and group detail rows route by stable hook ID; remove Configure buttons | Tap icon/text/empty space for every registered hook; no first-package fallback. |
| UXR02-P05 | Correct application navigation | One actual registered hook → exact hook, multiple → app-specific list (not first visible search hit) | Two-hook same-package fixture, filtered to one result, still opens list; exact back destination. |
| UXR02-P06 | Actual installed application icons | Extend hostIcon via PackageManager/application metadata; target package visibility scoped to catalog | Install harmless fixture packages; compare drawable identity; no internet lookup or broad package query permission. |
| UXR02-P07 | Deduplicated group icon stacks | Deterministic ordering, unique target packages, capped stack and meaningful label | Single/multi/many target fixture, duplicate hooks in one package, RTL and missing icon. |
| UXR02-P08 | Missing-app placeholders | Differentiate confirmed not installed from visibility/query failure; HookLab fallback, truthful availability | NameNotFound/SecurityException and unknown metadata, install/uninstall transition. |
| UXR02-P09 | Consistent icons and labels | Home, Hooks, group targets, hook setup, Scope and Health use shared provider and semantic naming | Screenshot/semantic assertions across all screens, no duplicate TalkBack announcements. |
| UXR02-P10 | Route and Settings back-stack | Stable route IDs, explicit back origin, no loops; restore selected group/app/hook; settings deep links | Home/Hooks/Health/Scope entry, Back and process recreation, invalid restored IDs. |
| UXR02-P11 | Native ripple and accessibility | Selectable/ripple foreground with pressed state, keyboard focus, separate accessible actions, 48dp targets | Touch bounds, keyboard/DPAD, accessibility checks and physical TalkBack pending. |
| UXR02-P12 | Remove redundant buttons and obsolete callers | Remove View group setup, Configure, Expand hooks ordinary card buttons; migrate actual callers/guards/docs | Hierarchy forbidden-label assertions in catalog, source reachability and no stale legacy paths. |
| UXR02-P13 | Preserve catalog state | Query, view, expansion, focus/IME, scroll and filter survive intended navigation/recreation | Search while framework state changes, rapid taps, rotation, app restart and large font. |
| UXR02-P14 | Navigation cannot mutate sensitive state | Only explicit configured controls can write scope/prefs/lock or enter root operation; no destructive long press | Command spy records zero calls on every card/navigation action; explicit opt-in/confirmation and failure readback tests. |
| UXR02-P15 | Bounded icon cache | Cache drawable state by package/version/configuration; invalidate install/remove/change and density/theme changes; bounded memory | Package update, process rotation, eviction, missing→installed transitions and no stale drawable. |

**Exit gate:** exact-source local structural tests (no Gradle), native Devtool lifecycle, required hosted checks and independent evidence for every assigned test; device gates as applicable. Never mark future screens approved in this overlay. **Rollback:** revert cohesive commit through reviewed native workflow; preserve persisted hook IDs/preferences, prior approved references, native sessions and evidence; never force-reset or replay a confirmed transaction.

### UX-R03 — Home & Whole-App Visual Fidelity

**Goal:** Complete all remaining screen layouts and visual tokens; preserve native behavior.  
**Prerequisites:** UX-R01 and UX-R02; UX-R00 reference gates.  
**Integration:** FlatUi, theme resources, MainActivity/controllers, native Settings/Scope/MCC/About/Help/privacy; strings and design manifests.  
**Promise count:** 10 · 0 source-complete asserted · 0 accepted.

| Promise ID | Outcome | Production implementation plan | Required proof / negative paths |
|---|---|---|---|
| UXR03-P01 | Shared design tokens | Unify dark/light/AMOLED canvas, surfaces, type, 8dp spacing rhythm, shapes, semantic status colors and no raised ordinary cards | Extract frozen reference tokens, compare native resources and computed views; exact fixed colors, contrast checks. |
| UXR03-P02 | Home dashboard | Reference-matched hierarchy, real readiness/counts, compact attention rows and quick routes | Connected/disconnected/unknown/empty states, unique app counts, no fabricated events; screenshot + click paths. |
| UXR03-P03 | Hooks density and chrome | Match approved Groups/By app hierarchy, native search, group/icon/card rhythm and action placement | Both views, expanded/collapsed/multi-app, zero results; geometry/visual checks against VIS-002/003. |
| UXR03-P04 | Group and hook detail | Match reference detail hierarchy and independent setup; only explicit switches/scope/safety buttons mutate | Correct ID/version/readback, deny/fail/restart, no scope grant from group. |
| UXR03-P05 | Settings and appearance | Native settings rows; system/dark/light/AMOLED and Material You optional, persist user choice | Theme changes, recreate/relaunch, fixed theme palette and dynamic color invariants. |
| UXR03-P06 | Scope and permissions | Native app list, icon/name/status and explicit separate controls; framework remains authority | Pending/denied/unknown, cancellation and unrelated packages untouched; no automatic grant. |
| UXR03-P07 | MCC manual control | Reference layout over existing real manual/preflight path, root-sensitive action remains confirmed and single-flight | Cancel, unavailable storage/root and denied operation; no real writes in emulator, physical owner-authorized gate separate. |
| UXR03-P08 | About, Help and Privacy | Reference sections and actual app/version/signer/source, collection/retention and redaction facts | Verify displayed source matches APK, unavailable signers/collectors, support links and no prototype-only controls. |
| UXR03-P09 | Native app shell and sheets | Material top bars, exactly three bottom destinations, rail at approved breakpoints, native dialogs/sheets and insets | Screenshot and hierarchy compare, edge-to-edge API36, system Back, IME and dialog cancellation. |
| UXR03-P10 | Reference parity closure | Update each affected case with exact geometry, text, actionable paths and per-region fidelity; explicitly retire stale legacy UI tests | All assigned cases captured and compared, no stale wrappers or unreviewed platform exceptions. |

**Exit gate:** exact-source local structural tests (no Gradle), native Devtool lifecycle, required hosted checks and independent evidence for every assigned test; device gates as applicable. Never mark future screens approved in this overlay. **Rollback:** revert cohesive commit through reviewed native workflow; preserve persisted hook IDs/preferences, prior approved references, native sessions and evidence; never force-reset or replay a confirmed transaction.

### UX-R04 — Accessibility, Adaptive Layout & Lifecycle

**Goal:** Make reference UI usable on supported Android configurations and lifecycle transitions.  
**Prerequisites:** UX-R03; early checks run throughout R00–R03.  
**Integration:** All production screens and components; Activity saved state, accessibility hierarchy, async state and diagnostics store; instrumentation.  
**Promise count:** 10 · 0 source-complete asserted · 0 accepted.

| Promise ID | Outcome | Production implementation plan | Required proof / negative paths |
|---|---|---|---|
| UXR04-P01 | Small-width adaptive layouts | 320/360/390/393dp phone, 600/840dp tablet and landscape; scroll instead of clipping; navigation rail as appropriate | Bounds, screenshot, keyboard and no invisible actions; both orientations. |
| UXR04-P02 | Large text and scaling | 100/160/200% font, wrapping rather than shrinking core text; all safety warnings reachable | All critical routes at small + large fonts; no ellipsis on mandatory information, scroll to actions. |
| UXR04-P03 | Accessibility semantics | Native headings, meaningful content descriptions, traversal, selected/expanded/checked state, accessible announcements | Espresso accessibility checks + UIAutomator hierarchy, real TalkBack manual acceptance separately. |
| UXR04-P04 | Touch and focus | 48dp interaction targets, distinct hit regions, keyboard/DPAD focus, IME safe area | Tap edges, keyboard traversal, focus restoration, no overlapping actions. |
| UXR04-P05 | Color and theme accessibility | Normal text ≥4.5:1, large text and relevant graphics ≥3:1; no color-only states, dynamic/system themes remain readable | Token contrast calculation plus native screenshots/measurements; dark/light/AMOLED/dynamic. |
| UXR04-P06 | Activity lifecycle restoration | Save safe route and UI state; orientation/recreate/process death with invalid IDs and expired sessions handled | Real kill/relaunch without test harness merely simulating state; bounded state, interrupted capture truthful. |
| UXR04-P07 | Async correctness | Preserve generation rejection, pending controls, lifecycle cancellation and readback; no optimistic writes | Delayed/out-of-order success/failure, disconnect and rapid navigation with fake transport plus clean emulator. |
| UXR04-P08 | Performance and memory | Bound list retention, screenshot tests separate from performance; baseline launch/frame timing, 4000 events, icon cache; no ANR | Measure repeated runs on pinned emulator, report p95 and peak memory; set evidence-based budgets before enforcing; physical device check separately. |
| UXR04-P09 | Supported API compatibility | API29 min and API36 target; API-dependent icons/insets/theme fallbacks; no new broad permissions | Install, run core journey on API29 and API36; dynamic color unavailable on old API handled. |
| UXR04-P10 | Failure and security regression | All denial, cancellation, stale callback and privacy invariants across adaptive screens | Negative cases, malformed imports, clipboard copy isolation, no hidden collection/root/Termux action. |

**Exit gate:** exact-source local structural tests (no Gradle), native Devtool lifecycle, required hosted checks and independent evidence for every assigned test; device gates as applicable. Never mark future screens approved in this overlay. **Rollback:** revert cohesive commit through reviewed native workflow; preserve persisted hook IDs/preferences, prior approved references, native sessions and evidence; never force-reset or replay a confirmed transaction.

### UX-R05 — Full Native Visual Acceptance & Regression Enforcement

**Goal:** Qualify the complete design contract on actual Android, enforce ongoing visual and functional regression prevention.  
**Prerequisites:** UX-R00–R04 source-complete and exact-source hosted results; reference changes reviewed.  
**Integration:** All design contracts, native goldens, CI workflows, independent verifier, governance ledger and device QA docs.  
**Promise count:** 11 · 0 source-complete asserted · 0 accepted.

| Promise ID | Outcome | Production implementation plan | Required proof / negative paths |
|---|---|---|---|
| UXR05-P01 | All reference cases native | Capture every VIS-001–024 actual production route or sheet at primary reference size, no fixture-only Activity | Exact roster with source-bound screenshots, hierarchy, fixture identity; missing/duplicate/blank/wrong case fails. |
| UXR05-P02 | Browser-to-native fidelity | All case-specific geometry/content/style comparisons within reviewed thresholds; no arbitrary scaling/cropping or broad masks | Per-region diff/triptychs and negative tests; approved exceptions limited and reviewed. |
| UXR05-P03 | Native golden admission | Human approve exact native candidates only after design fidelity and interactions pass; bind golden to reference/reviewer/source/environment | Separate explicit review PR; altered or unapproved golden/mask/threshold rejected. |
| UXR05-P04 | Strict native regression | Compare actual same-environment captures to immutable approved native PNGs and semantic contracts | Calibrated SSIM, changed-pixel ratio and regional checks; meaningful injected regressions fail. |
| UXR05-P05 | Full functional journeys | All navigation, diagnostics, settings and configuration safety tests run, including clean real Activity launch | Expected vs started/passed IDs exactly; no skip, retry-to-green or substituted mocks. |
| UXR05-P06 | Extended device matrix | Run API29/36, compact/tablet/landscape, 160/200% font and themes with explicit per-case coverage | Matrix receipts, per-shard status; missing shard fails corresponding qualification. |
| UXR05-P07 | Required PR enforcement | Always-run exact-head design + native aggregate checks; owner-authorized ruleset/branch protection with readback | Failing test blocks merge; skipped/cancelled/not-run/stale check cannot qualify; verify protection independently. |
| UXR05-P08 | Independent source-bound evidence | Verify SHA/tree, APK pair and installed identity, run/attempt, tests, screenshot hashes/geometry, reference and golden revisions | Reject forged, stale, partial or inconsistent receipt; publish sanitized report even on failure. |
| UXR05-P09 | Physical device UX and runtime QA | Android 16 real device, TalkBack, font/IME, system back, actual installed icons; real LSPosed scope/readback and imported evidence | Manual signed receipt by case, actual device/version/source; fixture results not runtime proof. |
| UXR05-P10 | Release qualification boundary | Only if separately authorized: protected stable signing, exact source provenance, known cert, versionCode/install compatibility; independent CI24 applicability | No release secrets in PRs, debug not stable; physical/release status remains pending or not authorized until observed. |
| UXR05-P11 | Close or preserve outstanding obligations | Map new tests to old 38-promise matrix, retain DX source gaps and independent integration roadmap; distinguish UX acceptance from runtime/release | Every old promise either requalified with actual receipt or explicit outstanding reason; no silent supersession. |

**Exit gate:** exact-source local structural tests (no Gradle), native Devtool lifecycle, required hosted checks and independent evidence for every assigned test; device gates as applicable. Never mark future screens approved in this overlay. **Rollback:** revert cohesive commit through reviewed native workflow; preserve persisted hook IDs/preferences, prior approved references, native sessions and evidence; never force-reset or replay a confirmed transaction.

## 5. Exact SVG inventory and screen ownership

Each case captures a named route, deterministic fixture, theme and viewport. The following labels were read from the actual SVG (not inferred from a previous summary). R00 establishes contracts for all; owner implements primary screen; R04 adds adaptive variants and R05 closes all coverage.

| Case | Exact reference label | Owner | Required state and journey |
|---|---|---|---|
| VIS-001 | Home | UX-R03 | Home connected sample; readiness and quick routes |
| VIS-002 | Hooks / Groups | UX-R02 | Groups view, expanded ObtainX |
| VIS-003 | Hooks / By app | UX-R02 | By app list; stable app membership |
| VIS-004 | Group detail | UX-R02 | Tap ObtainX group, independent targets/hooks |
| VIS-005 | Hook configuration | UX-R02 | Neo Store detail; explicit controls |
| VIS-006 | Diagnostics / Timeline | UX-R01 | Populated imported timeline |
| VIS-007 | Filter sheet | UX-R01 | Timeline → filter bottom sheet; apply/cancel |
| VIS-008 | Event inspector | UX-R01 | Tap MCC warning → exact inspector |
| VIS-009 | Diagnostics / Health | UX-R01 | Health selected; compact per-hook summaries |
| VIS-010 | Health report | UX-R01 | Tap MCC → independent health report |
| VIS-011 | Diagnostics / Capture | UX-R01 | Capture selected; no session |
| VIS-012 | Capture setup | UX-R01 | Open guided setup; select target/source |
| VIS-013 | Capture session | UX-R01 | Bounded running session with markers |
| VIS-014 | Session report | UX-R01 | Stop and review report |
| VIS-015 | Settings | UX-R03 | Appearance, framework and diagnostics sections |
| VIS-016 | Scope & permissions | UX-R03 | Target scope rows; independent request/remove |
| VIS-017 | MCC manual control | UX-R03 | Manual preflight and explicit confirmation |
| VIS-018 | About | UX-R03 | Identity/version/source/signing fact |
| VIS-019 | Help | UX-R03 | Contextual usage and limitations |
| VIS-020 | Privacy & logs | UX-R03 | Redaction/retention/copy/export truth |
| VIS-021 | Light theme | UX-R03 | Fixed light theme reference |
| VIS-022 | AMOLED / Empty timeline | UX-R01 | AMOLED + empty timeline (both dimensions checked separately too) |
| VIS-023 | Import sheet | UX-R01 | Single Import sheet; supported source options |
| VIS-024 | Disconnected framework | UX-R03 | Home disconnected; unknown status not off |


Additional cases (not substitutes for the 24): VIS-X01 multi-hook app list; X02 search empty; X03 missing app; X04 filter zero results; X05 import failure; X06 capture timeout/cancel; X07 process-loss interruption; X08 scope denied/pending; X09 failed/stale preference write; X10 unsupported version; X11 locked hook; X12 MCC preflight/root denial; X13 confirm/cancel; X14 system/dynamic theme; X15 export chooser/preview; X16 200% font/IME; X17 tablet rail; X18 actual clean disconnected launch. Add further cases if audit finds actual production routes not shown in SVG. Never label an unrepresented screen approved without its own reviewed contract.

## 6. Testing architecture & fixture design

### Reference governance and extraction

Proposed `design/hooklab/01_REFERENCE/{hooklab_ux_revamp.html,hooklab_ux_revamp.svg,reference-manifest.json,screenshots/}`; `02_CONTRACT/{design-tokens.json,screen-contracts.json,navigation-contract.json,interaction-contract.json,visual-tolerances.json,approved-platform-differences.json,acceptance-matrix.json}`; `03_CI/` for browser reference generation, native geometry/screenshot comparators, report generation and verifier; `04_NATIVE_GOLDENS/` for approved native PNGs keyed by API/profile/theme/font/width with approval manifest. Originals copied byte-for-byte, derived files separately hashed. Stable test IDs/semantic selectors in browser manifest and Android resource IDs/content semantics, not brittle coordinate taps. If browser automation requires adapter, implement external test adapter and record its hash; never mutate original reference silently. Use a pinned browser, fonts, locale, timezone, fixed time, density and theme. Record viewport plus app content/status/navigation regions; derive crop from known window insets, not automatic image alignment that hides errors.

### Two independent visual gates

1. **Design fidelity**: browser and SVG are design authority. Compare native semantic layout and per-region positions, content, state, tokens, typography, order, hierarchy and component metrics. Cross-renderer images produce diagnostics; don't demand byte-identical text antialiasing between Chromium and Android. Every exception must have bounded region, reason, reviewer, affected case and revision. Never mask main UI or missing controls. Actual installed icon differences are separately asserted using package fixtures; system clock/battery are controlled or narrowly masked. Strip only prototype watermark from target contract, not production UI.
2. **Regression fidelity**: only after design and functional fidelity pass, review actual Android native captures and freeze native goldens. Same emulator image, density, scale, locale, clock, theme and data are prerequisites. Compare SSIM, changed-pixel ratio, per-region metrics and exact semantic contract. No auto-regeneration on failure. A native golden cannot override a design failure.

Proposed fixed geometry ±2dp, padding ±2dp, radii ±1dp, font ±1sp where directly comparable; exact fixed colors sampled from interior pixels; minimum 48dp action targets; 4.5:1 normal text and 3:1 large text/relevant non-text graphics; zero missing controls, overlapping touch areas or clipped critical content. Use region-aware geometry rather than globally forcing text wrapping. Proposed native SSIM ≥0.99 and changed-pixel ratio ≤1% (RGB channel difference > 8/255), calibrated using repeated identical-run samples and meaningful deliberate regressions; freeze reviewed per-case values before enforcing. If calibration fails, fix nondeterminism or request narrow reviewed exception; no thresholds weakened by PR author to hide regression. Cross-browser-to-native threshold is not automatically native SSIM. Reports include reference/actual/diff triptychs, bounds and severity, exact case ID, source SHA and failed assertion.

### Deterministic fixtures, production-path requirement

| ID | State |
|---|---|
| F01 | Framework disconnected (also actual unmodified debug APK launch) |
| F02 | Connected, readback known |
| F03 | Per-target approved scope |
| F04 | Scope request pending/denied |
| F05 | Enabled and eligible configuration, not execution |
| F06 | Unsupported/unknown installed version |
| F07 | Safety lock/quarantine |
| F08 | Missing app / package-visibility error |
| F09 | Two registered hooks for same fixture app |
| F10 | Empty diagnostic timeline |
| F11 | Mixed-source bounded synthetic import |
| F12 | Running capture, markers, report |
| F13 | Failed remote preference write |
| F14 | MCC storage preflight denied/missing |
| F15 | Stale/out-of-order callbacks |

Use injection at actual state/transport boundaries, not a test Activity or copied layouts. Install harmless deterministic icon fixture APKs or controlled package lookup in instrumentation; test real PackageManager separately. Never hook real third-party apps in CI. Fixture hooks do not alter the release catalog. Debug-only injection has no exported entry points; source/manifest/APK negative tests prove production release excludes it. A clean emulator runs actual production debug APK without injection, internet/accounts or LSPosed. No fake scope approval, root, host execution or Termux acceptance. Explicit fixture IDs distinguish screenshots from real runtime observations. Use test state reset per journey, deterministic clock, idle synchronization, no unbounded sleeps. Fake time only for fixtures; check actual elapsed-time cap via independent bounded test.

### Test matrix and CI scheduling

| Profile | Width/scale | Required coverage |
|---|---|---|
| API36 primary | 390 × 844dp / 100%, fixed dark | All 24 case screenshots + critical journeys at R05; incremental assigned cases earlier |
| API36 compact | 360dp / 100% | Home/Hooks/Diagnostics, details, critical actions |
| API36 typical | 393dp / 100% | Additional real-native chrome regression |
| API36 small | 320dp / 160% | Navigation, capture, scope, MCC, warnings |
| API36 large text | 360 and 393dp / 200% | Full critical routes, IME, accessible controls |
| API36 tablet | 600 and 840dp / 100% | Adaptive columns and rail, landscape |
| API36 themes | 390dp / 100% | Light/AMOLED/system/dynamic with controlled inputs |
| API29 minimum | 360dp / 100% | Core launch/navigation/config and API fallbacks |
| Android 16 physical | Actual device metrics/font settings | Final visual, real installed icons, TalkBack, LSPosed and evidence QA |

PR: primary 390dp all admitted cases, compact smoke, functional negative paths, selected large-text cases; build once per exact head, share verified app/test APK artifacts across shards. Extended scheduled/manual: full adaptive/accessibility/API29/theme/large-data matrix. R05 qualification requires entire declared matrix on exact source (not necessarily all screens across every combination; manifest must enumerate every required pair). No skip/continue-on-error for required gates. Separate infrastructure outage from test failures; bounded retries only for verified transient setup failures, never retry a failed visual assertion into green. Record failed attempt. No local Gradle, including Devtool manifest tasks. No assumption CI24 is relevant: inspect actual Devtool/repo policy, dispatch independently once only if applicable; extended UI matrix is not CI24 by another name.

## 7. Cinderbox-derived CI implementation plan

At [Cinderbox exact source](https://github.com/Mikeyphw/Cinderbox-Companion/tree/2806231c51ad4060b5429abcc15dadaefe543a70), examined workflows: [approved reference](https://github.com/Mikeyphw/Cinderbox-Companion/blob/2806231c51ad4060b5429abcc15dadaefe543a70/.github/workflows/cinderbox-ux-approved-reference.yml), [native fidelity](https://github.com/Mikeyphw/Cinderbox-Companion/blob/2806231c51ad4060b5429abcc15dadaefe543a70/.github/workflows/cinderbox-ux09b-native-fidelity.yml), [normal visual](https://github.com/Mikeyphw/Cinderbox-Companion/blob/2806231c51ad4060b5429abcc15dadaefe543a70/.github/workflows/cinderbox-ux09d01-normal-visual.yml), [accessibility](https://github.com/Mikeyphw/Cinderbox-Companion/blob/2806231c51ad4060b5429abcc15dadaefe543a70/.github/workflows/cinderbox-ux09c01-accessibility.yml), [R48 real Activity](https://github.com/Mikeyphw/Cinderbox-Companion/blob/2806231c51ad4060b5429abcc15dadaefe543a70/.github/workflows/r48-real-activity-acceptance.yml), [`ux09b_emulator_ci.sh`](https://github.com/Mikeyphw/Cinderbox-Companion/blob/2806231c51ad4060b5429abcc15dadaefe543a70/scripts/devtool/ux09b_emulator_ci.sh) and [`r48_verify.py`](https://github.com/Mikeyphw/Cinderbox-Companion/blob/2806231c51ad4060b5429abcc15dadaefe543a70/scripts/devtool/r48_verify.py). Reuse principles, not Compose test code or Cinderbox package names. Cinderbox uses Ubuntu 24.04, API36 Google APIs x86_64, Pixel 6, KVM, 2 cores/2048M RAM/3072M disk, disabled animations and `ReactiveCircus/android-emulator-runner` pinned commit `a421e43855164a8197daf9d8d40fe71c6996bb0d`. Confirm pin/tool versions and image revision at implementation; HookLab retains Java 21 hosted runner and its actual Gradle/SDK settings.

| Workflow (proposed) | Trigger | Required jobs |
|---|---|---|
| Existing `.github/workflows/android.yml` | PR/main | Preserve source contract, unit, lint, debug APK and provenance; build test APK on hosted job or integrated native build; do not expose signing credentials |
| `hooklab-design-contract.yml` | PR/main | Reference hashes, browser geometry/interaction replay, roster/tokens, comparator negative tests |
| `hooklab-native-ui.yml` | PR/main | Exact checkout, build or retrieve source-bound APK pair, emulator shards, production Activity, design fidelity, native regression and independent aggregate verifier |
| `hooklab-ui-extended.yml` | Schedule/manual; exact candidate at R05 | API29/36, tablet, landscape, large fonts, themes, lifecycle, performance and evidence verifier |
| `hooklab-ui-reference-review.yml` | Explicit manual review | Produce candidate references/goldens only, no automatic commits or approval; separate review PR |

Hosted sequence: checkout exact PR head (record actual SHA; separately test merge queue SHA if configured), validate source reference, build/test APK on GitHub with `assembleDebug` + `assembleDebugAndroidTest`, signer verification, check KVM/disk/system image, boot bounded emulator, require emulator serial and API, disable animations, verify no stale install and offline/isolated network, install exact APK pair, verify installed base hash, run named instrumentation suite, capture expected screenshots and safe hierarchy, compare, independently validate complete receipt, publish allowlisted artifacts always. Source job may fetch dependencies; app execution offline. Use `contents: read`, persist-credentials false, pin third-party actions, no `pull_request_target` execution of untrusted source, no privileged release secrets. Prefer artifact IDs/digest verified against same run/attempt; do not trust filenames alone. Android API29 shard requires appropriate compatible runner/system image. Capture actual APK from app not fabricated UI; verify instrumented package and real component before screenshot. Use fail-closed aggregate job `if: always()` inspecting all required needs and manifest; missing/cancelled/skipped checks fail. During staged rollout only admitted cases are required, but missing future cases visible, no full acceptance until R05. Ruleset authorization/readback independent; cannot claim configured until verified.

### Required evidence bundle and gate logic

`receipt.json`, `apk-identity.json`, `environment.json`, `test-roster.json`, `reference-manifest.json`, `case-results.json`, `geometry.json`, `actual/*.png`, `diff/*.png`, `triptychs/*.png`, sanitized hierarchy and summary. Bind all files via SHA-256; reject unexpected paths, symlinks, extra/missing PNGs, wrong dimensions or duplicates unless explicitly expected distinct state with independent evidence. Receipt records repo/source SHA/tree, workflow path/ref/run ID/attempt/job, reference SHA and approval revision, golden SHA, app and instrumentation APK SHA, installed base hash/debug cert, emulator image/API/fingerprint/density/locale/font/theme, fixture ID, expected/started/passed/failed/skipped tests and per-case comparison results. Start receipt NOT_RUN; write PASS only after independent validation; failures still publish bounded evidence. Separate actual test counts from planned cases. No raw private logcat, clipboard, secrets, account data or unredacted imports. CI output is not a stable release or device receipt. Verifier tests deliberate missing evidence, stale SHA/attempt, corrupt PNG, duplicate case, skipped test, hidden action, altered thresholds, forged claim and failed dependency.

## 8. Functional journey inventory and non-regression

Navigation: NAV-01 group header, 02 independent chevron, 03 single-hook shortcut, 04 multi-hook app selection, 05 stable hook ID, 06 search/view preservation, 07 Android Back, 08 rotation/process state, 09 no mutation; add invalid ID, rapid duplicate tap, absent app and deep-link tests. Diagnostics: DIA-01 tab isolation, 02 source import/cancel, 03 filter apply/clear/cancel, 04 event identity, 05 health report, 06 capture start/stop, 07 marker provenance, 08 time cap/interrupt, 09 export/hash, 10 explicit clipboard, 11 denied access, 12 malformed/oversize/clock skew/duplicate text. Safety: SAFE-01 no card side effects, 02 no implicit scope grant, 03 no optimistic pref write, 04 failed/stale callback, 05 lock confirmation/cancel, 06 MCC preflight + root confirmation/cancel, 07 no unapproved live collectors, 08 no raw code or unrelated clipboard reads, 09 no Termux launch from diagnostic view, 10 release fixture exclusion. ACCESS tests for 48dp, contrast, headings/roles/traversal, large text, IME, insets, tablet, TalkBack separately. Link these to every promise and actual test method IDs in acceptance matrix; no screenshots alone accepted.

## 9. Execution sequence, rollback and governance

1. Reconcile authoritative roadmap, current HookLab SHA/tree and PR/session state before every overlay. Read current Devtool source/launcher/schema and authoritative operating/operator/delivery contracts. Inspect manifests for local Gradle side effects. Verify dependencies against source-complete/evidence status; do not replay existing commits. Adopt new roadmap and reference via R00 native delivery.
2. Audit every promise against actual production call chains; implement cohesive overlay, migrate callers/tests/docs and remove obsolete wrappers. Run feasible host-side structural/pure tests only; hosted Gradle. Prepare the single cumulative `devop-hooklab_cg.pyz` upgrade, preserving previous registry/session history and exact artifact/manifest hashes. Verify contract and native structural validator (when available). Directly executable Termux launcher, exact/numbered/hash fallback in `/sdcard/Download`, private `$TMPDIR`, atomic upgrade/rollback, no hidden tmux, native TTY/foreground and safe clipboard collect with original exit code.
3. One native `overlay <ID> run` lifecycle after authorized install: preflight → native check/apply/validate/commit/session → push/update matching PR → exact-head required CI → independent CI24 when applicable → watch/evidence/resume. Inspect actual CLI before offering command; no speculative executable. Preserve source binding, no duplicate PR, no force-push/reset or uncertain replay. Use read-only checks when recovering session; source movement invalidates stale evidence and requires reconciliation. Merge only after actual authorization/protection qualification. After merge verify resulting main and post-merge relevant checks.
4. Baseline review: source completeness ≠ merged ≠ accepted. Maintain one row per promise and evidence tier; gate PASS/FAIL/SKIP/N/A/INCONCLUSIVE with reason. For R00 smoke and partial coverage don't claim design parity. For R01–R04 require implemented case fidelity and preserve admitted checks; R05 requires all native goldens + functional/extended matrix + manual device evidence for claimed physical scope. Retain old DX source gaps as unresolved, separately qualify real framework/release. No implicit permission or root escalation.
5. Roll back via a reviewed reverse patch/revert through native lifecycle, not destructive resets. Preserve original approved golden revisions, app state keys and native journals; verify prior APK/version signing constraints before any authorized device rollback. Failure classification: infrastructure, source contract, compile/lint/unit, instrumentation, visual, semantic/interaction, evidence/provenance, runtime/device, signing. Fix root cause with targeted regression; no threshold relaxation or test deletion to pass. Resume from last independently observed confirmed stage only.

| Governance gate | Boundary | Status and next action |
|---|---|---|
| G01 required checks/ruleset | REQUIRED_AT_MERGE | Not qualified; inspect existing rules, owner-authorized configure/readback after checks exist |
| G02 reference approval | REQUIRED_AT_GOLDEN_ADMISSION | User review reference revision and candidate goldens; no self-blessing |
| G03 native Devtool source/session | OVERLAY_BLOCKING for actual delivery | Recheck live CLI/schema, exact base/worktree/session/PR before each overlay |
| G04 physical LSPosed and TalkBack | REQUIRED_FOR_RUNTIME/DEVICE_ACCEPTANCE | Actual device and owner authorization; fixture cannot substitute |
| G05 signing/environment | REQUIRED_AT_SIGNING/RELEASE | Existing `hooklab-signing` protected environment; separate authorization and cert proof; no PR secrets |
| G06 independent integration source gaps | SHARED/SEPARATE PROGRAM | Track DX-01/02/03 and other hook roadmap separately; no false completion |

## 10. Adoption and completion receipt

This is a planning artifact, not an operational overlay or installable operator. No production source modified, no native transactions/PRs created, no hosted Gradle/emulator/device/release action executed. Reference bytes and SVG roster verified locally. HookLab and Cinderbox source identities and inspected workflows verified through GitHub; original nine PRs confirmed merged. No independent complete source audit of all legacy promises or live hosted attempts. Scope is six new overlays, with every promise explicitly pending. First action is implement/adopt UX-R00 using the verified current repository and cumulative operator. All later stages depend on actual evidence. Wider project denominator and historical five-delivery handoff count inconclusive; do not reset ledger. No new Devtool defect reproduced by this planning task.


## 2026-11-24 — Reconciliation and replacement operator checkpoint

Current pinned main `3e87750247657d0c3c30645d07f6a5ff4f525aea`, tree `b1418074f296777057ca444f1bc9c00efe5a20e0`; original flat roadmap blob `08e77e41902e0452248b34ba9457f267027a1391`. Historical baseline in section 1 is preserved for provenance. MCC-R01 PR24 and CGT01-P05 PR25 merged; TB01 PR13 remains open and must not be replayed. Existing original UX/DX tranche 9/9 merged, 0 independently accepted on available evidence. Revamp 0/6 source-complete and 0/6 accepted; all six remain. UX-R00 is explicitly partial, not an accepted overlay.

Replacement cumulative operator uses schema 3 and exact pinned Devtool 05e120d185ce8e81287b591bac6a221054abe94e. It preserves native history and refuses unknown replay. Old deleted payloads were not recovered. No native session, hosted run, emulator or physical-device proof has been produced.

| Promise | Candidate evidence / gap |
|---|---|
| UXR00-P01 | Exact original HTML/SVG and digest/roster tamper checks; adoption pending. |
| UXR00-P02 | 24 cases captured twice identically on local Chromium 144; hosted engine unrun. |
| UXR00-P03 | Browser geometry contract only; native semantic mapping not complete. |
| UXR00-P04 | Real MainActivity instrumentation source added; hosted compile/test pending. |
| UXR00-P05 | API36 hosted workflow and fail-closed offline checks added; not run. |
| UXR00-P06 | Production fixture seam not implemented. |
| UXR00-P07 | Three real navigation screen capture cases in source; no native evidence yet. |
| UXR00-P08 | Cross-platform comparison and calibrated visual metrics not implemented. |
| UXR00-P09 | Three workflows added; required workflow registration and extended/review pending. |
| UXR00-P10 | Independent CI24 evidence verifier tested on synthetic receipts; native hosted evidence pending. |
| UXR00-P11 | Replacement cumulative operator implemented; actual native lifecycle not exercised. |

CI24: 24 JVM shards, sorted class round-robin, exact method roster, strict JUnit receipt and independent summary tied to source and run/attempt. Separate from emulator and release; initial PR-triggered adoption plus manual dispatch after registration. Never run Gradle locally. No automatic release or merge.


## 2026-11-24 — UX-R00 reference and native evidence continuation (operator 1.1.0)

Exact main and Devtool pins unchanged; original authoritative flat-roadmap blob remains `08e77e41902e0452248b34ba9457f267027a1391`. New revamp: 0/6 overlays source-complete and 0/6 accepted; UX-R00 promise source completeness 2/11. No native transaction or new PR was observed or executed by this session.

| Promise | Actual source candidate / remaining gate |
|---|---|
| UXR00-P01 | SOURCE_COMPLETE: original HTML/SVG plus immutable reference-manifest hash and 24 SVG labels; exact bytes and negative tamper checks pass. Adoption/hosted proof pending. |
| UXR00-P02 | SOURCE_COMPLETE: pinned hosted Playwright renderer and deterministic locale/clock/theme; two fresh local Chromium 144 runs matched all 24 PNG/geometry records. Pinned hosted engine pending. |
| UXR00-P03 | PARTIAL: frozen 199 semantic control records across 24 browser states, tokens, geometry, admission and no-mask rules; native title/navigation mapping for three candidates. Full native semantic mappings and explicit fixture equivalence pending. |
| UXR00-P04 | PARTIAL: Java AndroidJUnitRunner, Espresso/UI Automator and actual MainActivity tests for offline navigation and synthetic imported diagnostics; hosted compile/execution not run. |
| UXR00-P05 | PARTIAL: API36/KVM/disposable/offline guard and installed debug/test APK hashes; emulator setup and exact SDK/runtime verification not exercised. |
| UXR00-P06 | PARTIAL: instrumentation-only synthetic input through actual HookLabApp.diagnostics() bounded store, actual timeline/search/redaction assertions; no production fixture provider or fake service. Connected/denied/stale command adapter, deterministic capture clock/catalog and release absence proof remain missing. |
| UXR00-P07 | PARTIAL: actual Activity screenshot code and hierarchy with system insets, stable menu selection, semantic bounds/typography/colors, native PNG validation; no emulator captures yet. |
| UXR00-P08 | PARTIAL: three cross-platform candidate comparisons, per-control geometry/font/color/label deltas, 8x8-window SSIM, changed pixels, triptychs, independent recomputation and strict image regression unit tests. No approved native goldens or full-screen fixture-equivalent comparisons; calibration pending. |
| UXR00-P09 | PARTIAL: design/native/CI24 workflow wiring and separate exact-source native UI observer; extended/review workflows and required registration/branch protection pending. |
| UXR00-P10 | PARTIAL: independent host stdlib rehash of frozen references, repeat manifests, exact source/tree/run/attempt, APK identity receipts, fixture proof and PNG CRC/geometry/triptych hashes; synthetic tamper tests pass; real hosted evidence absent. |
| UXR00-P11 | PARTIAL: cumulative schema-3 operator updated to observe required CI, design/native candidate evidence, then separate CI24. Native lifecycle and actual foreground remote operations not run; previous receipt drift refuses replay. |

Local verification: 20 stdlib static tests, six Pillow image regression tests and one full report/host-verifier synthetic fixture test passed; two fresh 24-state browser captures using the local Chromium override matched, with 199 frozen semantic records. Synthetic screenshots and receipts were isolated test fixtures and are NOT native evidence. No Gradle/ADB/Android test was run locally. The report is CANDIDATE_COMPARISON_ONLY even when the job passes; candidate mismatches are recorded, not hidden or promoted. User-installed operator can opt into a draft checkpoint only via `--accept-checkpoint`; the connected-service fixture adapter and hosted evidence are still required before final UX-R00 acceptance and UX-R01. Previous artifact/session must be reconciled if already applied, never replayed.


## 2026-11-24 — UX-R00 completed foundation source handoff (operator 1.2.0)

Source implementation candidate: 1/6 overlays, UX-R00 11/11 promises authored; independently accepted 0/6. Earlier checkpoint counts above are historical, not the current status. Authoritative prior roadmap and main/runtime pins unchanged. Actual native transaction, hosted CI, emulator and CI24 are NOT RUN. The exact immutable preimages for six existing source paths are applied to a private archive in the operator before native check; no direct worktree edits or local Gradle.

| Promise | Source implementation and pending evidence |
|---|---|
| UXR00-P01 | Exact frozen HTML/SVG, manifest SHA-256, 24 SVG labels and negative tamper checks; reference version 1.0 unchanged. |
| UXR00-P02 | Pinned hosted renderer, deterministic locale/clock/theme and two fresh Chromium 144 local captures matching all 24 PNG/geometry records; hosted pinned renderer pending. |
| UXR00-P03 | All 24 browser semantic states, 199 controls, tokens, verified geometry, 15 fixture IDs and case ownership, explicit primary/extended profile admission, no blanket masks or native equivalence claims; native mappings for three foundation routes. |
| UXR00-P04 | AndroidJUnitRunner, Espresso, UI Automator and accessibility dependencies; actual MainActivity tests, exact instrumentation start/pass roster verifier and negative tests; hosted compile/execution pending. |
| UXR00-P05 | API36/KVM/disposable offline workflow; rejects unexpected serial/ABI/API, preexisting app, insufficient disk, non-offline network; records source, SDK/emulator revisions, app/test APK and installed hashes; hosted execution pending. |
| UXR00-P06 | Actual MainActivity and FrameworkStateStore now share UiEnvironment real binder transport and clock/catalog boundary; debug-only process lease allows isolated fixtures; tests assert confirmed writes, denial, stale callback, independent hooks, capture markers, real diagnostic import/redaction and no real service; release DEX exclusion gate. Host/Android execution pending. |
| UXR00-P07 | Real Activity captures title/navigation, hierarchy without raw text, insets and PNGs; duplicate/blank/wrong-route evidence refused, exact APK pair required. Hosted screenshots pending. |
| UXR00-P08 | Browser/native candidate comparison for three assigned baseline routes, region metrics, semantic geometry/font/color, triptychs and independent recomputation; repeated native same-environment regression with only measured system-bar exclusion. Local synthetic positives and negatives passed; actual native calibration pending; goldens deferred to R05. |
| UXR00-P09 | PR design/native and separate CI24 plus executable manual extended foundation and read-only candidate review workflows; explicit staged case/profile ownership. Full adaptive matrix and required-check registration belong to R04/R05; hosted registration/run pending. |
| UXR00-P10 | Independent allowlisted rehash of reference repeats, PNG CRC/dimensions, source/tree/run/attempt, exact test roster, APKs/cert, environment, fixture/release isolation and repeat calibration; synthetic stale/tamper/missing evidence tests pass; hosted evidence pending. |
| UXR00-P11 | Cumulative schema-3 operator normal run path; six source-bound recipes produce exact full postimages from immutable pinned blobs at preflight, then canonical native static check/apply/commit/session/PR/CI/CI24. Package and recipe tests passed; native lifecycle awaits exact repository/runtime checkout. |

Primary lifecycle: `devop-hooklab overlay UX-R00 run --yes --deliver --ci24`. No checkpoint override. Resume the recorded session, never reapply. Future screen parity and full adaptive matrix belong to their owning overlays; branch protection and release signing remain separate governance gates.

## 2026-11-24 — UX-R00 forward-port to post-R05 main

Current base `af75dfeecc51e92a0b128990e206517f88fd66e0`, tree `7015dc79d91b6ea0c8e1a44b1690b635877868e5`. Devtool `7543dc19caa9a151bbc38ffeaa36d0a01b8090de`. The Library roadmap revision 1.0 is retained. Earlier R00 candidate has been reported locally committed; native session/worktree state is not re-observed here. Use only the new branch `feature/hooklab-ux-r00-rebase-20261124`. No replay or deletion of old worktrees. R05/PR29 is merged; TB01/PR13 remains open. No R05 feature is removed. Libsu dependencies and Termux permission/settings bridge remain intact. The R00 baseline exercises the settings-only bridge without requesting permission or executing a command. The original 24 references remain unchanged; the newer settings screen has no separately approved visual reference. Add that reference only through reviewed design governance.

**Revamp progress:** 0/6 source-complete, 0/6 accepted, 0/6 merged. R00 partial; R01–R05 planned. Previous nine-overlay tranche remains merged; original acceptance debt below is not superseded. Historical browser and fixture results are distinct from current hosted verification. Current Android CI, native smoke, CI24, device and release are NOT RUN.

| Historical obligations | R00 relationship / unresolved gate |
|---|---|
| DS01-P01, DS01-P02, DS01-P03, DS01-P04 | Frozen references, native smoke geometry; theme/adaptive/device visual parity remains open |
| UI04-P01, UI04-P02, UI04-P03, UI04-P04 | Catalog fixture and native baseline; actual navigation and restore acceptance remains open |
| UI05-P01, UI05-P02, UI05-P03, UI05-P04 | Real state-store fixture and negative tests; real LSPosed/device acceptance open |
| UI06-P01, UI06-P02, UI06-P03 | Real Home smoke; final home fidelity/readiness open |
| DX01-P01, DX01-P02, DX01-P03, DX01-P04, DX01-P05, DX01-P06 | Import/privacy fixture; live collection, persistence, real device acceptance remain open |
| DX02-P01, DX02-P02, DX02-P03, DX02-P04 | Capture fixture; real reproduction, interruption and persistence remain open |
| DX03-P01, DX03-P02, DX03-P03, DX03-P04 | Diagnostic fixture; real evidence/collector/device gaps remain |
| UI07-P01, UI07-P02, UI07-P03, UI07-P04 | Settings-only bridge smoke; real scope/root denial, signing and accessibility remain open |
| UI08-P01, UI08-P02, UI08-P03, UI08-P04, UI08-P05 | Structural checks, independent evidence verifier; final native/device/performance/permission/signer acceptance open |

All 38 historic IDs preserved. No visual golden approved by this candidate. Keep existing required Android CI; new UI workflows and CI24 qualify separately at the exact PR head and attempt. No local Gradle. Candidate is not an acceptance receipt.

## UX-R00-F02 — Hosted Android lint and installed APK identity repair

PR #30 head `a60b2758c6cd9695dc8fab2bc1c955d35f9eb41e` tree `f86305f1272d9a78aa948996ec93ccd390a34336`. Design run 45470890438 and JVM CI24 run 45470889969 succeeded at that head. Android run 45470890057 failed `UseSdkSuppress` on NativeBaselineTest; native run 45470889972 failed `installed bytes mismatch` before instrumentation. The logs do not establish whether the raw read was denied, returned diagnostic bytes, or was a true mismatch. Repair uses exact API36 test filtering and retains both exact installed APK digests, with exit-aware on-device SHA-256, constrained package path, owner-only fallback on explicit access denial, and fail-closed drift/mismatch. No retries on genuine mismatch, no root, no waived checks. Fresh exact-head Android, native, Design, and CI24 results required; no native golden or device acceptance inferred.

F02 follow-up validates the normal ADB streamed/push install acknowledgement, refuses incremental installation, nonzero status, unexpected output, path splits, traversal and foreign-package base APK paths. The complete uploaded Git archive independently supplied the exact PR base object and tree. The repair retains R00 verification and the six-overlay roster; no emulator qualification is asserted.

## UX-R00-F03 — SDK package revision evidence repair

Remote PR #30 head `26985971d28c26847e498cf12dc1a08db04592ec`, tree `6b16e6e56be745a8897d2ccd09114776b09ce43a`, main `af75dfeecc51e92a0b128990e206517f88fd66e0`. At this head Android CI 45484518909, Design Contract 45484518905 and CI24 workflow 45484518958 reported success; CI24 independent artifact qualification is not inferred. Native UI 45484518896 job 138766211892 booted API36 and verified both installed APK SHA-256 values, then refused when invoking `emulator -version` returned 127. Instrumentation and subsequent visual gates were not established.

F03 removes the additional emulator executable invocation from the actual hosted metadata path. It reads the three installed SDK packages' `source.properties` files, validates revisions and any declared package identity, rejects duplicates/missing/malformed metadata, and explicitly labels the emulator package revision as `sdk-package:<revision>` (not an executed binary fingerprint). The independent verifier requires this evidence format. API36/qemu/ABI/offline guard, exact APK identity, instrumentation roster, visual comparison and artifact gates remain unchanged. Unit tests include the actual environment caller with a forbidden subprocess invocation and malformed/duplicate/missing metadata. Existing synthetic visual report fixtures now use the same revision format. No local Gradle, no approval of goldens, no physical-device or release acceptance.

F03 must be applied to the existing PR branch as a new native transaction, not by replaying F01/F02. All 11 promises and six overlay obligations remain open until independently qualified.

## UX-R00-F04 — Settings lifecycle identity and bounded failure evidence

PR #30 exact F03 `9a8bf6b737a5b190d338378c437b93873ec25bc0`, tree `0ff6e65f5f2a01115aabc98a7966770e83363673`. Android run 45509379985, design 45509380018 and CI24 45509379984 concluded success. Native run 45509379997 verified both installed APKs and SDK package revisions, then `NativeBaselineTest#realActivityOfflineNavigation` returned terminal -2. Its retained receipt did not identify the assertion. Source audit identifies a deterministic ActivityScenario mismatch: TermuxBridgeActivity replaces the settings intent with an empty intent before lifecycle matching; AndroidX matches action/component. F04 preserves only the trusted explicit settings action/component in a new intent, retaining zero caller extras, data, ClipData or flags; preview still clears the intent and stale recreation remains refused. Existing real navigation/capture, settings-only and no-execution checks remain, with direct payload-scrub assertions. Host records allowlisted checkpoint, exception class and test source frames with exact source/tree/run/attempt, ordinal, completed roster and terminal status, not raw message/log/UI contents. Nonzero, timeout, spawn and test failures remain fatal. No visual tolerance or approval changed. Fresh hosted qualification required; exact failing prior assertion was not retained. UX-R00 remains partial (0/6 source-complete, 0/6 accepted, 0/6 merged); R01–R05 remain dependent.

## UX-R00-F05 + HL-DIAG-R06 — repeat capture stabilization and terminal UX

Reconciled base `1a7b10c44a605c05e4776347ddef761c958854d3`, tree `fbd99cc6d8124d15c3308a2fdc5a85167a362fdc`. F04 Android CI 45517030013, Design 45517029971, CI24 45517030049 success; native 45517030188 job 138878997519 failed only after both real navigation instrumentation runs passed. Artifact 14977962049 SHA-256 `472ae6eef3ede95a586f966f7cfe3d0fa5e950a8aea0045256600d479ec37e63`: Home changed pixel ratio 0.04263772 with scrollbar and low-amplitude raster differences; Hooks and Diagnostics identical in measured app area. F05 waits for the real ScrollView fade interval, requires three consecutive identical viewport samples independently in each capture (bounded; never selects a match against the other run), asserts unchanged geometry and avoids redundant same-position scroll. Full screenshot and measured insets remain intact; thresholds SSIM >= .99 and changed pixels <= .01 unchanged. Repeat failure stores exact source/run/attempt, failed route, metrics and screenshot/hierarchy hashes. Fresh hosted native acceptance required, no golden approval.

Consolidated user-requested terminal implementation is separately tracked by `docs/roadmaps/hooklab-diag-r06.md`. This does not retire the remaining revamp or historic diagnostics promises. Existing UX-R00–R05 acceptance stays pending.

## UX-R00-F06 — Hosted startup fixture and Maven rate-limit recovery

Exact published F05/R06 PR #30 head `6437e20b9d8766ca08e7ebb2219ed16ae0ce5377`, tree `bb8c25e4779714a93458af95d4ec7018912d08e1`. Native transaction `20261125T005949Z-eabc575c6d39`, history `e05a8c28bc755b313c8868d7ef22930c`, session `8c2ac8e5ffb8452eacfe5e8e9093dc13`: committed, validation ok, remote verified, source matching. Android 45522569471 and CI24 45522569581 report success. Design 45522569588 job 138897821162 passed bootstrap and local source checks, then the actual PTY waited on a hosted `compinit` insecure-directory prompt instead of the fixture prompt. Native 45522569466 job 138897821166 exhausted three Maven Central HTTP 429 attempts resolving instrumentation dependencies before emulator startup. No fresh F05 screenshot acceptance. Both absent-artifact upload errors were downstream of those primary failures.

F06 isolates system interactive rc in the *synthetic fixture* `.zshenv` (not production Termux startup, no compinit bypass or permission changes); all real Zsh PTY assertions remain mandatory. Native workflow alone opts into five attempts with 20/60/120/240s backoff on exact Maven Central dependency HTTP 429, retaining default three attempts for normal Android CI. Non-429 and mixed compilation/test/lint/authentication failures are not retried. Failure artifact upload remains mandatory whenever its capture step ran; a skipped capture does not create a misleading secondary missing-artifact failure. Stub command tests exercise success, exhaustion, original return code, non-429 and mixed failure classification; no local Gradle. No dependency mirror, version, device permission, native threshold, or command execution semantics changed. Fresh source-bound Design and native CI plus Android/CI24 and device acceptance remain required. Preserve F05/R06 transaction and use a distinct forward native repair, never replay.

## UX-R00-F07 — Capture same-route state transition

PR #30 F06 head `4d6120fed056db373a553c4eb622ab9fe29a6f34`, tree `4a22b57bd606533d255dbf72b78bca26666a499c`. Android 45524845811, Design (including actual Zsh PTY) 45524845727, and CI24 45524846178 succeeded. Native 45524846002 passed both real navigation runs, repeat calibration, and diagnostic fixture before FrameworkFixtureTest line 165 raised NoMatchingViewException. Artifact 14982112689 SHA-256 `91b6b31572bb83372371eb223f5ba50b8d75b3c7d5dcb93a2b41d6a7b77c6203` binds the failure to that exact head. The production capture source selection creates the session, then calls goTo(CAPTURE) on the already selected CAPTURE page; the same-route guard refuses rendering. F07 explicitly refreshes only after successful capture creation, preserving ordinary same-page navigation no-op. Real instrumentation now checks active controls, marker/stop, restart on the same route, fresh marker clock and previous capture isolation, and reports only allowlisted stage/type/frames. No test retry, visual threshold change, production Termux change, or local Gradle. Fresh hosted checks and device acceptance remain required.

## UX-R00-F08 — native raster consistency and independent fixture evidence

PR #30 F07 head `cf24ef942ada487251580dbac9e57d10fc7d13a6`, tree `1e2154f04a05488e1a18d0ffe690cd32b3c52618`. Android CI 45527791794, Design 45527791792 and CI24 45527791913 succeeded. Native 45527791930/job 138915332996 passed both navigation tests, then failed repeat comparison for Hooks. Downloaded artifact 14982538633 SHA-256 `4fb371e1d137e9fcd8fc99282f12d1d6c637fef68443a34e9a4fc1e0e44737c0` has identical geometry; Hooks changed ratio 0.02640199, SSIM 0.99998757, max RGB delta 5, concentrated at antialiased glyph/icon/control edges. Home and Diagnostics identical. This suggests raster/compositor precision variation, not a proven GPU root cause. F08 uses the currently supported SwiftShader software GPU backend, disables guest Vulkan and snapshots for an isolated cold boot, verifies the booted guest Skia GL setting and SwiftShader GLES evidence, and requires a newly committed hardware-rendered frame for each of three independently stable screenshot samples. Preserve full actual screenshots, raw pixel equality while settling, measured viewport and unchanged SSIM >= .99 / changed pixels <= .01; no normalization, masking, or selecting a matching pair. Both captures run exactly once. Run all functional fixtures after the two captures and before the mandatory repeat comparison, retaining fixture hashes in repeat-failure evidence, so a visual failure cannot prevent already independent functional tests from reporting. Actual hosted GPU behavior and fresh complete native baseline remain pending. R06 production and F07 capture fixes unchanged; no local Gradle.


## 2026-11-25 — UX-R01 diagnostics consolidation (operator 2.3.0)

**Exact source anchor:** merged PR #30 `b82ba80020920cdc840968a726cf81b707dd09b3`, tree `e1e95913842dac6347a930b5a74baba87d6ee066`, identical to qualified F08 head `b805bcf96db6b6fcc7bc795e4641fe0b95b98bc6`. Android 45530308382, Design 45530308325, Native 45530308308, CI24 45530308323 all succeeded. Native baseline archive hash and receipt contents independently verified: Home, Hooks and Diagnostics repeat changed pixels 0%, SSIM 1.0; five successful instrumentation invocations. Status `BASELINE_PASS_NOT_VISUAL_APPROVAL`. CI24 summary 208 tests/24 shards and user receipt PASS; no fresh R01 hosted results. All R00 11 promises source implemented, foundation hosted gates verified, 0/24 whole-app visual approvals and real device/release pending. No PR #30 replay.

**Governance:** authoritative operating contract v1.25 SHA-256 `850a858e863bb2715da7f9e09bd26ede3d7f81231f7da05db3b381b6cfb7df76`; operator schema v1.8, delivery schema v1.15 and roadmap schema v1.1; previous qualified Devtool runtime `7543dc19caa9a151bbc38ffeaa36d0a01b8090de`. New branch `feature/hooklab-ux-r01-20261125` only from exact merged main. Native transaction and actual PR identity must be determined at user-host delivery, no invented PR.

| Promise | Actual production implementation | Local and pending acceptance evidence |
|---|---|---|
| UXR01-P01 | MainActivity routes DIAGNOSTICS/HEALTH/CAPTURE attach only chosen workspace; retained diagnosticTab | Structural route checks; hosted DiagnosticFixtureTest isolated workspaces and recreate, pending |
| UXR01-P02 | Single native import sheet with three supported explicit SAF sources; request is typed and bound before picker | Bounded strict UTF-8 and URI guard; model oversize/malformed/refusal tests; hosted cancel and three choices pending |
| UXR01-P03 | Draft filter sheet with source/severity/group/exact package/hook/time/query, apply/cancel/clear | Model grouped/time intersection and unknown clock tests; real sheet fixture pending |
| UXR01-P04 | Compact chronological rows; 30 events/page and retained/drop counts from immutable projection | Exact chronology, 4000 cap, duplicate identities and eviction tests; real Android paging pending |
| UXR01-P05 | Dedicated event inspector exact stable ID; stale selection refused; hash/line, time, source and correlation preserved | Model duplicate and stale identity tests; real inspector/recreate fixture pending |
| UXR01-P06 | Shared FlatUi.actionRow and PackageManager hostIcon; each exact hook row independent | Same-package model test, explicit fixture route and no-write assertions; hosted pending |
| UXR01-P07 | Dedicated HookHealthModel report with independent axes, attribution and safe setup/import/lock actions | 11 actual model tests incl unknown, disconnected, scope, lock, version, same-package and no runtime assertion; native fixture pending |
| UXR01-P08 | Existing guided target/source flow on independent capture tab; process-owned import gate and monotonic deadline | Actual capture timeout/cancel/interruption and cap tests; native start/restart retained, pending |
| UXR01-P09 | Fixed-code markers and bound immutable capture report, exact source/window attribution; retention loss explicit | Actual two-session and marker cap tests; no silent collector; native marker clock pending |
| UXR01-P10 | Redaction preview before copy/export, exact immutable timeline/health/capture selection at SAF launch, typed ZIP writer | Exact ZIP payload/hash and denied-write tests, fixture cancellation clipboard unchanged pending |
| UXR01-P11 | Rotation-retained ViewModel, root scroll and page, stable inspector and report return routes, process-loss notice; no evidence persisted | Structural state and picker identity checks; native recreate pending; safe loss on process death |
| UXR01-P12 | Old inline multi-workspace controls replaced; framework store and explicitly confirmed safety actions preserved | Existing 38 legacy promise checks; native same-package navigation no-write, isolated workspace fixture pending |

Local verification before packaging: 48 source QA checks and 37 direct JDK diagnostic model test methods passed (private JUnit assertion shim, not hosted JUnit); changed Java source syntax parsed, no local Android compile/Gradle. Exact package validation and native transaction evidence recorded separately. No new permissions, hidden collector, root action or device execution. Import-only evidence is unauthenticated. Real SAF provider, Android lifecycle/visual and physical-device gates remain external. No approval or mutation of a future overlay or release.

## UX-R01-F01 + HL-DIAG-R06-F01 — hosted Java API and persistent Termux login repair

Parent PR #31 head `c78b187c7e6bb715bfcf963858265d6c8c7c3f0e`, tree `202991cd8db399bdc800e22a432181cd0a891cc6`; main remains `b82ba80020920cdc840968a726cf81b707dd09b3`. User transaction `20261125T024154Z-9787df13f3e9`, session `9ff0c7aedce04cf4aa0fe525508998a6` remains historical; never reuse it for a changed head. Design run 45541608275 passed; Android run 45541608367 and Native UI run 45541608500 failed at Java compilation (View/Spinner setMinHeight). No CI24 qualification for this head.

| Promise | Implemented source | Required independent proof |
|---|---|---|
| UXR01-F01-P01 | FlatUi and filter Spinner use Android View.setMinimumHeight; preserve 48dp touch geometry | Hosted assemble, lint and real diagnostic fixture |
| R06-F01-P01 | Fixed Termux login route initializes termux-exec via installed login before persistent interactive login Zsh; no system library path assumptions | Login-aware shell fixture, real external Android Termux command and subsequent command |
| R06-F01-P02 | Four fixed startup shims, no payload in files, noninteractive probe guard, real zshenv/zprofile/zshrc/zlogin, restored ZDOTDIR, explicit NOART and original exit | Real persistent PTY, startup exactly once, failure and cleanup, custom ZDOTDIR, physical Termux |
| R06-F01-P03 | Explicit packed white sRGB host Compose terminal tint, modifier-only default mask, unchanged Copy action | Exact pinned DEX signature/mask verification and real host rendering |

Source repair does not certify device runtime or native visual approval. No new permission, implicit command, repository reset, or release.

### UX-R02 candidate evidence (2026-11-25)

Source: exact merged PR31 tree. Native catalog routing, scoped icon provider, stable route frames, visual candidate capture for VIS-002–005 and CI24 bounded Maven-429 retry/failure summaries are authored. See `docs/qa/hooklab-uxr02-acceptance.md` for evidence boundaries; no device, release, visual or hosted acceptance is asserted for R02. Historical accepted evidence remains bound to its own source.


## UX-R04-F01 — Hosted Dependency & Instrumentation Compatibility Repair

Base PR #33 `29644ecdf752f7fae405696f1477d3e8cf84b640`, tree `ecff597f8518024511e470a5694d93bd18f3d82f`. Android CI 45704443361 and CI24 45704443249 reported success; exact CI24 artifact qualification remains pending. Design 45704443306 failed before reference generation because two matrix tests imported Pillow before dependencies were installed. Native UI 45704443309 and all ten extended shards of 45704443281 failed compilation at NativeBaselineTest line 102 (Material R.attr.colorError unavailable). No native captures or matrix acceptance established.

| Promise | Production repair | Required evidence |
|---|---|---|
| UXR04-F01-P01 | Install the pinned Python QA packages before source tests in the actual Design workflow; no skipped tests | Fresh isolated hosted setup, full source suite and reference generation |
| UXR04-F01-P02 | Resolve the AppCompat colorError attribute from the real theme, assert each palette attribute resolves; retain exact palette comparison | Android instrumentation compilation, actual native palette and extended checks |
| UXR04-F01-P03 | Ordinary Android CI assembles instrumentation APK as well as debug app, JVM and lint; regression tests check actual workflow ordering and palette path | Fresh Android CI and independent native/extended/CI24 results |

No threshold, test roster, permission or runtime fixture relaxed. Forward-only native transaction on existing PR #33, retaining the committed R03 and R04 records. R00–R02 remain merged and hosted-qualified; R03 and R04 still require exact repaired-head hosted acceptance; R05 remains planned. Physical-device/visual/release gates unchanged.


## UX-R04-F02 — Adaptive UI qualification repair

Forward-only successor of F01 on existing draft PR #33. Preserve R03, R04 and F01 native transaction/session histories, the original matrix and all accessibility, artifact, source, privacy and CI24 gates.

| Promise | Production/QA path | Independent evidence required |
|---|---|---|
| UXR04-F02-P01 | Actual window frame metrics during 80 real scroll steps of the 4000-event timeline; reject missing, duplicate and malformed samples; separately measure five fresh process launches | Android runtime, source-bound performance records and independent recomputation |
| UXR04-F02-P02 | Stable native catalog input identity and cursor/focus ownership; bottom navigation yields to IME, rail stays; no stale queued focus override | Compact and all other real journeys including keyboard close, recreate and rotation |
| UXR04-F02-P03 | Use actual adaptive content width after scaled rail allocation; wait for observed real display/configuration before journey/performance; retain exact tablet journeys and accessibility assertions | All three previously failed API36 tablet cases; their original underlying exceptions were not retained, so runtime qualification remains pending |
| UXR04-F02-P04 | Fixed source/test-bound phase checkpoint and sanitized exception categories/source locations; never export raw test output, private hierarchy text or logcat | Negative checkpoint identity/phase tests and failure artifacts from any hosted refusal |
| UXR04-F02-P05 | Keep exact ten-case roster, eleven-route evidence, contrast and touch assertions, real process-death and synthetic fixture boundaries; all five hosted workflows must qualify | Independent same-head/same-attempt receipts and CI24 shard/summary hashes |

Physical-device, release and visual approval remain pending. No local Gradle. Shared Devtool gateway read retry belongs to the separately tracked `DT-20261125-CI-GATEWAY-READ`; do not change HookLab source to replay remote mutations.


## UX-R04-F03 — Adaptive Interaction and Instrumentation Boundary Repair

Forward only on PR #33, exact F02 preimage. Preserve R03, R04, F01 and F02 native transaction/session histories.

| Promise | Production / QA repair | Required evidence |
|---|---|---|
| UXR04-F03-P01 | Catalog mode change invalidates old scroll/focus restoration without replacing the EditText; fixture reveals and edits the real stable search target and verifies route/readback | Native and API36-390 fixture success; compact search and recreate/rotation unchanged |
| UXR04-F03-P02 | Home uses real tagged accessible shortcut row, scroll and visible/action assertions; no AccessibilityChecks suppression | All three tablet journeys; original exception category beyond HOME_SCROLL was lost, so root cause must be confirmed by fresh runtime evidence |
| UXR04-F03-P03 | Lock and read back WindowManager rotation before every instrumentation and subsequent performance sequence; actual Activity viewport assertions unchanged | Landscape performance and all ten cases |
| UXR04-F03-P04 | Structured source/test/phase-bound exception chain, fixed accessibility check identifiers and source frames; no raw exception or hierarchy content | Negative stale/private checkpoint tests and hosted failure diagnostics if needed |
| UXR04-F03-P05 | Keep exact native evidence, CI24, full matrix and four committed predecessor journals | Source-bound native, independent CI and artifact rehash |

Physical-device, visual and release gates remain pending. No local Gradle.


## UX-R04-F04 — Adaptive Platform, Touch Target and Shared APK Qualification Repair

Exact F03 predecessor, existing draft PR #33 only. Preserve all five committed predecessor transactions and source-bound receipts.

| Promise | Production / QA repair | Required evidence |
|---|---|---|
| UXR04-F04-P01 | API29 settings-based rotation and actual readback; API36 also locks WindowManager; each adaptive instrumentation independently sets and observes display orientation before Activity launch | All ten journeys, especially API29 and landscape performance; no weaker viewport checks |
| UXR04-F04-P02 | Real toolbar/nav destination and ordinary action row allocation at least 48dp; explicit noninteractive panel semantics; full root ATF retained; measured Home geometry on refusal | Tablet TouchTargetSizeCheck and all existing route geometry; exact original offending view not retained in F03 |
| UXR04-F04-P03 | Real resource identities for capture tab, target/input/stop/mark/interruption; scoped primary nav and deduplicated enabled clickable dialog ownership, fail on ambiguous independent owners | Actual process kill and restart, capture interruption and absent active controls |
| UXR04-F04-P04 | One hosted APK pair per exact run/attempt; build once, source/tree/attempt/digest manifest, all ten independent installation hashes, aggregate and native observer verify the same shared artifact | Twelve exact successful jobs, twelve artifacts including one APK build; fail closed on stale attempt or mixed pairs. Full attempt rerun required, not partial failed-job reuse |
| UXR04-F04-P05 | Forward-only cumulative native orchestration, five preserved predecessors and all original design/native/extended/CI24 checks | Real host receipt and all required independent CI, physical-device and release gates separate |

No accessibility suppression, test omission or release credentials. Local QA proves source and synthetic evidence verifier behavior; Android runtime, native delivery and hosted proof remain pending. Maven availability is external; one shared build reduces repeated downloads, but cannot guarantee Maven will not rate-limit.

## UX-R04-F05 — Measured Chrome, Rotation and Capture Dialog Repair

Forward only on the exact F04 head of draft PR #33; preserve R03, R04, F01–F04 committed transactions and native sessions. No new milestone in the six-overlay denominator.

| Promise | Production / QA repair | Required evidence |
|---|---|---|
| UXR04-F05-P01 | Root retains sole inset allocation; Material navigation no longer duplicates system-bar padding; actual menu-item visible geometry checked against 48dp | Real tablet landscape and all ten journeys |
| UXR04-F05-P02 | Theme overflow min size and actual ActionMenuView overflow layout allocation 48dp; real measured visible touch check | API36-840, tablet landscape, baseline and all root ATF checks |
| UXR04-F05-P03 | Freeze exact orientation before launch, verify actual resumed Activity display real size/rotation/configuration with bounded reassertion; safe numeric failure receipt | Actual landscape journey and independent performance launch, exact screenshot dimensions retained |
| UXR04-F05-P04 | Native capture choice rows own individual clicks and stable research/module IDs; actual dialog scroll bounds; real process-death PID and interruption unchanged | Primary five instrumentation tests plus actual process death and native baseline |
| UXR04-F05-P05 | Six predecessor receipts preserved, exact twelve-job shared APK evidence/CI24 unchanged | Native transaction/session/PR, all five workflows and independent artifacts |

No acceptance thresholds, cases or assertions removed. All external Android, hosted, physical-device, visual and release acceptance remains pending.
