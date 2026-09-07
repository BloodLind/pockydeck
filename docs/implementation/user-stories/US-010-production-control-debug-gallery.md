# US-010 — Inspect production controls in a deterministic debug gallery

| Field | Value |
| --- | --- |
| Parent feature | [F03 — Reusable controls and debug gallery](../features/F03-controls-gallery.md) |
| Status | Accepted |
| Type | Enabler |
| Implementation agent | `gpt-5.6-luna` / `medium` |

## User story

As a developer, I want an executable catalog of production control states and compositions, so that feature work can reuse and review the same controls before live data arrives.

## Ready when

- [US-009 — Provide reusable collection, setting and modal layouts](US-009-reusable-content-and-modal-layouts.md) is accepted.
- The coordinator has accepted F02 and its integrated evidence, opening F03 work.
- F03 wave 3, its exact lease, validation, and coordinator handoff requirements apply. F03’s completion gate is assessed after its child stories; later features wait for coordinator acceptance.

## Scope

Build a debug-only gallery from production controls and deterministic local assets, including fixture Home and content compositions. Cover tokens, type variants, selection versus focus, unavailable and pressed states, long labels/counts, artwork failure, empty collections, dialogs, keyboard-constrained variants, and native-window variants. Publish signatures and the coordinator-wired entry point.

## Acceptance criteria

- [x] **AC-01** — **Given** a debug build, **when** its gallery entry is opened, **then** it shows the documented production control inventory without network dependencies or production fixture leakage.
- [x] **AC-02** — **Given** examples with an active destination or selected chip, **when** a different control receives focus, **then** active/selected state and controller focus remain distinguishable.
- [x] **AC-03** — **Given** fixture Home reorder, long labels/counts, varied icon shapes, failed artwork, or IME constraints, **when** examples render, **then** they retain stable card dimensions for review.
- [x] **AC-04** — **Given** visual evidence is recorded, **when** clipping or readability issues or physical calibration gaps exist, **then** they are documented without claiming untested device fidelity.

## Verification

| Criterion | Evidence | Status |
| --- | --- | --- |
| AC-01 | [Native gallery, source and release-manifest inspection](../evidence/F03/US-010-gallery.md) | Passed |
| AC-02 | [Inspected actual focused/selected controls](../evidence/F03/US-010-controls.png) | Passed |
| AC-03 | [Native variants, reorder and IME captures](../evidence/F03/US-010-gallery.md) | Passed |
| AC-04 | [Limits and later device checks recorded](../evidence/F03/US-010-gallery.md) | Passed |

## Delivery notes

F03 grants the initial lease for debug catalog/drawable fixtures and evidence; the coordinator wires entry/build resources and later shell-gallery hooks. Source coverage: F03 ordered steps 4–6 and validation/handoff. The gallery uses production controls, never parallel gallery-only copies; consult [F03](../features/F03-controls-gallery.md) for paths.

## Out of scope

- Production destination behavior or live sensor/network data.
- Independent gallery versions of production controls.
