# Handheld Launcher

An Android HOME launcher for the Retroid Pocket Flip 2, built in Kotlin and Jetpack Compose with MVVM.

Implementation has started with F01's four-module foundation. The initial debug APK builds and opens as an ordinary Android Activity on an Android 13 emulator. HOME integration and feature screens follow in later stories; physical Flip 2 validation remains pending.

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
