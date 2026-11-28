# MOD-04 — GitHub Distribution & Updates

Consolidated successor to merged PR #39, exact main commit `5da4ed3810db7f6aa73241eac9141948f4f3146d`, tree `7d91820f4861242703a6823833dbfb74c30ff141`. PR run `46084664024` passed 44 jobs, 10 adaptive cases, 24 CI24 shards; 3 planned skips. Main run `46102361941` attempt 1 failed API29 compact with `cold launch preexisting process`; aggregate and final qualification consequently failed. The repair polls for two confirmed absent-process samples within the existing isolated cold-launch boundary; it neither repeats the force-stop nor launches twice. Fresh hosted evidence is required.

## Preserved MOD-03 device evidence

User reported PASS on the target device for hiding and restoring Run in Termux, Copy while hidden, named profiles and per-app overrides, 1200 ms repeat-tap protection, rotation/draft preservation, export/import, reset/undo, signed ChatGPT 1.1.0 runtime and custom UI. Collected runtime output contained `CGT01_P08_CONFIG_ACTION_HIDDEN execution=NONE`, both built-in hook registration markers, and preview Activity launch; collection returned 127 only because an extra `q` followed the completed log query. This is user-attested device acceptance, not independent signed archive or release identity attestation. Word Master 0.6.10 registers but produces no visual effect; 0.6.12 remains outside its exact supported range. No claim of Word Master repair or general device acceptance.

## Distribution promises and validation

| Promise | Implementation | Required external evidence |
|---|---|---|
| P01 | Independent module tags, hashed source projections and shared SDK input dependencies, patch version selection | Protected real release, single-module and SDK-wide changes |
| P02 | Go signed index, independent Java verification, monotonic revision and version/tombstone checks | Real publisher/remote release index |
| P03 | Explicit session-only fine-grained read token; no persistence, logs, saved state, autofill or redirected authorization | Device credential flow with scoped token; no claim of isolation from admitted personal code in same UID |
| P04 | Manager-only repository browsing, official/personal explicit selection, stable/testing separation, filter/details, signed offline cache | Physical UI and public/private remote read |
| P05 | Exact workflow/run/attempt/job/commit/tree/artifact/receipt binding and test publisher; debug only | Hosted real artifact inspection and device test lane |
| P06 | Exact supported target version, SDK and reviewed publisher checks, no override | Device upgrade/incompatibility checks |
| P07 | Batch archive validation, one atomic state transaction, version pins, existing rollback; explicit publication/restart | Device publication and rollback |
| P08 | Fixed HTTPS API and bounded asset redirect origins, no forwarded credentials, size/time bounds, ETag and offline label | Live network and private access |
| P09 | Protected main-only release dispatch, independent qualification and DEX evidence, index published last | Actual configured protected environment and authorized release |
| P10 | Opt-in hourly foreground refresh on opening manager repository screen; no host/background traffic or automatic install | Physical opt-in/out and notification state |

Local fixtures use ephemeral synthetic keys/payloads and mocked HTTP; they establish source behavior, not genuine release or GitHub authorization. Android manager build/lint and actual Activity test run in hosted CI, no local Gradle. Source qualification and hosted/device/release acceptance remain independent.
