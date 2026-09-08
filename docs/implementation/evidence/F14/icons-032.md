# HTML preview icon correction — 8 September 2026

Version **0.3.2 (code 5)** replaces the custom dock and status drawings with the Material Symbols used by the HTML design preview. Home, Library, Apps, Favorites, Settings and Search use its outlined weight-200 variants. Temperature, memory, storage, Wi-Fi and battery use its named status symbols and fill variants. This supersedes the heavier-icon choice recorded for 0.3.1.

The existing icon sizes, colors, typography, spacing, hit targets and accessibility descriptions are retained. All 11 upstream paths are copied into native Android vectors without redrawing; the original exports, hashes and license are recorded in [asset provenance](../../../references/material-symbols/README.md).

The final validation batch passed **20 native design-system tests**, debug and unsigned release builds, and lint with zero errors. One added lint warning identifies the official Apps path length; its geometry is intentionally preserved. [Command results](icons-032-verification.txt) include the test environment, warning counts and APK checksum. Earlier broad feature tests were not rerun for this icon-only correction.

The [emulator screenshot](icons-032-emulator.png) and [physical Flip 2 screenshot](icons-032-flip2.png) were inspected at 1920×1080/360dpi. All six dock symbols have the lighter outlines, and the five status symbols match the reference shapes. The physical device was awake for this capture. Its UiAutomator hierarchy dump failed, so physical evidence is limited to the actual screenshot, successful installation and Activity startup.

Version 0.3.2 is installed on the connected Flip 2. The installable debug APK is `.local/releases/handheld-launcher-0.3.2-debug.apk`.
