# US-016 — Restore each destination by item identity and scroll anchor

| Field | Value |
| --- | --- |
| Parent feature | [F05 — Controller input and focus restoration](../features/F05-controller-restoration.md) |
| Status | Planned |
| Type | Feature |
| Implementation agent | `gpt-5.6-sol` / `high` |

## User story

As a launcher user, I want my selected item and visible position restored when content or lifecycle changes, so that I can continue browsing from a valid familiar place.

## Ready when

- [US-015 — Move focus across page regions and contained dialogs](US-015-page-and-modal-focus-navigation.md) is accepted.
- The coordinator has accepted F04 and F06 with integrated evidence, opening F05 work.
- F05 wave 5, its exact lease, validation, and coordinator handoff requirements apply. F05’s completion gate is assessed after its child stories; later features wait for coordinator acceptance.

## Scope

Restore selected ID and first-visible ID/offset after data and target composition are available. Persist compact per-destination query, filter, sort, selection, and anchor keys through F06 durable snapshots and appropriate recreation state. Apply shared fallbacks after reordering, filtering, removal, and empty collections, and publish restoration helpers.

## Acceptance criteria

- [ ] **AC-01** — **Given** a selected item is reordered, **when** restoration runs, **then** its stable identity is retained rather than its old numeric index and a valid old anchor is reused or adjusted to keep the selection visible.
- [ ] **AC-02** — **Given** the restored selected item is offscreen, **when** content is composed, **then** it is scrolled into composition before focus is requested.
- [ ] **AC-03** — **Given** a saved target is unavailable, **when** restoration chooses a replacement, **then** it follows saved ID, nearest survivor, first item, empty-state action, then active dock item.
- [ ] **AC-04** — **Given** a destination switch, recreation, durable snapshot, or artwork change, **when** state is restored, **then** destinations retain independent keys and artwork does not steal focus or alter selection.

## Verification

| Criterion | Evidence | Status |
| --- | --- | --- |
| AC-01 | Planned reorder/anchor restoration Compose test | Not run |
| AC-02 | Planned offscreen-target scroll-before-focus test | Not run |
| AC-03 | Planned deterministic fallback test | Not run |
| AC-04 | Planned lifecycle/key-isolation and artwork-update test | Not run |

## Delivery notes

F05’s creation lease covers `navigation/restoration` and UI restoration helpers/tests. Snapshot encoding and shell attachments remain coordinator-owned. Source coverage: F05 ordered step 4, step 5 (persistence/content-change restoration), and restoration validation/handoff. The [F05 packet](../features/F05-controller-restoration.md) is the ownership reference.

## Out of scope

- Persisting Compose objects or complete lists, and guaranteeing identical pixels after reordering.
