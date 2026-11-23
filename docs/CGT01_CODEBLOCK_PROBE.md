# CGT01-P02 — ChatGPT CodeBlock Copy value-parity probe

**Tier:** source implementation candidate; real LSPosed injection and Copy parity pending device/hosted CI.
**Base:** `Mikeyphw/MikeysHookLab` commit `3fcffacc925baeb09f0d5fccebd413f4ece0e32a`, tree `55cf36b3a8f9bdf170756eae368698886bb45397`.
**APK:** `ChatGPT_1.2026.314.apk` SHA256 `42291c2010897f940e1691f357bfffcb8d35ba7c329104240d4b26412d3eae15`.
**Exact target:** `com.openai.chatgpt` 1.2026.314 / versionCode 2631431; main process only, OptIn default false; dynamic framework scope requires separate approval.

## Implementation

1. `TweakCatalog` registers `ChatGptCodeProbe::install` with exact version and no automatic scope.
2. Host dispatcher obtains verified `PackageInfo` and read-only LSPosed preferences and safety locks before invoking installer. The probe verifies name+code again.
3. Reflection requires exact `p4l.a` `CodeBlockImpl` (classes7.dex) static signature (20 parameters, including first two String parameters), with obfuscated types verified from the pinned APK. Hook inspects only these strings after original draw returned.
4. A second hook observes `ClipboardManager.setPrimaryClip(ClipData)` **after** the host call. Only text from a single clipboard item can be compared. The hook never writes to the clipboard or reads it later. Clipboard actions without a matching renderer source are logged as a sparse generic no-match code, with no content or lengths.
5. The matcher immediately converts text to UTF-8, HMACs it with a fresh random in-process key, retains only keyed digests and counts (not raw code). Matches log parameter slot, byte count, LF/CR and trailing LF, short **keyed** token, and *explicitly* `identity=UNVERIFIED action_causality=UNVERIFIED`.
6. A collision between two String slots is classified AMBIGUOUS; duplicate identical blocks remain **value-only**, never block-identity proof. There is no button injection, no Termux dispatch, no root or shell commands, no file persistence, no network permission, and no conversation serialization.
7. Source checks and pure-Java matcher fixtures cover CRLF, Unicode, substitutions, trailing whitespace, distinct strings, ambiguity, expiry, input cap and invalid UTF-16. Hosted CI `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, APK metadata and actual device remain separate.

## Manual device acceptance protocol (not performed)

1. Install a **hosted-CI-built and safely signed** HookLab APK, approve scope `com.openai.chatgpt` separately in LSPosed, enable `ChatGPT · Code-block Copy parity probe` in HookLab and restart ChatGPT. Do **not** install debug builds over stable-signed versions without signer continuity.
2. Display at least two adjacent code blocks with distinct source strings and syntax highlighting. Tap **Copy** on the first and second block separately, inspecting LSPosed logs for `CGT01_COPY_MATCH_VALUE_ONLY slot=…` within the action. Record a live source-bound test receipt; no copied command content in logs.
3. Repeat for CRLF/trailing newline, quote and `$()` preservation, rerendering, scrolling, streaming, theme switch. Check that only the tapped block's copied text matches one render candidate. A value match alone still cannot distinguish two identical blocks and is **not full identity acceptance**.
4. Test ordinary conversation Valdi, DIL/CodeBlock, native Compose and preview separately. The current probe instruments only the native `p4l.a` path; unmatched Valdi/DIL output must be classified as a missing path, **not** marked PASS.
5. Confirm there are no raw code/logcat leaks, no automatic execution or Termux interaction and no crashes on opening/closing app. Disable the opt-in switch and ensure the probe does not install after restart.
6. For a newer ChatGPT version, refuse installation until a new exact DEX symbol/method inventory is verified. No heuristic hook scanning of arbitrary application methods.

## Current limitation

This **does not yet prove a particular block was selected**. More precise diagnostics will need the real button callback/model identity for each renderer. Matching the String sent to the clipboard proves value parity only when a live tap provides context. Never rely on this alone to authorize command execution or to select the block for Termux.

## No local Gradle

Devtool-native validation must be **Python source-only**, with `restore/build/test=false`, a verified source-check script and no Android Gradle `tasks`. All Android Gradle tasks execute in hosted GitHub Actions. No local Gradle invocation is allowed, including indirect Devtool invocation.


## P03 — Action-callback object identity (unaccepted source candidate)

P03 retains P02's value-only logs and adds an independent process-local `CodeBlockActionMatcher`.
The renderer's two candidate `Function0` arguments (ordinal 10 and 14) are bound
by **object identity** to the renderer's second String. It hooks the concrete
zero-argument `invoke()` method of each verified candidate class, limited to
24 callback classes in the ChatGPT main process. It does not globally hook all
Kotlin `Function0` invocations or alter their return values or call order.

During a synchronous callback invocation, clipboard writes are checked against
that specific callback binding. `CGT01_ACTION_CALLBACK_INVOKED role=N binding_id=M`
is a callback observation only, **not yet proof it is the Copy button**.
`CGT01_ACTION_COPY_BOUND_VALUE_MATCH` means an exact clipboard text match occurred
on the same thread inside the bound callback; `visual_block_identity=UNVERIFIED`
remains explicit until UI tap → renderer block identity is independently shown.
`CGT01_ACTION_SHARED_CALLBACK_AMBIGUOUS` refuses a callback reused for multiple
roles or source strings. `CGT01_ACTION_COPY_VALUE_MISMATCH` never authorizes a block.
Asynchronous clipboard writes have no active callback and cannot be accepted.

Privacy: only process-local, per-installation keyed HMACs and bounded metadata are
recorded; no raw code, conversation IDs, shell execution or Termux dispatch.
The separate Valdi `onCopyCode` and DIL action UI remain unverified.

### P03 mandatory runtime tests

1. Confirm `CGT01_PROBE_INSTALLED` on the exact build after explicit opt-in.
2. Tap Copy on adjacent distinct blocks, separately: collect the paired
   `CGT01_ACTION_CALLBACK_INVOKED` and `CGT01_ACTION_COPY_BOUND_VALUE_MATCH`.
3. Tap Copy on two identical blocks, separately; require two distinct binding
   identities when the app exposes different callback instances. A shared
   instance is AMBIGUOUS and fails closed.
4. Repeat after scrolling, streaming, recomposition, theme switch and app restart.
5. Copy unrelated non-code text: no `ACTION_COPY_BOUND_VALUE_MATCH` may appear.
6. Check no Host process crash, clipboard mutation or raw code in logs.
7. Check ordinary Valdi, native Compose, DIL and preview independently. The
   only instrumented path is currently native `p4l.a`; no claim of full coverage.

**Acceptance: PENDING.** Static evidence supports the two candidate callbacks,
but a real Android/LSPosed tap test must identify which role, if either,
actually owns the Copy action and whether it is synchronous. Do not activate
Termux execution or claim exact block selection from this source alone.
