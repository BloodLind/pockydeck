# US-022 — Dispatch revalidated Android targets into external tasks

- Parent: [F07 — Android discovery and launch adapters](../features/F07-android-catalog.md)
- Status: **Accepted**
- Type: **Enabler**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a developer, I want an Android launch adapter with explicit success or recoverable failure, so that the common launch coordinator can record recency safely.

## Ready when

- [US-021 — Reconcile app changes without blocking cached browsing](US-021-cache-first-android-reconciliation.md) is accepted.
- The coordinator has accepted F06 and made the launch/catalog contracts available; F07’s wave-3 lease applies, while final F07 acceptance follows all of its child stories.
- The F08 launch coordinator is not required for this adapter’s result-level tests, but its expected handoff is published.

## Scope

Revalidate the exact component immediately before dispatch and use normal Android launcher task semantics. Return a typed result for a successful dispatch or a recoverable missing, disabled, or security-rejected target. Supply task-separation and dispatch-result evidence to F08; leave successful-open recording to its single orchestration path.

## Acceptance criteria

- [x] **AC-01** — **Given** a previously listed component is missing, disabled, or rejected for security, **when** dispatch is requested, **then** the adapter returns an explicit recoverable failure and does not report a successful open.
- [x] **AC-02** — **Given** an enabled matching component, **when** dispatch is requested, **then** it is revalidated and launched using normal Android launcher task semantics.
- [x] **AC-03** — **Given** a valid target has been dispatched, **when** the user returns to the launcher, **then** the target is not finished by launcher task reuse and its external task is observed separately.
- [x] **AC-04** — **Given** either adapter outcome, **when** it is delivered to a consumer, **then** it contains only dispatch success or recoverable failure and does not write a second recency event or claim a running process, suspension, or resume guarantee.

## Verification

- **AC-01:** Passed — 5 JVM adapter tests and 3 API-33 tests; typed failures, disabled/restored component, and race outcomes.
- **AC-02:** Passed — exact Settings component revalidation and MAIN/LAUNCHER task flags on API 33.
- **AC-03:** Passed — distinct caller task 78 and Settings task 74, with Settings retained after caller returned.
- **AC-04:** Passed — unchanged seeded Room recency and separate Sol/high contract review. See [evidence](../evidence/F07/US-022.md).

## Delivery notes

The F07 lease covers the Android-app launch adapter and matching tests/evidence. Manifest, Activity, and DI attachment remain a coordinator integration. Source coverage: F07 ordered step 5 and launch validation/handoff in [F07](../features/F07-android-catalog.md). Follow [the story standard](story-standard.md).

## Out of scope

Independent recency writes, process management, guaranteed app resumption, and ROM dispatch.
