# Getting started

PockyDeck requires Android 13 or later and is designed for landscape handhelds. Current source builds appear as **PockyDeck**. Published preview 0.10.3 still appears as **Handheld Launcher**; its controls differ from the unreleased changes described below.

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

Use **Settings → Launcher → Set as Home launcher** and select PockyDeck (Handheld Launcher in 0.10.3) in Android. To switch away later, open Android **Settings → Apps → Default apps → Home app** and choose another launcher.

## Find your games

Android applications are discovered automatically. Library contains Android games, ROMs, and supported PC exports. Apps contains other Android applications. Favorites contains available items you have starred. Home shows up to 20 items, with successful recent launches first and console representatives for consoles not yet represented.

See [ROM setup](rom-setup-guide.md) for folders, emulator selection, supported archives, and recovery. [PC games](pc-games.md) explains frontend exports.

## Controls

| Input | Action |
| --- | --- |
| D-pad / left stick | Move focus; long holds accelerate |
| A | Activate or open the selected item |
| B | From a collection item, jump to filters/sorting; from those controls, return to the selected item. Close modals or go back from Details, Search, or Settings |
| X | Open Search |
| Y | Add/remove the selected item from favorites; Clear while editing Search |
| Select | Item details and extra actions |
| Start | Item/page menu |
| L1 / R1 | Change destination |
| L2 / R2 | Change supported filters or move through Home cards; hold to repeat |

**Settings → Controls** can swap A/B, disable sound effects, set launcher sound volume from 0–100%, and independently disable vibration. The rounded ticks and pops also follow Android media volume; haptics follow Android touch-feedback settings and device support. Fast input drops overlapping feedback instead of queuing it. Rapid repeats are automatically softened to prevent sound energy building up; their gain cannot rise within the same burst, and a short pause restores the normal click level. Reducing volume or muting affects the current sound; increases apply to the next sound without reviving a muted tail. Vibration marks only the first and last action in a rapid sequence or held navigation; isolated actions get one pulse. Turning vibration off also cancels a pending end pulse. The footer reflects the active mapping and context.

Search uses A Apply and B Cancel while editing. Selecting the collapsed query edits it again; the round × clears it. Closing Search resets its query. Touch selects items without a controller outline; controller navigation shows the focused item.

Home and filter galleries are finite. Library/Apps/Favorites do not turn B into an automatic jump to Home; use the Home destination. Closing a modal returns to its underlying page.

In Library grid or list mode, **B · Controls** jumps straight to **All filters** from any game without scrolling to the beginning. Use Left/Right for filters, layout and sorting; Down or B returns to the selected game at the same scroll position. The shortcut follows your configured Confirm/Back mapping and also works in Apps and Favorites.

## Display and collections

**Settings → Display** provides UI scale at 90%, 100%, 110%, or 120%, a separate grid-card size from 70% to 140%, and Reduce motion. Android font scaling remains independent.

**Background color** cycles through Purple, Graphite, Blue, Green and Warm. **Tint strength** adjusts the color over charcoal, and **Grain intensity** adjusts the soft matte texture; set grain to 0% for a smooth finish. The default is Purple with 40% tint and 30% grain. Changes preview immediately and are saved. These controls customize the standard background; optional ROM-artwork backgrounds retain their own colors.

Library, Apps, and Favorites remember their own Grid/List selection. List mode shows items on the left and selected-item information on the right. The right pane contains cover art, console, file format and availability, with no buttons or controller focus targets. Touch a row to preview it, then use the footer to Play, Favorite or open Details. Controller A launches the selected row, Y toggles its favorite status, and Select opens Details and extra actions. D-pad Left/Right keeps the selected row; Up/Down moves through the list.

List rows use smaller artwork and single-line titles; the preview shows the full selected title. Enable **Settings → Display → List artwork background** to add the selected ROM’s blurred artwork behind lists. It defaults off, is independent of the Home background, and disappears in grid mode. Select/Details remains in the footer; the duplicate Start/Menu hint has been removed.

## Device status

The strip shows time, battery percentage, **battery temperature**, used/total RAM, and available internal/external storage. Temperature is not a CPU reading. Battery is green while charging, red at 10% or below, yellow below 15%, and neutral otherwise.

The compact **Free** group shows remaining storage: the drive icon is internal storage and the SD-card icon is removable storage. Multiple removable volumes show a count; an unknown reading stays marked as unavailable.

Wi-Fi uses an icon; Bluetooth appears when its radio state is available and enabled. The optional notification indicator requires Android Notification access and shows only whether notifications exist.

## Last played per console

A blue dot on Home marks the last ROM successfully dispatched for each console. Focus or hover a game to see a matching blue **Last played on [platform]** tag above its title. Launching a different PS2 game moves only the PS2 dot; launching a GameCube game does not change it. The dot persists across restart/reboot and appears only on Home. It records launch history, without claiming that the emulator is running or that the game booted successfully.

No Shizuku, root, or process-monitor setup is needed in current builds.

UI scale, grid size and sound volume use sliders. Drag with touch, or focus a slider and press Confirm to adjust with D-pad Left/Right. Holding a direction repeats. Back or Confirm finishes adjustment and keeps the value. Up/Down moves to another control; outside adjustment mode Left/Right can leave the slider too.

Hold a direction to accelerate through a collection. Home collections of up to 12 items load into a memory buffer, including cards just offscreen. Cached covers appear immediately, and installed-app icons load directly from Android without waiting for scrolling to stop. In larger collections, uncached covers wait until you pause briefly. The bounded buffers survive normal app switches and are trimmed when Android needs memory. Fresh covers settle into place with a quick slide and fade; **Reduce motion** turns the reveal off.

For a more colorful Home page, enable **Settings → Display → Home artwork background**. The selected ROM’s artwork becomes a softly blurred, shaded backdrop after scrolling settles, with a quick fade-in. The previous background stays visible until the next one is ready, then the images crossfade. It is off by default and applies only to Home; apps and games without artwork use the usual background. Reduce motion makes backdrop changes immediate once the image is ready.

When PockyDeck is already the default, **Settings → Launcher → Change Home launcher** opens Android’s Home chooser. Select another launcher to stop using PockyDeck as Home.
