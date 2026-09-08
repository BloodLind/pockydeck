# PC games through frontend exports

PockyDeck imports supported frontend shortcuts as PC game cards. It does not inspect another app's private database or install Windows executables.

## Setup

1. In GameNative, use **Export for frontend**.
2. Save the exported files in an accessible `ROMs/windows` folder, or in a named `steam`, `epic`, `gog`, or `amazon` folder.
3. Allow automatic discovery or select the folder in **Settings → ROM folders**. **Find folders now** requests a refresh.
4. Open a PC card. If several compatible apps are installed, choose one; set a default in **Settings → Emulators → PC games**.

GameNative and GameHub themselves remain ordinary app entries. Individual exported games appear in Library, Search, and Favorites.

## Supported contracts

| Export | GameNative 1.2.0+ | Compatible GameHub Lite |
| --- | --- | --- |
| `.steam` | Steam | Steam |
| `.epic` | Epic | — |
| `.gog` | GOG | — |
| `.amazon` | Amazon | — |
| `.pcgame` | Custom game | — |

Exports contain a positive 32-bit game ID, not a URL or command. The scanner and launcher validate the contents and size. Raw executables, installers, and support files do not become PC cards.

The supported GameHub packages are `gamehub.lite` and `emuready.gamehub.lite` using their Steam launch contract. Stock GameHub and arbitrary forks are not assumed compatible. GameNative custom IDs are not interchangeable with GameHub local IDs.

The adapter checks that the exact component is installed, enabled, exported, and accessible. It does not pass credentials or override runtime/container configuration.

## Artwork and compatibility

Existing ES-DE covers can be reused. The current Libretro artwork integration has no Windows collection, so missing PC covers keep a fallback.

GameNative's Steam launch path was exercised on a physical Retroid Pocket Flip 2 with GameNative 1.2.0. Epic/GOG/Amazon/custom sources and GameHub Lite have contract/test coverage, not equivalent physical game-boot coverage. Gameplay compatibility, firmware, runtime setup, login, and offline behavior belong to the frontend.

Primary integration sources: [GameNative v1.2.0 launch parser](https://github.com/utkarshdalal/GameNative/blob/v1.2.0/app/src/main/java/app/gamenative/utils/IntentLaunchManager.kt), [GameNative source definitions](https://github.com/utkarshdalal/GameNative/blob/v1.2.0/app/src/main/java/app/gamenative/data/LibraryItem.kt), and [ES-DE Android system contracts](https://gitlab.com/es-de/emulationstation-de/-/blob/master/resources/systems/android/es_systems.xml).
