# F07 — Android discovery and launch adapters

- Status: **planned**.
- User stories: [US-020](../user-stories/US-020-real-android-component-discovery.md), [US-021](../user-stories/US-021-cache-first-android-reconciliation.md), [US-022](../user-stories/US-022-safe-android-component-dispatch.md). Dispatch one ready story; this packet remains the technical ownership and feature-gate reference.
- Agent: `gpt-5.6-sol`, reasoning `high`.
- Outcome: the launcher discovers real installed launchable apps, keeps a usable cached catalog, and can safely dispatch an Android component.
- Prerequisite: F06 accepted. Runs independently beside F03.

## Ownership and contracts

Owns `core/data/src/main/kotlin/dev/handheld/launcher/core/data/android/apps/**`, `core/data/src/main/kotlin/dev/handheld/launcher/core/data/discovery/**`; matching data JVM/instrumentation test packages; `docs/implementation/evidence/F07/**`. Coordinator owns package-visibility manifest declarations, dependencies, DI, Room schema/repository contracts and shared policies. Request narrow deltas for those files.

Consumes F01 launch/catalog contracts and F06 completed-inventory/write boundaries. Provides Android component enumeration, serialized/coalesced reconciliation, availability validation, package-icon references, and Android component dispatch results. It returns successful/failed dispatch; the single orchestration path in F08 calls F06's successful-open operation. This adapter does not independently record a second recency event.

## Ordered steps

1. Choose and document a verified Android 13 enumeration approach for real `MAIN/LAUNCHER` components of the current user. Retain multiple components/package, include launchable emulator/system apps, exclude this launcher, and reject synthesized app-details entries if using `LauncherApps`.
2. Ask the coordinator for only the narrow package-visibility queries actually required. Avoid broad installed-package access by default; no ROM/network/overlay/accessibility/usage permission belongs in this slice.
3. Read Room immediately, then run enumeration off the main thread. Serialize/coalesce startup/resume and active package-change triggers. Mark inventory complete only after the whole scan succeeds; cancellation or a partial provider result cannot clear cached entries.
4. Reconcile package install/update/uninstall while keeping favorites, recency, user overrides, and stable identity intact. Use availability transitions, then active-list filtering and UI fallback; do not erase historical references on temporary failures.
5. Revalidate the exact component immediately before dispatch. Use normal Android launcher task semantics to put the external Activity in its own task. Return an explicit recoverable failure for missing/disabled/security-rejected targets; do not assert a process is running.
6. Expose lifecycle start/stop or explicit refresh hooks and supply constructor dependencies to the coordinator. Provide stale-cache/loading/error fixtures for F08 so browsing remains independent of scan completion.

## Excludes

No global usage monitoring, automatic import of opens outside this launcher, app killing, guaranteed suspend/resume, ROM enumeration, or discovery inside visual controls.

## Validation and handoff

Test multiple components in one package, self exclusion, install/update/uninstall convergence, cancelled/failed inventory preserving cache, and rejected/missing launch targets. On an Android device/emulator, verify normal external task placement and return without launcher task reuse finishing the target. Record which physical Flip 2 checks remain pending. Hand F08 exact dispatch result semantics, lifecycle hooks, cache evidence, and required shared-file deltas; coordinator integrates manifest/DI once.
