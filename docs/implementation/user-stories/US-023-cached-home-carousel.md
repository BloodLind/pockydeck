# US-023 — Browse cached Home items in the shared carousel

- Parent: [F08 — First working HOME loop](../features/F08-home-loop.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a launcher user, I want my catalog available in Home before background discovery finishes, so that I can choose an item immediately from a familiar readable layout.

## Ready when

- [US-016 — Identity and scroll restoration](US-016-identity-and-scroll-restoration.md) and [US-022 — Dispatch revalidated Android targets](US-022-safe-android-component-dispatch.md) are accepted.
- The coordinator has accepted F05 and F07, integrated their shared hooks, and opened F08’s wave-6 lease.
- Shared shell, catalog, card, and local-artwork boundaries are available through their existing contracts.

## Scope

Create Home Route, Screen, ViewModel, and immutable state using lifecycle-aware cache-first collection. Render metadata, title, a keyed horizontal carousel of up to twelve entries, and a Library action inside the shared content rectangle. Establish shared item presentation and action hooks plus bounded local native-icon/fallback loading for later destinations.

## Acceptance criteria

- [ ] **AC-01** — **Given** cached catalog entries and an in-progress refresh, **when** Home opens, **then** the cached entries remain browsable while refresh state is visible.
- [ ] **AC-02** — **Given** no cached entries, an unavailable entry, a removed selected entry, or a failed launch state, **when** Home renders, **then** it presents a distinct useful recovery action for that state.
- [ ] **AC-03** — **Given** more than twelve catalog entries, **when** Home renders, **then** the carousel presents at most twelve keyed item entries plus the Library action using the shared Home-derived geometry.
- [ ] **AC-04** — **Given** a long title, missing native icon, or unsupported artwork, **when** its card renders, **then** title and artwork stay within fixed card bounds and use a bounded local fallback without changing the item identity.
- [ ] **AC-05** — **Given** a consumer destination needs an item card or action label, **when** it uses the published Home port, **then** it receives supported `Play`, `Open`, or `Reopen` presentation without Home claiming unfinished destination behavior.

## Verification

- **AC-01:** Not run — Compose test with cached data and refreshing flow.
- **AC-02:** Not run — Compose tests/screenshots for loading, empty, unavailable, removed, and failed-launch fixtures.
- **AC-03:** Not run — ViewModel/Compose test for keyed twelve-item carousel and Library action.
- **AC-04:** Not run — screenshot comparison with long titles and failed icon/artwork fixture.
- **AC-05:** Not run — automated presentation-port test and coordinator API review.

## Delivery notes

F08 owns Home, common components/presentation, and local artwork initial creation; the parent defines the exact path lease and coordinator integrations. Source coverage: F08 ordered steps 1–2 and step 7. See [F08](../features/F08-home-loop.md) and [the story standard](story-standard.md).

## Out of scope

ROM content, network artwork, production telemetry, and making this screen the Android default HOME.
