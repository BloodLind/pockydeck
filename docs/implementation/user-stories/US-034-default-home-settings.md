# US-034 — Reach Android default-HOME setup from Settings

- Parent: [F12 — Basic Settings](../features/F12-settings.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-terra`, reasoning `high`

## User story

As a launcher user, I want to review and change my default-HOME selection, so that I can finish launcher setup without losing ordinary access.

## Ready when

- [US-033 — Configure confirm and Back mapping in Settings](US-033-controller-mapping-settings.md) is accepted.
- The coordinator has accepted F09, F10, and F11, and F08’s acknowledged HOME-role request port is integrated for Settings consumption.
- The system-action index can register the approved stable descriptor; F12’s wave-8 lease applies, while final F12 acceptance follows all of its child stories.

## Scope

Expose current selection or declined state and invoke the existing acknowledged HOME-role request from a deliberate Settings action. Provide the stable searchable descriptor for this implemented setting. Continue ordinary browsing after a decline; do not reproduce platform request mechanics inside Settings.

## Acceptance criteria

- [ ] **AC-01** — **Given** Android reports current HOME selection or a prior declined state through the supplied port, **when** Settings renders, **then** it displays the meaningful available state rather than claiming the launcher is default.
- [ ] **AC-02** — **Given** the user deliberately activates HOME setup by controller or touch, **when** the request is issued, **then** both inputs use the same acknowledged shared request behavior.
- [ ] **AC-03** — **Given** the user declines Android’s role prompt, **when** control returns to Settings, **then** browsing remains usable and navigation or state recollection does not replay the prompt.
- [ ] **AC-04** — **Given** the default-HOME setting is indexed for Search, **when** its stable key is resolved, **then** it opens the supported setup action rather than an unresolvable placeholder.

## Verification

- **AC-01:** Not run — Settings state fixtures for selected, unselected, and declined outcomes.
- **AC-02:** Not run — Compose test for controller/touch request-port equivalence.
- **AC-03:** Not run — Android device manual role-decline and no-replay procedure.
- **AC-04:** Not run — system-action index resolution test and Search integration test.

## Delivery notes

F12 owns the HOME setup rows, descriptors, and tests. HOME attachment and system-action index integration are coordinator-owned. Source coverage: F12 ordered steps 3 and 5. See [F12](../features/F12-settings.md) and [the story standard](story-standard.md).

## Out of scope

Silently making the launcher default, duplicate platform request logic, and default-HOME handling outside the shared port.
