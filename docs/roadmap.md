# Roadmap

The current 0.10.3 preview provides a usable Android handheld launcher. This is a direction of travel, not a release-date commitment.

## Next priorities

- Broader physical-device and controller coverage: analog axes, long holds, HOME return, lid/sleep/resume, UI scale, and removable-storage changes.
- More verified emulator/version/game-format combinations, with game-boot results distinguished from dispatch tests.
- Complete manual metadata, artwork matching, and correction tools.
- Continued measurement of large-library discovery, accelerated browsing, cover loading, and memory use.
- A durable production signing and distribution process before a stable release.

## Current limits

- Android 13 or later; landscape handheld layouts are the primary target.
- ROM suffix/folder recognition does not prove file integrity, BIOS/key availability, core installation, or game compatibility.
- RAR, encrypted archives, nested archive extraction, and conversion of emulator-native compressed images are unsupported.
- Several recognized emulators are detection-only. See [compatibility](emulators.md).
- PC games require supported frontend exports; private frontend libraries and arbitrary executables are not imported.
- Live process badges require optional Shizuku and appear only on Home. An emulator process does not establish which ROM is playing.
- Temperature reports battery temperature, not chip temperature.
- Optional Libretro cover lookup has no Windows game collection in this integration.
- Runtime data migrations and remaining device-specific behavior need continued testing before a stable release.

Suggest a focused improvement through the [feature request template](https://github.com/BloodLind/pockydeck/issues/new/choose).
