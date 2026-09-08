# Getting started

PockyDeck requires Android 13 or later and is designed for landscape handhelds. Android currently lists the app as **Handheld Launcher**.

## Install the preview

1. Open the [0.10.3 release page](https://github.com/BloodLind/pockydeck/releases/tag/v0.10.3).
2. Download `pockydeck-0.10.3-preview.apk` and `SHA256SUMS.txt`.
3. Check the APK hash against the checksum file, then open it on the device. If Android asks, allow APK installation for the browser or file manager you are using.
4. Open Handheld Launcher. Choosing it as the default Home app is optional.

On a computer, compare hashes using `sha256sum pockydeck-0.10.3-preview.apk` on Linux, `shasum -a 256 pockydeck-0.10.3-preview.apk` on macOS, or:

```powershell
Get-FileHash .\pockydeck-0.10.3-preview.apk -Algorithm SHA256
```

The preview is built with the release variant, with debugging and the development preview screens excluded. It is signed using the existing development certificate so it can update the earlier project-device builds with that same certificate. It is not a production signing commitment. The release includes `SIGNING_CERTIFICATE.txt` with the certificate fingerprint.

Android requires a matching signing certificate for an in-place update. A locally built debug APK normally uses your own machine's key and may not update the downloaded preview, or vice versa. Do not uninstall an existing copy just to resolve a signing mismatch without considering its local favorites, settings, and history. A future production-key transition may require a separate installation or migration; no automatic transition is promised.

To install through USB, enable Android USB debugging, connect the device, accept its authorization prompt, and use the serial from `adb devices`:

```sh
adb -s SERIAL install -r pockydeck-0.10.3-preview.apk
adb -s SERIAL shell am start -n dev.handheld.launcher/.MainActivity
```

## Choose your Home screen

Use **Settings → Launcher → Set as Home launcher** and select Handheld Launcher in Android. To switch away later, open Android **Settings → Apps → Default apps → Home app** and choose another launcher.

## Find your games

Android applications are discovered automatically. Library contains Android games, ROMs, and supported PC exports. Apps contains other Android applications. Favorites contains available items you have starred. Home shows up to 20 items, with successful recent launches first and console representatives for consoles not yet represented.

See [ROM setup](rom-setup-guide.md) for folders, emulator selection, supported archives, and recovery. [PC games](pc-games.md) explains frontend exports.

## Controls

| Input | Action |
| --- | --- |
| D-pad / left stick | Move focus; long holds accelerate |
| A | Activate or open the selected item |
| B | Close the current modal or go back from Details, Search, or Settings |
| X | Open Search |
| Y | Item details; Clear while editing Search |
| Start | Item/page menu |
| L1 / R1 | Change destination |
| L2 / R2 | Change supported filters or move through Home cards; hold to repeat |

**Settings → Controls** can swap A/B and disable controller sounds. The footer reflects the active mapping and context. Sounds use Android media volume. Fast input skips overlapping cues instead of queuing them.

Search uses A Apply and B Cancel while editing. Selecting the collapsed query edits it again; the round × clears it. Closing Search resets its query. Touch selects items without a controller outline; controller navigation shows the focused item.

Home and filter galleries are finite. Library/Apps/Favorites do not turn B into an automatic jump to Home; use the Home destination. Closing a modal returns to its underlying page.

## Display and collections

**Settings → Display** provides UI scale at 90%, 100%, 110%, or 120%, a separate grid-card size from 70% to 140%, and Reduce motion. Android font scaling remains independent.

Library, Apps, and Favorites remember their own Grid/List selection. List mode shows items on the left and selected-item information on the right. Touch a row to preview it, then use its round Open, Details, or Favorite controls; controller A launches the selected row.

## Device status

The strip shows time, battery percentage, **battery temperature**, used/total RAM, and available internal/external storage. Temperature is not a CPU reading. Battery is green while charging, red at 10% or below, yellow below 15%, and neutral otherwise.

Wi-Fi uses an icon; Bluetooth appears when its radio state is available and enabled. The optional notification indicator requires Android Notification access and shows only whether notifications exist.

## Optional running indicators

Running indicators are off by default and require the separate [Shizuku helper](https://shizuku.rikka.app/download/).

1. In **Settings → Launcher**, enable **Running indicators**, then choose **Set up Shizuku**.
2. Install and start the helper using its [official setup guide](https://shizuku.rikka.app/guide/setup/).
3. Allow Handheld Launcher in Shizuku's permission dialog.

While the launcher is foreground, it samples requested current-user package processes every three seconds. Badges appear only on Home. An app badge means a process was observed. A ROM badge names its associated emulator when that emulator process is observed; it does not prove that specific ROM is playing.

The debugging-based helper needs restarting after a device reboot. Turn Running indicators off to stop sampling, or revoke authorization in Shizuku. Other launcher features work without the helper.
