# WM01 — Word Master 0.6.10 opt-in night filter (experimental)

## Source/target identity
- Supplied archive: `Word Master_0.6.10.apk.zip` SHA-256 `bd85b0c435a6574022d36f357d45eb1d303fa413e404ba04a7d2be0fdeab035f`.
- Inner base APK: `com.fugo.wowsolitaire`, versionName `0.6.10`, versionCode `56240403`.
- Launcher: `com.unity3d.player.UnityPlayerActivity`; Unity IL2CPP assets and global metadata present.
- Manifest requires ABI split (`requiredSplitTypes=base__abi`); supplied APK alone has no `libunity.so` or `libil2cpp.so`.
- Metadata contains `NativeData.get_IsDarkModeOn`, but no proof the Unity scene honors the flag or exposes a Java theme setter.

## Actual implementation boundary
- Opt-in id `wordmaster.night.filter`, default off using HookLab framework-owned preferences.
- Same guarded API102 dispatch, main-process and version checks, manual scope approval, safety lock.
- Hook exactly `UnityPlayerActivity.onResume` after original callback; insert touch-transparent, accessibility-hidden, neutral-navy 39% opacity scrim once per decor view. Additional `onResume` only brings existing view to front. No timers or polling.
- The visual overlay makes the game *dimmer*. It does not recolor Unity meshes, textures, fonts, or buttons; calling it a genuine in-game dark palette would be incorrect.
- On disable, restart Word Master to remove the process-local overlay. It does not save files, modify app data, block touches intentionally, tamper with ads or alter licensing.

## Acceptance matrix
- [ ] Exact source & preimages verified against current main; Devtool native check and apply completed.
- [ ] Hosted compile, JUnit and lint passed at the resulting exact PR head.
- [ ] APK scope list includes Word Master only as opt-in recommendation; ObtainX is never injected.
- [ ] On Android 16 with original base+ABI split installed, enable HookLab scope + toggle, restart game: overlay visible; all taps/swipes and gameplay remain functional.
- [ ] Rotate, background/resume, open ads/settings, return: one overlay, no flicker or leaks.
- [ ] Disable switch and restart: overlay absent.
- [ ] Wrong Word Master version/other packages: no hook installed.
- [ ] Test whether `NativeData.IsDarkModeOn` influences actual Unity palette. If not, a true theme requires a separately verified, version-pinned Unity/IL2CPP palette hook; this overlay is not that hook.

DEVICE_ACCEPTANCE=PENDING
GENUINE_UNITY_DARK_PALETTE=NOT_IMPLEMENTED
STABLE_RELEASE=NOT_PERFORMED
