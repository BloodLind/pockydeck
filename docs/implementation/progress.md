# Implementation progress

Updated: 7 September 2026. Coordinator owns this record; story specifications remain the acceptance criteria.

## Baseline and authorization

- Planning baseline: `ad2b668` (`docs: establish handheld launcher planning baseline`).
- Accepted F01 baseline: `ce09ddc` (`feat: establish F01 launcher foundation and shared contracts`).
- Implementation branch: `codex/f01-foundation`.
- User authorized F01 implementation and parallel implementation of features whose prerequisites are satisfied.
- User confirmed a new four-module launcher with Argosy cloned as a local reference for selective reuse.
- Reference: `https://github.com/rommapp/argosy-launcher.git`, local `.reference/argosy-launcher`, revision `25ccb40840e5c53b3592265820c9494745ce8046` (shallow clone). Its source and notices remain in that separate checkout; no application source has been reused.

## Story and feature status

| Feature / story | State | Owner / model | Evidence and next gate |
| --- | --- | --- | --- |
| F01 | Accepted | Sol / high; coordinator integration | All three children accepted; [final build, review and Android 13 gate](evidence/F01/F01-final-gate.md) |
| US-001 | Accepted | `f01_foundation`, gpt-5.6-sol / high; separate Sol review | [Build, lint, tests, dependency and schema evidence](evidence/F01/US-001.md); [Android 13 launch](evidence/F01/US-001-launch.md). All four criteria passed; physical Flip 2 pending |
| US-002 | Accepted | `f01_foundation`, gpt-5.6-sol / high; separate Sol review | [12 passing domain tests and consumer compilation](evidence/F01/US-002.md); [published catalog/launch contracts](contracts.md) |
| US-003 | Accepted | `f01_foundation`, gpt-5.6-sol / high; separate Sol review | [22 total passing tests, build, lint and contract evidence](evidence/F01/US-003.md); final Android 13 smoke passed |
| F02 / US-004 | US-004 accepted; F02 in progress | `f02_theme`, Luna / medium; coordinator integration; Terra review | [Theme, offline/font-scale/motion renders and combined gate](evidence/F02/US-004-theme.md); US-005 ready |
| F06 / US-017 | US-017 accepted; F06 in progress | `persistence_preparation`, Sol / high; separate Sol review | [8 passing Android 13 Room tests, schema and transaction evidence](evidence/F06/US-017.md); US-018 ready |
| F03–F18, excluding F06 | Planned | Per feature packets | Existing dependency gates apply |

## Active ownership

- F01 creation leases are closed. Shared domain/app contracts, Gradle, manifest, DI, and contract publication return to coordinator ownership.
- US-004 theme/font signatures are integrated and reserved. US-005 is ready for a future single F02 writer; US-006 and the parent gate remain pending.
- US-017 catalog repositories and initial schema are integrated and reserved. US-018 is ready for a future single F06 writer; US-019, AppContainer wiring and the parent gate remain pending.
- This checkpoint has no active story writers. Coordinator owns shared changes, acceptance, commits, broad builds and emulator scheduling; future workers receive bounded leases.
- Debug-only foundation Room schemas are compile fixtures, separate from the F06 production database.

## Device evidence

No physical device is connected. An isolated Android 13 emulator was used at `emulator-5556`, 1920×1080 at 240 dpi, then stopped after restoring its settings; see [environment evidence](evidence/F01/emulator-environment.md). US-001 APK installed and opened normally, with its screenshot inspected and no Android runtime error. Physical Retroid Pocket Flip 2 behavior and density calibration remain pending.

## Reviews

- US-001: independent `foundation_review` (Sol / high) found no blocking findings after dependency compatibility, lifecycle request consumption, schema assets, test dependencies, and wrapper checksum fixes. Lint has zero errors and 48 warnings; warnings remain recorded rather than suppressed. Accepted by coordinator on 7 September 2026.
- US-002: independent Sol review found no remaining semantic blocker after inventory preservation/scope, launch identity, global order, collision, and concurrency fixes. Coordinator verified 12 passing domain test results and consumer compile evidence. Accepted on 7 September 2026.

- US-003/F01: independent Sol navigation/input/status/modal review and coordinator Activity-claim review found no remaining blocker. Full gate passed with 22 tests and zero lint errors; final Android 13 install/cold launch/task-return smoke passed. Accepted 7 September 2026.

- US-004: independent Terra review plus coordinator native rendering/contrast/font packaging checks passed. Default and 1.3 font scale rendered offline; reduced motion resolved to zero-duration properties. Accepted 7 September 2026.
- US-017: independent Sol review passed after chunked removal fix; actual Room suite passed 8/8, including 1,005 omissions and reopened cache while discovery remained pending. Accepted 7 September 2026.

## Final combined checkpoint

`assembleDebug lintDebug :core:domain:test :app:testDebugUnitTest :app:processReleaseMainManifest` passed in 34s. JVM tests: 22/22; actual Room Android 13 tests: 8/8; zero failures/errors/skips. Lint has zero errors and 48 existing app warnings, with no data/design-system issues. Release manifest excludes the debug preview Activity. Final ordinary Activity cold launch passed with no AndroidRuntime errors.

APK: `app/build/outputs/apk/debug/app-debug.apk`; SHA-256 `d34ac5e92d3d9b00c8f99fe8b3efc53ca05127412d29e1c53568fd41855ee557`. The UI remains a themed foundation; HOME integration and feature screens are later work. F02 and F06 are not complete: the next ready parallel stories are US-005 (metrics) and US-018 (successful-open ordering).
