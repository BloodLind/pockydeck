# Handheld Launcher — design system and UI contract

Planning baseline: 7 September 2026. Native dimensions below require calibration during implementation; reference measurements are not yet verified Compose dimensions.

## 1. Reference authority

1. The user's latest decisions define product behavior: custom launcher status strip, existing Android system controls, most-recently-opened Home ordering, no in-game HUD or analytics.
2. [Home](references/home.png) defines the visual proportions, alignment, palette, and shell.
3. The Home section in [design-source.txt](references/design-source.txt) supplies useful typography and CSS measurements.
4. [Library](references/library-template.png) and other source pages supply content arrangements and states. Their header/footer/dock sizes and tiny text are not authoritative.

The screenshot includes an editor border, dotted surroundings, and a black area above the actual launcher. These are not application UI. The content rectangle is approximately x=32–1426, y=21–805 in the Home image; verify its crop before visual comparison.

### 8 September readability and interaction revision

The user clarified that only small elements should increase slightly; the reference design and proportions remain authoritative. Keep reference-scaled typography, corner radii, card/title geometry and shell landmarks. Apply a 1.15 multiplier only to status, footer/control and Settings text plus small control glyphs. Clock and status text are regular weight. The subsequent icon correction restores the HTML preview's Material Symbols Outlined at weight 200 for Home, Library, Apps, Favorites, Settings and Search; status symbols use the preview's fill variants. Native vectors preserve the official paths, with sources recorded in [Material Symbols assets](references/material-symbols/README.md). Android font scaling remains in effect. Preserve 48dp touch allocations independently of smaller visible controls. Filters use compact dock-style pills. Do not introduce fixed text/artwork minimums that enlarge the whole layout on a dense display.

The subsequent small-text correction applies a tapered increase after reference scaling: 3sp becomes 6sp, 9sp becomes 11sp, and the increase reaches zero at 14sp. Intermediate sizes interpolate between those anchors. Line height follows the resulting font-size ratio, then Android applies the user's font scale normally. This targets dense-screen badges, subtitles, filter labels, footer hints and Settings text. The status row wraps its text height to avoid clipping; card/artwork geometry and the lighter icon assets retain their existing sizes.

Library restores the reference filter strip: counted compact pills, L2/R2 hints, and More with the outlined expand symbol. Show at most six primary filters; More opens the existing accessible modal for every populated console. A selected overflow console replaces the last primary pill so the active filter stays visible. Reference consoles lead the order when populated; remaining consoles follow alphabetically. Counts use the same available-game rules as the grid. Inactive filter labels and borders use the reference's muted treatment.

Every card has an unfocused perimeter outline; focused cards use amber and a short lift/press transition, disabled under reduced motion. Collection captions sit outside square artwork. Console captions use abbreviations such as GBA, SNES and PSX, with stable console-specific tag/caption colors. The written abbreviation remains the identity; color supplements it. Navigation into or reselecting a populated page activates its selected surviving card, falling back to its first card. Settings and empty Search focus a meaningful setting or the query. Touch activation selects the touched card before launch, and scrolling does not activate cards.

Library contains ROMs and Android games. Apps contains other Android apps, including emulators. Favorites includes favorited games and apps. Android's declared game classification and recognized emulator packages supply defaults; user category changes take precedence for Android apps. Blank Search shows a prompt, and a query with no matches stays empty. System search results use the same card component and dimensions as catalog results.

Home promotes one most recently opened available item, then shows Android games, ROMs, and remaining apps alphabetically within each group. Its lazy carousel retains the full available catalog. Search collapses its heading/query/filter panel when scrolling results down, retaining an Edit search control; upward scrolling or the Search shortcut reveals editing. Confirm applies the edit, hides the IME and focuses results; Back cancels the edit and restores its entry query. Default legends are A/B and follow the user's Confirm/Back mapping. Hardware keyboard text/caret behavior remains native.

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
| Dock/pill radius | Fully rounded |

Keep subtle lower-edge depth and restrained shadows. Reserve focus space before layout so outlines and lifts are not clipped by lazy collections. The source transition is about 200ms; indicate focus immediately and animate only the decorative movement. Reduce or remove motion when the system requests it. No continuous idle animations are required.

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

There is no production `RunningBadge` or suspended-game state in this scope. Recency is ordering data, never a visual claim about another application's process.

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
| Home | Platform/title metadata above a horizontal cover carousel | Recent item focused; no apps; long title; missing art; app removed; restored return |
| Library | Heading/count + sort + platform strip + square-cover grid | All/matching platform/empty platform; six columns as a target when readable, otherwise adapt; A Play/Open, Y Details |
| Apps | Heading/count + category strip + icon-led grid | All, populated Emulators/Other filters; native icons; long names; no apps/icon unavailable |
| Favorites | Library collection layout with All/Games/Apps filters | Mixed favorites; removal keeps nearest focus; empty state links to Library |
| Search | Query + All/Games/Apps/System categories + compact result cards | Empty-query prompt, matches, no results, restricted filters, IME visible; system cards match catalog-card dimensions |
| Settings | Category list and grouped setting rows | Appearance/readability, controller mapping, default-HOME setup; ROM/emulator/provider settings arrive with those stages |
| Item details | Shared heading/artwork + full title/platform + supported actions | Open/Play, favorite, Android app info where available; later emulator choice and artwork correction |

Library includes ROMs and Android games. Apps shows the remaining Android applications, including emulators, from the same catalog. Installed Android game classification uses declared metadata with a user override; unknown apps remain in Apps until classified. Library filters are All, Android when native games exist, and console abbreviations derived from available ROMs.

Favorites stores references, not duplicate catalog objects. Removing a favorite does not uninstall an app or delete a file. Empty states must not make scanning or scraping the normal maintenance workflow.

Search is a dock destination, not an in-game overlay. It searches only locally indexed content. In All results, an Android game appears once in Games rather than being duplicated under Apps. Category changes retain the query; platform filters appear only when relevant. Reuse `SearchResultCard` in a two-column layout when space permits, and one column when the IME or width makes two columns unreadable.

Item details is a launcher content route with origin restoration, using the same shell. It is optional for launching. Do not import the PRD's full-screen analytics/save-management hub.

## 7. Focus, keyboard, and restoration

Compose owns focus requesters, focus groups, pressed interactions, scroll controllers, and IME handles. ViewModels own selected IDs and query/filter state. No control stores navigation history or decides most-recently-opened ordering.

Define explicit transitions between header controls, filters, content, and dock. For Library, Up from the first content row reaches filters, then header controls. Down from the last content row can reach the dock; intermediate rows scroll naturally. Home Up can reach any meaningful metadata action if one exists; otherwise it remains in the carousel. The dock is reachable without adding focus to decorative labels.

Dialog focus is contained until dismissal. The system keyboard handles editing while visible; Back dismisses it before leaving Search. Touch selection followed by controller input restores a valid visible focus target. Shell shortcuts are disabled or delegated where the active modal/editor consumes them.

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
