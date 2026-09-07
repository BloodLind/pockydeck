# US-006 accessible visual primitives

Accepted by the coordinator on 8 September 2026. Initial Luna implementation was completed through coordinator integration; independent Terra review found no remaining source issue after the minimum-target modifier order was corrected.

## Implemented behavior

`LauncherText`, `LauncherIcon`, `LauncherSurface` and `FocusFrame` are state-driven primitives with no product-module, repository or sensor dependency. Native text inherits a readable foreground from its enclosing surface. Selection uses a light surface and dark content; unavailable/disabled content uses a readable secondary foreground. Actual focus, selected, pressed, enabled and unavailable states are independent. Only disabled controls publish disabled semantics; an unavailable item may still support an enabled details action.

The caller owns actual focus and activation. Focus publishes one immediate amber outline, while stable frame padding and lift slack keep drawing and square content inside measured bounds. Reduced motion suppresses decorative lift without delaying or removing the outline. `LauncherSurface` applies its 48dp default minimum before a caller's preferred size; parent constraints still need enough room. Null icon descriptions clear only internal decoration semantics, leaving the containing control accessible. Meaningful glyphs expose one label with no duplicate symbol announcement.

Six original native vector glyphs cover Home, Library, Apps, Favorites, Settings and Search. Separate physical A/B/X/Y/Start legends let consumers apply the persisted Confirm/Back mapping; no action is hardcoded to a face button. Callers may supply localized content/state descriptions.

The full API is published in [shared contracts](../../contracts.md). `LauncherPrimitivesPreviewContent(reducedMotion)` requests real focus and renders long text, an unavailable value, selected/focused states, a static pressed sample and physical legends. The app's preview Activity is debug-only. These are primitive evidence fixtures; product controls, navigation and sensor reading remain later stories.

## Acceptance evidence

| Criterion | Actual evidence | Result |
| --- | --- | --- |
| AC-01 | Two Android Compose tests acquire and move real focus, assert focus independently of selection, sample amber pixels after one frame, compare content/allocation bounds, and verify reduced motion preserves the frame without lift | Passed |
| AC-02 | Android semantics test verifies a decorative child leaves its parent's label/click action visible, introduces no focus target, and meaningful vector/face glyphs announce a single description without raw text | Passed |
| AC-03 | Android test lays out 1.3-font text over multiple lines with no overflow; explicitly requested 24dp and 48dp adjacent surfaces measure at least 48dp without overlap; disabled/unavailable semantics remain explicit | Passed |
| AC-04 | Native debug Activity renders the shared primitives without product imports; default and 1.3-font/reduced-motion screenshots inspected by coordinator; calibration limitation below | Passed |

Initial focused run after source integration: `:core:designsystem:connectedDebugAndroidTest :app:assembleDebug` passed in 32s, 105 tasks. All four Android tests passed. The final combined run repeated them after the reviewed 48dp modifier-order correction and passed again, alongside all nine metrics tests and the storage/application tests. See [combined gate](F02-F06-final-gate.md) for exact command and totals.

## Native visual evidence

- [Default font scale and actual focused Search sample](US-006-standard.png)
- [System font scale 1.3 with reduced motion enabled](US-006-large-reduced-motion.png)

Both rendered on the isolated Android 13 / API 33 emulator at 1920×1080, 240dpi. The coordinator inspected the text wrapping, selected foreground contrast, visible focus border, vector glyphs and distinct pressed/unavailable treatments. UI-idle synchronization preceded each capture. Cold launches returned `Status: ok`, with no AndroidRuntime errors. Font scale was restored to 1.0 and the animator override removed before stopping the emulator.

Physical Retroid Pocket Flip 2 density, display, controller and lid calibration remain pending. No emulator screenshot is claimed as physical-device acceptance.
