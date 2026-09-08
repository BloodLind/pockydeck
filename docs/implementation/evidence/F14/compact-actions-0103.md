# Compact action refinement — 0.10.3

Date: 9 September 2026. Based on `2d2f145` / 0.10.2. The user found the previous action buttons too large and wanted Search controls to fit the existing design.

## Changes

- Text actions use the normal subdued control color, a quiet outline, pill corners and medium-weight control typography. Single-line surfaces are 36dp high inside the existing 48dp touch allocation, with a 64dp minimum target width instead of 80dp. Longer labels may grow vertically. The transparent inset still receives touch/controller activation.
- List-preview actions use smaller 36dp visible circles inside their 48dp targets. The 24dp light glyphs and Favorite semantics remain; the actions are grouped more closely.
- Collapsed Search shows the current query with the existing search glyph. A round × clears it, and the muted result count is aligned to the right. The query action still exposes Edit search to accessibility and the controller footer. Long queries truncate on one line while reserving room for Clear and the count.
- Clear retains the existing behavior: reset the query and Cancel baseline, then edit with the native keyboard. The mapped A/B and Y Clear shortcuts are unchanged. Filters, Sort, shell, cards and footer retain their existing styling and three-category behavior.
- The × is the official Material Symbols Outlined `close` glyph at weight 200/FILL 0, bundled with its SVG/hash in [asset provenance](../../../references/material-symbols/README.md). No dependency, permission or storage-contract changes.

## Verification

The combined build passes with 309 JVM tests: app 136, domain 61, data 100, design system 12. One existing Windows data fixture is skipped. Lint has zero errors and 65 warnings; debug and release APK builds pass. Build log: `.local/verification-0103.log` (48s).

All device work uses the physical Retroid Pocket Flip 2 (`89a34d44`), Android 13, at the user's 110% UI scale. Installation uses `adb install -r`; existing launcher data is retained. No game is launched, ROM/save file modified, scan/removal triggered, or audio/display preference left changed.

50 distinct Android checks pass: MainActivity input 9, collection presentation 7, page presentation 5, Search focus restoration 2, and shared design-system controls/cards/foundation/modal/settings 27. The input batch includes seven seconds of held D-pad Down in populated Search, reversal, horizontal row traversal and stable focus after release. The shared tests verify that the smaller painted surfaces retain full touch targets, including taps in the transparent inset, and that labels still fit at 130% Android font scale.

The initial app run passed 20/23 (`.local/device-0103.log`, 122.783s). Three older page fixtures expected the obsolete All content description, automatic editor focus on passive entry, or a page-restoration request to steal an active edit. They now use the stable All tag, explicitly request query focus for typing, and apply the edit before expecting result focus. All five page tests pass after that test-only correction (`.local/device-0103-fixtures.log`, 9.253s; build `.local/verification-0103-fixtures.log`, 2s). The added Clear-icon touch/Cancel-baseline check passed in both runs. All 27 shared tests passed on their first run (`.local/designsystem-device-0103.log`, 35.952s).

Physical screen review confirmed the compact query/Clear row, truncation of a long query without hiding Clear/count, the smaller List circles and Sort Close. Tapping × cleared the long query and opened the empty editor; Cancel and leaving Search did not restore it. Library is left in its original PSP / Recent / List configuration.

- [Search](compact-actions-0103-search.png)
- [Long query and visible Clear control](compact-actions-0103-search-long.png)
- [List actions](compact-actions-0103-library.png)
- [Sort Close](compact-actions-0103-sort.png)

Version 0.10.3, code 17, is installed. The saved APK `.local/releases/handheld-launcher-0.10.3-debug.apk` is 30,156,802 bytes. Its SHA-256 matches the installed `base.apk`: `d3afaf33f756518da19661b429980ef70434bd6c969c6f92307fb5049aa8ad41`.
