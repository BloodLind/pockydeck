# Handheld Launcher

An Android HOME launcher for the Retroid Pocket Flip 2, built in Kotlin and Jetpack Compose with MVVM.

F01's four-module foundation is accepted. The shared theme and durable Room catalog cache have been implemented in parallel. The initial debug APK builds and opens as an ordinary Android Activity on an Android 13 emulator. HOME integration and feature screens follow in later stories; physical Flip 2 validation remains pending.

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

Use JDK 17, Android SDK platform 34 and the checked-in Gradle 8.9 wrapper. Point Android Studio or the ignored `local.properties` at your SDK. The current application requires Android 13 or later; validation currently covers an Android 13 emulator.

```powershell
.\gradlew.bat assembleDebug lintDebug :core:domain:test :app:testDebugUnitTest
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. On macOS/Linux, use `./gradlew` with the same tasks. Host-specific commands and the Windows JBR socket workaround used in this session are recorded in [foundation evidence](docs/implementation/evidence/F01/US-003.md).
