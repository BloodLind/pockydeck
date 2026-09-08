# US-041 — Connect ROM folders and recover unavailable access

- Parent: [F15 — ROM sources and incremental indexing](../features/F15-rom-index.md)
- Status: **Implemented; physical reboot, grant-revocation and SD-removal acceptance remains pending**. See [batch evidence](../evidence/F15/rom-feature-batch.md).
- Type: **Feature**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a launcher user, I want to choose ROM folders once and understand source health, so that I can restore missing access without losing my library references or files.

## Ready when

Implementation follows the user's approved integrated batch workflow; the original wave prerequisites below no longer block coding. Physical acceptance requirements remain applicable.

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

- **AC-01:** Native smoke — accepted the actual Android folder picker grant for a synthetic external-storage tree and indexed five games; access and the library survived application force-stop/restart. Physical reboot/grant persistence remains a separate check.
- **AC-02:** Native smoke — opened the folder picker again, navigated Back through its folders and cancelled it; the original five-game source remained without another source being added. Activity result handling only adds a source for an accepted non-null tree; process-loss/recreation replay testing remains separate.
- **AC-03:** Partial — native repository tests cover unavailable-source projection and prevention of accidental restoration by console correction. Actual provider failure, revoked-grant and removed-SD procedures remain pending.
- **AC-04:** Passed — native Room tests retain identities, favorites, artwork and history across removal and exact-root reattachment. The removal path disables configuration and never deletes source documents.
- **AC-05:** Passed for selected emulator storage — actual SAF enumeration and bounded descriptor reads indexed the synthetic tree. No raw-path conversion or broad storage permission is used. Physical reboot/grant and SD procedures remain pending.

## Delivery notes

F15 owns source code and its settings subpackage; SAF Activity wiring and settings registration are coordinator deltas. Source coverage: F15 ordered steps 3, 6, and 7. See [F15](../features/F15-rom-index.md) and [the story standard](story-standard.md).

## Out of scope

Broad filesystem access, ROM-file deletion, and manual scanning as ordinary maintenance.
