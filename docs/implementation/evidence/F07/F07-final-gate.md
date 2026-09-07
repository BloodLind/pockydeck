# F07 integration gate

Accepted 8 September 2026 against `bc60032` plus the reviewed shared work.

- US-020: 7 JVM tests and 3 API-33 tests passed. The production adapter was also exercised under the real launcher app context on the connected Flip 2; its complete inventory contained 26 real components, including emulator and system entries, and excluded itself.
- US-021: 8 JVM tests and 3 API-33 tests passed. Cached Room state survives failed/partial/cancelled refreshes, active package changes reconcile without deleting user references, and a resume during a scan queues a later scan.
- US-022: 5 JVM tests and 3 API-33 tests passed. The exact external Settings component launched in a distinct task and survived caller return. Dispatch did not mutate recency.
- Separate Sol/high review found no outstanding P1/P2 issue across the final adapter integration.
- The combined `:app:assembleDebug` passed after the US-009 compilation correction, validating both lazy catalog and dispatcher wiring in `AppContainer`.

The root manifest contains only the narrow MAIN/LAUNCHER visibility query. Room schemas, migrations, durable keys, launch contracts, and permissions retain their accepted meanings. F08 receives `androidCatalog` start/stop/resume hooks and `launchDispatcher`; the adapter itself never records a successful open.

Physical controller, default-HOME choice, and the complete production Home launch/return loop are later acceptance checks. This gate does not claim those checks were performed.
