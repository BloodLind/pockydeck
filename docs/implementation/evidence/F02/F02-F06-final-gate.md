# F02 / F06 final integration gate

Accepted 8 September 2026 against previous checkpoint `b9e68d2` on `codex/f01-foundation`.

## Outcome

F02 visual foundations and F06 persistence are complete for their defined code/emulator gates. US-004/005/006 and US-017/018/019 are accepted. Shared theme/metrics/primitives, Room schema/migration/repositories, DataStore keys and repository constructors are published in [contracts](../../contracts.md) and reserved for downstream consumers. The next ready parallel stories are US-007 under F03 and US-020 under F07.

The application container uses its application context and owns one lazy Room database and one lazy preferences store shared across the domain repository ports. Startup does not scan packages. MainActivity remains the themed foundation; production controls, catalog enumeration, HOME role and feature screens belong to later stories.

## Final command

Environment: Android Studio JBR 17, Android SDK platform 34, existing Gradle 8.9 wrapper, and `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=.local/java-tmp`.

```powershell
.\gradlew.bat --no-daemon assembleDebug lintDebug :core:domain:test :app:testDebugUnitTest :core:designsystem:testDebugUnitTest :app:processReleaseMainManifest :core:data:connectedDebugAndroidTest :core:designsystem:connectedDebugAndroidTest
```

**BUILD SUCCESSFUL in 54s**, 213 tasks: 84 executed, 12 from cache and 117 up-to-date. The observed device run completed data instrumentation before starting design-system instrumentation.

| Validation | Result |
| --- | --- |
| Domain JVM tests | 15 passed |
| App JVM tests | 7 passed |
| Shared metrics JVM tests | 9 passed |
| Actual Room/DataStore tests on API 33 | 23 passed |
| Actual native Compose focus/semantics/layout tests on API 33 | 4 passed |
| All test failures/errors/skips | 0 |
| Lint | 0 errors; 48 existing app warnings; no data/design-system issues |
| Release manifest | Only MainActivity is registered as an Activity; all three debug preview Activities and the Compose test Activity are absent |

The final storage run includes the strengthened malformed fixture: integer obsolete version 99 and a wrong-type version on separate destinations, unrelated swapped mapping/valid snapshot preservation, and wrong-type mapping defaults. Initial schema v1 remains byte-identical; v2 retains the reviewed receipt/counter migration with no destructive fallback.

The single-use Gradle daemon warned that its default metaspace was low near the end of lint. The command completed successfully; no global host settings or unverified performance tuning were applied.

## Review

- US-005: separate Terra review cleared the corrected width/height/font/capacity metrics and native fixtures.
- US-018: separate Sol review cleared transactional recency, migration, deduplication and tests.
- US-019: separate Sol review found wrong-type decoding and doubled filename issues; both were fixed. The missing obsolete-version fixture was restored and passed the final Android run. No remaining source/lifetime/DI issue was reported.
- US-006: separate Terra review cleared focus, semantics, vectors and tests after the default 48dp minimum was moved before caller sizing and verified with an explicit 24dp request.

## Final APK and smoke

APK: `app/build/outputs/apk/debug/app-debug.apk`

SHA-256: `874b1f1f3df62fcee88214e9a4d75c95d1667945704eabb7f6332af94801bffe`

The APK installed successfully on `Handheld_Foundation_API_33` (`emulator-5556`). Standard and system-font-1.3/reduced-motion native primitive previews cold-launched and were visually inspected. Font scale was restored to 1.0 and the animator override deleted. Final MainActivity cold launch returned `Status: ok`, 1087ms reported launch time, with no AndroidRuntime errors. That one observed time is smoke evidence, not a performance guarantee.

[Final ordinary Activity screenshot](F02-F06-foundation.png). The isolated emulator was then stopped with its SDK control command.

Physical Flip 2 testing remains pending. These feature gates do not claim physical controller, lid, HOME lifecycle, density or delivery acceptance.
