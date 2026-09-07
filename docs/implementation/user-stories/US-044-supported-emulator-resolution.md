# US-044 — Resolve supported emulator capabilities and saved choices

- Parent: [F16 — Emulator selection and ROM launch](../features/F16-emulator-launch.md)
- Status: **Planned**
- Type: **Enabler**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a developer, I want deterministic emulator capability and choice contracts for the accepted matrix, so that launching uses a validated target rather than assuming an installed package is compatible.

## Ready when

- [US-043 — Browse indexed ROMs through existing launcher destinations](US-043-rom-catalog-destination-integration.md) is accepted.
- F15 is accepted; F16’s wave-11 gate remains mandatory.
- Exact emulator app/component/version, platform/format, and URI/storage strategies are recorded as approved readiness inputs.

## Scope

Record each accepted emulator’s package, component, version, formats, action/extras/flags, URI grants, and validation device. Detect installed supported apps through shared visibility; define deterministic default, override, missing, and ambiguous resolution. Publish reviewed persistence and preservation rules before dependent UI or adapters begin.

## Acceptance criteria

- [ ] **AC-01** — **Given** an installed emulator package, **when** capability is presented, **then** it is offered only for recorded supported matrix rows and installation alone does not imply every format is valid.
- [ ] **AC-02** — **Given** identical availability, default, and override inputs, **when** resolution runs, **then** it returns the same accepted target or an explicit user-choice/recovery requirement.
- [ ] **AC-03** — **Given** a user accepts a default or item override, **when** the approved persistence rule restores it, **then** precedence follows the reviewed contract and an emulator update does not silently change the choice.
- [ ] **AC-04** — **Given** URI or storage behavior is needed by an adapter, **when** its capability row is published, **then** the recorded strategy and prerequisite are explicit and no raw-path or hidden-copy behavior is assumed.

## Verification

- **AC-01:** Not run — capability matrix and installed-app detection fixtures.
- **AC-02:** Not run — deterministic resolution tests for default/override/missing cases.
- **AC-03:** Not run — persistence/restart and emulator-update fixture tests.
- **AC-04:** Not run — coordinator review of URI/storage prerequisites.

## Delivery notes

F16 owns emulator capability/resolution proposals and evidence; domain, persistence, and visibility changes remain coordinator owned. Source coverage: F16 ordered steps 1–2. See [F16](../features/F16-emulator-launch.md) and [the story standard](story-standard.md).

## Out of scope

Broad compatibility claims, emulator purchases, and silent storage-policy changes.
