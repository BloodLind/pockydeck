# US-045 — Launch a supported ROM through the common open pipeline

- Parent: [F16 — Emulator selection and ROM launch](../features/F16-emulator-launch.md)
- Status: **Implemented in the broad ROM batch; game-boot and physical-return acceptance pending**
- Type: **Feature**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a launcher user, I want a supported ROM to open in its resolved emulator, so that I can play it and return to the same selected game.

## Delivery prerequisites

- [US-044 — Resolve supported emulator capabilities and saved choices](US-044-supported-emulator-resolution.md), F15 and the URI/storage contracts are integrated in the [broad ROM batch](../evidence/F15/rom-feature-batch.md).
- The user's approved [batch workflow](../progress.md#current-delivery-workflow) supersedes the former per-story and wave gate for implementation.
- Source-verified dispatch contracts are distinguished from actual game-boot compatibility. Device proof is still required before claiming successful gameplay for an app/version/platform/format combination.

## Scope

Implement accepted emulator adapters with immediate target, URI, and grant validation and external task placement. Integrate them through F08’s common orchestration: successful dispatch records recency once; failure retains order and offers source or selection recovery. Record license, revision, path, and notices for any selectively reused mapping.

## Acceptance criteria

- [ ] **AC-01** — **Given** an accepted compatibility row with its validated target and URI, **when** the user launches the ROM, **then** the recorded emulator contract opens it with only required grants.
- [ ] **AC-02** — **Given** the emulator is missing, source grant is revoked, format is unsupported, or SAF input cannot be consumed under the accepted strategy, **when** launch is attempted, **then** it returns recoverable selection/source failure and recent order is unchanged.
- [ ] **AC-03** — **Given** ROM dispatch succeeds, **when** the user returns Home, **then** the same ID is promoted once, repeated opens do not duplicate it, and valid identity/anchor restore in the separate launcher task.
- [x] **AC-04** — **Given** ROM support is integrated, **when** Android app launches and system actions are exercised, **then** their existing launch behavior and recency exclusions remain intact.

## Verification

- **AC-01: Partial; actual game boots pending.** The [F16 matrix and validation scope](../evidence/F16/emulator-contracts.md) record 15 passing policy tests and seven passing API 33 intent/URI tests, including read-only grants, Dolphin ClipData, `bootPath` and RetroArch SAF serialization. `Started` means Android accepted the activity start. These synthetic contract tests and read-only installed-package inspection do not demonstrate that each emulator boots a real game; this criterion stays unchecked.
- **AC-02: Partial.** Policy tests cover unsupported formats, required companions and invalid cores; native tests reject mismatched original/prepared document hierarchies. The [integrated pipeline](../evidence/F15/rom-feature-batch.md#implementation) revalidates targets and readable content before dispatch and maps failure/cancellation without a successful-open acknowledgement. Actual emulator disappearance and physical grant revocation during ROM launch have not been exercised together with the recency assertion; the full criterion stays unchecked.
- **AC-03: Partial; physical ROM return pending.** Both [LaunchCoordinator tests](../../../app/src/test/kotlin/dev/handheld/launcher/launch/LaunchCoordinatorTest.kt) passed in the integrated run: origin persistence/duplicate suppression/matching acknowledgement records once, and mismatched acknowledgement records nothing. These fixtures use Android items. The [earlier native base](../evidence/F14/native-application-base.md#verification) records Android Clock launch/return and cold restoration. Neither establishes actual ROM-to-emulator-to-Home identity, anchor or task behavior on the Flip 2, so this criterion stays unchecked.
- **AC-04: Passed on the API 33 emulator.** All three [Android component dispatch regression tests](../../../core/data/src/androidTest/kotlin/dev/handheld/launcher/core/data/android/apps/AndroidComponentLaunchDispatcherInstrumentedTest.kt) passed in the ROM batch, including `settingsLaunchUsesExternalTaskAndDoesNotWriteRecency`, exact MAIN/LAUNCHER task flags, and missing/disabled components. The [original dispatch evidence](../evidence/F07/US-022.md#acceptance-mapping) explains their task and recency assertions. Physical device regression remains part of F18.

## Delivery notes

F16 owns emulator adapters and evidence. Common dispatcher, domain, permissions, and persistence integration are coordinator deltas. If a mapping is selectively reused, record compatible licensing, contract validation, source revision/path, and required notices in delivery evidence. Source coverage: F16 ordered steps 3–4 and 6. See [F16](../features/F16-emulator-launch.md) and [the story standard](story-standard.md).

The [F16 source record](../evidence/F16/emulator-contracts.md#installed-device-read-only-evidence) identifies independent implementation and factual Argosy cross-checks; [dependency notices](../../rom-dependencies.md) document packaged licenses. Automatic archive preparation follows the user's approved bounded-cache policy. Implementation is recorded in [progress](../progress.md#story-and-feature-status); the unchecked criteria prevent universal launch or physical-lifecycle acceptance claims.

## Out of scope

Hidden ROM copying, BIOS/save management, guaranteed suspension, and process monitoring.
