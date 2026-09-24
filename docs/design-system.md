# Design system

PockyDeck uses a shared Compose design system. Keep new screens consistent with the existing shell and controls; avoid replacing them with unrelated stock page styling.

The source of truth is `LauncherTheme`, `ShellMetrics`, and the shared components in [core/designsystem](../core/designsystem/src/main/kotlin/dev/handheld/launcher/core/designsystem/). Historical design previews remain in Git history.

## Visual language

- The application icon uses a white pd monogram with blue/red centers on dark navy, following the supplied visual reference. Keep its native adaptive foreground inside the mask-safe area and its background full bleed. The same foreground alpha supplies Android's themed icon; [the editable SVG](branding/pockydeck-icon.svg) provides the rounded-square presentation.
- A purple-gray backdrop with soft matte-plastic grain, quiet dark surfaces, light text, and a warm amber controller-focus frame. Display settings offer Purple, Graphite, Blue, Green and Warm pigments, plus 0–100% tint strength and grain intensity. Defaults are Purple/40%/30%; Graphite or 0% tint restores neutral charcoal, and 0% grain bypasses the texture. A periodic 3×3 filter softens the stationary noise once. Cache at most ten 64 KiB intensity tiles and remember the shell brush; never regenerate noise or load a texture while navigating. Color changes affect the standard background, with the optional artwork layers remaining independent.
- Use Android's native sans-serif consistently for titles, controls and metadata. Clock/status values use medium weight and tabular numerals; keep secondary details quieter than titles. Footer labels use a 14-unit semibold role with the small-control readability multiplier; status text remains compact.
- Material Symbols Outlined uses the official light paths, generally weight 200. Reuse [the checked-in glyphs and provenance](references/material-symbols/README.md); do not redraw or thicken them.
- Console labels use short names and consistent console-specific colors.
- Touch interaction suppresses controller focus decoration; controller input restores a visible selected item.
- Navigation dock presses tint only the circular button; footer actions use a rounded pill press fill. Press feedback appears immediately and clears in 50 ms (half the general control press duration). Reduce motion removes the release animation. No default rectangular ripples cover their touch targets.

| Role | Current default |
| --- | --- |
| Background top / middle / bottom | `#35373B` / `#2B2D31` / `#222429` |
| Card / app / artwork surfaces | `#252831` / `#23262F` / `#1C1E24` |
| Dock / control surfaces | `#1E2229` / `#21232A` |
| Primary / secondary text | `#F4F5F8` / `#A6ACB8` |
| Controller focus / lower edge | `#E5A01A` / `#996409` |
| Confirm / cancel | `#3EC07D` / `#E05244` |

Use semantic theme roles instead of repeating hex values at call sites.

Settings use a keyed lazy list. ROM folders expose separate, stable rows for each source's status, notices, console choice and actions, so scan updates above the viewport preserve its first visible row and offset. Use the same row identities in compact All settings. Status changes must not reset scroll state or request initial focus; deliberate section activation may do so.

## Sizing and text

`ShellMetrics` converts the original reference scale into the available native window. At the Flip 2's 1920×1080 / 360dpi baseline, the logical window is approximately 853×480dp. Reference units are not raw dp or final sp values.

The theme applies its small-text readability curve and shared multipliers centrally. Controller-aware sliders provide UI scale at 90/100/110/120%; Android font scale remains independent. Grid card size offers 70–140% without shrinking text, and is separate from Home and Search sizing. Column count follows available width and target card size; artwork fills the cell with consistent spacing. Sliders support touch drag. With a controller, Confirm enters adjustment, D-pad Left/Right adjusts with held repeats, and Back or Confirm finishes. The footer uses a horizontal D-pad icon while adjusting. Up/Down always allows leaving the control. Touch-to-controller changes restore the actual touched control before any page fallback.

Interactive controls retain at least 48dp touch allocations where possible, even when their painted surface is smaller. Hit regions must not overlap. Current text actions and round icon actions paint at 36dp inside their touch allocation. Reuse the components instead of applying another scale factor in a screen.

Keep titles bounded and ellipsized with full text available in Details. Do not shrink arbitrary long titles to fit. Counts, loading states, and unknown status readings must not shift shell anchors.

## Shell and collections

Status, content, destination dock, and footer share one persistent shell. Page transitions are short and respect Reduce motion. Footer hint changes should not blink focus onto the Home button.

Size chrome from its contents rather than screen-height percentages. At the baseline Flip2 scale the status strip is 32 dp, dock band 60 dp and footer 48 dp; collection/settings pages receive the recovered space. Home retains its established composition: metadata begins at 14.9% of window height and the card row at 35.6%, with extra room for longer text when required. Compact windows and larger system text use the available space safely. Start uses smaller measured text and tighter padding. Select uses an overlapping-panel symbol in a circle matching the face-button hints, with an accessible Select description; footer glyphs and labels are compact while all action touch targets remain at least 48 dp.

Footer actions sit in a content-sized rounded band with no full-width divider. The painted band follows the label line height; its invisible 48 dp targets stay separate from the dock. The compact Select symbol retains the adjacent readable action label. Navigation circles use 58 reference units and their symbols 31.5, preserving the established Home title/card placement. The dock capsule has 6 reference units of vertical padding and 8 at each end; its band reserves 3 more units above and below. The circles and L1/R1 hints remain centered together.

Home uses a finite horizontal gallery with up to 20 items. Returning Home resets it to the first item. Non-selected cards retain their quiet outline; controller focus has a stable allocation and visual lift without resizing the row.

Library, Apps, and Favorites support Grid and List. Grid titles have aligned bounded caption regions and category tags on the artwork. List mode uses a left list with a right selected-item preview. The right pane is a rounded, subtly platform-tinted showcase with framed cover art, title, availability, favorite status, and platform/format facts. Its depth and color use native drawing and reuse the existing artwork loader; it adds no image request or forced blur. The pane has no action or controller focus targets. List selection stays on the left, including on Left/Right input. Confirm launches the row, Y toggles favorite and Select opens item details/extra actions. Touch selects a row and publishes the same actions in the footer; the compact preview also remains read-only.

A slim position rail sits beside the catalog in both layouts, between the list and its preview or at the grid's right edge. It follows viewport position rather than selection, reads scroll offsets in drawing, and adds no controller or touch action. The thumb estimates visible rows with a minimum visible size and reaches exact native scroll boundaries, including partial final grid rows. Hide it when the filtered catalog fits; retain its narrow lane to avoid a layout jump. Accessibility exposes the position and visible item range.

Collection list rows use 56 reference-dp artwork, a single-line ellipsized title and optional subtitle, retaining at least a 48 dp touch target. Search results keep their larger layout. The preview supplies the full title. Omit the redundant Start/Menu hint from the action footer; the physical Start shortcut remains available.

Artwork is decoded near its physical display size. Power-of-two sampling is followed by resizing to the requested bucket, avoiding oversized retained bitmaps. Cover and Android icon caches are bounded to 32 MiB and 8 MiB respectively. Detached cards release their painters; backgrounding cancels image work and releases painters while preserving cached bitmaps for return Home. Actual Android memory pressure trims the caches; UI-hidden alone does not. Fast scrolling suppresses decorative cover transitions, and memory-cache hits skip the reveal. Do not add per-card spinners or blurred loading artwork. Controls share a 160ms focus lift; content transitions use the same motion tokens and Reduce motion removes decorative animation.

## Filters and actions

Place the collection filter row beside the page title. Fixed All and All filters controls sit beside a distinct finite strip of three complete populated categories where space permits. Backfill the final window so it does not shrink to a single last category.

L2/R2 are compact, darker hints with breathing room beside the category strip. A trigger press changes one category; a sustained hold begins repeat after its delay. D-pad movement only visits visible filter controls, then leaves the strip instead of scrolling it.

All filters opens a grid chooser. Sorting uses the same surface, typography, focus treatment, and Close action as other modals.

From any grid/list item, the mapped Back button focuses All filters (or All if the chooser is absent). Show the contextual Controls footer hint. Cancel pending grid movement before focusing the header so it cannot reclaim focus. Left/Right traverses header controls; Down or Back restores the selected item without resetting the viewport. The header shows Back, while slider adjustment retains Done. This uses the shared collection behavior in Library, Apps and Favorites; modal dismissal and Search/Settings Back behavior remain unchanged.

Collapsed Search shows the current query with a search icon, a round × Clear action, and an aligned result count. Long queries truncate while leaving Clear and the count visible. The query retains accessible Edit search semantics.

## Input and focus

Prioritize navigation over uncached covers in large collections: pause fresh requests/decodes during movement, retain displayed painters, and resume after 180 ms without scrolling or navigation. Cache hits are available during the first composition even while the gate is closed. Home preloads its entire row (at most 20 items), including offscreen cards, without waiting for scrolling to stop. Preloads and displayed Home cards use the same display bucket, capped at 512 px: all twenty square covers occupy at most 20 MiB of the shared 32 MiB cache. Installed-app icons use PackageManager on IO with an independent cache, no cover idle gate, and package-change invalidation. Decode one cover at a time with a short cancellable gap, prepare its texture off the UI thread, and keep file metadata reads on IO. Fresh covers use a 160 ms eased downward slide (4.5% of card height), slight scale-in, and fade. Animation values are read only by draw layers; cached covers and Reduce motion skip the reveal.

Held input accelerates from 115 ms to 55 ms repeat intervals over 2.5 seconds after the initial hold delay. Count dispatch time toward the interval; yield at least one frame after slow work and never catch up by replaying overdue steps. Accelerated navigation retains one conflated scroll/focus destination and skips per-row decorative scroll animations.

Use semantic actions shared by controller input and footer hints. Keep trigger hysteresis/de-duplication, initial repeat delay, and acceleration centralized. Do not queue repeated sounds or block navigation behind sound playback. The six cue roles use original matched-RMS samples, at least 14 dB of peak headroom, and silent tapered endpoints. Movement and card selection share an identical 18 ms filtered-noise click without a resonator or pitch sweep; their 40 ms guard fits the full 55 ms accelerated input rate. Other actions retain a minimum 80 ms guard. Every cue also reserves its duration plus 16 ms. One voice owns playback on a dedicated audio worker. Accept at most one pending cue, discard requests older than 80 ms, and start the native repeat guard after the driver returns. Conflate volume updates while retaining the lowest pending level: reductions or mute received during a slow driver call must still apply even if the slider has already risen again. Increases apply only to a fresh cue; unmute never resumes a tail.

Restore focus by stable item ID after the item is available. Modal dismissal returns to the underlying page. Search and Settings support Back to Home; ordinary collection pages retain their destination. Directional movement in grids crosses row boundaries without wrapping the full collection.

Every icon-only action needs a meaningful accessibility description. Decorative icons do not create extra focus stops.

## Status treatment

Show available storage as one compact **Free** group. The original drive-outline glyph identifies internal storage and the SD-card glyph identifies removable storage; values do not repeat INT/EXT prefixes. Multiple external volumes retain a visible count and complete anonymous accessibility descriptions. Unknown/partial readings remain explicit, and low storage retains warning color.

Home and collection lists may independently opt into a selected-ROM artwork backdrop through Display settings. Both options default off; the list option applies to list layouts only, never grids, Search, Details or Settings. Use the same renderer and a separately cached, pre-blurred thumbnail with a maximum 128 px edge, drawn beneath all shell chrome with a contrast scrim. Reuse already-decoded covers and process these tiny blurs independently of the paced cover queue. The active page's navigation gate supplies the only settling delay. Retain the prior color wash until the next image is ready, then crossfade over 120 ms; the initial image also fades in. Further navigation cancels pending image work but does not snap or cancel an active reveal. Android apps and missing images return to the standard gradient. The backdrop has no focus/click target or accessibility announcement; Reduce motion removes the fade, and backgrounding releases its painters.

Battery is green when charging, red at 10% or below, yellow below 15%, and neutral otherwise. Battery temperature uses cool/neutral/hot icon and tint states; it must not be labeled CPU temperature. RAM has a distinct tint; internal storage is cyan and external storage amber.

Wi-Fi is icon-only. Bluetooth and notification presence appear only when readings are available and enabled/present. Home alone shows a blue dot with a dark outline based on successful dispatch history. Focus or pointer hover shows a matching blue Last played on [platform] tag above the title. Other pages omit this status; there are no running-process badges.

## Verification

Use the debug component galleries for isolated controls and the actual launcher for interaction checks. Inspect 100/110/120% UI scale, larger Android font scale, long titles, empty states, reduced motion, modal return, and touch/controller switching. See [development](development.md) for test commands and accelerated-input checks.

Feedback loudness is controlled at the audio worker as well as by the single-voice gate. Normalize matched-RMS cues by duration, bound their recent energy with a 200 ms decay and 5% burst headroom, and never raise gain inside a burst. Reset the burst after 600 ms without a new cue. Volume reductions multiply the current repeat gain; volume increases never boost an active tail. Dropped/failed starts spend no energy, and stop/mute cannot reset the burst ceiling to make the next rapid click louder.

Haptics have their own main-thread burst owner. A handled input or touch activation starts one pulse; asynchronous artwork/card focus callbacks do not add pulses. Resolved D-pad/stick/trigger holds keep the burst open through the initial repeat delay, with no intermediate vibrations. After release or the final rapid tap, 160 ms without another action ends the burst with one pulse only if there were repeated actions. A single action gets no extra trailing pulse. Disable, IME entry, pause and release cancel pending haptics; the sound toggle and volume are independent.
