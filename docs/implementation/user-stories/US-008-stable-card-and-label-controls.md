# US-008 — Render items and status with stable reusable presentation

| Field | Value |
| --- | --- |
| Parent feature | [F03 — Reusable controls and debug gallery](../features/F03-controls-gallery.md) |
| Status | Accepted |
| Type | Enabler |
| Implementation agent | `gpt-5.6-luna` / `medium` |

## User story

As a developer, I want generic cards, captions, fallback artwork and labels, so that content changes keep the same readable layout and focus behavior.

## Ready when

- [US-007 — Compose accessible activation, filtering and query controls](US-007-reusable-input-controls.md) is accepted.
- The coordinator has accepted F02 and its integrated evidence, opening F03 work.
- F03 wave 3, its exact lease, validation, and coordinator handoff requirements apply. F03’s completion gate is assessed after its child stories; later features wait for coordinator acceptance.

## Scope

Implement badges and status presentation, `CoverTile`, `AppIconTile`, `SearchResultCard`, `ArtworkFallback`, and `TileCaption`. Provide `HomeCover`, `CollectionCover`, and `AppIcon` variants with one focus treatment and generic content/callback slots. Fit app icons, crop artwork without stretching, and bound titles without auto-shrinking text.

## Acceptance criteria

- [x] **AC-01** — **Given** pending, absent, or failed artwork, **when** its card renders, **then** only the image content is replaced inside unchanged card bounds and the accessible item name remains available.
- [x] **AC-02** — **Given** Home, collection, and app variants, **when** they receive focus or activation, **then** each uses the same semantics while differing icon shapes fit and cover artwork remains unstretched.
- [x] **AC-03** — **Given** a long caption or a status value, **when** it renders, **then** bounded lines or ellipsis preserve tile height and available, unavailable, and unsupported status states remain distinct.
- [x] **AC-04** — **Given** a card control is used, **when** it receives presentation data and callbacks, **then** it does not decide catalog type, emulator, navigation, or image data source.

## Verification

| Criterion | Evidence | Status |
| --- | --- | --- |
| AC-01 | [Native tests and inspected render](../evidence/F03/US-008-cards-labels.md) | Passed |
| AC-02 | [Native tests and inspected render](../evidence/F03/US-008-cards-labels.md) | Passed |
| AC-03 | [Native tests and inspected render](../evidence/F03/US-008-cards-labels.md) | Passed |
| AC-04 | [Native tests and inspected render](../evidence/F03/US-008-cards-labels.md) | Passed |

## Delivery notes

F03 grants the initial lease for cards and label controls with examples/tests; production mapping remains F08. Source coverage: F03 ordered step 2. The exact creation boundaries remain in [F03](../features/F03-controls-gallery.md); no new shared signature is granted outside that packet.

## Out of scope

- Domain-to-card mapping, artwork fetching, or per-page duplicate cards.
- Running badges and fabricated telemetry.
