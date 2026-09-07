# US-009 reusable layouts, settings and modal evidence

Accepted 8 September 2026 against the shared F03/F07 source state.

Production controls now provide caller-owned page headings/counts, scrolling filter strips, keyed collection grids, recovery/notice content, settings actions/choices/switches, and an in-tree dialog with a scrollable body and persistent Close action. They contain no app/domain imports, repositories, navigation, or physical input dispatcher.

The dialog backdrop is a separate pointer-consuming sibling behind its panel; panel gestures still reach buttons and scrolling. One focus group requests its first enabled descendant once, falling back to Close. Directional and sequential focus cannot leave the group. Updated lifecycle callbacks are observed without spurious show/dismiss cycles. F05 will bind these presentation hooks to origin restoration and the application input owner.

Validation:

- `:core:designsystem:connectedDebugAndroidTest :app:assembleDebug` — passed: 16 native design-system tests, zero failures/skips, and combined app build success.
- Six US-009 tests cover callback counts and disabled controls, actual touch and Enter activation, switch semantics independent of selected state, bounded long choice text, scrolling filters, stable-key state after reorder, modal initial/fallback focus, all four directions plus Next/Previous containment with exactly one focused node, backdrop isolation, panel touch activation, current lifecycle callbacks, and a 26-action scrolling dialog with Close still visible.
- The long-menu test initially exposed scroll semantics being merged into the larger panel. Giving the scroll viewport its own merging boundary fixed the actual accessibility scroll extent. The final test scrolls the last action fully into view while Close remains displayed.
- The dialog/settings classes were rerun directly (3/3 passed) to retain their native images before Gradle uninstalls the test APK.
- [Settings render](US-009-settings.png) shows the focused row, on switch, disabled action and bounded two-line choice. [Scrolled dialog-body render](US-009-dialog.png) shows the final actions within the native viewport; the test separately verifies visible Close and panel bounds. Complete shell/gallery compositions follow in US-010/F04.
- Separate Terra/medium source review found no P1 issue. The toggle callback now forwards the native requested value directly. No outstanding source finding remains.

This evidence is from the isolated API-33 emulator at the Flip 2's 360dpi density. It does not claim physical controller, IME, or complete Home visual acceptance.
