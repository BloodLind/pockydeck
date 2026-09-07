# US-009 — Provide reusable collection, setting and modal layouts

| Field | Value |
| --- | --- |
| Parent feature | [F03 — Reusable controls and debug gallery](../features/F03-controls-gallery.md) |
| Status | Accepted |
| Type | Enabler |
| Implementation agent | `gpt-5.6-luna` / `medium` |

## User story

As a developer, I want shared layout controls with explicit focus boundaries, so that pages can compose readable content and recoverable dialogs without local visual forks.

## Ready when

- [US-008 — Render items and status with stable reusable presentation](US-008-stable-card-and-label-controls.md) is accepted.
- The coordinator has accepted F02 and its integrated evidence, opening F03 work.
- F03 wave 3, its exact lease, validation, and coordinator handoff requirements apply. F03’s completion gate is assessed after its child stories; later features wait for coordinator acceptance.

## Scope

Implement `PageHeading`, `FilterStrip`, `CollectionGrid`, `EmptyState`, and `InlineNotice`; setting rows, toggles, choices, and actions; and `LauncherDialog` with `ItemActionList`. Expose foundation-defined presentation-only modal lifecycle callbacks and verify containment hooks with local fakes.

## Acceptance criteria

- [x] **AC-01** — **Given** an empty or recoverable error collection, **when** it is presented, **then** it explains the state, offers one useful focusable recovery action, and preserves any still-available content.
- [x] **AC-02** — **Given** a setting or layout control receives state and callbacks, **when** it renders, **then** it uses the shared typography and focus treatment without owning page state.
- [x] **AC-03** — **Given** a fixture dialog is opened, **when** control focus moves, **then** focus remains contained until dismissal and presentation lifecycle can be reported without app/domain types.
- [x] **AC-04** — **Given** a layout, settings row, or modal control, **when** it is invoked, **then** it does not navigate, dispatch Android actions, or define its own input owner.

## Verification

| Criterion | Evidence | Status |
| --- | --- | --- |
| AC-01 | [Native recovery/keyed-layout tests](../evidence/F03/US-009-layouts.md) | Passed |
| AC-02 | [Native settings render and actual input tests](../evidence/F03/US-009-layouts.md) | Passed |
| AC-03 | [Six-direction containment, gesture and lifecycle tests](../evidence/F03/US-009-layouts.md) | Passed |
| AC-04 | Separate Terra review and coordinator integration | Passed |

## Delivery notes

F03’s initial lease covers layout, settings, and modal packages with matching tests/examples and evidence. Source coverage: F03 ordered step 3. F05 later binds app input to these hooks; the [F03 packet](../features/F03-controls-gallery.md) retains the ownership and integration rules.

## Out of scope

- Page ViewModels, navigation controllers, and app input binding.
- Reserved primitive-signature changes without coordinator integration.
