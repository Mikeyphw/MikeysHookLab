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
