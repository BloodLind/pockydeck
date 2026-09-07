# US-029 — Inspect item details and return to the initiating place

- Parent: [F10 — Favorites and item details](../features/F10-favorites-details.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-terra`, reasoning `high`

## User story

As a launcher user, I want full item information and only supported local actions, so that I can act on an item and resume browsing where I started.

## Ready when

- [US-028 — Keep and browse favorite references](US-028-favorite-collection-management.md) is accepted.
- The coordinator has accepted F08 and integrated F10’s shared action, origin, and restoration contracts; F10’s wave-7 lease applies, while final F10 acceptance follows all of its child stories.
- Android app-info dispatch is available only through a coordinator-owned Activity request integration.

## Scope

Create the Details route, immutable state, ViewModel, and screen within the shared shell. Show full title, platform, artwork fallback, availability explanation, and supported Open/Play, favorite, and app-info actions. Keep menus within one focus scope; use the common launch path; return to the exact initiating snapshot when valid. Later emulator and artwork-correction capabilities remain absent.

## Acceptance criteria

- [ ] **AC-01** — **Given** an item with a title longer than a card caption, **when** Details opens, **then** it presents the full title, platform, artwork fallback, and any availability explanation in the shared shell.
- [ ] **AC-02** — **Given** an item has supported Open/Play, favorite, or app-info actions, **when** Details renders, **then** only those supported actions are visible and A/touch behavior agrees with the footer.
- [ ] **AC-03** — **Given** app info is supported, **when** the user invokes it, **then** Details submits the coordinator-integrated Activity request; **given** it is unsupported, **when** Details renders, **then** no inert app-info control is shown.
- [ ] **AC-04** — **Given** an action menu is opened, **when** controller focus moves or it is dismissed, **then** focus remains contained in the menu and returns to the initiating Details action.
- [ ] **AC-05** — **Given** the user backs out, dismisses a menu, or returns after a launch, **when** the origin item still exists, **then** its destination, selected ID, filter, and anchor are restored; **given** it disappeared, **then** F05 fallback is used.
- [ ] **AC-06** — **Given** Details is viewed or favorite is toggled, **when** the operation completes, **then** recent order does not change and direct card launch remains available.

## Verification

- **AC-01:** Not run — Compose screenshot with a long title, fallback artwork, and unavailable item.
- **AC-02:** Not run — automated supported-action visibility and footer agreement test.
- **AC-03:** Not run — Activity request-port integration test and unsupported fixture test.
- **AC-04:** Not run — Compose focus-scope and dismissal test.
- **AC-05:** Not run — ViewModel origin restoration and removed-item fallback tests.
- **AC-06:** Not run — automated recency immutability and direct-launch tests.

## Delivery notes

F10 owns the Details feature paths, tests, and evidence. Route/overlay, app-info dispatch, and shared contracts remain coordinator-owned. Source coverage: F10 ordered steps 3–6 and remaining validation/handoff. See [F10](../features/F10-favorites-details.md) and [the story standard](story-standard.md).

## Out of scope

Save management, provider lookups, emulator setup, artwork correction, analytics, and a duplicate shell.
