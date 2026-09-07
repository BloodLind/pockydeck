# US-007 controls evidence

The controls package provides `LauncherButton`, `LauncherIconButton`, `FilterChip`, `SortSelector`, and native IME-backed `SearchField` under `core.designsystem.controls`. Controls use named state and callbacks, actual Compose focus and press state, one activation modifier, unavailable reason semantics, and no app/domain dependencies.

## Verification

- `:core:designsystem:testDebugUnitTest` — passed.
- Filtered `:core:designsystem:connectedDebugAndroidTest` for `LauncherControlsTest` on `emulator-5556` — 3 tests passed.
- `:app:assembleDebug` — passed.

The instrumentation coverage includes touch activation, Enter and D-pad center activation, directional focus movement without activation, selected-versus-focused chips, disabled activation, unavailable reason semantics, native text input, and IME Search callback delivery. Device visual calibration and physical-device capture remain coordinator-owned.

Coordinator final check: the complete design-system run passed 10 metrics JVM tests and 7 Android tests (3 controls and 4 primitives) in 31 seconds on the isolated Android 13 emulator at 360dpi. This includes public-modifier `FocusRequester` → native SearchField focus, resolving a review candidate that incorrectly assumed a requester could not target a nested child. Disabled SearchField text resolves the secondary foreground while respecting explicit caller text colors. Independent Terra review found no duplicate input owner or app/domain coupling. Native frame tests confirm the focused lower edge remains visible inside the allocation with and without reduced motion. Full control-gallery visual inspection follows in US-010.
