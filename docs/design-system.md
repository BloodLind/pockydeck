# Handheld Launcher — design system and UI contract

Planning baseline: 7 September 2026; current behavior reconciled through v0.10. Reference measurements remain distinct from native dimensions. Calibration and acceptance evidence are recorded in [implementation progress](implementation/progress.md).

## 1. Reference authority

1. The user's latest decisions define product behavior: custom launcher status strip, existing Android system controls, most-recently-opened Home ordering, no in-game HUD or analytics.
2. [Home](references/home.png) defines the visual proportions, alignment, palette, and shell.
3. The Home section in [design-source.txt](references/design-source.txt) supplies useful typography and CSS measurements.
4. [Library](references/library-template.png) and other source pages supply content arrangements and states. Their header/footer/dock sizes and tiny text are not authoritative.

The screenshot includes an editor border, dotted surroundings, and a black area above the actual launcher. These are not application UI. The content rectangle is approximately x=32–1426, y=21–805 in the Home image; verify its crop before visual comparison.

### 9 September v0.10 readability and interaction revision

The user clarified that only small elements should increase slightly; the reference design and proportions remain authoritative. Keep reference-scaled typography, corner radii, card/title geometry and shell landmarks. Apply a 1.15 multiplier only to status, footer/control and Settings text plus small control glyphs. Clock and status text are regular weight. The subsequent icon correction restores the HTML preview's Material Symbols Outlined at weight 200 for Home, Library, Apps, Favorites, Settings and Search; status symbols use the preview's fill variants. Native vectors preserve the official paths, with sources recorded in [Material Symbols assets](references/material-symbols/README.md). Android font scaling remains in effect. Preserve 48dp touch allocations independently of smaller visible controls. Filters use compact dock-style pills. Do not introduce fixed text/artwork minimums that enlarge the whole layout on a dense display.

The small-text correction applies a tapered increase after reference scaling: 3sp becomes 6sp, 9sp becomes 11sp, and the increase reaches zero at 14sp. Intermediate sizes interpolate between those anchors. v0.7 adds 10% to all type roles after the previous 8% increase, with proportional line heights and normal Android font scaling. Clock/status text remains regular weight. Dock controls and glyphs are 10% larger than the previous revision. The footer reserves at least 52dp, with its action content raised by eight reference units for more bottom clearance. Other default-scale shell anchors remain the baseline where space permits.

Collection headers align title, a centered filter group, Grid/List and Sort in a row at least 48dp high, with the item count below. Native text measurement includes the actual scaled typography, padding, glyphs and 48dp targets; the header chooses a second filter row when those controls no longer fit, including at 100%, 110% and 120% UI scale. All is a separate fixed shortcut, followed by a divider and a finite strip of populated categories. The strip fits whole natural-width pills and reserves separation from the chooser; it does not depend on an estimated fixed label width. Fixed decorative L2/R2 hints sit outside the scrolling area. All filters opens a three-column chooser with larger targets. The strip, chooser and content share selection/count rules; explicit filter changes can scroll the selected category into view, while D-pad focus never reveals clipped/offscreen chips. Reference consoles lead when populated; remaining consoles follow alphabetically. Sort uses the shared icon/label/chevron control and a Title/Recent chooser with a compact Close control. Inactive pills retain the reference's muted treatment.

Every card has an unfocused perimeter outline; controller-focused cards use amber and a short lift/press transition, disabled under reduced motion. At the default grid size, collection artwork is centered and capped at 176 reference units, while two-line titles retain the wider column allocation. App and ROM collections share that geometry. The default five/four/three-column breakpoints remain, with five columns on the Flip 2 at default UI and grid scale; horizontal and vertical gaps use the smaller `xs` spacing. Library, Apps and Favorites each persist an independent Grid/List choice. List mode uses one column of the shared horizontal result cards beside a selected-item preview, retaining item identity, filters and sorting. APP, EMU, ANDROID and console abbreviations sit on the artwork, including Home; redundant type subtitles are removed. Console colors supplement written abbreviations such as GBA, SNES and PSX. Loaded artwork changes crossfade over 140ms, with immediate replacement during scrolling or Reduce motion.

Collection artwork also respects the available grid height after reserving focus clearance and the actual scaled caption line heights. At larger UI scale, artwork shrinks enough to keep both title lines inside the viewport above the dock. Home and horizontal Search cards retain their own geometry.

Collections keep their previous cards attached while a new filter or sort is calculated, preserving visual continuity and native focus. Card activation and navigation wait for settled results. Selecting the already active filter preserves the selection and scroll anchor. Search continues to hide results that belong to a different query.

Touch mode has no controller highlight or automatic leaf focus. A completed tap selects its item before activation; dragging does not launch it. The first controller navigation/Confirm input restores the surviving selected card, falling back to the first card, without accidentally launching it. Destination changes in controller mode focus a card; empty Search and Settings use their meaningful body controls. L1/R1 change the dock's selected destination without briefly highlighting the dock button.

Library contains ROMs and Android games. Apps contains other Android apps, including emulators. Favorites includes favorited games and apps. Android's declared game classification and recognized emulator packages supply defaults; user category changes take precedence for Android apps. Blank Search shows a prompt, and a query with no matches stays empty. System search results use the same card component and dimensions as catalog results.

Home places all available recent games/apps first in descending successful-open order. Remaining slots show one stable representative per console not already represented, one Android-game group where needed, then other apps in deterministic title/ID order. Multiple recent games from the same console remain eligible; unopened games from that console do not fill extra slots. Home keeps at most 20 catalog cards plus its Library shortcut. Every Home entry, reselection and return from another Activity while Home is the current page resets selection and scroll to the first card in both input modes, including when newer recency data arrives later. Modal dismissal alone preserves the initiating control. Unidentified ROMs stay out of Home and collections until their console is assigned.

Search collapses its heading/query/filter panel when scrolling results down, retaining an Edit search control; upward scrolling or the Search shortcut reveals editing. Confirm applies the edit and hides the IME; Back cancels the edit and restores its entry query. Controller mode then focuses results; touch mode stays unhighlighted. Footer Apply/Cancel remain valid when the field loses focus. Default legends are A/B and follow the user's Confirm/Back mapping. Hardware keyboard text/caret behavior remains native.

Closing Search for another page resets its query, filter to All, selected result, scroll anchor and local editor/collapse state. Opening item details from Search preserves the session for return. Y Clear works with or without the IME: it clears the query and Cancel baseline, resets filter/selection/anchor, and keeps an active editor and keyboard open. Cancel after Clear cannot restore the old query.

Passive Search focus restoration waits for settled results. A filter with no matches focuses a visible filter or the compact Edit search action without reopening the keyboard. Explicit Search/Edit or touch still opens the editor; the Search shortcut from Search-origin details returns to that preserved editor.

The old Recent activity tracker is removed. The user-approved optional Shizuku integration supplies process badges on Home only and is off by default. Library, Apps, Favorites, Search and Details do not display them. It checks requested package processes for the current user every three seconds while foreground. Android apps show observed process presence; ROMs name their last dispatched emulator, or configured/unique supported installed emulator, only when that process is observed. An emulator can be paused, cached or in its menu. Neither recency nor process presence identifies an active ROM or live gameplay session, and no Usage Access is requested. Disabled, denied or disconnected access removes process badges.

The v0.9.1 filter correction increases horizontal/vertical padding from 10/4 to 12/6 reference units and uses an 8-unit corner radius. Rendered controls and header measurement share the same geometry; native 48dp touch allocations remain. The settled strip sums exact pixel widths from its actual starting category, so later selections cannot expose a cut-off neighboring count. Touch scrolling remains continuous and settles on a complete cell without changing the selected filter. START alone is drawn at 85% inside its original footer hint allocation. Other legends and footer dimensions keep their existing size.

Original short controller cues accompany handled navigation, confirmation, back and page/filter actions. They use media volume, stay within the foreground launcher and do not request audio focus or change system audio settings. Touch, ordinary text entry and unhandled input stay silent. Settings → Controls can disable all launcher controller sounds; held input retains the existing acceleration and de-duplication rules.

Wi-Fi is icon-only and crosses out only when its radio is disabled. Temperature is explicitly battery temperature: blue below 15°C, red at 45°C or above, otherwise neutral. Battery is green only for Android's CHARGING state; otherwise it is red at 10% or below, yellow below 15%, and neutral above that. Being plugged in or full does not imply charging. RAM uses a distinct cool tint. Storage reports internal free space and all mounted external volumes; external totals retain an unknown marker if only some readings are available.

Bluetooth appears only when the public radio-state reading is available and enabled. A small neutral dot beside the clock appears only when the notification listener reports active notifications. Unknown, unsupported, denied or disconnected readings show neither indicator. Settings → Android → Notification indicator opens Android's opt-in Notification access screen. The launcher keeps only notification presence in memory; it stores no notification keys, text or payloads. Bluetooth radio-state reading needs no Nearby devices grant on the supported Android versions.

Settings offers launcher UI scale at 90% (Compact), 100% (Default), 110% (Large), and 120% (Extra large), plus Reduce motion. Both preferences persist in DataStore. UI scale changes native text, artwork and controls while preserving Android's independent font scaling; layout adapts to the remaining space. Decorative motion is suppressed when either the launcher preference or Android's animation setting requests it.

The v0.10 collection revision adds a separate 70–140% grid card size in ten-point steps, defaulting to 100%. It adjusts the collection artwork cap and column density while leaving typography, Home and Search sizing independent. Landscape List mode splits the content into a left list and right selected-item preview, with explicit Play/Open and Details actions. Touch selects a row without launching it; controller A still launches the focused row. Narrow windows retain a compact selected-item action, or open Details on row touch when height cannot accommodate that action.

L2/R2 filter hints have darker surfaces, smaller legends and rounder outer corners, with space between the strip and its adjacent controls. All filters has a larger text/icon gap. Sort options are more compact and Close is slightly larger, retaining native touch allocations. Internal storage glyphs are cyan and external storage glyphs amber; unknown and critical-low readings keep their existing state treatment.

Artwork is requested only for visible collection cards and decoded for their physical size. Scrolling suppresses loading/crossfade animation, and detached cards release their image references. Backgrounding cancels card image jobs and clears decoded caches. Scroll anchors persist when scrolling settles, and selection changes reuse prepared filter counts and widths.

Landscape editing requests an inline keyboard without its fullscreen extract editor. When the keyboard leaves at least 128dp above it, the compact shell reserves a 48dp Apply/Cancel footer while hiding the dock. Collection captions use native two-line text allocation, avoiding rounded fixed heights that cut off line two at larger Android font settings.

Footer content changes use a short 120ms fade and size transition. There is one live action tree keyed by semantic input, so the transition cannot expose a second set of actionable controls. Enabled-state updates alone do not restart the content fade. Reduce motion makes footer and artwork changes immediate.

Page changes reveal the single new live page with a 160ms fade and an 8dp vertical slide. Outgoing controls leave immediately; the transition does not keep a second actionable page. Reduce motion makes the replacement immediate. Settings → Launcher category counts are informational rows with no activation or controller focus target.

Resolved reference inconsistencies:

- Home puts clock/temperature/memory/storage on the left and Wi-Fi/battery on the right. Follow Home rather than the reversed PRD prose.
- Use visible Home colors rather than unused Tailwind palette entries or the earlier pastel proposal.
- Keep one common dock/footer size. Library and Apps currently shrink or reposition them.
- Match the Home tile outline and small lift first. Its HTML keeps the outer tile size constant; do not add large selection zoom based only on the PRD's “enlarged” wording.
- Sample game art, labels, counts, `RUNNING`, `Resume`, version strings, and memory-card status do not create functional requirements. Product data replaces fixtures.

## 2. Native sizing strategy

The device target is 1920×1080, landscape 16:9. The source measurements appear to use an approximately 1280×720 design coordinate system; that is an inference, not the device resolution.

Do not copy CSS pixels directly into Android `dp` or `sp`, and do not scale the whole UI as one bitmap. Measure the available window and its density, compute a single `ShellMetrics` at the root, and let native layout/text use those metrics. All normal destinations receive the same content rectangle.

Use these screenshot proportions as the first visual calibration targets:

| Landmark | Approximate proportion |
|---|---|
| Left/right content gutter | 3.7–3.8% of available width |
| Home card outer width | 18.6–18.8% of available width |
| Home card gap | 1.9% of available width |
| Platform badge top | 14.9% of height |
| Focused card top | 35.6% of height |
| Dock center | 85.2% of height |
| Footer top | 92.8% of height |

At the reference viewport, establish `ShellMetrics` values for gutter, status region, content bounds, dock region, footer height, and Home card geometry. Implement those as native constraints. The shell, not individual screens, selects a compact layout when height or density requires it.

The calibrated Home geometry is the baseline. At other densities/aspect ratios or larger font settings, preserve square artwork, readable text, usable controls, and shell hierarchy. Adapt content density and scroll behavior before reducing text below readable sizes. A partially visible next carousel card is intentional; a clipped focused card or footer is not.

Interactive controls should have at least a 48dp touch target where possible even when their visible glyph or pill is smaller; larger invisible hit regions must not overlap. This follows Android's [accessibility guidance](https://developer.android.com/develop/ui/compose/accessibility/api-defaults). Typography uses `sp` and respects system font scaling. Any resulting compact layout is tested separately from the default-scale Home fidelity baseline.

## 3. Theme and tokens

Expose one `LauncherTheme` with typed `colors`, `typography`, `spacing`, `shapes`, `depth`, and `motion`. Provide `ShellMetrics` separately because it depends on available space. Feature screens refer to semantic roles, not literal hex colors, arbitrary radii, or repeated font sizes.

Compose supports custom design systems and wrappers around existing controls. Reuse foundation/accessibility behavior where useful while supplying the launcher's visual treatment. Do not inherit default Material page styling accidentally. [Custom Compose design systems](https://developer.android.com/develop/ui/compose/designsystems/custom).

### Colors

| Semantic token | Home value / treatment |
|---|---|
| `backgroundTop` | `#313042` |
| `backgroundMiddle` | `#282736` |
| `backgroundBottom` | `#232230` |
| `surfaceCard` | `#252831` |
| `surfaceApp` | `#23262F` |
| `surfaceArtwork` | `#1C1E24` |
| `surfaceDock` | `#1E2229` |
| `dockInactive` | `#CBD5E1` at 20% opacity |
| `destinationSelected` | `#E2E8F0` with `#1E2229` foreground |
| `focus` | `#E5A01A` |
| `focusLowerEdge` | `#996409` |
| `textPrimary` | `#F4F5F8` |
| `textSecondary` | `#A6ACB8` |
| `textMuted` | `#78889B` |
| `confirm` / `success` | `#3EC07D` |
| `cancel` / `error` | `#E05244` |
| `borderSubtle` / `borderEmphasis` | White at 5% / 10% |

Use the Home radial gradient once in the shell. Amber is primarily the controller-focus signal; a badge may use the same accent without receiving a focus ring. Status meaning must also be conveyed by icon/text, not color alone. Check text contrast after native compositing; muted colors are a reference, not permission to make essential labels unreadable.

### Typography

Use **Plus Jakarta Sans**, bundled locally with the required font notice. The supplied CSS loads 400–800 but requests `font-black` (900); start with explicit 800 for the heavy style and compare it against Home instead of relying on synthesized weight.

| Semantic style | Home CSS reference | Proposed use |
|---|---|---|
| `homeTitle` | 34px / 40px line height, heavy, tight tracking | Selected Home item |
| `pageTitle` | New role, initially about 22–24 reference units | Library/Apps/Favorites/Settings headers; calibrate for readability |
| `tileTitleLarge` | 18px heavy | Home app fallback title |
| `clock` | 14px heavy | Status clock |
| `actionPrimary` | 13px bold | Primary footer action |
| `actionLabel` | 12px medium/bold | Other footer actions |
| `controlLabel` | 12px bold | Dock bumper labels, buttons/selectors |
| `statusValue` | 11px bold; battery 12px heavy | Telemetry |
| `platformLabel` | 11px heavy, uppercase | Selected platform tag |
| `badgeLabel` | 10px heavy, uppercase | Small platform/category badge |
| `tileTitle` / `tileSubtitle` | New roles; do not adopt template 8–10px captions | Readable collection captions and result subtitles |
| `body` / `settingLabel` | New roles | Empty states, settings, details |

The table records source scale and semantic roles, not final `sp` constants. Calibrate the font sizes together with `ShellMetrics` on the actual device, then freeze the native defaults centrally. New roles should follow Home's weight/contrast hierarchy without copying the templates' undersized labels.

Long text rules:

- Home reserves a stable metadata region. Allow a bounded two-line title at the standard title size; expose the full text through item details when needed. Do not push the carousel down.
- Collection titles allow up to two lines; secondary text allows one line. Ellipsize overflow without changing tile height.
- App names use the same bounded caption rules; details show the full name.
- Do not auto-shrink fonts to fit arbitrary titles or use package names as ordinary captions.
- Counts, translations, and unavailable sensor values must not shift shared shell anchors.

### Spacing, shapes, and motion

These are Home CSS measurements for calibration:

| Role | Reference value |
|---|---|
| Spacing scale | 4, 8, 12, 16, 24, 32, 48 design units |
| Shell horizontal gutter | 48 |
| Header top / bottom padding | 16 / 12 |
| Footer vertical padding | 12 |
| Home card / gap | 240×240 / 24 |
| Home outer / inner radius | 24 / 16 |
| Home default / focused frame | 6 / 8 |
| Home focus lift | 4 upward; unchanged allocated bounds |
| Dock circle / icon | 48 / 26 |
| Dock interior padding / gap | 10 / 12 |
| Bumper group spacing | 16 |
| Footer controller disc | 28 |
| Footer action-group gap | 24 |
| Bumper horizontal / vertical padding | 12 / 6 |
| Small control radius | 8 |
| Dock/sort radius | Fully rounded |
| Filter corner radius | 8 |

Keep subtle lower-edge depth and restrained shadows. Reserve focus space before layout so outlines and lifts are not clipped by lazy collections. The source transition is about 200ms; indicate focus immediately and animate only the decorative movement. Reduce or remove motion when the launcher preference or system requests it. No continuous idle animations are required.

## 4. Control library

Every control defines appearance, interaction states, accessibility semantics, and a preview/catalog example. Controls are state-driven and contain no data fetching, navigation controller, or ViewModel.

| Family | Controls | Responsibility |
|---|---|---|
| Foundations | `LauncherText`, `LauncherIcon`, `LauncherSurface`, `FocusFrame` | Typography, semantic icons, depth, shared focus treatment |
| Input | `LauncherButton`, `LauncherIconButton`, `FilterChip`, `SortSelector`, `SearchField` | Accessible activation/editing and consistent focused/pressed/disabled states |
| Labels | `PlatformBadge`, `StatusIndicator`, `ControllerGlyph` | Compact type/status/action presentation |
| Cards | `CoverTile`, `AppIconTile`, `SearchResultCard`, `ArtworkFallback`, `TileCaption` | Shared image geometry, fallback, captions, focus and activation |
| Collection layout | `PageHeading`, `FilterStrip`, `CollectionGrid`, `EmptyState`, `InlineNotice` | Reusable content structure and recovery controls |
| Settings | `SettingRow`, `ToggleRow`, `ChoiceRow`, `ActionRow` | Launcher preferences using the same typography/focus language |
| Modal layout | `LauncherDialog`, `ItemActionList` | Launcher-local dialogs with a contained focus scope |

`NavigationDock`, `StatusStrip`, `ControllerActionFooter`, and `LauncherShell` are application shell components composed from this library. `LibraryItemCard` lives in the app's shared UI package and maps domain-derived presentation into the generic card controls. This keeps emulator/catalog concepts outside the visual library.

Do not create independent Home, Library, and Favorites versions of the same card. Use semantic variants such as `HomeCover`, `CollectionCover`, and `AppIcon` with central defaults. A variant may alter size/caption placement; it must preserve focus behavior and accessibility.

### Minimal API shape

Illustrative contracts, not implemented code:

```kotlin
@Composable
fun CoverTile(
    title: String,
    variant: CoverTileVariant,
    activationEnabled: Boolean,
    onActivate: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    artwork: @Composable () -> Unit,
    badge: @Composable (() -> Unit)? = null,
)

@Composable
fun HomeScreen(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
)
```

A content slot/presentation adapter supplies artwork. The control does not decide whether an image comes from an Android package, a local ROM cache, or a metadata provider. Android icons use a centered fit; game artwork may crop within a square container without stretching. Image failure replaces the image inside the existing bounds.

Prefer named parameters/typed callbacks and small content slots over a universal card object containing every screen's behavior. Focus/pressed state comes from the actual Compose interaction/focus system; preview fixtures can simulate it. Focus callbacks report stable selection upward without launching an item.

## 5. State contracts

| State | Visual/behavior rule |
|---|---|
| Destination selected | Light-gray dock fill, independent of controller focus |
| Filter selected | Selected chip fill, independent of controller focus |
| Controller focused | One visible amber frame; reserved drawing bounds |
| Pressed | Short feedback without duplicate activation |
| Activation unavailable | Muted treatment and a reason; may remain focusable for explanation/recovery |
| Artwork pending/missing | Stable fallback surface; same dimensions and accessible item name |
| Data loading | Initial state only when no cache exists; cached content remains usable during refresh |
| Empty | Clear explanation and one useful focusable action |
| Error | Recoverable message/action; preserve available content and focus |

The app-level `RunningIndicator` renders only a live process label supplied by the optional monitor. Its accessibility description identifies process detection. There is no suspended-game or active-ROM state; recency remains ordering data.

Useful UI-only models:

- `TileUiModel`: stable UI key, title, subtitle, artwork presentation, badges, supported actions, availability reason.
- `ControllerPromptUi`: semantic action key, physical glyph, label, enabled state.
- `StatusValueUi`: available formatted value, temporarily unavailable value, or unsupported capability.
- `CollectionUiState`: title, filter choices, sort, visible items, loading/error/empty presentation.
- `ShellUiState`: active destination, status presentation, current controller prompts, optional launcher message.

Unavailable telemetry uses a stable placeholder such as `—` when temporary; unsupported optional readings are omitted consistently. Keep measured value widths stable. The adapter/ViewModel determines what a reading means; the control only renders the supplied presentation and accessibility label.

## 6. Page templates

All templates live inside the shell's content slot and use its gutter. Their ViewModels may differ; their common controls and collection primitives do not.

| Page | Composition | Main states and actions |
|---|---|---|
| Home | Platform/title metadata above a horizontal cover carousel | Recent items before stable console showcase; at most 20; return selects and scrolls to first; missing art/removed item |
| Library | Measured heading/count + centered filters + Grid/List + Sort | All/matching platform/empty platform; five grid columns at default Flip 2 scale or one-column list; A Play/Open, Y Details |
| Apps | Shared header + independently persisted icon grid or horizontal list | All, populated Emulators/Other filters; native icons; long names; no apps/icon unavailable |
| Favorites | Shared Grid/List collection with All/Games/Apps filters | Mixed favorites; removal keeps nearest focus; empty state links to Library |
| Search | Query + All/Games/Apps/System categories + compact result cards | Empty-query prompt, matches, no results, restricted filters, IME visible; system cards match catalog-card dimensions |
| Settings | Category list and grouped setting rows | UI scale/Reduce motion, controller mapping, default-HOME setup, informational category counts, optional notification/Shizuku access, ROM sources, emulators and artwork |
| Item details | Shared heading/artwork + full title/platform + supported actions | Open/Play, favorite, Android app info where available, emulator choice and supported artwork controls |

Library includes ROMs and Android games. Apps shows the remaining Android applications, including emulators, from the same catalog. Installed Android game classification uses declared metadata with a user override; unknown apps remain in Apps until classified. Library filters are All, Android when native games exist, and console abbreviations derived from available ROMs.

Favorites stores references, not duplicate catalog objects. Removing a favorite does not uninstall an app or delete a file. Empty states must not make scanning or scraping the normal maintenance workflow.

Search is a dock destination, not an in-game overlay. It searches only locally indexed content. In All results, an Android game appears once in Games rather than being duplicated under Apps. Category changes retain the query; platform filters appear only when relevant. Reuse `SearchResultCard` in a two-column layout when space permits, and one column when the IME or width makes two columns unreadable.

Item details is a launcher content route with origin restoration, using the same shell. It is optional for launching. Do not import the PRD's full-screen analytics/save-management hub.

## 7. Focus, keyboard, and restoration

Compose owns focus requesters, focus groups, pressed interactions, scroll controllers, and IME handles. ViewModels own selected IDs and query/filter state. No control stores navigation history or decides most-recently-opened ordering.

Define explicit transitions between header controls, filters, content, and dock. In grids, Right continues from a row's last card to the next row's first card; Left reverses this and horizontal navigation stops at collection ends. Up/Down preserve the column, clamp an incomplete final row and can leave at vertical edges for filters or the dock. Focus follows the latest requested item with short bounded scrolling, without queued animations. Home Up can reach a meaningful metadata action if one exists; otherwise it remains in the carousel. Decorative labels never become focus stops.

L2/R2 step through the finite filter sequence on collection/Search pages and move through cards on Home. A press moves once. Trigger holds start repeating after 650ms; D-pad, hat and stick holds start after 360ms. Acceleration begins after the respective threshold and shortens repeat intervals from 115ms to 55ms over 2.5 seconds. Filter navigation stops at either end without changing the current grid anchor. Digital and analog reports of one trigger share a held state with separate press/release thresholds, so a slow squeeze does not double-step. Direction/source handoff preserves elapsed hold time; release, editor entry and loss of activity reset repeating input.

The collection header's D-pad Left/Right path is fixed All → fully visible category chips → All filters → Grid/List → Sort, and reverses in the opposite direction. Clipped and offscreen chips are excluded; moving focus does not implicitly scroll the strip. Down restores the selected or first settled card, falling back to All if none survives. L2/R2 change the selected filter through the full finite sequence.

Dialog focus is contained until dismissal, with automatic focus only in controller mode. The system keyboard handles editing while visible; mapped Back cancels the edit before leaving Search. Touch selection followed by controller input restores a valid visible focus target. Shell shortcuts are disabled or delegated where the active modal/editor consumes them. Filter/sort choices return controller focus to their resulting cards; ordinary modal dismissal restores the initiating control.

After active editor/modal handling, Back on dock-opened Search or Settings returns to Home. Back on Home, Library, Apps or Favorites preserves the current page, focus, selection and viewport. Shortcut Search and item details retain explicit origins and Back restores those origins; when that destination is Home, Home resets to its first card. Dismissing a modal without leaving Home does not reset the carousel.

Restore by stable key after the item is available/composed. Do not save a focus requester or reuse a numeric index after reordering. Nearest-item and empty-state fallback rules are defined in [the project plan](project-plan.md).

## 8. Component gallery and visual acceptance

Create a debug-only gallery in the app, built from the production control library. It doubles as the implementation reference for future screens.

Include:

- Every token/type style and control variant.
- Active destination with focus somewhere else; active destination itself focused.
- Selected chip with another chip focused.
- Focused/default/pressed/unavailable controls.
- Long titles, translated labels, large counts, no art, failed image, and app icons of different shapes.
- Available, unavailable, and unsupported status values.
- Empty Library/Favorites/Search and a launcher dialog.
- Normal Home, Home after recency reordering, and Search with keyboard-visible constraints.

Acceptance procedure:

1. Render Home at the actual device viewport/density using local fixture assets.
2. Compare the app content rectangle against the Home screenshot after excluding editor surroundings.
3. Calibrate shell anchors, card geometry, typography, and depth centrally; record final dp/sp defaults.
4. Render Library/Apps/Favorites with the same shell and adapt only their content density.
5. Validate readability, touch targets, full controller reachability, and clipping on the physical Flip 2, including larger fonts and IME appearance.
6. Establish screenshot regression baselines and focused interaction tests once those values are accepted.

The first visual milestone is a faithful Home plus a reusable gallery and shell. It is not a pixel-for-pixel recreation of the inconsistent secondary templates.
