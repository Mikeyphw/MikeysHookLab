# M8: modern libxposed recommended scope

Source starting point: HookLab merged M7 main `cdc43be27298196aa925f5bc26533832440e8262`.
Exact app version: 0.6.1 (versionCode 8), signed only after review/merge.

## Metadata
- `META-INF/xposed/java_init.list`: unchanged.
- `META-INF/xposed/module.prop`: unchanged, `staticScope=false`.
- `META-INF/xposed/scope.list`: one line each: `br.art.code.meucronogramacapilar`, `com.machiav3lli.fdroid`, `com.looker.droidify`.
- **Not scoped**: ObtainX `dev.bikram.obtainx` because it is a destination only.
- No legacy manifest `xposedscope`, process whitelist expansion or bulk scope grants.

## Gates
- [ ] Native Devtool source-pinned apply and draft PR.
- [ ] Exact-head hosted Android compile, lint, JUnit and APK source provenance.
- [ ] Inspect actual APK ZIP and verify resource path and exact three-line scope.
- [ ] PR merge and postmerge Android CI on exact commit.
- [ ] Stable-signed 0.6.1 with **existing** HookLab certificate, no key rotation.
- [ ] On device, LSPosed Vector displays three recommended host packages.
- [ ] User approves desired scope; HookLab Scope reflects actual framework state.
- [ ] HookLab Tweaks remain independently opt-in, no automatic enablement.
- [ ] With only one module active per host, verify MCC button, Neo Store icon and Droid-ify menu; negative and lifecycle cases.

DEVICE_ACCEPTANCE=NOT_PERFORMED
RELEASE_QUALIFICATION=NOT_PERFORMED


## Verbose LSPosed event logging (included in M8)

- [ ] Open HookLab **Settings**; verify **Verbose LSPosed logging** is off by default.
- [ ] Before framework service connects, verify switch is unavailable (no local fallback).
- [ ] With API102 service, enable the switch and verify successful remote preference commit.
- [ ] Restart MCC, Neo Store and Droid-ify; verify that new `HookLabVerbose event=...`
      debug entries appear in LSPosed module logs when the corresponding tweak is active.
- [ ] Confirm stage codes include `MCC_BUTTON_ATTACHED`, `NEO_PAGE_READY`, or
      `DROID_TOOLBAR_ADDED` under appropriate runtime conditions.
- [ ] Disable the switch, restart the targets, and confirm no further verbose events.
- [ ] Confirm baseline warnings/failures remain logged independently of verbose mode.
- [ ] Check logs contain no package IDs, account details, repository URLs, source-site
      metadata, credentials, host file paths, exception messages or stack traces.
- [ ] Verify at most one same-stage verbose message within 3 seconds; no excessive polls.
- [ ] Hosted JUnit and lint must pass on the exact PR commit. Device acceptance remains pending.

Note: LSPosed or the active framework can filter DEBUG messages, so a missing
`HookLabVerbose` message is not proof of a faulty preference write. Observe
scope, module activation, and log-level settings independently.
