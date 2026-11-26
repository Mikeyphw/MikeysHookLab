# MOD-00-F02 — Shared APK Verification and CLI Regression Repair

Parent: MOD-00; predecessor: MOD-00-F01. Noncounted hotfix. Exact PR #34 head `534b92cb9311d09462bc072f11d479e789106f6d`, run `45834440755`, attempt 1. Hosted success and 24/24 independent CI24 shards do not establish complete independent acceptance. The local standalone `shared_native_apks.py verify` command raised `AttributeError` because its argument Namespace shadowed the imported adaptive_matrix module. F01 evidence is retained, not transferred to a new SHA.

## Implementation promises

- F02-P01: Separate the imported module and parsed arguments; verify both shared APK receipts and the unsigned release receipt through the real CLI without changing evidence predicates. No local Gradle, no signing.
- F02-P02: Exercise standalone verify, publish, restore and refusal paths with exact Git/run binding and synthetic APK bundles. Refuse changed app, instrumentation or release digest, every receipt identity drift, unexpected roster and restoration before invalid evidence is accepted.
- F02-P03: Preserve all existing hosted qualification gates, history and the existing draft PR #34; native apply and successor delivery only after retained F01 native session, exact successful hosted run and pending independent qualification are reconciled. Recompute fresh full required CI/CI24 and independent artifacts for the successor. Device/release/visual approval remain separate.
