# Third-party notices

PockyDeck's [Apache 2.0 license](LICENSE) applies to original project material. It does not replace the licenses of the components below, imported artwork, or software launched by the app.

| Component | Use | License and retained text |
| --- | --- | --- |
| AndroidX / Jetpack Compose / WorkManager | Android UI, lifecycle, persistence, background work | Apache 2.0; [license text](app/src/main/assets/licenses/Apache-2.0.txt) |
| Kotlin and kotlinx.coroutines | Language runtime and asynchronous work | Apache 2.0; [license text](app/src/main/assets/licenses/Apache-2.0.txt) |
| Plus Jakarta Sans | Bundled font weights | SIL Open Font License 1.1; [copyright and license](core/designsystem/src/main/assets/font-notices/PlusJakartaSans-OFL.txt) |
| Google Material Symbols | Vector icons | Apache 2.0; [license](app/src/main/assets/licenses/material-symbols-LICENSE.txt), [SVG sources and hashes](docs/references/material-symbols/README.md) |
| Apache Commons Compress, IO, Codec, Lang | Archive decoding and support | Apache 2.0; original licenses and notices in [APK assets](app/src/main/assets/licenses/) |
| XZ for Java | XZ/LZMA decoding | 0BSD; [license](app/src/main/assets/licenses/xz-1.10-COPYING.txt) |
| Shizuku API/provider | Optional live process readings | MIT; [license](app/src/main/assets/licenses/shizuku-api-13.1.5-LICENSE.txt) |
| Gradle wrapper | Build bootstrap | Apache 2.0; [Gradle source](https://github.com/gradle/gradle) |

Exact dependency versions are in [the version catalog](gradle/libs.versions.toml). [ROM dependency provenance](docs/rom-dependencies.md) and [the packaged attribution index](app/src/main/assets/licenses/README.txt) identify the retained upstream texts. Font notices are packaged under `assets/font-notices/`; other notices are under `assets/licenses/`.

The geometric debug covers are original vector fixtures. Controller WAV cues are synthesized from the checked-in [PowerShell generator](scripts/audio/Generate-ControllerSounds.ps1); they contain no recordings or samples from a console operating system.

No third-party game covers are bundled in the current source or APK. Covers loaded from local ES-DE metadata or an optional remote artwork provider remain the property of their respective owners and are not relicensed by PockyDeck. Historical reference material remains in Git history under its original ownership.

Emulator integrations use public Android launch contracts. No emulator, core, ROM, BIOS, firmware, account credential, or proprietary frontend database is distributed by this project. Compatibility names and trademarks belong to their respective owners.
