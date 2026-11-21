# Mikey's HookLab
Small hooks. Better apps.

An Android configuration app and modern LSPosed API 102 module for independently developed app-specific tweaks. M0 is intentionally inert: no app hooks, no selected targets, no permissions and no network operations. Activation is managed in LSPosed, not inferred by the UI.

## Build and verification
Java 17 source, JDK 21 on GitHub Actions, AGP 8.13.2, Gradle 8.13, Android SDK 36. Native Devtool handles the overlay transaction and delivery; GitHub Actions hosts Android assemble, unit tests, lint and APK identity checks. Run `python3 -B scripts/check_contract.py` for the local structural gate. `bash gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` is the hosted Android gate. The debug APK is not a release or device qualification.

## Architecture
- `ui`: standalone Material 3 home, catalog, settings and about screens; night mode preference is app-private.
- `core`: immutable tweak descriptors and package/version/opt-in policy; no Android dependencies.
- `hook`: modern Xposed entry point, deliberately inert until named hooks and a framework-mediated settings transport have been implemented and tested.

Future tweaks must have unique IDs, a specific package, a reviewed version range, explicit opt-in, failure isolation, and tests. Do not use world-readable preferences, blanket hooks or guessed activation state. Version checks and per-process filtering must be enforced by the future runtime dispatcher, not just by the UI. No device actions or production signing are performed by M0.
