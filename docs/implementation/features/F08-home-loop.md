# F08 — First working HOME loop

- Status: **implemented in the native application batch**; see [integrated evidence and remaining device checks](../evidence/F14/native-application-base.md). Individual physical acceptance is not implied.
- User stories: [US-023](../user-stories/US-023-cached-home-carousel.md), [US-024](../user-stories/US-024-acknowledged-home-launch-and-return.md), [US-025](../user-stories/US-025-android-home-role-and-window-loop.md). This packet remains the scope and acceptance reference; the user-authorized combined delivery workflow is recorded in progress.
- Agent: `gpt-5.6-sol`, reasoning `high`.
- Outcome: choose this launcher as HOME, select and open an installed app, then return with the same item selected and correct most-recently-opened order.
- Prerequisites: F05 and F07 accepted; coordinator has integrated their shared hooks.

## Ownership and contracts

Owns `app/src/main/kotlin/dev/handheld/launcher/feature/home/**`, `app/src/main/kotlin/dev/handheld/launcher/launch/**`, `app/src/main/kotlin/dev/handheld/launcher/platform/home/**`, `app/src/main/kotlin/dev/handheld/launcher/ui/components/**`, `app/src/main/kotlin/dev/handheld/launcher/ui/presentation/**`, `app/src/main/kotlin/dev/handheld/launcher/ui/artwork/local/**`; matching app JVM/instrumentation test packages; `docs/implementation/evidence/F08/**`. Initial shared item-card/presentation/launch API creation is leased here; later consumers request coordinator changes. Manifest, `MainActivity.kt`, window wiring, DI, route registry, contracts, theme and persistence remain coordinator-owned.

Consumes F01/F06 catalog/favorite/navigation/launch contracts, F04 shell, F05 focus/input, and F07 adapters. Provides Home Route/Screen/ViewModel/immutable UiState; shared `LibraryItemCard` and `TileUiModel` mapping; a common launch/action coordinator; normally acknowledged HOME-role requests. These are the production APIs used by F09–F12.

## Ordered steps

1. Implement Home metadata/title plus a horizontal keyed carousel inside the existing content rectangle. The 8 September revision keeps all available entries reachable: newest item first, then Android games, ROMs, and other apps, with title/ID order within each group. Use production generic cards, bounded titles, native icon/fallback artwork and stable dimensions.
2. Connect cache-first Flow to immutable UI state through lifecycle-aware collection. Initial empty/loading, refreshing cache, unavailable item, failed launch and removed selection must each have recoverable behavior.
3. Implement one activation path: persist origin snapshot → suppress duplicate pending activation → revalidate/dispatch → acknowledge result → record successful open exactly once. A failure preserves ordering. Opening details/focusing an item must never promote it.
4. Use F05 identity/anchor restoration after order changes and on return. Example `[C,B,A] → open B → [B,C,A]`; preserve B, with the nearest feasible scroll placement if its old anchor is invalid.
5. Supply HOME role request handling and ask coordinator to register one exported `singleTask` Activity with separate `MAIN/HOME/DEFAULT` and `MAIN/LAUNCHER` filters. Declining role selection must leave ordinary browsing usable; repeated HOME intents must not recreate the Activity/page state or replay launch requests.
6. Ask coordinator to wire launcher-only landscape/immersive window behavior and supported transient system bars. Android notification shade, volume/brightness, power and Retroid controls remain functional; external applications retain their own windows/tasks.
7. Publish common item action/presentation ports, supported labels (`Play`, `Open`, `Reopen`), native-icon loading strategy with bounded caching, and fixture states for parallel destination agents. Details may remain a feature hook until F10; do not pretend unfinished destinations are delivered.

## Excludes

No running badges, analytics, guaranteed process resume, global launch monitoring, full destination completion, ROMs or scraping. Production must not display fixture status readings.

## Validation and handoff

Required loop evidence: default HOME selection; open B and return with one B selected at the front; repeated open without duplication; rejected dispatch unchanged; cold cached Home before discovery finishes; repeated Home presses; recreation/process restoration; selected-app removal fallback; external target task surviving return. Run applicable JVM/Compose tests, debug build, and lint. Save Home screenshots and report unverified Flip 2 checks. Coordinator reviews this first functional milestone before F09/F10/F11 launch in parallel.
