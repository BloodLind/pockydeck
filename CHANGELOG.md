# Changelog

## 0.10.4 — 2026-09-25

- Reduce ordered image-loading delay by advancing waiting workers directly, removing the extra per-decode timer, and displaying ready ROM artwork while request bookkeeping continues. Reveal each cover as it becomes available in display order; retain single-cover decoding, instant cache hits, full Home preloading and scroll/lifecycle protections.

- Remove the dock button's separate press fill so an incoming tab cannot flash ahead of the moving white selection. Preserve the droplet/tail animation, icon contrast, controller focus, click feedback and reduced motion.

- Isolate the visible-cover queue from collection page composition and read card-focus animation during placement/drawing. Add a viewport recomposition regression check and a repeatable on-device frame measurement script.

- Smooth Apps scrolling by preloading small catalogs into the existing icon cache, retaining nearby icons, and removing per-card subcomposition and unused artwork animation layers. Large app catalogs use a bounded prefetch window; backgrounding cancels warming.

- Move the white navigation selection like a water droplet: a 160 ms ease-in-out leading circle with a short tapered tail that settles by 224 ms. Keep icons and targets stationary, match icon contrast to the entire droplet, redirect interrupted motion, and switch instantly with Reduce motion.

- Replace the cycling background-color button with a full hue/saturation/brightness palette and five preset swatches. Preview and save custom colors, support touch and D-pad adjustment with Back to finish, and keep the latest color during rapid dragging. Preserve the dark background finish, tint strength and grain controls.

- Hide the catalog position rail when every row is on screen, including small scroll ranges caused by padding or slightly clipped edge rows.

- Load fresh artwork in display order, with a quick left-to-right or row-by-row reveal. Preserve instant cache hits and full Home preloading; skip missing covers, bound slow-provider waits, and restart ordering after cache reclamation or viewport changes.

- Lighten all six navigation icons with finer rounded outlines and no interior wash. Simplify Search to a clean lens and a slim handle.

- Add a small scaled gap between navigation dock buttons while retaining their existing size and touch targets.

- Remove the enclosing list-preview card and cover frame. Enlarge its platform/format tags, show the configured emulator instead of repeated platform facts, and use a filled favorite star or an outline. Move the catalog position rail to the left edge and slightly dim the optional list artwork background.

- Restore the original rounded navigation ticks and pops byte-for-byte. Retain fixed peak/RMS headroom and play navigation repeats about 7 dB quieter than the first click, without progressive gain changes; preserve volume, mute, and first/last haptics. Keep the restored 48 ms selection sound in step with 55 ms held navigation.

- Give the navigation dock original rounded console-style icons, including a game cartridge for Library, soft app tiles and star, a rounded gear and a simple search lens. Preserve button sizes, spacing, selection contrast and accessible destination labels.

- Add a mapped Back/Controls shortcut from any Library, Apps or Favorites grid/list item to the filter and sort controls. Return with Down or Back to the same selected item and scroll position; cancel pending scrolling so it cannot steal header focus.

- Give the navigation dock inner padding above, below and around its end buttons. Preserve button/icon sizes and center the taller capsule within a slightly larger dock band so it remains clear of page content and footer actions.

- Add Purple, Graphite, Blue, Green, and Warm background colors with saved tint-strength and grain-intensity controls in Display settings. Default to a subtle purple-gray at 40% tint and 30% grain. Smooth the stationary matte texture with a cached periodic filter; 0% grain removes it entirely.

- Add a slim, read-only catalog-position rail to lists and grids; it follows the viewport through acceleration and filtering without taking controller focus.

- Replace the wide SELECT legend with a compact circular overlapping-panel symbol matching the face-button hints. Retain the adjacent action label, accessible Select description, and full footer touch target.


- Improve footer readability with larger semibold action text and clearer button legends. Replace the full-width footer divider with a content-sized pill while retaining full touch targets, and increase navigation button/icon artwork by about 10%. Change the default background to charcoal gray with a cached, stationary matte-plastic grain.

- Restore Home's established title and card-row alignment independently of the compact shell, keeping the tighter layout on library and settings pages.

- Tighten the shell around its content instead of reserving screen-height percentages for chrome. Bring pages closer to the status strip, reduce dock/footer spacing, shrink Start/Select legends and footer labels, and retain 48 dp touch targets. Use native Android sans-serif typography with tabular status numerals and group available storage behind distinct internal-drive and SD-card symbols.

- Make library list rows more compact with smaller artwork and single-line titles. Add an independent, default-off List artwork background option using the same cached blur and quick reveal as Home. Remove the redundant Start/Menu footer hint while retaining Select/Details and the controller Menu shortcut.

- Anchor ROM-folder settings to stable row identities so scan progress, warnings, unidentified-game controls, and newly discovered folders do not move the row being viewed. Apply the same behavior to compact settings.

- Make the optional Home backdrop appear sooner by removing its second navigation delay, reusing decoded cover pixels, and processing small blurs independently of the cover queue. Add a 120 ms entrance/crossfade that preserves the previous image while loading and continues when navigation resumes.

- Make navigation/footer press feedback immediate on touch-down with a short 50 ms release, avoiding a lingering highlight during quick taps.

- Replace square tap ripples in the navigation dock and action footer with circular/rounded press fills matching the launcher controls, preserving touch targets and controller focus.

- Preload the full Home row of up to 20 items before they enter view, even while scrolling; show cached covers immediately and retain bounded image caches across normal app switches. Cap Home covers at 512 px so all twenty fit within 20 MiB. Load installed-app icons directly through Android without the cover-art idle delay, and invalidate them on package changes.

- Vibrate only at the beginning and end of a repeated input burst. Single actions get one pulse; held navigation, trigger reports and rapid taps no longer buzz on every step. Cancel pending ending pulses on disable, keyboard entry or leaving the app.


- Prevent very fast taps after controller input from launching list rows; preserve the touched row when Y returns to controller input. Keep grid artwork and captions inside the visible content at enlarged UI scales.
- Make list mode a PSP-inspired library with selection confined to the left list and read-only cover art/game information on the right. Move quick actions to Y (favorite), Select (details/extra actions), and the touch footer.

- Add an optional Home artwork background in Display settings, off by default. A small pre-blurred image follows the selected ROM after scrolling settles, with shading for readable text and a standard-background fallback for apps or missing artwork.
- Defer uncached covers in large collections during touch scrolling and held navigation, keep displayed artwork, and resume after 180 ms of rest. Pace decoding and keep file metadata reads off the UI thread.
- Count navigation work toward accelerated repeat timing without queuing overdue input; reveal fresh covers with a short, eased cartridge-like slide/fade that respects Reduce motion.
- Make Home launcher settings open Android's launcher chooser when PockyDeck is already default.
- Preserve the touched control when switching to controller input; sliders use Confirm to adjust and Back to finish, with D-pad hints.
- Replace feedback with original Switch-inspired ticks and pops, balanced across cues; retain per-launcher volume and independent vibration controls.
- Apply mute/volume reductions to the active voice without boosting or replaying its tail. Move native playback off the UI thread, drop stale/busy cues, and guard against duplicate clicks after a slow audio-driver call.
- Show RetroArch core settings only for consoles using RetroArch and identify each console's core.
- Replace Shizuku/process badges with a persistent blue dot per ROM console on Home, with a matching Last played on [platform] tag above the focused/hovered title.
- Recreate boot-path emulator tasks when launching a selected ROM, addressing PS2 emulators reopening the previous game.
- Fill grid cells with artwork, keep consistent spacing, and replace scale choices with touch/controller sliders.
- Share focus/content motion, fade clear artwork in, and skip memory-hit/loading-spinner animations.
- Apply the PockyDeck Android label and reference-inspired white pd monogram with blue/red centers on dark navy; support adaptive and themed icons while retaining the existing package ID.
- Simplify setup documentation and record release verification in [stabilization notes](docs/releases/next.md).

## 0.10.3 — 2026-09-09

First public preview. Includes the Android launcher foundation and the following capabilities developed before the first tagged release:

- Home, Library, Apps, Favorites, Search, Settings, and item details.
- Android application discovery, favorites, successful-launch recency, and optional default-Home setup.
- Populated console-folder discovery, recognized ROM formats, descriptor/disc grouping, emulator preferences, RetroArch core selection, and bounded archive preparation.
- ES-DE metadata/artwork reuse, optional Libretro cover lookup, and GameNative/compatible GameHub Lite frontend exports.
- Controller acceleration and trigger de-duplication, A/B mapping, contextual hints, and soft synthesized sound effects.
- Grid and split-pane list collections, UI/card scaling, reduced motion, compact console filters, and artwork lifetime improvements.
- Device status and optional Shizuku process indicators on Home.

### Latest refinements

- Reduced visible action-button size while preserving 48dp touch targets.
- Replaced the collapsed Search controls with a query action, round Clear icon, and aligned result count.
- Kept three complete visible console categories where space permits, including the end of the filter strip.
- Grouped round list preview actions and refined controller audio during accelerated movement.

### Public repository

- Added Apache 2.0 licensing, attribution, contributor and security guidance, issue/PR templates, and a build workflow.
- Consolidated historical planning/evidence into current setup, development, compatibility, and design docs.
- Replaced third-party debug cover samples with original geometric vectors.
- Published a non-debuggable preview APK signed with the existing development certificate, with SHA-256 checksums.

See [release notes and verification](docs/releases/0.10.3.md) for limitations and packaging details. Earlier development checkpoints remain in Git history; there were no earlier GitHub releases.
