# US-040 — Establish the approved ROM identity and source contract

- Parent: [F15 — ROM sources and incremental indexing](../features/F15-rom-index.md)
- Status: **Planned**
- Type: **Enabler**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a developer, I want a concrete first-platform support and preservation contract, so that ROM indexing can be implemented without guessing file grouping or data behavior.

## Ready when

- [US-039 — Verify native launch, lifecycle and catalog resilience together](US-039-native-lifecycle-and-catalog-gate.md) is accepted.
- F14 is accepted; F15’s wave-10 lease applies, while final F15 acceptance follows all of its child stories.
- The user-approved first platform, format/archive, multi-file/disc, root-change, and duplicate-root choices are recorded. Any missing choice remains a readiness input.

## Scope

Record the approved support matrix and unresolved-classification policy. Submit bounded domain, schema, and SAF requests using generated item IDs plus source-root/document identity. Document duplicate roots, root changes, and preservation of user-owned references, then publish exact reviewed contracts and migration requirements before consumers begin.

## Acceptance criteria

- [ ] **AC-01** — **Given** each initial platform, format, archive, and grouping case, **when** the matrix is recorded, **then** it has approved supported or unresolved status and no extension alone promises launchability.
- [ ] **AC-02** — **Given** identical filenames in separate roots or a validated multi-file/disc group, **when** identities are proposed, **then** separate roots remain distinct and grouping follows the approved matrix rather than filename-only identity.
- [ ] **AC-03** — **Given** a source/discovery update, **when** the reviewed contract is applied, **then** discovered data stays separate from favorites, recency, manual classification, and artwork references.
- [ ] **AC-04** — **Given** root changes, duplicate roots, or schema migration, **when** the proposal is reviewed and tested, **then** it specifies handling without silent merging/deletion and preserves the existing Android catalog and user data.
- [ ] **AC-05** — **Given** a consumer needs ROM contracts, **when** implementation begins, **then** the coordinator has published exact signatures and migration behavior.

## Verification

- **AC-01:** Not run — manual review of recorded user-approved support matrix.
- **AC-02:** Not run — automated identity/grouping fixtures.
- **AC-03:** Not run — contract and preservation test review.
- **AC-04:** Not run — migration preservation tests.
- **AC-05:** Not run — coordinator publication checklist.

## Delivery notes

F15 owns ROM source/repository proposals and evidence; domain/schema/SAF signatures and migrations are coordinator owned. Source coverage: F15 ordered steps 1–2. See [F15](../features/F15-rom-index.md) and [the story standard](story-standard.md).

## Out of scope

Choosing support silently, universal format claims, and emulator dispatch.
