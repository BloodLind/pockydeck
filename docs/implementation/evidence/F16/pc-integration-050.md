# PC games through GameNative — 8 September 2026

Version **0.5.0 (code 8)** imports exported PC games into the existing launcher. Library has a compact **PC** filter and ordinary selectable cards; Search and Favorites use the same catalog entries. The user approved GameNative as the integration, with GameHub also acceptable. No catalog schema, migration, card geometry, typography or icon changes are part of this revision.

## Adding games

In GameNative, use **Export for frontend** and save the exported shortcuts in an accessible `ROMs/windows` folder, or the corresponding `steam`, `epic`, `gog` or `amazon` folder. With the already approved All files access, the background scanner discovers populated folders. A manually selected ROM folder works too. Settings → ROM folders explains this workflow; Find folders now requests a refresh.

Settings → Emulators → PC games chooses the launch app. One eligible app is automatic. Several eligible apps require a choice unless a valid console or item preference already exists. GameNative and GameHub themselves remain under Apps. Additional games require frontend exports; the launcher does not enumerate another app's private library database or install PC games from executables.

## Supported export and launch contracts

The source contract was inspected against GameNative **v1.2.0**, matching the version installed on the physical device:

| Export | GameNative `game_source` | GameHub Lite |
| --- | --- | --- |
| `.steam` | `STEAM` | Supported Steam ID contract |
| `.epic` | `EPIC` | Not offered |
| `.gog` | `GOG` | Not offered |
| `.amazon` | `AMAZON` | Not offered |
| `.pcgame` | `CUSTOM_GAME` | Not offered; these are not GameHub local IDs |

Each export contains one positive 32-bit game ID, not a URL or command. UTF-8 BOM and surrounding whitespace are accepted within a 128-byte limit. Both scanning and launch validate the value; launch rereads a bounded 129-byte prefix to detect oversized or changed exports. Invalid or unreadable exports require repair. Raw executables, installers, archives and other support files in PC folders do not become game cards. Generic console archive recognition remains available outside this PC integration.

GameNative uses explicit component `app.gamenative/app.gamenative.MainActivity`, action `app.gamenative.LAUNCH_GAME`, Int extra `app_id` and the source String above. The resolver requires version 1.2.0 or newer and checks that the exact activity is installed, enabled, exported and accessible. It sends no file URI, read grant, account data or container configuration. See the [versioned launch parser](https://github.com/utkarshdalal/GameNative/blob/v1.2.0/app/src/main/java/app/gamenative/utils/IntentLaunchManager.kt) and [source definitions](https://github.com/utkarshdalal/GameNative/blob/v1.2.0/app/src/main/java/app/gamenative/data/LibraryItem.kt).

Compatible packages `gamehub.lite` and `emuready.gamehub.lite` use `com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity`, action `gamehub.lite.LAUNCH_GAME`, String `steamAppId`, and Boolean `autoStartGame=true`. This matches [ES-DE's Android system contracts](https://gitlab.com/es-de/emulationstation-de/-/blob/master/resources/systems/android/es_systems.xml) and [package lookup](https://gitlab.com/es-de/emulationstation-de/-/blob/master/resources/systems/android/es_find_rules.xml). Stock GameHub and other forks are not advertised through an assumed API. Neither supported GameHub package was installed, so its coverage is contract verification and native intent tests, not a physical game boot.

## Real-device result

The Retroid Pocket Flip2 runs Android 13 at 1920×1080 and 360dpi, with GameNative 1.2.0. The existing SD-card `ROMs/windows` folder contained three exports:

| Game | Steam ID | Observed result |
| --- | --- | --- |
| Aliens vs. Predator | 10680 | Imported as a PC card; fallback artwork |
| Grim Dawn | 219990 | Imported, ES-DE cover reused, selectable Search result |
| Half-Life 2 | 220 | Imported, ES-DE cover reused, launched through GameNative to its main menu |

The catalog grew from **3,773 to 3,776** entries after background reconciliation. [Library](pc-050-library.png) shows exactly three PC cards and the preserved total. [PC app settings](pc-050-settings.png) and the [chooser](pc-050-choice.png) identify GameNative 1.2.0; [Apps](pc-050-apps.png) retains its separate frontend card. [Search](pc-050-search.png) shows Grim Dawn alongside the other two actual matches for `grim`, using the same result card size and controller focus.

Gamepad-source D-pad events selected Half-Life 2 and A dispatched its export. Android reported the GameNative launch action, then GameNative connected to Steam, prepared its runtime, and reached the [actual Half-Life 2 main menu](pc-050-boot.png). No new/load-game action was taken. GameNative's Exit control returned directly to Library with Half-Life 2 still selected and first in Recent order. The launcher did not change emulator configuration or source ROM/save/ES-DE files; GameNative performed its normal runtime setup.

The final APK adds a UTF-8 byte-limit consistency check after that boot test. JVM checks, lint and both APK builds were rerun, the final APK was installed with `adb install -r`, and its retained library, settings, Apps classification and Search result were inspected. A later repeat boot check was interrupted by other device activity, so it is not counted as final-APK game-boot evidence. The source-specific launch contract was unchanged by the byte-limit correction.

## Verification and limits

The [verification record](pc-integration-050-verification.txt) records **262 unique passing tests**, one skipped Windows symlink fixture, zero lint errors and debug/release build success. The 69 Android checks ran on the physical Flip2 using the isolated data-module test app. Main-app instrumentation was not run because its install/uninstall lifecycle can remove user launcher data.

Tests cover every exported source, malformed/oversized IDs, PC-folder support-file exclusion, source/platform eligibility, version gating, multiple-app choice, and exact typed Android intents without URI grants or container overrides. The GameNative classification conflict found during the first combined run was corrected before installation. Final strict-size tests include multibyte whitespace, keeping scan and dispatch limits consistent.

ES-DE artwork reuse is supported for PC entries. Aliens vs. Predator has no usable local cover in the inspected media, and Libretro's configured collections do not include Windows, so it retains the fallback. A new online PC artwork provider is not introduced by this integration. Epic/GOG/Amazon/custom exports are validated by fixtures and the versioned contract; only the existing Steam exports were available for physical checks. Gameplay compatibility, offline boot, all physical buttons/analog axes and the broader batched discovery/search performance work remain separate coverage.

Rapid ADB text injection through the full-screen IME produced one malformed query; confirmed slower entry found the expected results. A first A press after closing the IME appeared ineffective during a later check, but the foreground also changed between checks. Device input was paused for coordination; this observation is not counted as a confirmed or fixed controller regression.
