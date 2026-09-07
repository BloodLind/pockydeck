# US-007 — Compose accessible activation, filtering and query controls

| Field | Value |
| --- | --- |
| Parent feature | [F03 — Reusable controls and debug gallery](../features/F03-controls-gallery.md) |
| Status | Accepted |
| Type | Enabler |
| Implementation agent | `gpt-5.6-luna` / `medium` |

## User story

As a developer, I want reusable buttons, chips, selectors and query input, so that destinations expose consistent controller and touch interactions.

## Ready when

- [US-006 — Provide accessible visual primitives and focus treatment](US-006-accessible-visual-primitives.md) is accepted.
- The coordinator has accepted F02 and its integrated evidence, opening F03 work.
- F03 wave 3, its exact lease, validation, and coordinator handoff requirements apply. F03’s completion gate is assessed after its child stories; later features wait for coordinator acceptance.

## Scope

Implement `LauncherButton`, `LauncherIconButton`, `FilterChip`, `SortSelector`, and an IME-compatible `SearchField` using F02 primitives. Expose separate activation and focus callbacks using real Compose interaction state. Include local enabled, pressed, unavailable, and selected-versus-focused examples.

## Acceptance criteria

- [x] **AC-01** — **Given** a control receives one physical or touch activation, **when** the event is handled, **then** it invokes one activation callback and moving focus alone invokes none.
- [x] **AC-02** — **Given** one selected filter chip and another focused chip, **when** they render together, **then** selection remains visible and focus receives its separate amber treatment.
- [x] **AC-03** — **Given** an unavailable control with a supplied reason or recovery state, **when** it is presented or activated, **then** that state is exposed and disabled activation produces no action.
- [x] **AC-04** — **Given** `SearchField` enters editing, **when** text is changed, **then** native IME editing is used without navigation, repository, or ViewModel lookup inside the control.

## Verification

| Criterion | Evidence | Status |
| --- | --- | --- |
| AC-01 | Touch, Enter, D-pad center and actual focus-movement instrumentation | Passed |
| AC-02 | Native selected Games / focused Apps fixture plus shared frame pixel checks | Passed |
| AC-03 | Disabled click and reason semantics instrumentation | Passed |
| AC-04 | Public focus requester, native editing and one IME Search callback | Passed |

## Delivery notes

The F03 initial lease covers the controls package and local examples/tests; F02 tokens and shared signatures remain coordinator-controlled. Source coverage: F03 ordered step 1. See [F03](../features/F03-controls-gallery.md) for exact owned paths and handoff rules.

## Out of scope

- Physical key normalization, page filtering, and search business rules.
- A custom keyboard or app-specific control dependencies.
