# Implementation progress

Updated: 7 September 2026. Coordinator owns this record; story specifications remain the acceptance criteria.

## Baseline and authorization

- Planning baseline: `ad2b668` (`docs: establish handheld launcher planning baseline`).
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
| F02 / US-004 | Ready | gpt-5.6-luna / medium | F01 accepted; theme implementation may start after the reviewed baseline commit |
| F06 / US-017 | Ready | `persistence_preparation`, gpt-5.6-sol / high | F01 accepted; catalog persistence may start after the reviewed baseline commit |
| F03–F18, excluding F06 | Planned | Per feature packets | Existing dependency gates apply |

## Active ownership

- F01 creation leases are closed. Shared domain/app contracts, Gradle, manifest, DI, and contract publication return to coordinator ownership.
- F02/US-004 has the theme/font/notice and corresponding evidence paths in its packet. No shell, metrics or primitive implementation is included in this first story.
- F06/US-017 has the local Room/catalog repository/schema and matching test/evidence paths in its packet. Domain, Gradle and app DI stay coordinator-owned.
- Coordinator owns acceptance/status, commits, shared integration, broad Gradle runs and emulator scheduling. Workers request focused build/emulator access to avoid overlapping runs.
- Debug-only foundation Room schemas are compile fixtures, separate from the F06 production database.

## Device evidence

No physical device is connected. An isolated Android 13 emulator is booted at `emulator-5556`, 1920×1080 at 240 dpi; see [environment evidence](evidence/F01/emulator-environment.md). US-001 APK installed and opened normally, with its screenshot inspected and no Android runtime error. Physical Retroid Pocket Flip 2 behavior and density calibration remain pending.

## Reviews

- US-001: independent `foundation_review` (Sol / high) found no blocking findings after dependency compatibility, lifecycle request consumption, schema assets, test dependencies, and wrapper checksum fixes. Lint has zero errors and 48 warnings; warnings remain recorded rather than suppressed. Accepted by coordinator on 7 September 2026.
- US-002: independent Sol review found no remaining semantic blocker after inventory preservation/scope, launch identity, global order, collision, and concurrency fixes. Coordinator verified 12 passing domain test results and consumer compile evidence. Accepted on 7 September 2026.

- US-003/F01: independent Sol navigation/input/status/modal review and coordinator Activity-claim review found no remaining blocker. Full gate passed with 22 tests and zero lint errors; final Android 13 install/cold launch/task-return smoke passed. Accepted 7 September 2026.
