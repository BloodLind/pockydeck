# US-036 — Display lifecycle-aware battery, clock and connectivity

- Parent: [F13 — Device status](../features/F13-device-status.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a launcher user, I want truthful baseline status in the custom launcher strip, so that I can see available device information without disrupting browsing.

## Ready when

- [US-025 — Use the launcher as HOME while retaining native system controls](US-025-android-home-role-and-window-loop.md) is accepted.
- F08 is accepted and integrated; F13’s wave-8 lease applies, while final F13 acceptance follows all of its child stories.
- Coordinator-owned strip, contracts, manifest declarations, and DI attachment are available for a narrow integration delta.

## Scope

Implement supported battery, time, and connectivity sources with stable semantic formatting. Bind callbacks to launcher foreground lifecycle and use low-frequency sampling only where a callback cannot provide the value. Publish source API, cadence, unavailable/error meaning, and coordinator provider attachment.

## Acceptance criteria

- [ ] **AC-01** — **Given** a supported source supplies a battery, clock, or connectivity reading, **when** the launcher is foreground, **then** the strip displays its documented meaningful value.
- [ ] **AC-02** — **Given** a callback is lost or a source errors, **when** the provider updates, **then** the affected reading becomes explicit unavailable state rather than continuing to show it as current.
- [ ] **AC-03** — **Given** the launcher enters background and returns, **when** collection stops and resumes, **then** it creates no duplicate listener and resumes the supported readings.
- [ ] **AC-04** — **Given** a status reading changes while Home is visible, **when** the strip updates, **then** selected item, scroll state, and shell anchors are preserved.

## Verification

- **AC-01:** Not run — Android source fixture and emulator/device strip capture.
- **AC-02:** Not run — automated callback-loss/error state test.
- **AC-03:** Not run — lifecycle test for foreground stop/resume and listener count.
- **AC-04:** Not run — Compose selection/anchor preservation test.

## Delivery notes

F13 owns Android status sources and app status formatting; contracts, shell, manifests, and DI are coordinator owned. Record source API, cadence, failure meaning, and any ordinary-read declaration with its source and reason for coordinator review. Source coverage: F13 ordered steps 1, 3, and 4. See [F13](../features/F13-device-status.md) and [the story standard](story-standard.md).

## Out of scope

Polling while games run, fixture telemetry in production, usage monitoring, and process monitoring.
