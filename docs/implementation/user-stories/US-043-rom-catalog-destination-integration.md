# US-043 — Browse indexed ROMs through existing launcher destinations

- Parent: [F15 — ROM sources and incremental indexing](../features/F15-rom-index.md)
- Status: **Implemented in the integrated ROM batch; final physical destination matrix remains partial**. See [batch evidence](../evidence/F15/rom-feature-batch.md).
- Type: **Feature**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a launcher user, I want indexed ROMs to appear in the same library and search flows, so that I can browse my games consistently before and after emulator setup.

## Ready when

The user authorized implementation of F15/F16 together under the integrated batch workflow. The original prerequisites below remain historical planning context, not an unfulfilled coding gate.

- [US-042 — Maintain the ROM index through complete incremental scans](US-042-incremental-rom-indexing.md) is accepted.
- F14 is accepted and the coordinator has integrated ROM catalog/schema changes through existing destination contracts.
- Unverified emulator capability remains an explicit unavailable state until F16.

## Scope

Integrate completed ROM catalog rows with Library, Search, Favorites, Home presentation, and availability contracts. Use shared stable identities and show unresolved or no-compatible-emulator state honestly. Publish schema, recovery, and migration evidence for F16 without changing shared destination packages outside a lease.

The 8 September 2026 [page-rule revision](../contracts.md#september-page-membership-search-and-interaction-revision) keeps all available ROMs in Library, including those with a legacy category override; Apps contains only non-game Android apps. Favorites includes available favorite ROMs and Android apps across all consoles. Search includes ROMs only for a matching nonempty query and recognizes title, console ID, full console name, and abbreviation. Console card captions and filter pills use abbreviations; empty console folders still create no console rows. These presentation rules do not change source access, identity, schema, or the historical evidence below.

## Acceptance criteria

- [ ] **AC-01** — **Given** an available completed indexed ROM row, **when** common destinations query the catalog, **then** it uses its stable shared identity in Library, matching nonempty Search, Favorites when marked favorite, and eligible Home content; it does not appear in Apps or create a separate ROM store or screen.
- [ ] **AC-02** — **Given** a ROM is unresolved or has no compatible emulator yet, **when** it renders, **then** it exposes its availability/recovery state and does not claim launch support.
- [ ] **AC-03** — **Given** a source becomes unavailable or a rescan occurs, **when** ROM rows update, **then** favorite, recent, and artwork references follow the reviewed preservation policy.
- [ ] **AC-04** — **Given** ROM schema integration occurs, **when** native Android browsing and launch are exercised, **then** they remain usable and source/grant/grouping evidence distinguishes tested cases from pending device cases.

## Verification

- **AC-01:** Implemented through the shared catalog — JVM collection/tile fixtures and native SAF-to-Library smoke verify ROM rows and dynamic console filters. Full physical coverage of each destination is separate.
- **AC-02:** Native smoke — an unassigned ISO remains selectable; opening a synthetic GBA game without an installed emulator gives an actionable message. No game boot or emulator support is inferred.
- **AC-03:** Passed — native source removal/readdition, unavailable-source, rescan and migration fixtures retain stable identities and user references.
- **AC-04:** Integrated JVM/native migration and Android catalog/dispatch regression results are recorded in the [batch evidence](../evidence/F15/rom-feature-batch.md). Physical controller/lid/HOME and real emulator game-boot cases remain explicitly separate.

## Delivery notes

F15 supplies ROM repository/presentation integration evidence; coordinator registers shared catalog/settings deltas and routes destination defects. Source coverage: F15 ordered step 6 and validation/handoff. See [F15](../features/F15-rom-index.md) and [the story standard](story-standard.md).

## Out of scope

Claiming ROM launch before F16 or rewriting common destination paths without a lease.
