# Feature implementation plan

Status: **planned; no Android implementation has been completed**. Baseline: 7 September 2026. This decomposition uses Astra Ultra once for planning. Implementation uses the cheaper model assignments below; it does not automatically escalate back to Astra.

Read [the project plan](../project-plan.md), [the design contract](../design-system.md), and [the agent workflow](agent-workflow.md) before dispatching a packet. Product behavior in those documents remains authoritative. Home alone supplies shell geometry; the other screens are content templates.

The [user-story backlog](user-stories/README.md) now divides each F01–F18 packet into smaller execution units. Use one ready US per implementation assignment. Keep these feature packets as the technical scope, ownership, and integration-group reference; their dependencies and gates still apply to all child stories.

## How to use the packets

Implement the relevant numbered steps for one child user story at a time, using its acceptance criteria. Start no feature before its prerequisites pass. A packet is complete only when all child stories, applicable validation, and handoff evidence are reviewed and accepted by the coordinator. The coordinator integrates after every parallel wave; a successful worker report alone does not open the next wave.

Use at most **three concurrent implementation workers**, leaving the fourth agent slot for the coordinator. The schedule below is deliberately conservative about shared files. A single worker may do sequential packets. Optional worktrees require a reviewed committed baseline first because this repository currently has no commits; directory leases work for a shared checkout. Do not reset or discard existing work to create a baseline.

Model names and reasoning levels are implementation assignments, not estimates of Codex charges. `gpt-5.6-terra` at `high` is the default feature implementer; `gpt-5.6-luna` at `medium` handles constrained visual controls; `gpt-5.6-sol` at `high` handles Android lifecycle, input, persistence, storage, and integration. Those combinations are callable in this session. The Astra `ultra` planning choice is session-specific, not a general statement about API reasoning options.

## Packet index and dependency graph

All packets begin with status **planned**. Dependencies identify reviewed results, including the coordinator's shared-file integration.

| ID | Feature packet | Model / effort | Dependencies | Wave |
|---|---|---|---|---|
| F01 | [Foundation and shared contracts](features/F01-foundation.md) | sol / high | Approved planning baseline | 1 |
| F02 | [Theme, metrics, and visual primitives](features/F02-visual-foundations.md) | luna / medium | F01 | 2 |
| F06 | [Catalog and preferences persistence](features/F06-persistence.md) | sol / high | F01 | 2 |
| F03 | [Reusable controls and gallery](features/F03-controls-gallery.md) | luna / medium | F02 | 3 |
| F07 | [Android discovery and launch adapters](features/F07-android-catalog.md) | sol / high | F06 | 3 |
| F04 | [Persistent shell and navigation](features/F04-shell-navigation.md) | terra / high | F03 | 4 |
| F05 | [Controller input and focus restoration](features/F05-controller-restoration.md) | sol / high | F04, F06 | 5 |
| F08 | [First working HOME loop](features/F08-home-loop.md) | sol / high | F05, F07 | 6 |
| F09 | [Library and Apps](features/F09-library-apps.md) | terra / high | F08 | 7 |
| F10 | [Favorites and item details](features/F10-favorites-details.md) | terra / high | F08 | 7 |
| F11 | [Local Search and system keyboard](features/F11-search.md) | terra / high | F08 | 7 |
| F12 | [Basic Settings](features/F12-settings.md) | terra / high | F09, F10, F11 | 8 |
| F13 | [Real device status](features/F13-device-status.md) | sol / high | F08 | 8 |
| F14 | [Destination integration gate](features/F14-destination-integration.md) | sol / high | F09, F10, F11, F12, F13 | 9 |
| F15 | [ROM sources and indexing](features/F15-rom-index.md) | sol / high | F14; first-platform decision | 10 |
| F16 | [Emulator selection and ROM launch](features/F16-emulator-launch.md) | sol / high | F15; emulator support decision | 11 |
| F17 | [Metadata and artwork enrichment](features/F17-metadata-artwork.md) | sol / high | F16; provider decision | 12 |
| F18 | [Flip 2 hardening and delivery](features/F18-device-delivery.md) | sol / high | F17 | 13 |

`sol`, `terra`, and `luna` mean `gpt-5.6-sol`, `gpt-5.6-terra`, and `gpt-5.6-luna`. Wave 3 allows controls and Android services to progress independently. F04/F05 use fixtures and explicit unavailable status values; they do not wait for enrichment, ROMs, or every production destination. Wave 7 uses three workers with disjoint feature packages. Integrate all three before starting Settings. The first real installed-app HOME loop is F08, before full destination, ROM, or provider delivery.

## Concrete path and ownership policy

Use application ID and base package `dev.handheld.launcher`. Android module source sets use `src/main/kotlin`; tests use `src/test/kotlin` or `src/androidTest/kotlin`. These are **proposed paths**, not claims that the scaffold already exists.

| Area | Concrete path prefix |
|---|---|
| App code | `app/src/main/kotlin/dev/handheld/launcher/` |
| Pure domain | `core/domain/src/main/kotlin/dev/handheld/launcher/core/domain/` |
| Data/platform adapters | `core/data/src/main/kotlin/dev/handheld/launcher/core/data/` |
| Design system | `core/designsystem/src/main/kotlin/dev/handheld/launcher/core/designsystem/` |
| Debug gallery | `app/src/debug/kotlin/dev/handheld/launcher/catalog/` |
| Per-packet evidence | `docs/implementation/evidence/Fxx/` |

Every packet lists its exact writable patterns. A directory suffix `/**` includes only that subtree. Test patterns substitute the stated source set and retain the package prefix; they do not authorize edits to other test packages. Workers may read all project files. They may not edit sibling features or shared files merely to make their branch compile.

The coordinator is the integration owner for the following reserved surfaces:

- Root/module Gradle files, wrapper, version catalog, `gradle.properties`, repository settings, build scripts, and dependency declarations.
- All manifests, permissions, intent filters, application resources outside an explicit visual-foundation lease, and release configuration.
- `app/.../MainActivity.kt`, `app/.../di/**`, `app/.../contract/**`, top-level navigation registry/route declarations, and Activity request wiring.
- All domain models/repository interfaces/policies that define shared behavior and their contract tests.
- `core/data/.../local/**`, `core/data/schemas/**`, and persisted entity/DAO/migration formats, including DataStore keys and versioned navigation encoding.
- Design tokens, typography, `ShellMetrics`, shared control signatures, and shared app presentation/input/navigation contract signatures.

F01 creates the initial reserved foundation. F02 receives a one-time lease for original tokens/metrics. F06 receives a one-time lease for original Room/DataStore implementation and schema. F03 establishes the initial shared control API, F04 the initial navigation/shell internals, F05 the initial input/restoration internals, and F08 the initial common item presentation adapter. These creation leases are explicit exceptions; once a packet is accepted, later workers submit shared deltas to the coordinator. A lease cannot have two active writers. An integration packet may receive a documented temporary lease; it is not blanket permission to edit every feature.

A required shared delta is a short proposal: affected path/symbol, current contract, proposed contract, reason, existing consumers, migration/backward-compatibility impact, and focused validation. The coordinator implements or assigns that bounded change and publishes the revised contract before consumers continue. Routine additive implementation within approved behavior does not require user reapproval. A change to product meaning, public launch contract, security/permissions, persistent schema meaning, data integrity, or supported compatibility with materially different tradeoffs triggers the user's ambiguity gate. Do not silently select one of those alternatives.

## Foundation contract checklist

F01 records concrete signatures and semantics in `docs/implementation/contracts.md` and the corresponding domain/app code. Keep it small enough for the installed-app slice; add later ROM/provider contracts through their explicit integration checkpoints.

1. `ItemId`, current-user Android component identity, `LibraryItem`, `LaunchTarget`, `Availability`, supported item actions, deterministic categories, and source provenance.
2. Read-only observable catalog queries, favorite references, successful-open recency, navigation snapshots, and preference access. A completed inventory and a failed/partial inventory must be distinguishable.
3. Launch request/result semantics: acknowledged one-shot requests, duplicate suppression, a successful dispatch updates recency exactly once, and failure updates nothing. The session correlation key is an operation identifier, not a playtime/session tracker.
4. Six destination IDs in fixed dock order, details/search origin behavior, per-destination identity/anchor snapshots, and a typed shell action/footer contract. Persist identifiers and anchors, never Compose objects or whole lists.
5. Semantic input actions, a typed persisted confirm/back mapping preference and single-owner dispatch precedence. UI focus and scrolling remain in Compose; ViewModels retain selection/filter/query keys. Modal controls expose only presentation-level lifecycle callbacks; app input binding arrives later and cannot create a design-system dependency on app/domain.
6. Status available/unavailable/unsupported states and optional capability omission. F04 can render honest unavailable values before F13 is wired.
7. A shared item-to-card presentation adapter boundary and launch/navigation ports for pages. Features never instantiate repositories or Android adapters inside controls.

The project remains four modules: `app`, `core:domain`, `core:data`, `core:designsystem`. Manual `AppContainer` constructor injection and explicit ViewModel factories are the default. Domain contains no Android/Compose types; the design system depends on no product module. Scaffold minimum implementations and deterministic fixtures rather than inventing speculative frameworks.

## Integration and decision gates

| Gate | Evidence required before advancing |
|---|---|
| After F01 | Four modules build with pinned compatible tools; approved initial contracts and path leases; no unsupported library-version assumptions. |
| After wave 2 | Theme/primitives compile independently; Room/preferences behavior tests pass; no Android/domain leakage. |
| After wave 3 | Production control gallery renders; cached discovery/dispatch tests pass; no placeholder readout masquerades as telemetry. |
| After F05 | Static Home inside one shell matches reference proportions as far as available device evidence allows; controller focus/Back route rules work with fixtures. Record unverified physical calibration explicitly. |
| After F08 | Choose default HOME → open B from `[C,B,A]` → return Home → `[B,C,A]` with B selected; cache visible before scanning; failed dispatch preserves order; external task remains separate. |
| After waves 7 and 8 | Combined build, lint, relevant JVM tests, and applicable Compose tests pass; shared-file diffs reviewed; no duplicate shell or mismatched footer/action. |
| After F14 | Six real destinations, details, IME and controller restoration pass together. Native-app slice is complete enough to use before ROM work. |
| Before F15/F16 | Record a first supported platform/format and emulator app/version matrix; validate multi-file/disc identity and URI behavior before promising support. Ask the user only for genuinely unresolved product/support choices. |
| Before F17 | Name the first metadata provider, terms/attribution, credential approach, requests/data sent, limits/cost, and offline behavior. Prepare choices for user approval where these materially differ. |
| After F18 | Final on-device matrix, calibrated screenshot evidence, measured performance, installable APK, and setup/recovery documentation. |

Lack of a connected Flip 2 does not block planning, pure tests, emulator tests, or implementation that has an approved contract. It does block claiming the physical-device gates passed. Record the missing evidence and continue independent authorized work; ask for the device/test result when that gate becomes necessary. Do not fabricate numbers, compatibility results, screenshots, or a passing status.

All integration gates run the configured debug build, Android lint, relevant JVM tests, and applicable instrumentation tests. Add screenshot baselines after native calibration, not before. Repeat checks only for the integrated change and unresolved risks. Numeric startup/memory budgets are set from the first physical measurement, not invented in this plan.

## Cross-packet rules

- One persistent shell owns background, status, dock, footer, insets, navigation, and overlays. All pages use the allocated content rectangle.
- Home uses most-recently-opened ordering. Reopening promotes the same ID. Focus, details, scraping, and system actions do not promote entries.
- Successful launcher-initiated Android/ROM dispatch is recency data; it never proves that a process is running. No HUD, analytics, usage access, process management, or global key interception is introduced.
- Room supplies cached catalog/favorites/recency. DataStore stores small preferences and durable navigation snapshots. Failed discovery/scan never infers deletion from an incomplete result.
- Android keeps notification shade, volume/brightness controls, power menu, and Retroid controls. Immersive handling applies only to this launcher's window.
- Content updates preserve stable IDs, focus, scroll anchors, and card dimensions. All controls support touch and controller interaction and semantic unavailable/error states.
- Reused GPL-compatible source and bundled assets/fonts keep required notices and attribution. Reuse is selective and reviewed; it is not permission to copy a different product architecture.

Each completion handoff contains: changed paths; provided contract symbols; screenshots/test commands and actual outcomes; known limitations; pending coordinator delta; and readiness of dependent packets. Update the packet status only when the coordinator accepts that evidence. For dispatch prompts, branch/worktree decisions, and review routing, use [the agent workflow](agent-workflow.md).
