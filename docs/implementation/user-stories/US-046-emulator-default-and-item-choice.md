# US-046 — Choose default and item-specific emulators

- Parent: [F16 — Emulator selection and ROM launch](../features/F16-emulator-launch.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a launcher user, I want to choose among supported emulators and recover a missing choice, so that ROM launch uses my accepted preference without losing browsing context.

## Ready when

- [US-045 — Launch a supported ROM through the common open pipeline](US-045-supported-rom-launch.md) is accepted.
- F15 is accepted; F16 resolution, persistence, and adapter contracts are integrated.
- Native-app regression evidence is recorded before F17 can be accepted.

## Scope

Provide functional Settings defaults and item-specific choice using shared controls. Persist accepted choices through coordinator-reviewed preferences/schema and present supported capabilities only. Restore origin focus after accept or dismiss, then publish full compatibility and native-regression evidence.

## Acceptance criteria

- [ ] **AC-01** — **Given** a supported default or item-specific choice is accepted, **when** the launcher restarts, **then** resolution restores it according to the published policy.
- [ ] **AC-02** — **Given** compatible choices are ambiguous or the prior selection is unavailable, **when** a launch needs resolution, **then** the user sees a useful supported choice or recovery state rather than a silent switch.
- [ ] **AC-03** — **Given** a choice dialog is accepted or dismissed, **when** control returns, **then** it restores the initiating item and controller focus; a later launch uses the accepted choice.
- [ ] **AC-04** — **Given** an app/version/platform/format combination lacks accepted evidence, **when** the user opens emulator selection, **then** that combination is unavailable or unsupported and is not offered as a compatible choice.

## Verification

- **AC-01:** Not run — default/override persistence and restart tests.
- **AC-02:** Not run — missing/ambiguous emulator decision fixtures.
- **AC-03:** Not run — Compose modal-origin and focus restoration test.
- **AC-04:** Not run — selection UI fixture for unsupported combinations; device compatibility matrix and native regression review remain required before support is claimed.

## Delivery notes

F16 owns settings/details emulator subpackages and evidence; shared persistence and registration remain central. Source coverage: F16 ordered step 5 and validation/handoff. See [F16](../features/F16-emulator-launch.md) and [the story standard](story-standard.md).

## Out of scope

Controller remapping, emulator purchases, and unrelated Settings changes.
