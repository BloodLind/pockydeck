# US-030 — Find indexed items with local query and category filters

- Parent: [F11 — Local Search and system keyboard](../features/F11-search.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-terra`, reasoning `high`

## User story

As a launcher user, I want to search local indexed content and supported actions, so that I can find a matching target without network access.

## Ready when

- [US-025 — Use the launcher as HOME while retaining native system controls](US-025-android-home-role-and-window-loop.md) is accepted.
- The coordinator has accepted F08, integrated local query, system-action registry, and presentation hooks, and opened F11’s wave-7 lease.
- System-action results are available only if the coordinator supplies resolvable stable keys.

## Scope

Implement Search route/state/ViewModel query, category, and platform state for local catalog matching across All/Games/Apps/System. Games includes ROMs and effective Android games; Apps includes other Android apps, including emulators. Deduplicate by stable ID, retain query on category changes, and show platform filtering only when relevant. Require a trimmed nonempty query for catalog and system results; blank Search shows an entry prompt, and zero matches remain empty. ROM queries match title, console ID, short caption, and full console name. Render catalog and system results with the same `SearchResultCard` dimensions and abbreviated console captions.

This scope incorporates the 8 September 2026 [page-rule revision](../contracts.md#september-page-membership-search-and-interaction-revision), superseding empty-query recents. Existing acceptance status and verification records below remain historical records, not acceptance of the revised behavior.

## Acceptance criteria

- [ ] **AC-01** — **Given** an Android game exists in the local catalog, **when** Search shows All results, **then** it appears once under its Games classification rather than once as a game and again as an app.
- [ ] **AC-02** — **Given** a system action result, **when** Search presents it, **then** it comes from a coordinator-supplied resolvable stable key; fixture or unresolvable actions are not shown as working shortcuts.
- [ ] **AC-03** — **Given** a nonempty query, **when** the user changes category, **then** the query remains unchanged and platform filtering is shown only when applicable.
- [ ] **AC-04** — **Given** an empty or whitespace-only query, **when** Search renders, **then** it shows an entry prompt and no catalog or system results; **given** a nonempty query or restricted filter with no matches, **then** it stays empty rather than falling back to recents or the full catalog. Internal system shortcuts never enter Home recency.
- [ ] **AC-05** — **Given** late local result updates or a filter change, **when** the current selected item remains, **then** its stable selection is retained; **when** it does not, **then** normal fallback is applied.
- [ ] **AC-06** — **Given** Search is used offline, **when** matching occurs, **then** it reads local indexed data without network requests or access inside another app, matches ROM console IDs/short labels/full names as well as titles, and catalog/system cards share stable dimensions.

## Verification

- **AC-01:** Not run — automated stable-ID deduplication fixture test.
- **AC-02:** Not run — registry integration test for resolvable and rejected system actions.
- **AC-03:** Not run — ViewModel category/query retention test.
- **AC-04:** Not run — the original recents test plan is superseded by blank/whitespace, no-result, restricted-filter, and recency-exclusion coverage; this revision does not record a new acceptance run.
- **AC-05:** Not run — automated late-result selection-restoration test.
- **AC-06:** Not run — automated repository fake proving local-only queries plus card screenshot review.

## Delivery notes

The F11 Search lease owns local state and result composition; query interface and action registry changes are coordinator deltas. Source coverage: F11 ordered steps 1–3. See [F11](../features/F11-search.md) and [the story standard](story-standard.md).

## Out of scope

Online/global device search, per-keystroke provider calls, unsupported settings intents, and searching inside other apps.
