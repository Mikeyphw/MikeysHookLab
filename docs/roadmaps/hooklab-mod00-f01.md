# MOD-00-F01 — Adaptive Rotation Readback and Catalog Window Readiness Repair

Parent: MOD-00. This non-counted hotfix is based on PR #34 head `468d93144f45625b822dc5fa7302712b51b20b92`, tree `21d780e187ae092f3347faaba6de9d359119d618`. PR base is main `6306d05d369824ace13ce5fb746269acb4f844ef`.

## Hosted evidence and scope

- Run 45821564490 attempt 1, job 139916262114 (`api36-landscape`): emulator boot completed; `ADAPTIVE_PHASE_SEARCH_OPEN`, `NoMatchingViewException` at `AdaptiveUiTest.java:282`, instrumentation exit failure. The exact underlying UI/root selection cause is not established by the sanitized checkpoint.
- Attempt 2, job 139930433889 (`api36-tablet-landscape`): boot completed in 41603 ms, APK digests verified, `EXTENDED_UI_REFUSED matrix rotation lock not confirmed` before instrumentation. Readback timing/state is the failure boundary; raw lock value was not retained.
- Both attempts: 24/24 JVM shards and 9/10 adaptive cases passed. Adaptive aggregate and targeted aggregate correctly refused failure. Preserve both attempts. No relaxation of required matrix, dimensions, orientation, accessibility, real click, search/IME, screenshot, APK identity, CI24, or exact attempt checks.

## Promises and acceptance

| ID | Implementation | Local evidence | External acceptance |
|---|---|---|---|
| MOD00-F01-P01 | Reassert only the exact rotation lock once on bounded stale readback; require two consecutive platform/settings confirmations before each instrumentation boundary. Transport errors propagate. | Mocked platform sequences in `test_adaptive_matrix.py`: immediately stable, delayed, permanently incorrect, alternating, API29, transport failure. | Same real landscape dimensions and both instrumentation methods on API36 tablet landscape. |
| MOD00-F01-P02 | Verify actual Hooks selection/title/attached measured search field and focused Activity decor; execute original real click/scroll and IME assertions in that exact root. No synthetic focus or navigation retry. | Source-contract checks of actual call path; safe source/phase-bound readiness checkpoint negative fixtures. | Both landscape journeys, including `SEARCH_OPEN`, typing, IME, recreation, rotation and clear. |
| MOD00-F01-P03 | Preserve failed parent acceptance and attach non-counted F01; unchanged coordinator requires all applicable jobs and artifacts on exact successor. | Roadmap roster, exact Git preimage/postimage, complete source regression suite. | Full exact-head hosted coordinator, 24 CI24 shards, ten adaptive cases, independent artifact verification. Physical, visual approval and release remain separate. |

No production Activity UI semantics are changed. No emulator retry or aggregate evidence waiver. Root binding and bounded readback are proposed robustness repairs; proof of efficacy requires the hosted successor run. No local Gradle execution.
