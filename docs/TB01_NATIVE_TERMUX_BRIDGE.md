# TB01 — Native Termux Command Bridge

**Repository:** `Mikeyphw/MikeysHookLab`
**Pinned base:** `main` at commit `3fcffacc925baeb09f0d5fccebd413f4ece0e32a`
**Delivery:** source-bound candidate (operator 1.0.1, original source-contract patch anchor corrected); GitHub write, hosted CI, device and stable release unverified.
**Version target:** 0.7.0 / versionCode 9. Existing signing identity must be retained.

## Reconciled project inventory (no single authoritative global ROADMAP file exists on this snapshot)

| ID | Available in pinned source | Acceptance evidence from repository |
| --- | --- | --- |
| M6 — MCC manual diamond button | Source and README | Device/stable acceptance INCONCLUSIVE |
| M7 — Neo Store and Droid-ify ObtainX integration | Source and README | Device/stable acceptance INCONCLUSIVE |
| M8 — Recommended LSPosed scope and verbose logging | Source and README | Device acceptance documented as pending |
| WM01 — Word Master night filter | Source and README | Device dark-palette acceptance pending |
| TB01 — Native Termux Command Bridge | This candidate source | Device/CI/release PENDING |

No project-global overlay completion percentage can be verified from current repository evidence. TB01 is separate from LSPosed, not a hook, never changes package scope or introduces root.

## User workflow

1. Share text/plain from ChatGPT/browser/another app and choose **HookLab · Run in Termux**; Android `ACTION_PROCESS_TEXT` is also supported where the selection menu offers it.
2. HookLab opens an editable multiline preview. A separate **Paste clipboard** button is available when opened from HookLab **Settings → Termux command bridge**.
3. Review and optionally change the working directory under Termux's home. Tap **Run in Termux** and confirm **Execute** in the second dialog.
4. Termux opens a new foreground session running `/data/data/com.termux/files/usr/bin/zsh -l -i -c <exact user command>`; no automatic `su`, hooking or background command execution.

## Prerequisites (user-managed, not changed by HookLab)

- Termux (`com.termux`) with Zsh installed, `pkg install zsh` if needed.
- Android Settings → Apps → Mikey's HookLab → Permissions → Additional permissions → **Run commands in Termux environment**. HookLab requests this when Run is tapped, and after grant requires a second manual Run.
- In Termux, set `allow-external-apps=true` under `~/.termux/termux.properties`, then run `termux-reload-settings` or restart Termux. The app never changes this setting.
- Android 10+ may require additional foreground/overlay notification configuration for Termux; consult the Termux RUN_COMMAND wiki.

Reference: https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent

## Promise acceptance matrix

| Promise | Source evidence | Pending acceptance |
| --- | --- | --- |
| Switchable share target (`ACTION_SEND text/plain`) | Exported native Activity alias and Settings switch; internal receiver | Android 16 Sharesheet toggle test |
| Selected-text process action | Manifest and `EXTRA_PROCESS_TEXT` | Real selection provider test |
| Editable preview and clipboard | Activity input and explicit clipboard button | UI/instrumentation test |
| Confirmation and no automatic launch | Two separate positive taps; permission grant never retries | Device negative tests |
| Foreground Zsh with intact multi-line text | Exact String[] args to Termux RUN_COMMAND | Real Termux integration test |
| Configurable working dir under Termux home | Pure Java validator + persisted preference | Device path test |
| Error messages | Missing Termux/permission/start failure UI states | Device denial and misconfiguration cases |
| No LSPosed scope or hook changes | Scope resources unchanged; native Activity only | APK metadata/static CI |
| Signing compatibility | Bump to 0.7.0 code9; signing config unchanged | Stable-signed APK exact cert verification |

**Important:** Successful `startService` proves only that Android accepted dispatch, not that Termux ran the command or that its exit code was zero. This feature intentionally does not request or copy output/transcripts, to protect the clipboard and avoid leaking secrets. The output remains visible in Termux.

## Accessible tests

`python3 -B scripts/check_contract.py` includes TB01 static guard, and is run on the prepared worktree; `:app:testDebugUnitTest` includes 13 pure-Java policy tests; `:app:assembleDebug`, `:app:lintDebug`, signed APK verification, and Android 16 runtime tests must be run on an Android-capable runner/device.

**Source acceptance:** pending integration into exact pinned repository and native Devtool transaction.
**Native/PR:** pending; GitHub connector refused branch creation (403 Resource not accessible by integration).
**Hosted CI:** NOT RUN.
**Device:** NOT RUN.
**Stable-signed release:** NOT REQUESTED / NOT PERFORMED.

### Operator recovery v1.0.1

If `prepare` exits with code 2, read the preceding `HOOKLAB_TB01_REFUSED=...` line; the outer `CalledProcessError` is not the actual cause. `--clone-if-missing` with `--yes` may create **only** the missing standard checkout at `~/Code/MikeysHookLab`; this is an explicit option, never a silent clone. If a different matching worktree exists, `REPO_NOT_AT_REQUESTED_PATH` lists candidates and requires a user-selected `--repo`. Repeated invocation never deletes old branches or worktrees. Existing history is preserved. The original zipapp 1.0.0 contained an incorrect preimage anchor in `scripts/check_contract.py`, fixed in 1.0.1. Full Android Gradle, CI and device acceptance remain pending.

### Operator recovery v1.0.2 — missing local commit

The source-bound `prepare` preflight now detects a missing pinned base commit in an existing correct HookLab checkout. Unless `--offline` is selected, it independently checks `origin/main` via `ls-remote` against the pinned commit SHA, then runs `git fetch --no-tags origin refs/heads/main` with live terminal output, and verifies that the exact object became available. This is a non-destructive fetch: it does not pull, reset, rebase, merge, checkout, alter local tracked files or remove worktrees. The operation refuses a changed remote main, wrong origin, or failed fetch. `--offline` refuses the missing object without network access. This closes `PINNED_COMMIT_UNAVAILABLE` for normal online preparation when remote main is still pinned. It does not imply native/CI/device acceptance.

### Operator recovery v1.0.3 — Word Master patch mismatch

The pinned `scripts/check_wordmaster_contract.py` used double-quoted strings
in the package query list. The previous operator used a single-quoted patch
anchor, so real-source preflight stopped with `PATCH_ANCHOR_MISMATCH`. TB01 now
matches the precise upstream text and includes its byte-for-byte pinned Git
blob as a regression fixture. The self-test verifies the fixture's Git object
SHA-1, applies the real-source patch, and (when `~/Code/MikeysHookLab` and the
pinned commit are present) runs **all eight** original file transformations
before calling preparation. This is an operator source-check improvement, not
Android Gradle, native Devtool, device or hosted CI acceptance. It preserves
any existing worktree and refuses ambiguous reapplication.
