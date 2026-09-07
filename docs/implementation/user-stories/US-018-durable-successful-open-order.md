# US-018 — Record successful opens once in deterministic recent order

| Field | Value |
| --- | --- |
| Parent feature | [F06 — Catalog and preferences persistence](../features/F06-persistence.md) |
| Status | Ready |
| Type | Enabler |
| Implementation agent | `gpt-5.6-sol` / `high` |

## User story

As a developer, I want persistent recency that records acknowledged successful dispatch exactly once, so that Home order stays correct through repeats, failures and clock changes.

## Ready when

- [US-017 — Persist a cache that survives catalog reconciliation](US-017-durable-catalog-and-inventory-cache.md) is accepted.
- The coordinator has accepted F01 and its integrated evidence, opening F06 work.
- F06 wave 2, its exact lease, validation, and coordinator handoff requirements apply. F06’s completion gate is assessed after its child stories; later features wait for coordinator acceptance.

## Scope

Implement serialized increasing open-order keys and one recency record per stable item. Align duplicate and transaction handling with F01 acknowledged launch results while leaving external dispatch separate. Order unopened items deterministically by title/ID after recent items.

## Acceptance criteria

- [ ] **AC-01** — **Given** persisted order `[C, B, A]`, **when** B is successfully opened once or repeatedly, **then** the order becomes and remains `[B, C, A]` with one B.
- [ ] **AC-02** — **Given** failed dispatch or a repeated acknowledgement for one operation, **when** it is recorded, **then** failure leaves order unchanged and acknowledgement cannot add a second promotion.
- [ ] **AC-03** — **Given** concurrent successful opens, **when** recency is written, **then** they receive deterministic serialized order independent of wall-clock ties or clock changes.
- [ ] **AC-04** — **Given** the application restarts, **when** recency and restoration are reloaded, **then** order persists and no external launch request is replayed.
- [ ] **AC-05** — **Given** recent items and unopened items share the catalog, **when** Home ordering is read, **then** recent items precede unopened items and unopened items use deterministic title/ID ordering to break ties.

## Verification

| Criterion | Evidence | Status |
| --- | --- | --- |
| AC-01 | Planned actual Room ordering/repeat test | Not run |
| AC-02 | Planned failure and duplicate-operation transaction test | Not run |
| AC-03 | Planned concurrent-write deterministic-order test | Not run |
| AC-04 | Planned restart/persistence and no-replay test | Not run |
| AC-05 | Planned mixed recent/unopened ordering fixture test | Not run |

## Delivery notes

F06’s creation lease covers recency repository/local transaction implementation and actual Room tests/schema notes. Source coverage: F06 ordered step 2 (successful-open and unopened ordering) and step 4. The F01 launch contract governs acknowledgement semantics; [F06](../features/F06-persistence.md) defines persistence ownership.

## Out of scope

- Running-process state, session analytics, or recording opens outside this launcher.
