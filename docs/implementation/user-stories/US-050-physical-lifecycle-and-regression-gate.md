# US-050 — Verify final integrated and physical lifecycle resilience

- Parent: [F18 — Flip 2 hardening and installable delivery](../features/F18-device-delivery.md)
- Status: **Planned**
- Type: **Gate**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a tester, I want actual integrated and Flip 2 lifecycle evidence, so that delivery claims match how the launcher behaves on the supported device.

## Ready when

- [US-049 — Correct metadata matches and artwork without losing overrides](US-049-manual-metadata-and-artwork-correction.md) is accepted.
- F17 is accepted; F18’s wave-13 and inherited final matrix gates remain mandatory.
- Physical Flip 2 access/results are required to close this story. Independent artifact/document work may proceed under US-053.

## Scope

Review architecture exclusions, bounded async work, and one-shot requests; run integrated native/emulator/provider checks. Exercise repeated HOME, app-to-Home, lid close/open in launcher/game, reboot/unlock, cleanup/recreation, inactive package change, storage loss/revocation, and external task continuity. Route defects to owners.

## Acceptance criteria

- [ ] **AC-01** — **Given** each claimed integrated behavior, **when** the final matrix is reviewed, **then** it has actual results for cache-first use, truthful status/native controls, and no replayed one-shot action.
- [ ] **AC-02** — **Given** repeated HOME, app-to-Home, lid, reboot/unlock, process cleanup, or recreation is exercised on Flip 2, **when** it completes, **then** a valid visible selection/anchor restores and external targets survive Home return.
- [ ] **AC-03** — **Given** storage access is lost or apps change while inactive, **when** recovery/reconciliation runs, **then** catalog and user references are not destructively lost.
- [ ] **AC-04** — **Given** a device case is unrun, failed, or blocked, **when** evidence is handed off, **then** it remains explicit and keeps physical completion open until accepted evidence closes the gate.

## Verification

- **AC-01:** Not run — integrated build, lint, JVM/instrumentation, emulator/provider regression matrix.
- **AC-02:** Not run — physical Flip 2 lifecycle procedure.
- **AC-03:** Not run — device storage/inactive-change recovery matrix.
- **AC-04:** Not run — coordinator review of accepted/pending/failed evidence.

## Delivery notes

F18 owns device integration tests and evidence; product fixes require their owner or an explicit coordinator lease. Source coverage: F18 ordered steps 1–2 and 4. See [F18](../features/F18-device-delivery.md) and [the story standard](story-standard.md).

## Out of scope

Untested firmware promises, speculative refactoring, and new features.
