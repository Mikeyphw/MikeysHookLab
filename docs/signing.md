# HookLab stable signing (M5.1)

## Goal

All **future installable release APKs** use one preserved signing identity.
An existing APK with another certificate cannot be updated in place, even if the versionCode rises. The previously uploaded `hooklab-debug-*` artifacts use Android's debug signing; do not install them as subsequent production updates. Back up any existing app data before any voluntary reinstall; this repository never uninstalls or clears apps automatically.

## Identity and permissions

- Package remains `com.mikeyphw.hooklab`; versionCode `5`, versionName `0.4.1` for this update. Increment versionCode for every later installable release.
- A private signing identity is created once under `~/.local/share/hooklab/signing/` on Termux, not on the runner. It is reused after every build; losing it and the password breaks update continuity. Back up both files privately (do not share passwords, keys or the directory).
- `scripts/configure_signing.py --publish-secrets` creates or verifies the `hooklab-signing` GitHub **Environment**, restricts its branch policy to exactly `main`, and uploads five encrypted environment secrets using standard input (no command-line arguments containing secrets):
  - `HOOKLAB_SIGNING_KEYSTORE_B64`
  - `HOOKLAB_SIGNING_STORE_PASSWORD`
  - `HOOKLAB_SIGNING_KEY_ALIAS`
  - `HOOKLAB_SIGNING_KEY_PASSWORD`
  - `HOOKLAB_SIGNING_CERT_SHA256` (public fingerprint, stored alongside credentials to pin the trusted identity)
- If environment rules are wider than exactly `main`, or any of the five secrets already exist, setup refuses to overwrite secrets or rotate keys. Recover the existing local key/backup instead.
- To continue using a compatible pre-existing keystore, run `scripts/configure_signing.py --import-keystore /secure/existing.p12 --alias EXISTING_ALIAS --publish-secrets`; it prompts for passwords privately. Ensure the existing key is genuinely HookLab's signing key.

## After native M5.1 overlay and CI

1. Merge the M5.1 PR after normal review and device acceptance. Confirm the branch `main` is the intended signed source. Do not run a protected release from a PR or feature branch.
2. In Termux, after installing `gh` and an OpenJDK that contains `keytool`, run `python3 -B "$HOME/Code/MikeysHookLab/scripts/configure_signing.py" --publish-secrets` from the updated main checkout. This operation never invokes Gradle.
3. Privately back up `~/.local/share/hooklab/signing` in an encrypted location.
4. Trigger the workflow on main: `gh workflow run stable-release.yml -R Mikeyphw/MikeysHookLab --ref main`.
5. Independently verify workflow status and SHA/certificate evidence before installation. The workflow builds `:app:testDebugUnitTest`, `:app:lintRelease`, `:app:assembleRelease` and verifies the APK signature against the pinned fingerprint; all happen on GitHub Actions.
6. Download the `hooklab-stable-release-1` run artifact. Keep the certificate and source evidence alongside the APK. Future stable APKs must match that certificate and advance versionCode. Avoid PR/debug APKs for updates.

## Security and limitations

- No keystore is ever committed, printed, copied to clipboard, or uploaded as an Actions artifact.
- PR workflows continue using debug builds with no access to signing secrets. A separate `workflow_dispatch` workflow requires the `hooklab-signing` environment and a main-branch job condition. Configure GitHub branch protections/rulesets for `main` before relying on this for distribution.
- Signed-release job uses minimal contents-read GitHub token permission, writes the decrypted keystore only to runner temporary storage, deletes it on shell exit, and uses password environment variables without writing them to `GITHUB_ENV`.
- A signed APK build alone is not a device validation, install authorization, or genuine MCC VIP entitlement.

## Independent module publisher (MOD-04)

Manager version 0.8.0/11 is an APK update; routine module releases do not rebuild the manager. Module publisher identity remains the existing personal RSA-3072 key, not the APK signing key. Set up a separately protected `hooklab-module-signing` environment restricted to `main` with required reviewers, and pin the existing public-key DER SHA-256 as `HOOKLAB_MODULE_SIGNER_SHA256`; store the existing private PKCS#8 DER encoded as base64 only in `HOOKLAB_MODULE_SIGNING_KEY_B64`. Never generate or rotate an existing identity automatically. Do not store credentials or keys in the repository. No module release is dispatched by the overlay. After exact fresh main qualification passes, an authorized reviewer may dispatch `module-release.yml` with `qualified_run` equal to the successful main run ID. The read-only verification job and protected signing job independently bind the complete qualification and artifacts. The signed index is published last after asset readback. The manager requires explicit publisher enrollment, verified fresh index and reviewed install, then separate framework publication and restart. The optional repository token is session-only; use read-only Contents permission and Actions read only when inspecting testing artifacts. Reviewed custom UI shares the manager UID; this is not a sandbox or credential isolation boundary. Public/private access and protected environment settings must be independently tested before production release.
