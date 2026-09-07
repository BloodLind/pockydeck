# US-043 — Browse indexed ROMs through existing launcher destinations

- Parent: [F15 — ROM sources and incremental indexing](../features/F15-rom-index.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a launcher user, I want indexed ROMs to appear in the same library and search flows, so that I can browse my games consistently before and after emulator setup.

## Ready when

- [US-042 — Maintain the ROM index through complete incremental scans](US-042-incremental-rom-indexing.md) is accepted.
- F14 is accepted and the coordinator has integrated ROM catalog/schema changes through existing destination contracts.
- Unverified emulator capability remains an explicit unavailable state until F16.

## Scope

Integrate completed ROM catalog rows with Library, Search, Favorites, Home presentation, and availability contracts. Use shared stable identities and show unresolved or no-compatible-emulator state honestly. Publish schema, recovery, and migration evidence for F16 without changing shared destination packages outside a lease.

## Acceptance criteria

- [ ] **AC-01** — **Given** a completed indexed ROM row, **when** common destinations query the catalog, **then** it appears with its stable identity through their shared flow rather than a separate ROM store or screen.
- [ ] **AC-02** — **Given** a ROM is unresolved or has no compatible emulator yet, **when** it renders, **then** it exposes its availability/recovery state and does not claim launch support.
- [ ] **AC-03** — **Given** a source becomes unavailable or a rescan occurs, **when** ROM rows update, **then** favorite, recent, and artwork references follow the reviewed preservation policy.
- [ ] **AC-04** — **Given** ROM schema integration occurs, **when** native Android browsing and launch are exercised, **then** they remain usable and source/grant/grouping evidence distinguishes tested cases from pending device cases.

## Verification

- **AC-01:** Not run — integrated catalog fixtures across Library, Search, Favorites, and Home.
- **AC-02:** Not run — Compose availability/recovery-state fixtures.
- **AC-03:** Not run — rescan/unavailable preservation tests.
- **AC-04:** Not run — native regression matrix and migration evidence review.

## Delivery notes

F15 supplies ROM repository/presentation integration evidence; coordinator registers shared catalog/settings deltas and routes destination defects. Source coverage: F15 ordered step 6 and validation/handoff. See [F15](../features/F15-rom-index.md) and [the story standard](story-standard.md).

## Out of scope

Claiming ROM launch before F16 or rewriting common destination paths without a lease.
