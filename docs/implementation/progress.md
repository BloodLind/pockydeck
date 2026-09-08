# Implementation progress

Updated: 8 September 2026. Coordinator owns this record; story specifications remain the acceptance criteria.

## Current delivery workflow

The user explicitly changed the workflow on 8 September: build the complete application base first, then reconcile it with the documentation and fix findings. Per-story acceptance and wave-order gates no longer block implementation during this run. Large integrated build, logic, visual and device checks replace the former test-after-every-story cadence. Source ownership and truthful evidence still apply; older accepted checkpoints remain historical evidence only.

The native application batch replaces the foundation entry point with the production Home shell and all six destinations, shared catalog actions, native controller input, settings, status and HOME-role setup. See the [batch evidence and documentation reconciliation](evidence/F14/native-application-base.md) for actual verification and remaining physical checks.

The subsequent ROM batch delivers version **0.2.0**: 56-family folder/format recognition, selectable ROMs, console-derived Library filters, emulator/core preferences and bounded automatic archive preparation. **234 tests pass** (149 JVM, 85 Android); lint has zero errors. The APK was installed on the connected Flip 2; its dozing/keyguard state prevented a fresh physical screen comparison. See [ROM batch evidence](evidence/F15/rom-feature-batch.md) for exact coverage, checksum and remaining physical/game-boot checks.

The combined revision delivers **0.3.1 (code 4)** with the queued touch/focus, page membership, Home ordering, Search controller/collapse and console-color requests. The user's correction retains original design proportions and increases only small text/glyphs by 15%. Explicitly approved All files access adds read-only discovery on shared internal, SD and USB storage, with an additive Room 3→4 migration and preserved source/game identities. **301 tests passed** (188 JVM, 113 Android); one Windows symlink fixture skipped, lint zero errors, and debug/release builds passed. The corrected APK is installed on the connected Flip 2, which remains dozing. [UI and storage evidence](evidence/F14/ui-storage-revision.md) records verification, rendered screens and remaining device limits.

The subsequent **0.3.2 (code 5)** icon correction imports the HTML preview's exact Material Symbols, including the lighter weight-200 dock outlines. It preserves the corrected layout and small-control scaling. **20 native design-system tests**, lint (zero errors), and both APK builds passed. The update is installed on the Flip 2, and the awake physical screen was inspected. See [icon revision evidence](evidence/F14/icons-032.md).

The **0.3.3 (code 6)** revision increases the smallest text with the user's 3→6sp and 9→11sp examples, tapering the adjustment to zero at 14sp. Library filters now use the preview's compact counted pills, L2/R2 hints and a More chooser with the exact lighter expand-more symbol. Physical checks exposed and fixed modal focus and focus restoration when filtering out the active card. Known game frontends are correctly classified as Apps. Approved All files access is enabled on the Flip 2, and Library displays **3,773 ROMs**. **152 tests passed**, one Windows fixture skipped; lint has zero errors and both builds passed. Android validation used the physical Flip 2 only. [Revision evidence](evidence/F14/text-filters-033.md) records normal/130% text captures and remaining checks.

The **0.4.0 (code 7)** artwork revision reuses ES-DE covers and downloads missing ROM artwork from Libretro's public thumbnail collection, explicitly approved by the user. A separate persistent artwork queue, bounded cache, lazy bitmap decoding and small card hints preserve the catalog and existing card layout. Artwork settings provide pause/resume and retry. Real downloads for all 22 Jaguar games were verified on the physical Flip 2. [Artwork evidence](evidence/F17/artwork-040.md) records the request contract, final checks and remaining acceptance coverage.

The user approved GameNative as the PC integration, with GameHub also acceptable. The **0.5.0 (code 8)** revision imports GameNative's Steam, Epic, GOG, Amazon and custom-game exports as PC library cards, reuses ES-DE media, and dispatches source-specific game IDs. Compatible GameHub Lite packages can handle Steam exports through the existing app chooser and console preferences. The catalog schema is unchanged. The physical Flip2 imported three existing PC games, and Half-Life 2 reached its main menu through GameNative. **262 unique tests passed**, one Windows fixture skipped; lint has zero errors and both APK builds passed. The update is installed. See [PC integration evidence](evidence/F16/pc-integration-050.md) for exact build coverage and remaining limits.

The **0.6.0 (code 9)** queued-controls revision keeps the original shell and light icons, moves Library filters beside its title, adds a cyclic strip and console grid chooser, and puts type tags on smaller collection artwork with wider two-line captions. Home is capped at 20 entries. Held triggers, sticks and D-pad inputs accelerate; digital/analog trigger reports share one engagement, and horizontal navigation continues across grid rows. Touch hides controller focus, with the first controller input restoring a card. Search keeps a real empty state, publishes cancellable result batches, and supports mapped and touch Apply/Cancel above the landscape keyboard.

The same batch moves preparation away from selection/scroll updates, publishes each completed discovered ROM source in batches without immediately rescanning it, and avoids rewriting unchanged entries. Unidentified games stay out of main collections until corrected through ROM-folder Settings. Bounded ES-DE console evidence can fill unresolved identity only when paths prove the match. Recent activity badges identify the dispatched emulator for ROMs and expire honestly; optional Usage Access adds app observations. Current Wi-Fi and telemetry icons reflect their values. See [0.6 verification and reconciliation](evidence/F14/queued-controls-060.md) for checks, installed artifact and remaining limits.

Version 0.6.0 is installed on the Flip 2 without resetting its library. **343 tests passed** (244 JVM and 99 Android), with one Windows-only fixture skipped; lint has zero errors and both APK builds passed. The final main-app device class passed all four tests, including native mixed-trigger timing, row wrapping and Search touch/controller editing. Home, Library, the filter chooser, inline keyboard and 130% system text were inspected; the device's font scale was restored to 1.0. The final SHA-256 and exact coverage are in the linked evidence.

Full rich metadata import, manual artwork/match correction and the complete emulator/game-boot matrix remain later work. The large-catalog discovery/search work requested in this batch is implemented; no universal storage-scan time is promised.

## Baseline and authorization

- Planning baseline: `ad2b668`; accepted F01 baseline: `ce09ddc`; previous theme/catalog checkpoint: `b9e68d2`.
- Implementation branch: `codex/f01-foundation`.
- User authorized F01 and parallel implementation of ready features, then requested continuation. The previous continuation completed F02/F06. The user now explicitly requests sustained implementation of the Home page and all controls until the actual app matches the Home design, opening the ready F03/F07 → F04 → F05 → F08 milestone without stopping after each wave.
- User confirmed a new four-module launcher with Argosy as a cloned reference for selective reuse. The local reference is `.reference/argosy-launcher`, upstream `https://github.com/rommapp/argosy-launcher.git`, revision `25ccb40840e5c53b3592265820c9494745ce8046`. Source/notices remain separate; no application source has been reused.

## Story and feature status

| Feature / story | State | Owner / review | Evidence |
| --- | --- | --- | --- |
| F01 | Accepted | Sol/high; coordinator integration | [Final F01 gate](evidence/F01/F01-final-gate.md) |
| US-001 | Accepted, 7 September | Sol/high; separate Sol review | [Build/schema](evidence/F01/US-001.md), [Android 13 launch](evidence/F01/US-001-launch.md) |
| US-002 | Accepted, 7 September | Sol/high; separate Sol review | [Catalog/launch contracts and tests](evidence/F01/US-002.md) |
| US-003 | Accepted, 7 September | Sol/high; separate Sol review | [Navigation/input/status/Activity ports](evidence/F01/US-003.md) |
| F02 | Accepted, 8 September | Luna/medium; coordinator integration; Terra review | All children and [combined feature gate](evidence/F02/F02-F06-final-gate.md) passed |
| US-004 | Accepted, 7 September | Luna/medium; coordinator integration; Terra review | [Theme/font/offline/motion evidence](evidence/F02/US-004-theme.md) |
| US-005 | Accepted, 8 September | Luna/medium; coordinator geometry integration; Terra review | [9 metrics tests and native fixtures](evidence/F02/US-005-metrics.md) |
| US-006 | Accepted, 8 September | Luna/medium; coordinator source/test integration; Terra review | [4 native focus/semantics/layout tests and renders](evidence/F02/US-006-primitives.md) |
| F06 | Accepted, 8 September | Sol/high; separate Sol review; coordinator DI | All children and [combined feature gate](evidence/F02/F02-F06-final-gate.md) passed |
| US-017 | Accepted, 7 September | Sol/high; separate Sol review | [Actual Room inventory/cache preservation](evidence/F06/US-017.md) |
| US-018 | Accepted, 8 September | Sol/high; separate Sol review | [Recency transactions, migration and concurrency](evidence/F06/US-018.md) |
| US-019 | Accepted, 8 September | Sol/high; separate Sol review; coordinator DI | [Actual DataStore round-trip and recovery](evidence/F06/US-019.md) |
| F03 / US-007 | Accepted | Luna/medium; Terra review; coordinator integration | [Native controls evidence](evidence/F03/US-007-controls.md) |
| F03 / US-008 | Accepted | Luna/medium; coordinator integration; Terra review | [Native card tests and inspected variants](evidence/F03/US-008-cards-labels.md) |
| F03 / US-009 | Accepted | Luna/medium; coordinator integration; Terra review | [6 native layout/settings/modal tests](evidence/F03/US-009-layouts.md) |
| F03 / US-010 | Accepted | Luna/medium; coordinator integration; Terra review | [Inspected native gallery and Home variants](evidence/F03/US-010-gallery.md) |
| F03 | Accepted | Coordinator integration and separate review | [52 JVM + 48 native checks and visual gate](evidence/F03/F03-F07-final-gate.md) |
| F07 / US-020 | Accepted | Sol/high; separate Sol review; coordinator physical capture | [Real Android discovery and Flip 2 inventory](evidence/F07/US-020.md) |
| F07 / US-021 | Accepted | Sol/high; separate Sol review | [8 JVM + 3 native Room/receiver tests](evidence/F07/US-021.md) |
| F07 / US-022 | Accepted | Sol/high; separate Sol review; coordinator DI | [5 JVM + 3 native dispatch/task tests](evidence/F07/US-022.md) |
| F07 | Accepted | Sol/high; separate Sol review; coordinator integration | [Combined app build and feature gate](evidence/F07/F07-final-gate.md) |
| F04 / US-011–013 | Implemented in native batch | Terra/high; coordinator integration | One shell, six routes, origin navigation and landscape IME layout |
| F05 / US-014–016 | Implemented; physical controller acceptance pending | Sol/high; coordinator integration | Input ownership/repeat, mapping, modal and stable-ID restoration |
| F08 / US-023–025 | Implemented; physical HOME/lid acceptance pending | Sol/high; coordinator integration | Real Home carousel, acknowledged launch/return and user-controlled HOME setup |
| F09–F12 / US-026–035 | Implemented in native batch | Terra/high; coordinator integration | Library/Apps, Favorites/Details, Search/IME and Settings |
| F13 / US-036–037 | Implemented in native batch | Sol/high review; coordinator integration | Actual lifecycle-bound device status |
| F14 / US-038–039 | Integrated checks; physical matrix partial | Coordinator and Sol/high review | [Native application evidence](evidence/F14/native-application-base.md) |
| F15 / US-040–043 | Implemented in broad ROM batch; physical source matrix pending | Parallel planner/UI work; coordinator storage/integration | [ROM batch](evidence/F15/rom-feature-batch.md), [format support](evidence/F15/format-support.md), [extraction](evidence/F15/archive-extraction.md) |
| F16 / US-044–046 | Implemented adapters and emulator/core choice; actual game-boot matrix partial | Parallel contract research; coordinator launch integration | [Emulator contracts and versions](evidence/F16/emulator-contracts.md), [GameNative PC integration](evidence/F16/pc-integration-050.md) |
| F17 / US-047–048 | Artwork implemented; acceptance coverage partial | Coordinator integration | [ES-DE reuse, approved Libretro downloads and device evidence](evidence/F17/artwork-040.md) |
| F17 / US-049 | Planned | Per feature packet | Full manual metadata/match/artwork correction remains |
| F18 / US-050–053 | Later work | Per feature packet | Final physical delivery gates remain |

## Ownership and handoff

F01–F03/F06/F07 have accepted historical checkpoints, most recently `28384f6`. The native batch integrates F04/F05/F08–F14 across disjoint agent leases. Coordinator owns shared contracts, Gradle, manifests, DI, calibration, integration, device scheduling and commits. Room schema/migrations and persisted key encoding were preserved. See [published contracts](contracts.md).

`LauncherApplication` supplies its application context to one lazy `AppContainer`. It shares one Room database across catalog/favorite/override/recency repositories and one preferences store across controller/snapshot repositories. Setup does not scan packages. Debug-only foundation schema fixtures and repository fakes remain separate from production persistence.

## Previous F02/F06 combined checkpoint

[Full command, review and smoke evidence](evidence/F02/F02-F06-final-gate.md): build, lint, JVM tests, release manifest and actual Android storage/UI tests passed in 54s. JVM: 31/31 (15 domain, 7 app, 9 metrics). Android 13: 27/27 (23 storage, 4 native UI). Zero failures/errors/skips. Lint: zero errors and 48 existing app warnings, with no data/design-system issues.

US-019 review fixes cover wrong-type values and exact filenames; the final run includes both obsolete and wrong-type versions and preserves unrelated valid state. US-006 review fixes cover default target sizing; its tests request 24dp and verify at least 48dp, no adjacent overlap, actual focus/pixels, stable allocation and meaningful/decorative semantics.

APK: `app/build/outputs/apk/debug/app-debug.apk`; SHA-256 `874b1f1f3df62fcee88214e9a4d75c95d1667945704eabb7f6332af94801bffe`. Final install and ordinary Activity cold launch passed with no AndroidRuntime errors. Release manifest includes none of the debug preview/test Activities. MainActivity remains a themed foundation; production controls, catalog discovery, HOME behavior and feature screens remain later work.

## Previous F02/F06 device evidence and remaining gates

No physical device was connected at that checkpoint. The isolated Android 13 emulator at `emulator-5556`, 1920×1080/240dpi, was used for Room/DataStore and native Compose checks. Standard, compact, alternate-destination, 1.3 font scale and reduced-motion evidence was inspected. Settings were restored and the emulator stopped after the final smoke.

Physical Retroid Pocket Flip 2 display/density, controller, lid and HOME lifecycle acceptance remains pending. Emulator acceptance does not close physical gates or permit downstream stories to bypass their explicit prerequisites.

## Earlier Home milestone baseline

Starting source checkpoint: `bc60032`. The user reports the existing APK runs on the actual device and requests the complete designed Home experience. The connected Flip 2 reports Android 13, landscape 1920×1080, 360dpi (density 2.25), and font scale 1.0. The former 240dpi emulator assumptions require native token/metrics calibration at 853.33×480dp in immersive mode. The foreground was verified as our MainActivity before capturing the foundation baseline in ignored `.local/physical`. Controller, lid and HOME-role acceptance is not inferred from connectivity or the user's initial smoke. Role choice remains user-controlled; no automatic default-HOME change is authorized.
