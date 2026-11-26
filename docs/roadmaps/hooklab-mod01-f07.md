# MOD-01-F07 — Hosted Dependency CLI Integration Repair

PR #35 head `186e738bcac30e7b888b0bd4b3d0e2bc1284970e`, tree `3f04f646696db35c4fe58fc4d2a4724491665659`, run `45902345026` attempt 1. Android job `140191166362` restored 130 MB wrapper and 315 MB historical dependency caches, then refused `seed-standard` because the composite invocation omitted `--state`. No Gradle ran. Source, Go, terminal and browser passed; CI24 summary `140191325296` refused missing dependency readiness and targeted qualification `140191634064` refused failed Android. Preserve actual failed attempt and existing PR; no replay, new mirror, dependency change or Devtool source mutation.

- F07-P01: pass the bound private state to the real `seed-standard` CLI; preserve resolver key and exact-source readiness checks, cache isolation, publisher authentication, offline execution and all CI24 gates.
- F07-P02: regression executes actual composite shell commands with real Python CLI, isolated cache paths and exact Git identity, including empty, standard private cache and historic home seed; reject wrong/missing state, unsafe seed and non-hosted or stale identity. Inspect all dependency CLI call sites and exercise real consumer verification against a synthetic snapshot fixture (not hosted acceptance). No local Gradle.
- F07-P03: reconcile predecessor terminal attempt through native Devtool, retain PR #35 and all historical evidence; fresh source-bound hosted language qualification and subsequent independent CI24/device/release acceptance required. Do not publish an unqualified operator.

Maintained shell harness: `scripts/qa/shell/dependency_routes.bats` exercises the real retry/refusal and composite CLI paths without Gradle; run via `bats scripts/qa/shell/dependency_routes.bats` when Bats is available.
