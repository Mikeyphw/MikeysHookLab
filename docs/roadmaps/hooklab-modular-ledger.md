> Historical proposal preserved verbatim. Current authoritative status and MOD-00 scope: `hooklab-modular.json` and `hooklab-mod00.md`.

# HookLab — Decision, risk, gap and evidence ledger

**Program:** HOOKLAB-MODULAR-PRIVATE · **Revision:** 1.0-proposed · **Snapshot:** 2026-11-25 (America/Sao_Paulo)

**Status:** Planning document; not an adopted roadmap, source patch, operator, device acceptance or release authorization.

Append-only project ledger. This is not the canonical Devtool bug ledger and does not report hypothetical risks as confirmed bugs. Each entry includes owner/next gate; close only with source-bound actual evidence. No implementation, transaction, operator, signing or device actions were performed for this planning task.

## Decisions

| ID | Decision | Status / rationale | Owner / gate |
|---|---|---|---|
| DEC-01 | Single-user design; no public marketplace | User requirement; simplify trust and dependencies | MOD-01 |
| DEC-02 | Same GitHub repo by default, independent module artifacts | Proposed; repository visibility unchanged | MOD-04 |
| DEC-03 | Native Java SDK/runtime/UI, optional Kotlin; Go external CLI | Proposed; no Go in injected host | MOD-01/05 |
| DEC-04 | Existing three-tab design and shared UI components | Proposed; no competing UI | MOD-03, UX-R05 |
| DEC-05 | Custom native module pages plus reusable typed controls | User-requested capability; schema optional | MOD-03 |
| DEC-06 | Optional read-only GitHub PAT; secret isolation mandatory for claimed isolation | User-requested feature; distinct-UID broker preferred or explicit trust-only decision | MOD-03/04 |
| DEC-07 | Signed packs/index, one personal trust root, test lane | Proposed; independent APK signer | MOD-02/04 |
| DEC-08 | Keep stable hook IDs and explicit per-hook scope/opt-in | Existing safety contract; no retired hooks | MOD-01/02 |
| DEC-09 | Complete R03/R04 first, five MOD overlays, R05 last | Proposed roadmap amendment, not yet adopted | Owner/native reconciliation |
| DEC-10 | No arbitrary hot unload; restart host after generation change | Proposed safe baseline | MOD-02 |
| DEC-11 | MCC privileged manager workflow initially built in | Design decision, safe migration separately scoped | MOD-02 |
| DEC-12 | One cumulative Devtool operator, Go CLI not a delivery replacement | Existing workflow retained | Every overlay |

## Observed gaps and existing failures

| ID | Observation / exact source | State | Next action |
|---|---|---|---|
| HL-MOD-G01 | `TweakCatalog` hardcoded entries and `HookLabModule` static catalog eligibility/dispatch on main snapshot | Source observation; no dynamic registry established | MOD-01 replace real callers and qualify identity |
| HL-MOD-G02 | Draft PR #33 head `ccb228f7faaaf94dc55c12c52a34c87b398be4f5`: extended matrix aggregate and four adaptive cases failed | Observed check failure, not root-cause diagnosis | Existing UX-R04 repair, inspect logs/receipts, no MOD start |
| HL-MOD-G03 | UX-R04-F05 mentioned in recent host context, published head still F04 | Native state inconclusive | Reconcile exact transaction/session/PR before resuming |
| HL-MOD-G04 | R00–R02 historical hosted qualification reported, independent full device acceptance not established | Evidence gap | Preserve source-bound receipts; UX-R05/device gates |
| HL-MOD-G05 | DX-01/02/03 historical collector/persistence/runtime gaps in authoritative roadmap | Existing open debt, not retired | Separate eligible source/device repair, reconcile before final acceptance |
| HL-MOD-G06 | Actual Vector remote file transport and in-host imported DEX execution unproven | Feasibility blocker for claiming plug-and-play | MOD-01 physical experiment before MOD-02 |
| HL-MOD-G07 | Whole-app 24 visual references do not cover new module pages | Design gap | MOD-03 reviewed amendment; UX-R05 acceptance |
| HL-MOD-G08 | Complete current governance/Devtool ledger and validator review not performed in planning session | Pre-implementation obligation | Re-read entire latest sources, verify hashes, source-bound validator execution |

## Risk register

| ID | Risk / trigger | Mitigation and closure evidence | Gate |
|---|---|---|---|
| RISK-01 | Remote file API availability differs from installed framework behavior | Physical scoped benign pack probe, exact framework version, deny/unavailable paths | MOD-01 |
| RISK-02 | Dynamic DEX classloader collision or Android read-only restriction | Verified bounded in-memory bytes/proven read-only path, SDK identity and host classloader test | MOD-02 |
| RISK-03 | Same-UID custom UI can access PAT or Keystore | Distinct-UID broker or explicit trust-only acceptance; no isolation claims otherwise; keep PAT gated | MOD-03/04 |
| RISK-04 | Malicious/modified pack or compromised signing key | Signed byte manifest, trusted keys, revocation/rotation, negative corpus and release protection | MOD-02/04 |
| RISK-05 | Partial registry/config update or process crash | Immutable generations, durable journal, kill/fault tests, previous verified generation | MOD-02 |
| RISK-06 | Hook crashes target or leaves partially installed interception | Guarded installs, handles where supported, explicit safe mode/restart, no promise of universal crash isolation | MOD-02/05 |
| RISK-07 | Scope/config mismatch between manager and host | Single generation, explicit scope, readback, unknown fails closed, no host writes | MOD-01/03 |
| RISK-08 | App update breaks obfuscated methods | Exact package/version and optional fingerprint; latest compatible, fail closed, pin and rollback | MOD-04 |
| RISK-09 | GitHub token leak in logs/redirects/backup | Keystore, broker, redacted logs, no auth redirect, test expired/revoked credentials | MOD-04 |
| RISK-10 | Stale/replayed index or mismatched artifact run | Signed monotonic index, exact run/attempt/digest, offline stale labels, explicit rollback | MOD-04 |
| RISK-11 | ABI/JNI overhead or dependency collisions | No Go in APK initially, bounded lazy load and SDK-only dependency model | MOD-01/05 |
| RISK-12 | Termux command interface abused by another app | Narrow authenticated requests, explicit consent, replay protection, no arbitrary shell | MOD-05 |
| RISK-13 | Native visual or accessibility regression | Retain current gates, approved new references, actual instrumentation/device | MOD-03/UX-R05 |
| RISK-14 | Retired or privileged hook behavior accidentally reactivated | Stable ID migration audit, default off, exact explicit approval, preserve MCC core boundary | MOD-02/05 |
| RISK-15 | Failed/stale Devtool session or duplicated PR mutation | Exact preflight/session reconcile, forward-only repair, native receipts, canonical ledger routing | Every overlay |
| RISK-16 | Repository is public although application is personal | Never publish secrets/private data; explicitly authorize any visibility change | MOD-04 |

## Evidence ledger

| ID | Evidence | Verdict / limit |
|---|---|---|
| E-001 | GitHub main SHA/tree and PR #33 branch identities in source snapshot | Observed via read-only GitHub connector |
| E-002 | PR #30/#31/#32 merged | Observed; not device acceptance |
| E-003 | 40 exact-head check runs for PR #33; extended aggregate failure, four named adaptive failures | Observed conclusions; no independent artifact/attempt requalification |
| E-004 | Operating contract raw SHA matches V125 validation receipt | Hash verified; full semantic review outstanding |
| E-005 | libxposed and Android/GitHub official API docs | Design feasibility only; no actual device test |
| E-006 | New planning files and their SHA-256 manifest | Document consistency/integrity only; no overlay qualification |

## Change and acceptance ledger

All 50 new promises remain `UNINSPECTED / NOT_RUN / PENDING` (checklist is the authoritative proposed per-promise roster). No fix commit, native transaction, session, module pack, release, device result or operator artifact exists from this task. Existing PR #33 remains unchanged. No new Devtool defect was diagnosed in this session; canonical Devtool ledgers were not modified. Any newly reproduced Devtool-specific defect must be captured in the appropriate canonical Library ledger and reverified with exact failing/fix source and regression proof. Project risk entries above do not substitute for that obligation.

**Next entry:** after UX-R04 repair qualification, record exact current PR head, native transaction/session, hosted run/attempt/artifact evidence, acceptance status, merged main/tree, and reviewed roadmap adoption. Then begin MOD-01, not MOD-02.

## Source snapshot and authority

- Repository: [https://github.com/Mikeyphw/MikeysHookLab](https://github.com/Mikeyphw/MikeysHookLab); default branch `main`, SHA `a76361eacad31ec072c3ef13563ea26be5b380a2`, Git tree `26fa5c749deb9b0a1d7168d946030a7da6688b62`.
- Active [draft PR #33](https://github.com/Mikeyphw/MikeysHookLab/pull/33): head `ccb228f7faaaf94dc55c12c52a34c87b398be4f5`, base `a76361eacad31ec072c3ef13563ea26be5b380a2`. Branch roadmap blob `4c26ce8d54dc34580be1c038d660fea7c53790a0`; historical flat UX blob `d7424293500431917e0732db7e975a881be18cbd`. Main revamp blob observed earlier `47e5479b7dd845a109dc4a0bc271a5b165bf6caa`.
- [Current revamp roadmap at PR head](https://github.com/Mikeyphw/MikeysHookLab/blob/ccb228f7faaaf94dc55c12c52a34c87b398be4f5/docs/roadmaps/hooklab-ux-revamp.md) and [historical flat UX roadmap](https://github.com/Mikeyphw/MikeysHookLab/blob/ccb228f7faaaf94dc55c12c52a34c87b398be4f5/docs/roadmaps/hooklab-flat-ux.md) remain authoritative for existing scope. This proposal supplements them only after adoption. Re-fetch before each overlay.
- PR #30/R00, #31/R01, #32/R02 merged. Branch roadmap reports R00–R02 source-complete/hosted-qualified (historical claims, not independently re-audited here). PR #33 exact-head check snapshot: Android build/unit/lint, native visual candidate, frozen design, shared APK build and CI24 shard summary report success; extended aggregate fails; api36-840, api36-390, api36-landscape, api36-tablet-landscape fail. Other six listed adaptive cases pass. These are check-run conclusions, not independent receipt/artifact qualification. F05 appears in prior host context but no published successor was observed; reconcile real native transaction before resuming.
- Prior nine-overlay tranche merged; device-inclusive acceptance not established. Broader HookLab project coverage inconclusive; do not count unrelated programs in this denominator.
- Contract `/Agent Contracts/ai_agent_operating_contract_cg.md`, AGENT-OPERATIONS-DEVTOOL v1.25, Library item `file_00000000b5b0820e82a6bff5cb1ba2b5`, Library version 25, SHA-256 `850a858e863bb2715da7f9e09bd26ede3d7f81231f7da05db3b381b6cfb7df76`; raw file hash matches V125 receipt. Operator v1.8, delivery v1.15, roadmap v1.1 hashes also matched. Full byte materialization and relevant sections inspected; exhaustive line-by-line contract/validator/Devtool ledger review not completed in this planning session. Mandatory before implementation.
