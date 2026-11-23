# Mikey's HookLab — Full-App UI/UX & Diagnostics Final Roadmap

**Roadmap ID:** `HOOKLAB-FLAT-UX`  
**Revision:** 1.0 · **Prepared:** 2026-11-23  
**Status:** ADOPTED ROADMAP — DS-01, UI-04, UI-05, DX-01, UI-06, DX-02 and DX-03 merged with required hosted CI PASS; UI-07 source candidate pending native/CI/device acceptance  
**Repository home:** `docs/roadmaps/hooklab-flat-ux.md` (committed by DS-01)  
**Verified UI-07 base:** `Mikeyphw/MikeysHookLab` `main` at `5d091ad9985dac6f81d9cb60b29f94880508712b` (merged PR #20, DX-03).  
**Contract:** `/Agent Contracts/ai_agent_operating_contract_cg.md`; `AGENT-OPERATIONS-DEVTOOL` v1.15; SHA-256 `cb3963ba70dd90001d04b07eb11c12cb6f29fad3d9de5fb19dda3952e1596100`.  
**Visual source of truth:** `hooklab_diagnostics_mock_cg.html`, SHA-256 `0d2d0050be230ee9d27529d711cdb48022b87046f076cc11dd883bb1aa1a126b`, explicitly extended across the **entire** native app. Prototype behavior uses sample data and does not prove Android/LSPosed functionality.

> **One app, one visual language.** Dark, calm, flat and borderless; generous negative space, editorial type, mint accents, low-contrast tonal surfaces, thin or absent separators, no elevated cards, no busy outlines. The diagnostics prototype's appearance governs Home, Hooks, hook/group setup, Diagnostics, Scope, Settings, About, MCC control, dialogs and export sheets. No HTML WebView rewrite is implied; implement native Material components and ordinary Android accessibility/behavior.

## 1. Verified starting point and scope of this roadmap

The verified repository has five independent, opt-in `TweakCatalog` entries: MCC manual diamond control; Neo Store → ObtainX; Droid-ify → ObtainX; Word Master experimental night filter; and the read-only ChatGPT code-block callback diagnostic. `MainActivity` currently exposes Home / Apps / Diagnostics plus app detail, Scope, Settings and About. `FrameworkStateStore` provides asynchronous framework-owned preference and scope state. `VerboseLogs` emits opt-in, rate-limited, sanitized events via libxposed; the current Diagnostics page mainly exposes configuration/safety locks rather than a log viewer. Source acceptance and real-device status must not be inferred from a merged PR.

**Explicitly out of scope:** inventing new hook behavior, making unshipped Termux execution appear available, unlocking app purchases, changing signing credentials, installing APKs, forcing root, or replacing libxposed/LSPosed with an app-managed hook injector. The proposed design may describe future capability placeholders when clearly labeled as such.

### Current registered hooks → proposed mini-module groups

| Stable group ID | Group title | Existing registered member(s) | Target applications | Availability |
|---|---|---|---|---|
| `group.obtainx` | ObtainX Integration | Neo Store → ObtainX; Droid-ify → ObtainX | Neo Store, Droid-ify | Source present; independent runtime acceptance not established |
| `group.chatgpt_termux` | ChatGPT → Termux | ChatGPT Code-Block Action Diagnostic | ChatGPT | Diagnostic in merged source; **Termux execution is planned, not shipped** |
| `group.mcc` | MCC Diamond Control | MCC Manual Diamond Button | Meu Cronograma Capilar | Source present; explicit confirmation for root-sensitive actions |
| `group.wordmaster` | Word Master Night Filter | Word Master Experimental Night Filter | Word Master | Source present; not a full in-game dark palette |

**Group semantics:** A group is a *discovery and organization unit*, never a new entitlement, global scope grant, injector, APK, or forced common toggle. Each registered hook retains its persisted ID, individual opt-in, exact package/version/process guards, safety lock, compatibility and scope. Multiple hooks may target one app or one group may span apps. Future groups (for example Tinder) appear in the active catalog **only after** independently reviewed, registered real hooks exist.

## 2. Final information architecture

### Primary navigation

- **Phone bottom bar:** `Home` · `Hooks` · `Diagnostics` (exactly three main destinations). App settings via consistent top-right action, not a fourth crowded tab.
- **Tablets/large landscape:** quiet left navigation rail; content max width; diagnostics optionally becomes two-pane timeline + inspector. Native Android back semantics preserve last destination, selected hook, filters and scroll.
- **Top-level secondary destinations:** Settings, Scope & permissions, About, Help/Documentation, Export history.
- **Deep screens:** Group detail → individual Hook detail; Diagnostics Timeline → Event inspector; Capture → Capture session report; Health → per-hook diagnostic report; MCC manual control.
- **Global search:** discover hooks/groups/apps plus log search when in Diagnostics. Never mix search results from unrelated contexts without labels.

### Screen contract

| Screen | Primary purpose | Required visible content/actions |
|---|---|---|
| **Home** | Actual framework/readiness and useful next actions | LSPosed service/scope health, registered hook and group counts, problems needing attention, recent **observed** events where available, quick links to hooks/capture, no optimistic “running” claims |
| **Hooks** | Default group organization; switch to By app | Search, sort/filter, expandable groups, hook counts, target icons, concise status summary, group/app view persisted; no misleading group enable |
| **Group detail** | Mini-module context across apps | Group description, exact member hooks, independent state, per-target scope, version support, recent events, diagnostics shortcut |
| **Hook detail** | Safe opt-in and troubleshooting | Individual toggle, supported build, explicit package/process, scope state, safety lock, last verified runtime evidence, restart guidance, dedicated logs, failure explanation |
| **Diagnostics · Timeline** | Follow signals | Merged chronological events, visible source provenance, filter/search/time window, severity, pause/follow, event inspector, copy/export; no false attribution |
| **Diagnostics · Capture** | Reproduce an issue / discover new hooks | Guided target selection, allowed collector sources, one bounded capture session, action markers, stop/report, redacted export |
| **Diagnostics · Health** | Diagnose failures without log archaeology | Framework/scope/prefs/version/safety-lock conditions, last actual event, error categories, collector gaps, next safe steps |
| **Scope & permissions** | Understand and request framework authority | Actual LSPosed scope per target, safe request/removal, pending/unavailable states, confirmation, never bulk-authorize by selecting group |
| **Settings** | Appearance, privacy and diagnostics behavior | System/light/dark/AMOLED, dynamic colors, log retention, capture permissions, redaction defaults, developer mode opt-in |
| **MCC manual control** | Existing dangerous action in safer visual design | Dedicated summary, preflight, explicit confirmation before root, real state and refusal reasons; no counterfeit success state |
| **About / Help** | Traceability | Package/app/source/version details, LSPosed API support, signing identity *fingerprint only where available*, privacy and diagnostics guidance |

## 3. Design system — exact prototype-inspired look

### Visual tokens

| Role | Dark | Light | Behavior |
|---|---|---|---|
| Canvas | `#0d1214` | `#f4f6f3` | Default page/background |
| Surface | `#151b1e` | `#e9eeeb` | Tonal rows, sheets, active event inspector |
| Surface 2 | `#1e272a` | `#dce5df` | Selected chips/secondary affordances |
| Primary text | `#f0f4f1` | `#182321` | High-contrast typography |
| Secondary text | `#9ca9a7` | `#546660` | Captions/labels |
| Muted text | `#677775` | `#6d817a` | Metadata (verify accessibility contrast) |
| Accent | `#b7eccb` | `#295e4c` | Focus, selected tab, primary CTA |
| Accent ink | `#183229` | `#ffffff` | Accent-button foreground |
| Warning / error | `#f4c987` / `#f29d9a` | `#916126` / `#ae403f` | Always paired with icon/text, not color alone |

**Shapes:** Zero stroke borders on ordinary cards and controls; no elevation shadows or raised panels. Use intentional tonal surfaces with mostly 8–16 dp corner radii, 0 dp seams, 8 dp spacing scale, and larger editorial headings. Bottom navigation has a continuous tonal surface but no dividing line. The event timeline is predominantly open whitespace; selected events may have a soft surface highlight.

**Typography:** Material/Android system-friendly sans stack, 34–40 sp mobile primary headlines where suitable, 20–26 sp section headlines, 14–16 sp body, 11–13 sp metadata; scalable and ellipsized responsibly. Monospace only for event codes/IDs, not the whole UI.

**Motion:** 120–220 ms transitions for tab/filter/inspector, subtle position/opacity shifts; respect system animator and reduced-motion settings. No spinning heartbeat falsely implying activity. Skeletons and clear stale-time markers beat indefinite loading.

**Themes:** Preserve existing light/dark/AMOLED/system and Material You user choice. The exact mint palette is the reference; dynamic color remains optional and may alter accent hue but not the borderless composition. WCAG-conscious dynamic text contrast, touch targets >= 48 dp, screen reader labels, bidi and large fonts are required.

**Responsive:** phones ~320–600 dp use bottom nav, full-width sections and overlay inspector sheet; mid-width landscape/tables use adaptive columns; >= 840 dp rail + two-pane diagnostics. The same content exists on all sizes; never hide critical warnings on narrow screens.

## 4. Interaction and application state rules

1. **Single state authority:** `TweakCatalog` is the source for installed hook entries; new `HookGroupCatalog` owns static group metadata, not configuration. `FrameworkStateStore`/framework service remain source of truth for scope, remote preferences and safety locks.
2. **Stable identity:** Select hook by hook ID; never `selectedPackage` as the unique hook key. This fixes the current “first entry for a package” limitation and prevents future multi-hook app collisions.
3. **Group/app view:** Groups default; By app optional and persisted in app-local appearance/UI preference. Moving between views preserves hook identity, current filters and no opt-in state.
4. **No imaginary toggles:** An entire group has no underlying framework enabled flag. Do not show group “on” unless explicitly implemented as an aggregate display with clear mixed states and safe rules; default is no group-wide toggle.
5. **Unknown is not off:** Framework disconnected, scope unknown, preference read failed, version unsupported, safety lock present, installation observed and *running* are distinct statuses.
6. **No optimistic success:** Every toggle/scope write stays pending until framework readback. Retain source-bound generation/stale-observer rejection from UI-03. An acknowledged scope request is not confirmed scope approval.
7. **Safe defaults:** Developer capture and verbose logs off by default; no auto root, auto export, background log scraping, hidden Termux launch or unsafe clipboard capture.
8. **Navigation:** Detail/back state, restoration after rotation/process death, selection and source filtering must be tested, not inferred from a screenshot.

## 5. Unified diagnostics — real instrumentation, not just a pretty timeline

### Collection architecture

`Structured HookLab Events` + `LSPosed Module Logs` + `LSPosed Framework Logs` + `Optional Authorized Android Logcat` + `Operator Markers`  
→ **Source adapters** → **normalize** → **time alignment** → **correlation with confidence** → **privacy filters** → **bounded session store** → **Timeline / Inspector / Health / Export**.

- **HookLab adapter:** Wrap current libxposed codes with versioned, structured fields and stable hook ID; use optional, rate-limited emission and preserve source compatibility. Do not serialize clipboard contents, app object payloads, account data or credentials.
- **LSPosed adapters:** Import framework/module manager-exported logs through Android Storage Access Framework / Share. Optional read-only privileged collector requires explicit request and exact installed LSPosed variant detection. Ordinary app permissions do not imply access to other process logs or libxposed read-back APIs.
- **Android adapter:** Optional, time-limited authorized logcat collection only when capabilities truly exist; clear unavailable/import-only path otherwise. No silent `READ_LOGS` or permanent root logging service.
- **Annotation adapter:** User marks “opened app”, “tapped item”, “expected toolbar”, “observed failure”; timestamps are recorded as *user annotations*, not host-confirmed actions.

### Event identity contract

Each record contains `schema_version`, `event_id` (stable unique provenance key), UTC and monotonic time if available, `source`, `collector`, optional `import_file_hash`, `severity`, `event_code`, `message_redacted`, `process`, `package`, `hook_id` nullable, `group_id` derived, `capture_id` nullable, `correlation` (`EXACT`, `ASSOCIATED`, `UNCLASSIFIED`), `redaction` state and source-specific loss/clock precision metadata. The inspector always identifies the original source record. Do **not** claim a framework warning originated in a hook merely because the package names match; never deduplicate two records solely on equal text.

### Timeline, search, filter and copy/export

- Hierarchical filters: **Group → App → Hook**, then source, severity, time window, process, capture ID, search and event kind. Any app/group/hook selection can include adjacent source events with provenance and associated confidence; user can choose “Strict matches only”.
- Virtualized large timeline, pause/follow, filters-as-URL-equivalent persistent UI state, event inspector detail, visible dropped-event or collection-gap indicators.
- Copy single event, selected events, currently filtered **redacted** dataset, or bounded context around error. Copy button may not report success before OS confirms. Log-copy control is separate from ChatGPT code-block clipboard logic.
- Export `.txt`, `.jsonl`, or redacted evidence `.zip` through Android SAF/Share. Include manifest, time window, filters, sources, source version/app identity and SHA-256 of exported files. Preview redactions and sizes before export; no automatic sharing.
- Backpressure, finite retention, indexed paging, cancellation, crash recovery and report-only fallback. Diagnostic failure cannot take down host apps or modify hook execution semantics.

### Capture / discovery

Start with target **group/app/hook** or an explicitly unregistered target (research-only), select sources, define a time bound, capture the reproduction with operator markers, stop and inspect a report. Provide per-target package/version/process evidence, class/method *metadata only when legally/technically accessible and explicitly allowed*, relevant failed guard categories, provenance and negative cases. New hooks require their own source review, version pins and consent; a capture session must **not** automatically install invasive runtime probes.

### Health

Separate **framework service** / **remote preferences** / **LSPosed scope** / **app version compatibility** / **safety lock** / **hook install attempt** / **last observed event** / **collector access** / **storage/retention**. Show `UNKNOWN`, `OUT_OF_SCOPE`, `DISABLED`, `INCOMPATIBLE`, `GUARDED`, `OBSERVED`, `FAILED` appropriately. Offer precise, reversible next steps; opening Health never resets a safety lock.

## 6. Full overlay pipeline — proposed new work

**Pipeline denominator: nine proposed NEW UX/DX overlays.** These are **not** a claim that HookLab's total cross-project roadmap has nine overlays. Within the nine-overlay tranche: **DS-01, UI-04 and UI-05 merged with exact-head required hosted CI PASS; 0/9 independently device-accepted**; DX-01 is an unaccepted source candidate. The roadmap is adopted in the repository. Each row has mandatory independent local source, native Devtool, hosted exact-head CI, device and release gates as applicable.

| ID | Complete name | Main delivery | Dependencies | Implementation / acceptance |
|---|---|---|---|---|
| `DS-01` | Flat Design System & Adaptive Native Shell | Tokens, themes, responsive navigation, surfaces, reusable typography and controls | Current UI-03 stable state | MERGED · hosted CI PASS / DEVICE PENDING |
| `UI-04` | Hook Groups & Group/App Catalog | Catalog metadata, default Groups, By app, search and stable hook identity | DS-01 for final visuals; current `TweakCatalog` | MERGED · hosted CI PASS / DEVICE PENDING |
| `UI-05` | Group/Hook Detail & Independent LSPosed Setup | Mini-module details, per-hook switches/scope/status, multi-hook same-app support | UI-04, UI-03 state authority | MERGED · hosted CI PASS / DEVICE PENDING |
| `UI-06` | Home Dashboard, Readiness & Recent Activity | Flat Home, honest framework/readiness summary, quick actions, last imported evidence (explicitly unauthenticated) | DS-01, UI-04, DX-01 for imported event provenance | MERGED · hosted CI PASS / DEVICE PENDING |
| `DX-01` | Unified Diagnostics Timeline, Log Adapters & Export | Structured events, source ingestion, correlation, filtering, inspector, safe export | DS-01, UI-04 metadata | MERGED · hosted CI PASS / DEVICE + SOURCE GAPS PENDING |
| `DX-02` | Guided Capture Sessions & Hook Discovery | Reproduction workflow, bounded collection, annotations, probe metadata, evidence bundle | DX-01 | MERGED · hosted CI PASS / DEVICE + SOURCE GAPS PENDING |
| `DX-03` | Hook Health, Troubleshooting & Evidence Explorer | Source-aware health/status, failure analysis, collector gaps, capture-linked reports | DX-01, UI-05; DX-02 for capture links | MERGED · hosted CI PASS / DEVICE + SOURCE GAPS PENDING |
| `UI-07` | Settings, Scope, MCC Control, About & Help Redesign | Remaining first-party screens in same language; independent permissions/confirmation retained | DS-01, UI-05 | SOURCE CANDIDATE · NATIVE/CI/DEVICE PENDING |
| `UI-08` | Accessibility, Regression, Performance & Legacy UI Retirement | Full app QA; replace old visual paths, reconcile tests/docs; safely adopt complete redesign | All above source-complete | PLANNED / PENDING |

### Mandatory promise/acceptance checklist, per overlay

**DS-01:** `DS01-P01` all screens share exact tokens/theme palette; `DS01-P02` phone/tablet/landscape and edge-to-edge responsive shell; `DS01-P03` no raised cards/borders or inaccessible focus states; `DS01-P04` theme transitions incl. system/dynamic/AMOLED preserve contrast. **Proof:** pixel/screenshot comparison, accessibility scanner/manual TalkBack, 320/360/600/840+dp and dark/light/AMOLED, process-state restoration, actual device.

**UI-04:** `UI04-P01` every existing hook exactly once in group metadata; `UI04-P02` default Groups and persisted By app; `UI04-P03` multiple hooks per app / groups across apps; `UI04-P04` zero hook/scope side effects when changing views. **Proof:** catalog invariant tests, fixture with two hooks same package, rotation/restart, UI instrumentation.

**UI-05:** `UI05-P01` per-hook saved opt-in with authoritative readback; `UI05-P02` per-target scope requests and remove confirmation; `UI05-P03` safety/version/unknown status & independent controls; `UI05-P04` deep links/selection never open wrong hook. **Proof:** disconnected/stale async fixtures, failed write/permission denial, multiple hooks same app, actual LSPosed.

**UI-06:** `UI06-P01` counts refer to registered hooks/groups/apps and scoped *unique* target packages; `UI06-P02` framework state and last real event show provenance and unknown where absent; `UI06-P03` quick actions route to right hook/capture with no accidental enable. **Proof:** empty/loading/error states, screen navigation, source-bound event test.

**DX-01:** `DX01-P01` HookLab structured event producer; `DX01-P02` LSPosed import, plus privileged/logcat adapters gated by capability; `DX01-P03` merged timeline attribution/time ordering/duplication policy; `DX01-P04` cross-entity/time/source filtering and inspector; `DX01-P05` redacted copy/export and manifest; `DX01-P06` nonroot and collector-failure resilience. **Proof:** mixed-source fixtures, rotated/truncated logs, clock skew, PII/redaction tests, real LSPosed exported log, device root denied, file hashes/round-trip JSONL.

**DX-02:** `DX02-P01` start/stop/cancel bounded capture; `DX02-P02` user action markers and evidence attribution; `DX02-P03` metadata-only probe inspector with safety guard; `DX02-P04` source-bound export of replayable reproducible report. **Proof:** termination/reboot/permissions/timeouts, no log leak, real target action test, no implicit hook injection.

**DX-03:** `DX03-P01` complete independent health dimensions; `DX03-P02` source-linked error and collector-gap analysis; `DX03-P03` safe suggestions and capture links without side effects; `DX03-P04` no false ACTIVE/success label. **Proof:** disconnected, stale, quarantined, incompatible, noisy logs, no events and successful runtime cases.

**UI-07:** `UI07-P01` scope and preferences remain framework-authoritative; `UI07-P02` MCC root/manual control retains all confirmation/preflight behavior; `UI07-P03` entire Settings/About/Help uses flat theme, precise privacy/identity disclosures; `UI07-P04` accessibility and restoration. **Proof:** permission denial and confirm-cancel tests, existing MCC safety guard regression, visual and TalkBack review.

**UI-08:** `UI08-P01` all native screens use final design, no duplicate legacy app-card pathways; `UI08-P02` all existing hook IDs and pref migration/regressions pass; `UI08-P03` UI performance/memory/accessibility tested on target devices; `UI08-P04` stable release exact-cert signing verified independently, if release authorized; `UI08-P05` docs/screenshots/status language and diagnostics privacy audit match shipped behavior. **Proof:** complete gate matrix, CI signature artifact and installed APK cert comparison if applicable, real-device end-to-end acceptance.

## 7. Sequencing without one giant risky UI rewrite

| Wave | Parallel/coordinated overlays | Observable milestone | Release gate |
|---|---|---|---|
| **A — Foundations** | DS-01 + UI-04 (interface model can start while tokens are implemented) | App shell, group/app catalog, persistent view switch | Source + native + hosted CI; no functionality regression |
| **B — All-core screens** | UI-05 + UI-07, with UI-06 skeleton independently | Hook and group configuration, redesigned Settings/Scope/About/MCC, Home shell | Runtime switch/scope/restart/safety QA |
| **C — Evidence backbone** | DX-01; connect UI-06 to actual event projection | Merged, searchable provenance-aware Timeline and export | Real imported LSPosed evidence + nonroot failure tests |
| **D — Developer workspace** | DX-02 + DX-03 | Capture workflow, log inspector, per-hook Health | Real reproduction/capture and safe export round trip |
| **E — Consolidated acceptance** | UI-08 across all prior work | One visually consistent app; old UI removed intentionally | Full hosted CI + device + signing/release checks separately |

Suggested delivery units are cohesive, but the order is a **dependency DAG**: UI-06 and UI-07 may progress in parallel with DX-01 where the backing data is not fabricated. Do not merge half-wired visual controls or ship stub buttons.

## 8. What happens to existing project overlays

The following is the **known** existing/previously proposed project inventory based on merged source and prior project receipts. It is **not a claim of exhaustive authoritative cross-project roadmap coverage** because a complete root-level HookLab repository roadmap was not found.

| Existing ID | Name | Source/delivery observation | Independent acceptance / next gate |
|---|---|---|---|
| `M6` | MCC Manual Diamond Control | Merged functionality present in catalog | Physical-device and sensitive action authorization not attested here |
| `M7` | Neo Store + Droid-ify ObtainX Integration | Merged, two independent hooks | App-version/device parity and intent handoff pending independent proof |
| `M8` | LSPosed Scope Recommendations & Optional Verbose Event Logging | Merged code present | Real framework logging and scope behavior must be tested |
| `UI-01/02` | Material 3 Control Center & Per-App Setup | Merged historic UI | Visual layer superseded by DS-01/UI-04–08, never disable behavior casually |
| `UI-03` | Asynchronous Framework State & Material You | Merged state plumbing | Preserve snapshot generation, pending writes and permission boundaries |
| `WM01` | Word Master Experimental Night Filter | Merged hook | Device test for real visual behavior, not true Unity theme |
| `CGT01-P01` | Static ChatGPT Code-Block Renderer Mapping | Research/static inspection evidence | App-path coverage incomplete |
| `CGT01-P02` | Read-Only Code-Block Clipboard Value-Parity Probe | PR #11 merged | Device value causality / visual block identity not implied by CI |
| `CGT01-P03` | Read-Only Native Callback Identity Probe | PR #12 merged | Per-action/device identity and Valdi/DIL coverage unverified |
| `CGT01-P04` | Valdi/DIL & Preview Identity Coverage | Previously proposed | Not registered/delivered in verified catalog |
| `CGT01-P05` | Open in Termux Action Confirmation | Previously proposed | Not registered/delivered in verified catalog; prerequisite validation |
| `TB01` | Native Termux Command Bridge | Local recovery inconclusive: original worktrees absent, durable archived transaction `abandoned-kept-changes`, no commit candidate | **Never replay from missing XDG state**. Reconcile preserved native logs and original Git before any new source mutation |

**Tinder:** the separate Library roadmap `HOOKLAB-TINDER` v1.0 is an independent proposed integration program and is not silently included in these nine UI overlays. When/if it yields registered hooks, they must adopt HookGroups, diagnostics and safety automatically without recoding the screens. A group must not be displayed as installed before real registration.

## 9. Source architecture & migration (native implementation target)

```text
app UI shell ── Home / Hooks / Diagnostics / Settings / Scope / About
    │
    ├─ UI theme tokens + adaptive layout primitives (DS-01)
    ├─ HookGroupCatalog ───────────── TweakCatalog / TweakSpec [UNCHANGED IDS]
    ├─ HookDetailState & GroupSummary ─ FrameworkStateStore [truth + pending writes]
    └─ DiagnosticSessionStore
           ├─ HookLab structured event adapter
           ├─ LSPosed imports and optional authorized collector
           ├─ Android logcat opt-in collector
           ├─ Correlation + redaction + clock reconciliation
           └─ Timeline + Health + Capture + copy/export
```

Native Java/Kotlin choice follows the project’s existing Java/libxposed source policy; no architectural requirement to rewrite the app in a WebView, Compose or a new framework. Prefer extracted testable Java presenters/models plus native Android views; replace old `MainActivity` monolith with clean screen controllers while preserving actual callers and contracts.

Migration requirements: keep existing opt-in pref keys and history, preserve `TweakSpec` and `TweakEngine` dispatch semantics, retain `FrameworkStateStore` async state and its strict generation checks, migrate only presentation preference keys for Groups/By app, preserve native LSPosed ownership of scope, and ensure no accidental diagnostic background process or permission expansion. Discard redundant UI wrappers only after all real callers/tests/docs migrate.

## 10. Acceptance, delivery and release protocol

- **Before each implementation overlay:** re-read the complete operating contract and current authoritative roadmap, verify intended exact `main` SHA/tree, list **every** remaining overlay by ID/name; independently report **source-implemented** and **accepted** progress. Reconcile newly merged PRs, source and preexisting Devtool sessions before touching worktrees.
- **Per individual promise:** code path and actual call chain; positive, negative and error/restart coverage; local structural/unit proof; native source-bound apply/commit/session; required exact-head hosted CI; separate CI24 when applicable; actual Android/LSPosed runtime and visual evidence; signing/release proof if shipping. Never substitute screenshots or mocked data for device acceptance.
- **Operator:** use existing single cumulative `devop-hooklab_cg.pyz` and installed `devop-hooklab`; one source-bound `overlay <ID> run` lifecycle with correct Devtool-native preflight/check/apply/commit/delivery/CI/watch, transaction receipts and original TTY output. No overlay-specific mini operators, hidden tmux, or unverified replay.
- **Never execute Gradle locally** or indirectly via Devtool validation. Android build/test/lint belong to exact-source GitHub Actions; PR CI debug APKs are debug signed and **not** the user's trusted stable cert.
- **Stable signing:** use existing main-only protected `stable-release.yml` and `hooklab-signing` environment, verifying source SHA, trusted certificate fingerprint, APK signature, versionCode advancement and install compatibility. Do not expose keystores/secrets to PR jobs; no release/device install merely because CI is green.
- **Privacy:** support safe redaction, preview and strict clipboard/export scope. No unrelated app messages, raw code, private logs, root permission grant or volatile memory content by default.
- **Cross-cutting governance:** repo protections, signing, privileged log access and future device approval are independently enforced at the correct boundary. An unresolved future release permission must not be called an implementation failure for an unrelated UI overlay.
- **Non-goals:** no group-wide root/permission prompt, no raw unbounded log collector, no nonfunctional “Active” status, no automatic sensitive data copying, no background collecting without explicit, expiring authorization.

### Source and acceptance baseline (truthful)

- **New UX/DX overlays:** `3/9` merged with required hosted CI; `0/9` independently accepted; DX-01 source candidate pending native and CI; five other overlays planned.
- **Existing merged source:** five registered hook entries and prior UI/state source present. **Existing cross-project denominator and accepted counts:** `INCONCLUSIVE` until the canonical repository-wide roadmap and per-gate acceptance receipts are established.
- **This deliverable:** DX-01 import-first source candidate with local tests; previous DS-01/UI-04/UI-05 PRs merged and hosted CI successful. **DX-01 native/PR/hosted CI/device/release:** `NOT PERFORMED`.
- **Next eligible implementation tranche once adopted:** `DS-01` + `UI-04`, followed by independent UI-05/UI-07 and DX-01 work as dependencies permit.


### UI-04 source implementation inventory (not runtime acceptance)

- `HookGroupCatalog`: reviewed stable hook-ID mapping, no group master switch, immutable members, unique target packages, strict unmapped-ID refusal, group cross-app membership.
- `MainActivity`: Groups by default, persisted By app, search, expandable group and app rows, preserves search/IME focus, selects individual hook by stable ID (migrates legacy saved activity package once).
- Source guard and pure Java JUnit tests check catalog coverage, duplicate/unmapped IDs, same-package hook independence, search, immutability and UI-only preference boundaries.
- `UI04-P01..P04` remain pending hosted Android build/lint and device/LSPosed proof; no local Gradle was run.
- DS-01 merged PR #14, hosted Android run #45234343946 PASS, source head `ea3d13cfeee2cf3548f9c5ce54d5a02465ceefe1`, merge commit `7c963a49b0c83e71159e65d558e88ed20ae9c738`; device/TalkBack not performed.


### UI-05 source implementation inventory (independent device acceptance outstanding)

- `HookDetailRoute`: exact hook/group resolution, unique ID fail-closed validation and no first-hook fallback on same-package targets.
- `MainActivity`: grouped setup page with distinct per-target LSPosed scope requests/removal confirmations, independent per-hook setup, explicit compatibility/safety/version evidence, and deterministic return-to-group navigation across restoration.
- `FrameworkStateStore` remains the only remote preference writer, with commit/readback and framework snapshot refresh; unknown preference state hides the switch rather than implying OFF.
- The group is categorically not an authorization or enablement unit; existing `TweakCatalog` hook IDs and version/process guards are unchanged.
- `UI05-P01..P04`: source-bound regression suite and hosted JUnit pending native Android CI; LSPosed/device runtime and deep-link behavior remain pending.
- UI-04 PR #15 merged `c3e2968eb506b35ff566afd18d512e1a362a187e` with Android CI run #45242448541 successful for PR head `859c7be780a0d91fd4adb1dde2c955cebe08f55b`; device evidence not collected.


### UI-05-F01 — Hosted CI source-contract regression correction

- Original UI-05 source commit: `7dbce69623245d74f6708b85d8598f1c7fdcafaf` on PR #16.
- Required Android CI run #45248242365, job #137950625526: `CONTRACT_FAIL UI-04 detail navigation uses independent hook ID` during `scripts/check_contract.py`, before Android compilation.
- Root cause: UI-04's test asserted `entry.spec.id.equals(selectedHookId)` after UI-05 deliberately replaced that loop with the verified `HookDetailRoute.hook(TweakCatalog.all(), selectedHookId)` call. The production route remains in place; the obsolete internal assertion is replaced with exact-path and missing-ID rejection guards.
- Change scope: `scripts/check_contract.py` and this roadmap only. Existing hook IDs, framework readback, LSPosed scope, UI production code and Java routing tests are unchanged.
- Native Devtool apply/commit, hosted exact-head CI, protected merge and Android/LSPosed/device acceptance remain **PENDING** until independently verified. Original UI-05 source must **not** be reapplied.


### DX-01 source candidate — import-first diagnostics, exact source-bound

- Preconditions verified: DS-01 PR #14 merged `7c963a49b0c83e71159e65d558e88ed20ae9c738`; UI-04 PR #15 merged `c3e2968eb506b35ff566afd18d512e1a362a187e`; UI-05 PR #16 merged `e669c1d3949268623dbf341ea5c2efc2cd4bc66c`, final PR CI run #45252300059 success. Device evidence still pending.
- The `DX-01` new code stores only source-bound sanitised event codes/category/clock/line/hash, in process memory, bounded to 2 MiB per import, 10,000 lines per file and 4,000 retained events. Arbitrary raw imported lines are **not** retained, copied or exported. Imported log lines are never authenticated as actual hook execution, and unknown clocks stay unknown.
- Existing opt-in `VerboseLogs` is the source of recognized HookLab code events. Actual LSPosed module/framework and previously authorized logcat export files are imported only through Android Storage Access Framework. Privileged/live logcat collectors are intentionally **unavailable** until a separate explicit capability-gated implementation can be proven safe; no new permissions/root or hidden collectors.
- Diagnostic screen offers explicit import by source, source/severity/package/hook/query filters, bounded paging, per-event provenance inspector, copy-selected/filtered redacted JSONL, and SAF ZIP with `events.jsonl` and SHA-256 manifest. Selected imported evidence survives rotation in-process via `HookLabApp` but does not survive process death.
- **Acceptance truth:** DX01-P01..P06 source candidate; only local JVM privacy/model guards if verified, native and hosted CI pending, device/LSPosed export round trip and crash-recovery UX pending. Missing live collectors, bounded persistence and group/time UI mean this is **NOT SOURCE COMPLETE / NOT ACCEPTED**. No assertion of runtime log access or fully functional DX-01.

## UI-06 · Consolidated Home Dashboard source candidate (2026-11-23)

- **Base:** `main` at `703be7363ac2cc9dadfd92975e8164c67b37a907`, DX-01 PR #17 merge; PR-head required CI #45258951146 and post-merge main CI #45259364441 passed. Device validation pending.
- UI06-P01: unique hook/group/target-package and approved scope counts from `TweakCatalog`, `HookGroupCatalog` and `FrameworkSnapshot`. Duplicate group membership fails closed.
- UI06-P02: framework readiness never equates eligible configuration with executed hooks. Latest DX-01 imported event is labeled as reported imported evidence and retains source/correlation/clock; no imports = explicitly unknown.
- UI06-P03: working navigation to registered hook detail, Groups, Diagnostics and Scope; no write/toggle or invented Capture button.
- **Source handoff only:** UI-06 Android CI, physical LSPosed and device UI verification pending. DX-01 import-only model lacks authenticated live execution and process-death retention; do not conflate a successful merge with full DX-01 acceptance.
- **Other pending overlays:** DX-02 Guided Capture Sessions & Hook Discovery; DX-03 Hook Health, Troubleshooting & Evidence Explorer; UI-07 Settings, Scope, MCC Control, About & Help Redesign; UI-08 Accessibility, Regression, Performance & Legacy UI Retirement.

## DX-02 · Explicit Guided Capture source candidate (2026-11-23)

- **Base:** `main` at `090a97f9bc5e7bc092df2e69f2b8ea1cad47447c`; UI-06 PR #18 source `ae90895ad1cc8ccd9d5e9eaffbd3a35eef8d405a`, hosted required CI #45263005786 PASS; merge `090a97f9bc5e7bc092df2e69f2b8ea1cad47447c`, post-merge CI #45263756545 PASS.
- DX02-P01: one user-directed SAF-import-only session at a time, 10-minute cap, 64 fixed action markers, explicit stop/cancel and process-loss interruption marker (without replaying in-memory log events). No root/hidden access.
- DX02-P02: marker elapsed time and import ordinal, source-restricted event correlation and retention-loss note; imported evidence never claims actual execution.
- DX02-P03: reviewed hook spec and research-only package metadata; no class/method reflection, hook discovery injection or scope changes.
- DX02-P04: explicit Android SAF redacted ZIP report with events/markers JSONL hashes and provenance; requires hosted test and real device import/export verification.
- **Acceptance pending:** native PR/CI, Android install, reboot/process-death, permission denial, timeout, actual target reproduction test. DX-01 remains import-only; no live collectors are represented as installed.

## DX-03 · Source-linked hook health candidate (2026-11-23)

- **Base:** `main` 58ac1d335779526818355c9b0ebdf8fc7c943e9c; DX-02 PR #19 exact head `69e71198f8ce5398194f36c9e57616772b2631e8`, required CI #45269128242 PASS, post-merge CI #45269805687 pending at initial inspection.
- DX03-P01: independent read-only framework, scope, opt-in, safety-lock, and installed-version axes; eligibility explicitly means configuration, not hook execution.
- DX03-P02: exact stable hook-ID imported event attribution, source SHA-256 and line, individual errors versus unattributed errors, missing collector and retention-loss warnings; no fabricated live success.
- DX03-P03: functional read-only navigation to hook setup, filtered Diagnostics, and existing SAF guided capture; redacted JSON evidence clipboard, no side effects or extra permissions.
- DX03-P04: no ACTIVE or proof of runtime execution from imported logs, even on eligible configuration. Source and Java negative tests require hosted Android CI and physical-device verification.
- **Pending:** native Devtool delivery, required CI, Android lifecycle/rotation and real device/runtime acceptance. DX-01 and DX-02 remain intentionally import-only; privileged live collectors are not claimed.

## UI-07 · Native Settings, Scope, MCC, About & Help candidate (2026-11-23)

- **Base:** `main` 5d091ad9985dac6f81d9cb60b29f94880508712b; DX-03 PR #20 exact head `90dc58108a8f113d7150e0923806ece7a21161a1`, required CI #45273304125 PASS, merge `5d091ad9985dac6f81d9cb60b29f94880508712b`, post-merge CI #45273688643 PASS.
- UI07-P01: deduped, framework-snapshot-owned scope rows with verified unknown/pending states; per-target request and confirmed removal uses existing `FrameworkStateStore`; unrelated approved packages counted but never modified.
- UI07-P02: native MCC panel preserves manual inspect and explicitly confirmed root write. Process-local single-flight worker survives Activity rotation, avoids duplicate writes and never runs in `onCreate`; result codes sanitized, no new root path.
- UI07-P03: Settings retains theme and independently framework-backed verbose logging, adds real About/Help flows with installed package version, optional APK signer certificate SHA-256, API102 compatibility and explicit imported-log/clipboard/privacy limitations.
- UI07-P04: native back navigation, saved Help route and accessible live MCC status; no local Gradle; JUnit tests for source authority, rotation/single-flight and identity.
- **Acceptance pending:** native Devtool, exact-head hosted Android compilation/CI, physical device rotation, denied root/scope, accessibility/TalkBack and release-signing qualification. DX-01/02 have SAF import-only source gaps and no authenticated live hook execution.

## UI-07-F01 · Hosted CI Maven Central rate-limit recovery (2026-11-23)

- **Exact candidate:** PR #21 head `fec611844865534aa255bdd36ce31749a997ad68`, tree `f08d31d55e0ddfcbaab7d455ce5e388f6fd402f7`, base `main` `5d091ad9985dac6f81d9cb60b29f94880508712b`.
- **Failure receipt:** Actions run `45278453969`, attempt 1, Android build job `138056285410`; source-contract PASS, Gradle `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` failed before project compilation while fetching AGP transitive dependencies from Maven Central (`HTTP 429 Too Many Requests`, 48 similar failures). APK stage skipped. Not an observed Java compile/test failure.
- **Remediation source candidate:** `actions/setup-java@v6` Gradle dependency cache and bounded `scripts/ci_gradle_retry.sh` (max 3 attempts, 20s/60s backoff only when logs contain both dependency resolution failure and HTTP 429). Preserve `PIPESTATUS`, streamed stdout/stderr, original failure codes, and never retry compilation, test, lint, or unrelated authentication failures.
- **No source semantics changed:** Android Java, LSPosed preferences, MCC root worker, signing, and repository origin untouched. No mirror substitution, no local Gradle, no force push, and no reapplication of UI-07.
- **Acceptance:** F01 local shell negative/positive regression required; native Devtool exact-tree transaction, existing PR #21 update, fresh exact-head hosted Android CI, verified PR merge and device/runtime acceptance PENDING. Even successful CI does not qualify a physical device.
