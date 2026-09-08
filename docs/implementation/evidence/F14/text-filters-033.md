# Small text and reference filters — 8 September 2026

Version **0.3.3 (code 6)** increases the smallest text after reference scaling and before Android system font scaling. The curve passes through the user's examples, 3→6sp and 9→11sp, and tapers to zero at 14sp. Line height grows proportionally. On the Flip 2, badge text changes from 6.67 to 9.06sp, control text from 9.2 to 11.12sp, and the clock from 10.73 to 12.04sp. The top strip retains regular text weight and allows its text height to grow. Card allocations, dock geometry and the lighter preview icons retain the established design.

The Library strip follows the HTML collection preview: compact counted pills, pale selected fill, muted inactive text and subtle outlines, L2/R2 hints, and More. Six primary filters are shown; the chooser exposes all available filters and a selected overflow console stays in the primary strip. Console labels remain abbreviated. The More chevron is the original Material Symbols Outlined weight-200 path, recorded in the existing asset provenance. Filters use the same availability, category and favorite rules as their destination.

The physical catalog revealed that ES-DE, GameNative and Artemis declare themselves as games. Exact package exceptions now classify these frontends as Apps while preserving user category overrides. With the previously approved All files access enabled, Library shows **3,773 ROM entries**; examples include GBA 440, PSP 1 and PS2 6. No ROM, save, ES-DE or PC-game files were changed.

## Verification

The combined validation passed **152 tests**: 62 app JVM, 70 data JVM and 20 native design-system tests on the physical Retroid Pocket Flip 2. One Windows symlink fixture was skipped. Debug and unsigned release builds passed; lint reported zero errors. [Verification details](text-filters-033-verification.txt) record commands, environment and APK checksum. No Android emulator was used for this revision.

Two focus failures found on the device were corrected: opening a modal from touch could leave focus behind it, and filtering out the active card could move focus to L2. The dialog now requests an actual option after placement. A removed card retains restoration intent until its replacement is placed. Final gamepad-source key injections on the physical device verified R2 from PSP to PS2 and L2 back to PSP with a game card focused; A opens More and applies All; B dismisses More and returns focus to its opener. These checks do not substitute for a full physical-button, analog-stick or game-boot acceptance matrix.

Fresh device screenshots were inspected:

- [Home at 100% text](text-filters-033-home.png): larger badges/status/footer text and the existing lighter icons.
- [Library after A applies All](text-filters-033-library.png): 3,773 entries and compact counted filters.
- [R2 switches to PS2](text-filters-033-trigger.png): a replacement game card has the amber focus frame.
- [More chooser](text-filters-033-more.png): first real option focused.
- [Settings at 100% text](text-filters-033-settings.png) and [130% text](text-filters-033-settings-130.png): supporting text and controls remain readable.
- [Library at 130% text](text-filters-033-library-130.png): the compact shell and scrollable grid remain usable; a full tall card caption extends below the initial viewport. This is not certification of every enlarged-font layout.

System font scale was restored from 1.3 to the original **1.0**. The final APK was installed successfully and Android reports version 0.3.3/code 6. UiAutomator returned a null root on this device; screenshots were captured directly and no stale hierarchy data was used as evidence.

## Remaining requested work

Local ES-DE gamelists and downloaded media were found on the SD card, along with Windows `.steam` shortcuts pointing to GameNative. GameNative 1.2.0 is installed; GameHub was not found. The questions about that PC integration target and local-only versus online metadata remain unanswered. ES-DE import, PC shortcut integration, lazy ROM artwork with a pending indicator, and batched discovery/search improvements are not part of this APK. Existing fallback artwork is visible in the screenshots.
