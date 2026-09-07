# US-008 cards and labels evidence

Baseline: `bc60032` plus the Home milestone native calibration. Luna supplied the initial APIs; coordinator integration corrected the actual variant layouts, image sizing, shared geometry and tests before acceptance.

Production controls now provide:

- `CoverTile(HomeCover)`: square artwork using caller-supplied Home allocation/frame/lift, without a second caption below Home's row.
- `CoverTile(CollectionCover)`: square artwork and a fixed two-title-line/one-subtitle-line caption area.
- `AppIconTile`: a bounded fitted icon and centered title inside the tile, matching Home's native-app fallback composition.
- `SearchResultCard`: horizontal artwork/caption layout.
- `CoverArtwork` and `AppIconArtwork`: local painter slots with explicit Crop and Fit behavior; no image fetching.
- `ArtworkFallback`, `TileCaption`, `PlatformBadge` with Home accent option, truthful `StatusIndicator`, and `ControllerGlyph` using the existing physical-face glyphs and bounded shoulder legends.

All cards share one actual focus/press/activation path and accept unavailable reason independently from enabled state. Controls contain no domain, navigation, repository or emulator types. Native theme scaling and system font scale remain separate.

Verification: full `:core:designsystem:testDebugUnitTest` passed 10 metrics tests; full `:core:designsystem:connectedDebugAndroidTest` passed 10 Android tests (4 primitives, 3 input controls, 3 card/label tests), and `:app:assembleDebug` passed. The first card-test compile required the native input-mode API opt-in, then the complete run passed in 35 seconds. All tests targeted only the Android 13 emulator at 360dpi.

The revised tests actually replace loading → failed → loaded artwork, verify unchanged allocation and exact inner dimensions against `ShellMetrics`, request native focus without activation, and activate once. Non-square striped painter pixel checks distinguish Crop from Fit without stretching. Long-title/subtitle changes preserve collection height, native icons stay smaller than their tiles, unsupported status is omitted, and meaningful shoulder labels hide duplicate raw glyph text.

The [native variant render](US-008-cards.png) was captured by instrumentation and inspected by the coordinator: square collection art, bounded ellipsized captions, modest fitted native icon, horizontal result row, Home accent badge, and status/shoulder examples. The RGB stripes are deterministic image-scaling test data. This is a control fixture, not a finished Home screenshot. A single render test was rerun directly to retain the PNG before Gradle's test-APK cleanup.

Independent Terra review found no cards-layer defect. Its proposed lift-allocation mismatch used a stale primitive: current `FocusFrame.padding(focusLift)` reserves both sides. The new exact-inner-size test passed, confirming the current metric contract. Full fixture Home and all-control visual comparison follows in US-010/F04.
