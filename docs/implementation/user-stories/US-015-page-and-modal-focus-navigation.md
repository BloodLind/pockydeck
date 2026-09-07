# US-015 — Move focus across page regions and contained dialogs

| Field | Value |
| --- | --- |
| Parent feature | [F05 — Controller input and focus restoration](../features/F05-controller-restoration.md) |
| Status | Planned |
| Type | Feature |
| Implementation agent | `gpt-5.6-sol` / `high` |

## User story

As a launcher user, I want controller navigation to reach every applicable page region, so that I can operate filters, content, dialogs and the dock without a focus trap.

## Ready when

- [US-014 — Translate controller and touch actions through one input owner](US-014-single-owner-controller-input.md) is accepted.
- The coordinator has accepted F04 and F06 with integrated evidence, opening F05 work.
- F05 wave 5, its exact lease, validation, and coordinator handoff requirements apply. F05’s completion gate is assessed after its child stories; later features wait for coordinator acceptance.

## Scope

Implement explicit header, filter, content, and dock transitions and bind presentation-only modal focus hooks in the application. Allow shoulder destination wrap while preventing unexpected spatial grid-edge wrap. Let triggers change filters only when present. Preserve a valid visible focus target when touch is followed by controller input.

## Acceptance criteria

- [ ] **AC-01** — **Given** a collection first row, intermediate row, or last content row, **when** directional navigation is used, **then** Up reaches filters/header as applicable, intermediate content scrolls, and the dock remains reachable.
- [ ] **AC-02** — **Given** a spatial grid edge or shoulder destination action, **when** navigation occurs, **then** grid edges do not unexpectedly wrap and shoulder switching follows its allowed wrap policy.
- [ ] **AC-03** — **Given** a launcher dialog is active, **when** focus moves or the dialog is dismissed, **then** focus is confined until dismissal and returns to its initiating surface.
- [ ] **AC-04** — **Given** touch changes selection before controller input resumes, **when** controller navigation begins, **then** one valid visible focus target is restored without focusing decorative labels.

## Verification

| Criterion | Evidence | Status |
| --- | --- | --- |
| AC-01 | Planned Compose region-transition and scroll test | Not run |
| AC-02 | Planned grid/shoulder policy test | Not run |
| AC-03 | Planned modal containment and return test | Not run |
| AC-04 | Planned touch-to-controller focused UI test | Not run |

## Delivery notes

The F05 lease covers active focus scopes/region helpers and modal input binding; shared control signatures require coordinator changes. Source coverage: F05 ordered step 3, step 5 (touch-to-controller transition), and step 6 (modal containment and evidence). See [F05](../features/F05-controller-restoration.md) for exact ownership.

## Out of scope

- New ViewModel focus logic or independent modal key dispatch.
