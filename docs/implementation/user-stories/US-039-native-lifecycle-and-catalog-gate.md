# US-039 — Verify native launch, lifecycle and catalog resilience together

- Parent: [F14 — Destination integration](../features/F14-destination-integration.md)
- Status: **Planned**
- Type: **Gate**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a tester, I want integrated evidence that launch and catalog changes preserve usable state, so that the native-app launcher is reliable before ROM support begins.

## Ready when

- [US-038 — Verify the six destinations operate as one launcher](US-038-integrated-destination-journey-gate.md) is accepted.
- F09–F13 are accepted/integrated; F14’s wave-9 lease applies, while final F14 acceptance follows all of its child stories.
- Android task, inventory, cache, and HOME-role evidence can be exercised against the integrated build.

## Scope

Exercise launch/return, repeated HOME, Activity recreation/process restoration, task separation, active/inactive install-update-uninstall, and failed inventory with cached rendering. Publish the accepted native-app matrix, concrete owned defects, and late-stage readiness inputs without implementing ROM/provider work.

## Acceptance criteria

- [ ] **AC-01** — **Given** a successful launcher-initiated app open, **when** the user returns, **then** a valid identity/anchor restores with unique recent entries; failed dispatch and repeated collection do not replay an action.
- [ ] **AC-02** — **Given** an external target launches, **when** HOME return occurs, **then** it remains in its own task and is not cleared through launcher task reuse.
- [ ] **AC-03** — **Given** an app is installed, updated, or removed while active or inactive, **when** reconciliation completes, **then** the catalog converges; **given** inventory fails, **when** browsing continues, **then** the prior cache remains usable.
- [ ] **AC-04** — **Given** default-HOME role is declined or native controls/status are used, **when** the integrated journey continues, **then** ordinary browsing remains usable and status/system-control presentation remains truthful.
- [ ] **AC-05** — **Given** a blocker depends on device evidence or later ROM/emulator/provider decisions, **when** the native matrix is reviewed, **then** it is explicit and does not invalidate verified native-app behavior.

## Verification

- **AC-01:** Not run — integrated launch/return, repeated-action, recreation tests.
- **AC-02:** Not run — Android emulator/device task-separation procedure.
- **AC-03:** Not run — active/inactive package-change and failed-inventory matrix.
- **AC-04:** Not run — role-decline and native-control/status integration check.
- **AC-05:** Not run — coordinator evidence and blocker review.

## Delivery notes

F14 owns lifecycle/catalog integration tests and evidence; fixes remain bounded and centrally assigned. Source coverage: F14 ordered steps 4–5 and 7. See [F14](../features/F14-destination-integration.md) and [the story standard](story-standard.md).

## Out of scope

ROM/provider implementation, false passing evidence, and expanding native behavior to satisfy later decisions.
