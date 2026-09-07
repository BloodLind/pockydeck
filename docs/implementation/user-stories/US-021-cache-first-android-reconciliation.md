# US-021 — Reconcile app changes without blocking cached browsing

- Parent: [F07 — Android discovery and launch adapters](../features/F07-android-catalog.md)
- Status: **Planned**
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

- [ ] **AC-01** — **Given** a persisted catalog and a slow or failed scan, **when** discovery starts, **then** the last successful catalog stays available and the consumer receives explicit refreshing or error state.
- [ ] **AC-02** — **Given** a cancelled scan or partial provider result, **when** reconciliation ends unsuccessfully, **then** it does not clear cached entries or mark inventory complete.
- [ ] **AC-03** — **Given** several startup, resume, and package-change triggers, **when** they overlap, **then** they are serialized or coalesced without concurrent destructive reconciliation.
- [ ] **AC-04** — **Given** an install, update, uninstall, or missed inactive callback, **when** a complete scan or resume follows, **then** the catalog converges using availability changes while stable IDs, favorites, recent history, artwork overrides, and category overrides remain preserved.
- [ ] **AC-05** — **Given** a Home consumer, **when** it observes the adapter lifecycle, **then** it can use supplied refresh hooks and stale-cache/loading/error fixtures without waiting for scan completion to browse cached items.

## Verification

- **AC-01:** Not run — automated repository test for cached data during slow and failed enumeration.
- **AC-02:** Not run — automated cancellation/partial-inventory test.
- **AC-03:** Not run — automated concurrent-trigger test with observable one-at-a-time reconciliation.
- **AC-04:** Not run — automated install/update/uninstall and resume-convergence tests.
- **AC-05:** Not run — manual or Compose fixture review by the F08 consumer.

## Delivery notes

Use only F07’s discovery-pipeline and lifecycle enumeration lease; Room, DI, and domain changes are coordinator deltas. Source coverage is F07 ordered steps 3–4 and 6. See [F07](../features/F07-android-catalog.md) and [the story standard](story-standard.md).

## Out of scope

Main-thread scanning, global monitoring of launches, and clearing user references after an incomplete inventory.
