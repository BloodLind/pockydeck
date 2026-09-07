# Native Home calibration follow-up

Source baseline: `bc60032` plus the sustained Home milestone changes. The actual Retroid Pocket Flip 2 reports Android 13, 1920×1080 landscape at 360dpi (Compose density 2.25), font scale 1.0. The prior emulator used 240dpi. The correct immersive device viewport is 853.33×480dp.

Coordinator-owned `ShellMetrics` and `LauncherTheme` changes convert the approximately 1280-unit design into native dp/sp tokens. A 2/3 reference scale is independent of Android font scale. This native viewport stays in reference mode. The shared dock/footer have separate visual anchors and non-overlapping 48dp interaction bands; the visible dock circles are 32dp. Metadata is at 14.9%, focused card top at 35.6%, outer card width at 18.7%, and footer divider at 92.8%. Dock center is clamped from 408.96dp to 408dp so its target ends at the footer's 432dp interaction boundary.

The card surface now includes the reference's lower hard edge, restrained shadow and inset highlight. Reserving two lift widths on each axis contains the edge while preserving square artwork and reference outer-card size/pitch. Reduced motion keeps the immediate amber frame and depth while suppressing lift. Terra review accepted the native geometry and the bounded depth-reservation proposal; the metrics fixture was corrected to use distinct visual anchors.

Verification: `:core:designsystem:testDebugUnitTest :core:designsystem:connectedDebugAndroidTest` passed on the isolated Android 13 emulator at 360dpi: 10 metrics JVM tests and 7 native UI tests, zero failures/errors/skips. Tests cover finite extreme inputs, compact and large-font geometry, real physical-density proportions, non-overlapping bands, stable square allocations, actual focus and lower-edge pixels, reduced motion, minimum target sizes, and native input controls.

This follow-up verifies shared native geometry and primitives. The complete Home screenshot comparison and actual controller/lid/default-HOME loop remain with their integrated consumers; this record does not mark those physical gates passed.
