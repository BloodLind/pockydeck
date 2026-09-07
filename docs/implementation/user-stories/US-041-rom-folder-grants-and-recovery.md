# US-041 — Connect ROM folders and recover unavailable access

- Parent: [F15 — ROM sources and incremental indexing](../features/F15-rom-index.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a launcher user, I want to choose ROM folders once and understand source health, so that I can restore missing access without losing my library references or files.

## Ready when

- [US-040 — Establish the approved ROM identity and source contract](US-040-approved-rom-source-and-identity-contract.md) is accepted.
- F14 is accepted and F15’s source/identity/reference-retention contract and wave-10 lease are published.
- Actual selected storage results are required before claims about persisted grants, reboot, or SD behavior.

## Scope

Implement acknowledged SAF tree selection and persist only accepted document grants. Present source setup, health, and recovery for revoked access, removed storage, and provider errors. Apply the approved removal/reference-retention policy without deleting ROM files or assuming raw filesystem paths.

## Acceptance criteria

- [ ] **AC-01** — **Given** the user accepts a SAF tree grant, **when** the request is acknowledged, **then** the granted document access is persisted for later access and the selection is not replayed.
- [ ] **AC-02** — **Given** the user cancels a tree selection, **when** the acknowledgement returns, **then** no source is added and the request is not replayed by recollection.
- [ ] **AC-03** — **Given** a grant is revoked, storage is removed, or a provider fails, **when** source health updates, **then** it shows unavailable state and a recoverable action rather than a successful empty scan.
- [ ] **AC-04** — **Given** source configuration is removed, **when** the operation completes, **then** underlying ROM files remain intact and catalog references follow the approved retention policy.
- [ ] **AC-05** — **Given** a source is configured, **when** its access is used, **then** it uses document grants without raw-path or broad-storage assumptions.

## Verification

- **AC-01:** Not run — instrumented document-provider grant persistence test.
- **AC-02:** Not run — acknowledged-cancellation no-replay test.
- **AC-03:** Not run — revoked-grant, removed-storage, and provider-error fixtures.
- **AC-04:** Not run — source-removal/reference-retention test.
- **AC-05:** Not run — instrumented document-access test; physical reboot/grant and SD procedure remain pending device evidence.

## Delivery notes

F15 owns source code and its settings subpackage; SAF Activity wiring and settings registration are coordinator deltas. Source coverage: F15 ordered steps 3, 6, and 7. See [F15](../features/F15-rom-index.md) and [the story standard](story-standard.md).

## Out of scope

Broad filesystem access, ROM-file deletion, and manual scanning as ordinary maintenance.
