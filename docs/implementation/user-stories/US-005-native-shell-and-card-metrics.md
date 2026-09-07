# US-005 — Calculate shared native shell and card metrics

| Field | Value |
| --- | --- |
| Parent feature | [F02 — Theme, metrics, and visual primitives](../features/F02-visual-foundations.md) |
| Status | Accepted |
| Type | Enabler |
| Implementation agent | `gpt-5.6-luna` / `medium` |

## User story

As a developer, I want one metrics calculation from the actual available window, so that all destinations fit the same Home-derived shell across supported layout constraints.

## Ready when

- [US-004 — Express Home's visual language as a shared native theme](US-004-shared-home-theme.md) is accepted.
- The coordinator has accepted F01 and its integrated evidence, opening F02 work.
- F02 wave 2, its exact lease, validation, and coordinator handoff requirements apply. F02’s completion gate is assessed after its child stories; later features wait for coordinator acceptance.

## Scope

Define root-computed `ShellMetrics` from bounds, density, and font scale, covering status, content, dock, footer, gutter, and Home-card geometry. Reserve frame/lift space and make a compact-layout decision that preserves readable text and square artwork. Document approximate reference proportions and unresolved physical calibration assumptions.

## Acceptance criteria

- [x] **AC-01** — **Given** standard and compact window fixtures, **when** their metrics are calculated, **then** each receives coherent content bounds from one metrics source.
- [x] **AC-02** — **Given** a normal destination uses the shell, **when** its content density adapts, **then** it does not independently move shared shell anchors.
- [x] **AC-03** — **Given** a focused card or larger-font fixture, **when** it renders, **then** focus outline/lift remains inside reserved bounds and square artwork and usable controls remain available.
- [x] **AC-04** — **Given** reference proportions are recorded, **when** metrics are reviewed, **then** they are described as initial guidance and physical Flip 2 calibration is still identified as pending.

## Verification

| Criterion | Evidence | Status |
| --- | --- | --- |
| AC-01 | [Accepted evidence](../evidence/F02/US-005-metrics.md) | Passed |
| AC-02 | [Accepted evidence](../evidence/F02/US-005-metrics.md) | Passed |
| AC-03 | [Accepted evidence](../evidence/F02/US-005-metrics.md) | Passed |
| AC-04 | [Accepted evidence](../evidence/F02/US-005-metrics.md) | Passed |

## Delivery notes

The F02 creation lease covers `ShellMetrics`, related theme metrics, and isolated previews/evidence; signatures become centrally reserved after acceptance. Source coverage: F02 ordered step 3 and step 5 (standard, compact, and large-font metrics previews). Do not state invented geometry constants, performance targets, or device-fidelity results.

## Out of scope

- Screen-specific shell dimensions or whole-UI bitmap scaling.
- Invented performance or device-fidelity claims.

Coordinator acceptance: 8 September 2026; independent review passed. See linked evidence for actual commands, tests and limitations.
