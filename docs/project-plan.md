# Handheld Launcher — project plan

Planning baseline: 7 September 2026. This document plans the application; it does not report implemented functionality.

## 1. Confirmed direction

Build a small native Android HOME launcher using Kotlin, Jetpack Compose, MVVM, coroutines/Flow, Room, and DataStore. Start with the Retroid Pocket Flip 2 and its Android 13 environment, as agreed in yesterday's **Build handheld gaming launcher** task.

Use the approved Home design for the shared shell, proportions, spacing, and visual language. Library and the other supplied pages are templates for content and behavior; their exact measurements must be adapted to Home. The earlier pastel direction is superseded.

The current request adds an explicit reusable control library and clear separation between styles, controls, page composition, ViewModels, and data/platform services. The proposed module structure below supports that separation without making every screen a separate Gradle project.

### Decisions resolved in this conversation

| Topic | Decision |
|---|---|
| Status area | Custom status strip inside the launcher, matching Home. |
| Other Android controls | Keep the notification shade, volume UI, brightness/system controls, power menu, and Retroid controls. |
| In-game HUD and analytics | Excluded from this project, not deferred delivery milestones. |
| Home ordering | Most recently opened first. Reopening an item moves that same item to the front. |
| Return behavior | Restore selection by stable item identity and restore the scroll anchor; adjust only when ordering or availability makes the old position invalid. |
| Application lifecycle | Launch/reopen external applications through Android. Recency is not a list of guaranteed running processes. |
| Foundation | Carry forward yesterday's small new launcher approach and permission for selective GPL-compatible Argosy reuse later. |
| Current deliverable | Project plan and design-system specification; application implementation follows separately. |

The user called the Home behavior “FIFO”; the clarified behavior is **most-recently-opened ordering**. Use that precise term in code and documentation.

## 2. Product scope

### Included

- Become the user's selected Android HOME application; also remain launchable normally during setup.
- One persistent launcher shell with six destinations: Home, Library, Apps, Favorites, Settings, Search.
- Controller-first navigation with working touch input.
- Automatic discovery of installed launchable Android apps, including emulator and system apps that expose launchable activities.
- Cached content available before background discovery or artwork work finishes.
- Most-recently-opened ordering, favorites, optional item details/actions, and reliable navigation restoration.
- Configured ROM folders, automatic reconciliation, external emulator selection and launching in later implementation stages.
- Asynchronous metadata/artwork enrichment with an immediate local fallback.
- Search of the launcher's indexed games, apps, and available settings/system actions.
- A custom launcher status strip with honest, available device information.

### Excluded

- Replacing Android, custom firmware, custom notification/volume/power controls, and in-game overlays.
- TDP/GPU/fan/FPS controls or changing another application's performance.
- Analytics dashboards, playtime tracking, usage monitoring, and session-duration measurement.
- Process killing, a running-app queue, guaranteed suspension/resume, and custom task management.
- Save-state management, BIOS management, cloud save synchronization, and emulator controller remapping.
- A custom keyboard, online/global device search, and searching inside other apps.

The PRD's `RUNNING`, `Resume`, `MemCard Slot A Ready`, temperature values, and library counts are sample design content. Do not reproduce them as real state. Use `Play`, `Open`, or `Reopen` where appropriate; ordinary launch history does not justify a running indicator. Last-opened time is functional recency data, not an analytics feature.

## 3. Shared shell and screen composition

```text
MainActivity
└── LauncherTheme
    └── LauncherShell
        ├── LauncherBackdrop
        ├── StatusStrip                    fixed placement
        ├── DestinationContent             changes by route
        │   └── Home / Library / Apps / Favorites / Settings / Search
        ├── NavigationDock                 fixed placement
        ├── ControllerActionFooter         fixed placement, contextual labels
        └── LauncherOverlayHost            launcher dialogs and item actions only
```

`LauncherShell` is composed once around destination content. Feature screens must not recreate the background, status strip, dock, footer, or system-inset policy. Their allocated content rectangle is the only normal page layout surface. Local headers, filters, grids, and empty states live inside that rectangle.

“Persistent” means stable ownership, structure, and placement. Status values, active-destination styling, and footer prompts remain reactive. A clock update must not recreate the page or reset its focus.

The root owns top-level navigation and modal layering. Each page supplies a typed action description for its focused element. The footer renders that description, and the input dispatcher invokes the same actions. This prevents a footer that says `Play` while A actually opens a filter or dialog.

Use a custom status strip and immersive presentation only in the launcher window. Leave Android responsible for system controls and other applications' windows. Allow transient system bars through the normal edge gesture; tolerate their appearance without losing navigation state. This uses Android's supported [immersive-mode APIs](https://developer.android.com/develop/ui/views/layout/immersive).

The search keyboard is an explicit layout exception: use the system IME, keep the query visible, shrink/scroll results, and move the shell's bottom area above the IME where space permits. Validate keyboard usability on the Flip 2; do not claim its system keyboard is controller-compatible before testing it.

## 4. MVVM boundaries and project structure

Start with four focused Gradle modules. Keep feature screens as packages until independent build/reuse needs justify extracting them.

| Module | Owns | Does not own |
|---|---|---|
| `:app` | Activity, dependency wiring, navigation, shell, routes, page ViewModels, page composition, UI mapping | Database queries, package discovery, reusable visual tokens |
| `:core:domain` | Stable item identities, library/launch models, repository interfaces, shared ordering/launch rules | Android Context, Compose, Room entities, HTTP clients |
| `:core:data` | Repository implementations, Room, DataStore, Android adapters, discovery and launch dispatch; later ROM and metadata workers | UI state, focus requesters, page layouts |
| `:core:designsystem` | Theme tokens, text styles, visual controls, controller glyphs, focus treatment, reusable layout primitives | ViewModels, repositories, navigation, emulator knowledge |

```text
app/
  di/                         AppContainer, ViewModel factories
  platform/                   Activity-bound role requests and window integration
  shell/                      LauncherShell, ShellViewModel, ShellUiState
  navigation/                 destinations, page restoration, back policy
  input/                      input normalization and active focus scope
  ui/components/              LibraryItemCard, mapped item/action presentation
  feature/
    home/                     HomeRoute, HomeScreen, HomeViewModel, HomeUiState
    library/                  LibraryRoute, LibraryScreen, LibraryViewModel
    apps/                     AppsRoute, AppsScreen, AppsViewModel
    favorites/                FavoritesRoute, FavoritesScreen, FavoritesViewModel
    search/                   SearchRoute, SearchScreen, SearchViewModel
    settings/                 SettingsRoute, SettingsScreen, SettingsViewModel
    details/                  ItemDetailsRoute, ItemDetailsScreen, ItemDetailsViewModel
  src/debug/.../catalog/      executable component/state gallery
core/domain/
  model/                      ItemId, LibraryItem, LaunchTarget, Availability
  repository/                 LibraryRepository, PreferencesRepository, etc.
  policy/                     RecentOrdering, EmulatorResolution when needed
core/data/
  repository/                 implementations and mapping
  local/                      Room database, DAOs, preference/state stores
  android/                    package discovery, launch adapter, status providers
  discovery/                  serialized catalog reconciliation
  rom/                        added with ROM support
  metadata/                   added with enrichment support
core/designsystem/
  theme/                      colors, typography, spacing, shape, depth, motion
  controls/                   focus surfaces, buttons, chips, badges, fields
  cards/                      cover/icon/result presentation
  layout/                     page heading, filter strip, empty state
  glyphs/                     controller and navigation symbols
```

Dependency direction: `app → domain + designsystem`; app wiring also references `data`; `data → domain`. `designsystem` has no project dependency on data/domain/app. Feature ViewModels import domain contracts rather than concrete data implementations. Pure ordering rules can be tested without Android.

Use an application-scoped `AppContainer`, constructor injection, and explicit ViewModel factories initially. Avoid service lookups inside controls, generic base repositories, and a use-case class for every getter. Shared rules that matter—launch validation, recency, or emulator choice—deserve named operations; simple reads can use repository flows directly. The module count and manual DI are project choices, consistent with Android's guidance to size architecture to the application. [Architecture recommendations](https://developer.android.com/topic/architecture/recommendations), [modularization](https://developer.android.com/topic/modularization).

### Per-page contract

```text
Repository Flow → ViewModel → immutable UiState → Screen → reusable controls
                       ↑                          │
                       └──── typed user actions ──┘
```

- `HomeRoute` obtains the ViewModel, collects state with lifecycle awareness, and connects navigation/Activity requests.
- `HomeScreen(state, onAction)` describes layout and can render with preview/fake state.
- `HomeViewModel` applies page behavior and exposes `StateFlow<HomeUiState>`.
- Reusable controls accept values and callbacks. They never obtain a ViewModel or read a database.
- `FocusRequester`, scroll mechanisms, animation state, and IME handles stay in UI state holders. ViewModels hold durable identity/filter/query information, not Compose objects.
- Activity-bound requests use an explicit, acknowledged UI request when needed. Do not replay a launch or role prompt merely because state is recollected.

This follows Android's [UI-layer separation](https://developer.android.com/topic/architecture/ui-layer). It is MVVM with state flowing down and actions flowing up, rather than mutable two-way bindings between controls and repositories.

## 5. Unified library and data behavior

Use a stable `LibraryItem` abstraction with `AndroidApp`, `RomGame`, and `SystemAction` variants. Presentation sees an item title, artwork/icon, type/platform label, availability, and supported actions. Launch dispatch resolves the underlying target.

| Data | Proposed identity / persistence |
|---|---|
| Android entry | Current-user component identity, retaining multiple launchable activities in one package |
| ROM entry | Stable generated item ID plus source-root/document identity; never identify only by filename |
| System action | Explicit stable action key; index only supported actions |
| Most recent use | Item ID and persisted increasing open-order key; optional wall-clock date is display data only; one record per item |
| Favorites | References to item IDs; removing a favorite never deletes its app or ROM |
| Navigation | Destination, selected ID, first visible ID/offset, filter/sort/query keys |
| Artwork/metadata | Cache references and provenance independent from discovery records |

Room is the source of observable catalog, recency, and favorite data. DataStore holds small preferences and durable navigation snapshots. SavedStateHandle/rememberSaveable hold small restoration keys for recreation; reconstruct lists from Room rather than storing them in a Bundle. A ViewModel alone does not survive process death. [State-saving guidance](https://developer.android.com/develop/ui/compose/state-saving).

### Open, reorder, and return

1. User activates one launchable item with A or touch.
2. Save the current navigation snapshot; reject duplicate activation while dispatch is pending.
3. Revalidate the target and dispatch through its Android/external-emulator adapter into an external Android task. Use normal launcher task behavior for Android components and validate each emulator's flags/contract; never place a game Activity above the launcher's `singleTask` Activity, where a later HOME intent could clear it. [Android task behavior](https://developer.android.com/guide/components/activities/tasks-and-back-stack).
4. After successful dispatch, update that item's recency once. Failed dispatch leaves ordering unchanged and shows a recoverable error.
5. The Home query emits items ordered by most recent successful open. Reopening updates the existing entry rather than adding another card.
6. On return, restore selection by ID. Restore the old scroll anchor when still valid; otherwise keep the selected item visible at the closest feasible position.

Example: `[C, B, A] → open B → [B, C, A]`. B stays selected when returning from a launch initiated on Home. Physical pixels cannot always remain identical when an item moves to index zero; valid selected-item continuity takes priority over a stale numeric index.

Moving focus, opening item details, or receiving artwork does not promote an item. Recency applies to Android app and ROM entries. Internal launcher routes and `SystemAction` shortcuts remain in Search/Settings rather than entering Home recents; a real Android Settings app component still behaves as an Android app. Record opens initiated by this launcher; do not add global usage monitoring to detect opens from other apps. Never equate successful intent dispatch with proof that an external application remains running. Use a serialized increasing open-order key so clock changes or timestamp ties cannot corrupt recency. Unopened entries follow recents in a deterministic title/ID order. Home can initially show twelve entries plus a Library action; this is a content limit, not twelve fixed on-screen tiles.

### Automatic Android discovery

- Enumerate real `MAIN/LAUNCHER` components off the main thread, including launchable system apps; exclude this launcher itself.
- Declare the package visibility needed for those queries. Do not request broad package access by default.
- Use package callbacks while active and reconcile at startup/resume to recover missed changes. `LauncherApps.getActivityList()` may include synthesized app-details entries; filter/validate if using it as an inventory source. [Package visibility](https://developer.android.com/training/package-visibility/declaring), [LauncherApps API](https://developer.android.com/reference/android/content/pm/LauncherApps).
- Read cached rows immediately. Serialize/coalesce discovery triggers and transactionally apply a completed inventory.
- A failed, cancelled, or partial enumeration must not clear the cache or infer mass deletion.
- Keep discovered fields separate from favorites, recency, and user artwork overrides. An app update must not erase them.
- Remove uninstalled/unavailable entries from active launch lists, restore focus to a neighbor, and retain history/favorite references separately so reconciliation is not destructive.

### ROMs and metadata

Add these after the native-app launch loop works:

- Ask once for user-selected ROM roots through SAF and persist the granted tree access. Handle revoked permission and removed storage as unavailable sources, not empty successful scans.
- Infer platforms from configured roots and formats. Keep ambiguous formats unresolved until the user chooses, then remember the mapping.
- Index incrementally and commit complete scan batches. Avoid duplicating multi-file/disc sets as separate launch entries; validate these rules against the first supported platforms.
- Use foreground reconciliation and available change signals. SAF does not promise immediate recursive notifications, and URI grants are not raw file paths. [Android document access](https://developer.android.com/training/data-storage/shared/documents-files).
- Adapt external emulator launch mappings selectively, validate each supported app/version and URI contract, and preserve attribution for reused source. Do not bring in Argosy's broader product architecture.
- Add one metadata provider first, with bounded retries, persistent caching, manual correction, and progressive updates. No match/network failure may prevent local launching.
- Use WorkManager for durable deferrable enrichment and reconciliation work. It is not an exact timer; foreground responsiveness cannot depend on the next periodic run. [Work scheduling](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).

## 6. Controller and navigation contract

Normalize D-pad, hat axes, left stick, physical button mapping, and touch activation to semantic actions. Use a dead zone and bounded repeat for held directions; suppress duplicate events from the same physical action. Scope input to the active launcher surface, never other applications.

| Input | Behavior |
|---|---|
| D-pad / stick | Move within the active focus region; scroll offscreen content into view |
| A / Confirm | Invoke the focused control's primary action |
| B / Back | IME first, then dialog/details, then originating page; root Home remains open |
| X | Open global Search; within Search focus the query |
| Y | Open the selected item's details/actions when supported |
| Start | Open page/item menu according to the visible footer context |
| L1 / R1 | Previous/next destination in the fixed six-item dock order |
| L2 / R2 | Previous/next filter where a filter strip is available |
| Android Home | Android routes to this HOME app; no global key interception |

Provide a confirm/back mapping preference and matching controller glyphs. Default shoulder navigation can wrap between first and last destination; spatial grid edges do not wrap unexpectedly. Footer actions disappear or disable when they do not apply.

One input path owns an action: modal/IME handling has priority, then the focused control/page, then unhandled shell shortcuts. Do not consume the same key at both the screen and the shell. Compose focus groups and explicit transitions govern movement across header controls, filters, content, and dock. [Compose focus behavior](https://developer.android.com/develop/ui/compose/touch-input/focus/change-focus-behavior).

Maintain a separate saved state for every destination. Dock switches do not accumulate a Back history. B from Library, Apps, Favorites, Settings, or Search reached through the dock returns to Home. Search opened through X remembers its origin; Back restores that origin's filter, selected ID, and anchor. B on root Home keeps the launcher open. Repeated HOME intents must not create another Activity or reset restored launcher state. Details and menus return to their exact initiating item when it still exists.

Focus restoration fallback: saved ID → nearest surviving item → first item → empty-state action → active dock item. If a target is offscreen, scroll it into composition before requesting focus. Preserve one visible amber focus target during controller use; the selected dock destination remains a separate light-gray state.

## 7. Android platform integration

- Register one exported `singleTask` Activity with separate `MAIN/HOME/DEFAULT` and `MAIN/LAUNCHER` intent filters. Handle new HOME intents without rebuilding state.
- Offer the system HOME role request without blocking ordinary browsing if declined. Android requires user selection; the app cannot silently make itself default. [HOME role](https://developer.android.com/reference/android/app/role/RoleManager).
- Let Android handle boot/unlock routing and external app tasks. No launcher boot service or global Home-key interception.
- Keep landscape as the supported initial mode. Calibrate at 1920×1080 and on the physical Flip 2, respecting display density, cutouts, and IME/system insets.
- Pin a mutually compatible Kotlin/Compose/AGP/Gradle/JDK set during scaffolding. Yesterday's JDK/SDK findings are historical context, not a substitute for verifying the installed build tools. This initial plan does not promise Play Store eligibility or Android 11/12 compatibility.
- The installed-app slice needs no ROM-storage, Internet, overlay, accessibility, usage-access, or process-control permissions. Add only ordinary status-read permissions actually required by the selected status providers. Internet arrives with metadata; ROM access arrives through folder grants.
- Status providers expose available/unavailable/error values. Prefer callbacks and lifecycle-bound collection, with low-frequency sampling where unavoidable. Do not continuously poll while a game is foreground.

Battery/clock/connectivity are the initial status baseline. Memory/storage and a clearly identified temperature reading are added only when supported accurately. A battery sensor must not be presented as CPU or ambient temperature. Optional slots collapse or show a restrained unavailable value; sample sensor data is never shipped as live data.

## 8. Delivery stages and completion gates

Each stage produces a reviewable result. These are dependency stages, not calendar estimates; ROM/emulator compatibility and physical-device results determine the later effort.

| Stage | Deliverable | Completion gate |
|---|---|---|
| 0. Design contract | This plan, token inventory, shared-shell geometry, component/state matrix | Home authority and product scope recorded; native sizing calibration is an explicit implementation task |
| 1. Foundation and visual library | Gradle scaffold, theme, control gallery, static shared shell and Home using local fixtures | App builds; Home proportions match reference; all controls demonstrate focus, disabled, missing-data, and long-text states |
| 2. First working HOME loop | Role setup, cached installed-app discovery, A launch, recency and return restoration | Set default HOME → select app → launch → press Home → same item selected with updated order; no scan blocks browsing |
| 3. Launcher destinations | Library, Apps, Favorites, Search, basic Settings, local item details/actions | All six dock destinations work through the same shell; controller-only navigation and Back/IME restoration pass |
| 4. ROM launch loop | SAF setup, incremental ROM index, emulator detection/defaults, external ROM launching | Copy/remove ROM and reconcile automatically; missing storage preserves data; supported emulator contracts pass on device |
| 5. Artwork and metadata | First provider, durable cache/queue, progressive enrichment, corrections | Offline launch still works; provider errors do not block use; artwork updates do not change focus or card dimensions |
| 6. Device hardening | Profiling, lifecycle/device tests, final visual calibration, installable APK and setup guide | Acceptance matrix passes on Flip 2; remaining limitations explicitly documented |

Stage 1 creates all dock positions but does not ship inert destinations as a finished product. Stage 2 is the smallest functional slice; stage 3 completes the launcher UI before the ROM and enrichment work expands it. Library initially shows Android entries from the unified catalog and later gains ROM entries without a new screen architecture.

Per-stage validation uses meaningful domain/repository and UI tests rather than tests that simply restate style constants. Run a debug build, Android lint, relevant JVM tests, and applicable Compose instrumentation tests. Add screenshot baselines for the stable shell and Home after calibration, not for unapproved raw templates.

## 9. Acceptance matrix

| Area | Required evidence |
|---|---|
| Shared design | Home, Library, and other destinations have identical normal-mode shell anchors; cards/focus do not collide with dock/footer |
| Input | Every launcher action is reachable by controller; no focus trap after filtering, modal dismissal, empty results, touch use, or item removal |
| Recency | Opening B changes `[C,B,A]` to `[B,C,A]`; repeated opens never duplicate B; failed dispatch changes nothing |
| Return | App→Home, screen switching, Search→Back, Activity recreation, and process restoration recover valid selection and scroll anchors; verify external targets occupy separate tasks and returning HOME does not finish them as a side effect of launcher task reuse |
| Discovery | Install/update/uninstall while active or inactive converges correctly; multiple components survive; a failed inventory does not empty the library |
| Performance | Cached Home appears independently of scan/scrape completion; no disk/network work on the main thread; measure startup, warm return, scroll jank, and idle activity on device |
| Status | Real readings or an unavailable state; no fake running/memory-card/temperature status; Android shade and volume UI remain accessible |
| ROM resilience | Persisted grant survives reboot; SD removal/revocation marks source unavailable; cancellation does not cause destructive reconciliation |
| Artwork | Missing/failed/late images preserve dimensions, ordering, and focus; images decode near displayed size with a bounded cache |
| Device lifecycle | Repeated physical Home presses, lid close/open on launcher and in a game, reboot/unlock, and firmware process cleanup tested on the Flip 2 |

Set numeric startup/memory budgets after the first physical-device measurement. The PRD's “zero input latency” is an aspiration, not an achievable acceptance metric. Immediate focus feedback and measured responsiveness are the targets.

## 10. Implementation handoff

Start with stage 1: scaffold the four modules, wire one theme and shell, implement the component gallery, and reproduce Home with local preview data. Then complete the installed-app HOME loop before adding ROM or network services.

The UI build contract is in [design-system.md](design-system.md). The supplied [Home image](references/home.png) is the geometry reference; the [Library image](references/library-template.png) and [original PRD/HTML](references/design-source.txt) provide adaptable templates and provenance. Statements such as “phase complete” inside the supplied PRD describe its design document, not the actual project implementation.
