# CGT01-P05 — Copy Action Causality (ChatGPT 1.2026.321)

**State:** source candidate; pinned static APK evidence PASS; native Devtool/hosted Android CI/physical device **not yet performed** for P05. The observed CGT01-P04 device signals are carried forward as a *prerequisite*, not a P05 acceptance receipt.

**Repository:** `Mikeyphw/MikeysHookLab`, `main` at `2f313c2664189713415f1986e723ce31c7559736`, exact tree `30b534c7de3d750b99878753564bf34eeace12c4`.
**Host:** `com.openai.chatgpt`, versionName `1.2026.321`, versionCode `2632128`, main process only. Existing opt-in `chatgpt.codeblock.copy.probe` ID and LSPosed scope are unchanged.

## Static DEX evidence

The user-provided APKM `base.apk` SHA256 `3ef776e51c81b47a16938a027b03e3dc0c059fa5825f746ba35955709f6d63ad` contains:

- `classes4.dex`, SHA256 `a2d2b9725d0cc5eceeb466bdbd736eb91b7a78b2288040d5b466af04ce70d76d`: `lxl.a` `CodeBlockImpl` and `lxl.c` `CopyButtonImpl`, with the exact pinned P04 method signatures. The latter creates an `Ltwl;` instance (`new-instance` at code-unit 228 and exact seven-argument constructor call at code-unit 242).
- `classes5.dex`, SHA256 `c257cf018f184939b71fa666e87286d6e97405bc09230f023f13cdcc3d53bb24`: `twl` implements `kotlin.jvm.functions.Function0`, with the seven-argument `(<Object>,<Object>,String,<Object>,<Object>,bho0,byte)` constructor and zero-argument `Object invoke()`.
- `co3`, the third `lxl.c` argument, is **an annotated text class implementing `CharSequence`, not a click callback**. P05 does not misidentify it as such.

The verifier `scripts/check_cgt01_p05_apkm.py` checks these pinned DEX identities independently. This is static evidence for a plausible click callback; whether the callback is created, invoked or responsible for a Copy tap remains a separate on-device fact.

## Instrumented behavior, no action replacement

1. Keep P04's native renderer/clipboard observers and P03's `Function0` slot-10/14 correlation and keyed value-only parity. Do not change clipboard data or return values.
2. Report bounded, fixed-code P03 callback registration outcomes: missing callback or source, not a Function0, ambiguous reuse, class hook already installed, class cap, `invoke` missing/incorrect return, hook installed/failed, frame missing, callback invoked. This addresses the earlier silent-register-or-reject ambiguity.
3. During `lxl.c` composition, start a thread-local render frame and close it in `finally` even if the host throws. Hook only the exact pinned `twl` seven-argument constructor and `twl.invoke()` method. Mark a new callback object **only if its constructor completes inside that render frame**. Entries use weak **object-identity** references, max 96, TTL two minutes, no plaintext source. `twl` instances constructed elsewhere are never tagged.
4. An invocation of a tagged callback is logged as `CGT01_P05_BUTTON_HANDLER_INVOKED`, **candidate**, not necessarily a physical tap. A same-thread clipboard write inside its invocation gets a `BUTTON_CLIPBOARD_MATCH` only when the P04 HMAC value matcher also identifies one exact renderer parameter; ambiguous/no-match are different codes. Identity of the particular visual code block remains **UNVERIFIED** even after a match.
5. Each fixed event+role logs at most four times per process; counters are bounded and do not persist. Candidate classes/paths/argument values/raw code are not written to logs. All hooks are protective, opt-in, exact version/process and read-only; there is **no Termux launch or execution**.

## Device acceptance (unperformed)

- Install P05 from the exact hosted-CI source and stable-signing path; confirm binary source/provenance and `CGT01_PROBE_INSTALLED` plus `CGT01_P05_HANDLER_HOOKS_INSTALLED`. New HookLab APK might still use versionName `0.7.1`, so inspect actual APK code and signer before replacement.
- Fully restart ChatGPT. Render two adjacent **different** code blocks and tap Copy on each separately. Collect bounded `CGT01_P05_P03_*` registration events, `CGT01_P05_BUTTON_CONSTRUCTOR_BOUND`, `CGT01_P05_BUTTON_HANDLER_INVOKED`, and `CGT01_P05_BUTTON_CLIPBOARD_MATCH` with the correct association and without raw data.
- Repeat identical code blocks, recompositions, scrolling, streaming, rotation, dark/light theme and process restart. For identical copied values, deny visual block identity if callbacks cannot be distinguished.
- Check unrelated copying and `twl` invocations in other UI locations: no false Copy correlation. Verify callback interception has no crash, changes to clipboard semantics, network/shell dispatch or privacy leaks. Inspect both LSPosed framework/module logs and logcat because logcat alone can be incomplete.
- If the matching clipboard event is not synchronous with the tagged callback, classify `ASYNC_OR_UNRESOLVED`, not a successful action identity.
- Require hosted build/unit/lint/APK provenance/installed stable signer separately; do not run Gradle locally, directly or through Devtool.

**Next overlay (future, not included):** a user-confirmed, HookLab-owned Termux bridge and visible code-block action, only after the real Copy action and block identity gates pass. Permission ownership is different inside ChatGPT's injected process. No live Termux intent can be authorized by value equality alone.
