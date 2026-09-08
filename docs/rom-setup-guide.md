# ROM folders, consoles and emulators

This guide covers the 0.2.0 development build. Native Android application launching continues to use the same Home, Library, Favorites and Search pages.

## Add games

1. Open **Settings → ROM folders → Add ROM folder**.
2. In Android's folder picker, select a ROM directory on internal storage or the SD card and grant access. A parent such as `ROMs` can contain `psp`, `ps2`, `psx`, `snes`, `gba`, and other named console folders. Selecting one console folder also works.
3. Return to the launcher. Enumeration runs off the UI thread and the existing catalog stays visible. Console filters appear in Library and emulator settings only after games are found. Empty folders do not add consoles.
4. Open a game card. When a shared format cannot identify its console, choose the console and the launcher remembers that assignment. Details also provides **Choose console**.

Folder names and deterministic file formats identify consoles. Shared suffixes such as ISO, CHD, BIN and ZIP need a named folder or a user choice. A source-level console choice overrides automatic detection for that source; leave a multi-console parent on automatic. BIOS/support files are excluded where identifiable, and valid disc descriptors/playlists group their tracks/discs into one game. Damaged sets remain visible with an explanation and need repair; assigning a console does not bypass missing files.

The [format matrix](implementation/evidence/F15/format-support.md) lists the 56 recognized console/computer families and grouping limits. Archives remain one source item; when preparation discovers multiple game entries, opening it offers a game chooser.

## Choose the emulator

With one compatible installed app, the launcher selects it automatically. When several match, it asks which one to use and offers **Use this app for this console**. Change that choice in **Settings → Emulators**. Item details can override the app for one game. A missing or incompatible saved choice prompts for a replacement instead of silently switching apps.

For RetroArch, install the desired core inside RetroArch, then choose that core for the console in launcher settings. Android prevents this app from inspecting RetroArch's private installed-core directory, so the core list is a supported-choice list, not an installation inventory. RetroArch's folder adapter requires a compatible hierarchical document provider; the launcher's prepared-game provider also supplies that layout.

The [emulator matrix](implementation/evidence/F16/emulator-contracts.md) distinguishes verified intent contracts, installed versions inspected, and actual game-boot evidence. Some detected apps require opening their own library because no compatible external launch contract has been verified. Installation alone is not a promise that all formats, firmware, BIOS, encryption or core combinations work.

## Automatic extraction and cache

ZIP/7Z and supported gzip, xz, bzip2 and ordinary TAR wrappers are prepared before opening their contents, so an archive containing several games always offers a choice. Native arcade and DOS archive collections retain their required containers. Emulator-native image containers such as CHD, CSO and RVZ are not converted. The [extraction contract](implementation/evidence/F15/archive-extraction.md) lists decoder and archive limits. RAR is recognized but cannot be extracted by this build; nested archives and encrypted archives require manual preparation. If a disc descriptor or playlist inside an archive is damaged, repair the archive before opening it; surviving tracks are not treated as a complete game.

The original archive is kept. Prepared games are served through a read-only document provider, including required companion files. The default cache budget is **8 GiB**, adjustable to 2, 4, 8 or 16 GiB in **Settings → ROM folders**. The budget includes a compressed working copy and its extracted content; at least 512 MiB free space is reserved. Large archives may require a higher limit. Unsafe paths, links, corrupted data, excessive entries/output and excessive decoder memory stop extraction with a recoverable message. Canceling discards partial output.

Copies reserved by game launches are excluded from automatic removal because returning Home does not prove the emulator has closed. They live in app-managed storage excluded from backups, rather than Android's purgeable cache directory. **Clear unused copies** retains those reservations. After closing emulators, use **Clear all copies — emulators closed** to reclaim them.

## Recovery and preservation

- Missing SD card or revoked access: reconnect storage or select the same folder again. Identities, favorites and history are retained.
- Removing a source: stops showing its games without deleting ROM files. Re-adding the exact same source restores its saved identities. Different or moved provider roots are new sources; no silent filename-based merge occurs.
- Duplicate or overlapping roots: use the existing common root. Providers that cannot establish overlap should use one root for their ROM collection.
- Failed or interrupted scan: retained entries are not treated as deleted. Completed batches can add/update games, but only a successful full-source reconciliation marks absent files removed.
- Unsupported emulator or file: choose another compatible app, correct the console, repair the set, or prepare the files in an accepted format. A failed dispatch does not promote the game in Home history.

Foreground startup/resume and **Scan now** reconcile sources. An eight-hour deferrable Android background job provides recovery while respecting battery/storage constraints; it is not continuous filesystem monitoring or an exact timer. No broad file-access permission, ROM upload, provider account, or global input interception is introduced.
