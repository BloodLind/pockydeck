# Action controls and three-category filters — 0.10.2

Date: 9 September 2026. Based on `ee8e2ed`, version 0.10.1. This revision addresses the user's List-preview actions, three visible categories, and explicit button readability requests.

## Changes

- List previews group circular Open, Details and Favorite controls beside the selected item's information. Each target is 48dp; the light Material glyph remains 24dp instead of stretching to fill its target. Narrow previews retain Open and Favorite. The star reflects the saved favorite state and uses the existing stable-ID favorite callback. Controller focus, footer descriptors and captured callbacks follow item replacement and updated favorite state.
- Explicit text actions use the full painted button area, a stronger fill/outline, 12dp corners, a minimum 80×48dp target and the larger Settings-value typography. Labels can wrap to two lines. Clear, Edit search, Scan, chooser actions and dialog Close share this treatment. Category filters, Sort and the footer keep their existing styling.
- The finite category strip shows three complete populated categories when space permits. Its final window backfills with preceding categories. The header reserves the widest three-category window before deciding whether to wrap. Fewer actual categories or a genuinely narrow window reduce the count without inventing or clipping targets. Selecting a wider later category keeps it inside the resulting window. D-pad traverses only visible controls; L2/R2 retains the existing finite stepping and hold behavior.
- Play and Information are bundled Material Symbols Outlined, weight 200/FILL 0, with upstream SVGs and checksums in [the existing asset provenance](../../../references/material-symbols/README.md). No runtime dependency, permission, catalog schema or scan contract changes.

## Verification

309 JVM checks pass: app 136, domain 61, data 100, shared design 12. One existing Windows data fixture remains skipped. Lint reports zero errors and 65 warnings. Debug and release builds pass. Final build log: `.local/verification-0102-final.log` (34s).

Android validation uses the physical Retroid Pocket Flip 2, serial `89a34d44`, Android 13, 1920×1080. The existing catalog and 110% UI setting are retained. Installation uses `adb install -r`; no launcher data is cleared, game launched, or ROM/save file changed. Tests restore temporary layout/filter/display changes.

47 distinct Android checks pass across the 20-case app selection and 27 shared design-system cases. Coverage includes three complete filter cells at 100/110/120%, unequal widths and final windows, touch/controller preview actions, current-item favorite callbacks, compact layouts, Search editing/clear/cancel, native triggers, row traversal, dialog focus restoration, filled action bounds, and 100/130% Android font-scale labels.

The initial app run passed 17 cases. The first three could not enter the UI while the device was Dozing, including teardown errors (`.local/device-0102.log`, 124.066s). After waking the device, those three passed (`.local/device-0102-awake.log`, 36.851s). The sustained Search case uses one native key-down/key-up pair per hold: seven seconds Down, 3.5 seconds Up, four seconds Right. Through 3,778 results, the Down hold recorded 88 selection changes, with 14 in its early interval and 23 in its late interval; reversal, row crossing and stable focus after release passed. Timing: `.local/held-search-timing-0102.log`.

The initial shared-design run passed 26/27 (`.local/designsystem-device-0102.log`, 35.961s). Its new label check exposed a mismatch between a centered text paragraph's width and its reported bounds, although the real Search screenshot displayed the label correctly. Constraining the paragraph to its intrinsic width fixes that mismatch. Visual review also caught icon targets stretching their decorative glyphs; a centered content container preserves the requested glyph size. All 11 affected control/dialog cases then passed, including the added icon-bounds assertions (`.local/action-controls-device-0102-final.log`, 16.116s).

The final APK also passes all nine repeated app cases covering Search apply/cancel/clear/close and all collection presentation tests (`.local/device-0102-final-actions.log`, 31.073s).

## Physical screen review and installation

At the user's 110% scale, the Library header displays PSP, PS2 and GBA together with balanced trigger hints. The preview's round Open, Details and Favorite actions fit below its information. A real favorite toggle changed the star and label, survived leaving for Apps and returning, and was then restored to the original unfavorited state. Sort Close, the Search Edit/Clear row and ROM-folder Scan buttons were visually inspected; no scan or removal was triggered.

- [List preview and three categories](action-controls-0102-library.png)
- [Favorite selected](action-controls-0102-favorite.png)
- [Search actions](action-controls-0102-search.png)
- [ROM-folder Scan actions](action-controls-0102-scan.png)
- [Sort Close](action-controls-0102-sort.png)

Version **0.10.2**, code **16**, is installed. Device `base.apk` SHA-256 matches the saved debug APK at `.local/releases/handheld-launcher-0.10.2-debug.apk`: **30,529,557 bytes**, `9942c748a54ea59bb32595596786299646b293b1ff19fe7691172f43a54ae540`. Library is left in its original PSP / Recent / List configuration at 110% UI scale, with the Search query reset by leaving Search.

These are targeted UI/input checks, not a new emulator compatibility, scraping or memory benchmark. Synthetic input passes through the actual Android Activity path; physical finger feel and subjective sound preference remain user assessments.
