# US-002 — Define stable catalog, persistence and launch contracts

| Field | Value |
| --- | --- |
| Parent feature | [F01 — Foundation and shared contracts](../features/F01-foundation.md) |
| Status | Accepted |
| Type | Enabler |
| Implementation agent | `gpt-5.6-sol` / `high` |

## User story

As a developer, I want minimal shared contracts for item identity, catalog state and acknowledged launches, so that data and feature implementations agree on preservation and recency behavior.

## Ready when

- [US-001 — Create an installable four-module launcher foundation](US-001-installable-four-module-foundation.md) and the relevant F01 parent gate are accepted.
- The approved project, design, and ownership baseline is available; it does not imply Android implementation.
- F01 wave 1, its exact lease, validation, and coordinator handoff requirements apply. F01’s completion gate is assessed after its child stories; dependent features wait for coordinator acceptance.

## Scope

Define identities for items and launchable components, availability, supported actions, category, provenance, and observable catalog/favorite/preference boundaries. Separate completed inventory from partial or failed inventory and discovery fields from user data. Define one-shot acknowledged launch request/result semantics, duplicate suppression, and successful-open recording. Keep extension boundaries generic for later ROM/provider work.

## Acceptance criteria

- [x] **AC-01** — **Given** two launchable components in one package, **when** they are represented or rediscovered, **then** they remain distinct stable identities rather than being keyed by a title or filename.
- [x] **AC-02** — **Given** an incomplete inventory result or an availability change, **when** the catalog contract is applied, **then** it distinguishes that outcome from a completed inventory and retains favorite and history references.
- [x] **AC-03** — **Given** an acknowledged successful launch for an existing item, **when** it is recorded, **then** that item is promoted exactly once; a failure, focus change, details view, or internal system action does not promote it.
- [x] **AC-04** — **Given** deterministic fixture items, **when** successful opens and unopened items are ordered, **then** successful-open order is deterministic and unopened items use title/ID order without asserting process state or session duration.

## Verification

| Criterion | Evidence | Status |
| --- | --- | --- |
| AC-01 | [Stable identity and mismatch tests](../evidence/F01/US-002.md) | Passed |
| AC-02 | [Scoped inventory and reference-preservation tests](../evidence/F01/US-002.md) | Passed |
| AC-03 | [Acknowledgement, duplicate and concurrent-order tests](../evidence/F01/US-002.md) | Passed |
| AC-04 | [Recency/title/ID ordering tests](../evidence/F01/US-002.md) | Passed |

## Delivery notes

The F01 initial-creation lease covers domain model, repository, and policy contracts, matching domain tests, and their `contracts.md` entries; [F01](../features/F01-foundation.md) retains the exact path policy. Source coverage: F01 ordered step 4 (identity/query/preservation/launch/favorite/status types) and step 6 (deterministic domain fixtures). This story introduces no Room schema, Android enumeration, or compatibility promise.

## Out of scope

- Room entities or schema implementation, and Android enumeration.
- Speculative ROM/provider fields or compatibility promises.
