# F09 — Library and Apps

- Status: **implemented in the native application batch**; see [integrated evidence and remaining device checks](../evidence/F14/native-application-base.md). Individual physical acceptance is not implied.
- User stories: [US-026](../user-stories/US-026-unified-library-browsing.md), [US-027](../user-stories/US-027-android-app-category-browsing.md). This packet remains the scope and acceptance reference; the user-authorized combined delivery workflow is recorded in progress.
- Agent: `gpt-5.6-terra`, reasoning `high`.
- Outcome: browse the unified library or its Android-app view with usable platform/category filters and stable controller selection.
- Prerequisite: F08 accepted. Runs beside F10 and F11 with no shared-file writes.

## Ownership and contracts

Owns `app/src/main/kotlin/dev/handheld/launcher/feature/library/**`, `app/src/main/kotlin/dev/handheld/launcher/feature/apps/**`; matching app JVM/instrumentation test packages; `docs/implementation/evidence/F09/**`. Shared cards, tokens, focus helpers, shell/route registry, DI, domain/data contracts and category persistence are outside the lease.

Consumes the catalog query/category/availability contracts, common card/action mapping from F08, generic collection controls, and F05 restoration. Provides two Route/Screen/ViewModel/immutable-state sets and their route constructor/action dependencies. Library reads all appropriate catalog items; Apps is a filtered view of that same source, never a second store. ROM entries flow into Library later through the existing contract.

## Ordered steps

1. Implement Library heading/count, sort selector, platform strip and square-cover grid. Target six columns only when readable within Home's content rectangle; adapt density centrally provided by shell metrics rather than shrinking labels.
2. Implement Apps heading/count, All/Games/Emulators/Other categories and icon-led grid. Use verified Android metadata plus stored user override precedence from the shared contract; unknown classification stays honest rather than guessing reliable categories.
3. Connect repository flows without hiding cached data during refresh. Preserve long names, image fallbacks, unavailable explanations and useful no-app/empty-filter recovery.
4. Wire A/touch to the shared launch port and Y to details when supported. Filters/sort changes use typed actions and update the footer descriptor; selected chips are independent of controller focus.
5. Apply explicit header/filter/content/dock focus transitions and retain separate filters, selected ID and anchors per destination. Item removal/filtering uses F05 fallback rather than stale indices.
6. Hand constructor signatures and screen factories to coordinator for route/DI registration. Request any genuinely missing shared API once; do not fork a card or repository implementation.

## Excludes

No separate Apps catalog, invented emulator certainty, ROM scanning, custom shell geometry, duplicate physical-key handling, network artwork fetching, or analytics counts beyond actual current content.

## Validation and handoff

Use focused ViewModel/Compose tests for shared catalog filtering, Android game category override, empty-filter recovery, selected-item removal, controller access to sort/filter/grid/dock, and launch/details action agreement. Render Library and Apps with the same shell anchors as Home, including long names and failed icons. Provide actual test/build results and route wiring delta. Coordinator integrates the three wave-7 packets together before the next wave.
