# US-033 — Configure confirm and Back mapping in Settings

- Parent: [F12 — Basic Settings](../features/F12-settings.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-terra`, reasoning `high`

## User story

As a launcher user, I want to choose the launcher confirm/back mapping, so that physical actions and displayed glyphs match my preferred controls.

## Ready when

- [US-027 — Browse installed apps by honest category](US-027-android-app-category-browsing.md), [US-029 — Inspect item details and return to the initiating place](US-029-item-details-and-origin-return.md), and [US-032 — Act on Search results and restore the correct origin](US-032-search-actions-and-origin-restoration.md) are accepted.
- The coordinator has accepted F09, F10, and F11 together, integrated their shared hooks, and opened F12’s wave-8 lease.
- Typed persisted mapping and the one shared input path are available through coordinator-owned contracts.

## Scope

Build readable Settings categories and grouped rows with explicit focus transitions. Bind mapping selection to the typed persisted preference and coordinator-integrated input/glyph presentation. Provide Settings extension slots while keeping later ROM, emulator, and provider panels absent until they work.

## Acceptance criteria

- [ ] **AC-01** — **Given** Settings has grouped rows, **when** the user traverses it by controller, **then** every implemented row is reachable and its state-driven current value is visible in the shared shell.
- [ ] **AC-02** — **Given** no mapping preference has been saved, **when** Settings and input initialize, **then** A is confirm and B is Back.
- [ ] **AC-03** — **Given** the user chooses an available confirm/back mapping, **when** it is saved and input is used after restoration, **then** the persisted mapping changes physical confirm/back behavior and controller glyphs together through the single shared input path.
- [ ] **AC-04** — **Given** Settings is displayed before F13 device status is integrated, **when** the current status provider is unavailable, **then** the page remains usable and represents that status truthfully.
- [ ] **AC-05** — **Given** later source, emulator, or provider functionality is unavailable, **when** Settings renders, **then** it exposes extension slots without inactive controls for those features.

## Verification

- **AC-01:** Not run — Compose controller traversal and grouped-row screenshot test.
- **AC-02:** Not run — automated default-preference and glyph fixture test.
- **AC-03:** Not run — preference round-trip plus semantic input/glyph agreement test.
- **AC-04:** Not run — Compose unavailable-status provider fixture.
- **AC-05:** Not run — manual UI review and automated absence assertion for later panels.

## Delivery notes

This is within the F12 Settings structure/mapping lease. Preference encoding, shared input binding, and DI remain coordinator owned. Source coverage: F12 ordered steps 1–2 and step 5. See [F12](../features/F12-settings.md) and [the story standard](story-standard.md).

## Out of scope

Emulator remapping, a second input path, unapproved preferences, and nonfunctional later settings panels.
