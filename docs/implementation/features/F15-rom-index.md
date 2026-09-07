# F15 — ROM sources and incremental indexing

- Status: **planned**.
- User stories: [US-040](../user-stories/US-040-approved-rom-source-and-identity-contract.md), [US-041](../user-stories/US-041-rom-folder-grants-and-recovery.md), [US-042](../user-stories/US-042-incremental-rom-indexing.md), [US-043](../user-stories/US-043-rom-catalog-destination-integration.md). Dispatch one ready story; this packet remains the technical ownership and feature-gate reference.
- Agent: `gpt-5.6-sol`, reasoning `high`.
- Outcome: select ROM folders once, browse their indexed games, and retain recoverable data when storage disappears or a scan fails.
- Prerequisites: F14 accepted; recorded first-platform/format scope and multi-file/disc grouping rules. If those choices remain materially ambiguous, prepare a small recommendation and obtain the user's decision before dependent implementation.

## Ownership and contracts

Owns `core/data/src/main/kotlin/dev/handheld/launcher/core/data/rom/source/**`, `core/data/src/main/kotlin/dev/handheld/launcher/core/data/rom/scan/**`, `core/data/src/main/kotlin/dev/handheld/launcher/core/data/rom/repository/**`, `app/src/main/kotlin/dev/handheld/launcher/feature/settings/sources/**`; matching data/app JVM/instrumentation test packages; `docs/implementation/evidence/F15/**`. F12's original Settings lease is closed; this is an explicit new subpackage lease. SAF Activity requests, domain ROM/source contracts, Room schema/DAOs/migrations, WorkManager/dependencies, manifests, DI and settings-section registration remain coordinator-owned.

Consumes the unified catalog and source-availability contracts, F12 settings extension slot and platform request port. Provides persisted user-selected source handling, source health, incremental scan batches, stable ROM identity mappings, and a functional source-settings section. Before code consumes new contracts, coordinator accepts/publishes their exact signatures and migration/preservation behavior.

## Ordered steps

1. Prepare a concrete initial support matrix: platforms, recognized formats, archive policy, multi-file/disc grouping and unresolved classification behavior. Do not treat all extensions as equally launchable. Confirm material choices before committing to identity/data behavior.
2. Submit a bounded domain/schema/SAF request proposal. Use generated stable item IDs plus source-root/document identity, not bare filenames. Keep user classification and artwork/favorite/recency references separate. Define root changes and duplicate-root handling without silently deleting or merging user records.
3. Implement SAF tree selection and persist granted access through acknowledged Activity requests. URI grants are document access, not raw filesystem paths. Revoked grants, removed SD cards and provider errors mark the source unavailable with a recoverable action.
4. Implement off-main-thread incremental enumeration and transactionally committed completed batches. Reconcile copied/removed files after foreground startup/resume and supported change signals; coalesce triggers. SAF does not promise immediate recursive notifications, so do not advertise instant monitoring.
5. Apply platform/format inference only when deterministic; expose unresolved cases for user selection and remember mappings. Collapse validated multi-file/disc sets into the agreed launch entry and preserve stable identity across rescans.
6. Provide source setup/health/recovery UI and integrate catalog rows through the common Library/Search/Favorites/Home contracts. Before F16, ROMs may show an explicit no-compatible-emulator/unresolved state; never report an untested launch as supported.
7. Add durable deferrable reconciliation using WorkManager where useful through coordinator dependency/wiring changes. Foreground browsing and launch cannot depend on a future periodic run. Removing a source configuration never deletes underlying ROM files; apply approved reference-retention rules.

## Excludes

No broad filesystem permission by default, treating partial scans as deletion, raw-path assumptions from SAF URIs, emulator dispatch, BIOS/save management, cloud sync or manual scanning as the normal maintenance workflow.

## Validation and handoff

Test grant persistence, revoked permission/SD removal, cancellation mid-scan, failed provider enumeration, copied/removed files, duplicate names in different roots, multi-file/disc grouping and ambiguous platform correction. Use instrumented document-provider fixtures and actual selected storage where available; reboot/grant and SD-removal proof must come from device testing. Migration tests must preserve Android catalog/favorites/recency. Deliver source/format support matrix, schema/contract version, recovery evidence and unresolved launch capabilities to F16.
