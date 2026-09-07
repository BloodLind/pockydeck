# Handheld Launcher — user-story backlog

**53 planned user stories under F01–F18. No application implementation or acceptance test has been completed by creating this backlog.**

This is the implementation dispatch list. Each linked file contains one bounded story, a user/developer/tester statement, numbered Given/When/Then acceptance criteria, dependencies, exclusions, verification evidence, and a link to its parent feature's technical ownership.

## How to use this backlog

1. Select one ready US. Its direct story dependencies and the prerequisite-feature/wave gates must be satisfied. The owning feature's final integration gate follows its children; it does not block starting those same stories.
2. Assign the story's implementation model and effort. Send the US file, its parent packet, the relevant contracts, and the [agent workflow](../agent-workflow.md) to the worker.
3. Use one active story writer per parent feature. The existing schedule permits up to three workers across ready features; smaller stories do not grant parallel write access to shared files.
4. Review each acceptance criterion against actual evidence. Code integration alone does not mean acceptance if required device checks remain pending.
5. Accept the parent Fxx only after all its stories and its integration gate pass.

Story IDs are stable references, not execution order. For example, US-014 depends on US-019 because controller behavior consumes the persistence feature. After F01 is accepted, the visual branch (US-004 onward) and persistence branch (US-017 onward) can follow the existing parallel wave schedule.

Begin with [US-001](US-001-installable-four-module-foundation.md). Use the [feature plan](../feature-plan.md) for wave order, shared-file ownership and integration gates. Use the [story standard](story-standard.md) for acceptance/evidence rules.

The [story map](story-map.json) records each story's scope, dependency IDs and coverage of the original feature steps. Astra Ultra produced the decomposition; the individual stories retain the lower-cost implementation assignments from their parent features.

## Types and completion

- **Feature:** a useful behavior for the launcher user.
- **Enabler:** a concrete capability for the developer, such as a working build or durable catalog.
- **Gate:** an integrated verification or delivery outcome for a tester/owner.

All criteria start unchecked. A story is accepted only when its required criteria and applicable delivery checks have evidence. Report failed, pending and not-run checks explicitly. Physical Flip 2 behavior, controller/IME compatibility, ROM/emulator support and native display calibration require the actual evidence specified by the story.

The last four F18 stories share F17 as a prerequisite. One F18 owner may prepare the APK and guide while physical lifecycle, display or performance checks await the device. Delivering those artifacts does not close the outstanding physical gates.

## Readiness inputs already identified

ROM platform/format and grouping choices, the supported emulator/version/URI matrix, and the first metadata provider's access/terms/data handling remain readiness inputs in their relevant stories. They do not block earlier native-app stories. No new product choice was invented during this split.

Performance limits must follow recorded measurement and explicit acceptance. Reference screenshot/CSS dimensions require native calibration; no new pixel tolerance or latency threshold is assumed.

## Stories by parent feature

### F01 — Foundation and shared contracts

Parent: [F01 technical packet](../features/F01-foundation.md). Implementation agent: `gpt-5.6-sol` / `high`.

| Story | Outcome | Type | Direct story dependencies |
|---|---|---|---|
| [US-001](US-001-installable-four-module-foundation.md) | Create an installable four-module launcher foundation | enabler | None; initial readiness applies |
| [US-002](US-002-stable-catalog-and-launch-contracts.md) | Define stable catalog, persistence and launch contracts | enabler | [US-001](US-001-installable-four-module-foundation.md) |
| [US-003](US-003-shared-navigation-input-and-presentation-ports.md) | Publish shared navigation, input and presentation ports | enabler | [US-002](US-002-stable-catalog-and-launch-contracts.md) |

### F02 — Visual foundations

Parent: [F02 technical packet](../features/F02-visual-foundations.md). Implementation agent: `gpt-5.6-luna` / `medium`.

| Story | Outcome | Type | Direct story dependencies |
|---|---|---|---|
| [US-004](US-004-shared-home-theme.md) | Express Home's visual language as a shared native theme | enabler | [US-003](US-003-shared-navigation-input-and-presentation-ports.md) |
| [US-005](US-005-native-shell-and-card-metrics.md) | Calculate shared native shell and card metrics | enabler | [US-004](US-004-shared-home-theme.md) |
| [US-006](US-006-accessible-visual-primitives.md) | Provide accessible visual primitives and focus treatment | enabler | [US-005](US-005-native-shell-and-card-metrics.md) |

### F03 — Reusable controls and gallery

Parent: [F03 technical packet](../features/F03-controls-gallery.md). Implementation agent: `gpt-5.6-luna` / `medium`.

| Story | Outcome | Type | Direct story dependencies |
|---|---|---|---|
| [US-007](US-007-reusable-input-controls.md) | Compose accessible activation, filtering and query controls | enabler | [US-006](US-006-accessible-visual-primitives.md) |
| [US-008](US-008-stable-card-and-label-controls.md) | Render items and status with stable reusable presentation | enabler | [US-007](US-007-reusable-input-controls.md) |
| [US-009](US-009-reusable-content-and-modal-layouts.md) | Provide reusable collection, setting and modal layouts | enabler | [US-008](US-008-stable-card-and-label-controls.md) |
| [US-010](US-010-production-control-debug-gallery.md) | Inspect production controls in a deterministic debug gallery | enabler | [US-009](US-009-reusable-content-and-modal-layouts.md) |

### F04 — Persistent shell and navigation

Parent: [F04 technical packet](../features/F04-shell-navigation.md). Implementation agent: `gpt-5.6-terra` / `high`.

| Story | Outcome | Type | Direct story dependencies |
|---|---|---|---|
| [US-011](US-011-persistent-home-shaped-shell.md) | Keep a single Home-shaped shell around launcher content | feature | [US-010](US-010-production-control-debug-gallery.md) |
| [US-012](US-012-destination-and-origin-navigation.md) | Switch destinations and return to the correct origin | feature | [US-011](US-011-persistent-home-shaped-shell.md) |
| [US-013](US-013-shell-keyboard-and-inset-layout.md) | Keep the shell usable when the keyboard or system bars appear | feature | [US-012](US-012-destination-and-origin-navigation.md) |

### F05 — Controller input and restoration

Parent: [F05 technical packet](../features/F05-controller-restoration.md). Implementation agent: `gpt-5.6-sol` / `high`.

| Story | Outcome | Type | Direct story dependencies |
|---|---|---|---|
| [US-014](US-014-single-owner-controller-input.md) | Translate controller and touch actions through one input owner | feature | [US-013](US-013-shell-keyboard-and-inset-layout.md), [US-019](US-019-durable-preferences-and-navigation-snapshots.md) |
| [US-015](US-015-page-and-modal-focus-navigation.md) | Move focus across page regions and contained dialogs | feature | [US-014](US-014-single-owner-controller-input.md) |
| [US-016](US-016-identity-and-scroll-restoration.md) | Restore each destination by item identity and scroll anchor | feature | [US-015](US-015-page-and-modal-focus-navigation.md) |

### F06 — Persistence

Parent: [F06 technical packet](../features/F06-persistence.md). Implementation agent: `gpt-5.6-sol` / `high`.

| Story | Outcome | Type | Direct story dependencies |
|---|---|---|---|
| [US-017](US-017-durable-catalog-and-inventory-cache.md) | Persist a cache that survives catalog reconciliation | enabler | [US-003](US-003-shared-navigation-input-and-presentation-ports.md) |
| [US-018](US-018-durable-successful-open-order.md) | Record successful opens once in deterministic recent order | enabler | [US-017](US-017-durable-catalog-and-inventory-cache.md) |
| [US-019](US-019-durable-preferences-and-navigation-snapshots.md) | Persist controller preferences and small navigation snapshots | enabler | [US-018](US-018-durable-successful-open-order.md) |

### F07 — Android discovery and launching

Parent: [F07 technical packet](../features/F07-android-catalog.md). Implementation agent: `gpt-5.6-sol` / `high`.

| Story | Outcome | Type | Direct story dependencies |
|---|---|---|---|
| [US-020](US-020-real-android-component-discovery.md) | Discover real launchable Android components | enabler | [US-019](US-019-durable-preferences-and-navigation-snapshots.md) |
| [US-021](US-021-cache-first-android-reconciliation.md) | Reconcile app changes without blocking cached browsing | enabler | [US-020](US-020-real-android-component-discovery.md) |
| [US-022](US-022-safe-android-component-dispatch.md) | Dispatch revalidated Android targets into external tasks | enabler | [US-021](US-021-cache-first-android-reconciliation.md) |

### F08 — Working HOME loop

Parent: [F08 technical packet](../features/F08-home-loop.md). Implementation agent: `gpt-5.6-sol` / `high`.

| Story | Outcome | Type | Direct story dependencies |
|---|---|---|---|
| [US-023](US-023-cached-home-carousel.md) | Browse cached Home items in the shared carousel | feature | [US-016](US-016-identity-and-scroll-restoration.md), [US-022](US-022-safe-android-component-dispatch.md) |
| [US-024](US-024-acknowledged-home-launch-and-return.md) | Open an item once and return with it selected first | feature | [US-023](US-023-cached-home-carousel.md) |
| [US-025](US-025-android-home-role-and-window-loop.md) | Use the launcher as HOME while retaining native system controls | feature | [US-024](US-024-acknowledged-home-launch-and-return.md) |

### F09 — Library and Apps

Parent: [F09 technical packet](../features/F09-library-apps.md). Implementation agent: `gpt-5.6-terra` / `high`.

| Story | Outcome | Type | Direct story dependencies |
|---|---|---|---|
| [US-026](US-026-unified-library-browsing.md) | Browse, filter and sort the unified Library | feature | [US-025](US-025-android-home-role-and-window-loop.md) |
| [US-027](US-027-android-app-category-browsing.md) | Browse installed apps by honest category | feature | [US-026](US-026-unified-library-browsing.md) |

### F10 — Favorites and details

Parent: [F10 technical packet](../features/F10-favorites-details.md). Implementation agent: `gpt-5.6-terra` / `high`.

| Story | Outcome | Type | Direct story dependencies |
|---|---|---|---|
| [US-028](US-028-favorite-collection-management.md) | Keep and browse favorite references | feature | [US-025](US-025-android-home-role-and-window-loop.md) |
| [US-029](US-029-item-details-and-origin-return.md) | Inspect item details and return to the initiating place | feature | [US-028](US-028-favorite-collection-management.md) |

### F11 — Local Search

Parent: [F11 technical packet](../features/F11-search.md). Implementation agent: `gpt-5.6-terra` / `high`.

| Story | Outcome | Type | Direct story dependencies |
|---|---|---|---|
| [US-030](US-030-local-search-results.md) | Find indexed items with local query and category filters | feature | [US-025](US-025-android-home-role-and-window-loop.md) |
| [US-031](US-031-native-keyboard-search-editing.md) | Edit Search with the native keyboard and controller | feature | [US-030](US-030-local-search-results.md) |
| [US-032](US-032-search-actions-and-origin-restoration.md) | Act on Search results and restore the correct origin | feature | [US-031](US-031-native-keyboard-search-editing.md) |

### F12 — Settings

Parent: [F12 technical packet](../features/F12-settings.md). Implementation agent: `gpt-5.6-terra` / `high`.

| Story | Outcome | Type | Direct story dependencies |
|---|---|---|---|
| [US-033](US-033-controller-mapping-settings.md) | Configure confirm and Back mapping in Settings | feature | [US-027](US-027-android-app-category-browsing.md), [US-029](US-029-item-details-and-origin-return.md), [US-032](US-032-search-actions-and-origin-restoration.md) |
| [US-034](US-034-default-home-settings.md) | Reach Android default-HOME setup from Settings | feature | [US-033](US-033-controller-mapping-settings.md) |
| [US-035](US-035-readability-and-supported-system-settings.md) | Access supported readability and system settings | feature | [US-034](US-034-default-home-settings.md) |

### F13 — Device status

Parent: [F13 technical packet](../features/F13-device-status.md). Implementation agent: `gpt-5.6-sol` / `high`.

| Story | Outcome | Type | Direct story dependencies |
|---|---|---|---|
| [US-036](US-036-baseline-device-status.md) | Display lifecycle-aware battery, clock and connectivity | feature | [US-025](US-025-android-home-role-and-window-loop.md) |
| [US-037](US-037-honest-optional-device-status.md) | Expose optional status only when its meaning is supported | feature | [US-036](US-036-baseline-device-status.md) |

### F14 — Destination integration

Parent: [F14 technical packet](../features/F14-destination-integration.md). Implementation agent: `gpt-5.6-sol` / `high`.

| Story | Outcome | Type | Direct story dependencies |
|---|---|---|---|
| [US-038](US-038-integrated-destination-journey-gate.md) | Verify the six destinations operate as one launcher | gate | [US-027](US-027-android-app-category-browsing.md), [US-029](US-029-item-details-and-origin-return.md), [US-032](US-032-search-actions-and-origin-restoration.md), [US-035](US-035-readability-and-supported-system-settings.md), [US-037](US-037-honest-optional-device-status.md) |
| [US-039](US-039-native-lifecycle-and-catalog-gate.md) | Verify native launch, lifecycle and catalog resilience together | gate | [US-038](US-038-integrated-destination-journey-gate.md) |

### F15 — ROM indexing

Parent: [F15 technical packet](../features/F15-rom-index.md). Implementation agent: `gpt-5.6-sol` / `high`.

| Story | Outcome | Type | Direct story dependencies |
|---|---|---|---|
| [US-040](US-040-approved-rom-source-and-identity-contract.md) | Establish the approved ROM identity and source contract | enabler | [US-039](US-039-native-lifecycle-and-catalog-gate.md) |
| [US-041](US-041-rom-folder-grants-and-recovery.md) | Connect ROM folders and recover unavailable access | feature | [US-040](US-040-approved-rom-source-and-identity-contract.md) |
| [US-042](US-042-incremental-rom-indexing.md) | Maintain the ROM index through complete incremental scans | feature | [US-041](US-041-rom-folder-grants-and-recovery.md) |
| [US-043](US-043-rom-catalog-destination-integration.md) | Browse indexed ROMs through existing launcher destinations | feature | [US-042](US-042-incremental-rom-indexing.md) |

### F16 — Emulator launching

Parent: [F16 technical packet](../features/F16-emulator-launch.md). Implementation agent: `gpt-5.6-sol` / `high`.

| Story | Outcome | Type | Direct story dependencies |
|---|---|---|---|
| [US-044](US-044-supported-emulator-resolution.md) | Resolve supported emulator capabilities and saved choices | enabler | [US-043](US-043-rom-catalog-destination-integration.md) |
| [US-045](US-045-supported-rom-launch.md) | Launch a supported ROM through the common open pipeline | feature | [US-044](US-044-supported-emulator-resolution.md) |
| [US-046](US-046-emulator-default-and-item-choice.md) | Choose default and item-specific emulators | feature | [US-045](US-045-supported-rom-launch.md) |

### F17 — Metadata and artwork

Parent: [F17 technical packet](../features/F17-metadata-artwork.md). Implementation agent: `gpt-5.6-sol` / `high`.

| Story | Outcome | Type | Direct story dependencies |
|---|---|---|---|
| [US-047](US-047-durable-metadata-enrichment.md) | Enrich metadata through a durable bounded provider queue | enabler | [US-046](US-046-emulator-default-and-item-choice.md) |
| [US-048](US-048-progressive-cached-artwork.md) | Show cached artwork progressively without moving items | feature | [US-047](US-047-durable-metadata-enrichment.md) |
| [US-049](US-049-manual-metadata-and-artwork-correction.md) | Correct metadata matches and artwork without losing overrides | feature | [US-048](US-048-progressive-cached-artwork.md) |

### F18 — Device hardening and delivery

Parent: [F18 technical packet](../features/F18-device-delivery.md). Implementation agent: `gpt-5.6-sol` / `high`.

| Story | Outcome | Type | Direct story dependencies |
|---|---|---|---|
| [US-050](US-050-physical-lifecycle-and-regression-gate.md) | Verify final integrated and physical lifecycle resilience | gate | [US-049](US-049-manual-metadata-and-artwork-correction.md) |
| [US-051](US-051-flip2-native-visual-calibration.md) | Calibrate and accept native Home geometry on Flip 2 | gate | [US-049](US-049-manual-metadata-and-artwork-correction.md) |
| [US-052](US-052-measured-device-performance-gate.md) | Measure responsiveness and set evidence-based budgets | gate | [US-049](US-049-manual-metadata-and-artwork-correction.md) |
| [US-053](US-053-installable-artifact-and-recovery-guide.md) | Deliver an installable artifact and setup recovery guide | gate | [US-049](US-049-manual-metadata-and-artwork-correction.md) |

## Traceability

The [story map](story-map.json) records every story's parent, scope, dependency links, implementation settings and source-step coverage. The individual Markdown story is the reviewable acceptance document. Maintain the map, story and parent technical packet together when an approved change affects them; a worker must not silently widen any of them.
