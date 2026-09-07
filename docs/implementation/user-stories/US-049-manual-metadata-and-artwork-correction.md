# US-049 — Correct metadata matches and artwork without losing overrides

- Parent: [F17 — Metadata and artwork enrichment](../features/F17-metadata-artwork.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a launcher user, I want to review provider status and correct an item's match or artwork, so that my explicit correction remains authoritative across refreshes.

## Ready when

- [US-048 — Show cached artwork progressively without moving items](US-048-progressive-cached-artwork.md) is accepted.
- F16 is accepted and F17’s provider, cache, correction, and preservation contracts are published.
- Actual migration, job, and offline evidence is required before F18 readiness.

## Scope

Implement provider configuration/status and functional manual match/artwork correction through existing Settings and Details slots. Persist correction provenance through reviewed operations, restore origin focus on completion or cancellation, and publish provider/privacy/attribution plus offline correction evidence.

## Acceptance criteria

- [ ] **AC-01** — **Given** provider matching returns no match or ambiguity, **when** the user views the item, **then** functional correction options appropriate to the approved provider are available.
- [ ] **AC-02** — **Given** the user accepts a correction, **when** provider retry, refresh, or discovery rescan runs, **then** the correction and its provenance persist without silent overwrite.
- [ ] **AC-03** — **Given** correction is cancelled or completed, **when** its UI closes, **then** focus restores to the initiating item and favorite/recent state remains unchanged.
- [ ] **AC-04** — **Given** the provider is offline or fails, **when** content is browsed, **then** its status is clear while corrected, cached, or local content and Android/ROM launch remain usable.

## Verification

- **AC-01:** Not run — no-match/ambiguous correction UI tests.
- **AC-02:** Not run — retry/refresh/rescan override-provenance preservation tests.
- **AC-03:** Not run — modal origin-focus and history immutability test.
- **AC-04:** Not run — offline/provider-failure launch and content regression matrix.

## Delivery notes

F17 owns metadata settings/details correction work and evidence. Shared contracts, schema, credentials, and registration remain coordinator owned. Source coverage: F17 ordered step 5 and validation/handoff. See [F17](../features/F17-metadata-artwork.md) and [the story standard](story-standard.md).

## Out of scope

Another provider, arbitrary data upload, and a full metadata-management product.
