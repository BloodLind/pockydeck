# US-046 — Choose default and item-specific emulators

- Parent: [F16 — Emulator selection and ROM launch](../features/F16-emulator-launch.md)
- Status: **Implemented in the broad ROM batch; full restart, chooser-return and device-matrix acceptance pending**
- Type: **Feature**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a launcher user, I want to choose among supported emulators and recover a missing choice, so that ROM launch uses my accepted preference without losing browsing context.

## Delivery prerequisites

- [US-045 — Launch a supported ROM through the common open pipeline](US-045-supported-rom-launch.md), F15 and the F16 resolution/persistence contracts are integrated in the [broad ROM batch](../evidence/F15/rom-feature-batch.md).
- The user's approved [batch workflow](../progress.md#current-delivery-workflow) supersedes the former per-story acceptance and wave gate for implementation; physical compatibility and return criteria retain their separate evidence requirements.
- Native Android launch regression is recorded in [US-045](US-045-supported-rom-launch.md#verification). This delivery does not close F17 or F18.

## Scope

Provide functional Settings defaults and item-specific choice using shared controls. Persist accepted choices through coordinator-reviewed preferences/schema and present supported capabilities only. Restore origin focus after accept or dismiss, then publish full compatibility and native-regression evidence.

## Acceptance criteria

- [ ] **AC-01** — **Given** a supported default or item-specific choice is accepted, **when** the launcher restarts, **then** resolution restores it according to the published policy.
- [x] **AC-02** — **Given** compatible choices are ambiguous or the prior selection is unavailable, **when** a launch needs resolution, **then** the user sees a useful supported choice or recovery state rather than a silent switch.
- [ ] **AC-03** — **Given** a choice dialog is accepted or dismissed, **when** control returns, **then** it restores the initiating item and controller focus; a later launch uses the accepted choice.
- [ ] **AC-04** — **Given** an app/version/platform/format combination lacks accepted evidence, **when** the user opens emulator selection, **then** that combination is unavailable or unsupported and is not offered as a compatible choice.

## Verification

- **AC-01: Partial.** The passing native [Room ROM tests](../../../core/data/src/androidTest/kotlin/dev/handheld/launcher/core/data/rom/RoomRomLibraryInstrumentedTest.kt) cover console emulator/core choices after database reopen and item emulator override preservation through source removal/re-add/rename. Settings console defaults and Details item overrides are integrated; item overrides take precedence, and selecting Automatic removes the relevant explicit choice. A complete launcher-process restart followed by launch using each kind of accepted emulator choice has not been demonstrated; this criterion stays unchecked.
- **AC-02: Passed for selection/recovery policy.** The [15 passing policy tests](../evidence/F16/emulator-contracts.md#validation-scope) cover multiple-candidate determinism, unavailable preferences, one-candidate behavior and detection-only exclusion. The [integrated ROM controller](../../../app/src/main/kotlin/dev/handheld/launcher/rom/RomFeatureController.kt) presents available choices when a saved app is unavailable, preserves a one-time choice unless Remember is accepted, and revalidates the selected app after dialogs/preparation instead of silently substituting another app.
- **AC-03: Partial; full chooser-return acceptance pending.** The shared modal and origin integration is implemented and chooser cancellation produces an explicit non-success acknowledgement, as recorded in the [batch evidence](../evidence/F15/rom-feature-batch.md#implementation). Existing modal and launch-origin tests are narrower than accepting/dismissing an emulator dialog, restoring its initiating game/controller focus and then launching that game. That full sequence, including physical return behavior, has not been verified; this criterion stays unchecked.
- **AC-04: Partial; compatibility acceptance pending.** The [source-contract matrix](../evidence/F16/emulator-contracts.md#implemented-coverage) explicitly separates dispatch and detection-only entries. Policy/native contract tests enforce console/format/core/URI boundaries, and final dispatch revalidates eligibility. Settings rows describe console capabilities, not proof that every game format works. A complete per-item chooser fixture and actual emulator/version/game-boot matrix are not recorded; this criterion stays unchecked rather than treating source review as universal device acceptance.

## Delivery notes

F16 owns settings/details emulator subpackages and evidence; shared persistence and registration remain central. Source coverage: F16 ordered step 5 and validation/handoff. See [F16](../features/F16-emulator-launch.md) and [the story standard](story-standard.md).

The [progress record](../progress.md#story-and-feature-status) marks these controls implemented with a partial physical matrix. RetroArch core choices are curated integration metadata; users must install the selected core themselves. Emulator package-update behavior remains unverified under [US-044 AC-03](US-044-supported-emulator-resolution.md#verification).

## Out of scope

Controller remapping, emulator purchases, and unrelated Settings changes.
