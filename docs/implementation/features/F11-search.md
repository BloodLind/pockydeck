# F11 — Local Search and system keyboard

- Status: **implemented in the native application batch**; see [integrated evidence and remaining device checks](../evidence/F14/native-application-base.md). Individual physical acceptance is not implied.
- User stories: [US-030](../user-stories/US-030-local-search-results.md), [US-031](../user-stories/US-031-native-keyboard-search-editing.md), [US-032](../user-stories/US-032-search-actions-and-origin-restoration.md). This packet remains the scope and acceptance reference; the user-authorized combined delivery workflow is recorded in progress.
- Agent: `gpt-5.6-terra`, reasoning `high`.
- Outcome: search indexed games/apps and supported launcher/settings actions while preserving query, origin, and focus through the system keyboard.
- Prerequisite: F08 accepted. Runs beside F09 and F10 with no shared-file writes.

## Ownership and contracts

Owns `app/src/main/kotlin/dev/handheld/launcher/feature/search/**`; matching app JVM/instrumentation test packages; `docs/implementation/evidence/F11/**`. Shared search/domain repository interfaces, system-action registry/adapters, IME shell policy, navigation/DI and tokens remain coordinator-owned. Keep any feature-local query transformation inside this package; propose a persistence query addition centrally if required for catalog scale.

Consumes F01 indexed-item/system-action/query contracts, F08 presentation/actions, F05 restoration/input, and F04 Search-origin/IME layout hooks. Provides Search route, immutable state, ViewModel, screen and query-focus request handling. System results come only from a supported stable-key registry integrated by the coordinator; fixture or unresolvable actions never ship as working shortcuts.

## Ordered steps

1. Search the local catalog and supported internal/system action entries with All/Games/Apps/System categories. An Android game appears once in All, under Games classification; deduplicate by stable ID.
2. Preserve query when category changes. Show platform filtering only when relevant, with useful empty-query/recent, matching, no-result, and restricted-filter states. Empty-query recents use the established ordering and exclude internal system shortcuts from Home recency.
3. Build a visible query field and compact result cards. Use two columns when readable; switch to one and shrink/scroll results when IME/width constraints demand. Keep artwork dimensions and selected ID stable during result updates.
4. Use the native Android IME. X enters Search or focuses its query; Back dismisses IME first, then follows origin policy. Compose owns query focus/IME handles, and typing must not leak shell shortcuts or cause duplicate activation.
5. Wire results through the common action port: Android/ROM items launch normally; internal/system actions dispatch supported behavior without promoting Home recency. Request coordinator registration of any missing supported action.
6. Restore query/category/platform/selection/anchor when returning from details or an external launch. Shortcut Search restores its origin on Back; dock Search follows the dock-to-Home rule. Publish route/DI attachment delta.

## Excludes

No network/global device search, searching inside other apps, custom keyboard, global input interception, speculative unsupported settings intents, or provider metadata requests on every keystroke.

## Validation and handoff

Test deduplication, category/query retention, no results and empty query, late result changes preserving selection, and system shortcuts excluded from recent history. Compose tests cover IME-first Back, X-to-query, origin restoration, and result actions matching footer labels. Physically verify Flip 2 keyboard editing/controller usability before claiming it works; otherwise mark that check pending. Provide narrow system-action/shell integration requests and screenshots with keyboard constraints to the coordinator.
