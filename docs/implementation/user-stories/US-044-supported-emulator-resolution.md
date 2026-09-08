# US-044 — Resolve supported emulator capabilities and saved choices

- Parent: [F16 — Emulator selection and ROM launch](../features/F16-emulator-launch.md)
- Status: **Implemented in the broad ROM batch; emulator-update acceptance pending**
- Type: **Enabler**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a developer, I want deterministic emulator capability and choice contracts for the accepted matrix, so that launching uses a validated target rather than assuming an installed package is compatible.

## Delivery prerequisites

- [US-043 — Browse indexed ROMs through existing launcher destinations](US-043-rom-catalog-destination-integration.md) and F15 are integrated in the [broad ROM batch](../evidence/F15/rom-feature-batch.md).
- The user's approved [batch workflow](../progress.md#current-delivery-workflow) supersedes the former per-story acceptance and wave-11 implementation gate. Unverified device criteria remain open.
- Package/component, format and URI/storage contracts, observed installed versions, and their limitations are recorded in the [F16 compatibility evidence](../evidence/F16/emulator-contracts.md).

## Scope

Record each accepted emulator’s package, component, version, formats, action/extras/flags, URI grants, and validation device. Detect installed supported apps through shared visibility; define deterministic default, override, missing, and ambiguous resolution. Publish reviewed persistence and preservation rules before dependent UI or adapters begin.

## Acceptance criteria

- [x] **AC-01** — **Given** an installed emulator package, **when** capability is presented, **then** it is offered only for recorded supported matrix rows and installation alone does not imply every format is valid.
- [x] **AC-02** — **Given** identical availability, default, and override inputs, **when** resolution runs, **then** it returns the same accepted target or an explicit user-choice/recovery requirement.
- [ ] **AC-03** — **Given** a user accepts a default or item override, **when** the approved persistence rule restores it, **then** precedence follows the reviewed contract and an emulator update does not silently change the choice.
- [x] **AC-04** — **Given** URI or storage behavior is needed by an adapter, **when** its capability row is published, **then** the recorded strategy and prerequisite are explicit and no raw-path or hidden-copy behavior is assumed.

## Verification

- **AC-01: Passed for contract eligibility.** The [matrix](../evidence/F16/emulator-contracts.md#implemented-coverage) contains 37 package profiles: 27 standalone dispatch profiles, three RetroArch variants and seven detection-only entries. Fifteen passing policy tests include console/format boundaries, detection-only exclusion and version eligibility; installed exported-component checks are implemented. The [installed-device inventory](../evidence/F16/emulator-contracts.md#installed-device-read-only-evidence) records observed versions, not successful game boots.
- **AC-02: Passed for deterministic policy.** `severalCandidatesNeverChooseByRegistryOrder` and `oneCandidateIsAutomaticAndRemovedDefaultIsIgnored` are included in the [passing policy suite](../evidence/F16/emulator-contracts.md#validation-scope). The integrated controller gives an item override precedence over its console default, asks when a saved choice is no longer eligible, and automatically selects a sole candidate only when no saved preference requires recovery.
- **AC-03: Partial.** The passing native [Room ROM tests](../../../core/data/src/androidTest/kotlin/dev/handheld/launcher/core/data/rom/RoomRomLibraryInstrumentedTest.kt) verify console emulator/core persistence through database reopen (`userAssignmentDuringEnumerationSurvivesRescanAndDatabaseReopen`) and item override preservation through source removal/re-add/rename (`sourceRemovalReaddAndRenameRetainIdentityFavoritesArtworkAndHistory`). An actual emulator package update with restored choices has not been exercised; this criterion stays unchecked.
- **AC-04: Passed for the published strategy and native contract checks.** [Storage evidence](../evidence/F16/emulator-contracts.md#retroarch-and-storage) records exact content URIs, validated RetroArch SAF paths, temporary read grants and companion handling. Seven Android contract tests passed on API 33. User-approved [bounded extraction](../evidence/F15/archive-extraction.md) is explicit; no launcher All files permission or guessed ROM filesystem path is used.

## Delivery notes

F16 owns emulator capability/resolution proposals and evidence; domain, persistence, and visibility changes remain coordinator owned. Source coverage: F16 ordered steps 1–2. See [F16](../features/F16-emulator-launch.md) and [the story standard](story-standard.md).

Implementation status follows the [progress record](../progress.md#story-and-feature-status). Checked policy criteria do not certify every emulator/version/game combination, installed RetroArch core, BIOS or key setup.

## Out of scope

Broad compatibility claims, emulator purchases, and silent storage-policy changes.
