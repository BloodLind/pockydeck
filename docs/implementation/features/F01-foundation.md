# F01 — Foundation and shared contracts

- Status: **accepted**; see [implementation progress](../progress.md) for reviewed story evidence.
- User stories: [US-001](../user-stories/US-001-installable-four-module-foundation.md), [US-002](../user-stories/US-002-stable-catalog-and-launch-contracts.md), [US-003](../user-stories/US-003-shared-navigation-input-and-presentation-ports.md). Dispatch one ready story; this packet remains the technical ownership and feature-gate reference.
- Agent: `gpt-5.6-sol`, reasoning `high`; sole foundation/integration writer.
- Outcome: an installable empty native launcher project with clear contracts so later agents can work without overlapping edits.
- Prerequisites: approved [project plan](../../project-plan.md), [design contract](../../design-system.md), and [ownership policy](../feature-plan.md). Planning baseline committed as `ad2b668`; scaffold implementation began on 7 September 2026.

## Ownership and contracts

Initial-creation lease: root `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`, `gradlew`, `gradlew.bat`, `gradle/**`; each of `app/build.gradle.kts`, `core/domain/build.gradle.kts`, `core/data/build.gradle.kts`, `core/designsystem/build.gradle.kts`; `app/src/main/AndroidManifest.xml`, `core/data/src/main/AndroidManifest.xml`, `core/designsystem/src/main/AndroidManifest.xml` (the pure Kotlin `core:domain` has no Android manifest); `app/src/main/kotlin/dev/handheld/launcher/MainActivity.kt`, `app/src/main/kotlin/dev/handheld/launcher/di/**`, `app/src/main/kotlin/dev/handheld/launcher/contract/**`; `app/src/main/res/values/{strings,themes}.xml`; `core/domain/src/main/kotlin/dev/handheld/launcher/core/domain/{model,repository,policy}/**`; matching domain JVM test packages; `docs/implementation/contracts.md`; `docs/implementation/evidence/F01/**`. Any extra required build/resource path needs a coordinator lease first. Keep machine-specific SDK paths untracked.

Consumes the two authoritative plans. Provides pinned build/tool versions, four-module boundaries, constructor-injection skeleton, domain item/query/launch/status/preferences contracts, typed shell/navigation/input request interfaces, and small fake implementations for previews/tests. Concrete page navigation belongs to F04; Room formats belong to F06. Define generic ROM/provider extension boundaries only; detailed later contracts wait for F15–F17 decisions.

## Ordered steps

1. Inspect existing workspace changes, installed JDK/SDK/Gradle tooling, and applicable local instructions. Verify a compatible current toolchain against official documentation when needed; record versions and build commands. Do not assume historical tool availability.
2. Scaffold exactly `:app`, `:core:domain`, `:core:data`, `:core:designsystem`, with the dependency direction in the project plan. Use Kotlin/Compose, MVVM, coroutines/Flow, Room and DataStore where appropriate; manual `AppContainer` and explicit ViewModel factories. Configure `core:data` Room runtime/testing, compatible KSP processing, DataStore dependencies and Room schema export to `core/data/schemas` now so F06 can work without Gradle edits.
3. Set `dev.handheld.launcher` as application ID; use package prefixes in the ownership policy. Configure Android 13/Flip 2 as the initial validation target, with physical tests pending and no promise of other-device compatibility. Record SDK choices rather than guessing support claims.
4. Write the contract checklist from the feature plan as actual minimal interfaces/types. Specify stable component identity, query ordering, non-destructive discovery outcomes, successful dispatch/recency atomicity boundary, favorite semantics, per-page snapshot keys, typed confirm/back mapping preference with the agreed A-confirm/B-back default, and one-shot Activity request acknowledgement. Document a presentation-only modal focus lifecycle boundary for F03: its callbacks contain no app/domain types; F05 later binds the app input scope. Document unresolved later-stage fields instead of inventing product behavior.
5. Register a normally launchable Activity; prepare the shared Activity request port. F08 integrates final HOME filters/role/window behavior. Start with a minimal composable and explicit fake/unavailable state; no production claim of full functionality.
6. Provide deterministic test/preview fixtures through test/debug source sets or packet-local fakes. Keep sample status, app titles, and artwork out of production state.
7. Build and run the initial focused contract tests. Publish exact symbols and path leases to dependent packets. Coordinator reviews and creates a clean committed baseline when implementation is authorized, before optional worktrees; never discard unrelated files.

## Excludes

No feature screen implementation, scans, emulator/provider integrations, speculative generic frameworks, production permission expansion, or automatic default-HOME selection. Do not add more modules.

## Validation and handoff

Evidence: successful configured debug build, lint, and domain tests; dependency graph demonstrating pure domain and independent design system; ordinary Activity launch smoke test; recorded installed tool versions. Test meaningful identity/order/request semantics, not DTO getters. Report any device-only checks as pending. Hand off `contracts.md`, build commands, actual file list, and reserved-path ownership to F02/F06; subsequent shared changes go through the coordinator.
