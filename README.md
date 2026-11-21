# Mikey's HookLab

Small hooks. Better apps. A modular LSPosed API 102 app with explicit, per-tweak opt-in, framework-managed scope, read-only host preferences, per-process fault quarantine, and stable GitHub-Environment signing.

## M6 — Meu Cronograma Capilar: manual diamonds only

The single registered tweak is **MCC · Manual diamond button** (`mcc.diamond.control`). When explicitly enabled for `br.art.code.meucronogramacapilar`, the Flutter Activity receives a compact **♦ 9** button. It opens HookLab's manual control screen; opening the screen never stops MCC or requests root. Root requires an additional explicit confirmation. Use the read-only storage inspection before attempting a change.

The original project [MCC-VIP-Lab](https://github.com/Mikeyphw/MCC-VIP-Lab) provided evidence for a single-digit, account-local `*-DiamondBalance` file under `databases` on app version `20.2609.3009`. This port checks only the target's own recognized storage directories and refuses missing, ambiguous, symlinked or unknown balance formats. It does not fabricate server-side diamonds. A successful local write can still be overwritten by MCC synchronization.

**Removed from active registration:** automatic refill, RevenueCat VIP spoofing, and ad/banner hooks. Legacy classes remain unregistered because native Devtool's current overlay format does not support deletion. The automatic handoff entry point itself is disabled. Previously persisted opt-in settings for retired tweak IDs have no effect.

## Distribution and safety

APK builds, unit tests, lint and provenance checks run on GitHub Actions with Android SDK 37.0. Stable releases use the existing manual `stable-release.yml` workflow and a preserved signing certificate in the `hooklab-signing` GitHub Environment; do not install debug APKs over stable releases. Version `0.5.0`, code `6` follows signed version `0.4.1`, code `5`.

Native Devtool creates a new isolated worktree and draft PR, verifies exact source and hosted CI, and does **not** merge, install APKs, call root, rotate signing identities, or qualify a device/release.

See [MCC manual port and device acceptance](docs/mcc_port.md) and [stable signing](docs/signing.md).
