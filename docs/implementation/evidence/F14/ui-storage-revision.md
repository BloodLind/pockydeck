# UI and shared-storage revision — 8 September 2026

This revision follows the user's combined queued requests and approval of All files access. Implementation was grouped before integrated verification. Earlier feature acceptance records remain historical.

## Delivered behavior

- Original reference proportions, corner radii and card/title sizing with a modest 15% increase to small status, footer/control and Settings text/glyphs. Status text is regular and icons heavier. Compact category pills retain separate 48dp touch targets.
- Outlined unselected cards, focus/press transitions and reduced-motion handling. Completed taps establish card selection before activation; drags do not launch. Page selection and reselection restore a surviving card; empty pages retain an actionable entry point.
- Home promotes one newest available successful open, then Android games, ROMs and remaining apps, alphabetically within each group. The carousel includes the full available catalog.
- Library contains ROMs and Android games. Apps contains other Android apps, including emulators. Favorites includes any available favorited item. Filters share these boundaries and obsolete saved choices return to All.
- Search requires a nonempty query, retains genuine zero-match results, and sizes system results like catalog cards. Downward result scrolling collapses its header. Editing restores the header; mapped Confirm applies and mapped Back cancels to the entry query while ordinary keyboard editing stays native.
- ROM cards use abbreviated console names and console-specific accents. Metadata IDs and full console names remain unchanged.
- Explicit Android All files setup, read-only discovery on mounted internal/SD/USB public storage, exact console folder aliases and actual-game checks before adding roots. Manual folder grants remain available.
- Stable volume/document identities, additive Room 3→4 migration, identity-preserving manual adoption, removal tombstones, and recovery after permission loss or disconnected storage. Discovery uses persisted bounded continuation and avoids rescanning every registered source on every slice.
- A read-only registered-folder document provider supplies companion-file access for emulator adapters without exposing unrelated storage.

## Integrated verification

Final result: **301 passed**, one skipped, zero failures/errors. JVM: app 61, data 70 passed plus one skipped, design system 10, domain 47. Android 13 instrumentation: app 31, data 62, design system 20. The generated Room 4 schema is included with the migration; no schema identity hash was handwritten.

Validation was consolidated after the implementation batch. The initial native Search test exposed transient query-field refocus during keyboard dismissal. The final implementation prevents that transition from reopening an edit session; mapped Apply/Cancel and ordinary keyboard deletion now pass through the real Activity. The UI correction also updated the existing compact-pill test's painted-size expectation while retaining its independent 48dp target and no-overlap checks.

Final command records are in [ui-storage-verification.txt](ui-storage-verification.txt). Debug and unsigned release builds succeeded. Lint has zero errors (app 74 warnings, data 2, design system 6). No temporary input diagnostics remain.

## Native rendering and discovery

Inspected at 1920×1080/360dpi: [Home](ui-revision-home.png), [Library](ui-revision-library.png), [Settings](ui-revision-settings.png), and [130% font scale](ui-revision-font130.png). Home title/artwork geometry remains at the reference proportions; shell landmarks are restored and the START legend fits with enlarged text. Collection/system result geometry and page/card focus are covered by native tests. An actual footer touch opened Search; a no-match query applied with A showed zero results.

Through Android's actual All files permission screen, the final build discovered GBA, NES and PSX test folders and indexed four selectable games. The CUE/BIN pair formed one item; an empty SNES folder did not create a console; an ISO outside a named console folder was not automatically registered. The read-only provider, removal/restore, source takeover, migration and interrupted/remounted continuation paths have separate automated regressions. Real SD/USB connection combinations are not inferred from this internal-storage smoke.

Version **0.3.1 (code 4)** was installed with `adb install -r` on the Flip 2. Android accepted MainActivity startup and reported the expected version. The physical device remained dozing, so no fresh physical visual acceptance is claimed. All files access on the physical device remains a user action under **Settings → ROM folders → Set up automatic discovery**.

Stable debug APK: `.local/releases/handheld-launcher-0.3.1-debug.apk`. SHA-256: `09952ea5505b0893068dbf78b844b5590a45590532c9bd09386b5cae49d3af15`.

## Evidence limits

Generated ROM fixtures verify folder recognition, indexing, grouping, selection and provider reads. They are not playable games and do not establish emulator game-boot compatibility. Physical controller mapping, lid/HOME behavior and the full removable-media/emulator matrix remain separate device acceptance checks.

One JVM symlink fixture is skipped when the Windows host cannot create symbolic links. The Android provider suite separately exercises a real symlink escape in app-private fixture storage.
