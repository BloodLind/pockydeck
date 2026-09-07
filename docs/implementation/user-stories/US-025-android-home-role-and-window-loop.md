# US-025 — Use the launcher as HOME while retaining native system controls

- Parent: [F08 — First working HOME loop](../features/F08-home-loop.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a launcher user, I want to choose this launcher as HOME and return to it reliably, so that it can be my everyday entry point while Android continues handling system controls.

## Ready when

- [US-024 — Open an item once and return with it selected first](US-024-acknowledged-home-launch-and-return.md) is accepted.
- The coordinator has accepted F05 and F07, integrated F08 shared hooks, and approved the narrow manifest, MainActivity, window, and DI deltas.
- The physical device setup needed for HOME-role and Retroid-control evidence is available; missing device evidence remains pending.

## Scope

Provide acknowledged HOME-role setup and support the coordinator’s integration of one exported `singleTask` Activity with separate `MAIN`/`HOME`/`DEFAULT` and `MAIN`/`LAUNCHER` filters. Integrate launcher-only landscape and immersive presentation with transient system bars. Execute the HOME loop, recreation, cached-return, removal-fallback, and external-task evidence suite.

## Acceptance criteria

- [ ] **AC-01** — **Given** Android presents default-HOME selection, **when** the user accepts or declines it, **then** acceptance sets up the launcher role and decline leaves ordinary browsing and launch behavior usable without replaying the prompt.
- [ ] **AC-02** — **Given** repeated HOME intents after role setup, **when** Android delivers them, **then** the single launcher Activity retains valid page state and does not replay a previous launch request.
- [ ] **AC-03** — **Given** the user opens an external app and returns Home, **when** the launcher resumes, **then** it restores a valid selected identity and anchor or removal fallback while the external target remains in its own task.
- [ ] **AC-04** — **Given** launcher immersive presentation is active, **when** notification shade, volume, brightness, power, or Retroid controls are used, **then** Android retains responsibility for those native controls and transient system bars can be revealed as supported.
- [ ] **AC-05** — **Given** a saved catalog, selected identity and scroll anchor while catalog refresh is delayed, **when** Android recreates the launcher or starts it after process loss, **then** Home shows cached content without waiting for refresh, restores the valid identity and anchor or a deterministic fallback, and does not replay an external launch or HOME-role prompt.

## Verification

- **AC-01:** Not run — Android device manual HOME-role accept/decline procedure.
- **AC-02:** Not run — Android emulator/device repeated-HOME intent test.
- **AC-03:** Not run — Android device app-to-Home, recreation, removal-fallback, and task-separation procedure.
- **AC-04:** Not run — physical-device manual system-control check; Flip 2-specific controls pending until tested.
- **AC-05:** Not run — Android integration test and device cold-start/process-restoration procedure with cached data and a deliberately delayed refresh.

## Delivery notes

F08 owns platform/home evidence, while the coordinator owns manifest, `MainActivity`, window, DI, and route integration. Source coverage: F08 ordered steps 5–6 and its integrated validation/handoff. This story is the native HOME integration gate following US-024. See [F08](../features/F08-home-loop.md) and [the story standard](story-standard.md).

Link each result to the current source baseline, alongside the required build, lint and relevant automated results. Mark untested physical Flip 2 scenarios pending; recording the procedure alone does not satisfy the criterion.

## Out of scope

Automatic default-HOME selection, boot services, replacing Android’s system controls, and external-app window control.
