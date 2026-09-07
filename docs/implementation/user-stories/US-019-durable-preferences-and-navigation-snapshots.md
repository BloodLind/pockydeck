# US-019 — Persist controller preferences and small navigation snapshots

| Field | Value |
| --- | --- |
| Parent feature | [F06 — Catalog and preferences persistence](../features/F06-persistence.md) |
| Status | Planned |
| Type | Enabler |
| Implementation agent | `gpt-5.6-sol` / `high` |

## User story

As a developer, I want versioned preference and restoration storage with safe recovery, so that user choices and place survive restarts without risking catalog data.

## Ready when

- [US-018 — Record successful opens once in deterministic recent order](US-018-durable-successful-open-order.md) is accepted.
- The coordinator has accepted F01 and its integrated evidence, opening F06 work.
- F06 wave 2, its exact lease, validation, and coordinator handoff requirements apply. F06’s completion gate is assessed after its child stories; later features wait for coordinator acceptance.

## Scope

Implement typed confirm/back mapping and small DataStore navigation snapshots. Version snapshot encoding and recover malformed or obsolete optional data with safe defaults. Publish repository constructors, write boundaries, and fakes for coordinator DI and F05/F07 consumers.

## Acceptance criteria

- [ ] **AC-01** — **Given** a stored or absent confirm/back preference, **when** it is read after a storage round-trip, **then** the stored mapping is retained and absence defaults to A-confirm/B-back.
- [ ] **AC-02** — **Given** destination, selected ID, anchor ID/offset, query, filter, and sort keys, **when** a snapshot makes a storage round-trip, **then** each key is retained for that destination.
- [ ] **AC-03** — **Given** malformed or obsolete optional snapshot data, **when** it is read, **then** safe defaults are used without clearing Room catalog, favorites, or recency.
- [ ] **AC-04** — **Given** stored preference and snapshot data, **when** its encoding and published interface are reviewed, **then** it contains compact identifiers/keys rather than Compose objects or lists and matches F01 contracts.

## Verification

| Criterion | Evidence | Status |
| --- | --- | --- |
| AC-01 | Planned actual DataStore default and round-trip test | Not run |
| AC-02 | Planned typed per-destination snapshot round-trip test | Not run |
| AC-03 | Planned malformed/obsolete snapshot recovery test | Not run |
| AC-04 | Planned encoding/interface review and consumer fake test | Not run |

## Delivery notes

F06’s one-time lease covers DataStore preferences/navigation repository, matching tests, and evidence; persisted formats become reserved after acceptance. Source coverage: F06 ordered steps 5 and 7 and remaining validation/handoff. Constructors and fakes are published for coordinator DI and F05/F07, while [F06](../features/F06-persistence.md) retains format ownership.

## Out of scope

- New user preferences, speculative ROM/provider keys, and destructive recovery.
