# ROM folders, consoles and emulators

This guide covers the 0.3.1 development build. Library contains ROMs and Android games, Apps contains other Android apps, and Favorites includes any available item you have marked. Home shows the most recently opened available item first, followed by Android games, ROMs, and other apps alphabetically within each group.

## Automatic discovery

1. Open **Settings → ROM folders → Set up automatic discovery**.
2. Enable **All files access** for Handheld Launcher in Android settings, then return to the launcher.
3. Leave **Discover new console folders** enabled. The launcher searches mounted shared internal storage, SD cards and USB storage for console folders such as `gba`, `snes`, `psx` and `psp`. It adds a folder only after finding games. Empty console folders do not create console filters.
4. Use **Find folders now** to request another discovery and catalog refresh. Scanning runs in the background and keeps the current catalog visible.

Automatic discovery reads your games without modifying them. Hidden folders, links and Android's private app directories are excluded. Large storage trees are processed in bounded slices, with queued work continuing in the background. Startup, resume and storage connection changes request refreshes; an eight-hour deferrable Android job provides recovery. Android can delay background work, so this is not an instant filesystem monitor.

Turning **Discover new console folders** off stops adding new roots; folders already registered still refresh. Removing an automatically found folder keeps it removed until you choose **Add again** or explicitly select it in the folder picker. Files, game identities, favorites and history are retained.

## Add games

1. Open **Settings → ROM folders → Add ROM folder**.
2. In Android's folder picker, select a ROM directory on internal storage, an SD card or connected USB storage and grant access. A parent such as `ROMs` can contain `psp`, `ps2`, `psx`, `snes`, `gba`, and other named console folders. Selecting one console folder also works. This option works without All files access.
3. Return to the launcher. Enumeration runs off the UI thread and the existing catalog stays visible. Console filters appear in Library and emulator settings only after games are found. Empty folders do not add consoles.
4. Open a game card. When a shared format cannot identify its console, choose the console and the launcher remembers that assignment. Details also provides **Choose console**.

Folder names and deterministic file formats identify consoles. Shared suffixes such as ISO, CHD, BIN and ZIP need a named folder or a user choice. A source-level console choice overrides automatic detection for that source; leave a multi-console parent on automatic. BIOS/support files are excluded where identifiable, and valid disc descriptors/playlists group their tracks/discs into one game. Damaged sets remain visible with an explanation and need repair; assigning a console does not bypass missing files.

The [format matrix](implementation/evidence/F15/format-support.md) lists the 56 recognized console/computer families and grouping limits. Cards use short console names and console-specific tag colors. Archives remain one source item; when preparation discovers multiple game entries, opening it offers a game chooser.

## Choose the emulator

With one compatible installed app, the launcher selects it automatically. When several match, it asks which one to use and offers **Use this app for this console**. Change that choice in **Settings → Emulators**. Item details can override the app for one game. A missing or incompatible saved choice prompts for a replacement instead of silently switching apps.

For RetroArch, install the desired core inside RetroArch, then choose that core for the console in launcher settings. Android prevents this app from inspecting RetroArch's private installed-core directory, so the core list is a supported-choice list, not an installation inventory. RetroArch's folder adapter requires a compatible hierarchical document provider; the launcher's prepared-game provider also supplies that layout.

The [emulator matrix](implementation/evidence/F16/emulator-contracts.md) distinguishes verified intent contracts, installed versions inspected, and actual game-boot evidence. Some detected apps require opening their own library because no compatible external launch contract has been verified. Installation alone is not a promise that all formats, firmware, BIOS, encryption or core combinations work.

## Automatic extraction and cache

ZIP/7Z and supported gzip, xz, bzip2 and ordinary TAR wrappers are prepared before opening their contents, so an archive containing several games always offers a choice. Native arcade and DOS archive collections retain their required containers. Emulator-native image containers such as CHD, CSO and RVZ are not converted. The [extraction contract](implementation/evidence/F15/archive-extraction.md) lists decoder and archive limits. RAR is recognized but cannot be extracted by this build; nested archives and encrypted archives require manual preparation. If a disc descriptor or playlist inside an archive is damaged, repair the archive before opening it; surviving tracks are not treated as a complete game.

The original archive is kept. Prepared games are served through a read-only document provider, including required companion files. The default cache budget is **8 GiB**, adjustable to 2, 4, 8 or 16 GiB in **Settings → ROM folders**. The budget includes a compressed working copy and its extracted content; at least 512 MiB free space is reserved. Large archives may require a higher limit. Unsafe paths, links, corrupted data, excessive entries/output and excessive decoder memory stop extraction with a recoverable message. Canceling discards partial output.

Copies reserved by game launches are excluded from automatic removal because returning Home does not prove the emulator has closed. They live in app-managed storage excluded from backups, rather than Android's purgeable cache directory. **Clear unused copies** retains those reservations. After closing emulators, use **Clear all copies — emulators closed** to reclaim them.

## Recovery and preservation

- Missing SD/USB storage or revoked access: reconnect storage or use **Restore access**. Automatically discovered folders need All files access; manually selected folders retain their own Android folder grants. Identities, favorites and history are retained.
- Removing a source: stops showing its games without deleting ROM files. **Add again** restores its saved identities. Automatic discovery does not re-add a removed source. Moved files or unrelated provider roots are not silently merged by filename.
- Duplicate or overlapping roots: manually selected Android storage folders take priority over automatic roots. Selecting an automatic root or its parent through Android's storage picker preserves known game identities while consolidating access. If an existing third-party folder provider cannot identify its physical storage, automatic discovery pauses to avoid duplicates; manually selected sources still scan.
- Failed or interrupted scan: retained entries are not treated as deleted. Completed batches can add/update games, but only a successful full-source reconciliation marks absent files removed.
- Unsupported emulator or file: choose another compatible app, correct the console, repair the set, or prepare the files in an accepted format. A failed dispatch does not promote the game in Home history.

**Scan now** refreshes registered sources. A failed or disconnected source retains its records for recovery. ROM discovery and preparation stay local; launching an emulator grants read access to the selected registered or prepared folder. The launcher does not grant write access to ROMs.
