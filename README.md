# PockyDeck

**Your games. One pocket-sized home.**

An open-source Android launcher for handheld gaming devices, built with Kotlin and Jetpack Compose. Browse Android games, ROMs, and supported PC-game exports with a controller or touch.

[Download public preview 0.10.3](https://github.com/BloodLind/pockydeck/releases/tag/v0.10.3) · [Getting started](docs/getting-started.md) · [ROM setup](docs/rom-setup-guide.md) · [Changelog](CHANGELOG.md)

## Play from one place

- Home with recent games, plus Library, Apps, Favorites, and Search.
- ROM-folder discovery, emulator preferences, and RetroArch core selection.
- Grid and list views, controller-friendly UI/card-size sliders, and reduced motion.
- Local ES-DE covers and optional Libretro artwork lookup.
- A blue dot on Home marks each console's most recently launched ROM. Focus or hover shows a matching blue Last played on [platform] tag above the title; launch history survives restarts.

Install and configure emulators separately, then add your ROM folders in **Settings → ROM folders**. See [supported emulators](docs/emulators.md) and [formats](docs/rom-formats.md). No games, BIOS files, emulators, or cores are bundled.

## Release status

The latest published preview is **0.10.3**. The sliders, Home-only last-played dots and tags, PockyDeck Android label/icon, and removal of Shizuku described in the current source are **unreleased**; see the [stabilization notes](docs/releases/next.md). The published 0.10.3 APK still uses the Handheld Launcher label and its earlier status controls.

Android 13+ is required. Development primarily targets the Retroid Pocket Flip 2; other devices need verification. The package remains `dev.handheld.launcher` for upgrade continuity. Updates also require a matching signing certificate; see [installation details](docs/getting-started.md#install-the-preview).

## Build and contribute

Use JDK 17, Android SDK Platform 34 / Build Tools 34.0.0, and the included Gradle wrapper:

```sh
./gradlew :app:assembleDebug
```

On Windows use `.\gradlew.bat`. Configure the SDK through `ANDROID_HOME` or an ignored `local.properties`. Read [development and testing](docs/development.md), [architecture](docs/architecture.md), and [contribution guidance](CONTRIBUTING.md).

[PC-game exports](docs/pc-games.md) · [Design system](docs/design-system.md) · [Roadmap](docs/roadmap.md) · [Privacy](docs/privacy.md) · [Security](SECURITY.md)

## License

Original code and assets are licensed under [Apache 2.0](LICENSE). See [third-party notices](THIRD_PARTY_NOTICES.md). PockyDeck is independent of the device, game, emulator, and frontend projects it supports.
