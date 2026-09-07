# US-005 — Calculate shared native shell and card metrics

| Field | Value |
| --- | --- |
| Parent feature | [F02 — Theme, metrics, and visual primitives](../features/F02-visual-foundations.md) |
| Status | Planned |
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

- [ ] **AC-01** — **Given** standard and compact window fixtures, **when** their metrics are calculated, **then** each receives coherent content bounds from one metrics source.
- [ ] **AC-02** — **Given** a normal destination uses the shell, **when** its content density adapts, **then** it does not independently move shared shell anchors.
- [ ] **AC-03** — **Given** a focused card or larger-font fixture, **when** it renders, **then** focus outline/lift remains inside reserved bounds and square artwork and usable controls remain available.
- [ ] **AC-04** — **Given** reference proportions are recorded, **when** metrics are reviewed, **then** they are described as initial guidance and physical Flip 2 calibration is still identified as pending.

## Verification

| Criterion | Evidence | Status |
| --- | --- | --- |
| AC-01 | Planned standard and compact metrics preview | Not run |
| AC-02 | Planned multi-destination shell fixture comparison | Not run |
| AC-03 | Planned focused-card and large-font preview | Not run |
| AC-04 | Planned calibration-assumption record | Not run |

## Delivery notes

The F02 creation lease covers `ShellMetrics`, related theme metrics, and isolated previews/evidence; signatures become centrally reserved after acceptance. Source coverage: F02 ordered step 3 and step 5 (standard, compact, and large-font metrics previews). Do not state invented geometry constants, performance targets, or device-fidelity results.

## Out of scope

- Screen-specific shell dimensions or whole-UI bitmap scaling.
- Invented performance or device-fidelity claims.
