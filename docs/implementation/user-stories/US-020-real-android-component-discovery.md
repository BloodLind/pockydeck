# US-020 — Discover real launchable Android components

- Parent: [F07 — Android discovery and launch adapters](../features/F07-android-catalog.md)
- Status: **Accepted**
- Type: **Enabler**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a developer, I want a verified inventory of current-user launchable Android components with narrow visibility, so that the launcher lists software a user can actually open.

## Ready when

- [US-019 — Durable preferences and navigation snapshots](US-019-durable-preferences-and-navigation-snapshots.md) is accepted.
- The coordinator has accepted F06 and integrated its catalog write boundary; F07’s wave-3 lease applies, while final F07 acceptance follows all of its child stories.
- The coordinator can review any required package-visibility declaration before it is added.

## Scope

Document and implement Android 13 enumeration for real current-user `MAIN`/`LAUNCHER` components. Preserve the component identity, including multiple launchable activities from one package; include launchable emulator and system activities; exclude this launcher and synthesized app-details-only entries. Submit only the visibility queries the verified approach needs.

## Acceptance criteria

- [x] **AC-01** — **Given** a package with two launchable activities, **when** inventory is produced, **then** it contains two distinct stable component entries rather than one package-level entry.
- [x] **AC-02** — **Given** launchable system and emulator activities in the current user, **when** enumeration runs, **then** their real launchable components are included in the result.
- [x] **AC-03** — **Given** this launcher and an app-details-only synthesized entry, **when** inventory is produced, **then** neither is presented as a launchable catalog item.
- [x] **AC-04** — **Given** the selected Android 13 enumeration approach, **when** its record and manifest delta are reviewed, **then** they identify only required package queries and do not request broad installed-package, ROM, network, overlay, accessibility, or usage access.

## Verification

- **AC-01:** Passed — 7 JVM tests and 3 actual Android PackageManager tests; see [evidence](../evidence/F07/US-020.md).
- **AC-02:** Passed — production query running inside this app on the Android 13 Flip 2 returned 26 components, including Dolphin, RetroArch, PPSSPP and system Settings; see [physical capture](../evidence/F07/US-020-flip2-discovery.png).
- **AC-03:** Passed — actual Android fixture rejects app-details-only and self entries; physical capture confirms self excluded.
- **AC-04:** Passed — separate Sol/high source review and coordinator review of exact MAIN/LAUNCHER query, with no new permission.

## Delivery notes

Work stays in the F07 Android-app enumeration lease and its matching tests/evidence. The parent packet owns the exact paths, dependency boundary, and coordinator-only manifest integration: [F07](../features/F07-android-catalog.md). Source coverage: F07 ordered steps 1–2. Common completion requirements are in [the story standard](story-standard.md).

## Out of scope

ROM enumeration, category inference, global usage monitoring, and any permission outside the narrow visibility proposal.
