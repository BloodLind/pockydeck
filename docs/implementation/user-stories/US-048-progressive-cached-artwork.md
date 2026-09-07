# US-048 — Show cached artwork progressively without moving items

- Parent: [F17 — Metadata and artwork enrichment](../features/F17-metadata-artwork.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a launcher user, I want artwork to improve in place while local fallbacks remain usable, so that browsing and launching stay stable even with slow or unavailable network.

## Ready when

- [US-047 — Enrich metadata through a durable bounded provider queue](US-047-durable-metadata-enrichment.md) is accepted.
- F16 is accepted and F17’s approved cache/preservation contract and provider queue are integrated.
- F08 local artwork fallback remains available independently of this enrichment work.

## Scope

Persist reusable metadata/artwork references in a bounded cache and decode near displayed dimensions. Attach enriched artwork behind the existing presentation contract while showing local icon/fallback immediately. Preserve fixed card bounds, item order, selection, and scroll during loading, failure, late arrival, cache reuse, and eviction.

## Acceptance criteria

- [ ] **AC-01** — **Given** artwork is pending or fails, **when** a card renders, **then** its local fallback appears immediately in existing bounds.
- [ ] **AC-02** — **Given** artwork arrives after a card is focused or scrolled into view, **when** it updates, **then** only card content changes and no item reorders, resizes, steals focus, or changes scroll.
- [ ] **AC-03** — **Given** artwork has been cached, **when** the network is unavailable later, **then** the cache can be reused; eviction follows the approved bounded-cache strategy.
- [ ] **AC-04** — **Given** network enrichment is unavailable, **when** Android and ROM items are browsed or launched, **then** local launch paths remain usable.

## Verification

- **AC-01:** Not run — pending/failure fallback Compose fixtures.
- **AC-02:** Not run — late-image focus, scroll, order, and dimension tests.
- **AC-03:** Not run — offline cache reuse and bounded-eviction test.
- **AC-04:** Not run — offline launch regression; record before/after card-geometry screenshots as integration evidence.

## Delivery notes

F17 owns enriched artwork/cache work and evidence; local loader and shared presentation signature remain protected by their existing owners. Source coverage: F17 ordered steps 4 and 6. See [F17](../features/F17-metadata-artwork.md) and [the story standard](story-standard.md).

## Out of scope

Replacing local icon/launch paths or adding online Search.
