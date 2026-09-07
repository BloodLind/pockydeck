# US-004 — Express Home's visual language as a shared native theme

| Field | Value |
| --- | --- |
| Parent feature | [F02 — Theme, metrics, and visual primitives](../features/F02-visual-foundations.md) |
| Status | Planned |
| Type | Enabler |
| Implementation agent | `gpt-5.6-luna` / `medium` |

## User story

As a developer, I want Home-derived semantic theme tokens and local typography, so that screens share one visual language without duplicating literal styling.

## Ready when

- [US-003 — Publish shared navigation, input and presentation ports](US-003-shared-navigation-input-and-presentation-ports.md) is accepted.
- The coordinator has accepted F01 and its integrated evidence, opening F02 work.
- F02 wave 2, its exact lease, validation, and coordinator handoff requirements apply. F02’s completion gate is assessed after its child stories; later features wait for coordinator acceptance.

## Scope

Inspect the actual Home app-content crop and record reference and density assumptions. Implement `LauncherTheme` colors, typography, spacing, shapes, depth, motion, and one gradient recipe. Bundle Plus Jakarta Sans with its required notice, begin the heavy style at explicit weight 800, and support system font scaling.

## Acceptance criteria

- [ ] **AC-01** — **Given** the Home reference is documented, **when** it is used for comparison, **then** editor surroundings are excluded and Home is identified as the sole shell-geometry authority.
- [ ] **AC-02** — **Given** a visual consumer, **when** it requests color, type, or spacing, **then** it can use semantic theme roles without repeating visual literals and essential text remains readable after compositing.
- [ ] **AC-03** — **Given** the application is offline, **when** a themed screen renders, **then** bundled Plus Jakarta Sans and its required notice are available.
- [ ] **AC-04** — **Given** system font scaling or reduced-motion intent, **when** the theme is rendered, **then** it respects that setting without fixed bitmap scaling.

## Verification

| Criterion | Evidence | Status |
| --- | --- | --- |
| AC-01 | Planned reference-crop record and manual comparison | Not run |
| AC-02 | Planned theme consumer preview and contrast review | Not run |
| AC-03 | Planned offline render and notice review | Not run |
| AC-04 | Planned font-scale and reduced-motion preview | Not run |

## Delivery notes

The F02 one-time creation lease covers theme files, font resources/notices, and evidence; token changes after integration require the coordinator. Source coverage: F02 ordered steps 1–2. Final native dp/sp calibration and physical Flip 2 evidence remain pending; CSS units are not copied into Android dimensions.

## Out of scope

- Shell composition, live data, and final device-calibrated dp/sp values.
- Direct copying of CSS units into Android dimensions.
