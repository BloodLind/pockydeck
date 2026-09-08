# Handheld Launcher

An Android HOME launcher for the Retroid Pocket Flip 2, built in Kotlin and Jetpack Compose with MVVM.

The native application base includes Home, Library, Apps, Favorites, Search, Settings and item details in one persistent shell. It discovers installed Android apps, opens them through one acknowledged launch path, stores successful-open order, favorites and category overrides, and offers user-controlled default-HOME setup. The Home proportions, colors, typography, carousel, dock and controller footer follow the supplied design with real device content.

The ROM feature batch adds granted folder scanning, console detection, selectable ROMs, emulator/core choices, automatic archive preparation, existing ES-DE artwork reuse and bounded Libretro cover lookup. Optional Shizuku integration reports observed app or emulator processes; it does not identify the active ROM or infer a game session from launch history.

See the [ROM setup and recovery guide](docs/rom-setup-guide.md), [recognized formats](docs/implementation/evidence/F15/format-support.md), and [emulator compatibility contracts](docs/implementation/evidence/F16/emulator-contracts.md). Recognizing a file format does not imply that every emulator can launch it.

- [Implementation progress](docs/implementation/progress.md): current story status, ownership, evidence, and dependency gates.
- [Foundation launch evidence](docs/implementation/evidence/F01/US-001-launch.md): Android 13 emulator smoke test and screenshot.

- [Project plan](docs/project-plan.md): scope, architecture, application behavior, delivery stages, and acceptance criteria.
- [Design system](docs/design-system.md): shared shell, reference measurements, typography, styles, controls, and page templates.
- [Feature implementation plan](docs/implementation/feature-plan.md): individual agent tasks, dependencies, ownership, and acceptance gates.
- [User-story backlog](docs/implementation/user-stories/README.md): smaller stories under every feature, with testable acceptance criteria and required evidence.
- [Agent workflow](docs/implementation/agent-workflow.md): model assignments, execution order, and copy-ready coordinator, worker, and review prompts.
- [Home reference](docs/references/home.png): authoritative visual reference for proportions and alignment.
- [Library template](docs/references/library-template.png): content template; its shell dimensions are not authoritative.

The latest user decisions take precedence over the attached [design source](docs/references/design-source.txt). That source includes generated PRD/HTML material, demonstration data, and features excluded from this project.

## Build locally

Use JDK 17, Android SDK platform 34 and the checked-in Gradle 8.9 wrapper. Point Android Studio or the ignored `local.properties` at your SDK. The application requires Android 13 or later.

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest :core:designsystem:testDebugUnitTest
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. On macOS/Linux, use `./gradlew` with the same tasks. Host-specific commands and the Windows JBR socket workaround used in this session are recorded in [foundation evidence](docs/implementation/evidence/F01/US-003.md).

## Install and use on a device

Enable USB debugging, connect the device and accept its Android authorization prompt. Use `adb devices` to find its serial, then replace `SERIAL` below. `install -r` preserves the launcher's existing data.

```powershell
adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s SERIAL shell am start -n dev.handheld.launcher/.MainActivity
```

Use the D-pad or left stick to move, A to activate, B to go back, X to search, Y for item details, Start for the item/page menu, L1/R1 to switch destinations and L2/R2 to change supported filters or move through Home cards. Settings → Controls swaps A/B and updates the footer. Touch uses the same controls. Search uses A Apply, B Cancel and Y Clear while editing; closing Search clears its query and filter, while opening details preserves the Search session for return.

Home shows up to 20 cards: available recently opened games and apps first, then one stable game from each console not already represented, one Android-game representative where needed, and remaining apps. Returning Home, selecting Home again or returning from another Activity while Home is open resets selection and scrolling to the first card. Library, Apps and Favorites each remember their own Grid/List choice. Settings → Display offers 90%, 100%, 110% and 120% UI scale and Reduce motion.

To make this the Home screen, use Settings → Launcher → Set as Home launcher and choose it in Android. Declining leaves normal browsing available. To switch away later, use Android Settings → Apps → Default apps → Home app.

The status strip shows local time, battery percentage, **battery temperature**, used/total RAM, internal free space and free space across mounted external volumes, plus Wi-Fi and available optional indicators. Temperature is blue below 15°C and red at 45°C or above; it is not a CPU reading. Battery color is green only while Android reports charging, otherwise red at 10% or below, yellow below 15%, and neutral at higher levels. RAM uses a distinct cool tint and remains separate from storage.

## Optional running indicators

Running indicators are off by default. They need the separate official Shizuku helper and your permission; ordinary launcher features work without them.

1. Open Settings → Launcher, enable **Running indicators**, then choose **Set up Shizuku**.
2. If needed, install Shizuku from its [official download page](https://shizuku.rikka.app/download/). Start it using its USB/computer or wireless-debugging instructions in the [official setup guide](https://shizuku.rikka.app/guide/setup/).
3. Allow Handheld Launcher when Shizuku asks. Its normal persistent authorization avoids a prompt on every reading. After a reboot, restart Shizuku; the debugging-based helper does not remain running across reboots. If permission was revoked, authorize the launcher again through Shizuku.

While the launcher is foreground, it checks requested current-user package processes every three seconds. An app badge means its process was observed. A ROM badge names its last dispatched emulator, or the configured/unique supported installed emulator, only when that emulator process is observed. It may be paused, cached or showing its menu; the badge does not mean that ROM is playing.

Turn **Running indicators** off to stop sampling and remove badges, or revoke the launcher's authorization in Shizuku's authorized-app list. No Usage Access or session-history inference is used. This integration bundles Shizuku API 13.1.5; the development device setup uses the official Shizuku 13.6.0 helper started over USB.

## Verification

For the combined emulator checks, set `ANDROID_SERIAL` to an isolated emulator before running `:app:connectedDebugAndroidTest :core:designsystem:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest`. Detailed verification and remaining physical controller/lid checks are recorded in [implementation progress](docs/implementation/progress.md).
