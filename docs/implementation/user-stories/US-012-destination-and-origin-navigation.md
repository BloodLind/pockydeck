# US-012 — Switch destinations and return to the correct origin

| Field | Value |
| --- | --- |
| Parent feature | [F04 — Persistent shell and navigation](../features/F04-shell-navigation.md) |
| Status | Planned |
| Type | Feature |
| Implementation agent | `gpt-5.6-terra` / `high` |

## User story

As a launcher user, I want predictable dock and Back navigation, so that I can change pages without accumulating an unexpected history.

## Ready when

- [US-011 — Keep a single Home-shaped shell around launcher content](US-011-persistent-home-shaped-shell.md) is accepted.
- The coordinator has accepted F03 and its integrated evidence, opening F04 work.
- F04 wave 4, its exact lease, validation, and coordinator handoff requirements apply. F04’s completion gate is assessed after its child stories; later features wait for coordinator acceptance.

## Scope

Implement the fixed Home, Library, Apps, Favorites, Settings, and Search dock with selected state separate from focus. Centralize fixture route registration and apply dock-to-Home Back, shortcut Search origin, and details/dialog origin policy. Pass per-destination snapshot keys through existing contracts for F05 restoration.

## Acceptance criteria

- [ ] **AC-01** — **Given** a non-Home dock destination, **when** Back is invoked, **then** it reaches Home; **given** root Home, **when** Back is invoked, **then** the launcher stays open.
- [ ] **AC-02** — **Given** shortcut Search or details/dialog content is entered from a destination, **when** it is dismissed or Back is invoked, **then** it returns to the initiating origin.
- [ ] **AC-03** — **Given** a dock destination is selected while another control is focused, **when** the dock renders, **then** selection does not claim controller focus is on the destination.
- [ ] **AC-04** — **Given** fixture destinations and later registered features, **when** they use the route boundary, **then** each has independent snapshot keys through one published registration point.

## Verification

| Criterion | Evidence | Status |
| --- | --- | --- |
| AC-01 | Planned navigation behavior test | Not run |
| AC-02 | Planned shortcut/details/dialog origin tests | Not run |
| AC-03 | Planned selected-versus-focused dock capture | Not run |
| AC-04 | Planned fixture registration and key-isolation test | Not run |

## Delivery notes

F04’s creation lease covers navigation except `restoration/**`, dock route hooks, and matching tests/evidence; the registry becomes reserved after acceptance. Source coverage: F04 ordered steps 2–3. See [F04](../features/F04-shell-navigation.md) for the precise lease.

## Out of scope

- Durable restoration implementation and actual feature pages.
