# F06 — Catalog and preferences persistence

- Status: **planned**.
- User stories: [US-017](../user-stories/US-017-durable-catalog-and-inventory-cache.md), [US-018](../user-stories/US-018-durable-successful-open-order.md), [US-019](../user-stories/US-019-durable-preferences-and-navigation-snapshots.md). Dispatch one ready story; this packet remains the technical ownership and feature-gate reference.
- Agent: `gpt-5.6-sol`, reasoning `high`.
- Outcome: catalog, favorites, most-recent opens, preferences, and navigation state survive restarts without losing user data during refreshes.
- Prerequisite: F01 accepted. Runs independently beside F02.

## Ownership and contracts

Initial-creation lease: `core/data/src/main/kotlin/dev/handheld/launcher/core/data/local/**`, `core/data/src/main/kotlin/dev/handheld/launcher/core/data/repository/**`, `core/data/schemas/**`; matching data JVM/instrumentation test packages; `docs/implementation/evidence/F06/**`. No domain interface, dependency/Gradle, manifest, or app DI edits. Coordinator integrates those deltas. On acceptance, schemas/DAOs/DataStore formats become reserved; later packets cannot add columns or keys silently.

Consumes F01 repository and identity/order/navigation contracts. Provides Room-backed catalog/favorite/recency observation and DataStore-backed small preferences/navigation snapshots. Supply a repository write boundary for completed inventories without exposing Room entities to feature code. The initial schema implements already-approved persistence semantics; document concrete structure and preservation guarantees before downstream use.

## Ordered steps

1. Model discovered fields separately from favorite references, open-order history, user category/artwork overrides, and source provenance. Preserve multiple launchable components of the same package. User data survives rediscovery and temporary unavailability.
2. Implement cached observable queries first. Order successful opens by a serialized increasing key; unopened items follow in deterministic title/ID order. Updating the same item never inserts a second identity.
3. Define atomic application of a **completed** inventory; partial, cancelled, and failed inventories cannot mark the whole catalog absent. Hide unavailable items from active launch lists while retaining user/history references separately.
4. Implement acknowledged launch-result recording with a consistent duplicate/transaction strategy aligned to F01. Failed dispatch never promotes an item; request restoration persistence and recency update must not replay the external intent.
5. Implement DataStore preferences, including the F01 typed confirm/back mapping with its agreed default, and small durable snapshots containing destination, selected ID, anchor ID/offset, query/filter/sort keys. Version the encoding and handle malformed/obsolete optional snapshot data with safe defaults rather than losing catalog data.
6. Export Room schema, document indices/transactions, and add migration infrastructure with no destructive fallback. No speculative ROM/provider tables are needed now; later schema evolution is a coordinator-owned checkpoint.
7. Hand repository constructors and test fakes to the coordinator for `AppContainer` wiring. Publish exact observable/write contracts before F05/F07 consume them.

## Excludes

No package enumeration, UI state holders, network storage, analytics/session duration, process state, and no persistence of complete lists or Compose objects in Bundles/DataStore.

## Validation and handoff

Repository tests must demonstrate `[C,B,A] → open B → [B,C,A]`, repeated B without duplication, failed dispatch unchanged, concurrent opens with deterministic increasing order, update/unavailability preserving favorites and recency, failed inventory preserving cache, typed confirm/back preference round-trip/defaults, and durable snapshot recovery. Use actual Room instrumentation tests for transactions/queries and DataStore round-trip/corrupt snapshot handling; do not rely only on mocks. Supply schema export, transaction notes, actual commands/results, and the future migration ownership rule to F07 and the coordinator.
