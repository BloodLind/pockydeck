# Implementation progress

Updated: 8 September 2026. Coordinator owns this record; story specifications remain the acceptance criteria.

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
| Remaining F03–F18 stories | Planned | Per feature packets | Existing dependency and physical-device gates apply |

## Ownership and handoff

F01, F02 and F06 creation leases are closed. F03 and F07 creation leases are closed. F04 is ready for its first story. Coordinator owns physical Home calibration, shared deltas, device scheduling and later gate dispatch. Coordinator owns shared contracts, Gradle, manifests, DI, status and commits. Accepted theme/metrics/primitives, Room v1/v2 exports and migration, DataStore keys/encoding, and repository constructors are reserved; later changes use the reviewed shared-file process. See [published contracts](contracts.md).

`LauncherApplication` supplies its application context to one lazy `AppContainer`. It shares one Room database across catalog/favorite/override/recency repositories and one preferences store across controller/snapshot repositories. Setup does not scan packages. Debug-only foundation schema fixtures and repository fakes remain separate from production persistence.

## Previous F02/F06 combined checkpoint

[Full command, review and smoke evidence](evidence/F02/F02-F06-final-gate.md): build, lint, JVM tests, release manifest and actual Android storage/UI tests passed in 54s. JVM: 31/31 (15 domain, 7 app, 9 metrics). Android 13: 27/27 (23 storage, 4 native UI). Zero failures/errors/skips. Lint: zero errors and 48 existing app warnings, with no data/design-system issues.

US-019 review fixes cover wrong-type values and exact filenames; the final run includes both obsolete and wrong-type versions and preserves unrelated valid state. US-006 review fixes cover default target sizing; its tests request 24dp and verify at least 48dp, no adjacent overlap, actual focus/pixels, stable allocation and meaningful/decorative semantics.

APK: `app/build/outputs/apk/debug/app-debug.apk`; SHA-256 `874b1f1f3df62fcee88214e9a4d75c95d1667945704eabb7f6332af94801bffe`. Final install and ordinary Activity cold launch passed with no AndroidRuntime errors. Release manifest includes none of the debug preview/test Activities. MainActivity remains a themed foundation; production controls, catalog discovery, HOME behavior and feature screens remain later work.

## Previous F02/F06 device evidence and remaining gates

No physical device was connected at that checkpoint. The isolated Android 13 emulator at `emulator-5556`, 1920×1080/240dpi, was used for Room/DataStore and native Compose checks. Standard, compact, alternate-destination, 1.3 font scale and reduced-motion evidence was inspected. Settings were restored and the emulator stopped after the final smoke.

Physical Retroid Pocket Flip 2 display/density, controller, lid and HOME lifecycle acceptance remains pending. Emulator acceptance does not close physical gates or permit downstream stories to bypass their explicit prerequisites.

## Current Home milestone

Starting source checkpoint: `bc60032`. The user reports the existing APK runs on the actual device and requests the complete designed Home experience. The connected Flip 2 reports Android 13, landscape 1920×1080, 360dpi (density 2.25), and font scale 1.0. The former 240dpi emulator assumptions require native token/metrics calibration at 853.33×480dp in immersive mode. The foreground was verified as our MainActivity before capturing the foundation baseline in ignored `.local/physical`. Controller, lid and HOME-role acceptance is not inferred from connectivity or the user's initial smoke. Role choice remains user-controlled; no automatic default-HOME change is authorized.
