# Queued controls, catalog and real-device revision — 0.6.0

Date: 8 September 2026. Baseline: `4d5b2b5`; branch: `codex/f01-foundation`. The user approved the recommended answers to all pending questions, parallel implementation and one combined verification phase on the physical Flip 2. No emulator was used.

## Delivered behavior

- Library title, a bounded cyclic filter strip, fixed decorative L2/R2 hints, All filters and sorting share one header. All filters presents larger three-column choices. Type and console abbreviations sit on card artwork. Collection artwork is smaller, centered and square; captions retain two native text lines and more horizontal room. Large system text reduces grid/filter density. Footer hints have more separation and a small bottom inset. Clock/status type stays regular; icons retain the HTML preview's official light Material Symbols.
- Home promotes the newest successful open, then Android games, identified ROMs and remaining apps, with alphabetical ordering within the groups and a 20-item cap. Library/Apps/Favorites keep their requested membership rules. Unresolved ROMs remain accessible for correction through Settings rather than appearing as unlaunchable main-grid cards.
- L2/R2 cycle filters (Home moves through cards). One press advances once; holding waits 360ms, then accelerates from 115ms toward 55ms intervals over 2.5 seconds. D-pad and stick holds use the same policy. Per-device digital/analog reports share a held engagement and hysteresis. Right continues to the following row; Left reverses, with horizontal stops at whole-collection ends. One navigation worker follows the latest target without queuing scroll animations.
- Touch mode removes controller highlight and automatic card focus. The first subsequent controller navigation/Confirm restores the selected card without launching it. L1/R1 change destination without briefly focusing a dock button. Mapped A/B and touch Apply/Cancel preserve Search's edit-entry query semantics. The landscape keyboard leaves the compact editing footer visible.
- Search prepares its index/results off the UI thread, publishes 128 initial matches then batches of up to 256, and cancels obsolete queries. Selection/viewport changes reuse prepared lists. Empty queries and no-match queries never expose the full catalog. Result scrolling collapses the search header. Artwork remains lazy and uses the existing approved ES-DE/Libretro queue and small pending hint.
- Discovery completes safe inventory/group planning before publication. It publishes completed sources independently in 128-entry batches and reuses their inventories instead of rescanning them immediately. Changed grouping atomically hides obsolete member cards; omission reconciliation waits for successful completion. Unchanged catalog rows are not rewritten. Per-item/source choices outrank directory evidence; exact validated ES-DE paths can resolve otherwise unknown console identity. No schema change was needed.
- Recent activity badges use acknowledged launches and optional Usage Access observations with a 30-minute expiry. A ROM identifies the last dispatched emulator; subsequent emulator activity does not extend the ROM timestamp. These badges do not assert a live process or running game. Wi-Fi uses radio state, and battery/temperature/memory/storage glyphs vary with readings.

## Findings corrected during combined verification

The review separated catalog work from fast selection updates, preserved stable selection while saving viewport anchors, and kept an interrupted scroll from terminating the navigation worker. Compose focus requests return Unit in the pinned version; restoration now verifies observed focus instead of treating the return value as a Boolean. Search retains its edit session when focus moves to footer controls.

On Android 13, the first directional key after touch could be consumed before Activity dispatch while Android restored native focus. MainActivity now owns recognized controller input in a native pre-IME host, with ordinary keyboard/editor input passed through. The ordering is documented by [Android's input-stage implementation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android13-release/core/java/android/view/ViewRootImpl.java).

The installed keyboard's landscape extract UI covered the launcher even with its no-fullscreen preference. Text input now explicitly requests both no-fullscreen and no-extract behavior through Compose's text-input interceptor. The compact shell also reserves a 48dp editing footer when space permits. [Android EditorInfo flags](https://developer.android.com/reference/android/view/inputmethod/EditorInfo) define the request; physical screenshots and touch tests verify this device's behavior.

At 130% system text, manually calculated caption heights could drop the second title line. Captions now use native minimum/maximum line counts, with a regression checking actual text layout and stable allocation for a shorter title. A failing earlier geometry fixture was also corrected to measure a wrapping title rather than the intrinsic width of a short word. Row-navigation test selectors were restricted to actual visible focusable cards, and keyboard-close assertions wait for the native transition.

## Verification record

**343 tests passed, one skipped, zero failures/errors in the final applicable runs.** The combined commands built debug/release APKs, ran lint and JVM tests, and used only the Flip 2 for Android verification. Main-app tests were installed with `adb install -r` and invoked by exact class/method; the Gradle task that uninstalls the main app was not used.

| Suite | Passed | Skipped |
| --- | ---: | ---: |
| Domain JVM | 58 | 0 |
| App JVM | 88 | 0 |
| Data JVM | 88 | 1 |
| Design-system JVM | 10 | 0 |
| Data Android fixtures | 73 | 0 |
| Design-system Android fixtures | 22 | 0 |
| MainActivity real-catalog Android tests | 4 | 0 |

The skipped test is the existing Windows symlink fixture. Lint reports zero errors and 74 warnings (64 app, 2 data, 8 design system). The latest build/lint/JVM/design command completed successfully in 1m10s; the final four main-app tests passed in 27.065s on the exact delivered APK. Earlier failed runs are retained in ignored logs, with their corrections described above. Logs: `.local/verification-060.log`, `.local/verification-060-caption-final.log`, and `.local/launcher-060-main-device-final.log`.

APK: `.local/releases/handheld-launcher-0.6.0-debug.apk`, version **0.6.0**, code **9**. SHA-256: `6ea4d725dee2bbcd29cf5e995a2d8730e32a4493aaf4beda6a68f3503769b237`. This APK was installed with `-r`, retaining launcher data.

The physical device is Retroid Pocket Flip 2, Android 13, serial `89a34d44`, 1920×1080 at 360dpi. Inspected captures are retained in ignored `.local`: `launcher-060-home-final.png`, `launcher-060-library-final.png`, `launcher-060-filters-final.png`, `launcher-060-text130-final.png`, and `launcher-060-search-final.png`. The final two show the corrected two-line large-text layout and the visible Apply/Cancel footer above the real keyboard. System font scale was restored from 1.3 to its original 1.0 in a finally block and checked afterward.

The physical test class is `dev.handheld.launcher.integration.MainActivityInputDeviceTest`. It reads the installed catalog, changes filters/search through ordinary UI, restores its query/filter, and never activates a game. It checks row-edge continuation, touch/controller focus, mapped and touch Search editing, slow mixed trigger reports in both orders, accelerated holds and release silence. Storage and design-system fixture packages are isolated from launcher data.

## Reconciliation and limits

[Design system](../../../design-system.md) and [implementation contracts](../../contracts.md) now reflect the approved 20-item Home cap, cyclic header filters, grid chooser, card tags, controller/touch distinction, repeat policy, partial results, unknown-game correction and recent-activity semantics. Older milestone evidence remains historical.

The visible physical Library contains 3,768 games during this revision. The count is an observed UI result, not proof of the cause of its difference from the previous 3,776-item count. Source files, saves, ES-DE metadata and frontend data were not modified or exported. All files access and the existing approved artwork downloads remain enabled; HOME role and optional Usage Access were not changed.

Injected native gamepad/joystick events on real Android validate the input path and timing; they do not replace long manual testing of every physical controller, stick range, firmware or emulator. Metadata matching deliberately rejects ambiguous paths and unsupported rich fields. Optional Usage Access, every running-state edge case and the complete emulator/game-boot matrix are not claimed as physically accepted. There is no guaranteed scan time across arbitrary storage sizes/providers.
