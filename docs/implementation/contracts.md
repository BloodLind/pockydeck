# Handheld Launcher shared contracts

Accepted implementation contracts for F01, F02 and F06. This file records code semantics; progress and story evidence record acceptance. Kotlin symbols live under `dev.handheld.launcher.core.domain` unless stated otherwise.

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

The Room and DataStore implementations below fulfill the catalog/reference/recency and preference/snapshot ports. Android enumeration and dispatch, ROM source identity details, metadata-provider fields, and concrete shell navigation, physical input, status-source, and Activity side-effect implementations remain owned by their later stories.

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

## US-004 theme integration

`core.designsystem.theme.LauncherTheme(reducedMotion, referenceScale = 1f, content)` provides typed `colors`, `typography`, `spacing`, `shapes`, `depth`, and `motion` groups. `colors.backgroundGradient()` creates the one Home elliptical radial recipe from actual drawing bounds. Apply it once at the root; consumers do not add their own gradient or guess pixel dimensions. Typography uses bundled Plus Jakarta Sans static weights 400–800 and native `sp`; physical-device calibration remains pending.

The app supplies reduced-motion intent from Android's animator-enabled setting on resume. Motion duration properties derive from that input, so requesting reduced motion resolves decorative durations to zero. Theme code imports no app/domain modules or status sensors. The debug-only `ThemePreviewActivity` is an isolated color/type/motion evidence consumer and is absent from the release manifest. Shared metrics and primitives are published below.

## US-005 shared metrics

`core.designsystem.foundation.ShellMetrics.calculate(ShellMetricsInput(widthPx, heightPx, density, fontScale))` runs once from the actual available root bounds. It returns status/content/dock/footer `ShellBounds`, gutter, compact mode, square Home artwork/allocation bounds, metadata/frame/lift reservation, and explicit capacity flags. Destinations consume these anchors; destination identity and content do not enter the calculation.

Native dp geometry adapts to height and width while text keeps system font scale. The reference guidance is 3.75% gutter, 18.7% outer card width, 1.9% outer-frame gap, 35.6% card top, 85.2% dock center and 92.8% footer start at the standard 1280×720dp fixture. Metadata reserves two scaled Home-title lines and platform text. Small windows can report no usable card while retaining text space; `controlsCanReachMinimumTouchTarget` requires both band width and height to fit 48dp, and does not promise an entire dock fits every window. Malformed inputs remain finite and bounds do not overlap. Physical Flip 2 density calibration remains pending. Debug-only `MetricsPreviewActivity` demonstrates standard, native compact/large-font, and alternate-destination fixtures.

## US-006 visual primitives

`LauncherText(text, modifier, style, color, maxLines, overflow, unavailable, unavailableReason)` uses native text layout and exposes its text plus an optional caller-supplied unavailable state description. `LauncherIcon(imageVector, contentDescription, modifier, tint)` clears internal image semantics: a non-null description is meaningful; null is decoration that neither becomes a focus target nor hides its containing control.

`LauncherSurface` and `FocusFrame` accept independent `selected`, `focused`, `pressed`, `enabled` and `unavailable` values, optional descriptions, shape, frame width and lift. Callers own actual focus and activation; wire `onFocusChanged` before their `focusable` modifier and pass the resulting focus value. Selection publishes selected semantics and chooses the selected surface/foreground; it never synthesizes focus. Only `enabled=false` marks a control disabled; an unavailable value can still have an enabled details action. Pressed treatment does not dim or delay the focus outline.

The measured allocation reserves frame padding plus lift/depth slack on both sides of each axis, so a square allocation retains square content. Pass US-005 frame/lift reservations for Home geometry. Focus changes placement/drawing without changing measurement; the amber outline is immediate and reduced motion suppresses decorative lift. `LauncherSurface` requests at least 48dp before applying a caller's preferred size. Parent constraints must still permit that target, and consumers own spacing. `LocalLauncherContentColor` / `launcherContentColor()` supplies readable foregrounds to nested text/icons on selected, default and unavailable surfaces.

`glyphs.LauncherGlyph` contains Home, Library, Apps, Favorites, Settings and Search, rendered by `LauncherGlyphIcon` using original bundled native vector paths. `LauncherFaceButton` contains physical A/B/X/Y/Start legends; `LauncherFaceGlyph` takes a caller-supplied semantic label. The data/domain mapping chooses which physical glyph means Confirm or Back; the design system hardcodes no such mapping. Meaningful glyphs announce their description once; decoration exposes no raw symbol text.

`LauncherPrimitivesPreviewContent(reducedMotion)` is an isolated native evidence fixture with real requested focus, long text, static pressed/unavailable examples and glyphs. Its app Activity is registered only in debug. Physical density/controller calibration remains pending.

## US-017 / US-018 persistence handoff

`core.data.local.LauncherDatabase.open(context, databaseName)` opens the version-2 Room database (`handheld-launcher.db` by default). Share one application-owned instance among `RoomCatalogRepository(database)`, `RoomFavoriteRepository(database)`, `RoomItemOverrideRepository(database)`, and `RoomSuccessfulOpenRepository(database)`; their public contracts are the accepted domain interfaces. Observation reads the persisted cache before discovery completes. Catalog snapshots are read transactionally and sorted by the shared title/ID policy. Combine successful-open `records` with `LibraryItemOrdering.recentFirst` for recency order.

Every valid completed inventory is reconciled in one Room transaction. Observed discovered fields/provenance/actions are updated, omissions are retained as unavailable, and the inventory status changes in that same commit. Large omission sets are divided into bounded SQL batches inside the transaction. Valid incomplete inventories only update status. F01's scoped, unique-ID validation remains in force for all inventory objects.

The frozen v1 export separates catalog fields, provenance, actions, inventory status, favorite references, user overrides, and successful-open history references. User references have no cascade relationship to discovered catalog rows. Version 2 adds immutable `successful_open_operations` receipts (operation primary key, unique order) and singleton `successful_open_order_state`. `migration1To2` preserves all seven old tables and seeds the counter from maximum retained history order; it invents no legacy receipts. The builder registers that migration and has no destructive fallback.

`RoomSuccessfulOpenRepository.recordOnce` deduplicates, increments the guarded positive order, inserts the receipt, and updates the item's single history row in one transaction; conflicts and overflow roll back all changes. Receipts survive restart. No persisted structure stores an external launch request. Future schema changes require the coordinator's reviewed migration step.

## US-019 preferences and snapshot format

`core.data.local.LauncherPreferencesStore.open(context, fileName)` owns the single DataStore for that file, default `handheld-launcher.preferences_pb` in the app's DataStore directory. Share it between `DataStoreControllerPreferenceRepository(store)` and `DataStoreNavigationSnapshotRepository(store)`; DataStore types remain internal. Its IO scope is owned for the application lifetime; tests call suspend `close()` to cancel and join that scope before reopening. Normal storage errors propagate; only an unreadable protobuf invokes whole-file corruption recovery to empty preferences. Room is a separate file and remains intact.

The only global keys are string `controller.confirm_button` and `controller.back_button`, with explicit `a`/`b` values. Missing, unknown, wrong-type or duplicate button assignments yield `ConfirmBackMapping.Default` without deleting unrelated saved data.

Each accepted destination key owns `navigation.<persistedKey>.` with exactly these fields:

| Suffix | Type | Meaning |
| --- | --- | --- |
| `version` | Int | Snapshot encoding version, currently 1 |
| `selected_item_id` | String, optional | Stable selected ID |
| `first_visible_item_id` | String, optional | Stable scroll anchor ID |
| `first_visible_offset_px` | Int | Non-negative anchor offset |
| `query` | String | Query text, including empty text |
| `filter_key` | String, optional | Typed opaque filter key |
| `sort_key` | String, optional | Typed opaque sort key |

Saving or clearing one destination is one atomic edit of its own fields. Missing/unknown/wrong-type versions yield no snapshot. Invalid optional IDs/keys become null, an invalid offset becomes zero, and an invalid query becomes empty. Valid unrelated fields, destinations and controller mapping survive local recovery. Destination identity comes from the stable prefix, never an ordinal; unknown prefixes are ignored. Encoding contains no Compose object, full list, external request or speculative provider preference.

Debug-only `FakeControllerPreferenceRepository` and `FakeNavigationSnapshotRepository` implement the same domain ports for isolated consumers. `LauncherApplication` owns one lazy `AppContainer(applicationContext)`, which shares one lazy Room database and one lazy preferences store among typed repository properties. Repository setup does not enumerate packages or require discovery before startup. These formats and constructors are reserved for subsequent F05/F07 consumers.

## Home milestone native calibration delta

The connected Flip 2 reports 1920×1080 landscape at 360dpi, which yields 853.33×480dp in immersive mode. `ShellMetrics.referenceScale` converts the 1280-unit reference into native tokens; pass it to the root `LauncherTheme`. It scales dp/sp token defaults without overriding Android density or the user's font scale. Standard 16:9 at default font scale remains reference mode on this device; constrained windows and larger text use compact layout.

`homeMetadataTop`, `dockCenterY`, and `footerDividerY` are visual anchors. Dock and footer `ShellBounds` continue to describe separate interaction bands, each at least 48dp when space permits. In the Flip 2 baseline, use 32dp visible dock circles inside 48dp slots; keep the footer divider near 92.8% height even though its touch band begins higher. Use the metrics' dock center (clamped to 408dp to avoid overlap), not the midpoint of its interaction band.

Home card allocation now reserves two lift widths on each axis, plus the frame. The surface has a small fixed lower edge and restrained shadow; focused cards lift inside that allocation, while reduced motion preserves the frame without the movement. Updated inter-slot spacing preserves the reference outer-card size and pitch. These are coordinator-owned changes reviewed against the physical density; final Home screenshot acceptance remains with the integrated shell and Home consumer.

## US-007 / US-008 controls and cards

`LauncherButton`, `LauncherIconButton`, and `FilterChip` each own one real clickable/selectable focus target and press interaction. Callers can attach a `FocusRequester` through `modifier`; `onFocusChanged` reports actual focus, not selection. Disabled controls cannot activate. `SortSelector` is a trigger whose caller owns the option overlay. `SearchField` uses native `BasicTextField`, caller-owned query state and IME Search callback; it does not intercept Android keyboard behavior.

`CoverTile` uses `CardVariant.HomeCover` for an image-only square and `CollectionCover` for square art with a reserved two-line title and one-line subtitle area. Allocate a Home tile with the shared `homeCardAllocatedSize`, `focusFrameReservation`, and `focusLiftReservation`; its inner image then matches `homeCardArtworkSize` exactly. `AppIconTile` places a bounded, fitted native icon and a two-line title inside the tile. `SearchResultCard` uses horizontal square artwork and bounded captions. Cards own one activation/focus target; presentation state does not trigger external effects.

Artwork remains caller-supplied: `CoverArtwork` crops a painter without stretching, `AppIconArtwork` fits it, and `ArtworkFallback` fills the same allocated slot. `PlatformBadge(homeAccent = true)` supplies the amber Home badge. `ControllerGlyph` accepts a physical legend and optional semantic description, without deciding what that button means. The design-system `StatusValue` is a presentation-only type; shell code maps the distinct domain value into it. Available text, unavailable reason/placeholder, and omitted unsupported values keep their existing meanings.

## US-020 / US-021 Android catalog

`PackageManagerAndroidAppDiscovery(context, dispatcher)` implements `AndroidAppDiscovery.discover()`. It queries current-user MAIN/LAUNCHER activities, preserves component identity, excludes the launcher's own package, and requires enabled, exported activities with enabled applications. Labels are loaded off the main thread. A discovery failure returns an incomplete inventory; it never implies a successful empty catalog. Package-derived categories remain `OTHER` until an explicit user override or later classification policy exists.

`AndroidCatalogRefreshCoordinator(catalogRepository, discovery, packageChanges, applicationScope)` exposes the persisted catalog immediately and separate refresh state (`Idle`, `Refreshing`, `Ready`, or `Error`). One application-owned worker serializes and conflates refresh requests after the first cache emission. `start()`/`stop()` bind the package-change monitor to active launcher observation; startup, later resume and protected package events request refresh. A resume occurring during an older scan still queues another scan. Cancellation, failure and partial results preserve cached items through the accepted reconciliation contract.

`BroadcastAndroidPackageChangeMonitor` observes protected package broadcasts while registered. It owns no Room state, favorites, overrides, or recency. The lazy `AppContainer.androidCatalog` shares the existing repository and application scope; creating the container itself does not start discovery. F08 binds its lifecycle when the production Home route is integrated. Physical discovery has been exercised through the real adapter under the launcher app context on the Flip 2; physical input and HOME-role tests remain separate gates.

## US-022 Android dispatch

`AndroidComponentLaunchDispatcher(context, dispatcher = Dispatchers.IO)` implements `LaunchDispatcher`. It accepts only current-user Android component targets, rejects the launcher's own package, and revalidates exact package/class against enabled, exported MAIN/LAUNCHER activities immediately before starting. It uses `ACTION_MAIN`, `CATEGORY_LAUNCHER`, the explicit component, and `NEW_TASK | RESET_TASK_IF_NEEDED`.

A missing/disabled target or `ActivityNotFoundException` yields `TARGET_UNAVAILABLE`; a security rejection yields `REJECTED`; another runtime validation/start failure yields `DISPATCH_FAILED`. `Dispatched` means only that `startActivity` returned. The adapter has no recency repository or running-process state. `AppContainer.launchDispatcher` is the single lazy production instance. F08 persists the origin and records one successful-open candidate only after the matching acknowledgement; it must never retry the external effect after process loss.

## US-009 layouts and modal boundaries

`PageHeading`, `FilterStrip`, `CollectionGrid`, `EmptyState`, and `InlineNotice` consume caller-owned content and callbacks. Filters scroll horizontally; grids accept a caller's `LazyGridState` and stable key function. Stateful production collections must supply item keys. `SettingRow`, `ActionRow`, `ChoiceRow`, and `ToggleRow` share real focus/press treatment and one enabled activation target; the switch track is decorative, with the row exposing native switch semantics.

`LauncherDialog(title, onDismissRequest, modifier, visible, lifecycle, content)` fills its overlay host and places a bounded panel inside it. The modifier applies to the panel. A sibling backdrop absorbs background touches, the body scrolls within its own accessibility boundary, and Close stays outside that scrolling body. The focus group initially requests its first enabled child (Close for an empty body) and cancels focus exit. `ItemActionList` supplies enabled, caller-owned actions; it does not choose a route or dispatch a key. Updated `ModalFocusLifecycle` callbacks are used without restarting the modal lifecycle. F05 owns Back/IME precedence and binds these callbacks to the initiating surface's focus restoration.

## US-010 gallery and final native typography

Debug `ControlGalleryActivity` computes one root snapshot and hosts `LauncherControlGallery(metrics, initialSection)` or content-only `HomeGalleryFixture(metrics, modifier, variant)`. The latter expects its modifier at `metrics.contentBounds`, subtracts those offsets internally, and intentionally lets the partial trailing card reach the screen edge. F04 embeds this content in its sole shell; production never uses the debug item list or artwork.

Home title tracking is -0.025em and platform tracking is +0.05em at source scale; native letter spacing scales with the theme. The platform badge uses the source horizontal/dot spacing, and Home metadata reserves additional native line-box rounding so two title lines fit without shifting the normal card row. `ControllerGlyph` supplies the reference physical face-button circles and Start/shoulder pills; its semantic label remains caller-owned, and it does not choose Confirm/Back mapping.
