# US-037 — Expose optional status only when its meaning is supported

- Parent: [F13 — Device status](../features/F13-device-status.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a launcher user, I want optional readings to identify their actual source or be omitted, so that the status strip does not misrepresent device capabilities.

## Ready when

- [US-036 — Display lifecycle-aware battery, clock and connectivity](US-036-baseline-device-status.md) is accepted.
- F08 remains accepted and F13’s wave-8 lease applies; final F13 acceptance follows all of its child stories.
- Candidate memory, storage, and temperature APIs have been inspected; actual device capability is still evidence-dependent.

## Scope

Inspect optional supported sources, adding a slot only with documented meaning. Omit unsupported values consistently and use the agreed restrained placeholder for temporary loss. Integrate the production provider and document device/lifecycle evidence while preserving strip layout and Home content state.

## Acceptance criteria

- [ ] **AC-01** — **Given** an optional reading is unsupported, **when** the strip renders, **then** its slot is absent consistently; **given** its supported source is temporarily unavailable, **when** it renders, **then** the agreed unavailable placeholder is shown.
- [ ] **AC-02** — **Given** an optional reading is displayed, **when** its source is reviewed, **then** its label, documented source, and meaning agree; battery temperature is never labelled CPU or ambient temperature.
- [ ] **AC-03** — **Given** optional slots appear, disappear, or update, **when** Home is active, **then** left/right strip placement, measured widths, selection, and shell content state are preserved.
- [ ] **AC-04** — **Given** the launcher leaves foreground, **when** optional collection would otherwise continue, **then** sampling follows the foreground lifecycle and resumes without duplicate listeners.

## Verification

- **AC-01:** Not run — supported, unsupported, and temporary-loss provider fixtures.
- **AC-02:** Not run — manual source/label evidence review.
- **AC-03:** Not run — shell screenshot and state-preservation test.
- **AC-04:** Not run — lifecycle listener/sampling test.
- **AC-01–AC-04:** Not run — physical Flip 2 optional-reading and native-control checks remain pending device evidence.

## Delivery notes

F13 owns optional providers, formatting, and evidence. Strip, DI, permissions, and shared contracts remain coordinator deltas. Source coverage: F13 ordered steps 2–5. See [F13](../features/F13-device-status.md) and [the story standard](story-standard.md).

## Out of scope

Inferred CPU temperature, memory-card fixture status, or replacement system controls.
