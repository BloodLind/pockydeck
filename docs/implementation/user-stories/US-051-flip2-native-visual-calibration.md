# US-051 — Calibrate and accept native Home geometry on Flip 2

- Parent: [F18 — Flip 2 hardening and installable delivery](../features/F18-device-delivery.md)
- Status: **Planned**
- Type: **Gate**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a tester, I want a verified Home-derived native layout across all destinations, so that the launcher is readable and consistent on the actual display.

## Ready when

- [US-049 — Correct metadata matches and artwork without losing overrides](US-049-manual-metadata-and-artwork-correction.md) is accepted.
- F17 is accepted and F18’s wave-13 gate applies; this story may proceed independently of other F18 stories under the one-writer rule.
- Actual Flip 2 density, 1920×1080 viewport, and Home app-content crop are available; central metric changes require an explicit coordinator lease.

## Scope

Measure native density/viewport and compare the Home app-content crop. Calibrate dp/sp metrics centrally, check common shell anchors across destinations, and validate large font, cutouts/transient bars, IME, focus outlines, long labels, and missing artwork. Create screenshot baselines only after calibration acceptance.

## Acceptance criteria

- [ ] **AC-01** — **Given** a Flip 2 is available, **when** calibration data is recorded, **then** native metrics use measured device density and a Home crop comparison rather than literal CSS conversion.
- [ ] **AC-02** — **Given** every destination in normal mode, **when** screenshots are compared, **then** common shell anchors agree and focused cards/footer are not clipped.
- [ ] **AC-03** — **Given** larger fonts, IME/transient bars, long labels, or missing art, **when** each destination renders, **then** controls remain readable and usable.
- [ ] **AC-04** — **Given** calibration is accepted, **when** baseline screenshots are published, **then** their locations, source baseline, and measured inputs are traceable; without device evidence the story stays pending.

## Verification

- **AC-01:** Not run — physical Flip 2 density and Home-crop measurement procedure.
- **AC-02:** Not run — cross-destination physical screenshot comparison.
- **AC-03:** Not run — large-font, IME, bars, long-content, and missing-art device matrix.
- **AC-04:** Not run — coordinator calibration and baseline-evidence review.

## Delivery notes

F18 owns visual device validation/evidence. Only the coordinator or explicitly leased owner may change reserved metrics/tokens. Source coverage: F18 ordered step 3. See [F18](../features/F18-device-delivery.md) and [the story standard](story-standard.md).

## Out of scope

Secondary-template geometry, fabricated calibration, and treating unavailable device evidence as acceptance.
