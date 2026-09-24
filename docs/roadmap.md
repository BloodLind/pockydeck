# Roadmap

The current 0.10.3 preview provides a usable Android handheld launcher. This is a direction of travel, not a release-date commitment.

## Next priorities

- Broader physical-device and controller coverage: analog axes, long holds, HOME return, lid/sleep/resume, UI scale, and removable-storage changes.
- More verified emulator/version/game-format combinations, with game-boot results distinguished from dispatch tests.
- Complete manual metadata, artwork matching, and correction tools.
- Continued measurement of large-library discovery, accelerated browsing, cover loading, and memory use.
- A durable production signing and distribution process before a stable release.

## After stabilization: Game Profiles

1. Setup/compatibility pages with sources, tested device/emulator versions, and exact game revision identification.
2. A versioned, declarative community profile database offering Quality, Balanced, and Performance presets.
3. Emulator-specific adapters that declare automatic, partial, or instructions-only support.
4. Preview changes, back up the existing per-game configuration, apply, and restore it on request or failure.
5. Verified patch/mod metadata and source links with revision compatibility, conflicts, and checksums. Never promise 60 FPS merely because a patch exists.

Automatic configuration writes and patch downloads are a separate iteration after the launcher release fixes.

## Current limits

- Android 13 or later; landscape handheld layouts are the primary target.
- ROM suffix/folder recognition does not prove file integrity, BIOS/key availability, core installation, or game compatibility.
- RAR, encrypted archives, nested archive extraction, and conversion of emulator-native compressed images are unsupported.
- Several recognized emulators are detection-only. See [compatibility](emulators.md).
- PC games require supported frontend exports; private frontend libraries and arbitrary executables are not imported.
- Home-only blue dots and matching Last played tags identify accepted launches per console; current game/process status is not inferred.
- Temperature reports battery temperature, not chip temperature.
- Optional Libretro cover lookup has no Windows game collection in this integration.
- Runtime data migrations and remaining device-specific behavior need continued testing before a stable release.

Suggest a focused improvement through the [feature request template](https://github.com/BloodLind/pockydeck/issues/new/choose).
