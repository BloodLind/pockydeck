# US-014 — Translate controller and touch actions through one input owner

| Field | Value |
| --- | --- |
| Parent feature | [F05 — Controller input and focus restoration](../features/F05-controller-restoration.md) |
| Status | Planned |
| Type | Feature |
| Implementation agent | `gpt-5.6-sol` / `high` |

## User story

As a launcher user, I want each physical or touch action to perform its displayed semantic action once, so that controls behave consistently without double movement or activation.

## Ready when

- [US-013 — Keep the shell usable when the keyboard or system bars appear](US-013-shell-keyboard-and-inset-layout.md) and [US-019 — Persist controller preferences and small navigation snapshots](US-019-durable-preferences-and-navigation-snapshots.md) are accepted.
- The coordinator has accepted F04 and F06 with integrated evidence, opening F05 work.
- F05 wave 5, its exact lease, validation, and coordinator handoff requirements apply. F05’s completion gate is assessed after its child stories; later features wait for coordinator acceptance.

## Scope

Normalize D-pad, hat axes, left stick, confirm/back mapping, and touch with a dead zone, bounded directional repeat, and duplicate suppression. Apply modal/IME, focused page/control, then unhandled shell-shortcut precedence. Bind A, B, X, Y, Start, shoulder, and trigger actions to the same descriptor used for footer glyphs.

## Acceptance criteria

- [ ] **AC-01** — **Given** one physical controller action produces duplicate reports, **when** input is normalized, **then** one logical action is dispatched and a held direction repeats in the bounded configured manner.
- [ ] **AC-02** — **Given** an axis or stick is inside its configured dead zone or returns to neutral, **when** directional input is processed, **then** it produces no directional action and any held-direction repeat stops until a new qualifying direction is received.
- [ ] **AC-03** — **Given** the confirm/back preference is changed or absent, **when** controller input and footer glyphs are presented, **then** both use the same mapping and absence defaults to A-confirm/B-back.
- [ ] **AC-04** — **Given** an enabled footer action is activated through its mapped controller input or touch, **when** that action is handled, **then** both paths perform the displayed semantic action exactly once.
- [ ] **AC-05** — **Given** an IME, modal, details route, or root Home is active, **when** Back is pressed, **then** the IME consumes it first, origin handling follows as applicable, and root Home remains open without leaked typing shortcuts.
- [ ] **AC-06** — **Given** X, Y, Start, or filter actions are unavailable or outside the launcher, **when** their inputs occur, **then** only applicable actions run and handling stops at the launcher boundary.

## Verification

| Criterion | Evidence | Status |
| --- | --- | --- |
| AC-01 | Planned focused JVM normalization/repeat test | Not run |
| AC-02 | Planned configured dead-zone and neutral-repeat-stop test | Not run |
| AC-03 | Planned preference-to-glyph/action agreement test | Not run |
| AC-04 | Planned controller/touch single-action and footer-agreement test | Not run |
| AC-05 | Planned Compose fixture Back-precedence test | Not run |
| AC-06 | Planned dispatch applicability and boundary test | Not run |

## Delivery notes

F05’s initial lease covers input normalization/dispatch and matching tests; the coordinator attaches Activity callbacks and shell hooks. Source coverage: F05 ordered steps 1–2 and step 6 (Back precedence and Activity input attachment). The exact path and attachment policy remains in [F05](../features/F05-controller-restoration.md).

## Out of scope

- Global interception, emulator remapping, or a second per-page key dispatcher.
