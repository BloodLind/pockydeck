# Collection layout and memory — 0.10.0

Date: 9 September 2026. Follow-up to the user's eight collection-layout and performance requests.

## Changes

- Decorative L2/R2 filter hints are smaller, darker and rounder, with more surrounding space. All filters has a larger text/icon gap and separation from the strip.
- The Sort popup uses smaller choices and a slightly larger Close control, retaining native touch allocations.
- List mode splits the content into a left list and a right selected-item preview. Touch rows select without launching; explicit Play/Open launches and Details opens existing item controls. Controller A launches the focused row, Right reaches preview actions, and Left from Open returns to the row. Compact windows retain a selected-item action or use existing Details when height is very limited.
- Settings → Display adds independent 70–140% grid sizing in ten-point steps. It changes collection artwork and columns without changing typography. Grid/List choices remain separate per destination.
- Internal storage glyphs are cyan and external glyphs amber; unknown and critical-low states keep their existing treatment.
- Only visible collection cards request images. Covers use bounded streams and physical-pixel decode buckets; icons and covers limit concurrent work and reuse same-size requests. Metadata priority/access-time updates no longer reload unchanged covers. Scrolling suppresses cover/loading animations.
- Backgrounding clears bitmap caches, cancels active image work and releases painter delegates even if Compose is stopped. A foreground generation restarts cleared requests after a rapid background/return pair. Cache eviction does not recycle images still held by a draw operation.
- Scroll positions save when scrolling settles. Filter keys, counts and measurements are prepared/reused instead of rescanning the catalog during input. Disposed cards remove their focus requesters.

No Room migration, new permission, emulator setting or ROM/save/frontend-file change is included.

## Physical profiling method

The physical Retroid Pocket Flip 2 (`89a34d44`) runs Android 13 at 1920×1080, density 2.25, UI scale 110% and normal Android font scale. The populated Library contains 3,768 games. At the start of this revision the user's Library was SNES/Recent/List; profiling temporarily used All/Grid at default grid size. No game was launched.

`adb shell dumpsys gfxinfo dev.handheld.launcher reset` starts each frame sample. Each touch round sends 12 upward swipes from (1050,738) to (1050,365), 280ms each with 160ms between commands. Frame deadlines and percentiles come from Android's `gfxinfo`. `dumpsys meminfo` supplies the launcher's PSS, Java/native heap and graphics allocation; the status bar's system RAM total is not used for app memory measurements. A separate controller sample sends 24 Right inputs with 90ms spacing after controller focus is restored.

These are debug-build device samples, not a controlled release benchmark. Runtime compilation, cache warmth, artwork coverage, background activity and temperature can vary. Repeated foreground/background samples test whether memory continues growing and whether covers return correctly; they cannot prove that no leak exists under every usage pattern.

### Previous installed build: 0.9.1

| Sample | Frames | Missed frame deadlines | p90 | App PSS |
|---|---:|---:|---:|---:|
| Touch round 1 |150|49.33%|97ms|331,535KiB|
| Touch round 2 |150|49.33%|97ms|316,913KiB|
| Touch round 3 |179|41.34%|97ms|319,201KiB|
| Controller 24 Right |181|30.94%|73ms|334,587KiB|

After returning Home, PSS was 283,325KiB. Opening Android Settings backgrounded the launcher without changing settings; background PSS was 253,045KiB and later 250,697KiB, including 60,584KiB of graphics memory. The three scrolling samples did not show sustained growth; the code review identified avoidable image retention and work rather than claiming a measured unbounded leak.

Baseline logs: `.local/frames-010-before-round1.txt` through `round3.txt`, `frames-010-before-controller.txt`, and matching `memory-010-before-*.txt`. Initial screenshots: `.local/launcher-010-baseline.png`, `launcher-010-library-baseline.png`, `launcher-010-profile-grid.png`.

### Updated build: 0.10.0

| Sample | Frames | Missed frame deadlines | p90 | App PSS |
|---|---:|---:|---:|---:|
| Touch round 1 |361|7.20%|26ms|228,792KiB|
| Touch round 2 |359|6.13%|27ms|270,079KiB|
| Touch round 3 |359|5.57%|20ms|237,699KiB|
| Controller 24 Right |254|14.57%|34ms|222,437KiB|

The average sampled scrolling PSS decreased from 315.0 MiB to 239.8 MiB, approximately 24%. After Home, PSS was 223,893KiB; background PSS was 204,142KiB with 45,032KiB graphics. The updated debug build still misses some frame deadlines, particularly during controller selection; these figures show an improvement, not a zero-stutter guarantee.

Updated logs: `.local/frames-0100-after-round1.txt` through `round3.txt`, `frames-0100-after-controller.txt`, and matching `memory-0100-after-*.txt`. The initial updated grid/storage/filter appearance is captured in `.local/launcher-0100-grid-first.png`.

### Repeated foreground/background memory checks

On the final APK, eight cycles each scroll three more times, wait one second, sample memory, open Android Settings, wait two seconds, sample again and return to the launcher. The same launcher process remains alive throughout; no forced GC or process reset occurs during these cycles.

| Cycle | Foreground PSS, KiB | Background PSS, KiB |
|---|---:|---:|
|1|204,285|264,675|
|2|214,260|210,307|
|3|200,823|189,358|
|4|189,509|178,949|
|5|265,285|193,572|
|6|269,112|165,667|
|7|210,135|185,995|
|8|210,281|187,107|

Allocation/collection fluctuations remain, including a higher first background sample. There is no sustained upward trend: the final foreground value is close to the first, and final background memory is lower. The eighth sample still has one Activity, one ViewRoot and eight Views. Covers are present after the eighth return (`.local/launcher-0100-after-cycles.png`). These observations support bounded retention in this workload; they do not prove the absence of every possible leak. Logs are `.local/memory-0100-cycle1-foreground.txt` through `cycle8-background.txt`.

## Integrated validation

357 distinct tests pass: 116 app JVM, 61 domain JVM, 100 data JVM, 12 shared-design JVM, 30 app Android, 15 preference Android and 23 shared-design Android. One existing Windows-only data fixture is skipped. Lint has zero errors, 65 warnings and 10 informational findings. Debug and release APKs build successfully.

The first combined build found a Kotlin generated-setter/method name collision in the artwork lifetime gate; renaming the method resolved it. The completed combined build is `.local/verification-0100-fixed.log` (73 seconds). The first app native run (`main-device-0100.log`,130 seconds) passed 26 of 29 cases. Corrections address fixture behavior: text can occupy one line while reserving a two-line caption allocation; isolated controller fixtures must request Keyboard input mode; cleanup must keep pumping Compose while waiting for a debounced scroll/focus save.

Review also found and fixed a production preview edge case: replacing/removing the selected item while Open/Details retains focus could leave the root Confirm callback targeting the old item. Stable current-state handlers and republished item metadata address it. The focused nine-case run passed eight; its new preview fixture needed the same explicit input-mode setup. That corrected regression passes in `.local/preview-device-0100.log` (1.835seconds), covering both retained Open and Details focus. All three artwork decoder/lifetime checks pass, including a background/return while the frame clock is paused and offscreen callback cleanup. The preference suite passes 15 cases (`data-device-0100.log`); shared controls pass 23 (`design-device-0100.log`).

Physical inspection verified grid 70/100/140 at UI 110, unchanged caption size, new filter/hint spacing, storage colors, split List information, and the Sort popup. A last visual correction retained Sort's full-width row treatment while reducing row height 56→48dp and text to 90%; Close is 110%. This styling-only correction rebuilt debug/release/lint and was inspected in `.local/launcher-0100-sort-final.png`; it does not change the measured grid paths. Supporting build logs are `verification-0100-final.log`, `verification-0100-fixture.log` and `verification-0100-sort.log`.

The installed app uses `adb install -r`; no app data was cleared and `:app:connectedDebugAndroidTest` was not run. The data/design suites target their isolated test packages. The user's SNES/Recent/List choice and 110% UI scale are restored; the new grid setting is left at its 100% default. A launcher-only process restart verifies the restored Library (`launcher-0100-library-restored.png`). Home is left on its first card in touch mode with working live RetroArch/PPSSPP/app badges (`launcher-0100-home-final.png`). Sounds, all-files access and the existing Shizuku grant are preserved.

The runtime error log has no AndroidRuntime crash. It does record the graphics driver's existing initialization/swap messages and a nonfatal Shizuku-provider `unlinkToDeath` exception on startup; live process readings and badges still work. This dependency-level log message is recorded in `.local/runtime-0100-final.log` and was not changed as part of the collection/memory work.

## Delivered artifact

- Version **0.10.0**, code **14**.
- `.local/releases/handheld-launcher-0.10.0-debug.apk`, 30,816,314 bytes.
- SHA-256: `372e9b6938ca94d0edfe651716d791712011f56f7065f401605b6a53ef5e52f9`.
- Installed package version and on-device `base.apk` checksum match this artifact.
