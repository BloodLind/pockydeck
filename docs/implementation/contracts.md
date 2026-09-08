# Handheld Launcher shared contracts

Accepted implementation contracts for F01, F02 and F06. This file records code semantics; progress and story evidence record acceptance. Kotlin symbols live under `dev.handheld.launcher.core.domain` unless stated otherwise.

## Catalog identity and item model

`ItemId` is the only cross-repository item key. Its string value is persisted losslessly and is never derived from a display title or filename.

`CurrentUserAndroidComponentId(packageName, activityClassName)` identifies one fully qualified launchable Activity for the Android user currently running the launcher. `itemId` encodes both package and Activity class as `android:<package>/<fully-qualified-class>`; `fromItemId` provides the inverse. Two launchable Activities in one package therefore remain distinct. This identity is scoped to the current Android user's catalog and does not claim cross-profile identity.

`LibraryItem` has three variants:

- `AndroidApp`: one current-user component, discovered title/category/availability/actions, Android provenance, and a matching Android target.
- `RomGame`: a stable generated `ItemId`, `CatalogSourceId`, discovered title/availability, optional canonical `platformId` and `format`, and an opaque external-content target. F15's document repository resolves that target; source identity is independent of emulator selection and extracted-copy paths. Metadata/provider fields remain deferred to F17.
- `SystemAction`: a stable built-in action ID and internal target. It is never eligible for Home recency.

Every item exposes `LibraryItemKind`, `LibraryCategory`, `Availability`, `SupportedItemAction`, `CatalogProvenance`, and a matching `LaunchTarget`. `LaunchRequest` verifies `itemId == target.itemId`; `LaunchRequest.forItem` is the preferred constructor.

Discovered item fields are replaceable catalog data. User-owned category and artwork choices use `UserItemOverrides` through `ItemOverrideRepository`; discovery never overwrites those records. Favorites and successful-open history likewise store `ItemId` references separately from discovered rows.

## Inventory and preservation

`CatalogRepository.snapshot` exposes `CatalogSnapshot`. `items` contains retained known records; `activeItems` contains only `Availability.Available` records. Unavailable records remain addressable for details/recovery and reference resolution, but do not enter active launch lists.

Every `CatalogInventory` declares an `InventoryScope`:

- `CurrentUserAndroid` means a complete enumeration of launchable Android components for the current user.
- `UserSource(sourceId)` scopes a complete source inventory. F15 uses separate revision-checked batch upserts and a final omission transaction rather than presenting each partial batch as a complete inventory.

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

- Once active editor/modal handling is complete, Back on dock-opened Search or Settings returns to Home. Back on Home, Library, Apps or Favorites is a no-op that preserves focus, selection and viewport.
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

`core.designsystem.theme.LauncherTheme(reducedMotion, referenceScale = 1f, uiScaleFactor = 1f, content)` provides typed `colors`, `typography`, `spacing`, `shapes`, `depth`, and `motion` groups. `colors.backgroundGradient()` creates the one Home elliptical radial recipe from actual drawing bounds. Apply it once at the root; consumers do not add their own gradient or guess pixel dimensions. Typography uses bundled Plus Jakarta Sans static weights 400–800 and native `sp`; physical-device calibration remains pending.

The app combines Android's animator-enabled setting, refreshed on resume, with the persisted launcher Reduce motion preference. Either request resolves decorative durations to zero. Theme code imports no app/domain modules or status sensors. The debug-only `ThemePreviewActivity` is an isolated color/type/motion evidence consumer and is absent from the release manifest. Shared metrics and primitives are published below.

## US-005 shared metrics

`core.designsystem.foundation.ShellMetrics.calculate(ShellMetricsInput(widthPx, heightPx, density, fontScale, uiScaleFactor = 1f))` runs from the actual available root bounds and current display preferences. It returns status/content/dock/footer `ShellBounds`, gutter, compact mode, square Home artwork/allocation bounds, metadata/frame/lift reservation, and explicit capacity flags. Destinations consume these anchors; destination identity and content do not enter the calculation.

Native dp geometry adapts to height and width while text keeps system font scale. Reference guidance retains the 3.75% gutter, 18.7% outer card width, 1.9% outer-frame gap, 35.6% card top and 85.2% dock center where capacity permits. Version 0.7 reserves at least 52dp for the footer and raises its content, superseding the original 92.8% divider anchor. Metadata reserves two scaled Home-title lines and platform text. Small windows can report no usable card while retaining text space; `controlsCanReachMinimumTouchTarget` requires both band width and height to fit 48dp, and does not promise an entire dock fits every window. Malformed inputs remain finite and bounds do not overlap. Physical Flip 2 calibration uses its actual 2.25 density; UI scaling changes native density and clamps the base reference scale before applying UI-scale compensation, avoiding double amplification in compact windows. Debug-only `MetricsPreviewActivity` demonstrates standard, native compact/large-font, and alternate-destination fixtures.

## US-006 visual primitives

`LauncherText(text, modifier, style, color, maxLines, overflow, unavailable, unavailableReason)` uses native text layout and exposes its text plus an optional caller-supplied unavailable state description. `LauncherIcon(imageVector, contentDescription, modifier, tint)` clears internal image semantics: a non-null description is meaningful; null is decoration that neither becomes a focus target nor hides its containing control.

`LauncherSurface` and `FocusFrame` accept independent `selected`, `focused`, `pressed`, `enabled` and `unavailable` values, optional descriptions, shape, frame width and lift. Callers own actual focus and activation; wire `onFocusChanged` before their `focusable` modifier and pass the resulting focus value. Selection publishes selected semantics and chooses the selected surface/foreground; it never synthesizes focus. Only `enabled=false` marks a control disabled; an unavailable value can still have an enabled details action. Pressed treatment does not dim or delay the focus outline.

The measured allocation reserves frame padding plus lift/depth slack on both sides of each axis, so a square allocation retains square content. Pass US-005 frame/lift reservations for Home geometry. Focus changes placement/drawing without changing measurement; the amber outline is immediate and reduced motion suppresses decorative lift. `LauncherSurface` requests at least 48dp before applying a caller's preferred size. Parent constraints must still permit that target, and consumers own spacing. `LocalLauncherContentColor` / `launcherContentColor()` supplies readable foregrounds to nested text/icons on selected, default and unavailable surfaces.

`glyphs.LauncherGlyph` contains Home, Library, Apps, Favorites, Settings and Search, rendered by `LauncherGlyphIcon` using original bundled native vector paths. `LauncherFaceButton` contains physical A/B/X/Y/Start legends; `LauncherFaceGlyph` takes a caller-supplied semantic label. The data/domain mapping chooses which physical glyph means Confirm or Back; the design system hardcodes no such mapping. Meaningful glyphs announce their description once; decoration exposes no raw symbol text.

`LauncherPrimitivesPreviewContent(reducedMotion)` is an isolated native evidence fixture with real requested focus, long text, static pressed/unavailable examples and glyphs. Its app Activity is registered only in debug. Physical density/controller calibration remains pending.

## US-017 / US-018 persistence handoff

`core.data.local.LauncherDatabase.open(context, databaseName)` opens the version-4 Room database (`handheld-launcher.db` by default). Share one application-owned instance among catalog, reference, successful-open and `RoomRomLibraryRepository` repositories. Observation reads the persisted cache before discovery completes. Catalog snapshots are read transactionally and sorted by the shared title/ID policy. Combine successful-open `records` with `LibraryItemOrdering.recentFirst` for recency order.

Every valid completed inventory is reconciled in one Room transaction. Observed discovered fields/provenance/actions are updated, omissions are retained as unavailable, and the inventory status changes in that same commit. Large omission sets are divided into bounded SQL batches inside the transaction. Valid incomplete inventories only update status. F01's scoped, unique-ID validation remains in force for all inventory objects.

The frozen v1 export separates catalog fields, provenance, actions, inventory status, favorite references, user overrides, and successful-open history references. User references have no cascade relationship to discovered catalog rows. Version 2 adds immutable `successful_open_operations` receipts (operation primary key, unique order) and singleton `successful_open_order_state`. `migration1To2` preserves all seven old tables and seeds the counter from maximum retained history order; it invents no legacy receipts. The builder registers that migration and has no destructive fallback.

`RoomSuccessfulOpenRepository.recordOnce` deduplicates, increments the guarded positive order, inserts the receipt, and updates the item's single history row in one transaction; conflicts and overflow roll back all changes. Receipts survive restart. No persisted structure stores an external launch request. Future schema changes require the coordinator's reviewed migration step.

## US-019 preferences and snapshot format

`core.data.local.LauncherPreferencesStore.open(context, fileName)` owns the single DataStore for that file, default `handheld-launcher.preferences_pb` in the app's DataStore directory. Share it between `DataStoreControllerPreferenceRepository(store)`, `DataStoreDisplayPreferenceRepository(store)` and `DataStoreNavigationSnapshotRepository(store)`; DataStore types remain internal. Its IO scope is owned for the application lifetime; tests call suspend `close()` to cancel and join that scope before reopening. Normal storage errors propagate; only an unreadable protobuf invokes whole-file corruption recovery to empty preferences. Room is a separate file and remains intact.

Controller keys are string `controller.confirm_button` and `controller.back_button`, with explicit `a`/`b` values. Missing, unknown, wrong-type or duplicate button assignments yield `ConfirmBackMapping.Default` without deleting unrelated saved data.

v0.7 adds integer `display.ui_scale_percent` and Boolean `display.reduce_motion` in the same DataStore. `DisplayPreferences` accepts only 90, 100, 110 or 120 percent, with a default of 100 and Reduce motion off. Missing, invalid or wrong-type stored values fall back independently; writes preserve controller and navigation data. The display repository adds no Room schema or migration: the database remains version 4.

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

`homeMetadataTop`, `dockCenterY`, and `footerDividerY` are visual anchors. The user's September correction restores reference-scaled typography and original shell landmarks: the standard dock center is 85.2% of height and the footer divider 92.8%, constrained by separate non-overlapping touch bands of at least 48dp where space permits. Only small status, footer/control and Settings text and glyphs receive a 1.15 size multiplier. The subsequent readability correction maps the resulting small text sizes through 3→6sp, 9→11sp and 14→14sp anchors with linear interpolation; line heights scale proportionally and Android font scaling remains independent. Home title/artwork geometry and reference-scaled corner radii are retained. Status text is regular weight and its row accommodates the measured text height. Control icons use the HTML preview's Material Symbols Outlined at weight 200; status symbols preserve its fill variants. Native vectors have no runtime font or network dependency.

Home card allocation now reserves two lift widths on each axis, plus the frame. The surface has a small fixed lower edge and restrained shadow; focused cards lift inside that allocation, while reduced motion preserves the frame without the movement. Updated inter-slot spacing preserves the reference outer-card size and pitch. These are coordinator-owned changes reviewed against the physical density; final Home screenshot acceptance remains with the integrated shell and Home consumer.

## US-007 / US-008 controls and cards

`LauncherButton`, `LauncherIconButton`, and `FilterChip` each own one real clickable/selectable focus target and press interaction. Callers can attach a `FocusRequester` through `modifier`; `onFocusChanged` reports actual focus, not selection. Disabled controls cannot activate. `SortSelector` is a trigger whose caller owns the option overlay. `SearchField` uses native `BasicTextField`, caller-owned query state and IME Search callback; it does not intercept Android keyboard behavior.

`CoverTile` uses `CardVariant.HomeCover` for an image-only square and `CollectionCover` for square art with a reserved two-line title and one-line subtitle area. Allocate a Home tile with the shared `homeCardAllocatedSize`, `focusFrameReservation`, and `focusLiftReservation`; its inner image then matches `homeCardArtworkSize` exactly. `AppIconTile` places a bounded, fitted native icon and a two-line title inside the tile. `SearchResultCard` uses horizontal square artwork and bounded captions. Cards own one activation/focus target; presentation state does not trigger external effects.

Collection filter counts share the grid's availability, category and favorite rules. Fixed All precedes a finite, horizontally scrolling strip of natural-width category pills; All filters opens the complete three-column chooser. Decorative L2/R2 hints remain outside the strip. Explicit filter changes can scroll the selected category into view, and trigger navigation uses the same filter-key order without wrapping at either end. D-pad focus follows only fully visible chips between All and All filters/Sort, without implicit strip scrolling. The root owns the filter modal, including mapped Back/Confirm handling and opener focus restoration. Exact known frontend packages ES-DE, GameNative and Artemis default to Apps even if Android declares them as games; explicit user category overrides remain authoritative.

Artwork remains caller-supplied: `CoverArtwork` crops a painter without stretching, `AppIconArtwork` fits it, and `ArtworkFallback` fills the same allocated slot. `PlatformBadge` accepts a console accent independently of the amber focus treatment. `ControllerGlyph` accepts a physical legend and optional semantic description, without deciding what that button means. The design-system `StatusValue` is a presentation-only type; shell code maps the distinct domain value into it. Available text, unavailable reason/placeholder, and omitted unsupported values keep their existing meanings.

## US-020 / US-021 Android catalog

`PackageManagerAndroidAppDiscovery(context, dispatcher)` implements `AndroidAppDiscovery.discover()`. It queries current-user MAIN/LAUNCHER activities, preserves component identity, excludes the launcher's own package, and requires enabled, exported activities with enabled applications. Labels are loaded off the main thread. A discovery failure returns an incomplete inventory; it never implies a successful empty catalog. Known emulator packages are classified `EMULATOR`; otherwise Android's declared game category or legacy game flag yields `GAME`, with `OTHER` for remaining apps. Explicit user category overrides take precedence in presentation and destination membership.

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

## F15 / F16 ROM sources, selection and launch

`LocalControlFocusRestoration` is an optional presentation-only hook. Shared settings rows, buttons, filter controls and cards publish a callback for their own focus target on focus and before activation. The root retains the initiating control across a modal and restores it after keyboard/controller or touch dismissal; page/shell groups remain fallback paths. This avoids treating a scrolling container's non-semantic focus target as restored control focus. Without a provider, the hook is a no-op and primitive signatures are unchanged.

The approved ROM batch adds optional `platformId` and `format` to `LibraryItem.RomGame`. A source has a generated `CatalogSourceId`; each source/document pair receives a generated `ItemId` retained across rescans and exact-root reattachment. Provider document IDs remain opaque. Relative names inform console detection and descriptor grouping; they never replace SAF access checks or become guessed filesystem paths.

The Room 2-to-3 migration introduced `rom_sources`, `rom_documents`, and `rom_preferences`, plus nullable console/format fields on the catalog projection, while preserving existing catalog, favorites, overrides, successful-open history and operation receipts. DataStore navigation data is unchanged. Removing a source disables its projection and retains identities and user references. Source revisions prevent an older scan from publishing after removal or reassignment. Enumeration/planning must finish before bounded upserts begin; only completion of all batches for the same revision may reconcile omissions. Failed or cancelled scans do not infer missing files from an incomplete scan; loss of root access marks their projection unavailable. The current Room 4 and 0.6 batch-publication rules below extend this original contract.

`RomScanPlanner` is provider-independent. Explicit item and source/folder assignments take precedence over console-folder inference; compatible exact metadata can identify otherwise unknown games, while unambiguous format inference remains supported. Conflicting automatic evidence requires a manual choice rather than silently replacing a console. Unidentified games remain stored and correctable in Settings, but do not enter the main game grids. Valid descriptors/playlists own their transitive companions. Malformed or incomplete sets retain a separate repair requirement, which choosing a console cannot clear. Library console filters and emulator settings are derived from present identified games in available sources, so empty console folders do not create console rows. Foreground reconciliation, manual scans and an eight-hour WorkManager request refresh the inventory; no instant filesystem-monitor guarantee is made.

`RomFeatureController` serializes ephemeral settings and launch choices. One compatible installed app is selected automatically; several require a chooser, with optional per-console preference. Details can persist a per-game override. Missing or incompatible saved apps require another explicit choice. RetroArch core selection records the user's installed-core choice; Android private core files are not inspected. Package profiles validate the specific enabled, exported activity and launch contract immediately before dispatch. Recognition does not imply an installed emulator supports a format.

Supported archives are prepared only when the selected launch path requires unpacked content. Original files are read-only. Preparation enforces byte, entry, memory and storage limits; encrypted, nested, unsupported archive variants and unsafe entries produce actionable errors. Completed immutable copies live in quota-managed `noBackupFilesDir/rom-prepared`, not Android's disposable cache directory. A read-only DocumentsProvider exposes only explicitly granted completed trees. Successful dispatch reserves a copy across process restarts; failed dispatch releases only a newly created reservation. Automatic eviction and ordinary clearing preserve reserved copies. Clearing all copies requires the user to state that emulators are closed; the launcher never infers emulator liveness.

The existing acknowledged launch coordinator owns origin snapshots, recency and operation deduplication. `CANCELLED` is an explicit non-success outcome: cancelling an emulator/core/archive choice neither records a successful open nor shows a launch error. No pending external launch is persisted or replayed after process loss. `Dispatched` still means only that Android accepted `startActivity`; gameplay and emulator-specific storage/core behavior require separate device evidence.

## September page membership, search and interaction revision

Library contains available identified ROMs and Android apps whose effective category is `GAME`. Apps contains available Android apps whose effective category is not `GAME`, including emulators. Favorites contains available visible catalog items marked favorite, across both groups. Existing source-availability and hidden-item behavior is preserved; the 0.6 rule below keeps unidentified ROMs in Settings until corrected. A ROM remains a game regardless of a legacy category override. Filters and category counts share these boundaries, and obsolete or irrelevant saved filter keys resolve to `all` without changing catalog data. The global `allItems` snapshot remains available for details and cross-page actions.

Search requires a trimmed, nonempty query for both catalog and system actions. A blank query displays an entry prompt; zero matches never fall back to the full catalog. ROM queries match title, console ID, full console name and short console label. System results use the same `SearchResultCard` as catalog results, including the same artwork allocation and reserved caption line count. Console labels on cards and console filters use compact abbreviations; stored platform IDs and full names remain unchanged.

Each explicit destination selection, including reselection, emits a fresh page activation request. In controller mode, populated pages restore the selected surviving card or first card after placement; empty Search and Settings focus their meaningful body controls. Touch mode preserves selection/viewport but has no automatic focus or amber highlight. The first controller navigation/Confirm input reacquires a card without activating it. A completed tap selects and activates its item; a scroll gesture does not. Search's explicit edit shortcut may request text focus and summon the IME in either input mode. Modal dismissal restores the initiating control only in controller mode; filter/sort selection instead requests the resulting card.

The user explicitly approved All files access and the recommended shared-storage discovery design on 8 September. A one-time Settings action opens Android's app-specific permission screen; the scanner remains read-only. Shared-volume sources extend the existing authorized SAF-folder scanner. Their additive Room migration preserves existing identities and source access; Android permission approval remains a user action on the device.

`HomeItemOrdering` promotes only the newest available successful open, then groups effective Android games, identified ROMs and other apps with the shared title/ID tie-breaker. Older launch history does not promote additional items. Home presents at most 20 catalog items plus its Library shortcut. Sorting and tile preparation run off the UI thread only when catalog, recency or overrides change, independently of selection/viewport updates. Console accents derive from stable platform IDs, separate from stored metadata and the amber focus indicator.

Search uses a local editor buffer and an edit-entry query snapshot while showing live matches. Apply commits the current query; Cancel restores its entry query. Both close the IME and, in controller mode, return focus to results or the compact Edit search action. The edit session survives field blur so footer Apply/Cancel still work. Downward result scrolling collapses the header; upward scrolling and explicit editing reveal it. Mapped gamepad A/B reach these actions during IME editing; keyboard text/caret keys remain native. Y Clear is available during IME editing as well as ordinary Search: it empties the editor and Cancel baseline, resets filter to All and clears selection/anchor, retaining the active editor/keyboard. Outside an edit session, Clear opens editing.

Leaving the Search session for another page calls `clearSearch()` and removes Search's saveable local page state, including editor/collapse state and pending query-focus requests. Query, filter, selection and scroll anchor reset; opening details with a Search origin remains part of the same session and preserves them for return. Dock-opened Search and Settings return to Home on Back, while shortcut Search restores its explicit origin.

Collection indexing, filtering and sorting use the injected computation dispatcher (Default in production), separate from light selection/viewport state. Ordinary collections retain their previous cards while new criteria are calculated, with activation/navigation gated until settled; this prevents transient empty content and focus jumps. Reapplying the selected filter is idempotent and retains selection/anchor. Search publishes 128 initial matches, then up to 256 more per 24ms yield. A new query cancels the previous publication; results are not shown against mismatched query text. `rememberSearchResults` includes both the raw editor query and the published query in its memoization keys, so identical ROM result lists reappear when publication catches up, including trailing-space edits. This changes no ROM scan or artwork-cache policy. Prepared lists and membership sets are reused across navigation. Scroll observers save anchors without recomposing an entire screen for every pixel.

## 0.8 controller, layout, Search and status

Collection launch callbacks compare the latest requested criteria with the last settled criteria synchronously, in addition to published loading/searching state and item membership. A filter-change followed immediately by Confirm cannot launch an old card before the next UI frame. Cards keep their native focus targets attached during the pending calculation, with activation guarded at the callback boundary.

Up from the grid's first row enters the selected fully visible filter, falling back to All. This explicit entry prevents native geometric focus search from scrolling a clipped category into view. Passive Search restoration waits for settled results and chooses a visible filter or compact Edit search control when no result survives; it never starts editing. Explicit editing remains available through touch, X/Edit search and Clear. X from Search-origin details returns to the preserved Search session before requesting editing.

The collection grid supplies its available viewport height minus focus clearance to the shared card. Each card reserves two scaled title lines, any contextual subtitle, caption padding and text-layout rounding slack before capping its artwork. This keeps complete captions above the dock at larger UI scale without changing Home or Search-result geometry.

Digital and analog reports are combined into per-device held controls. Triggers use 0.55 engagement and 0.25 release thresholds. A press emits once. Trigger repetition starts after 650ms; D-pad, hat and stick repetition starts after 360ms. Acceleration begins after the applicable hold threshold, reducing intervals from 115ms to 55ms over 2.5 seconds. D-pad, hat and stick ownership is resolved consistently and same-direction source handoff retains the hold timer. Release, IME ownership and lifecycle reset cancel repetition; held axes suppressed by a reset must return to neutral before rearming.

L2/R2 step through collection/Search filters and move horizontally on Home. Filter selection clamps at either end without resetting the grid anchor. Header Left/Right follows All → fully visible category chips → All filters → Sort, reversing in the other direction and omitting the chooser when absent. Visible keys come from the measured strip viewport; clipped/offscreen chips do not receive D-pad focus, and focus movement never implicitly scrolls the strip. Down restores the selected/first settled card, falling back to All if none survives. In the card grid, Right/Left follow reading order across rows and stop at the whole collection's ends. Up/Down preserve the column and allow exit at vertical edges. One conflated navigation worker follows the latest requested key, using bounded scrolling before focusing a placed item; it never queues an animation per repeat. Controller dock shortcuts change destination selection without requesting dock focus. After editor/modal handling, Back returns dock-opened Search and Settings to Home; Home/Library/Apps/Favorites stay in place, and shortcut Search/details restore their explicit origins.

MainActivity owns a native pre-IME input host so Android cannot consume the first post-touch directional key before the launcher sees it. If a route transition clears native focus after a touch, a posted fallback focuses the native host without selecting a Compose control. It only runs while the window is focused and Search is not editing. The same handler still lets editor caret/text input pass through. Landscape text input requests no fullscreen/no extract UI, and the compact shell reserves a 48dp Apply/Cancel footer above the keyboard when at least 128dp remains for the combined query/results/footer layout. Touch taps and the footer use the same edit-session actions as mapped controller A/B.

Library aligns its title, fixed All, finite compact filter strip, All filters and SortSelector in one row at least 48dp high, with the count below. Category pills use natural widths; the strip's maximum width reserves about 72 reference units, roughly one short category, from the available space while retaining a 48dp lower bound. Fixed decorative L2/R2 hints stay outside the scrolling region. Header height grows with text; below 560dp content width, filters occupy a second row. All filters uses three columns of larger targets. Collection artwork is capped at 208 reference units with wider two-line captions; the existing five/four/three-column breakpoints remain, and both grid gaps use `spacing.xs`. Type/console tags sit on the artwork. The v0.7 typography increase remains: a further 10% after the prior 8% and tapered small-text adjustment, preserving regular clock/status weights and official light icons. Dock controls/glyphs retain their 10% increase. Footer content stays raised by eight reference units inside a band of at least 52dp.

Footer content transitions last 120ms, using one live action tree keyed by semantic input plus size animation and a short reveal fade. Visible input/meaning/label or button-mapping changes restart the fade; an enabled-state change alone does not. The implementation does not crossfade duplicate actionable controls. Loaded artwork changes crossfade over 140ms. Reduce motion bypasses both transitions.

Filter selection explicitly requests resulting-card focus. Restoration follows the request, filter and sort, waits for completed results, validates the placed stable item key and retries for a bounded number of frames. A newer criterion, touch input or modal cancels that request. Dock outline suppression during bumper changes affects presentation only: native focus targets remain available so Android's early key handling does not swallow the first direction after touch. Back hints are shown only when an editor, modal, Search/Settings return to Home or explicit origin route can consume them.

Settings exposes persisted 90/100/110/120% UI scale and Reduce motion through `DisplayPreferenceRepository`. The root applies UI scale to native layout density and reference tokens while retaining Android's independent font scale; it does not scale a rendered bitmap. Shell metrics use the root's raw pixel constraints, avoiding conversion of cached dp bounds through a newly selected density. Reference scaling is clamped before UI-scale compensation so compact windows do not amplify the selected percentage. Shell metrics and content density adapt to the resulting bounds. Either Reduce motion or Android's disabled animation setting suppresses decorative motion. This preference change leaves Room 4, source identity and catalog data unchanged.

`RecentActivityTracker`, its badges, lifecycle polling and `PACKAGE_USAGE_STATS` permission are removed. Existing installed preference files are not deleted, and their backup exclusions remain. `LaunchCoordinator` still records acknowledged successful opens for Home/Recent ordering. No running-process or live-ROM state is inferred: normal Android process visibility is restricted, and usage events describe activity history rather than liveness. Future per-game liveness would require a cooperating emulator interface; dispatch acknowledgement alone is insufficient.

Wi-Fi's crossed glyph follows radio enabled state rather than connection presence. Status glyphs use raw battery, temperature, memory and storage readings, retaining accessible names/values when the visible Wi-Fi label is omitted. `DeviceStatusSnapshot.bluetoothEnabled` and `notificationsPresent` map to visible shell indicators only for `StatusValue.Available(true)`; false, unavailable and unsupported values show no indicator.

Bluetooth uses the public adapter power-state API and `ACTION_STATE_CHANGED`, without scanning, reading connected-device identities or changing the radio. On the supported Android versions this read requires no Nearby devices runtime grant; failures remain unavailable. The notification dot reflects active notifications Android exposes to the user-approved `LauncherNotificationListenerService`. Settings → Android → Notification indicator opens `NotificationStatusAccess.settingsIntent()` for Android's app-specific Notification access screen, with a listener-list fallback when needed. The service is guarded by `BIND_NOTIFICATION_LISTENER_SERVICE`; no POST_NOTIFICATIONS or Usage Access permission is requested. Ranking-key emptiness becomes an in-memory Boolean, with no notification keys, text or payloads stored. Disconnect/revocation removes the known-presence reading, and stale service-instance callbacks cannot replace the current connection's state.

## Shared-storage discovery and Room 4

The approved All files permission enables a second, read-only source access implementation behind `RomSourceAccess`. SAF remains supported independently. `StorageManager` supplies mounted public volume directories; identities use `primary` or the volume UUID plus a validated relative document path. The resolver rejects traversal, links, hidden paths and Android private trees. No guessed mount path or filename-only identity merge is allowed.

Room 3→4 adds source access kind, comparable physical root and automatic origin fields plus a physical-root index. Existing source and game IDs, favorites, overrides and successful-open records remain intact. Manual Android storage grants can adopt physically matching automatic documents while retaining their item IDs. Manual and disabled roots reserve their physical subtrees from discovery. Removing a source remains a tombstone; only an explicit restore or folder selection enables it again. An enabled opaque third-party manual source pauses automatic discovery to avoid unprovable overlap, while manual scans continue.

Automatic discovery checks exact registered console folder aliases and requires planned game entries before adding a source. A persisted breadth-first queue limits each slice to 2,000 folders or ten seconds between folder operations, depth 24, and 50,000 queued folders. Individual source enumeration retains its separate limits. Once a candidate's complete inventory proves it contains games, discovery publishes that source in bounded batches using the same inventory. Source identity, assignments and subtree exclusions are rechecked before reuse; changed conditions require reconciliation instead. `completedSourceIds` prevents an immediate duplicate scan of sources already published during that discovery pass. Continuations process new or interrupted sources without repeatedly scanning all known sources; worker retries request a full reconciliation to recover interrupted indexing. The cursor records currently mounted volume identities, so newly mounted and remounted storage joins an active cycle. Discovery omissions never delete games or sources.

Foreground/resume refreshes are serialized and conflated. Storage broadcasts enqueue background work. Bounded continuation is persisted through WorkManager; the eight-hour deferrable refresh remains a recovery path. Disabling discovery stops adding roots but not registered-source reconciliation. Revocation marks shared sources unavailable through the existing failed-scan contract; SAF grants remain independent.

`SharedRomDocumentsProvider` exposes registered trees through explicit read-only URI grants. It advertises no browseable roots or write operations, chooses the most specific registered source, and applies nested-root exclusions to document resolution and child checks. Descriptor opens validate the opened file descriptor against the verified registered path before returning it. Android app-private storage and unregistered sibling trees are outside the provider boundary.

## PC frontend exports

The user approved GameNative integration, with GameHub also acceptable. PC exports use existing ROM source, item identity and preference contracts with platform ID `windows` and display label `PC`; no catalog schema or migration changes are required. They participate in Library, Search, Favorites and Home's ROM group. GameNative and GameHub frontend applications remain in Apps.

The scanner recognizes `.steam`, `.epic`, `.gog`, `.amazon` and `.pcgame` as source-specific GameNative exports. Each contains one positive 32-bit integer. Invalid or unreadable descriptors require repair; raw PC executables and archives in a recognized PC folder are not game entries. Launch rereads at most 129 bytes, rejects files over 128 bytes, and validates the ID again. Only the integer and the allowlisted source are sent to an explicit installed frontend activity, without a file URI, permission grant, account data, command or container configuration.

GameNative 1.2.0 or newer handles all five sources. The two compatible GameHub Lite packages handle Steam IDs only. Runtime component checks and existing console/item defaults still apply; multiple eligible targets require a choice when no valid preference exists. IDs from one non-Steam frontend are not translated into another frontend's private IDs. Additional games must be exported through GameNative's Export for frontend action to an accessible PC folder. Existing ES-DE covers can be reused; the approved Libretro provider does not currently supply a Windows collection. [Versioned contracts and verification](evidence/F16/pc-integration-050.md) define the physical coverage and remaining limits.

## 0.6 ROM scan performance and console identification

Scanning remains off the UI thread and read-only. A source must complete its bounded structural inventory and descriptor/playlist planning before publishing games, because a playlist can reference files in another directory. Publication then uses transactions of at most 128 planned entries. A valid grouped parent and the removal of its previous standalone member cards publish atomically, retaining those member identities and user references. Only the final successful transaction reconciles omissions. Cancellation or failure keeps completed valid batches and does not treat unread content as deleted. SAF rejects loading/error listings and rechecks folder access before returning a completed enumeration.

Unchanged entries avoid full catalog, provenance and action rewrites. Their scan-token updates are deferred to the final transaction, reducing repeated catalog invalidation. Group lookup visits the relevant sorted document subtree rather than inspecting the whole source for every archive/package. Discovery publishes completed sources as it finds them and reuses their inventories as described above; it does not wait for every volume to finish. These are implementation policies, not measured latency guarantees.

`RomScanRequest.documentPlatformOverrides` gives explicit item choices highest priority, followed by explicit folder/source mappings and known console folders. `metadataPlatformCandidates` accepts only exact identity evidence keyed by document ID. Metadata must match the format and cannot silently override a named folder or resolve conflicting automatic evidence; those cases remain unidentified until the user chooses. Changing an item assignment increments the source revision, so an older grouping plan cannot overwrite the correction.

An unrecognized folder name prompts for a console during setup. Skipping that choice retains the source for later configuration; games without a recognized console remain outside Home, Library, Search and Favorites. Settings separates identified and unidentified counts and offers source-wide assignment or **Identify games** for individual corrections. The individual chooser shows at most 100 unresolved entries in deterministic order; assigned entries leave the list so later entries become reachable. Repair requirements remain independent of console selection. Visible counts can decrease when unresolved entries are hidden or standalone tracks become members of a validated group; a particular count change requires item-level evidence before attributing its cause.

`RomMetadataIdentifier.identify` optionally supplies console candidates to the coordinator. `EsDeRomMetadataIdentifier` reads existing shared-storage ES-DE metadata through validated paths. Absolute game paths must match an enumerated file exactly; relative paths need an explicit validated known-console root from `custom_systems/es_systems.xml`. Title/basename matches and unexpanded `%ROMPATH%` references do not establish identity. Imports reject external XML entities, traversal, URI/command expansion and ambiguous fields. Limits are 8 MiB and 30,000 records per file, 32 MiB and 60,000 records per import; exhausting an import budget yields no partial identification evidence. A bounded cache reuses unchanged parsed imports. Libretro artwork behavior is unchanged; this identification path does not download broad databases or hash complete ROMs.

This revision adds no schema migration: Room remains version 4, with existing source/item IDs, favorites, history and manual choices preserved. `RomSource.unidentifiedCount` is derived from existing rows. The launcher never renames, rewrites or deletes the user's ROM files during discovery, classification or reconciliation. Current validation results and physical acceptance remain separate evidence records.
