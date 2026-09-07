# Native application base — integrated delivery

8 September 2026. Starting checkpoint: `28384f6`, branch `codex/f01-foundation`.

The user authorized implementing the full native application base first and then reconciling it with the documentation. This record replaces per-story delivery reports for the batch. It does not mark the later ROM/provider or physical hardware gates complete.

## Implemented application

| Area | Delivered behavior |
| --- | --- |
| Home | Real cached Android catalog; up to twelve items and Library action; reference-calibrated title/carousel; fitted local icons; actual focus frame; Open/Reopen labels |
| Shell | One status strip, content rectangle, six-destination dock and contextual controller footer; details and menus use the same shell |
| Library / Apps | Shared persisted catalog, category filters, recent/title sorting, keyed grid, count, refresh and empty recovery |
| Favorites / Details | Persisted favorite references, All/Games/Apps filters, full item title, availability, launch, favorite, app info and category override actions |
| Search | Local item and supported setting results; All/Games/Apps/System filters; two columns when readable and one with landscape IME; native keyboard; preserved query/origin/selection |
| Settings | Persisted A/B mapping, user-controlled HOME-role request, grouped Android settings, current system text scale and reduced-motion state; searchable launcher settings |
| Controller | One Activity owner for D-pad, hat, stick, A/B/X/Y/Start, shoulders and triggers; held-repeat cancellation, IME ownership and stable per-ID restoration |
| Launch | Save origin, suppress pending duplicates, revalidate target, dispatch, match acknowledgement, write successful recency once; failures preserve order |
| Status | Real local time, battery percentage, explicitly identified battery temperature, RAM used/total, free internal storage and Wi-Fi connectivity |

Production has no fixture cover art, running badges, invented memory-card state, session analytics or inert future setup controls. Installed emulators are ordinary launchable Android entries until the later ROM integration stage.

## Reconciliation and fixes

- Replaced the foundation Activity with the production application, including separate MAIN/HOME/DEFAULT and MAIN/LAUNCHER filters, singleTask behavior, landscape and launcher-local immersive window handling.
- Matched Home's calibrated anchors and colors at the Flip 2's actual 1920×1080, density 2.25, 853.33×480dp window. Cards reserve focus frame/lift space, and the dock retains independent 48dp touch targets.
- Fixed selected cards being painted light gray, oversized collection cards, missing dock capsule/separator/shoulder hints, and incorrect status/footer vertical placement.
- Fixed landscape IME chrome consuming the query area, no-result UI stealing query focus, stale Search query-entry requests, and result/viewport restoration.
- Query clearing and filter changes reset an absent viewport anchor to the beginning; collection filters retain their own focus during result changes.
- Fixed Settings requesting an unattached focus target; section rows now publish their current action, and details have a compact layout with an initial action focus.
- Fixed menu item identity changing when its invoking card lost focus, and restored menu focus through the shell so dock origins also work.
- Fixed delayed durable snapshots being suppressed by initial layout/selection callbacks. Three JVM regressions cover Home restoration, collection restoration, and explicit user query/filter precedence.
- Fixed a native crash during rapid shoulder navigation: requesters now remain stable by item ID and are used only after lazy placement, with lifecycle-safe disposal handling. Complete shoulder/face-button presses are no longer incorrectly suppressed by trigger deduplication.
- Aligned Favorites filters with F10, added actual searchable launcher settings, respected the system clock format, and refreshed cached app icons after catalog reconciliation.
- Corrected the shared-control scroll test to scroll to the target instead of assuming a fixed drag distance. Corrected the app test to replace a previously saved query instead of appending to it.
- Lint's inherited AndroidX restriction on the public Window.Callback override has a narrow documented suppression. Icon loading uses explicit remembered state and a keyed effect; no broad lint baseline was added.

## Verification

The combined batch covers debug APK assembly, app lint, release manifest processing, JVM logic and native Android tests. The final results and APK checksum are in [verification.txt](verification.txt).

| Suite | Passed |
| --- | ---: |
| App JVM, including input/launch/navigation/snapshot regressions | 24 |
| Domain JVM | 15 |
| Data JVM | 20 |
| Design-system JVM | 10 |
| App native integration: six destinations, Search query/IME, rapid shoulders | 3 |
| Native shared controls/layouts/modal | 16 |
| Native Room/DataStore/discovery/dispatch | 32 |

The native app tests run against MainActivity. Storage and control tests run on the isolated Android 13 emulator, serial `emulator-5556`, at the physical device's display density. Existing Room schema and DataStore encoding were preserved. Release manifest inspection excludes debug gallery/preview Activities and fixture data.

Manual emulator evidence includes Home → menu → add favorite → details → Favorites, actual Android Clock dispatch, HOT return with Clock selected at the front and the Reopen footer, cold process restart retaining Clock selection, Android HOME picker cancellation with continued browsing, and A/B swapping with the new B activation restoring the default mapping. Clock's own permission prompt remains owned by Clock/Android; the launcher does not grant it.

Home was also rendered at system font scale 1.3, then the emulator setting was restored to 1.0. Native no-match keyboard editing retained query focus and kept its clear action visible.

The connected Retroid Pocket Flip 2 accepted `install -r`, preserving launcher data. Actual Home and Library screenshots show 26 discovered Android entries, local icons and real status readings. Automated input/touch injection is not physical-button evidence. Android's UI hierarchy exporter returned a null root on the Flip 2, so physical visual inspection used native screenshots and the foreground Activity record.

## Remaining scope and device checks

- Physical D-pad/stick/trigger event shape, OEM keyboard B behavior, lid/reboot/repeated physical Home behavior, role selection by the user and measured performance remain unverified. HOME is not changed automatically.
- Trigger digital/analog reports use a 65ms deduplication window; tune only if actual hardware evidence requires it.
- F15–F17 remain later work: granted ROM folders and supported formats, emulator/version/URI compatibility, and metadata/artwork provider setup. These capabilities are not represented as completed by the native base.
- F18's full physical delivery/performance matrix is not closed by these automated checks or screenshots.

## Inspected native captures

- [Flip 2 Home](flip2-home.png)
- [Flip 2 Library](flip2-library.png)
- [Home launch and return](home-launch-return.png)
- [Item details](details.png)
- [Favorites](favorites.png)
- [Settings and controller mapping](settings-controls.png)
- [Search with native landscape keyboard](search-ime.png)
- [Home at 130% system text scale](home-font130.png)
