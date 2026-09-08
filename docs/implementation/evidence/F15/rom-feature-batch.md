# ROM feature batch — 8 September 2026

The user approved broad popular ROM recognition from named folders, game-backed console lists, automatic compatible emulator discovery, a chooser/default per console, selectable ROM items, and automatic extraction where needed. The former single-platform readiness proposal is superseded by this direction. Metadata/provider selection remains outside this batch.

## Implementation

- The pure planner recognizes 56 families and conservatively groups descriptors/playlists, tracks and package folders. Unknown console assignments remain selectable; damaged sets carry an independent repair requirement.
- Room version 3 adds ROM sources, source/document-to-generated-item identity, preferences and console/format projection. Migration retains the Android catalog and all user references. A source revision prevents an older scan from resurrecting removed or reconfigured sources. Incomplete work never performs final omission reconciliation.
- SAF queries provide source grants, identities and off-main enumeration. Source health and scan progress are visible in Settings. Startup/resume and bounded durable WorkManager recovery request coalesced reconciliation.
- Library filters and emulator setting rows derive from detected readable games. Details provides console/app overrides. The existing acknowledged launch pipeline still owns origin snapshots, recency and operation deduplication; chooser cancellation is an explicit non-success outcome.
- Native checks exposed clipped collection cover captions and restoration to an implicit scrolling focus target. ROM cards now reserve their caption height, and shared controls publish an exact opener-focus callback for modal restoration with both controller and touch activation.
- Emulator profiles use documented/source-verified intent contracts with installed exported-component checks and immediate content revalidation. See [F16 matrix](../F16/emulator-contracts.md) for scope and actual evidence limits.
- Extraction uses Commons Compress 1.28.0 and XZ for Java 1.10, quotas, dictionary-memory limits, path/link checks and cancellation. Immutable completed cache trees are served by a read-only DocumentsProvider. Reservations survive process restart; clearing all reserved copies requires the user to confirm emulators are closed.
- Generic archives are prepared before content selection even if the emulator accepts ZIP, preserving the chooser for multi-game archives. Native arcade/DOS containers are retained. A prepared archive with a damaged descriptor or playlist stops with its repair reason rather than launching a surviving track.

## Validation record

The final result is **234 passing tests**: 149 JVM and 85 Android instrumentation tests, with zero failures/errors/skips. APK assembly, explicit migration checks, release-manifest inspection and lint passed; lint reports zero errors, 71 warnings and eight informational findings. The final app rerun also includes a bounded wait for Android's asynchronous keyboard dismissal in the existing Search integration test. See [commands, counts and APK checksum](verification.txt).

The actual Android folder picker indexed five synthetic games, grouped the CUE/BIN set, exposed the unassigned ISO and omitted an empty SNES folder. The GBA filter showed its two games. Grant/library state survived an application force-stop/restart; cancelling the folder picker retained the existing source. The [native Library capture](rom-library-native.png) shows the corrected titles and console captions at 1920×1080/360dpi.

Version **0.2.0 (code 2)** was installed with `adb install -r` on the connected Flip 2. Android accepted MainActivity startup and the process had no AndroidRuntime errors. The physical device remained in its dozing/keyguard state, so no fresh physical visual comparison is claimed. Stable APK: `.local/releases/handheld-launcher-rom-debug.apk`.

Actual physical storage removal/reboot/core/game-boot combinations remain separate evidence requirements; a successful intent dispatch does not establish gameplay or emulator liveness. RAR, encrypted/nested archives and the documented unsupported variants require manual preparation. Metadata provider selection remains later work.

References: [recognition and grouping](format-support.md), [archive extraction](archive-extraction.md), [setup/recovery](../../../rom-setup-guide.md).
