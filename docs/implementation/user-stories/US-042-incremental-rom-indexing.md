# US-042 — Maintain the ROM index through complete incremental scans

- Parent: [F15 — ROM sources and incremental indexing](../features/F15-rom-index.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a launcher user, I want copied and removed ROMs to reconcile automatically and safely, so that my catalog stays current without destructive results from interrupted storage access.

## Ready when

- [US-041 — Connect ROM folders and recover unavailable access](US-041-rom-folder-grants-and-recovery.md) is accepted.
- F14 is accepted; F15’s published source/identity matrix and wave-10 lease are available.
- WorkManager wiring, schema, and dependency changes are coordinator-owned readiness inputs.

## Scope

Implement off-main-thread incremental enumeration, coalesced foreground/change triggers, and transactionally committed complete batches. Apply only deterministic format/platform inference and approved grouping; retain user decisions for ambiguous cases. Add durable deferrable reconciliation where useful without making foreground browsing wait for periodic work.

## Acceptance criteria

- [ ] **AC-01** — **Given** files are copied or removed, **when** foreground reconciliation or a supported signal completes a scan, **then** the index converges without promising instant recursive SAF notifications.
- [ ] **AC-02** — **Given** enumeration is cancelled, fails, or is incomplete, **when** reconciliation ends, **then** prior data is preserved and mass deletion is not inferred.
- [ ] **AC-03** — **Given** duplicate names in different roots or a validated grouped set, **when** indexing completes, **then** roots remain distinct, approved groups become one launch entry, and stable IDs survive rescans.
- [ ] **AC-04** — **Given** platform inference is ambiguous, **when** the item is indexed, **then** it remains unresolved until user selection and retains the remembered accepted mapping afterward.
- [ ] **AC-05** — **Given** reconciliation is deferred or its process stops after a committed batch, **when** durable work resumes, **then** completed batches remain available, reprocessing does not duplicate stable item IDs, and cached foreground browsing remains usable without waiting for the scheduled run.

## Verification

- **AC-01:** Not run — copied/removed document-provider integration fixtures.
- **AC-02:** Not run — cancelled and failed provider enumeration preservation tests.
- **AC-03:** Not run — duplicate-root, grouping, and stable-ID rescan tests.
- **AC-04:** Not run — ambiguity/correction persistence test.
- **AC-05:** Not run — durable-work restart and cache-first browsing test.

## Delivery notes

F15 owns ROM scan/repository implementation and evidence; schema and WorkManager integration are coordinator changes. Source coverage: F15 ordered steps 4–5 and 7. See [F15](../features/F15-rom-index.md) and [the story standard](story-standard.md).

## Out of scope

Unsupported inference, destructive partial reconciliation, and emulator launching.
