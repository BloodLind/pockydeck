# US-013 — Keep the shell usable when the keyboard or system bars appear

| Field | Value |
| --- | --- |
| Parent feature | [F04 — Persistent shell and navigation](../features/F04-shell-navigation.md) |
| Status | Planned |
| Type | Feature |
| Implementation agent | `gpt-5.6-terra` / `high` |

## User story

As a launcher user, I want query content and available controls to remain usable around transient system UI, so that editing does not lose my position or clip essential controls.

## Ready when

- [US-012 — Switch destinations and return to the correct origin](US-012-destination-and-origin-navigation.md) is accepted.
- The coordinator has accepted F03 and its integrated evidence, opening F04 work.
- F04 wave 4, its exact lease, validation, and coordinator handoff requirements apply. F04’s completion gate is assessed after its child stories; later features wait for coordinator acceptance.

## Scope

Define a central IME exception that keeps the query visible, shrinks or scrolls results, and moves the bottom shell above the IME where feasible. Support transient-bar layout and state preservation through the shell contract pending F08 Activity wiring. Compare static Home and content fixtures through the coordinator gallery hook and publish insets and route interfaces.

## Acceptance criteria

- [ ] **AC-01** — **Given** an IME-constrained query fixture, **when** the keyboard appears, **then** the query remains visible, results retain a usable scroll region, and the shell bottom uses space above the IME where feasible.
- [ ] **AC-02** — **Given** the keyboard is dismissed, **when** normal layout returns, **then** common Home-derived anchors are restored without page-specific compensations.
- [ ] **AC-03** — **Given** transient system-bar layout changes, **when** they occur, **then** navigation keys and a valid content surface are retained.
- [ ] **AC-04** — **Given** transient system bars appear or disappear while a destination is active, **when** the available content bounds change, **then** the active destination retains a valid visible focus target without route or page-state loss.

## Verification

| Criterion | Evidence | Status |
| --- | --- | --- |
| AC-01 | Planned IME-constrained fixture capture and manual test | Not run |
| AC-02 | Planned post-IME shell-anchor comparison | Not run |
| AC-03 | Planned transient-bar state fixture test | Not run |
| AC-04 | Planned transient-system-bar bounds, state, and focus preservation test | Not run |

## Delivery notes

F04’s initial lease covers shell layout/insets policy, navigation attachment documentation, and evidence. The coordinator wires the gallery and later Activity window behavior. Source coverage: F04 ordered steps 5–6 and validation/handoff; use [F04](../features/F04-shell-navigation.md) for path ownership. Reference comparison excludes editor surroundings; physical density and keyboard calibration remain pending until device evidence exists.

## Out of scope

- A custom keyboard, replacement Android controls, or claims of tested Flip 2 IME behavior.
