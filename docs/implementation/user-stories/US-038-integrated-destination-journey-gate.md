# US-038 — Verify the six destinations operate as one launcher

- Parent: [F14 — Destination integration](../features/F14-destination-integration.md)
- Status: **Planned**
- Type: **Gate**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a tester, I want an integrated destination and input journey with reviewed evidence, so that the native-app stage has one consistent user experience.

## Ready when

- US-027, US-029, US-032, US-035, and [US-037](US-037-honest-optional-device-status.md) are accepted.
- F09–F13 are accepted and integrated; F14’s wave-9 gate and temporary-fix lease rules apply.
- All six destination routes and their shared shell/action/input contracts are registered.

## Scope

Review registered destinations and shared shell/action/input/module boundaries. Exercise Home, Library/Apps, Details/Favorites, shortcut Search with IME Back/origin, Settings, touch/controller transitions, empty states, filtering, sorting, and removal. Route each defect to its owning packet or an explicit coordinator lease.

## Acceptance criteria

- [ ] **AC-01** — **Given** each of the six registered destinations, **when** its primary journey is exercised, **then** it has real behavior using one shell, launch path, action/footer descriptor, and input owner.
- [ ] **AC-02** — **Given** a user changes destination, query, filter, sort, selection, or anchor, **when** they return, **then** each destination restores its independent state or its documented fallback.
- [ ] **AC-03** — **Given** empty results, selected-item removal, or a switch between touch and controller, **when** interaction continues, **then** focus remains recoverable and available footer actions agree with activation.
- [ ] **AC-04** — **Given** normal, large-font, and keyboard-constrained layouts, **when** all destinations are reviewed, **then** shell anchors and focus bounds agree with Home and controls remain reachable.
- [ ] **AC-05** — **Given** build, lint, test, and hardware evidence is assembled, **when** the gate is reviewed, **then** actual results and bounded owner fixes are linked while unrun hardware checks remain pending.

## Verification

- **AC-01:** Not run — integrated destination journey matrix.
- **AC-02:** Not run — route/state restoration integration tests.
- **AC-03:** Not run — controller/touch, empty-state, and removal Compose tests.
- **AC-04:** Not run — cross-destination screenshot review with font/IME fixtures.
- **AC-05:** Not run — coordinator review of build, lint, test, and evidence bundle.

## Delivery notes

F14 owns integration test packages and evidence only; code fixes return to packet owners or explicit central leases. Source coverage: F14 ordered steps 1–3 and 6. See [F14](../features/F14-destination-integration.md) and [the story standard](story-standard.md).

## Out of scope

Redesigning contracts, blanket feature-file access, or claiming unrun hardware checks pass.
