# Feature implementation plan

Status: **implementation in progress**; see [current story status and evidence](progress.md). Planning baseline: 7 September 2026. This decomposition used Astra Ultra once for planning. Implementation uses the cheaper model assignments below; it does not automatically escalate back to Astra.

Read [the project plan](../project-plan.md), [the design contract](../design-system.md), and [the agent workflow](agent-workflow.md) before dispatching a packet. Product behavior in those documents remains authoritative. Home alone supplies shell geometry; the other screens are content templates.

The [user-story backlog](user-stories/README.md) divides F01–F18 into traceable acceptance criteria. On 8 September the user authorized building the complete native application base first, followed by a combined verification and documentation reconciliation. Per-story dispatch and intermediate wave gates are superseded for this delivery; packet scope, shared-file ownership and truthful device evidence still apply.

## Current v0.10.3 integration scope

The current batch implements all available recent Home games/apps before a stable showcase containing one game per otherwise unrepresented console and one Android-game group, followed by deterministic app fillers, with a 20-card limit. Every Home entry/reselection and actual Activity return to Home resets selection and scrolling to first, including delayed recency publication; modal dismissal alone preserves its opener.

Library, Apps and Favorites independently persist Grid/List mode. Grid artwork is capped at 176 reference units and still respects caption/viewport space. Collection headers measure and center their native controls across 100/110/120% scale instead of assuming fixed label widths. Page replacement uses one live page with a 160ms fade/8dp slide, respecting Reduce motion. Sort has a compact Close control, and Launcher category counts are noninteractive.

The user explicitly approved optional Shizuku process indicators and the normal persistent permission flow. They remain off by default and require explicit setup. API/provider 13.1.5 reads requested current-user package process names every three seconds while foreground; the current development setup uses the official Shizuku 13.6.0 helper started over USB. Badges are restricted to Home. ROM badges name the last dispatched emulator or the configured/unique supported adapter only when that process is observed. This does not identify an active ROM, infer a session from history, or introduce Usage Access/process management.

The v0.9.1 correction gives filters slightly more horizontal/vertical padding and less rounding, shrinks only START inside its existing hint, and adds original controller sound cues with an independent Controls toggle. Sound follows media volume and existing normalized input/repeat ownership. Exact-ROM open-file inspection is denied on the Flip 2; no root or remote-control interface is enabled for this optional request.

The v0.10 batch adds wider separation around decorative filter hints and All filters, a larger Sort Close control with smaller choices, a selected-item preview beside List mode, and independent 70–140% grid sizing. Visible-card image requests, size-bucketed decoding, cancellation, bounded caches and background release address scrolling and RAM usage; scroll positions save after settling and filter data is reused during selection changes. Physical frame-timing and memory samples compare the previous and updated builds.

The v0.10.1 follow-up softens the original audio cues, adds a distinct changed-card selection sound, and drops audio requests while one cue is playing. Fixed All and All filters surround a compact console group with balanced L2/R2 spacing. The grid worker keeps focus progressing during sustained maximum-speed repeats; Search also uses visible-only artwork requests, disposed focus requesters and settled scroll saves. Physical input coverage includes a seven-second D-pad hold, reversal and accelerated horizontal row traversal through the populated Search results.

The v0.10.2 correction groups round Open, Details and Favorite controls in List previews, makes explicit text actions visibly fill their 48dp targets, and keeps three whole console categories in the finite filter window. The header measures that three-category requirement before deciding whether to wrap. Category filters and footer controls retain their existing visual scale. Validation covers scaled geometry, touch/controller activation, current-item favorite behavior and the populated-device Search regression.

The v0.10.3 visual follow-up supersedes the large action surfaces with subdued 36dp pills/circles inside unchanged 48dp touch targets. Search uses a query-and-icon action, an adjacent round Clear control and a right-aligned count. Shared button labels use medium-weight control typography; preview actions are grouped more closely. The three-category filter behavior is retained.

The status revision labels battery temperature accurately (blue below 15°C, red from 45°C), colors the battery green only when Android reports CHARGING, otherwise red at 10% or below, yellow below 15%, and neutral above that. RAM has a distinct cool tint; storage covers internal and all mounted external free space, with cyan internal and amber external glyphs. [Current contracts](contracts.md) define behavior and lifecycle limits; [progress](progress.md) records the separate validation outcomes. Code inclusion is not a claim that every physical check has passed.

## How to use the packets

Implement coherent batches across the native application, then check the integrated result against the story criteria and fix findings. A packet is accepted only when its applicable validation has evidence; code integration does not imply completion of unrun physical checks. ROM, emulator compatibility and provider choices retain their own later readiness decisions.

Use at most **three concurrent implementation workers**, leaving the fourth agent slot for the coordinator. Directory leases prevent concurrent edits to the same files in the shared checkout. The repository has committed checkpoints; do not reset or discard existing work to create another baseline.

Model names and reasoning levels are implementation assignments, not estimates of Codex charges. `gpt-5.6-terra` at `high` is the default feature implementer; `gpt-5.6-luna` at `medium` handles constrained visual controls; `gpt-5.6-sol` at `high` handles Android lifecycle, input, persistence, storage, and integration. Those combinations are callable in this session. The Astra `ultra` planning choice is session-specific, not a general statement about API reasoning options.

## Packet index and dependency graph

The table preserves the original dependency plan. Live implementation state and batch evidence are maintained in [progress](progress.md).

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

Use application ID and base package `dev.handheld.launcher`. Android module source sets use `src/main/kotlin`; tests use `src/test/kotlin` or `src/androidTest/kotlin`. These are the implemented module paths; per-task leases still determine who may edit them.

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
- Home prioritizes all available recent IDs before its stable console showcase and app fillers, capped at 20 cards. Reopening promotes the same ID. Focus, details, scraping and system actions do not promote entries. Explicit entry/return resets Home to first; ordinary modal closure does not.
- Successful launcher-initiated Android/ROM dispatch is recency data; it never proves that a process is running. The separately approved, optional Shizuku adapter reads process presence only. No active-ROM inference, HUD, analytics, Usage Access, emulator process management or global key interception is introduced.
- Room supplies cached catalog/favorites/recency. DataStore stores small preferences and durable navigation snapshots. Failed discovery/scan never infers deletion from an incomplete result.
- Android keeps notification shade, volume/brightness controls, power menu, and Retroid controls. Immersive handling applies only to this launcher's window.
- Content updates preserve stable IDs and the page's defined selection/anchor policy, including the explicit Home reset and Search-close reset. Grid/List and larger UI scale may adapt card allocation without changing item identity. All controls support touch and controller interaction and semantic unavailable/error states.
- Reused GPL-compatible source and bundled assets/fonts keep required notices and attribution. Reuse is selective and reviewed; it is not permission to copy a different product architecture.

Each completion handoff contains: changed paths; provided contract symbols; screenshots/test commands and actual outcomes; known limitations; pending coordinator delta; and readiness of dependent packets. Update the packet status only when the coordinator accepts that evidence. For dispatch prompts, branch/worktree decisions, and review routing, use [the agent workflow](agent-workflow.md).
