# Handheld Launcher shared contracts

Implementation contract baseline for F01. This file records accepted code semantics; it does not mark a story or feature accepted. Kotlin symbols live under `dev.handheld.launcher.core.domain` unless stated otherwise.

## Catalog identity and item model

`ItemId` is the only cross-repository item key. Its string value is persisted losslessly and is never derived from a display title or filename.

`CurrentUserAndroidComponentId(packageName, activityClassName)` identifies one fully qualified launchable Activity for the Android user currently running the launcher. `itemId` encodes both package and Activity class as `android:<package>/<fully-qualified-class>`; `fromItemId` provides the inverse. Two launchable Activities in one package therefore remain distinct. This identity is scoped to the current Android user's catalog and does not claim cross-profile identity.

`LibraryItem` has three variants:

- `AndroidApp`: one current-user component, discovered title/category/availability/actions, Android provenance, and a matching Android target.
- `RomGame`: a stable externally supplied `ItemId`, opaque `CatalogSourceId`, discovered fields, and an opaque external-content target. Paths, formats, disc grouping, emulators, and provider fields remain deferred to F15–F17.
- `SystemAction`: a stable built-in action ID and internal target. It is never eligible for Home recency.

Every item exposes `LibraryItemKind`, `LibraryCategory`, `Availability`, `SupportedItemAction`, `CatalogProvenance`, and a matching `LaunchTarget`. `LaunchRequest` verifies `itemId == target.itemId`; `LaunchRequest.forItem` is the preferred constructor.

Discovered item fields are replaceable catalog data. User-owned category and artwork choices use `UserItemOverrides` through `ItemOverrideRepository`; discovery never overwrites those records. Favorites and successful-open history likewise store `ItemId` references separately from discovered rows.

## Inventory and preservation

`CatalogRepository.snapshot` exposes `CatalogSnapshot`. `items` contains retained known records; `activeItems` contains only `Availability.Available` records. Unavailable records remain addressable for details/recovery and reference resolution, but do not enter active launch lists.

Every `CatalogInventory` declares an `InventoryScope`:

- `CurrentUserAndroid` means a complete enumeration of launchable Android components for the current user.
- `UserSource(sourceId)` is only a generic later extension boundary; its scan meaning is not defined before F15.

An inventory may contain only items belonging to its declared scope and may not contain duplicate IDs. A complete scoped result is rejected if an observed ID collides with a record retained outside that scope.

`CatalogReconciliationPolicy` applies these rules:

1. `Complete` is authoritative only for its declared scope. Observed records replace their discovered fields. Previously known in-scope records that are omitted are retained with `Unavailable(REMOVED)` and excluded from `activeItems`. Records outside the scope are unchanged.
2. `Incomplete(PARTIAL|FAILED|CANCELLED)` changes only the observable inventory status. Its payload is not committed, no omission implies removal, and the previous catalog remains intact.
3. Favorite IDs, successful-open records, and user overrides are separate repositories. Reconciliation never deletes or rewrites them. A favorite may reference an unavailable or no-longer-active item; removing a favorite never deletes the item.

This lets callers distinguish “complete and empty” from “failed with no observations” without destructive inference.

## Acknowledged launch and successful-open ordering

`LaunchOperationId` is an idempotency/correlation key for one user activation. It is not a gameplay session, duration, process, or analytics identifier.

The intended sequence is:

1. Resolve and revalidate an available catalog item, then create `LaunchRequest.forItem(operationId, item)`.
2. `AcknowledgedLaunchPolicy.begin` accepts one pending request and suppresses additional activation while it is pending.
3. `LaunchDispatcher` performs the external side effect and returns `LaunchAcknowledgement.Dispatched` or `Failed` for the same operation ID.
4. `AcknowledgedLaunchPolicy.acknowledge` consumes a matching operation once. Only a dispatched non-system target yields `SuccessfulOpenCandidate`. Failure, an internal action, an unknown/stale acknowledgement, or a repeated acknowledgement yields no record request.
5. `SuccessfulOpenRepository.recordOnce` uses one local persistence transaction to deduplicate the operation ID, allocate the next globally serialized positive `openOrder`, and upsert the item's single current recency record. Retrying the same candidate returns `AlreadyRecorded` without allocating another order.

Moving focus, opening details, changing artwork, collecting state again, or invoking an internal launcher action never creates a successful-open candidate.

Android/external dispatch and a later Room commit cannot be one cross-process transaction. A successful acknowledgement means the dispatch call was accepted; it does not prove the target remains running. If the launcher process dies after external dispatch but before the local recency transaction commits, the external intent must not be replayed and that open may be absent from recency. If the acknowledgement reaches persistence more than once, operation-ID deduplication makes the local commit idempotent. Later integration may narrow this crash window but must not claim impossible atomicity across processes.

`LibraryItemOrdering.recentFirst` sorts eligible records by descending `openOrder`. It ignores history attached to `SystemAction`, skips history for IDs absent from the supplied catalog, and sorts unopened/tied items by case-normalized title, exact title, then stable ID. Wall-clock time is not ordering authority.

## Repository surfaces

- `CatalogRepository`: observable retained/active catalog state, item lookup, and scoped inventory application.
- `FavoriteRepository`: observable stable-ID references and explicit add/remove semantics.
- `ItemOverrideRepository`: observable user-owned category/artwork overrides separate from discovery.
- `LaunchDispatcher`: external dispatch acknowledgement without process-liveness claims.
- `SuccessfulOpenRepository`: observable recency and transactionally idempotent, globally ordered writes.

The production Room/DataStore implementation, Android enumeration and dispatch, ROM source identity details, metadata-provider fields, and concrete shell navigation, physical input, status-source, and Activity side-effect implementations remain owned by their later stories.

## Navigation and restoration keys

`LauncherDestination` publishes exactly six stable keys in `dockOrder`: Home, Library, Apps, Favorites, Settings, Search. Persist `persistedKey`; enum ordinal is not a storage contract. `fromPersistedKey` returns null for unknown data so F06 can recover safely.

`DestinationSnapshot` is the durable state for one destination. It contains that destination plus optional selected and first-visible `ItemId`, a non-negative first-visible pixel offset, query text, and optional typed filter/sort `PageStateKey` values. It contains no list, numeric selection index, Compose focus requester, scroll object, or navigation controller. `NavigationSnapshotRepository` observes, saves, and clears one independent snapshot per destination.

`LauncherLocation`, `NavigationOrigin`, and `NavigationBackPolicy` define the compact root policy without a general history stack:

- Back on Home stays on Home; Back on any other dock destination goes to Home.
- Shortcut Search records its return destination and Back returns there.
- Item details records either a dock destination or shortcut-Search origin and Back recreates that exact origin.

`LauncherNavigationPort` is the page-facing app boundary for selecting a dock destination, entering shortcut Search or details with an origin, and invoking Back. F04 owns its concrete shell/navigation implementation.

## Input, footer and controller preference

`ConfirmBackMapping` is the persisted preference type. `ConfirmBackMapping.Default` explicitly binds A to Confirm and B to Back; its constructor prevents the same button serving both roles. `ControllerPreferenceRepository` must emit this default when storage is absent or invalid and persists only this typed choice.

`SemanticInputAction` describes normalized input. `LauncherActionDescriptor` binds one semantic input to one `LauncherActionMeaning`, localized label, and enabled state. `ControllerActionFooter` renders those exact descriptors and `SemanticActionPort` receives the same descriptor, so presentation and invocation cannot independently choose different meanings.

`InputDispatchPrecedence.orderedOwners` is the single-owner order: IME, modal, focused page/control, then shell. A future F05 dispatcher stops at the first owner that handles the normalized action. This contract does not implement physical key/axis normalization, repeat timing, focus movement, or a second page dispatcher.

`core.designsystem.contract.ModalFocusLifecycle` contains only `onModalShown` and `onModalDismissed` presentation callbacks. It imports no app/domain types. F03 controls may invoke it; F05 owns binding those callbacks to an application focus/input scope.

## Presentation and page ports

`StatusValue.Available(value)`, `Unavailable`, and `Unsupported` are distinct domain states. An Available payload is non-null, so absence must use one of the two explicit states. `StatusPresentation` renders Available text, renders the stable `—` placeholder for temporary unavailability, and omits Unsupported optional capabilities. A caller must use Unavailable for a supported source that temporarily cannot report; it must not present fixture data as available.

`ItemToCardPresentationAdapter` maps a `LibraryItem` and optional `UserItemOverrides` to `LibraryItemCardPresentation`. The model carries stable item identity, text/type/badges, availability and supported actions; artwork stays a presentation content slot so this contract does not invent storage/provider fields. `ItemLaunchPort.submit(ItemId)` is the page-facing launch boundary. A true result means only that the request entered the acknowledged launch flow; it is not an external-dispatch or recency success. F08 owns the production adapter and coordinator wiring.

## Activity request ownership

`ActivityRequestPort` serializes Activity-bound effects. `submit` publishes one pending request. An observer must call `claim(id)` before executing anything; the successful claim atomically removes the request from `pending` and returns an opaque `ClaimedActivityRequest`. Competing collectors, repeated StateFlow observations, and lifecycle reattachment therefore cannot execute it again. After attempting the effect, the winner calls `complete(claim, acknowledgement)` once. Unknown claims and repeated completion are rejected, and a completed ID cannot be submitted again.

Claiming favors at-most-once external behavior over replay. If lifecycle or process loss occurs between claim and completion, the effect and acknowledgement may be absent and the request is not replayed. The protocol makes no cross-process durability guarantee. The F01 Activity claims requests and completes them as `Unsupported`; F07/F08 own real Android launch and HOME-role behavior.

## Reserved consumer handoff

- F02 owns only its packet paths under `core/designsystem/.../{theme,foundation,glyphs}/**` and corresponding tests/assets. It follows the available/unavailable/unsupported presentation semantics through state-driven primitive parameters; it does not import the app/domain contract types or modules. `core/designsystem/.../contract/ModalFocusLifecycle.kt` is the reserved F01 callback signature and is consumed later by F03/F05.
- F06 owns only `core/data/.../{local,repository}/**`, `core/data/schemas/**`, and its packet tests/evidence. It implements `ControllerPreferenceRepository` and `NavigationSnapshotRepository` using `ConfirmBackMapping`, `LauncherDestination.persistedKey`, `DestinationSnapshot`, and `PageStateKey`. It may not persist enum ordinals, Compose objects, whole lists, or change these signatures/meanings without a coordinator-owned contract delta.
