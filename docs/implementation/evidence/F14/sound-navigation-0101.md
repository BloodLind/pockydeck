# Sound, sustained navigation and filter grouping — 0.10.1

Date: 9 September 2026. Follow-up to the user's five sound, fast-scroll testing and filter-row requests. Based on commit `2cd25ce`, version 0.10.0.

## Changes

- Regenerated six original mono PCM sounds with lower fundamentals (245–625Hz), a rounded 10ms attack, softer tails and less upper-harmonic content. Move, Select, Confirm, Back, Page and Filter remain distinct. No recordings, downloaded samples, dependencies or new permissions were added.
- Sound requests share a single busy interval, using the known sample duration plus 16ms. Busy input is discarded before playback or repeated AudioManager queries, without delaying navigation. Unloaded/failed playback does not reserve a voice; explicit stop, deactivation and release clear it. The SoundPool itself permits only one stream. Settings, media volume and lifecycle behavior remain authoritative. [Android's SoundPool API](https://developer.android.com/reference/android/media/SoundPool) documents the playback and stream controls used here.
- Changed card focus during controller navigation has a distinct Select cue. A synchronous expectation suppresses the generic movement sound while a lazy row is being placed. Touch, metadata-only publication, page/modal restoration, expired focus work and unchanged boundary selections do not add a selection cue. Header-to-card navigation keeps its single generic movement cue. Confirm uses the softened confirmation sound.
- All remains a fixed control before the console strip. L2, complete console cells and R2 form one quiet rounded group with equal spacing and a common center. All filters is fixed after the group. The previous loose separator and doubled hint margins are removed. Finite trigger scrolling and D-pad navigation across visible controls remain unchanged.
- The grid worker previously waited for an unchanged pending destination after a 90ms scroll animation, despite repeats reaching 55ms. It now keeps focus advancing to placed destinations. Closely spaced repeats scroll immediately; discrete steps retain their short animation. Direction reversal rejects a stale forward focus target. A pending vertical edge settles before normal header/dock exit is allowed.
- Search no longer unconditionally collapses on every navigation request, which conflicted with upward scrolling. Actual scroll direction controls the header. Only visible result cards load appropriately sized artwork; offscreen requesters are removed, cover animations pause during scrolling, and scroll anchors save after settling.

## Combined verification

303 JVM checks pass: app 130, domain 61, data 100 and shared design 12. One existing Windows-only data fixture is skipped. Lint reports zero errors and 65 warnings. Debug and release builds pass. Initial combined build: `.local/verification-0101.log` (40s). Review fixes for duplicate header/card sound and metadata consuming pending selection audio are covered by additional regressions in `.local/verification-0101-feedback.log` (26s).

Physical Android checks use the Retroid Pocket Flip 2 (`89a34d44`), Android 13, 1920×1080, at the user's 110% UI scale. Installation uses `adb install -r`; app data is preserved. The app's existing catalog is used by the Activity tests. No game is launched, ROM/save file changed, or persistent audio setting/volume altered. Isolated presentation fixtures and SoundPool lifecycle checks use their own temporary state.

25 distinct Android tests pass across:

- MainActivity input 9: real ROM Search editing, clear/close, navigation roots, modal handling, touch/controller focus, row crossing, finite filter controls, trigger report merging and acceleration, scale bounds, and sustained Search navigation.
- Collection layout 7: grouped filter geometry and spacing at 100/110/120%, unequal cell widths, list/grid behavior and preview actions.
- Search restoration 2 and query invalidation 1; Home reset 1.
- Actual SoundPool load/busy/enable/lifecycle/release 1 and all packaged WAV formats/durations 1. The playback test ran with the device's existing audible media settings; it was not skipped.
- Artwork decode/cache/background lifecycle 3.

The initial 25-case native run passed 24 (`.local/device-0101.log`, 112.426s). The new hold test initially used `SystemClock.sleep`, which advances the input engine but does not pump Compose's test frame clock. That produced two focus publications after an artificial frozen-render backlog. The fixture now pumps frames with `waitUntil` while measuring the real held duration with Android uptime. This matches the distinction in [Compose's synchronization documentation](https://developer.android.com/develop/ui/compose/testing/synchronization). No production code changed for this fixture correction. The test-only rebuild is `.local/verification-0101-hold-fixture.log` (3s); the corrected native regression passes in `.local/held-search-device-0101.log` (23.038s).

## Sustained physical-device Search workload

One injected Android gamepad DOWN and one UP delimit each hold, through the actual Activity input path. A loop of discrete button presses is not used. Search for `a` supplies 3,778 current catalog results. Rendering remains active during each hold; the engine reaches its 55ms maximum cadence after the acceleration ramp.

The seven-second Down hold publishes 91 changed selections and ends at index 219. The early 1.5-second sample (500–2,000ms) has 15 selection updates; the late sample (4,500–6,000ms) has 22. Both direction order and faster late progression pass. A 3.5-second Up reversal and four-second Right hold also pass, including crossing rows. After each release the test checks a single focused card matching the selected item, no later selection movement, no reopened keyboard, and the preserved query. Timing record: `.local/held-search-timing-0101.log`.

These are functional input/cadence observations on the physical debug build, not a release frame-time benchmark or subjective speaker-listening assessment. The previous revision's RAM/jank comparison is not reused as a measurement of this version. The sound changes are verified by synthesis parameters, policy regressions and native playback checks.

Physical visual inspection shows the new group in `.local/launcher-0101-library.png`, with PSP/Recent/List restored by the tests. `.local/launcher-0101-ready.png` shows Home. Existing 110% UI scale and sound preference remain. Runtime errors from the settled launcher process contain the existing Adreno property and OpenGL swap messages, without an AndroidRuntime crash (`.local/runtime-0101.log`).

## Artifact

- Version **0.10.1**, code **15**; installed on the physical Flip 2.
- `.local/releases/handheld-launcher-0.10.1-debug.apk`, 30,232,689 bytes.
- SHA-256: `f366a13fd96912e6d31625ccf1226f39eaf17059317ab2fbb0243cdc0cd46dff`.
- The installed version and `base.apk` checksum match the delivered artifact.
