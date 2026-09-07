# US-011 — Keep a single Home-shaped shell around launcher content

| Field | Value |
| --- | --- |
| Parent feature | [F04 — Persistent shell and navigation](../features/F04-shell-navigation.md) |
| Status | In progress |
| Type | Feature |
| Implementation agent | `gpt-5.6-terra` / `high` |

## User story

As a launcher user, I want a stable frame and truthful contextual prompts around every destination, so that navigation and actions stay in familiar locations.

## Ready when

- [US-010 — Inspect production controls in a deterministic debug gallery](US-010-production-control-debug-gallery.md) is accepted.
- The coordinator has accepted F03 and its integrated evidence, opening F04 work.
- F04 wave 4, its exact lease, validation, and coordinator handoff requirements apply. F04’s completion gate is assessed after its child stories; later features wait for coordinator acceptance.

## Scope

Compose backdrop, status, a single content slot, dock, footer, and overlay host once using root metrics. Render the contextual footer from the shared action descriptor. Show unavailable or unsupported status until live providers exist.

## Acceptance criteria

- [ ] **AC-01** — **Given** normal route or content changes, **when** the shell renders them, **then** every destination uses the same content rectangle, gutter, and shell anchors and details is not wrapped in a second shell.
- [ ] **AC-02** — **Given** clock or status presentation changes, **when** a page is visible, **then** its page state and focus are preserved.
- [ ] **AC-03** — **Given** a shared action descriptor, **when** the footer renders its labels and enabled state, **then** it reflects that descriptor and unavailable actions are hidden or disabled truthfully.
- [ ] **AC-04** — **Given** fixture values are displayed in the shell, **when** status is presented, **then** Home remains the geometry authority and fixture values are not represented as live telemetry.

## Verification

| Criterion | Evidence | Status |
| --- | --- | --- |
| AC-01 | Planned fixture-route shell comparison | Not run |
| AC-02 | Planned state/focus preservation test | Not run |
| AC-03 | Planned descriptor-to-footer test | Not run |
| AC-04 | Planned manual status-presentation review | Not run |

## Delivery notes

The F04 creation lease covers shell components/state and evidence; metrics, Activity, contracts, and gallery are coordinator-owned. Source coverage: F04 ordered steps 1 and 4. Exact ownership is defined in [F04](../features/F04-shell-navigation.md).

## Out of scope

- Production page rules, sensor polling, or Activity window integration.
