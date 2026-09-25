# PockyDeck

A free, open-source launcher for Android handhelds, focused on a familiar console-style experience. Browse your games with a controller or touch and spend less time navigating Android.

[Download 0.10.5 preview](https://github.com/BloodLind/pockydeck/releases/tag/v0.10.5) · [Quick setup](docs/getting-started.md) · [Report an issue](https://github.com/BloodLind/pockydeck/issues)

## What it supports

- Home, Library, Apps, Favorites, and Search for Android games, ROMs, and supported PC-game exports.
- Controller navigation with accelerated scrolling, touch controls, and configurable sound and vibration feedback.
- ROM-folder scanning, supported emulator selection, and RetroArch core preferences.
- Grid and list views with game previews, sorting, filters, and last-played markers on Home.
- Local ES-DE artwork and optional online covers, with cached images for quick browsing.
- Custom background colors, optional blurred game artwork, UI/card sizing, and reduced motion.

Requires **Android 13+** and runs in landscape. Tested primarily on the Retroid Pocket Flip 2; compatibility with other devices and emulators varies. Install your emulators separately and add your own games—no games, BIOS files, emulators, or cores are bundled. Root and Shizuku are not required.

## Documentation

[Getting started](docs/getting-started.md) · [ROM setup](docs/rom-setup-guide.md) · [Emulators](docs/emulators.md) · [Supported formats](docs/rom-formats.md) · [PC-game exports](docs/pc-games.md)

To build, use JDK 17, Android SDK Platform 34, and the included Gradle wrapper:

```sh
./gradlew :app:assembleDebug
```

On Windows, use `.\gradlew.bat`. See [development](docs/development.md), [contributing](CONTRIBUTING.md), [privacy](docs/privacy.md), and [security](SECURITY.md).

## License

PockyDeck is free to use, modify, and share under [Apache 2.0](LICENSE). Third-party components keep their own [licenses and notices](THIRD_PARTY_NOTICES.md).

## Support the project

PockyDeck is free for everyone. If you'd like to donate, [get in touch on GitHub](https://github.com/BloodLind/pockydeck/issues/new?title=Supporting%20PockyDeck) and I'll set up a donation jar for the project. Donations are entirely optional.
