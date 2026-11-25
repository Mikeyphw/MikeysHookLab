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
