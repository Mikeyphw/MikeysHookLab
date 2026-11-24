# HL-DIAG-R04 — Root startup qualification & confirmed Termux handoff

Authoritative continuation of `hooklab-flat-ux.md` at main `c544974c852d33ccc485c476142a9ed460374c9a`, tree `6647812c92b9cd4ec7191fd3e331a475c6aa44fa`, prior roadmap blob `9cb35e0ebce5ccf849ebe9c4aff8d17298dd4dbb`. Historical items are preserved. This is a source candidate, not a native/CI/device acceptance receipt.

## R03-F01

Separate libsu authorization (`Shell.getShell`, `isRoot`), main-thread `bindOrTask`, execution of libsu's own task on that shell, callback arrival, and fixed health IPC. Report each start/terminal elapsed time without raw logs. Timeout budgets 35/5/10/20/5 seconds respectively. Startup ambiguity latches restart-required; no automatic retry. Root service remains a fixed metadata-only health RPC, no change to MCC data or access policy. Compare BakaSU startup status using the user-triggered inspect button. Libsu 6.0.0 source `8c3e80ffe466b89ff875471e542d77e6a1b480c5`: task submission does not imply actual process startup. Do not change namespace/SELinux/root profiles or add storage/write authority.

## CGT01-P07

User-reported R03 P06 Copy action IDs 49–52 each carried full 9-stage object-correlated trace and clipboard parity. Permanent visual block identity remains UNVERIFIED. Preserve existing read-only toggle. Add separate default-disabled `chatgpt.codeblock.termux.handoff` opt-in, requiring the P06 diagnostic enabled. On verified native Copy with nonzero block ID and exact HMAC parity, present one transient, accessible, native post-Copy button in the foreground ChatGPT activity. It does not replace Copy, alter the clipboard, retain code, send code in an intent, or execute commands. A second deliberate user tap opens the explicit Termux launcher activity; user pastes and reviews manually. It expires in 12 seconds; host clipboard writes and pause invalidate. Missing Termux is refused. Other-app clipboard changes cannot be observed by this hook; never assert the later clipboard still matches. The design intentionally does not claim a per-block inline Compose insertion; that remains a distinct pending visual-identity/UI promise.

## Acceptance

Test fresh root denial, root authorization success, launch/callback/IPC failure, timeout, restart and BakaSU service identity without accessing target storage. Test both opt-ins, one alone, exact build, positive/mismatch/zero-block/unrelated copies, repeated copies, expiry, background/rotation, unavailable Termux, click then manual paste without execution. Verify root startup actual device phases and P07 UI accessibility/clipboard preservation; hosted required Android CI and signed installation on exact source are independent. No local Gradle; CI24 belongs to the unreconciled UX foundation, not this base.

## Remaining active work (known subset)

- HL-DIAG-R02 — ChatGPT Copy Causality & MCC Migration Parity: merged; original MCC storage still inaccessible.
- HL-DIAG-R03 — Copy Coroutine Field Correction & Root Platform Qualification: merged PR27, four observed Copy traces pass; root startup timeout.
- HL-DIAG-R04 — Root startup qualification & confirmed Termux handoff: R03-F01 + CGT01-P07 source candidate; device/inline action pending.
- UX-R00 — Design Contract & Emulator Infrastructure: historical candidate needs rebase/recovery.
- UX-R01 — Diagnostics Consolidation (depends UX-R00).
- UX-R02 — Navigation & Interaction Redesign (depends UX-R00).
- UX-R03 — Home & Whole-App Visual Fidelity.
- UX-R04 — Accessibility, Adaptive Layout & Lifecycle.
- UX-R05 — Full Native Visual Acceptance & Regression Enforcement.

Prior roadmap historical DS/UI/DX/MCC/CGT/WM items and acceptance debt remain in `hooklab-flat-ux.md`. No historical acceptance is inferred from merges.

## Observed R04 device evidence

PR28 merged at `1eabc38e9b514986a84f9cccd4467473eefc8585`; required Android CI run 45426151291 succeeded. User receipt: three complete P06 traces, actions 29, 30 and 47; two P07 launch requests, three offers. AUTH 49 ms, BIND_SETUP 17 ms, LAUNCH_SUBMISSION 7 ms, BIND_CALLBACK 1285 ms, IPC returned in 2 ms with IPC_REFUSED. No proof of successful health RPC. R05 supersedes post-Copy popup; inline rendering, permission and foreground execution require new acceptance.
