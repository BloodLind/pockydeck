# Finite navigation, display settings and spacing — 0.7.0

Date: 8 September 2026. Baseline: `8618b61`; branch: `codex/f01-foundation`. This batch implements the user's fourteen follow-up requests using their approved recommended decisions. Android checks used the physical Flip 2, with a combined verification phase after implementation.

## Delivered behavior

- The category gallery is finite. All is a fixed shortcut outside the strip, with a divider, decorative L2/R2 hints and the existing All filters grid. Natural chip widths and 2dp gaps remove the large spaces between categories. A short strip takes only its needed width, keeping R2 and All filters nearby. Title, filters and sorting share the header; below 560dp content width, filters move to a second row.
- D-pad/stick directions leave the scrolling filter strip instead of traversing its categories: Left/Up goes to All, Right to All filters, and Down to the selected or first card. L2/R2 change the category. A press advances once; a held trigger waits 650ms before accelerating from 115ms to 55ms intervals over 2.5 seconds. Directional holds retain their 360ms delay. Endpoint presses preserve selection and viewport. Digital/analog reports still share one engagement.
- L1/R1 switch pages without painting a transient Home/dock focus outline. Touch clears controller highlights; the first subsequent directional input restores a card. Grid movement continues across row ends. Filter changes restore a placed card by stable key after results settle, cancelling obsolete restoration when touch, a modal or newer criteria takes over.
- Back closes the active modal/editor first and restores its origin. Back leaves all six root dock pages in place. Shortcut Search and item details retain explicit return routes. A Back footer hint appears only when there is an action it can perform.
- App and ROM captions are centered below equally sized artwork and retain two native text lines. Default typography is a further 10% larger, with regular clock/status weights and the same light Material Symbols. Dock controls and glyphs are 10% larger. The footer has a minimum 52dp band and more bottom clearance.
- Settings uses consistent selected choice, action and toggle rows, right-aligned values and up to two supporting-text lines. Display provides persisted 90/100/110/120% UI scale and Reduce motion. UI scale changes native geometry and typography while preserving Android's independent text-size setting. No Room migration or catalog reset is involved.
- The misleading Recently active presentation, its polling and Usage Access setting/permission are removed. Successful-open history still supplies Home/Recent ordering. Existing stored files are not deleted.

## Running-state limitation

An ordinary Android launcher cannot reliably enumerate other applications' live processes or know which ROM an emulator is executing. Android restricts process visibility, usage events record activity history, and a cached process does not establish that a game is running. The revision therefore removes the historical badge instead of labeling history as live state. No privileged process-monitoring service was introduced. A trustworthy per-game indicator would require a cooperating emulator interface.

Primary references: [Android 13 process enumeration implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android13-release/services/core/java/com/android/server/am/ProcessList.java), [UsageEvents.Event](https://developer.android.com/reference/android/app/usage/UsageEvents.Event), and [Android process lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle).

## Findings corrected during verification

Disabling dock focusability hid its outline but allowed Android's early input handling to swallow the first D-pad event after touch. Dock suppression now affects presentation only. A native fallback also retains the pre-IME input host when a route clears native focus after touch, without selecting a Compose control or interfering with the Search editor. Device tests cover both the ordinary transition and Search-to-Library touch followed by controller input.

Restoration previously could run against an old filter result or an unplaced grid item. It now follows the filter/sort/request tuple, waits for completed results, verifies the stable key in the placed layout and retries for a bounded number of frames.

The live 120% visual check exposed cached dp bounds being converted through the newly selected density, producing a root larger than the native screen. Root geometry now reads raw pixel constraints. Reference-scale clamping also precedes UI compensation so small windows do not amplify the selected percentage. The new native regression changes scale through Settings and verifies the un-clipped root, dock, every destination and footer controls against the physical window, then restores the original preference.

An initial design-system device run occurred while the device was dozing and failed seven interaction/render assertions. A wake-only rerun passed all 22 tests. Power and lock settings were not changed. Earlier focus failures were fixed as described above; the final app class passes as one run.

## Verification

**347 tests passed, one skipped, zero failures/errors in the final applicable runs.** Debug/release APK builds and lint passed. Lint reports zero errors and 74 warnings: 64 app, 2 data and 8 design system. The existing Windows symlink fixture is the one skipped test.

| Suite | Passed | Skipped |
| --- | ---: | ---: |
| Domain JVM | 58 | 0 |
| App JVM | 84 | 0 |
| Data JVM | 88 | 1 |
| Design-system JVM | 12 | 0 |
| Data Android fixtures | 76 | 0 |
| Design-system Android fixtures | 22 | 0 |
| MainActivity real-catalog Android tests | 7 | 0 |

The final seven MainActivity tests passed together in **106.202 seconds** on the exact delivered APK. They cover touch/controller restoration, Search Apply/Cancel, mixed slow trigger reports and accelerated holds, Back on roots and modals, finite filters and directional exits, grid row transitions, and persisted live UI scaling within the native window. Native design tests include two-line captions at 130% text. DataStore tests verify display defaults, persistence on reopen, invalid stored values and rejection of invalid writes without changing unrelated settings.

Build and result logs remain in ignored `.local`: `verification-070.log`, `verification-070-delivery.log`, `verification-070-scale-final.log`, `device-070.log` (data pass and the earlier dozing design failure), `design-device-070-awake.log`, and `main-device-070-release.log`. Main-app installation used `adb install -r`; its tests used an explicit instrumentation class rather than the Gradle task that uninstalls the launcher. Core data/design test packages are isolated fixtures.

APK: `.local/releases/handheld-launcher-0.7.0-debug.apk`, version **0.7.0**, code **10**. SHA-256: `b69198555289c426e53e5cdfb4662b85174e24b20f7a7aed497a3459aae27ca4`. The installed `base.apk` has the same SHA-256. The final launcher process had no AndroidRuntime error output.

Device: Retroid Pocket Flip 2, Android 13, serial `89a34d44`, 1920×1080, density 2.25. Final inspected captures are in ignored `.local`: `launcher-070-home-final.png`, `launcher-070-library-final.png`, `launcher-070-apps-final.png`, `launcher-070-display.png`, `launcher-070-settings.png`, `launcher-070-scale120-fixed.png`, and `launcher-070-library120-final.png`. They show the normal and 120% layouts, compact filter spacing, consistent captions, and increased footer clearance. App UI scale is restored to 100%; system font scale remains 1.0.

## Reconciliation and limits

[Design system](../../../design-system.md), [implementation contracts](../../contracts.md), and [progress](../../progress.md) describe the current finite categories, input timing, root Back behavior, spacing, display settings and removal of historical activity badges. Earlier evidence is historical and may describe superseded cyclic filters or badges.

The final physical Library displays 3,768 games, and All files access remains allowed. ROMs, saves, ES-DE metadata and PC frontend files were not modified or exported; no game was activated by this revision's tests. HOME role and Android power/lock settings were not changed by this work.

Injected Android key/axis events on the actual device validate the native input path; they do not replace extended human testing of every controller, firmware or emulator. The complete emulator/game-boot matrix and full manual metadata/artwork correction remain outside this UI revision. No universal live-process claim or storage-scan time is made.
