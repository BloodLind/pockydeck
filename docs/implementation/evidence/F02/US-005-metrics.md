# US-005 native shell and card metrics

Accepted by the coordinator on 8 September 2026 after independent Terra review found no remaining actionable issues in the corrected metrics, tests, and native debug consumer.

`ShellMetrics.calculate(ShellMetricsInput)` is the single root calculation from actual available pixel bounds, density and font scale. It publishes status, content, dock and footer bounds; gutter; square Home artwork and its allocated bounds; metadata, frame and lift reservations; and compact/capacity flags. Destination content never enters this calculation.

| Criterion | Evidence | Result |
| --- | --- | --- |
| AC-01 | Standard and native 800×480dp compact fixtures; JVM bounds/non-overlap tests | Passed |
| AC-02 | Standard Home and Library fixtures use identical metrics and anchors | Passed |
| AC-03 | 1.3 system font scale, two-line title, square focused artwork contained in the content allocation, 48dp dock/footer controls; JVM short/narrow/extreme-input checks | Passed |
| AC-04 | Proportions and physical-device limitations recorded below | Passed |

## Geometry and recovery

At 1280×720dp the standard fixture follows approximate reference proportions: 3.75% gutter, 18.7% outer card width, 1.9% outer-frame gap, 35.6% card top, 85.2% dock center and 92.8% footer start. These are initial guidance, not accepted physical-device defaults. Actual Activity content on the test emulator is 1280×636.67dp because native system bars remain present; the 48dp footer minimum adapts the reference where needed.

Metadata reserves two 40sp title lines, a 14sp platform line and a 12dp gap, using the actual font scale. Card capacity accounts for both width and remaining height, including 8dp frame and 4dp lift reservations. Windows too small for 48dp artwork expose no usable card and retain readable metadata space. Dock/footer capacity checks both width and height; this says a band can contain a 48dp target, not that an entire future dock fits an arbitrarily narrow window. Bands shrink proportionally if necessary and never overlap.

Invalid density/font inputs recover safely; finite positive values retain their meaning. Double intermediates prevent overflow, final dimensions remain finite/nonnegative, and extreme finite font scale is not silently capped. Text uses native sp and compact fixtures lay out natively; no bitmap scaling is used.

## Validation

Using JBR 17, the established local Java socket-directory workaround, and the isolated Android 13 emulator:

```powershell
.\gradlew.bat --no-daemon :core:designsystem:testDebugUnitTest :app:assembleDebug
```

BUILD SUCCESSFUL in 19s: 81 tasks, 17 executed and 64 up-to-date. All 9 metrics JVM tests passed. Coverage includes standard/compact non-overlap, narrow and tiny control capacity, wide-short text fallback, large-font card containment, reference positions, empty/malformed/subnormal density and extreme finite font scale.

The installed debug APK launched `MetricsPreviewActivity` for each fixture. The coordinator inspected all three screenshots after native UI idle; no AndroidRuntime error was logged. System font scale was restored to 1.0 afterward. The Activity is debug-only and presents evidence fixtures, not a completed product shell.

- [Standard Home fixture](US-005-standard.png)
- [Native compact fixture at 1.3 font scale](US-005-compact-large-font.png)
- [Library fixture with the same shell anchors](US-005-library.png)

Physical Retroid Pocket Flip 2 density, controller and lid behavior remain pending; emulator evidence does not establish device fidelity. US-006 owns reusable visual primitives and their actual focus/semantics tests.
