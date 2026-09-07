# F03 — Reusable controls and debug gallery

- Status: **accepted**. All four children and the [combined feature gate](../evidence/F03/F03-F07-final-gate.md) passed.
- User stories: [US-007](../user-stories/US-007-reusable-input-controls.md), [US-008](../user-stories/US-008-stable-card-and-label-controls.md), [US-009](../user-stories/US-009-reusable-content-and-modal-layouts.md), [US-010](../user-stories/US-010-production-control-debug-gallery.md). Dispatch one ready story; this packet remains the technical ownership and feature-gate reference.
- Agent: `gpt-5.6-luna`, reasoning `medium`.
- Outcome: every destination can compose the same accessible buttons, cards, settings rows, and dialogs; developers can inspect their states before live data is connected.
- Prerequisite: F02 accepted. Runs independently beside F07.

## Ownership and contracts

Initial-creation lease: `core/designsystem/src/main/kotlin/dev/handheld/launcher/core/designsystem/{controls,cards,layout,settings,modal}/**`; matching design-system JVM/instrumentation test packages; `app/src/debug/kotlin/dev/handheld/launcher/catalog/**`; `app/src/debug/res/drawable/**`; `docs/implementation/evidence/F03/**`. Coordinator wires a debug entry point and any build/resource dependency. Do not edit F02 tokens or production shell files.

Consumes F02 primitives and [the control inventory](../../design-system.md). Provides the named input/label/card/collection/settings/modal families; generic artwork slots and explicit callbacks; a component gallery with deterministic local assets. API additions after acceptance require coordinator integration. Domain-to-card mapping is F08's responsibility.

## Ordered steps

1. Implement buttons/icon buttons, chips, sort selector, and system-IME-compatible search field using real Compose focus/interaction state. Keep activation and focus callbacks distinct.
2. Implement badges/status presentation, cover/app/search-result cards, artwork fallback, and bounded captions. Use semantic `HomeCover`, `CollectionCover`, and `AppIcon` variants with one focus treatment. App icons fit; cover artwork may crop without stretching.
3. Implement page heading/filter strip/grid, empty state/inline notice, settings rows, and launcher-local dialog/action-list layout. Expose presentation-only modal focus lifecycle callbacks/containment boundaries defined at foundation: the design system imports no app/domain type and controls do not navigate themselves. Verify these hooks with local fakes now; F05 later binds the app input scope.
4. Build the debug gallery from production controls, with fixture-only Home content and other composition examples. Include focused/default/pressed/unavailable, selected-versus-focused, missing/failed artwork, long labels, large counts, status variants, empty collections, and dialogs.
5. Add an IME-constrained preview and native-window size variants. Maintain fixed image bounds during loading/failure and bounded titles without shrinking fonts to fit.
6. Publish reusable signatures and fixture entry points to F04/F08. Request any needed foundational API change centrally rather than duplicating a primitive.

## Excludes

No repositories, navigation controllers, ViewModels in controls, network images as preview dependencies, emulator concepts, separate per-page card implementations, or `RunningBadge`.

## Validation and handoff

Build and open the gallery. Use focused Compose interaction tests for no duplicate activation, disabled/unavailable behavior, and selected state independent of focus. Render evidence for representative states and identify any clipping or readability issue before handing off. The complete gallery is visual evidence, not a claim of actual device calibration. Provide the component inventory and APIs to F04; coordinator integrates the debug route separately.
