# F18 — Flip 2 hardening and installable delivery

- Status: **planned**.
- User stories: [US-050](../user-stories/US-050-physical-lifecycle-and-regression-gate.md), [US-051](../user-stories/US-051-flip2-native-visual-calibration.md), [US-052](../user-stories/US-052-measured-device-performance-gate.md), [US-053](../user-stories/US-053-installable-artifact-and-recovery-guide.md). Dispatch one ready story; this packet remains the technical ownership and feature-gate reference.
- Agent: `gpt-5.6-sol`, reasoning `high`; integration/release reviewer.
- Outcome: a verified installable launcher with final Home calibration, measured performance, and clear setup/recovery instructions for the supported device and integrations.
- Prerequisite: F17 accepted and earlier gates retained. Physical Flip 2 access/results are required to claim physical-device completion; continue independent builds, review and documentation while awaiting that evidence.

## Ownership and contracts

Owns `app/src/androidTest/kotlin/dev/handheld/launcher/integration/device/**`, `app/src/test/kotlin/dev/handheld/launcher/integration/device/**`, `docs/implementation/evidence/F18/**`, `docs/setup-guide.md`, `docs/supported-integrations.md`, `docs/device-validation.md`. Generated installable artifacts use `artifacts/releases/**` if approved by the coordinator and never include private signing material. Build/release configuration, tokens/metrics, shared contracts/schema, manifests and feature fixes require explicit coordinator/owning-packet leases; no blanket project rewrite.

Consumes accepted feature evidence, Home reference and provider/emulator support matrices. Provides final device acceptance matrix, calibrated native sizing records, screenshot baseline locations, measured performance, installable artifact with checksum/version, and setup/recovery documentation. A debug APK may be a development deliverable; identify its build type rather than claiming production signing.

## Ordered steps

1. Review the whole implementation against the approved exclusions and architecture. Verify one shell, real status, native system controls, cache-first data, no main-thread disk/network work, no replayed one-shot requests, and bounded asynchronous work.
2. Run the integrated debug build, lint, relevant JVM/instrumentation tests and supported emulator/provider regression matrix. Route concrete failures to packet owners or centrally leased fixes; rerun affected checks after integration.
3. Calibrate native dp/sp metrics on Flip 2 at actual density and 1920×1080. Compare the Home app-content crop, then render all destinations using identical shell anchors. Correct tokens/metrics centrally. Verify large fonts, cutouts/transient bars, IME, focused outlines, long labels and missing artwork. Establish screenshot baselines only after calibration is accepted.
4. Execute physical lifecycle cases: repeated Home presses; app→Home; lid close/open both in launcher and game; reboot/unlock; process cleanup; Activity recreation; storage removal/revocation; installs/updates while inactive. Confirm external tasks survive launcher return and state restoration finds a valid visible item.
5. Measure startup, warm return, scroll jank, memory and foreground/idle activity with representative catalog sizes. Set numeric budgets from the first physical baseline, investigate measured regressions, and record before/after results. Do not claim zero latency or invent performance numbers.
6. Build the agreed installable APK, record variant/version/checksum and supported platform/emulator/provider versions. Verify install/update without loss of preferences/favorites/recency and preservation across existing schema migrations. Do not publish externally, purchase signing services or install over user data without the applicable authorization.
7. Write setup steps for normal launch, Android's default-HOME choice, controller mapping, source grants, emulator selection and provider configuration. Include recovery to Android launcher/settings, revoked storage, offline artwork, unsupported integrations and known device limits. Link the artifact and evidence in the coordinator handoff.

## Excludes

No new feature scope, guessed Play Store eligibility, untested firmware/device compatibility, custom firmware/control replacement, destructive resets, automatic public publishing or false claims that pending device tests passed.

## Validation and handoff

Complete the acceptance matrix from [project-plan.md](../../project-plan.md) with actual evidence and explicit pending/failed states. Final delivery requires an installable artifact, setup/recovery guide, correct notices, verified migration preservation, calibrated Home screenshots and physical Flip 2 lifecycle/input evidence. If device access is unavailable, hand off the built artifact and independent results with the exact outstanding checks; the coordinator must keep the physical completion gate open. Report remaining limitations plainly and close the packet only when its required evidence is accepted.
