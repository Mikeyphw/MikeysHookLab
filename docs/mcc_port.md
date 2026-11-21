# HookLab M6 — MCC diamond-only, manual port

Exact sources reviewed:
- HookLab merged `main`: `f28ee90faef138734c2b6a27e0d08e784b401c97`
- MCC-VIP-Lab `main`: `59d02beede7335acc13db6adc9f6a96ebf1d63ee`
- Original P06 file evidence: `databases/<account>-DiamondBalance`, one ASCII byte, from MCC `20.2609.3009`.

## Production behavior

- The **only** HookLab MCC catalog registration is `mcc.diamond.control`, disabled by default and limited to the target package/main process. Existing manual-toggle preference is retained. The former VIP, banner and automatic-refill implementations remain as unregistered legacy source only because the current native Devtool overlay contract permits adding/modifying but not deleting files. No runtime dispatcher can install those legacy hooks. `MccDiamondUi.requestAutomatic()` is a hard-disabled compatibility stub.
- The in-app `♦ 9` control is added to MCC's Flutter Activity decor view after resume/window focus. A bounded accessibility search anchors near the diamond counter; a top-right fallback works without Flutter semantics. Position retries are bounded (initial + 450 ms + 1400 ms). There is no poller and no launch during Activity creation.
- A user **taps the button** to open HookLab's visible manual control. Opening this Activity does **not** request root. It has a read-only balance storage inspection button and an explicitly confirmed `Set local diamonds to 9` action.
- `MccRefillWorker` obtains the package-manager-reported installed data directory, validates an exact package-owned path under `/data/data` or `/data/user/<id>`, and starts root only inside HookLab. The amount and directory cannot come from an Intent.
- The bounded root script checks only the direct `databases/`, `files/`, and `app_flutter/` directories of that exact MCC installation. It refuses symlinks, ambiguous accounts, unexpected account file names, missing files and non-single-digit balances. It never creates a file or modifies sync markers. The stop/write/reverify sequence happens only after successful preflight. A verified write and an attempted MCC relaunch are reported separately.
- The control does not assume that local currency changes have any server-side legitimacy. A new MCC storage layout may still be incompatible: in that case it refuses without attempting a guessed modification.

## Required device acceptance (not claimed by hosted CI)

1. Disable original MCC-VIP-Lab and any other modules for MCC. Install the **stable signed** HookLab after checking that the certificate matches the previous stable-signed APK; `versionCode 6` must advance code 5.
2. Enable HookLab in LSPosed, scope it only to `br.art.code.meucronogramacapilar`, and toggle **MCC · Manual diamond button** ON. Force-stop/reopen MCC to remove old process hooks.
3. Verify MCC opens and stays in the foreground indefinitely without automatically launching HookLab. Confirm exactly one visible `♦ 9` button in MCC Flutter UI in both light and dark modes, portrait, on navigation and after rotation.
4. Tap `♦ 9`. Verify HookLab opens but **does not request root, stop MCC or mutate the balance** on open. Back returns to MCC.
5. Use **Check local balance storage**. Observe `BALANCE_FILE_READY`, or a sanitized refusal explaining why the layout isn't recognized. No app data should change.
6. Only in a backed-up test account, confirm **Set local diamonds to 9**, approve HookLab's root request, and verify write evidence and relaunch separately. Do not keep retrying on refusal.
7. Disable manual tweak and restart MCC. Verify no button and no HookLab redirect. Inspect LSPosed framework diagnostics for unexpected errors.

### Limitations

- SDK 37 / libxposed API 102 hosted build, JUnit, lint, APK signature and source-provenance checks are necessary but do not substitute for Android device testing.
- No automatic refill, VIP state simulation, Google Mobile Ads intervention or backend update is delivered.
- The previously tested MCC app version is `20.2609.3009`; newer versions may store the balance differently. Do not create `databases/` or account files manually.
- Root runs only in HookLab's own authorized UID. The older MCC-VIP-Lab root grant does not authorize HookLab. No installation, uninstall, root write, or merge occurs from the native overlay itself.
