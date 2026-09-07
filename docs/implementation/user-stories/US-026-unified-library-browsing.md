# US-026 — Browse, filter and sort the unified Library

- Parent: [F09 — Library and Apps](../features/F09-library-apps.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-terra`, reasoning `high`

## User story

As a launcher user, I want to browse all indexed items with platform filtering and sorting, so that I can find and open content without leaving my place.

## Ready when

- [US-025 — Use the launcher as HOME while retaining native system controls](US-025-android-home-role-and-window-loop.md) is accepted.
- The coordinator has accepted F08, integrated its common presentation and launch APIs, and opened F09’s wave-7 lease.
- Catalog query, availability, restoration, generic collection-control, and route integration contracts are available.

## Scope

Build Library heading and current count, sort selector, platform strip, and adaptive square-cover grid inside the existing shell. Connect the unified catalog, shared card/action mapping, restoration, launch, and supported details action. Deliver the route/state/constructor integration request without owning the route registry or DI.

## Acceptance criteria

- [ ] **AC-01** — **Given** cached unified catalog content is refreshing, **when** Library opens, **then** it continues to show that content and its count reflects the current displayed set.
- [ ] **AC-02** — **Given** a platform or sort change removes the selected item from view, **when** the filter is applied, **then** selection uses the F05 valid-item fallback and an empty filter offers a useful recovery.
- [ ] **AC-03** — **Given** controller navigation through Library, **when** focus moves, **then** header, filters, grid, and dock are reachable and selected filters remain separate from controller focus.
- [ ] **AC-04** — **Given** a card supports launch and details, **when** A/touch or supported Y is used, **then** the shared action runs and the footer describes the same available actions.
- [ ] **AC-05** — **Given** long names, missing artwork, unavailable items, large font, or compact metrics, **when** the grid renders, **then** labels and recovery explanations remain readable and six columns are used only when shared metrics permit it.
- [ ] **AC-06** — **Given** the user returns to Library, **when** state restores, **then** its platform filter, sort, selected ID, and anchor are restored independently of Apps.

## Verification

- **AC-01:** Not run — ViewModel test for cached catalog and displayed count during refresh.
- **AC-02:** Not run — automated filter/sort selection-fallback and empty-recovery tests.
- **AC-03:** Not run — Compose controller focus traversal test.
- **AC-04:** Not run — Compose test for action/footer agreement.
- **AC-05:** Not run — standard, compact, and large-font screenshot review.
- **AC-06:** Not run — state restoration test across Library and Apps destinations.

## Delivery notes

The F09 Library lease contains the screen, state, tests, and evidence. Shared cards, category/query policy, routes, and DI remain coordinator owned. Source coverage: F09 ordered steps 1 and 3–6. See [F09](../features/F09-library-apps.md) and [the story standard](story-standard.md).

## Out of scope

ROM scanning, bespoke shell geometry, duplicate cards or input paths, and network artwork.
