# Mikey's HookLab

Small hooks. Better apps.

A modular Android app and modern Xposed API 102 module for **opt-in**, app-specific tweaks. M1–M3 adds an actual libxposed service-backed framework connection, managed scope, a read-only framework status page, a guarded runtime dispatcher, and remote per-tweak preferences. **There are still zero registered app hooks and no default scope.**

## Architectural boundaries

- `HookLabApp` registers exactly one `XposedServiceHelper` listener. A live binder shows that framework service is connected; it is **not** proof that a host app is hooked.
- `MainActivity` shows the framework name/version, actual module scope and registered tweaks. Scope changes require explicit user action and framework approval. Only packages registered in `TweakCatalog` can be requested or removed by the UI.
- Per-tweak settings live in the **framework-owned** `hooklab.tweaks.v1` remote-preference group. The UI requires a connected framework, and writes show success only after a successful commit. No local mirror or world-readable settings.
- The host `HookLabModule` uses modern API 102. It ignores system server, unscoped process names and unrelated loaded packages. When a matching package is registered it installs a single `Application.attach` interceptor, verifies its actual package version using the public `PackageInfo.getLongVersionCode()`, and dispatches only if the registry, process policy, version and explicit remote opt-in all agree.
- `TweakEngine` is pure Java. Failures from settings or individual installers are isolated, while failures from the host's original methods are allowed to propagate normally. Framework-fatal errors are not swallowed.
- `TweakCatalog` is deliberately empty. Adding a real tweak requires a separate reviewed registration, precise supported versions, tests and explicit scope consent. No sample hook is present in the shipping app.
- The libxposed service provider is explicitly declared with `${applicationId}.XposedService`, and its pre-unlock binder callback does not open credential-protected preferences.

## Build and verification

GitHub Actions runs `python3 -B scripts/check_contract.py`, `:app:assembleDebug`, `:app:testDebugUnitTest`, and `:app:lintDebug`, followed by APK metadata/signature and provenance verification. GitHub SDK setup remains `android-actions/setup-android@v4.0.4` with command-line tools `15859902`, explicit SDK 36 and no deprecated `tools` package. Java 17 source / JDK 21 hosted, Gradle 8.13, AGP 8.13.2, compileSdk 36. Remote service 102.0.0 is the known published artifact; API 102.0.0 is `compileOnly`.

Native Devtool handles the source-bound transaction, clean worktree checks, commitment, PR delivery and session recovery. Hosted compile, unit, lint, APK metadata and device tests must be independently verified before considering the milestone accepted. Neither a successful source contract nor a PR by itself qualifies a release. No production signing, auto-merge, reboot, device writes, or root privileges are performed by this overlay.
