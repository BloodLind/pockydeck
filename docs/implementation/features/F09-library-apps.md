# F09 — Library and Apps

- Status: **implemented in the native application batch**; see [integrated evidence and remaining device checks](../evidence/F14/native-application-base.md). Individual physical acceptance is not implied.
- User stories: [US-026](../user-stories/US-026-unified-library-browsing.md), [US-027](../user-stories/US-027-android-app-category-browsing.md). This packet remains the scope and acceptance reference; the user-authorized combined delivery workflow is recorded in progress.
- Agent: `gpt-5.6-terra`, reasoning `high`.
- Outcome: browse ROMs and Android games in Library, and other Android apps in Apps, with scoped filters and stable controller selection.
- Prerequisite: F08 accepted. Runs beside F10 and F11 with no shared-file writes.

## Ownership and contracts

Owns `app/src/main/kotlin/dev/handheld/launcher/feature/library/**`, `app/src/main/kotlin/dev/handheld/launcher/feature/apps/**`; matching app JVM/instrumentation test packages; `docs/implementation/evidence/F09/**`. Shared cards, tokens, focus helpers, shell/route registry, DI, domain/data contracts and category persistence are outside the lease.

Consumes the catalog query/category/availability contracts, common card/action mapping from F08, generic collection controls, and F05 restoration. Provides two Route/Screen/ViewModel/immutable-state sets and their route constructor/action dependencies. Both destinations read the same catalog, never separate stores. Library contains available ROMs and Android apps whose effective category is `GAME`; Apps contains available Android apps whose effective category is not `GAME`, including emulators. A ROM remains a game regardless of a legacy category override. Existing availability and reference-preservation rules still apply.

The 8 September 2026 page revision supersedes the original broad Library and Apps/Games rules. The current scope below follows [shared contracts](../contracts.md#september-page-membership-search-and-interaction-revision) and the [design system](../../design-system.md); historical evidence and physical acceptance status are unchanged. Favorites continues to include any available favorite game or app across these groups.

## Ordered steps

1. Implement Library heading/count, sort selector, platform strip and square-cover grid. Filters are All, Android when Android games are present, and consoles with available ROMs. Console card captions and filter pills use abbreviations such as GBA and PSX. Target six columns only when readable within Home's content rectangle; adapt density centrally provided by shell metrics rather than shrinking labels.
2. Implement Apps heading/count, All and populated Emulators/Other categories, and an icon-led grid. Known emulator packages default to `EMULATOR`; otherwise Android's declared game category or legacy game flag defaults to `GAME`, with `OTHER` for remaining apps. Stored user category overrides take precedence for Android items, including which destination contains them. Counts and filters use this same effective membership; category navigation opens the matching destination.
3. Connect repository flows without hiding cached data during refresh. Preserve long names, image fallbacks, unavailable explanations and useful no-app/empty-filter recovery.
4. Wire A/touch to the shared launch port and Y to details when supported. Filters/sort changes use typed actions and update the footer descriptor; selected chips are independent of controller focus.
5. Apply explicit header/filter/content/dock focus transitions and retain separate filters, selected ID and anchors per destination. Selecting or reselecting a populated page focuses its selected surviving card, or its first card, after the lazy item is placed. Touch selects the touched card before activation. Item removal/filtering uses F05 fallback rather than stale indices; obsolete or irrelevant saved filter keys resolve to All.
6. Hand constructor signatures and screen factories to coordinator for route/DI registration. Request any genuinely missing shared API once; do not fork a card or repository implementation.

## Excludes

No separate Apps catalog, invented emulator certainty, ROM scanning, custom shell geometry, duplicate physical-key handling, network artwork fetching, or analytics counts beyond actual current content.

## Validation and handoff

Use focused ViewModel/Compose tests for shared catalog filtering, Android game category override, empty-filter recovery, selected-item removal, controller access to sort/filter/grid/dock, and launch/details action agreement. Render Library and Apps with the same shell anchors as Home, including long names and failed icons. Provide actual test/build results and route wiring delta. Coordinator integrates the three wave-7 packets together before the next wave.
