# Handheld Launcher

An Android HOME launcher for the Retroid Pocket Flip 2, built in Kotlin and Jetpack Compose with MVVM.

The native application base includes Home, Library, Apps, Favorites, Search, Settings and item details in one persistent shell. It discovers installed Android apps, opens them through one acknowledged launch path, stores successful-open order, favorites and category overrides, and offers user-controlled default-HOME setup. The Home proportions, colors, typography, carousel, dock and controller footer follow the supplied design with real device content.

The ROM feature batch adds granted folder scanning, console detection, selectable ROMs, emulator/core choices, and automatic archive preparation. Metadata and cover providers remain later work. No running/session state or demonstration artwork is presented as real data.

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

Use the D-pad or left stick to move, A to activate, B to go back, X to search, Y for item details, Start for the item/page menu, L1/R1 to switch destinations and L2/R2 to change supported filters. Settings → Controls swaps A/B and updates the footer. Touch uses the same controls. In Search, Android owns keyboard editing; Back closes the keyboard before leaving Search.

To make this the Home screen, use Settings → Launcher → Set as Home launcher and choose it in Android. Declining leaves normal browsing available. To switch away later, use Android Settings → Apps → Default apps → Home app.

The status strip uses actual local time, battery percentage, battery temperature, used/total RAM, available internal storage and Wi-Fi connectivity. Battery temperature is not CPU temperature.

For the combined emulator checks, set `ANDROID_SERIAL` to an isolated emulator before running `:app:connectedDebugAndroidTest :core:designsystem:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest`. Detailed verification and remaining physical controller/lid checks are recorded in [implementation progress](docs/implementation/progress.md).
