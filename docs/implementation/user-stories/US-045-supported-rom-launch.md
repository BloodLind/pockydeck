# US-045 — Launch a supported ROM through the common open pipeline

- Parent: [F16 — Emulator selection and ROM launch](../features/F16-emulator-launch.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a launcher user, I want a supported ROM to open in its resolved emulator, so that I can play it and return to the same selected game.

## Ready when

- [US-044 — Resolve supported emulator capabilities and saved choices](US-044-supported-emulator-resolution.md) is accepted.
- F15 is accepted and the resolution contract plus accepted URI/storage strategy are published.
- Device proof is required for every row claimed as a supported app/version/platform/format combination.

## Scope

Implement accepted emulator adapters with immediate target, URI, and grant validation and external task placement. Integrate them through F08’s common orchestration: successful dispatch records recency once; failure retains order and offers source or selection recovery. Record license, revision, path, and notices for any selectively reused mapping.

## Acceptance criteria

- [ ] **AC-01** — **Given** an accepted compatibility row with its validated target and URI, **when** the user launches the ROM, **then** the recorded emulator contract opens it with only required grants.
- [ ] **AC-02** — **Given** the emulator is missing, source grant is revoked, format is unsupported, or SAF input cannot be consumed under the accepted strategy, **when** launch is attempted, **then** it returns recoverable selection/source failure and recent order is unchanged.
- [ ] **AC-03** — **Given** ROM dispatch succeeds, **when** the user returns Home, **then** the same ID is promoted once, repeated opens do not duplicate it, and valid identity/anchor restore in the separate launcher task.
- [ ] **AC-04** — **Given** ROM support is integrated, **when** Android app launches and system actions are exercised, **then** their existing launch behavior and recency exclusions remain intact.

## Verification

- **AC-01:** Not run — device test for every claimed compatibility row.
- **AC-02:** Not run — missing-target, revoked-grant, unsupported-format, and SAF strategy tests.
- **AC-03:** Not run — repeated-open, Home-return, identity/anchor, and task-separation tests.
- **AC-04:** Not run — native Android/system-action regression matrix.

## Delivery notes

F16 owns emulator adapters and evidence. Common dispatcher, domain, permissions, and persistence integration are coordinator deltas. If a mapping is selectively reused, record compatible licensing, contract validation, source revision/path, and required notices in delivery evidence. Source coverage: F16 ordered steps 3–4 and 6. See [F16](../features/F16-emulator-launch.md) and [the story standard](story-standard.md).

## Out of scope

Hidden ROM copying, BIOS/save management, guaranteed suspension, and process monitoring.
