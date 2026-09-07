# US-017 — Persist a cache that survives catalog reconciliation

| Field | Value |
| --- | --- |
| Parent feature | [F06 — Catalog and preferences persistence](../features/F06-persistence.md) |
| Status | Accepted |
| Type | Enabler |
| Implementation agent | `gpt-5.6-sol` / `high` |

## User story

As a developer, I want observable catalog storage with non-destructive completed-inventory commits, so that browsing and user references remain reliable during refresh failures.

## Ready when

- [US-003 — Publish shared navigation, input and presentation ports](US-003-shared-navigation-input-and-presentation-ports.md) is accepted.
- The coordinator has accepted F01 and its integrated evidence, opening F06 work.
- F06 wave 2, its exact lease, validation, and coordinator handoff requirements apply. F06’s completion gate is assessed after its child stories; later features wait for coordinator acceptance.

## Scope

Implement Room catalog and favorite-reference storage that separates discovered fields, overrides, and provenance from user history. Expose cached queries and atomic completed-inventory writes with active-list availability filtering. Export a schema, indices and transaction notes, and migration infrastructure without destructive fallback.

## Acceptance criteria

- [x] **AC-01** — **Given** cached catalog rows and a discovery operation that has not finished, **when** a consumer observes the catalog, **then** cached rows are available and multiple components from one package retain distinct stable IDs.
- [x] **AC-02** — **Given** a completed, failed, cancelled, or partial inventory result, **when** reconciliation occurs, **then** only the completed result updates availability and all other outcomes preserve the existing catalog.
- [x] **AC-03** — **Given** rediscovery, update, or temporary unavailability, **when** active lists are queried, **then** unavailable items are hidden while favorite, override, and history references remain retained.
- [x] **AC-04** — **Given** the initial persistent schema, **when** its export and migration setup are reviewed, **then** approved semantics are preserved without destructive fallback or speculative ROM/provider tables.

## Verification

| Criterion | Evidence | Status |
| --- | --- | --- |
| AC-01 | Actual Room reopen and pending-discovery cache test; two components round-trip | Passed |
| AC-02 | Room scoped atomic emission, incomplete outcomes, collision rollback and 1,005 removals | Passed |
| AC-03 | Unavailability/rediscovery retains favorites, overrides and history references | Passed |
| AC-04 | Exported seven-table v1 schema opens via MigrationTestHelper; explicit migrations/no destructive fallback | Passed |

## Delivery notes

F06’s one-time creation lease covers local Room/catalog repository/schema paths and actual Room tests; domain, build, and DI remain coordinator-owned. Source coverage: F06 ordered steps 1 and 3, step 2 (cached observable catalog queries), and step 6. Use [F06](../features/F06-persistence.md) for its exact paths and handoff.

## Out of scope

- Package scans, UI state, and silent schema expansion.

Accepted 7 September 2026 after independent Sol review, [8 passing Android 13 Room tests](../evidence/F06/US-017.md), and the combined build/lint/JVM gate. US-018 is ready; successful-open recording and DataStore preferences remain later child stories.
