# Design system

PockyDeck uses a shared Compose design system. Keep new screens consistent with the existing shell and controls; avoid replacing them with unrelated stock page styling.

The source of truth is `LauncherTheme`, `ShellMetrics`, and the shared components in [core/designsystem](../core/designsystem/src/main/kotlin/dev/handheld/launcher/core/designsystem/). Historical design previews remain in Git history.

## Visual language

- A dark violet-to-charcoal backdrop, quiet dark surfaces, light text, and a warm amber controller-focus frame.
- Plus Jakarta Sans is bundled at regular, medium, semibold, bold, and extra-bold weights. Clock/status text uses regular weight.
- Material Symbols Outlined uses the official light paths, generally weight 200. Reuse [the checked-in glyphs and provenance](references/material-symbols/README.md); do not redraw or thicken them.
- Console labels use short names and consistent console-specific colors.
- Touch interaction suppresses controller focus decoration; controller input restores a visible selected item.

| Role | Current default |
| --- | --- |
| Background top / middle / bottom | `#313042` / `#282736` / `#232230` |
| Card / app / artwork surfaces | `#252831` / `#23262F` / `#1C1E24` |
| Dock / control surfaces | `#1E2229` / `#21232A` |
| Primary / secondary text | `#F4F5F8` / `#A6ACB8` |
| Controller focus / lower edge | `#E5A01A` / `#996409` |
| Confirm / cancel | `#3EC07D` / `#E05244` |

Use semantic theme roles instead of repeating hex values at call sites.

## Sizing and text

`ShellMetrics` converts the original reference scale into the available native window. At the Flip 2's 1920×1080 / 360dpi baseline, the logical window is approximately 853×480dp. Reference units are not raw dp or final sp values.

The theme applies its small-text readability curve and shared multipliers centrally. UI scale offers 90/100/110/120%; Android font scale remains independent. Grid card size offers 70–140% without shrinking text, and is separate from Home and Search sizing.

Interactive controls retain at least 48dp touch allocations where possible, even when their painted surface is smaller. Hit regions must not overlap. Current text actions and round icon actions paint at 36dp inside their touch allocation. Reuse the components instead of applying another scale factor in a screen.

Keep titles bounded and ellipsized with full text available in Details. Do not shrink arbitrary long titles to fit. Counts, loading states, and unknown status readings must not shift shell anchors.

## Shell and collections

Status, content, destination dock, and footer share one persistent shell. Page transitions are short and respect Reduce motion. Footer hint changes should not blink focus onto the Home button.

Home uses a finite horizontal gallery with up to 20 items. Returning Home resets it to the first item. Non-selected cards retain their quiet outline; controller focus has a stable allocation and visual lift without resizing the row.

Library, Apps, and Favorites support Grid and List. Grid titles have aligned bounded caption regions and category tags on the artwork. List mode uses a left list with a right selected-item preview. Preview actions are small round Open, Details, and Favorite controls, with full touch targets.

Artwork requests are limited to visible cards and decoded near their physical size. Detached cards release image references; backgrounding cancels image work and clears decoded caches. Fast scrolling suppresses decorative cover transitions.

## Filters and actions

Place the collection filter row beside the page title. Fixed All and All filters controls sit beside a distinct finite strip of three complete populated categories where space permits. Backfill the final window so it does not shrink to a single last category.

L2/R2 are compact, darker hints with breathing room beside the category strip. A trigger press changes one category; a sustained hold begins repeat after its delay. D-pad movement only visits visible filter controls, then leaves the strip instead of scrolling it.

All filters opens a grid chooser. Sorting uses the same surface, typography, focus treatment, and Close action as other modals.

Collapsed Search shows the current query with a search icon, a round × Clear action, and an aligned result count. Long queries truncate while leaving Clear and the count visible. The query retains accessible Edit search semantics.

## Input and focus

Use semantic actions shared by controller input and footer hints. Keep trigger hysteresis/de-duplication, initial repeat delay, and acceleration centralized. Do not queue repeated sounds or block navigation behind sound playback.

Restore focus by stable item ID after the item is available. Modal dismissal returns to the underlying page. Search and Settings support Back to Home; ordinary collection pages retain their destination. Directional movement in grids crosses row boundaries without wrapping the full collection.

Every icon-only action needs a meaningful accessibility description. Decorative icons do not create extra focus stops.

## Status treatment

Battery is green when charging, red at 10% or below, yellow below 15%, and neutral otherwise. Battery temperature uses cool/neutral/hot icon and tint states; it must not be labeled CPU temperature. RAM has a distinct tint; internal storage is cyan and external storage amber.

Wi-Fi is icon-only. Bluetooth and notification presence appear only when readings are available and enabled/present. Running-process badges appear only on Home and distinguish an observed emulator process from a specific active ROM.

## Verification

Use the debug component galleries for isolated controls and the actual launcher for interaction checks. Inspect 100/110/120% UI scale, larger Android font scale, long titles, empty states, reduced motion, modal return, and touch/controller switching. See [development](development.md) for test commands and accelerated-input checks.
