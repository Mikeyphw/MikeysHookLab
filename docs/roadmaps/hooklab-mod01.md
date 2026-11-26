# MOD-01 — Modular Core & Internal SDK

Source preimage: PR #34 merge `84ecd37ca573ad4ea937e1fcef51dfaaa91b2c04`, tree `f8d86a93bd23dcf770a4aaaf39a8a686069c0414`. MOD-00 hosted qualification succeeded on run 45849030030 attempt 1; historical failures are retained. This is a built-in modular foundation, not a general hook-pack loader or a device acceptance claim.

| Promise | Implementation and independent source checks |
|---|---|
| P01 | Stable six hook IDs, existing packages, versions, preference keys and UI labels; one immutable `META-INF/hooklab/builtin-registry.json` loaded through `BuiltinRegistry`; `TweakCatalog` verifies reviewed constants and lazy adapters. |
| P02 | Manager grouping via `HookGroupCatalog` and host dispatch via `TweakCatalog` use the same manifest. `RegistryGeneration` requires a matching generation readback; missing/stale/invalid snapshots refuse host installation. |
| P03 | Separate Java `:sdk` and `:runtime` libraries. `HookSdk` owns versioned immutable descriptors, context, settings, event and entrypoint boundaries. Existing reviewed app engine remains the built-in adapter, not another catalog authority. |
| P04 | Existing engine, diagnostics, compatibility and keys retained; `HookContext` and `HostFlagSnapshot` bridge actual runtime. Existing package/version and safety restrictions still apply. |
| P05 | Exact main-process/package/version/opt-in checks before installer invocation; debug probe is limited to the owned package and reviewed version/signer. |
| P06 | Manager publishes and reads back registry generation; host consumes immutable read-only settings. No host networking or writes; mismatched generation disables installation. |
| P07 | Versioned manifest/settings/index/receipt metadata in `contracts/metadata.schema.json`; strict Java and independent Go readers; 33 shared TSV corpus cases and Go inspect/version CLI, build/vet/race/fuzz checks. No signing or distribution in this overlay. |
| P08 | Independent `:probe-payload` SDK-only marker DEX, debug-only manager Activity and isolated owned `:probe-target`; `mod01_probe_apk.py` verifies exact class-definition membership, release isolation and source/attempt APK digests. Device gate below remains pending. |
| P09 | Source-bound cumulative native overlay, exact hosted plan and independent artifact verification; no native history replay, no local Gradle, no auto-merge. |

## Verification

`scripts/qa/test_mod01_contract.py` compiles actual SDK/runtime with javac, executes `Mod01Checks`, separately compiles and loads the benign marker payload, checks shared corpus and exercises the standalone APK verifier with synthetic DEX fixtures. `scripts/test_apk_scope.py` checks packaged registry metadata and unchanged scope. Existing source and UI regression checks remain mandatory. Go tests, vet, build and bounded fuzzing execute without Android Gradle. Hosted Android CI must build debug/release and probe target, independently verify signed debug APK identity and exact source/attempt artifacts, and pass applicable full visual/adaptive and CI24 gates. Synthetic DEX checks are not device or APK build proof.

## Physical-device acceptance — not yet executed

Use only the source/attempt verified manager debug APK and owned probe target APK from the same successful hosted run. Compare the exact hashes to the bound receipt and verify both debug signers. Do not replace a stable production install with a mismatched debug signer. On an authorized test device inspect the actual installed Vector/LSPosed version, API 102 and remote-file capability. Open the debug manager's Transport Probe screen, request scope only for the owned target and confirm it in the framework. Launch the target before opt-in; its marker must report BASE. Explicitly publish/enable the reviewed payload and observe successful manager readback. Restart the owned target; its marker must report MOD01_PROBE_OK. Disable the probe and restart: BASE. Remove the test scope and restart: BASE. Missing/unsupported capability, stale generation, wrong signer or digest, unavailable transport, and missing consent must fail closed. Record exact device/framework version, capability, source/attempt APK hashes, manager readback, target marker and disable/restart results; do not infer physical proof from hosted success. No arbitrary external DEX may load. Do not advance MOD-02 on an unsuccessful or missing required transport gate.
