# Getting started

PockyDeck requires Android 13 or later and runs in landscape.

## Install the preview

1. Download `pockydeck-0.10.5-preview.apk` from the [release page](https://github.com/BloodLind/pockydeck/releases/tag/v0.10.5).
2. Open the APK on your device. Allow installation from your browser or file manager if Android asks.
3. Open **PockyDeck**. Making it your default Home app is optional.

The release includes `SHA256SUMS.txt` and `SIGNING_CERTIFICATE.txt`. You can check the download with `sha256sum` on Linux, `shasum -a 256` on macOS, or `Get-FileHash -Algorithm SHA256` in PowerShell.

For USB installation:

```sh
adb -s SERIAL install --no-incremental -r pockydeck-0.10.5-preview.apk
```

The preview uses the same development signing certificate as the official 0.10.3 preview, so it can update that build while keeping settings, favorites, and history. Locally built APKs may use a different key. If Android reports a signature mismatch, do not uninstall your existing copy without considering its saved data. Official previews are release builds with debugging disabled; a long-term production signing policy is still pending.

## Add your games

- Android apps are discovered automatically.
- Install and configure your emulators, then add folders in **Settings → ROM folders**. See [ROM setup](rom-setup-guide.md), [emulator support](emulators.md), and [supported formats](rom-formats.md).
- Supported [PC-game exports](pc-games.md) can also appear in your library.

Home shows up to 20 items, prioritizing recent launches. A blue dot and matching **Last played on [platform]** tag identify the most recently launched ROM for each console. They record launch history, not whether an emulator is still running. No root or Shizuku setup is needed.

## Controls

| Input | Action |
| --- | --- |
| D-pad / left stick | Move; holding a direction accelerates |
| A | Confirm or play |
| B | Back; from a collection item, jump to filters/sorting and return |
| X | Search |
| Y | Toggle favorite; clear text while editing Search |
| Select | Details and extra actions |
| Start | Item/page menu |
| L1 / R1 | Change page |
| L2 / R2 | Change filters or move through Home cards |

**Settings → Controls → Button layout** selects Xbox or Nintendo/retro labels. Xbox places A at the bottom, B on the right, X on the left, and Y at the top; Nintendo/retro swaps A/B and X/Y. This changes the displayed labels only, keeping every action on the same physical button. Xbox is the default. The table above uses Xbox labels.

**Confirm button** separately lets you swap Confirm and Back; its displayed button names follow your selected layout. Controls also lets you adjust or disable sound and disable vibration. Repeated navigation uses quieter clicks; vibration marks the first and last action in a rapid sequence. The footer shows the actions available for the current selection.

Touch a list row to preview it, then use the footer to play or open details. In Search, A applies the query and B cancels editing.

For sliders and the color palette, Confirm enters adjustment. Use the indicated D-pad directions, then Confirm or Back to finish.

## Make it yours

**Settings → Display** offers UI scale from 90–150% in 10% steps, grid-card size, a full background color picker with presets, tint strength, grain intensity, and Reduce motion. Home and list views have separate optional blurred artwork backgrounds.

Library, Apps, and Favorites remember their own grid/list layout. On wider screens, list mode shows a large game preview beside the list; narrow layouts use a compact summary. Filters, sorting, favorites, and the position indicator help navigate larger collections.

Home preloads its small collection. Cached images appear immediately; new covers load in display order, with large collections prioritizing scrolling. Installed-app icons come directly from Android.

## Choose your Home launcher

Use **Settings → Launcher → Set as Home launcher** to choose PockyDeck. When it is already the default, **Change Home launcher** opens Android's chooser so you can select another app.

## Status and help

The top strip shows the time, battery, battery temperature, RAM use, free storage, and connectivity. The optional notification indicator needs Android Notification access. Temperature is a battery reading, not a CPU reading.

For problems, [open an issue](https://github.com/BloodLind/pockydeck/issues) with your device, Android version, emulator version if relevant, and steps to reproduce. Do not attach games, BIOS files, or personal data.
