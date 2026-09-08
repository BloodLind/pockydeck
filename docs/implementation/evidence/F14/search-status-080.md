# Filter focus, Search, transitions and status — 0.8.0

Date: 8 September 2026. Baseline: `088e1a4`; branch: `codex/f01-foundation`. This batch implements the user's twelve follow-up requests, retaining the reference design and the existing scale preferences. Verification is combined after implementation and uses the physical Flip 2.

## Delivered behavior

- Ordinary collections retain their cards while a filter or sort is calculated. Selecting the active filter is idempotent. Result restoration uses stable keys, suppresses temporary header highlights and waits for settled results. A synchronous launch guard rejects stale cards even if Confirm follows a filter change before the next UI frame; native card focus targets remain attached.
- Library's title, filters and Sort share a centered row at least 48dp high, with the item count below. Below 560dp content width the filters occupy a second row. All stays fixed outside the finite strip. The strip reserves approximately 72 reference units, about one short category, while keeping natural chip widths and separate decorative L2/R2 hints. All filters still opens the larger grid chooser.
- D-pad/stick Left/Right traverse All, fully visible category chips, All filters and Sort, then reverse along that path. Clipped and offscreen chips are omitted; directional focus does not scroll categories. Up from the first card row enters the selected visible category or All, and Down returns to a settled card. L2/R2 retain explicit finite category selection, one step per press and a 650ms delay before accelerated holds.
- Collection gaps use the smaller shared `xs` spacing. Artwork is centered and capped at 208 reference units, with the existing five/four/three-column breakpoints. Available grid height further caps artwork after reserving two scaled title lines, contextual subtitle space and focus clearance. This keeps captions above the dock at larger scale. Home and Search-result geometry are unchanged.
- Footer content changes use a 120ms size/fade transition with one live action tree keyed by input. Enabled-state changes alone do not restart the fade. Covers crossfade over 140ms when loaded, and Android bitmap painters retain identity across unrelated recomposition. Reduce motion bypasses these transitions.
- Leaving Search clears its query, filter, selection, anchor and local editor/collapse state. Search-origin details preserve that session for Back; the Search shortcut returns from those details to the editor. Y Clear is available during editing and ordinary Search, resets the Cancel baseline and filter, and retains an already active keyboard. Cancel cannot resurrect cleared text.
- Search matches indexed ROMs, including title and console labels. Its memoization follows both raw editor text and the published query, allowing unchanged result lists to reappear after publication catches up. Prefix, full-title and trailing-space edits are covered with an existing device ROM. Empty filters remain empty and passive result restoration does not reopen the keyboard.
- Back closes the active editor or modal first. Dock-opened Search and Settings then return to Home; Home, Library, Apps and Favorites remain in place. Shortcut Search and details retain their explicit origins.
- Bluetooth appears only when the public radio-state reading is available and enabled, using the same official light Material Symbols family. It performs no scanning and changes no radio setting.
- A small dot beside the clock represents active notifications exposed by Android to the launcher. **Settings → Android → Notification indicator** opens Android's opt-in access screen. The service keeps only presence in memory, storing no keys, notification text or payloads. Denied, disconnected and unknown readings hide the dot. This is an active-notification indicator, not an unread-message count.

## Findings corrected during verification

The real-catalog Search test exposed a zero-result filter restoring the query field and reopening the IME. Passive focus now waits for settled results and targets a visible filter or the compact Edit search action. Explicit editing remains available. The same device assertion and two native compact/full-height fixtures cover the correction.

A 120% visual check exposed larger collection artwork pushing the second title line below the grid viewport. Cards now reserve actual scaled caption height before allocating artwork. The native scale test checks the first complete card against raw viewport bounds at both 120% and 100%, in addition to the existing native-window checks for dock/footer controls, and restores the original scale.

Review also found an immediate filter/Confirm race and a Search shortcut from Search-origin details that did not return to the editor. The synchronous criteria guard and explicit return handling correct them. The existing ViewModel test checks launch rejection immediately after changing criteria, before coroutine publication, and acceptance after results settle.

An initial local-JVM Search composition test could not run Android tracing. Its assertion moved to an actual Android Compose fixture, without enabling blanket mock return values. The final app-suite preparation also waits for the rendered Library grid, not only the repository snapshot: on the first process startup, the latter can precede the Activity's lifecycle collector. No feature assertion was removed.

## Verification

**363 tests passed, one skipped, zero failures/errors in the final applicable runs.** Debug/release APK builds, all JVM suites and lint passed. Lint reports zero errors and 74 warnings: 64 app, 2 data and 8 design system. One existing Windows symlink fixture is skipped.

| Suite | Passed | Skipped |
| --- | ---: | ---: |
| Domain JVM | 58 | 0 |
| App JVM | 89 | 0 |
| Data JVM | 92 | 1 |
| Design-system JVM | 12 | 0 |
| Data Android fixtures | 77 | 0 |
| Design-system Android fixtures | 22 | 0 |
| MainActivity real-catalog Android tests | 8 | 0 |
| Shell presentation Android tests | 2 | 0 |
| Search composition/focus Android tests | 3 | 0 |

All 13 app tests passed together in **73.902 seconds** against the delivered APK. They cover native touch/controller restoration, ROM search and Clear/reset, zero-result keyboard behavior, Back and modal origins, finite visible-filter traversal, trigger timing, grid row transitions and live 120%/100% scaling with complete captions.

The data suite includes a read-only radio-state check on Android 13. Shell fixtures verify both indicator visibility states and one actionable footer tree during transition; Search fixtures cover equal-list query publication and empty-result focus in normal and compact layouts. MainActivity tests use the real catalog and native Android input events without activating a game.

Build/result logs are in ignored `.local`: `verification-080-final.log`, `verification-080-refinement.log`, `device-080.log`, `design-device-080-final.log`, `device-harness-080-build.log` and `main-device-080-delivery.log`. Earlier `main-device-080.log` records the corrected Search failure; `main-device-080-final.log` records the first-render test-preparation failure. Main-app installation uses `adb install -r` and explicit instrumentation classes rather than the Gradle task that uninstalls the launcher. Core data/design test packages are isolated fixtures.

APK: `.local/releases/handheld-launcher-0.8.0-debug.apk`, version **0.8.0**, code **11**. SHA-256: `281a0463f772569fa6e5608a1bb6a807cc7f1cc982782df6e03faae7293521f1`.

The installed `base.apk` has that same SHA-256 and reports version 0.8.0/code 11. Final launcher-process AndroidRuntime error output is empty. All files access remains allowed, and the inspected Library contains 3,768 items. App UI scale is restored to 100%; system font scale remains 1.0.

Device: Retroid Pocket Flip 2, Android 13, serial `89a34d44`, 1920×1080, density 2.25. Final inspected captures in ignored `.local` are `launcher-080-library-final.png`, `launcher-080-apps-final.png`, `launcher-080-display-final.png`, `launcher-080-library120-final.png` and `launcher-080-scale100-restored.png`. They show the aligned compact header, reduced gaps, consistent app captions, complete two-line ROM captions at 120%, and restored scale. No Android HOME role, Bluetooth radio, notification-access or power/lock setting was changed.

## Reconciliation and limits

[Design system](../../../design-system.md), [implementation contracts](../../contracts.md) and [progress](../../progress.md) describe the current behavior. Earlier evidence is historical; in particular, v0.7's all-root Back behavior and directions that skipped the filter strip are superseded by this request.

No catalog migration, ROM/save/frontend file changes, library reset or artwork-source policy changes are involved. Notification access remains ungranted on the physical device; real notification arrival/removal after opting in is not claimed as verified. Bluetooth's read-only API path and both shell visibility states are covered without changing the radio. The full human-controller, emulator/game-boot and manual metadata/artwork correction matrices remain outside this UI batch.

Primary Android references: [Bluetooth radio state](https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#isEnabled()), [Android 12 adapter implementation](https://android.googlesource.com/platform/frameworks/base/+/android12-release/core/java/android/bluetooth/BluetoothAdapter.java), [active notification ranking keys](https://developer.android.com/reference/android/service/notification/NotificationListenerService.RankingMap#getOrderedKeys()), and [notification listener settings](https://developer.android.com/reference/android/provider/Settings#ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).
