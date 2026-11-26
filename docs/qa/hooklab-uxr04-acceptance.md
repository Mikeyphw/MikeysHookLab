# UX-R03-F01 + UX-R04 — candidate acceptance map

Source preimage: PR #33 `60ed46784cf7bcee6430fe70e28126e9ab06e1c3`, tree `b6a33ba8ecf27823a4a1b0d17784f5890519b632`. Main is `a76361eacad31ec072c3ef13563ea26be5b380a2`. Forward-only successor; preserve the previous transaction and receipt. The previous hosted Design pass is historical, Android and Native UI failed at Gradle configuration, and CI24 was not observed. All successor checks must bind the new exact head.

| Promise | Candidate production path | Verification path and remaining gate |
|---|---|---|
| R03-F01 | app/build.gradle enables resValues; About continues reading generated clean-source provenance | Local source guard; hosted APK compile and displayed SHA assertion, new R03 baseline |
| UXR04-P01 | MainActivity, AdaptiveContentFrame, UiLayoutPolicy, existing navigation rail, adaptive stacks | JDK policy tests; ten real hosted viewport cases and screenshot/geometry verification pending |
| UXR04-P02 | FlatUi and activity multiline text, accessible scroll paths, wrapping navigation labels | Policy tests; 100/160/200% real font matrix, headings and reachable actions pending |
| UXR04-P03 | Native headings, state descriptions, labelled row actions, live pending regions | Existing semantics; Espresso accessibility checks plus sanitized actual UIAutomator hierarchy pending; physical TalkBack pending |
| UXR04-P04 | Distinct catalog target controls, measured 48dp actions, bounded focus identity and IME insets | Actual separate target, IME and focus/recreate checks pending |
| UXR04-P05 | DesignTokens/Theme resources, dynamic-color capability fallback, fixed palette | Fixed palette unit tests; resolved text/primary contrast across theme matrix pending |
| UXR04-P06 | Bounded route/scroll/search/focus restore and persistent interrupted-capture marker | Actual orientation round trip, recreate, background kill/relaunch pending |
| UXR04-P07 | HookLabApp process-owned PendingFrameworkCommands; FrameworkStateStore generation and command epoch checks, verified readback | Executed JDK race/ownership tests; hosted FrameworkFixtureTest recreation and negative callback assertions pending |
| UXR04-P08 | Existing bounded icon cache and 4000-event retention; actual 30-event timeline pages | Hosted cold launches, frame durations, page timings and sampled PSS report-only baseline pending; no fabricated budget or physical memory claim |
| UXR04-P09 | API29-compatible UI with API36 edge/inset and dynamic-theme fallback | Actual hosted APK install/digest and API29/36 journeys pending |
| UXR04-P10 | Preserve explicit framework/root/Termux authorization; import/capture and redaction safeguards | Existing source guards; actual import/manual cancel and framework/diagnostic/Termux negative fixtures pending |

The extended workflow has ten named shards, each with eleven actual Activity routes; a separate aggregate fails if any shard, exact run attempt, installed APK digest, screenshot, projected hierarchy, test roster, or performance receipt is missing or inconsistent. The raw UIAutomator hierarchy is not exported. The reference case repeats the existing framework, diagnostics and Termux synthetic fixtures; none proves real LSPosed or root functionality. Locally executed tests include synthetic evidence fixtures and pure JDK production model tests, not Android runtime. No local Gradle, no visual golden admission, no physical-device or release acceptance. The metrics are observations rather than enforced performance budgets; approval and budget selection remain a later reviewed gate.


### F01 hosted correction

The exact parent failed Design (Pillow import order), native instrumentation compile (AppCompat error attribute incorrectly referenced via Material), and all ten extended shards at that compile gate. The F01 repair preserves all matrix cases, comparisons and independent artifact verification. Regression tests cover actual workflow dependency ordering, mandatory instrumentation assembly in ordinary Android CI, and real resolved palette attribute mapping. New exact-source hosted evidence is required for all five workflows. No native acceptance inferred from prior Android or CI24 success.


### F02 adaptive qualification repair

F01 exact head `d40521c183f43e97b6e3f14556409a3fc6ba1086` (Extended UI run 45710770410 attempt 1) passed one matrix case. Five cases completed the journeys/performance methods but reported no completed native frame samples; API36 compact failed the post-IME search-focus assertion. API36 600, 840 and tablet landscape retained only instrumentation status codes; no precise original tablet cause can be asserted. F02 addresses concrete inspected defects: stable input identity, cursor/focus ownership, asynchronous IME/resize, viewport settling and content-width calculation after scaled navigation rail. The timeline test collects completed hardware-accelerated Window FrameMetrics over real bounded scrolling instead of assuming cold-launch gfxinfo contains normal completed frames. Five cold launch timings remain independent and prove fresh process identities. All ten actual journeys and all existing thresholds remain required. New failure artifacts contain only allowlisted phase IDs, exception classes and app source locations, with a source/test-bound phase checkpoint even when runner stack text is missing. No raw Espresso dumps or new sensitive collection. This is a source correction; Android runtime validation, exact-source hosted aggregate, CI24 rehash, device and visual acceptance remain pending.


### F03 qualification repair

F02 exact source `68a9269ef41cfdfb3d24ffbd38e1b57017e7848e`: 3/5 workflows passed, 5/10 extended cases passed. The three tablet failures expose HOME_SCROLL but not the original assertion; the semantic row repair and structured cause checkpoint do not assert prior root cause or hosted acceptance. Native and primary extended fixture failed at `FrameworkFixtureTest.java:251`; the catalog mode and explicit stable search action now preserve query and independent hook identities. Landscape performance refused viewport convergence after the journey; rotation is locked/read back independently before every instrumentation and before cold launch. All previous accessibility checks, performance thresholds/evidence requirements, route rosters and source identity gates remain.


### F04 exact F03 qualification repair

Source `20724a6995b7ba8c8bd3e7c08254831fc619509a`, tree `a9bce675e4df4a4518af861bc9a4cc5ad4a238ec`. Extended 45724979860 attempt 1: 2/10 success, 6 runtime failures, 2 Maven 429 build failures. Native baseline passed. Correct API29 rotation compatibility, independently freeze display in each instrumentation, enforce actual toolbar/nav/row geometry, retain full ATF, add source-bound safe Home geometry on refusal, use real stable capture resources and actionable owner selection. Single hosted build emits one exact pair; all ten jobs independently verify installed hashes, aggregate compares against exact downloaded pair; operator independently rehashes twelve artifacts. Full rerun required if retrying the workflow. All real runtime gates pending, no physical-device/visual/release approval.


### F05 measured failure repair

F04 `ccb228f7faaaf94dc55c12c52a34c87b398be4f5` shared APK build passed; 6/10 cases passed. Tablet landscape rail targets 59px versus 96px, overflow 80px versus 96px in both failed tablet cases. Landscape stopped on launcher-side rotation wait, and primary API36-390 passed all five instrumentation tests before unavailable research choice. F05 removes duplicate Material nav insets, allocates actual overflow width, checks measured chrome targets on the real Home Activity, verifies exact rotated display after Activity resume, and uses individual stable capture choice rows with dialog-scoped scrolling. Real process death and all original matrix, accessibility, performance and shared APK gates remain mandatory. Local source QA is not native/hosted/device acceptance.
