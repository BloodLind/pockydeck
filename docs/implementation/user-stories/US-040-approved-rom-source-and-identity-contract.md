# US-040 — Establish the approved ROM identity and source contract

- Parent: [F15 — ROM sources and incremental indexing](../features/F15-rom-index.md)
- Status: **Implemented in the approved broad ROM batch**. See [published contracts](../contracts.md#f15--f16-rom-sources-selection-and-launch) and [batch evidence](../evidence/F15/rom-feature-batch.md).
- Type: **Enabler**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a developer, I want a concrete platform support and preservation contract, so that ROM indexing can be implemented without guessing file grouping or data behavior.

## Ready when

The user's 8 September direction supersedes the former single-platform and wave-order prerequisites: recognize popular formats across named console folders, retain selectable unresolved games, and automatically extract supported archives where needed. The following prerequisites describe the original plan; implementation follows the approved batch workflow.

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

- **AC-01:** Recorded — [56-family recognition/grouping matrix](../evidence/F15/format-support.md), [archive contract](../evidence/F15/archive-extraction.md), and separate [emulator capability matrix](../evidence/F16/emulator-contracts.md).
- **AC-02:** Passed — planner grouping fixtures and native Room tests for distinct source identities, rescans and exact-root reattachment.
- **AC-03:** Passed — native source-removal/rescan tests retain favorites, artwork references and history; discovered and user-assigned console fields remain separate.
- **AC-04:** Passed — native 2-to-3 Room migration retains catalog, user references, operation receipts and next recency order. Duplicate/overlap handling and moved-root semantics are published.
- **AC-05:** Published — coordinator domain, schema, SAF, cache-provider and launch-cancellation contracts are recorded in [contracts](../contracts.md#f15--f16-rom-sources-selection-and-launch). This record does not assert full physical feature acceptance.

## Delivery notes

F15 owns ROM source/repository proposals and evidence; domain/schema/SAF signatures and migrations are coordinator owned. Source coverage: F15 ordered steps 1–2. See [F15](../features/F15-rom-index.md) and [the story standard](story-standard.md).

## Out of scope

Choosing support silently, universal format claims, and emulator dispatch.
