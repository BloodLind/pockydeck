# Controller feedback and filter readability — 0.9.1

Date: 8–9 September 2026. Follow-up to the user's four requests after 0.9.0.

## Delivered behavior

- Filter cells gain horizontal/vertical padding (10/4 → 12/6 reference units) and an 8-unit corner radius. Rendering and header width measurement share their geometry. Native touch targets stay at least 48dp.
- Inspection of the user's PSX/Recent/List view found SMS's neighboring count clipped by the old strip width. The strip now sizes a complete window from its actual starting category and assigns the same rounded pixel widths to its cells. Touch scrolling settles on a complete boundary without applying a filter; selected filters remain reachable through L2/R2. D-pad traversal excludes offscreen controls as before.
- START draws at 85% within its existing footer hint. Other legends and the footer allocation keep their size.
- Running labels are supplied only within Home. Collection and Search cards ignore process labels; Details remains outside the provider even when it uses Home-style artwork.
- Five original, synthesized controller cues cover movement, confirmation, back, page and filter changes. Settings → Controls → Controller sounds defaults on and persists independently of catalog data. The reproducible generator is `scripts/audio/Generate-ControllerSounds.ps1`.
- Sound uses normal media volume, not Android touch-sound settings. The Flip 2 had system sounds muted and media volume at 3/15 during inspection; neither was changed. There is no audio-focus request or global sound/DND change. Playback follows Android's media interruption and routing policy.
- Feedback wraps normalized semantic input, preserving digital/analog trigger de-duplication and accelerated holds. Touch, ordinary native text editing and unhandled input stay silent. Pause, focus loss, disable and destruction stop remaining clips. Samples that have not finished loading are skipped without replay.

Audio integration follows Android's [SoundPool](https://developer.android.com/reference/android/media/SoundPool) and [AudioAttributes](https://developer.android.com/reference/android/media/AudioAttributes) contracts. The five PCM assets total 30,912 bytes, last 38–105ms, and have peaks under 26% before the playback gain.

## Exact-ROM feasibility

The read-only check found PPSSPP and RetroArch processes on the Flip 2. Both `/proc/766/fd` (PPSSPP) and `/proc/1851/fd` (RetroArch) returned `Permission denied` to the USB shell. The installed Shizuku helper also runs in shell mode. The launcher retains its already approved package-name-only helper contract; no file inspection, root access or remote-control endpoint was added.

An open descriptor would establish that a file is open, not necessarily that its ROM is the active game; an emulator can also load content into memory and close it. See the [Linux descriptor access rules](https://man7.org/linux/man-pages/man5/proc_pid_fd.5.html) and [libretro content contract](https://github.com/libretro/libretro-common/blob/master/include/libretro.h).

RetroArch's [network commands](https://docs.libretro.com/development/retroarch/network-control-interface/) and PPSSPP's [developer interface](https://www.ppsspp.org/docs/development/developer-tools/) are optional remote-control/debugging facilities, not launch acknowledgements or universal Android current-content APIs. Defaults and implementations were reviewed: [RetroArch configuration](https://github.com/libretro/RetroArch/blob/master/config.def.h), [RetroArch status command](https://github.com/libretro/RetroArch/blob/master/command.c), [PPSSPP configuration](https://github.com/hrydgard/ppsspp/blob/master/Core/Config.cpp), [PPSSPP game status](https://github.com/hrydgard/ppsspp/blob/master/Core/Debugger/WebSocket/GameSubscriber.cpp). These were not enabled. Process badges continue to name the emulator without asserting the specific ROM is playing.

## Validation

**167 distinct tests pass:** 110 app JVM tests, 12 shared-design JVM tests, 22 app Android tests and 23 shared-design Android tests. The physical device is the Retroid Pocket Flip 2, Android 13, serial `89a34d44`, 1920×1080 at density 2.25. No emulator was used for this revision.

The combined build passed in 48 seconds (`.local/verification-091.log`): app/design JVM tests, debug APK, release APK, test APKs and lint. Lint has zero errors, 65 warnings and 10 informational findings. The app's combined native run took 192 seconds (`.local/main-device-091.log`): 21 checks passed immediately; the newly adapted Home ROM badge fixture incorrectly retained the old “RetroArch (64-bit) running” label. Production already removes architecture suffixes. Correcting the fixture to the actual “RetroArch running” label produced a passing focused run (`.local/badge-device-091.log`, 1.438 seconds), without changing the app binary. All 23 shared-design native tests pass (`.local/design-device-091.log`).

Coverage includes real Window input dispatch, trigger hysteresis/hold acceleration/release, touch/controller focus, Search clear/cancel, page/modal Back, Home reset, Grid/List switching, scaled header alignment, complete later/tail filter cells after touch and resizing, font-scale filter padding and Home-only badge visibility. Device runs explicitly install with `adb install -r`; `:app:connectedDebugAndroidTest` was not used against the populated launcher.

Manual inspection verified the populated 3,768-game filter chooser, PSP's Library card without a running badge, full PSX (1)/SMS (54) labels at 110%, the smaller footer START legend, the Controls toggle, Details without a badge and Home with live RetroArch/PPSSPP/app badges. The Library was restored to the user's PSX/Recent/List selection and verified after restarting only the launcher. UI scale remains 110%. Final screenshots are `.local/launcher-091-library-final.png`, `launcher-091-library-psp.png`, `launcher-091-controls.png`, `launcher-091-controls-off.png`, `launcher-091-details.png` and `launcher-091-home-final.png`.

The sound preference was disabled through Settings, verified after a launcher process restart, then restored to enabled. Android's playback records for the same SoundPool player show no start after controller input while disabled and a start immediately after reenabling (`.local/audio-off-device-091.log`, `.local/audio-on-device-091.log`). Media volume and Android touch sounds were unchanged. This verifies playback dispatch and toggle behavior; subjective sound quality is not inferred from logs.

The final app process has no AndroidRuntime crash; the error-level log contains two graphics-driver initialization messages (`.local/runtime-091.log`). No database migration or ROM/save/frontend-file changes are part of this revision. The device is left on Home's first card, in touch mode, with controller sounds and the previously approved running indicators enabled.

## Delivered artifact

- Version **0.9.1**, code **13**.
- `.local/releases/handheld-launcher-0.9.1-debug.apk`, 29,922,151 bytes.
- SHA-256: `c297b362ea06b83fa3bd1a93e0a7d13480b53e3443ad992685b25e70ed7f80e5`.
- Installed package version and on-device `base.apk` checksum match this artifact.
