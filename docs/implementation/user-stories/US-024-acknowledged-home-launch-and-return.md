# US-024 — Open an item once and return with it selected first

- Parent: [F08 — First working HOME loop](../features/F08-home-loop.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a launcher user, I want a successful open to promote the same item and restore my selection on return, so that recent items stay easy to reopen without losing my place.

## Ready when

- [US-023 — Browse cached Home items in the shared carousel](US-023-cached-home-carousel.md) is accepted.
- The coordinator has accepted F05 and F07 and integrated the dispatch and restoration hooks. F08's final integration gate follows acceptance of its child stories.
- The ordinary launcher activity lifecycle and the shared dispatch path are available. Android default-HOME role integration is intentionally deferred to US-025.

## Scope

Implement one common path: save an origin snapshot, suppress duplicate pending activation, revalidate and dispatch, acknowledge the result, and record a successful open exactly once. Bind F05 identity and anchor restoration after an order change and after returning through ordinary launcher lifecycle. Publish the launch and action ports for later destinations.

## Acceptance criteria

- [ ] **AC-01** — **Given** Home order `[C, B, A]` with B selected, **when** B dispatches successfully and the user returns, **then** Home shows `[B, C, A]` with one selected B.
- [ ] **AC-02** — **Given** B is activated repeatedly while its request is pending, **when** dispatch acknowledgement arrives, **then** one dispatch is issued and B appears once in the resulting order.
- [ ] **AC-03** — **Given** dispatch is rejected or fails, **when** its acknowledgement is handled, **then** Home keeps its prior recent order and exposes a recoverable error.
- [ ] **AC-04** — **Given** an ordering change or an invalid prior anchor, **when** Home restores, **then** it retains the stable selected ID when available or uses the F05 fallback and nearest feasible visible anchor.
- [ ] **AC-05** — **Given** the user only focuses an item, opens details, receives artwork, or recollects state, **when** that event occurs, **then** it does not promote recency or replay an already acknowledged launch.

## Verification

- **AC-01:** Not run — automated ViewModel test using the specified `[C, B, A]` fixture and ordinary return event.
- **AC-02:** Not run — automated duplicate-pending activation test.
- **AC-03:** Not run — automated rejected-dispatch state and order-preservation test.
- **AC-04:** Not run — automated identity/anchor restoration tests including invalid anchor and removed item fallback.
- **AC-05:** Not run — automated history-mutation tests for focus, details, artwork, and recollection.

## Delivery notes

This is the F08 launch orchestration and Home restoration binding lease. Shared Activity, persistence, and domain deltas require coordinator integration. Source coverage: F08 ordered steps 3–4 and the common launch/action interface in step 7. Android default-HOME selection and manifest wiring are US-025’s integration journey. See [F08](../features/F08-home-loop.md) and [the story standard](story-standard.md).

## Out of scope

Default-HOME role registration, external-process guarantees, global open tracking, and task-management claims.
