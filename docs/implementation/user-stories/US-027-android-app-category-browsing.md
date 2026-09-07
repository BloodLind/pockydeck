# US-027 — Browse installed apps by honest category

- Parent: [F09 — Library and Apps](../features/F09-library-apps.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-terra`, reasoning `high`

## User story

As a launcher user, I want an app-focused view with reliable category filters, so that I can locate Android software without maintaining another library.

## Ready when

- [US-026 — Browse, filter and sort the unified Library](US-026-unified-library-browsing.md) is accepted.
- The coordinator has accepted F08 and made category and availability contracts available; F09’s wave-7 lease applies, while final F09 acceptance follows all of its child stories.
- Any stored category override is supplied by the approved shared contract.

## Scope

Build Apps heading and count, All/Games/Emulators/Other filters, and an icon-led grid from the same catalog source as Library. Apply verified Android metadata and stored user-category override precedence; leave an unknown classification honest. Connect shared launch/details actions, cached states, focus restoration, and the route handoff.

## Acceptance criteria

- [ ] **AC-01** — **Given** the Apps destination has catalog items, **when** it loads, **then** it filters the unified catalog and does not create a second app store.
- [ ] **AC-02** — **Given** Android metadata and a stored user override for an item, **when** a category is displayed or filtered, **then** the approved override precedence is used and unknown classification is not shown as certain.
- [ ] **AC-03** — **Given** All, Games, Emulators, or Other is selected, **when** its grid renders, **then** it shows only the matching items and exposes usable recovery for no apps or an empty filter.
- [ ] **AC-04** — **Given** cached entries, long names, failed icons, or unavailable entries, **when** Apps renders, **then** it retains cached content during refresh and presents readable labels, fallback artwork, and availability explanation.
- [ ] **AC-05** — **Given** filtering removes the focused item, **when** focus restores, **then** it uses the F05 fallback; A/touch and supported Y match the footer action descriptors.
- [ ] **AC-06** — **Given** navigation away and back, **when** Apps restores, **then** it keeps its own filter, selected ID, and anchor while retaining the shared shell anchors.

## Verification

- **AC-01:** Not run — ViewModel test confirming a shared catalog flow is filtered.
- **AC-02:** Not run — automated metadata and override-precedence fixtures.
- **AC-03:** Not run — Compose tests for each filter, no-app state, and empty-filter recovery.
- **AC-04:** Not run — screenshot/Compose fixtures for refresh, long names, icon failure, and unavailable state.
- **AC-05:** Not run — controller traversal and footer/action agreement test.
- **AC-06:** Not run — destination state restoration test.

## Delivery notes

Only the F09 Apps path, tests, and evidence belong to this story. Category persistence, shared controls, route registration, and DI are coordinator deltas. Source coverage: F09 ordered steps 2–6. See [F09](../features/F09-library-apps.md) and [the story standard](story-standard.md).

## Out of scope

Emulator compatibility claims, ROM scanning, and remote image fetching.
