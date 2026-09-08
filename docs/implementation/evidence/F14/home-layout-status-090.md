# Home, collection layouts and live process status — 0.9.0

Date: 8 September 2026. Baseline: `486fced`; branch: `codex/f01-foundation`. This batch handles the user's thirteen follow-up requests and their explicit approval for optional Shizuku support. It keeps the reference shell and light Material Symbols. Checks run together after implementation on the physical Flip 2.

## Delivered behavior

- Page entries use a 160ms fade and an 8dp upward settling motion. There is only one live interactive page, so an outgoing page cannot receive input or duplicate side effects. Reduce motion makes the change immediate; the existing short footer and cover transitions remain.
- Collection headers measure their actual title, pill, chooser, layout and sort controls before allocating space. Title and controls share a centered row when they fit and use a second filter row when needed. All remains separate; only completely visible categories join D-pad/stick navigation. L2/R2 retain finite stepping and accelerated holds after 650ms.
- Grid artwork is capped at 176 reference units, with full column width retained for centered two-line captions. Available height can shrink it further at large scales. Library, Apps and Favorites each have an independent, persisted Grid/List choice beside Sort. List rows use the shared horizontal card; changing layout preserves selected identity and controller navigation.
- Every available recent item leads Home in descending successful-open order. A new launch no longer discards the previously recent PSP/other ROM from Home. After recent entries, a stable representative fills each unrepresented console and the Android-game group; other apps follow alphabetically. Home remains capped at 20. The showcase is deterministic instead of reshuffling on every scan or recomposition.
- Entering Home, reselecting its dock button or resuming the actual Home page resets both the selected item and gallery position to the first card. Reset intent survives a delayed recent/catalog update until the user moves. This works in touch mode as well as controller mode.
- Settings → Launcher category counts are informational rows without navigation actions. Sort's Close action uses the shared compact pill at the end of the chooser.
- Battery tint is green while Android reports charging, red at 10% or below, yellow from 11% through 14%, and neutral otherwise. A full, plugged-in battery is neutral when Android reports FULL rather than CHARGING. Unknown readings are muted.
- The existing temperature source is **battery temperature**, not CPU temperature. Its glyph/tint is cold below 15°C, neutral from 15°C to below 45°C and hot at 45°C or above. RAM has its own cool tint.
- Storage samples internal free space and every distinct mounted external volume using metadata reads, without traversing game files. The strip shows INT and EXT; multiple external volumes use EXT×N and aggregate known free bytes. Partial/unknown readings are explicit, and accessibility describes each volume. The primary emulated duplicate is excluded. Read-only mounted storage remains represented.

## Optional Shizuku status

Settings → Launcher → Running indicators is off by default. Enabling it does not request privileged access automatically. Set up Shizuku opens the normal per-app request when the helper is running, otherwise its manager or official download page. The user explicitly approved adding Shizuku and then completed the concrete persistent permission dialog after automatic approval review required that extra confirmation.

The launcher samples live process presence every three seconds only while its foreground UI collects status. A separate Shizuku UserService runs one fixed `/system/bin/ps -A -o UID,NAME` command. Its AIDL accepts a bounded list of valid package names and the current Android user, enforces the calling launcher UID, and returns only matching requested packages. There is no arbitrary command/path API, external process management, game-file access or active-ROM inspection. A two-second watchdog applies only to this helper's own `ps` child. Lifecycle shutdown terminates only the helper itself.

Android cards show Running. ROM cards name the last successfully dispatched emulator; absent that history, the configured or uniquely available supported emulator supplies the target. Ambiguous unconfigured choices have no badge. A badge establishes that the corresponding process exists, including a paused emulator or its menu; it never asserts which ROM is active. Architecture suffixes stay in emulator choice labels and are omitted from compact running badges. Unknown, denied, disconnected or disabled readings hide badges. The launcher stores its opt-in and bounded ROM-handler routing history, not process observations.

The binding uses a profile-specific tag and checks connection identity/access again before publishing a result. Late callbacks and in-flight reads cannot revive stale status after disable, revocation or UI teardown. Disabled mode skips emulator target resolution and helper binding. Reconnects are bounded and back off after a stalled bind.

The app includes the MIT-licensed Shizuku API/provider **13.1.5**, with its unchanged notice in assets. The official helper **13.6.0.r1086.2650830c** was downloaded from its GitHub release, signature-checked, installed and started through the USB command displayed by its manager. No root or wireless-debugging setup was introduced. Shizuku needs starting again after a device reboot; permission can be revoked in its manager, and the launcher toggle stops its own monitoring.

Primary references: [Shizuku API and UserService contract](https://github.com/RikkaApps/Shizuku-API), [official setup guide](https://shizuku.rikka.app/guide/setup/), [official 13.6.0 release](https://github.com/RikkaApps/Shizuku/releases/tag/v13.6.0), [Android battery state](https://developer.android.com/reference/android/os/BatteryManager), and [storage-volume metadata](https://developer.android.com/reference/android/os/storage/StorageVolume).

Helper APK SHA-256: `6e273ab0e991c4e79bc8b1bbb9b9dd739ccac1a8712a541a214078886b7b790f`. Verified signer certificate SHA-256: `268b5590e868fb08bae7e0ac413564cd1ff88f5ccff74af9dbd0dc918e30db30` (CN=Rikka).

## Findings corrected during verification

The new Home capacity fixture initially used openOrder zero, violating the existing positive-order contract. The fixture now uses one-based order; the production invariant and assertion remain intact.

The first live visual check exposed app status being constrained inside the small native icon. Status now uses the full Home/grid card slot. Horizontal/list cards place it beside the artwork in the caption area, and the dot is a separate decorative shape that cannot wrap onto a line by itself. Native presentation regressions check readable labels at enlarged display/font scales and a single stable card activation target as status changes.

Those new regressions initially treated Compose's paragraph-overflow flag as proof of clipped text. Device diagnostics showed complete, unellipsized labels inside their actual bounds; the flag compared unused paragraph width against the compact text allocation. The checks now verify every character box, visible line end, line bottom and absence of ellipsis directly. Production layout was unchanged by this test correction.

## Verification

**399 unique tests passed, one skipped, zero unresolved failures/errors in the final applicable runs.** Debug/release builds and lint pass. Lint reports zero errors, 65 warnings and 10 informational findings. The existing Windows symlink fixture is skipped.

| Suite | Passed | Skipped |
| --- | ---: | ---: |
| Domain JVM | 59 | 0 |
| App JVM | 105 | 0 |
| Data JVM | 100 | 1 |
| Design-system JVM | 12 | 0 |
| Data Android fixtures | 81 | 0 |
| Design-system Android fixtures | 22 | 0 |
| MainActivity real-catalog Android tests | 8 | 0 |
| Shell presentation Android tests | 2 | 0 |
| Search composition/focus Android tests | 3 | 0 |
| Home touch/reset Android fixture | 1 | 0 |
| Collection header/layout Android fixtures | 2 | 0 |
| Status-strip Android fixture | 1 | 0 |
| Shizuku live-read Android test | 1 | 0 |
| Running-badge Android fixtures | 2 | 0 |

All 18 initial app checks passed together in 135.616 seconds, including current-user helper reads and cross-profile rejection. On the final badge-corrected APK, those 18 checks passed in the expanded run; the corrected badge assertions then passed both focused tests in 2.837 seconds on the identical app binary. The 22 design-system Android checks passed again after the shared card-slot change. The count above includes each test once, not repeated runs. All Android checks used the physical device.

Logs in ignored `.local`: `verification-090-integrated.log`, `verification-090-final.log`, `device-090.log`, `main-device-090.log`, `verification-090-delivery.log`, `main-device-090-delivery.log`, `badge-090-diagnostic-device.log`, `badge-090-final-build.log`, `badge-090-final-device.log`, and `design-device-090-delivery.log`. Main-app installation uses `adb install -r` and explicit instrumentation classes; its Gradle connected-test task is deliberately not used on the user's launcher. Core test packages are isolated fixtures.

APK: `.local/releases/handheld-launcher-0.9.0-debug.apk`, version **0.9.0**, code **12**. SHA-256: `e669b7ae67a81dfbf7890da982de42c415ad0e8ce90669e06600a1ad553df30a`. The installed `base.apk` matches that checksum and reports the same version. All files access remains allowed; Android's system font scale remains 1.0.

Device: Retroid Pocket Flip 2, Android 13, serial `89a34d44`, 1920×1080, density 2.25. The real Library still has 3,768 games. Shizuku reports the existing PPSSPP and RetroArch processes; their card badges match an independent read of the same device process table without launching or closing games.

The final device check disabled Running indicators, observed the launcher's helper exit while PPSSPP and RetroArch remained alive, then re-enabled it and observed live badges return. Indicators are left enabled with the user's grant. App UI scale is restored to the 110% setting observed before manual visual checks; Library is back in Grid mode, and Home is left at its first card. The final launcher-process AndroidRuntime error log is empty.

Final inspected captures in ignored `.local`: `launcher-090-home-final.png`, `launcher-090-library100-final.png`, `launcher-090-library110-final.png`, `launcher-090-library120-final.png`, `launcher-090-list-final.png`, `launcher-090-sort-final.png`, `launcher-090-apps-final.png`, `launcher-090-toggle-ready.png` and `launcher-090-toggle-off.png`. They cover the scaled headers, complete first-row captions, readable live labels, informational category counts and compact Close action.

## Reconciliation and limits

The [design system](../../../design-system.md), [contracts](../../contracts.md), [feature plan](../../feature-plan.md), [README](../../../../README.md) and [progress](../../progress.md) describe this revision. Prior evidence remains historical; newest-only Home ordering and the previous blanket absence of process indicators are superseded by these requests and the explicit Shizuku opt-in.

No Room migration, catalog reset, ROM/save/frontend modification or artwork-source change is involved. Notification access, wireless debugging, Android HOME role, radio settings and power/lock settings were not changed by this batch. The full human-controller and emulator/game-boot matrices remain separate; this batch verifies navigation with real Android input events and existing catalog data without starting new games. Thermal/color edge cases and multiple external volumes use controlled presentation/data fixtures; the physical device supplies its actual battery, RAM, internal and SD readings.
