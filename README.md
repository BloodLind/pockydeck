# PockyDeck

**Your games. One pocket-sized home.**

An open-source Android launcher for handheld gaming devices, built with Kotlin and Jetpack Compose. Bring Android games, ROMs, and exported PC games together in a controller-friendly home screen.

[![Android](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white)](docs/getting-started.md)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)
[![Build](https://github.com/BloodLind/pockydeck/actions/workflows/build.yml/badge.svg)](https://github.com/BloodLind/pockydeck/actions/workflows/build.yml)
[![Preview](https://img.shields.io/badge/preview-0.10.3-orange)](https://github.com/BloodLind/pockydeck/releases/tag/v0.10.3)

[Download 0.10.3](https://github.com/BloodLind/pockydeck/releases/tag/v0.10.3) · [Getting started](docs/getting-started.md) · [ROM setup](docs/rom-setup-guide.md) · [Contributing](CONTRIBUTING.md)

## What it does

- **One Home for your games:** up to 20 cards, recent launches first, with console representatives before your history fills out.
- **Separate collections:** Library for games, Apps for other Android applications, Favorites for anything you star, and Search across games, ROMs, apps, and launcher actions.
- **Controller and touch navigation:** D-pad/stick movement, accelerated holds, analog-trigger handling, A/B remapping, contextual footer hints, and soft original sound effects.
- **Flexible collections:** grid and split-pane list views, adjustable card size and UI scale, compact console filters, sorting, and reduced motion.
- **Background ROM discovery:** populated console folders on internal, SD, and USB storage; incremental results; explicit folder selection when broad storage access is unwanted.
- **Emulator selection:** compatible installed apps, per-console and per-game preferences, RetroArch core choices, and automatic preparation of supported archives.
- **Artwork:** reuse ES-DE metadata and covers, or optionally look up missing console covers through Libretro.
- **PC frontend exports:** import GameNative shortcuts; compatible GameHub Lite packages support Steam exports.
- **Device status:** battery, battery temperature, RAM, available storage, Wi-Fi, and optional Bluetooth/notification indicators. Optional Shizuku support adds observed app/emulator process badges on Home.

No games, BIOS files, emulator binaries, or emulator cores are bundled.

## Current release

**0.10.3 is the first public preview**, designed and tested primarily on the Retroid Pocket Flip 2 running Android 13. Other Android 13+ landscape devices may work but need device-specific verification.

The APK still appears as **Handheld Launcher** in Android. Its package is `dev.handheld.launcher`. The release uses a non-debuggable release build signed with the existing development certificate; it is a sideloaded preview, not a Play Store release. See [installation and signing details](docs/getting-started.md#install-the-preview).

Recent improvements include compact action buttons, a cleaner Search toolbar, three visible console categories, split-pane lists, softer controller audio, and improved artwork memory handling. Read the [changelog](CHANGELOG.md) and [release notes](docs/releases/0.10.3.md).

## Start playing

1. Download the APK and checksum file from the [release page](https://github.com/BloodLind/pockydeck/releases/tag/v0.10.3), install it, and open Handheld Launcher.
2. Installed Android applications appear automatically. Add ROM folders in **Settings → ROM folders**, or enable automatic discovery after granting Android's **All files access**.
3. Install and configure your emulators separately. Open a game and choose an emulator if several are compatible.
4. Optionally use **Settings → Launcher → Set as Home launcher** to make it your Android Home screen.

Read [recognized formats](docs/rom-formats.md) and [emulator compatibility](docs/emulators.md) before assuming a detected ROM can be launched. BIOS, keys, cores, and game compatibility remain emulator-specific.

## Build from source

Use **JDK 17**, **Android SDK Platform 34 / Build Tools 34.0.0**, and the checked-in **Gradle 8.9** wrapper.

```sh
git clone https://github.com/BloodLind/pockydeck.git
cd pockydeck
./gradlew :app:assembleDebug
```

On Windows, use `.\gradlew.bat`. Configure the SDK through Android Studio, `ANDROID_HOME`, or an ignored `local.properties` file. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The [development guide](docs/development.md) covers tests, device checks, modules, and release packaging.

## Documentation

| Guide | Contents |
| --- | --- |
| [Getting started](docs/getting-started.md) | Installation, controls, display settings, Home setup, running indicators |
| [ROM setup](docs/rom-setup-guide.md) | Storage access, discovery, extraction, emulator choices, recovery |
| [ROM formats](docs/rom-formats.md) / [Emulators](docs/emulators.md) | Recognition and dispatch compatibility |
| [PC games](docs/pc-games.md) | GameNative and compatible GameHub Lite exports |
| [Development](docs/development.md) / [Architecture](docs/architecture.md) | Build, tests, module boundaries, persistence and input rules |
| [Design system](docs/design-system.md) | Visual and interaction conventions |
| [Privacy](docs/privacy.md) / [Security](SECURITY.md) | Local data, optional network/helper access, reporting vulnerabilities |
| [Roadmap](docs/roadmap.md) | Current limitations and next work |

## Contribute

Bug reports, compatibility reports, documentation fixes, and focused pull requests are welcome. Start with the [contribution guidelines](CONTRIBUTING.md) and [code of conduct](CODE_OF_CONDUCT.md). Include your device, Android version, emulator version, and reproduction steps when reporting a problem.

## License and credits

PockyDeck's original code, documentation, geometric debug artwork, and synthesized controller cues are licensed under [Apache 2.0](LICENSE). Third-party components retain their own licenses; see [third-party notices](THIRD_PARTY_NOTICES.md).

Built with AndroidX and Jetpack Compose, Plus Jakarta Sans, Material Symbols, Apache Commons, XZ for Java, and the optional Shizuku API. PockyDeck is an independent project and is not affiliated with the device, game, emulator, or frontend projects it integrates with.
