> Historical proposal preserved verbatim. Current authoritative status and MOD-00 scope: `hooklab-modular.json` and `hooklab-mod00.md`.

# HookLab — Consolidated modular roadmap

**Program:** HOOKLAB-MODULAR-PRIVATE · **Revision:** 1.0-proposed · **Snapshot:** 2026-11-25 (America/Sao_Paulo)

**Status:** Planning document; not an adopted roadmap, source patch, operator, device acceptance or release authorization.

## Progress dashboard

Coverage: complete for the **proposed 11-overlay combined UX revamp + modular tranche only** (6 existing UX + 5 new MOD); wider project inconclusive. This is a proposed denominator change 6 → 11, not adopted. Source-complete as reported in branch roadmap: **3/11 (27.3%)** `[███░░░░░░░░░]`; independent full device-inclusive acceptance established in this session: **0/11** `[░░░░░░░░░░░░]`. Previously hosted-qualified per branch roadmap: 3/11; independent historical artifacts not re-audited. Merged 3/11. Remaining implementation or completion evidence 8; remaining full acceptance 11. Strict independently audited source-complete count is inconclusive (no exhaustive source audit in this planning session). MOD-only: 0/5 implemented, 0/5 accepted. New promises: 50.

| ID | Complete overlay name | Implementation / native state | Acceptance / dependency |
|---|---|---|---|
| UX-R00 | Design Contract & Emulator Infrastructure | Reported source-complete; merged #30 | Historical hosted foundation PASS; device pending |
| UX-R01 | Diagnostics Consolidation | Reported source-complete; merged #31 | Historical hosted-qualified; device pending |
| UX-R02 | Navigation & Interaction Redesign | Reported source-complete; merged #32 | Historical hosted-qualified; device pending |
| UX-R03 | Home & Whole-App Visual Fidelity | In progress; draft #33 | Current exact successor acceptance pending; R01/R02 |
| UX-R04 | Accessibility, Adaptive Layout & Lifecycle | In progress; draft #33, F04 published; F05 host state unverified | Extended matrix FAIL; R03 |
| MOD-01 | Modular Core & Internal SDK | PLANNED; NOT RUN | PENDING; UX-R03/R04 qualified and merged; design/roadmap adoption |
| MOD-02 | Hook Pack Runtime & Migration | PLANNED; NOT RUN | PENDING; MOD-01 source/hosted gates and applicable physical proof |
| MOD-03 | Native Module UI & Configuration | PLANNED; NOT RUN | PENDING; MOD-02 source/hosted gates and applicable physical proof |
| MOD-04 | GitHub Distribution & Updates | PLANNED; NOT RUN | PENDING; MOD-03 source/hosted gates and applicable physical proof |
| MOD-05 | Developer Tools & Full Qualification | PLANNED; NOT RUN | PENDING; MOD-04 source/hosted gates and applicable physical proof |
| UX-R05 | Full Native Visual Acceptance & Regression Enforcement | PLANNED | PENDING; R00–R04 and MOD-01–05, approved references |

**Current focus:** UX-R04 current repair on PR #33; exact current failing head recorded below. **Next eligible new work:** MOD-01 after R03/R04 repair, qualification and merge and roadmap adoption. Do not start parallel stale-source overlays. R05 remains final; do not waive existing tests or golden requirements. Shared governance: branch protection and APK/module signing authorization remain separately required at merge/sign/release. Device evidence required for dynamic loading and bridge; cannot be inferred from CI.

## New overlay implementation contracts

### MOD-01 — Modular Core & Internal SDK

**Promises:** 9 · 0 implemented / 0 accepted. Production integration paths below are planned; see checklist for evidence requirements.

- `MOD-01-P01` **Stable module and hook identity:** Catalog and SDK → manifest-backed module/hook descriptors; preserve all existing hook IDs and persisted opt-ins.
- `MOD-01-P02` **Shared registry authority:** Manager and host use the same immutable generation; metadata available without executing module code; replace hardcoded dispatch eligibility.
- `MOD-01-P03` **Versioned internal SDK:** Separate Java SDK and runtime Gradle modules; define binary compatibility, entrypoint, hook context, settings, diagnostics and lifecycle.
- `MOD-01-P04` **Preserve built-in behavior:** Adapters for existing eligible integrations; independent hook activation, scope, safety locks and host version guards remain authoritative.
- `MOD-01-P05` **Process eligibility:** Filter package/process/version and opt-in before loading any executable payload; default main-process-only.
- `MOD-01-P06` **Manager/runtime boundary:** Manager owns writes and network; hosts consume immutable read-only metadata/settings and verified runtime content.
- `MOD-01-P07` **Contract and Go foundations:** Versioned manifest/settings/index/receipt schemas, shared valid/invalid vectors; Go CLI skeleton and Java readers agree.
- `MOD-01-P08` **Real device feasibility gate:** Tiny self-authored benign test module via supported API 102 remote file transport; inspect actual installed Vector/LSPosed version and service capabilities.
- `MOD-01-P09` **SDK/lifecycle governance:** One cumulative devop-hooklab registry, exact source evidence, no duplicate old paths except proven external compatibility.

**Exit:** every listed promise source-complete and independently locally verified; applicable native, hosted, CI24, physical and release gates separately recorded. No partial operator delivery. Roll back by reviewed native revert; preserve IDs, registry history and prior verified versions as applicable.

### MOD-02 — Hook Pack Runtime & Migration

**Promises:** 10 · 0 implemented / 0 accepted. Production integration paths below are planned; see checklist for evidence requirements.

- `MOD-02-P01` **Deterministic signed pack format:** Go packager → canonical manifest, exact payload SHA-256 map, signature envelope; Java independent verifier.
- `MOD-02-P02` **Safe local import:** SAF and Share → bounded staged archive validation; no extraction escape, executable from Downloads, symlink or duplicate entries.
- `MOD-02-P03` **Trusted signing identity:** One personal module publisher trust root, separate from existing APK signer; test key isolated, rotation/revocation explicit.
- `MOD-02-P04` **Atomic install/update registry:** Private staging → verified immutable payload → generation activation; journal and crash recovery, retained prior version.
- `MOD-02-P05` **Host transport and loader:** API 102 verified remote files → bounded verified in-memory DEX or proven read-only alternative; SDK parent identity and target classloader kept distinct.
- `MOD-02-P06` **Controlled hook activation:** Default-off imported hooks; explicit scope and per-hook settings; no activation while registry or signature state unknown.
- `MOD-02-P07` **Failure isolation and recovery:** Track install handles, rollback partial registration when supported, quarantine on known exceptions, safe mode and retained known-good generation.
- `MOD-02-P08` **Removal and restart semantics:** Disable/remove registry generation, revoke future eligibility, preserve settings optionally; explicitly report restart needed.
- `MOD-02-P09` **Migrate current eligible hooks:** Package ObtainX, Word Master and eligible ChatGPT hooks without changing existing behavior; MCC manager-sensitive workflow stays built in until separately verified.
- `MOD-02-P10` **Package validation tooling:** Go hooklabctl pack/verify/inspect uses independent validation; Gradle/D8 build in hosted CI only.

**Exit:** every listed promise source-complete and independently locally verified; applicable native, hosted, CI24, physical and release gates separately recorded. No partial operator delivery. Roll back by reviewed native revert; preserve IDs, registry history and prior verified versions as applicable.

### MOD-03 — Native Module UI & Configuration

**Promises:** 11 · 0 implemented / 0 accepted. Production integration paths below are planned; see checklist for evidence requirements.

- `MOD-03-P01` **Integrated Hooks navigation:** Keep Home/Hooks/Diagnostics; Hooks offers Installed, By app and repository route when functional. Stable module/hook routes and app icons.
- `MOD-03-P02` **Module detail shell:** Overview/Configure/Logs/Versions; title, signer/provenance, compatibility, status, install/update/remove and independent hooks.
- `MOD-03-P03` **Native custom configuration provider:** Trusted Java/Kotlin provider builds native views in manager-owned lifecycle with shared theme, nav, controls and dialog APIs.
- `MOD-03-P04` **Credential isolation from UI plugins:** Separate credential boundary with distinct UID if isolation is required; otherwise restrict executable UI to fully trusted self-authored signed code and disclose same-UID access risk.
- `MOD-03-P05` **Reusable typed controls:** SDK setting rows, switches, choices, sliders, validation, conditional visibility, previews, optional small declarative schema.
- `MOD-03-P06` **Namespaced config and profiles:** Typed module/hook namespaces, per-app overrides and named profiles; framework remote prefs read-only in hosts.
- `MOD-03-P07` **Apply semantics:** Declare live vs restart-host behavior; live only with proven propagation and safe hook semantics.
- `MOD-03-P08` **Config import/export and reset:** Explicit review, versioned schema, defaults/undo, sanitized export; no credentials, privileged data or automatic execution.
- `MOD-03-P09` **Diagnostic module identity:** Module ID/version/hash/registry generation and existing hook ID on structured events; link exact evidence, no fabricated running status.
- `MOD-03-P10` **Custom UI resource contract:** Explicit pack assets and translations through SDK, bounded reads, resource namespace; no arbitrary Android resource loading assumption.
- `MOD-03-P11` **Design contract amendment:** Add native module screens and approved visual cases without changing old golden thresholds; keep shared FlatUi tokens and adaptive layout.

**Exit:** every listed promise source-complete and independently locally verified; applicable native, hosted, CI24, physical and release gates separately recorded. No partial operator delivery. Roll back by reviewed native revert; preserve IDs, registry history and prior verified versions as applicable.

### MOD-04 — GitHub Distribution & Updates

**Promises:** 10 · 0 implemented / 0 accepted. Production integration paths below are planned; see checklist for evidence requirements.

- `MOD-04-P01` **Monorepo module releases:** Same repository, distinct module tags/assets from APK releases, per-module change detection, SDK changes rebuild dependents.
- `MOD-04-P02` **Signed repository index:** Go generator validates module metadata; signed monotonic revision, artifact IDs/hashes, compatibility and source/build provenance.
- `MOD-04-P03` **Optional GitHub PAT:** Fine-grained read-only repository token, Contents read and Actions read only if artifact access needed; manager/credential broker only.
- `MOD-04-P04` **Repository browsing:** Official same-repo default, optional explicit personal repo, stable/testing filters, search/details/changelog, cached metadata.
- `MOD-04-P05` **Qualified workflow artifacts:** Exact repo/workflow/run/attempt/head/artifact ID, digest and qualification receipt; testing lane separate from release.
- `MOD-04-P06` **Compatibility selection:** Select newest compatible version using exact package/version guard, SDK range and optional signer/fingerprint; no forced override on normal path.
- `MOD-04-P07` **Transactional updates:** Reuse MOD-02 staging/verification/activation; pin versions, rollback, show pending restart; update all with review.
- `MOD-04-P08` **Network privacy and limits:** HTTPS allowlisted origin, redirect credential stripping, bounded timeout/download, conditional requests and offline cache.
- `MOD-04-P09` **Independent release workflow:** Hosted tests, independent validator, approval-bound signing, signed pack/index publication with coherent metadata; no app APK rebuild for routine packs.
- `MOD-04-P10` **Update notifications:** Manual refresh and optional manager-only checks; no background host traffic, no automatic executable install.

**Exit:** every listed promise source-complete and independently locally verified; applicable native, hosted, CI24, physical and release gates separately recorded. No partial operator delivery. Roll back by reviewed native revert; preserve IDs, registry history and prior verified versions as applicable.

### MOD-05 — Developer Tools & Full Qualification

**Promises:** 10 · 0 implemented / 0 accepted. Production integration paths below are planned; see checklist for evidence requirements.

- `MOD-05-P01` **Unified Go hooklabctl:** One project tool for init/build-plan/pack/verify/sign/index/inspect/doctor/evidence; local Go builds permitted; Android build hosted only.
- `MOD-05-P02` **Authenticated Termux management:** Narrow explicit broker for list/info/import/update/disable/rollback/logs/status; no arbitrary exported command or shell execution.
- `MOD-05-P03` **Developer tools workspace:** Diagnostics → package/module inspector, metadata, validator, initialization timing and evidence export; no credentials/private app payload capture.
- `MOD-05-P04` **Internal SDK template:** Independent module scaffold, custom settings example, SDK contracts and tests, source-bound hosted artifact build.
- `MOD-05-P05` **Development lane:** Local and GitHub testing packs signed by explicit test trust identity, visible build provenance; opt-in dev mode; no stable guard bypass.
- `MOD-05-P06` **Operational recovery:** Global safe mode, independent recovery instructions, durable install history and per-module rollback; preserve native evidence.
- `MOD-05-P07` **Performance and resource limits:** Lazy load only eligible runtime DEX, no Go/JNI in hosts, bound archive/cache/logs/initialization overhead.
- `MOD-05-P08` **Security and migration audit:** Full end-to-end source review, credential boundary, trust, signing, config migration, no retired hooks, scope/consent and stable APK signing preserved.
- `MOD-05-P09` **Cross-platform and native qualification:** Native lifecycle via sole devop-hooklab; exact hosted Android/native/extended/CI24 plus Go gates and actual LSPosed/Termux proof.
- `MOD-05-P10` **Final integration handoff:** Record exact modules/SDK/signers/version/hash, acceptance, outstanding historical UX/DX debt, rollback and release boundary.

**Exit:** every listed promise source-complete and independently locally verified; applicable native, hosted, CI24, physical and release gates separately recorded. No partial operator delivery. Roll back by reviewed native revert; preserve IDs, registry history and prior verified versions as applicable.

## Historical obligations (not superseded)

All nine original overlay IDs remain tracked; nine merged, zero full device acceptance established here. DX source gaps remain open. Their full 38 promise definitions stay in the pinned flat UX roadmap; no renumbering.

| ID | Name | Unresolved gate |
|---|---|---|
| DS-01 | Flat Design System & Adaptive Native Shell | Device theme/visual/accessibility |
| UI-04 | Hook Groups & Group/App Catalog | Stable identity/navigation/restore |
| UI-05 | Group/Hook Detail & Independent LSPosed Setup | Real framework and negative paths |
| UI-06 | Home Dashboard, Readiness & Recent Activity | Device readiness/evidence |
| DX-01 | Unified Diagnostics Timeline, Log Adapters & Export | Live capability-gated adapters, persistence, filtering and device |
| DX-02 | Guided Capture Sessions & Hook Discovery | Capability/metadata, persistence, real reproduction |
| DX-03 | Hook Health, Troubleshooting & Evidence Explorer | Real collector and runtime evidence |
| UI-07 | Settings, Scope, MCC Control, About & Help Redesign | Scope/root denial, signing and accessibility |
| UI-08 | Accessibility, Regression, Performance & Legacy UI Retirement | Full physical/regression qualification |

Revamp original promise inventory: R00 11, R01 12, R02 15, R03 10, R04 10, R05 11 (69 total). Preserve exact definitions and all F-series repairs from original roadmap. The new MOD program adds promises, does not replace them. Other known cross-project obligations (M6, M7, M8, UI-01/02/03, WM01, CGT01-P01–P05, TB01, HL-DIAG series, proposed Tinder program) are outside this denominator; latest authority and acceptance must be reconciled separately if implicated. No project-wide percentage asserted.

## Source snapshot and authority

- Repository: [https://github.com/Mikeyphw/MikeysHookLab](https://github.com/Mikeyphw/MikeysHookLab); default branch `main`, SHA `a76361eacad31ec072c3ef13563ea26be5b380a2`, Git tree `26fa5c749deb9b0a1d7168d946030a7da6688b62`.
- Active [draft PR #33](https://github.com/Mikeyphw/MikeysHookLab/pull/33): head `ccb228f7faaaf94dc55c12c52a34c87b398be4f5`, base `a76361eacad31ec072c3ef13563ea26be5b380a2`. Branch roadmap blob `4c26ce8d54dc34580be1c038d660fea7c53790a0`; historical flat UX blob `d7424293500431917e0732db7e975a881be18cbd`. Main revamp blob observed earlier `47e5479b7dd845a109dc4a0bc271a5b165bf6caa`.
- [Current revamp roadmap at PR head](https://github.com/Mikeyphw/MikeysHookLab/blob/ccb228f7faaaf94dc55c12c52a34c87b398be4f5/docs/roadmaps/hooklab-ux-revamp.md) and [historical flat UX roadmap](https://github.com/Mikeyphw/MikeysHookLab/blob/ccb228f7faaaf94dc55c12c52a34c87b398be4f5/docs/roadmaps/hooklab-flat-ux.md) remain authoritative for existing scope. This proposal supplements them only after adoption. Re-fetch before each overlay.
- PR #30/R00, #31/R01, #32/R02 merged. Branch roadmap reports R00–R02 source-complete/hosted-qualified (historical claims, not independently re-audited here). PR #33 exact-head check snapshot: Android build/unit/lint, native visual candidate, frozen design, shared APK build and CI24 shard summary report success; extended aggregate fails; api36-840, api36-390, api36-landscape, api36-tablet-landscape fail. Other six listed adaptive cases pass. These are check-run conclusions, not independent receipt/artifact qualification. F05 appears in prior host context but no published successor was observed; reconcile real native transaction before resuming.
- Prior nine-overlay tranche merged; device-inclusive acceptance not established. Broader HookLab project coverage inconclusive; do not count unrelated programs in this denominator.
- Contract `/Agent Contracts/ai_agent_operating_contract_cg.md`, AGENT-OPERATIONS-DEVTOOL v1.25, Library item `file_00000000b5b0820e82a6bff5cb1ba2b5`, Library version 25, SHA-256 `850a858e863bb2715da7f9e09bd26ede3d7f81231f7da05db3b381b6cfb7df76`; raw file hash matches V125 receipt. Operator v1.8, delivery v1.15, roadmap v1.1 hashes also matched. Full byte materialization and relevant sections inspected; exhaustive line-by-line contract/validator/Devtool ledger review not completed in this planning session. Mandatory before implementation.
