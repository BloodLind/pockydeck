# US-010 production control gallery

Accepted 8 September 2026. All fixtures and artwork are debug-only; the release manifest contains only the ordinary MainActivity. The gallery uses production controls and contains no repositories, domain models, network images or duplicate shell.

Entry: `dev.handheld.launcher/.debug.ControlGalleryActivity`. String extra `section` selects `controls`, `cards`, `layouts`, `settings`, `modal`, or the content-only `home` comparison. `compact=true` constrains the native fixture to 600×340dp. Home string extra `variant` supports `long`, `failed`, and `reordered`.

The Activity computes one root metrics snapshot and applies the calibrated theme. `HomeGalleryFixture(metrics, modifier, variant)` is placed inside `metrics.contentBounds`. It subtracts root offsets for child anchors and lets the trailing carousel extend to the screen edge. Its square allocations consume the shared frame/lift reservations; visible frames align with the metadata gutter. Activation reorders a stable-ID item and returns focus to that same identity.

Inspected native API-33 captures at 1920×1080, 360dpi:

- [Home content](US-010-home.png): reference platform/title placement, cover size and pitch, amber frame/lower edge, and partial trailing native-app tile. Status/dock/footer are deliberately supplied by F04 later.
- [Controls](US-010-controls.png): a focused search icon, separately selected filter, pressed specimen, disabled and unavailable actions, native query input, physical face-button circles and shoulder legends.
- [Cards](US-010-cards.png): loading placeholder inside fixed bounds, square collection art with reserved captions, round/missing native icons, and horizontal search result. The change-state button cycles loading → failed → loaded content in that same tile.
- [Layouts](US-010-layouts.png) and [settings](US-010-settings.png): long labels/counts, surviving cached content next to a recovery notice, scrollable content, a real switch, bounded choice value, and disabled action. The lower recovery action is reached by scrolling; the page does not shrink to fit one screenshot.
- [Modal](US-010-modal.png): the first enabled action has actual focus, background controls are dimmed/blocked, and Close is visible.
- [Long title](US-010-home-long.png): two native title lines fit without moving the card row. Native line rounding required an additional metadata reservation; the final shared metrics JVM checks passed.
- [Failed artwork](US-010-home-failed.png): unavailable art retains the allocated square and focused frame.
- [Reordered Home](US-010-home-reordered.png): starting from the normal fixture, emulator D-pad Right then Enter moved Dead Cells to the front with the same item focused. This is a debug UI reorder, not an external launch/recency claim.
- [Compact native window](US-010-compact.png): geometry uses the smaller root bounds; the surrounding gray area is outside the fixture viewport.
- [Native Android IME](US-010-ime.png): touch focused the query, `zelda` was typed, and Android reported `mInputShown=true` and `mIsInputViewShown=true`. The query remains visible while the scrollable gallery adapts. The outer query-frame bottom is partly obscured; F04 owns the complete shell/insets policy.

The reference typography calibration adds source `tracking-tight` to the Home title and `tracking-wider` to the platform badge, with native-scaled letter spacing. Source artwork and hashes are recorded in [artwork provenance](US-010-artwork.md). The user's original design-source file is unchanged.

Separate Terra/medium review found no remaining source/API issue. Build, JVM, native UI/data, and lint evidence is recorded in the [F03/F07 gate](F03-F07-final-gate.md). These captures do not close physical controller, HOME-role, or complete shell acceptance.
