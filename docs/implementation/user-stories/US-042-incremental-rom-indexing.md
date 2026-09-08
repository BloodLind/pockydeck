# US-042 — Maintain the ROM index through complete incremental scans

- Parent: [F15 — ROM sources and incremental indexing](../features/F15-rom-index.md)
- Status: **Implemented; durable scheduling and physical storage interruption acceptance remain partial**. See [batch evidence](../evidence/F15/rom-feature-batch.md).
- Type: **Feature**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a launcher user, I want copied and removed ROMs to reconcile automatically and safely, so that my catalog stays current without destructive results from interrupted storage access.

## Ready when

The user authorized the integrated ROM batch and coordinator schema/WorkManager wiring. Original wave prerequisites below are historical; remaining device evidence is not waived.

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

- **AC-01:** Partial — native Room fixtures verify that completed scans reconcile absent games; actual selected-folder smoke verifies discovery and grouped entries. Full physical copy/remove lifecycle remains pending.
- **AC-02:** Passed at the repository boundary — incomplete enumeration, a failing later batch and source removal during an in-flight scan preserve retained data. Provider-level interruption procedures remain pending.
- **AC-03:** Passed — planner fixtures and native Room tests verify grouping, distinct roots, stable rescans and exact-root reattachment. Native SAF smoke grouped CUE/BIN as one item.
- **AC-04:** Passed — native Room test assigns a console during enumeration, then verifies it survives reconciliation and database reopen. An ambiguous ISO remains selectable in the native Library smoke.
- **AC-05:** Partial — native later-batch failure retains committed rows without final omission reconciliation; stable identities survive reprocessing. WorkManager scheduling is implemented, but forced worker/process interruption and reboot timing are not claimed tested.

## Delivery notes

F15 owns ROM scan/repository implementation and evidence; schema and WorkManager integration are coordinator changes. Source coverage: F15 ordered steps 4–5 and 7. See [F15](../features/F15-rom-index.md) and [the story standard](story-standard.md).

## Out of scope

Unsupported inference, destructive partial reconciliation, and emulator launching.
