# F10 — Favorites and item details

- Status: **planned**.
- User stories: [US-028](../user-stories/US-028-favorite-collection-management.md), [US-029](../user-stories/US-029-item-details-and-origin-return.md). Dispatch one ready story; this packet remains the technical ownership and feature-gate reference.
- Agent: `gpt-5.6-terra`, reasoning `high`.
- Outcome: favorite items remain easy to find, and details show the full item title and supported local actions without losing the user's place.
- Prerequisite: F08 accepted. Runs beside F09 and F11 with no shared-file writes.

## Ownership and contracts

Owns `app/src/main/kotlin/dev/handheld/launcher/feature/favorites/**`, `app/src/main/kotlin/dev/handheld/launcher/feature/details/**`; matching app JVM/instrumentation test packages; `docs/implementation/evidence/F10/**`. The coordinator owns route/overlay wiring, Android app-info dispatch integration, shared action/domain contracts, persistence schema and generic controls.

Consumes favorite references/catalog flows, F08 item presentation/launch ports, F05 restoration, and F04 origin route policy. Provides Favorites and Details route/state/ViewModel/screen factories, supported detail-action descriptors, and origin-return requests. Any category override UI uses an existing approved action/persistence contract; propose missing shared support centrally.

## Ordered steps

1. Build Favorites with the reusable collection layout and All/Games/Apps filters. Resolve references against the catalog; do not copy objects into a separate favorite library.
2. Support favorite add/remove through the shared repository. Removing a favorite from the visible grid restores nearest surviving focus; the empty action opens Library. Unavailability preserves the reference and follows the catalog's active-list policy.
3. Build details as launcher content within the same shell: full title/platform, artwork fallback, availability explanation, Open/Play, favorite and Android app info only when supported. Launching remains possible directly from cards.
4. Use typed supported actions, the common launch path, and one contained focus scope for any action menu/dialog. Request coordinator integration of app-info Activity dispatch rather than adding platform calls to visual controls.
5. Return from details/menu to the exact origin destination, selected ID, filter and anchor when possible; apply normal fallback if the item disappeared. Viewing details and toggling a favorite do not promote recency.
6. Prepare extension points for later emulator selection and artwork correction as absent capabilities, not inert controls. Deliver route constructors and coordinator wiring delta after focused tests.

## Excludes

No uninstall/file-delete controls, save-state management, playtime/session screens, process state, duplicate shell, ROM/emulator setup implementation, or provider lookup UI.

## Validation and handoff

Test favorite references surviving rediscovery/unavailability, removal of the focused last/only item, all filters, details full-title presentation, supported action visibility, and origin restoration after launch/menu dismissal. Verify favorite/remove/details never changes recent order. Capture full/empty Favorites and details in the common shell. Hand the shared-file delta and actual results to coordinator for integration with F09/F11.
