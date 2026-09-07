# US-021 — Reconcile app changes without blocking cached browsing

- Parent: [F07 — Android discovery and launch adapters](../features/F07-android-catalog.md)
- Status: **Accepted**
- Type: **Enabler**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a developer, I want coalesced background discovery that preserves a usable cache, so that package changes converge without destructive refreshes.

## Ready when

- [US-020 — Discover real launchable Android components](US-020-real-android-component-discovery.md) is accepted.
- The coordinator has accepted F06 and integrated its catalog write boundary; F07’s wave-3 lease applies, while final F07 acceptance follows all of its child stories.
- Room, catalog identity, favorite, recent, and override contracts are available through the existing coordinator-owned boundaries.

## Scope

Read persisted catalog data first, then enumerate off the main thread. Serialize or coalesce startup, resume, and active package-change triggers. Reconcile installation, update, and removal through availability transitions without replacing identities or user-owned references. Provide lifecycle or refresh hooks and stale-cache, loading, and error fixtures for Home consumers.

## Acceptance criteria

- [x] **AC-01** — **Given** a persisted catalog and a slow or failed scan, **when** discovery starts, **then** the last successful catalog stays available and the consumer receives explicit refreshing or error state.
- [x] **AC-02** — **Given** a cancelled scan or partial provider result, **when** reconciliation ends unsuccessfully, **then** it does not clear cached entries or mark inventory complete.
- [x] **AC-03** — **Given** several startup, resume, and package-change triggers, **when** they overlap, **then** they are serialized or coalesced without concurrent destructive reconciliation.
- [x] **AC-04** — **Given** an install, update, uninstall, or missed inactive callback, **when** a complete scan or resume follows, **then** the catalog converges using availability changes while stable IDs, favorites, recent history, artwork overrides, and category overrides remain preserved.
- [x] **AC-05** — **Given** a Home consumer, **when** it observes the adapter lifecycle, **then** it can use supplied refresh hooks and stale-cache/loading/error fixtures without waiting for scan completion to browse cached items.

## Verification

- **AC-01:** Passed — see [JVM, Room, protected-broadcast and API evidence](../evidence/F07/US-021.md).
- **AC-02:** Passed — see [JVM, Room, protected-broadcast and API evidence](../evidence/F07/US-021.md).
- **AC-03:** Passed — see [JVM, Room, protected-broadcast and API evidence](../evidence/F07/US-021.md).
- **AC-04:** Passed — see [JVM, Room, protected-broadcast and API evidence](../evidence/F07/US-021.md).
- **AC-05:** Passed — see [JVM, Room, protected-broadcast and API evidence](../evidence/F07/US-021.md).

## Delivery notes

Use only F07’s discovery-pipeline and lifecycle enumeration lease; Room, DI, and domain changes are coordinator deltas. Source coverage is F07 ordered steps 3–4 and 6. See [F07](../features/F07-android-catalog.md) and [the story standard](story-standard.md).

## Out of scope

Main-thread scanning, global monitoring of launches, and clearing user references after an incomplete inventory.
