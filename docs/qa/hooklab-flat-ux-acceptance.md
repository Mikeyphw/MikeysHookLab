# HookLab Flat UX — UI-08 acceptance matrix (source-bound, NOT device-accepted)

**Roadmap ID:** `HOOKLAB-FLAT-UX` · **Overlay:** UI-08 · **Base:** `90f749b84fac6a9adbb61a9d1a740e35f2b10391` (UI-07 PR #21 merged).  
**Source qualification:** local exact-source audits and pure Java tests only. **DEVICE_NOT_RUN** · **LSPOSED_RUNTIME_NOT_VERIFIED** · **RELEASE_NOT_AUTHORIZED**.  
**Android signing:** no release built, installed or compared. Debug APK from CI is not a signed release acceptance receipt.

## 38 independent promises and outstanding gates

| Overlay | Promise IDs | Source implementation | Actual acceptance still required |
|---|---|---|---|
| DS-01 | DS01-P01, DS01-P02, DS01-P03, DS01-P04 | Merged, hosted CI passed | Light/dark/AMOLED, 320/360/600/840+dp, TalkBack and screenshot comparisons |
| UI-04 | UI04-P01, UI04-P02, UI04-P03, UI04-P04 | Merged, hosted CI passed | Rotation, search focus, same-package multi-hook, no scope writes on view changes |
| UI-05 | UI05-P01, UI05-P02, UI05-P03, UI05-P04 | Merged, hosted CI passed | LSPosed API102 device; denied write; stale callback; ambiguous deep link |
| UI-06 | UI06-P01, UI06-P02, UI06-P03 | Merged, hosted CI passed | Device Home readiness and imported evidence source truth |
| DX-01 | DX01-P01, DX01-P02, DX01-P03, DX01-P04, DX01-P05, DX01-P06 | Import-only source in main; privileged/live collectors and process-death source gaps remain | LSPosed export, file truncation, logcat denied, privacy/redaction and ZIP roundtrip |
| DX-02 | DX02-P01, DX02-P02, DX02-P03, DX02-P04 | SAF import-only source in main | Stop/cancel, process death, capture timeout, UI import/export and real target reproduction |
| DX-03 | DX03-P01, DX03-P02, DX03-P03, DX03-P04 | Read-only evidence-aware source in main | Error source attribution, no event, collector loss and real device navigation |
| UI-07 | UI07-P01, UI07-P02, UI07-P03, UI07-P04 | Merged UI07 including Maven-429 CI recovery | MCC root denial and rotation, scope remove cancel, About signer and Help TalkBack |
| UI-08 | UI08-P01, UI08-P02, UI08-P03, UI08-P04, UI08-P05 | Source candidate: dead legacy cards retired, ambiguous migration rejected, headings/touch/contrast gates, auditable documentation | Full final-device walkthrough, perf/memory, permission denial, signing if authorized |

## Required manual acceptance matrix — all gates begin unqualified

| Gate | Exact scenario | Accepted only if |
|---|---|---|
| UI08-P01 visuals | Android 16 device and tablet; light/dark/AMOLED/dynamic, 320/360/600/840dp widths | No legacy route, clipping, unusable action or divergent visual token |
| UI08-P02 identity/migration | Saved package-only state with 2 registered hooks and an unknown hook ID | No hook randomly selected; per-hook preferences still isolated, no scope writes from views |
| UI08-P03 accessibility | TalkBack linear traversal Home→Hooks→Detail→Diagnostics→Settings→Scope→Help/MCC; 200% font size; keyboard focus | Labelled actions, correct headings/focus order, >=48dp touch, no clipped text, color contrast passes real themes |
| UI08-P03 performance | 4000 retained imported events, 10-minute capture; scroll/rotate/restart; low-memory background kill | No ANR, session interruption described truthfully, no secret/data leak, measured bounded allocation/latency |
| UI08-P04 signer | ONLY AFTER EXPLICIT RELEASE AUTHORIZATION: signed release APK AND installed same APK, stable pinned certificate, source SHA provenance | Independent package/version and signer fingerprint match, CI and installed device receipts verified; NEVER inferred from debug CI |
| UI08-P05 docs/privacy | Review actual app/help strings, exported JSONL/ZIP, log imports, source-aware health | No claim of authenticated hook execution, passive log harvesting, production release, or device validation |

## Read-only local regression

`python3 -B scripts/check_ui08_qa.py` validates exact native paths, migration, color contrast, no new permissions and the 38-promise QA matrix. Hosted CI must run source contract, QA source checks, assembleDebug, testDebugUnitTest, lintDebug, APK provenance. Local Gradle is prohibited. New UI-08 source must not claim a device/release PASS.

## Delivery and release gates

Source-bound native Devtool check/apply/commit/session, PR exact-head hosted CI and guarded merge are independent of Android real-device QA. **Do not mark UI-08 fully accepted** until every relevant 38-promise device gate above has actual evidence and remaining DX-01/DX-02 source gaps are reconciled. No root, keystore, signing or device action is performed by this document.
