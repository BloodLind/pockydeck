# F04 — Persistent shell and navigation

- Status: **planned**.
- User stories: [US-011](../user-stories/US-011-persistent-home-shaped-shell.md), [US-012](../user-stories/US-012-destination-and-origin-navigation.md), [US-013](../user-stories/US-013-shell-keyboard-and-inset-layout.md). Dispatch one ready story; this packet remains the technical ownership and feature-gate reference.
- Agent: `gpt-5.6-terra`, reasoning `high`.
- Outcome: one stable Home-shaped shell surrounds all six destination positions and launcher-local details/dialog content.
- Prerequisite: F03 accepted. Uses fixtures and explicit unavailable telemetry; live services are not required.

## Ownership and contracts

Initial-creation lease: `app/src/main/kotlin/dev/handheld/launcher/shell/**`, `app/src/main/kotlin/dev/handheld/launcher/navigation/**` **except** its `restoration/**` subtree (F05); matching app JVM/instrumentation test packages; `docs/implementation/evidence/F04/**`. Production `feature/**`, `contract/**`, `MainActivity.kt`, DI, theme/metrics, and the debug gallery remain outside this lease. Coordinator owns Activity wiring and any gallery-shell demonstration edits. After acceptance, shell/navigation registry changes are reserved for integration.

Consumes F01 typed destination/action/request contracts and F02/F03 visual APIs. Provides `LauncherShell`, root content bounds/insets policy, dock order Home/Library/Apps/Favorites/Settings/Search, overlay host, shell state, and registration points for real routes. Provides fixture route hosts until the relevant feature packet lands. Consumes available/unavailable/unsupported status presentation; F13 supplies real values later.

## Ordered steps

1. Compose backdrop, fixed status strip, one destination content slot, dock, contextual footer, and launcher overlay host exactly once. Compute metrics at the root; give normal pages the same content rectangle and gutter.
2. Build the six-position dock with separate selected-destination and focused-control states. Keep the route registry centralized; later features deliver composables/ViewModel contracts for coordinator registration.
3. Implement navigation semantics: dock switching replaces active destination without a Back stack; non-Home dock destinations return to Home; shortcut Search records origin; details/dialogs return to their origin. Pass per-destination snapshot keys through the F01 contract; F05 supplies restoration machinery.
4. Render footer prompts from the same typed action descriptor used by the input path. Hide/disable unavailable actions. Leave status readings explicit unavailable/unsupported rather than showing sample telemetry.
5. Define the IME exception centrally: query content remains visible, results can shrink/scroll, and bottom shell area moves above keyboard when feasible. Keep transient system bars compatible with preserved state; coordinator wires actual Activity window handling in F08.
6. Integrate fixture Home through the coordinator's gallery hook and compare normal-mode shell anchors with Home. Other fixtures adapt their content, never change dock/footer geometry.

## Excludes

No page-specific data rules, native sensor polling, ROM UI, process indicators, duplicated shell around details, or interception of Android system controls.

## Validation and handoff

Render static Home and several content templates through the same shell. Verify clock/status presentation updates do not recreate page state, fixture route switching preserves independent keys, and details/shortcut Search have correct origin policy. Save anchor comparison images and note physical calibration gaps. Hand off route registration points, action/footer contract, insets policy, and fixture navigation to F05. Coordinator merges the shared route API once before dependent work begins.
