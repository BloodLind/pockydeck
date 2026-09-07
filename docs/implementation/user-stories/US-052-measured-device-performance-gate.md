# US-052 — Measure responsiveness and set evidence-based budgets

- Parent: [F18 — Flip 2 hardening and installable delivery](../features/F18-device-delivery.md)
- Status: **Planned**
- Type: **Gate**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a tester, I want measured startup, return, scrolling, memory and idle behavior, so that performance expectations are based on the supported device.

## Ready when

- [US-049 — Correct metadata matches and artwork without losing overrides](US-049-manual-metadata-and-artwork-correction.md) is accepted.
- F17 is accepted and F18’s wave-13 gate applies; this story may proceed independently of other F18 stories under the one-writer rule.
- A physical device and representative catalog are available. No numeric performance baseline or threshold is presumed.

## Scope

Measure cold startup, warm return, scroll jank, memory, and foreground/idle activity with representative catalog sizes. Establish budgets from first physical measurements, investigate observed regressions, and record baseline/before-after evidence while retaining cache-first and bounded lifecycle-aware work.

## Acceptance criteria

- [ ] **AC-01** — **Given** the representative catalog and Flip 2 build conditions, **when** measurements are captured, **then** the record identifies the device, build, catalog conditions, and each measured quantity.
- [ ] **AC-02** — **Given** first physical measurements are reviewed, **when** performance budgets are set, **then** their numeric values derive from that baseline rather than guessed thresholds.
- [ ] **AC-03** — **Given** a measured regression, **when** it is investigated, **then** its cause, bounded fix, and relevant before/after evidence are recorded.
- [ ] **AC-04** — **Given** scans or enrichment run, **when** Home is used, **then** cached browsing remains independent, no disk/network work runs on the main thread, and background activity respects lifecycle.

## Verification

- **AC-01:** Not run — physical Flip 2 measurement capture with conditions log.
- **AC-02:** Not run — coordinator review of first baseline and accepted budgets.
- **AC-03:** Not run — regression investigation and before/after evidence review.
- **AC-04:** Not run — profiling/trace and cache-first lifecycle regression checks.

## Delivery notes

F18 owns device measurement/integration evidence; fixes are assigned to existing owners or explicit coordinator leases. Source coverage: F18 ordered step 5. See [F18](../features/F18-device-delivery.md) and [the story standard](story-standard.md).

## Out of scope

Zero-latency claims, invented performance numbers, and unrelated performance scope.
