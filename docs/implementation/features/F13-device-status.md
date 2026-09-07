# F13 — Real device status

- Status: **planned**.
- User stories: [US-036](../user-stories/US-036-baseline-device-status.md), [US-037](../user-stories/US-037-honest-optional-device-status.md). Dispatch one ready story; this packet remains the technical ownership and feature-gate reference.
- Agent: `gpt-5.6-sol`, reasoning `high`.
- Outcome: the custom launcher strip displays supported device information honestly and stops unnecessary work while another app is foreground.
- Prerequisite: F08 accepted. Scheduled beside F12 with disjoint ownership.

## Ownership and contracts

Owns `core/data/src/main/kotlin/dev/handheld/launcher/core/data/android/status/**`, `app/src/main/kotlin/dev/handheld/launcher/status/**`; matching data/app JVM/instrumentation test packages; `docs/implementation/evidence/F13/**`. Status contract signatures, manifest permissions, `AppContainer`, shell rendering/layout and theme/metrics remain coordinator-owned.

Consumes F01 available/unavailable/unsupported status types and F04 strip presentation boundary. Provides lifecycle-aware device status sources and UI formatting with stable measured widths. Battery, clock and connectivity are the baseline; supported memory/storage and clearly identified temperature may be added without faking capabilities. Report every reading's API, meaning, cadence and failure behavior.

## Ordered steps

1. Implement battery, time and connectivity from supported Android APIs. Prefer callbacks and lifecycle-bound collection. Represent missing values explicitly rather than retaining stale values as if current.
2. For memory/storage/temperature, inspect actual supported APIs and device access. Add only accurately named readings. Battery temperature cannot be labeled CPU or ambient; unsupported optional slots are consistently omitted, temporarily unavailable readings use the specified placeholder.
3. Define low-frequency sampling only where callbacks cannot supply a value. Suspend collection/sampling when the launcher is not foreground and resume safely without duplicate listeners. Avoid permission requests that exceed the approved baseline; submit needed ordinary read permissions with reason to coordinator.
4. Map values into stable-width semantic labels/accessibility descriptions. Preserve Home's left/right strip placement and shell anchors; clock updates must not reset content focus or ViewModels.
5. Hand coordinator the production provider/formatter dependencies to replace F04's unavailable fallback. Handle callback errors and lifecycle loss as state, not a fatal page failure.

## Excludes

No usage monitoring, session/playtime recording, FPS/TDP/GPU/fan controls, foreground polling during games, fake memory-card state, CPU temperature inferred from battery temperature, or custom system-control replacements.

## Validation and handoff

Test available→unavailable/unsupported transitions, formatting widths, listener registration/unregistration, and pause/resume without duplicated callbacks. Validate real supported readings on the available device and note which Flip 2 capabilities remain unverified. Verify system shade/volume controls work and status changes preserve selected item/scroll. Record provider provenance and sampling behavior, actual screenshots and results; coordinator wires the strip before F14.
