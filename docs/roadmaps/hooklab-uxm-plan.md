# HookLab — App-wide UX fidelity and implementation plan

**Proposed roadmap:** HOOKLAB-UX-M · revision 1.0 (planning; no implementation or native acceptance claimed)

## 1. Authority, provenance and sequencing

- Repository: `Mikeyphw/MikeysHookLab`. Observed main `5da4ed3810db7f6aa73241eac9141948f4f3146d`, tree `7d91820f4861242703a6823833dbfb74c30ff141`; open draft PR #40 candidate `09bb40b7f718219861d49bdb38be4afea91b63d7`. Reconcile exact merged successor and tree before authoring. Do not replay MOD-04.
- Design input: `hooklab-ux-interactive_cg.html`, 41,843 bytes, SHA-256 `2010fca484ad65f78f7a4cb688fb2fdba80aa8b119546762e6943e44aefc0b58`. User directs implementation of this design app-wide. Freeze the exact original bytes under a new versioned design reference; do not overwrite old references or silently edit the prototype to match implementation. This direction does not approve unobserved native screenshot goldens.
- Existing active combined ledger: `docs/roadmaps/hooklab-modular.json` v2.0-MOD-04, 12 counted combined overlays, historical references excluded. Keep all existing IDs, definitions, receipts and historical thresholds. Add new proposed overlays only via governed roadmap adoption. Do not count hotfixes or claim a project-wide denominator.
- Sequence: reconcile MOD-04 → UX-M00 → cohesive UX-M01/M02/M03 → UX-M04/M05 → UX-M06 → MOD-05 (new tools use same UI rules) → UX-R05 (final visual acceptance, including MOD-05). UX-M06 qualifies the redesigned existing surfaces; it does not replace UX-R05 or close physical/runtime/release gates. Existing security and behavior promises survive.
- Before implementation: recover exact cumulative `devop-hooklab` predecessor, current Devtool/Showcase ancestry, authoritative Library contracts, validators, pins, ledgers and permissions. Validate them in full. Use only current Devtool-native lifecycle; no local Gradle, no direct GitHub mutations, no unqualified delivery. This planning file does not certify that governance or any implementation is complete.

## 2. Binding design scope

Use the actual HTML, not a prose approximation, as reference. Keep Java/AppCompat/Material native Views, not a WebView. Native rendering must be comparable to the reference, with approved and bounded platform differences only. Do not add a module master switch: the prototype explicitly provides independent hook flags. Groups derive from module identity and become a catalog grouping or retained deep-link alias, not an obligatory second management stack.

| Surface | Required layout and workflow |
|---|---|
| Home | Manager overview, metrics, needs-attention rows, Diagnostics and Repository quick access. Real observed state only. |
| Modules | Search, Installed/Updates/Needs attention/Enabled filters, compact grouped rows, status and update hints; repository entry. |
| Apps | Searchable app list, stable app route, scope and per-app profile management, shared module link. |
| Module workspace | Header with icon/title/version/source/status; sticky horizontal Tweaks/Apps/Activity/About tabs. Same route regardless of hook count. |
| Tweaks | Independent switches plus separate row detail targets, profile selector and Manage profiles, typed settings and inline preview, unsaved-change review/discard bar. No duplicate hook controls across tabs. |
| Hook details | Activation first, module tweaks, runtime evidence, safety/compatibility, collapsed technical information. |
| App details | Compatibility, explicit LSPosed scope, inherited/overridden profile, shared module workspace. |
| Profiles | Edit selection, active selection and app override remain separate semantics; management dialog, validated create/remove, contextual override dialog. |
| ChatGPT | Show Run in Termux, repeat-tap interval, inline code preview with Copy preserved, advanced diagnostics conditional on Show Run. Use actual SDK limits and actual behavior. |
| Word Master | Custom filter, opacity 40–128, tone navy/neutral/warm, inline illustrative filter preview; report actual effect as unconfirmed unless evidence proves it. |
| Activity | Module-filtered runtime evidence, empty state, full diagnostics entry. |
| About | Selected version, version history, publisher/signature and collapsed technical data. |
| Repository | Source and session access, search and update filtering, human-readable review, version history/rollback; separate actual trust, pin, publish, restart and debug testing controls. |
| Settings | Appearance and administration sections; all real existing settings, scope, privacy, help, about, recovery and developer tools remain reachable. |
| Diagnostics and remaining legacy surfaces | Reuse the same shell, row styles, tokens and status model for Timeline, Health, Capture, event reports, export, MCC, Termux bridge, SAF and permission flows. Preserve their existing real controls. The HTML contains only a simplified diagnostics example and omits many of these routes; specify and approve reference extensions before implementing them. |

Prototype-only fixtures, letter avatars, sample events, fake versions, reset-demo action, mock badge, fake authorization and no-network disclaimer must not ship as real functionality. Real package icons, signed identities, observed compatibility and legitimate permission UI replace demo data. Do not copy the browser implementation's full rerender or simulated mutation behavior. The mock's 40–46 px controls and 28 px switch track require 48 dp native touch areas. Real SDK profile naming/count/ranges and canonical defaults prevail; document any mismatch as a prospective reference delta before native code. System insets, IME, accessibility scaling and Android font rasterization are platform constraints, not blanket exemptions. No unapproved changes to visual structure.

## 3. Native design system

Extract exact tokens from the frozen HTML programmatically: dark background `#111318`, panel `#191c23`, raised `#232730`, divider `#343943`, text `#e8eaf0`, muted `#a7afbf`, accent `#bed0ff`, on-accent `#172b52`, good `#9bd7b0`, warning `#f4ca87`, danger `#ffb4ab`; light background `#f7f8fc`, panel `#ffffff`, raised `#e9edf5`, divider `#d7dce6`, text `#1b2331`, muted `#536073`, accent `#315b9c`, on-accent `#ffffff`, good `#256b42`, warning `#805100`, danger `#9c302c`.

Use 16 dp surface radius; 64 dp rows; 44 dp standard and 60 dp hero icon visual sizes; 52 dp search; 50 dp tabs; 29/26/20/15/13 sp nominal text hierarchy per semantic component; 12–16 dp compact content padding, 28 dp desktop; 760 dp rail breakpoint, 1100 dp two-column workspace, compact variant at 360 dp. Reference rail width 230 CSS px; test native adaptation with explicit measured content width and no implicit scaling. Separate touch bounds from visual bounds. Keep sticky tabs, bottom navigation on phone, labeled rail on wider screens, sticky review bar that does not obscure controls, proper scroll and focus restoration. Material You/AMOLED remain supported as independent themes, not a reason to change fixed-theme reference palettes.

## 4. Work packages and promise IDs

Each ID below is an independent obligation. For each record exact source entry point, real caller, positive/negative/lifecycle test, evidence artifact and implementation/acceptance verdict. Every batch must compile and pass applicable existing and new gates; no half-integrated duplicate UI generation.

| Overlay / promise | Production change | Required regression evidence |
|---|---|---|
| UX-M00-P01 | Freeze reference bytes, versioned token manifest, complete route/state/interaction roster and explicit platform deltas | Exact digest, reproducible browser render, missing/changed reference rejected |
| UX-M00-P02 | Capture equivalent native/browser fixtures and define semantic IDs/regions for all routes | Real source-bound fixture identity; absent/extra state rejected |
| UX-M00-P03 | Required fidelity, UX and native golden gate modes; no candidate-only substitution | Mutated geometry, color, label, missing control, broken journey and golden tampering rejected |
| UX-M00-P04 | Preserve prior test roster and thresholds, review workflow and baseline ancestry | Old contract still verifies; transition metadata maps every prior promise |
| UX-M01-P01 | Four-destination shell, bottom/rail navigation, route and tab restoration | Home/Modules/Apps/Settings, real Back, deep links, rotation, process death |
| UX-M01-P02 | Unified module/group/app catalog and consistent destination type | One/many hooks, search by name/description/package/ID, empty/filter state |
| UX-M01-P03 | Extract shared catalog/state/route presenters from MainActivity and PackManagerActivity | No duplicate legacy management routes; valid old intents redirected by stable ID |
| UX-M02-P01 | Actual module workspace and reusable row, header, tab, preview, notice components | Exact tabs, independent row/switch targets, no repeated controls |
| UX-M02-P02 | Hook preference at first visible region; scope and safety separate | Confirmed LSPosed write/readback, failure/unknown/pending/lock states; no implicit scope or execution |
| UX-M02-P03 | Consistent state projection and real evidence | Enabled vs observed execution, compatibility, selected/published version, restart and unknown are independent |
| UX-M03-P01 | Persistent per-module draft and dirty tracking outside views | Tab/module/back/rotation/process death retain edits; explicit discard only |
| UX-M03-P02 | Profile editor, override management and human-readable diff | Create/remove/activate/inherit, actual SDK boundaries, review cancellation, no raw JSON review |
| UX-M03-P03 | Native ChatGPT and Word Master typed controls and previews | Hidden Run retains Copy, interval constraints, opacity/tone, conditional controls, live illustrative previews |
| UX-M03-P04 | Verified save with conflict handling and retained drafts, import/export/reset/undo | Concurrent change, service loss, failed readback, cancellation, process death, malformed SAF input |
| UX-M03-P05 | Preserve custom UI ABI and explicit reviewed archive consent | Admit/cancel/close/recreate, same-UID warning, test-channel rejection, no hidden execution |
| UX-M04-P01 | Repository browsing, session credential dialog and update filters | Public/private session access, token clear on stop, offline/stale/invalid index, no credential logs |
| UX-M04-P02 | About/version/publisher/recovery flows and explicit selection/publication/restart | Verified signature, incompatibility, pin, rollback, batch atomicity, safe mode, cancellation |
| UX-M05-P01 | Apply design system to all remaining app screens and dialogs | Complete native route inventory including diagnostics/MCC/Termux/legacy settings |
| UX-M05-P02 | Adaptive, accessible, theme-correct and efficient native rendering | Existing 10-case matrix plus added module/app/repository/config journeys; no overflow, focus loss, undersized target or hidden primary action |
| UX-M05-P03 | Targeted state updates, stable identities, bounded lists and background I/O | Stable focus during toggle/search, no main-thread remote calls, operation-specific pending states |
| UX-M06-P01 | End-to-end UX and fidelity gate required for impacted UI changes | All adopted browser/native pairs and journey assertions pass; no skipped required case |
| UX-M06-P02 | Reviewable native baseline admission, independent verifier, regression report | Signed/source-bound provenance, exact APK/test APK/run/attempt, independent recomputation, no self-approval |
| UX-M06-P03 | Reconcile prior promises, runtime/device/release boundaries, operator handoff | Exact cumulative operator locally qualified; hosted/device/release tracked separately; all outstanding gaps recorded |

## 5. Mandatory qualification contract

### A. Reference and design preflight

Create proposed versioned artifacts under `design/hooklab/`: original HTML, reference manifest with hash and ancestor, semantic roster, tokens, state fixtures, navigation journeys, platform delta ledger, native golden manifest. Extend existing `reference.py`, `visual.py`, `visual_routes.py`, `visual_body.py`, `mod03_visual.py`, `verify_ui_artifacts.py`, `qualification.py`, native instrumentation and workflow orchestration; reuse shared APK build and evidence identities. Do not introduce competing screenshot systems. Add stable semantic IDs to production native views, not artificial test-only duplicate screens. Use fixture data to drive actual app routes through existing isolated debug infrastructure; release builds must exclude fixtures.

### B. Cross-renderer fidelity — blocking before UI acceptance

Freeze HTML with pinned browser, fonts, viewport, theme, locale and deterministic fixture. Match identical semantic state and measured content viewport to real native Activity; exclude only measured system chrome or predeclared narrowly scoped differences. For every required route and component verify exact role, label (or approved localization), order, visibility, selection, state and action binding. Require every semantic comparison pass, not a report-only result. Nominal geometry ±2 dp, typography ±1 sp, fixed-theme semantic color RGB delta 0. Compare surface sizes, spacing, radius, divider, alignments, navigation placement, sticky bars and touch bounds. Record per-region screenshot SSIM/changed pixels and diff triptychs. Before native implementation, set reviewed per-route/region cross-renderer raster limits using deterministic font/platform calibration, not the implementation's errors; do not blindly apply native 99%/1% to unlike browser rasterization. Unknown/missing limit, fixture mismatch or unapproved delta fails the gate. No global masking, resize-to-hide mismatch, averaging failed components away, or automatic threshold relaxation.

### C. Native visual regression — blocking once approved golden exists

Retain existing strict native repeat test: SSIM ≥0.99 and changed-pixel ratio ≤0.01, geometry ±2 dp, typography ±1 sp, semantic RGB exact. Apply same strict native-to-approved-native criteria for every approved same-environment route. Approved golden must be derived from a native candidate already passing reference fidelity and reviewed independently. Store per-state source/tree, APK digest, environment, reference and image hashes, reviewer identity and acceptance scope. No automatic golden replacement; a planned redesign of an old approved state requires explicit reviewed successor and retained history. Before a new golden is approved, candidate evidence may be produced and locally qualified operator may be delivered, but UX acceptance and final release remain pending. A candidate pass never becomes an approved pass.

### D. UX behavior — blocking from first implementation batch

Define interaction budgets as proposed acceptance criteria for standard 390dp/1.0 font fixture: from Modules, one row tap opens Tweaks; hook preference reachable directly without scrolling in the default tested module fixture, one explicit switch operation; app scope reachable via Apps → target app; profile settings reachable within two actions from module catalog; no unnecessary duplicate page; configuration save requires review and explicit confirmation. Dirty bar visible when draft differs, no dirty bar for a clean draft. Draft survives tab/back/module changes, rotation/process death and recoverable failures; conflict shows resolution choices, no silent overwrite. One authoritative hook control per workspace, row tap never toggles. Unknown never false OFF, enabled never equates to running. Repeated tap while pending cannot duplicate writes. Search updates on input and retains focus, selected item and scroll as applicable. No route depends on single/multiple hook count. No raw JSON as default review. Existing import/export, reset, undo, scope, locks, publication, signature and custom UI permission paths stay covered.

### E. Adaptive and performance — blocking

Preserve all existing ten adaptive matrix cases and required CI24 roster. Expand actual native module and profile/repository journeys for 320/360/390/393 dp phones, 600/840 dp tablet, portrait and landscape, API29/API36, 1.0 and large-font settings, light/dark/dynamic/AMOLED where supported. Capture actual route states including keyboard, pending/unknown/error, empty results, review dialog, large profile names, conflicting drafts, update/rollback and no framework. Require no clipping/horizontal overflow (except intentional tab scroll with accessible choices), real 48 dp action bounds, screen-reader roles/state, meaningful focus order, text contrast ≥4.5:1 for normal text, IME and system inset correctness. Set reproducible performance budgets from a measured baseline before implementation; fail regression outside reviewed budgets. Avoid duplicating expensive builds: shared APK once per source/run attempt; targeted test selection with fail-closed impact routing and existing exact-tree reuse only where authorized.

### F. CI gate integration and final status

Existing jobs `design`, `visual`, `adaptive`, source, Android and CI24 remain independent. Add required result dimensions `reference_contract`, `native_fidelity`, `ux_journeys`, `native_regression`, `visual_approval`, `adaptive`, with per-case status and explicit pending/not-applicable reason. UI source, resources, reference, test/validator/impact changes trigger all relevant UI checks; global shell/shared tokens trigger all routes. Final qualification rejects missing/failed/stale/unauthorized skipped required evidence. Candidate vs approval remains separate; pending device or approved native golden does not veto otherwise local operator qualification, but does veto declaring final UI/release acceptance. Independent verifier recomputes metrics from exact original artifacts and verifies roster and provenance; no reliance on a claimed PASS string. Preserve first failures, use bounded diagnosed retries only. Export summary in Actions with per-route pass/fail, measured differences and direct links to reference/native/diff artifacts. Branch-protection configuration requires authorized host-side changes if not already enforced.

## 6. Evidence and transition acceptance

A batch cannot be considered source complete until every scoped promise has working real callers and positive/negative/lifecycle checks. UX-M06 requires complete adopted route coverage, no open critical source defect, exact source-bound hosted UI and CI24 qualification, no silent design divergence. MOD-05 must introduce its omitted developer surfaces under the same design and test scheme. UX-R05 is the final cross-app visual acceptance gate after MOD-05; physical LSPosed behavior and signed release proof remain independent. Historical test expectations that explicitly preserve bad behavior (e.g. replacing a switch instance, cardinality-based destination routing, exactly three primary nav entries) must be deliberately migrated to stronger behavioral equivalence checks, with a reviewed mapping and preserved history, never simply deleted or weakened.

Planning decisions: exact prototype layout is the target, not its simulated data or JavaScript rendering approach; new overlay IDs and budgets are proposals; additional routes absent from mock require reference extension; no golden approved here. No repository modification, CI run, source qualification, operator artifact or contract pin update performed by this planning task.
