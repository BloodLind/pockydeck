# US-006 — Provide accessible visual primitives and focus treatment

| Field | Value |
| --- | --- |
| Parent feature | [F02 — Theme, metrics, and visual primitives](../features/F02-visual-foundations.md) |
| Status | Planned |
| Type | Enabler |
| Implementation agent | `gpt-5.6-luna` / `medium` |

## User story

As a developer, I want state-driven text, icon, surface, focus and glyph primitives, so that later controls share predictable semantics and focus visuals.

## Ready when

- [US-005 — Calculate shared native shell and card metrics](US-005-native-shell-and-card-metrics.md) is accepted.
- The coordinator has accepted F01 and its integrated evidence, opening F02 work.
- F02 wave 2, its exact lease, validation, and coordinator handoff requirements apply. F02’s completion gate is assessed after its child stories; later features wait for coordinator acceptance.

## Scope

Implement `LauncherText`, `LauncherIcon`, `LauncherSurface`, `FocusFrame`, and semantic glyph rendering. Keep selection, actual focus, pressed, and unavailable states distinct, including decorative-content semantics. Render isolated long-text and unavailable examples and publish initial APIs and assumptions for control and shell consumers.

## Acceptance criteria

- [ ] **AC-01** — **Given** a focused control whose destination or filter is selected, **when** it renders, **then** actual focus has one immediate visible treatment independent of selection and decorative motion does not delay it.
- [ ] **AC-02** — **Given** text, icon, and decorative primitives, **when** accessibility semantics are exposed, **then** meaningful content is announced and decoration is not a misleading focus target.
- [ ] **AC-03** — **Given** long text or an unavailable value, **when** it renders, **then** it remains readable and a supported touch target can reach 48dp without overlapping an adjacent target.
- [ ] **AC-04** — **Given** an isolated primitive preview, **when** it runs, **then** it needs no repository, sensor, or product-module import and records unresolved calibration evidence.

## Verification

| Criterion | Evidence | Status |
| --- | --- | --- |
| AC-01 | Planned focused/selected state preview and interaction test | Not run |
| AC-02 | Planned accessibility semantics inspection | Not run |
| AC-03 | Planned long-text, unavailable, and touch-target preview | Not run |
| AC-04 | Planned isolated compile/render evidence and API inventory | Not run |

## Delivery notes

The F02 creation lease covers foundation/glyph packages, their matching tests/previews, and evidence; no build dependency is added without a coordinator delta. Source coverage: F02 ordered step 4, step 5 (primitive state/text previews), and step 6 plus validation/handoff. Use the [F02 packet](../features/F02-visual-foundations.md) for the exact lease.

## Out of scope

- Product cards, shell navigation, and sensor readings.
- Tests that merely restate token constants.
