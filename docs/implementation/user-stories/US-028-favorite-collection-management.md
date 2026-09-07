# US-028 — Keep and browse favorite references

- Parent: [F10 — Favorites and item details](../features/F10-favorites-details.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-terra`, reasoning `high`

## User story

As a launcher user, I want a filtered collection of items I favorite, so that my chosen content remains easy to find without duplicating or deleting it.

## Ready when

- [US-025 — Use the launcher as HOME while retaining native system controls](US-025-android-home-role-and-window-loop.md) is accepted.
- The coordinator has accepted F08, integrated shared favorite, catalog, launch, presentation, and restoration hooks, and opened F10’s wave-7 lease.
- All/Games/Apps filtering is supported by existing catalog contracts.

## Scope

Build Favorites with reusable collection layout and All/Games/Apps filters that resolve stable references against the catalog. Connect shared favorite add/remove operations, recovery for removal and empty states, shared cards and launch behavior, and the route/state constructor handoff.

## Acceptance criteria

- [ ] **AC-01** — **Given** an item is favorited, **when** Favorites loads, **then** it resolves the existing stable catalog item rather than creating a duplicate catalog object.
- [ ] **AC-02** — **Given** All, Games, or Apps is selected, **when** the filtered collection renders, **then** it shows matching favorite references only.
- [ ] **AC-03** — **Given** a favorite becomes unavailable or catalog discovery runs again, **when** Favorites updates, **then** its reference is retained according to active-list policy rather than erased.
- [ ] **AC-04** — **Given** the focused favorite is removed, **when** another visible item remains, **then** focus moves to the nearest surviving item; **given** it was the only item, **when** it is removed, **then** a Library action is available.
- [ ] **AC-05** — **Given** a favorite is added, removed, or viewed, **when** the operation completes, **then** it does not uninstall content, delete ROM data, or promote recent order.
- [ ] **AC-06** — **Given** a favorite card is launched, **when** its supported action runs, **then** it uses the shared launch/card/restoration behavior.

## Verification

- **AC-01:** Not run — automated stable-reference versus duplicate-object test.
- **AC-02:** Not run — Compose/ViewModel tests for All, Games, and Apps filters.
- **AC-03:** Not run — automated rediscovery and unavailable-reference preservation test.
- **AC-04:** Not run — Compose focus-restoration tests for last, only, and adjacent removal.
- **AC-05:** Not run — automated history and content-mutation assertions.
- **AC-06:** Not run — shared-launch port integration test.

## Delivery notes

The F10 Favorites lease owns this screen, tests, and evidence. Repository and action contracts are consumed rather than rewritten. Source coverage: F10 ordered steps 1–2 and its favorite filtering/removal validation. See [F10](../features/F10-favorites-details.md) and [the story standard](story-standard.md).

## Out of scope

Duplicate favorite objects, file or uninstall management, and managing ROM source content.
