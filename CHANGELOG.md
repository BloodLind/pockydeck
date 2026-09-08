# Changelog

## 0.10.3 — 2026-09-09

First public preview. Includes the Android launcher foundation and the following capabilities developed before the first tagged release:

- Home, Library, Apps, Favorites, Search, Settings, and item details.
- Android application discovery, favorites, successful-launch recency, and optional default-Home setup.
- Populated console-folder discovery, recognized ROM formats, descriptor/disc grouping, emulator preferences, RetroArch core selection, and bounded archive preparation.
- ES-DE metadata/artwork reuse, optional Libretro cover lookup, and GameNative/compatible GameHub Lite frontend exports.
- Controller acceleration and trigger de-duplication, A/B mapping, contextual hints, and soft synthesized sound effects.
- Grid and split-pane list collections, UI/card scaling, reduced motion, compact console filters, and artwork lifetime improvements.
- Device status and optional Shizuku process indicators on Home.

### Latest refinements

- Reduced visible action-button size while preserving 48dp touch targets.
- Replaced the collapsed Search controls with a query action, round Clear icon, and aligned result count.
- Kept three complete visible console categories where space permits, including the end of the filter strip.
- Grouped round list preview actions and refined controller audio during accelerated movement.

### Public repository

- Added Apache 2.0 licensing, attribution, contributor and security guidance, issue/PR templates, and a build workflow.
- Consolidated historical planning/evidence into current setup, development, compatibility, and design docs.
- Replaced third-party debug cover samples with original geometric vectors.
- Published a non-debuggable preview APK signed with the existing development certificate, with SHA-256 checksums.

See [release notes and verification](docs/releases/0.10.3.md) for limitations and packaging details. Earlier development checkpoints remain in Git history; there were no earlier GitHub releases.
