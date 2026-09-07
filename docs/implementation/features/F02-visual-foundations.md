# F02 — Theme, metrics, and visual primitives

- Status: **accepted**, 8 September 2026; US-004/005/006 and the [combined feature gate](../evidence/F02/F02-F06-final-gate.md) passed. Physical calibration remains pending.
- User stories: [US-004](../user-stories/US-004-shared-home-theme.md), [US-005](../user-stories/US-005-native-shell-and-card-metrics.md), [US-006](../user-stories/US-006-accessible-visual-primitives.md). Dispatch one ready story; this packet remains the technical ownership and feature-gate reference.
- Agent: `gpt-5.6-luna`, reasoning `medium`.
- Outcome: Home's visual language is expressed once as reusable native tokens and layout metrics.
- Prerequisite: F01 accepted. Runs independently beside F06.

## Ownership and contracts

Initial-creation lease: `core/designsystem/src/main/kotlin/dev/handheld/launcher/core/designsystem/theme/**`, `core/designsystem/src/main/kotlin/dev/handheld/launcher/core/designsystem/foundation/**`, `core/designsystem/src/main/kotlin/dev/handheld/launcher/core/designsystem/glyphs/**`, `core/designsystem/src/main/res/font/**`, `core/designsystem/src/main/assets/font-notices/**`; matching design-system `src/test/kotlin` and `src/androidTest/kotlin` packages; `docs/implementation/evidence/F02/**`. Propose any new dependency/build change to F01's integration owner.

Consumes the Home image and typography/geometry specification in [design-system.md](../../design-system.md). Provides `LauncherTheme`, typed colors/type/spacing/shapes/depth/motion, `ShellMetrics`, `LauncherText`, `LauncherIcon`, `LauncherSurface`, `FocusFrame`, and semantic glyph primitives. Final names/signatures are registered in `contracts.md` by the coordinator. These shared surfaces become reserved after acceptance.

## Ordered steps

1. Inspect the actual [Home reference](../../references/home.png) and its app-content crop. Separate editor surroundings from launcher content. Record proportions and density assumptions; CSS units are calibration references, not native dp values.
2. Implement semantic tokens with one gradient recipe and Plus Jakarta Sans bundled locally with its font notice. Use explicit weight 800 for the heavy style initially; respect system font scale. Features must not need literal colors, radii, or font sizes.
3. Define one root-computed `ShellMetrics` from available bounds, density, and font scale. Include status/content/dock/footer bounds and Home card dimensions; reserve space for focus frame/lift. Provide a compact-layout decision without scaling the entire UI bitmap.
4. Implement the four primitives and glyph rendering. Separate destination/filter selection, actual focus, pressed, and disabled state. Expose semantics and decorative-content handling. Keep focus decoration immediate with optional restrained motion.
5. Add isolated previews for standard/compact metrics, long text, large font scale, and unsupported values. Do not read repositories or Android sensor state from visual primitives.
6. Publish the initial APIs and measured assumptions for F03/F04. Central calibration later revises tokens through the coordinator; do not lock unverified measurements as accepted device defaults.

## Excludes

No shell composition, screens, domain dependencies, ViewModels, icon/artwork discovery, sensor readings, or invented controls for HUD/process management.

## Validation and handoff

Build the design system and render representative previews. Verify focus decoration fits allocated bounds, semantic text remains readable, supported touch targets can reach 48dp without overlap, and primitive APIs remain state-driven. Save Home crop/measurement notes and available screenshots, explicitly marking physical-density calibration pending. Avoid tests that merely repeat color or spacing constants. Hand off token/metric signatures and font attribution to F03; notify the coordinator that later edits require the reserved-file process.
