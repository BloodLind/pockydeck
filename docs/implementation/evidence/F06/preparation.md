# F06 / US-017 preparation

Planning-only readiness record from 7 September 2026. US-017 and F06 remain
planned until US-003 and the F01 integration gate are accepted. This work did
not add production code, entities, schemas, dependencies, or status changes and
did not use the coordinator-owned emulator.

## Approved persistence semantics

- Room is the observable catalog and favorite-reference source. Consumers can
  read the last committed cache before discovery finishes.
- An Android entry is identified by its current-user launchable component, so
  two activities in one package remain two items. Titles and filenames are not
  identities.
- Only a completed inventory may reconcile availability. A failed, cancelled,
  or partial inventory preserves the last committed catalog without inferring
  removal.
- A completed inventory upserts its discovered rows and marks previously known
  rows omitted from that completed scope unavailable in one transaction.
  Active launch lists exclude unavailable rows.
- Rediscovery and app updates may replace discovered fields and provenance, but
  must not erase favorite references, open history, or user category/artwork
  overrides. Temporary unavailability retains those references for later
  rediscovery.
- A favorite is an independent reference to an item ID. Removing a favorite
  changes only that reference; it never deletes a catalog item. Catalog-row
  deletion is therefore not part of US-017 reconciliation.
- The initial schema contains only the native catalog structures needed by the
  approved contracts. ROM/provider tables are deferred to their later gates.
  Room schema export and explicit migration registration are required; no
  destructive fallback is permitted.

These rules are already approved by the project plan and F06 packet. Their
concrete table layout is an implementation detail to reconcile with the final
F01 types, not a new product decision.

## Contract inputs required after F01 acceptance

US-017 can begin as soon as the accepted F01 handoff identifies:

1. the exact `ItemId` representation/codec and Android component identity
   fields, including how the current-user constraint appears in the ID;
2. the `LibraryItem`, launch-target, availability, category, supported-action,
   provenance, and user-override types that Room rows must map without exposing
   entities;
3. the observable catalog and favorite repository signatures, query/filter
   shape, and deterministic title/ID ordering policy applicable before US-018;
4. the completed-versus-failed/partial/cancelled inventory type, its reconciliation
   scope, and the repository write result/error contract;
5. the reference-preservation and launch/recency boundary exposed by F01, so
   US-017 leaves the correct foreign-key and transaction surface for US-018;
6. the exact package paths and symbols published in
   `docs/implementation/contracts.md` plus any coordinator integration hooks.

At this preparation snapshot, `docs/implementation/contracts.md` and
`core/domain/src/main` are not yet present. Any contracts observed before F01
acceptance are provisional.

## Minimal design reconciliation after contracts publish

These are contract-integration checks for the US-017 implementer, not requests
to revisit the approved schema meaning:

- Choose a lossless SQL encoding for the accepted `ItemId` and prove round-trip
  mapping, rather than inventing a second data-layer identity format.
- Match the accepted completed-inventory scope exactly. The transaction may
  mark only rows omitted from that completed scope unavailable; it must not
  treat a scoped result as a global empty inventory.
- Map accepted availability/provenance/action/category variants to stable
  persisted codes and fail safely on unsupported codes. Do not infer new ROM or
  provider variants.
- Decide the smallest normalized split that prevents discovery upserts from
  touching favorite, history, category-override, or artwork-override rows. Use
  foreign-key behavior that retains user/history references when an item is
  unavailable.
- Confirm whether the accepted catalog API observes domain items only or also
  exposes retained unavailable references. In either case, implement active
  queries as an explicit availability filter and keep a separate reference
  query for retained favorites.
- Name the production database and version-1 schema path once, then register an
  explicit migration list (empty at version 1) without
  `fallbackToDestructiveMigration`. Later schema changes remain coordinator
  checkpoints.

If an accepted F01 signature contradicts one of the approved preservation rules,
stop and route that concrete mismatch to the coordinator. Routine naming,
column, index, and mapper choices can proceed under the F06 creation lease.

## Android 13 instrumentation matrix

Run these as `core:data` instrumentation tests against `emulator-5556` (API 33)
after the coordinator dispatches US-017 and releases the emulator. Use a real
Room database. Prefer a file-backed temporary test database for restart and
migration checks; an in-memory database is sufficient only where reopen
durability is not under test. Collect the test task result and exported schema
diff as evidence.

| Criterion | Focused test | Setup and action | Required assertions |
| --- | --- | --- | --- |
| AC-01 | `cachedRowsEmitBeforeInventoryCompletes` | Commit A and B, close/reopen the file-backed database, subscribe to the active catalog, then suspend a new inventory before its completed commit. | A and B emit from Room without waiting for discovery; the pending operation causes no write. |
| AC-01 | `samePackageComponentsRoundTripAsDistinctItems` | Commit two current-user component identities with the same package and different activity classes, then query and reopen. | Two rows and two distinct accepted `ItemId` values survive; neither overwrites the other. |
| AC-02 | `completedInventoryReconcilesAtomically` | Start with A/B, observe the query, then apply a completed inventory containing updated A and new C while omitting B. | The observer sees only a committed end state; A is updated, C is active, B is unavailable, and no partial intermediate set is emitted. |
| AC-02 | `nonCompletedInventoriesPreserveCommittedCatalog` | From the same A/B baseline, submit failed, cancelled, and partial outcomes containing empty or truncated payloads in separate cases. | Rows, discovered fields, availability, and observable active results remain logically unchanged for every outcome. |
| AC-03 | `rediscoveryDoesNotOverwriteUserOwnedData` | Give A a favorite reference plus category/artwork overrides (and a history fixture through the reserved US-018 boundary when available); rediscover A with changed title/icon/provenance. | Discovered fields/provenance update; favorite, overrides, and existing history values remain unchanged. |
| AC-03 | `unavailableItemIsHiddenButReferencesSurviveAndRecover` | Favorite A, then apply a completed inventory omitting A; query active catalog and favorite references; finally rediscover A. | A disappears from active results, its favorite/override/history references remain, and rediscovery restores the same identity with those references intact. |
| AC-03 | `favoriteRemovalDoesNotDeleteCatalogItem` | Favorite active A, remove only the favorite reference, and query both stores. | The favorite reference disappears while A remains an active catalog item. |
| AC-04 | `versionOneSchemaMatchesReviewedContract` | Generate the production version-1 export and inspect it with Room schema tooling/review. | Primary keys preserve accepted identity; required indices and foreign keys match query and retention behavior; no speculative ROM/provider structures exist. |
| AC-04 | `databaseReopensWithoutDestructiveFallback` | Populate the file-backed production database, close it, and reopen with the production builder and registered migrations. | Data remains and Room validates the exported version-1 identity; the builder has no destructive fallback. |
| AC-04 / future migration harness | `migrationHelperCanOpenExportedSchema` | Use `MigrationTestHelper` with the production database class and schema assets. At version 1, create/open the exported schema; when version 2 exists, migrate a populated version-1 file through the explicit migration list. | The version-1 asset is resolvable now; future tests can validate retained catalog/favorite/override/history rows and reject missing migrations without a Gradle change. |

For the atomicity assertion, collect the invalidation-backed Flow throughout the
transaction and assert there is no observable mixed generation, while also
querying the final rows directly. Coroutine scheduling alone is not evidence of
transaction isolation.

## Scaffold readiness

`core/data/build.gradle.kts` is sufficient for US-017 without an edit:

- `testInstrumentationRunner` is `androidx.test.runner.AndroidJUnitRunner`;
- Android test dependencies include AndroidX JUnit, runner, core,
  `androidx.room:room-testing`, and `kotlinx-coroutines-test`;
- Room runtime/KTX and KSP compiler are configured;
- KSP exports schemas to `core/data/schemas`; and
- that schema directory is mounted into the `androidTest` assets used by
  `MigrationTestHelper`.

The debug-only `FoundationFixtureDatabase` and its exported version-1 JSON show
that KSP/schema wiring compiled during US-001. They are scaffold evidence only:
US-017 must use a distinct production database class/schema and must not migrate
or expand the fixture. No `androidTest` source directory exists yet, which is
normal; US-017 can create its owned test package. No Gradle edit is currently
needed for actual Room query, transaction, reopen, or future migration tests.

## Readiness result

US-017 is implementation-ready from the persistence/scaffold side. The only
gate is the planned one: accepted US-003 contracts and coordinator acceptance of
F01. After that handoff, create the production Room implementation only under
the F06 lease, run the matrix on the released API-33 emulator, export/review
version 1, and return exact commands and results to the coordinator. Physical
Flip 2 evidence is not required for this persistence story.
