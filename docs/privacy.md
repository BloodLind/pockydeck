# Privacy and permissions

PockyDeck does not require an account. The launcher stores its catalog, preferences, favorites, source identities, and successful-open order locally. There is no analytics or crash-reporting service in the current app.

## Files and storage

Manually selected ROM folders use Android's folder permissions. Automatic discovery needs optional **All files access** so the app can find populated console folders across shared internal, SD, and USB storage. It reads original games and metadata; it does not edit ROM or save files.

Prepared archive copies live in app-managed storage. The launcher grants emulators read access to the selected registered or prepared folder. Removing a source hides it without deleting its original files. See [ROM recovery and cache behavior](rom-setup-guide.md).

Android backup behavior is controlled by the packaged backup rules. Prepared games are excluded; preference/catalog backup behavior also depends on Android and the device's backup settings. Do not rely on uninstall/reinstall as a migration or backup mechanism.

## Optional network requests

Online artwork lookup uses Libretro's thumbnail infrastructure. Matching sends the relevant console collection and normalized title/cover path in requests to the provider. It does not upload ROM contents. The provider can observe ordinary connection information such as the device's public IP address.

Local ES-DE artwork can be reused without an online artwork request. Online lookup is controlled in launcher settings. Opening an external download/setup link uses the selected browser and that site's own privacy practices.

## Optional system access

- **Shizuku:** after explicit setup and authorization, checks requested current-user app/emulator process names while the launcher is foreground. It does not claim to identify an active ROM. Disabling Running indicators stops sampling.
- **Notification access:** used for a presence dot. The launcher retains notification presence in memory, not notification titles, text, keys, or payloads.
- **Installed applications:** Android's package queries discover launchable current-user apps and compatible emulator components.
- **Device status:** public Android readings supply time, battery, battery temperature, RAM, storage, and supported radio state.

Shizuku and emulator/frontend apps are separate software with their own permissions and policies. Launching a game does not grant the launcher access to a frontend's private account or game database.

## Reports

Diagnostics and screenshots you attach to a GitHub issue are public. Remove personal paths, notifications, account names, tokens, and game/save contents before sharing. Report vulnerabilities privately using [the security policy](../SECURITY.md).
