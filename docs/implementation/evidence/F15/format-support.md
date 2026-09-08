# F15 ROM recognition and grouping

Implemented in `core/domain/.../rom/scan/`. The support matrix describes **recognition and entry planning**, independently of whether an installed emulator supports a format, Android launch intent, decryption, or companion-file access. Recognition does not verify a binary ROM header, checksum, game compatibility, or whether an installable package is a base game instead of an update/DLC.

The user's approved scope is broad popular console formats, console folders populated only when game candidates are present, automatic emulator discovery with a chooser for multiple matches, persistent console emulator preferences, and automatic archive extraction when required. Storage, extraction, launch capability and UI are separate implementations; the pure planner never reads ROM bodies or extracts an archive itself.

## Selection rules

- A file needs a recognized suffix and a positive or unknown size. Empty console folders, files known to be empty, support directories, saves, documents and common named BIOS files do not create game entries.
- Console selection uses the nearest explicit folder override, then a source-wide assignment, then the nearest exact normalized console-folder alias, then the selected root's display name. Case, spaces, punctuation and manufacturer prefixes explicitly listed in the registry are supported; arbitrary substring matches are not used.
- With no folder/assignment context, only deterministic platform suffixes select a console. Shared ISO, CHD, BIN, ROM, PBP, ZIP, 7Z, RVZ, ST and other shared formats remain selectable, unassigned entries. Generic GZ, PKG, CONF, CMD, BAT, WAD, O, ABS, COF and PRX need context even if this registry has one matching platform.
- A folder/format disagreement remains an unassigned, correctable entry. A manual selection is not silently used to reinterpret an incompatible format.
- File identity remains the provider's opaque document ID. The planner never constructs durable item identity from a title, filename or path. Reordering provider enumeration produces the same plan.
- Generic compressed/archive formats are opaque candidates for every configured console platform except the PC frontend integration: ZIP, 7Z, RAR, GZIP/GZ, XZ, BZIP2/BZ2, TAR and tar.gz/tgz, tar.xz/txz, tar.bz2/tbz2/tbz. RAR remains discoverable with an explicit unsupported-extraction outcome; it is not silently hidden. The archive filename does not establish its contents. The caller can feed extracted metadata back to this planner; several resulting games remain several entries for a chooser, rather than arbitrarily selecting one.
- PC frontend folders (`windows`, `steam`, `epic`, `gog`, `amazon`, `pc`, `pcgames`) accept only GameNative's exported `.steam`, `.epic`, `.gog`, `.amazon` and `.pcgame` shortcuts. Each must contain one positive Int game ID. Executables, installers, archives and support files in that context do not become game cards. These exports refer to the frontend's existing library; they do not install games or enumerate its private database.

## Recognized formats

Suffixes are case-insensitive. The longest suffix wins, including `.nkit.iso` and `.tar.gz`. The generic compressed/archive formats above are additional opaque formats for every row except `windows`. DOSZ remains specifically identified as a DOS archive. This broad matrix is not a promise that every emulator can launch every format listed for its console.

| Console ID | Recognized suffixes besides generic compressed/archive formats |
| --- | --- |
| nes | nes, fds, unf, unif |
| snes | sfc, smc, fig, swc, bs, st |
| n64 | z64, n64, v64, ndd |
| gb / gbc / gba | gb, sgb / gbc, cgb / gba, agb |
| virtualboy | vb, vboy |
| nds | nds, dsi, ids, srl |
| 3ds | 3ds, cci, cxi, cia, 3dsx |
| gamecube | gcm, iso, gcz, rvz, wia, ciso, nkit.iso, nkit.gcz, dol, elf, m3u |
| wii | iso, wbfs, gcz, rvz, wia, ciso, nkit.iso, nkit.gcz, wad, dol, elf, m3u |
| wiiu | wud, wux, wua, rpx |
| switch | xci, nsp, nro, nso, nca, nsz, xcz |
| psx | cue, bin, img, iso, chd, pbp, ccd, mds, mdf, toc, cbn, ecm, exe, m3u, m3u8 |
| ps2 | iso, chd, cso, zso, bin, img, mdf, elf, gz, m3u, m3u8 |
| psp | iso, cso, chd, pbp, elf, prx |
| psvita | vpk, pkg |
| sg1000 | sg, bin |
| mastersystem | sms, bms, bin |
| megadrive | md, mdx, gen, smd, bin, 68k, sgd |
| gamegear | gg, bin |
| segacd | cue, iso, chd, bin, m3u, m3u8 |
| sega32x | 32x, bin, smd, md |
| saturn | cue, iso, chd, bin, ccd, mds, mdf, m3u, m3u8 |
| dreamcast | gdi, cdi, chd, cue, bin, elf, m3u, m3u8 |
| naomi / atomiswave | chd, lst, dat / lst, dat |
| arcade | chd, cmd |
| neogeo / neogeocd | neo / cue, chd, iso, bin, m3u, m3u8 |
| pce / pcecd / supergrafx | pce, bin / cue, chd, ccd, iso, bin, m3u, m3u8 / sgx, pce |
| atari2600 / atari5200 / atari7800 | a26, bin, rom / a52, bin, rom, car / a78, bin |
| atari800 | atr, atx, xfd, dcm, xex, car, rom, cas, bas, com, bin, m3u |
| atarist | st, stx, msa, dim, ipf, m3u, m3u8 |
| atarilynx / atarijaguar | lnx, lyx, o / j64, jag, rom, abs, cof, bin |
| wonderswan / wonderswancolor | ws / wsc |
| neogeopocket / neogeopocketcolor | ngp, ngpc / ngc, ngpc, ngp, npc |
| colecovision / intellivision / vectrex | col, cv, rom, bin / int, itv, bin, rom / vec, bin |
| 3do | iso, chd, cue, bin |
| amiga | adf, adz, dms, fdi, ipf, hdf, hdz, lha, slave, cue, iso, chd, ccd, mds, nrg, m3u, m3u8, uae, rp9 |
| c64 | d64, d71, d81, d80, d82, g64, g41, x64, t64, tap, prg, p00, crt, bin, m3u, m3u8 |
| zxspectrum | tzx, tap, z80, sna, szx, trd, scl, dsk, dck, pzx, rzx, ipf |
| amstradcpc | dsk, sna, tap, cdt, voc, cpr, m3u |
| msx / msx2 | rom, ri, mx1, dsk, cas, m3u, m3u8 / rom, ri, mx2, dsk, cas, m3u, m3u8 |
| dos | dosz, exe, com, bat, iso, cue, ins, img, ima, vhd, jrc, tc, conf, m3u, m3u8 |
| windows (PC) | steam, epic, gog, amazon, pcgame — validated game-ID exports only |
| scummvm | scummvm |

## Multi-file games and packages

- CUE: a bounded descriptor must contain FILE and TRACK records; every referenced file must exist in the enumerated source. Quoted filenames, spaces and audio tracks are preserved as companions. Only a validated descriptor suppresses its tracks as separate games.
- GDI: validates its declared count, unique track numbers, record structure and files before producing one Dreamcast entry.
- M3U/M3U8: preserves disc order, ignores comment lines and BOM, recursively includes validated CUE/GDI/other playlist dependencies, and suppresses member discs and tracks. All members must have a compatible possible console. Cycles, excessive depth, unavailable files and cross-console lists are repair states.
- TOC: supports quoted FILE, DATAFILE and AUDIOFILE references. Unsupported reference syntax is reported for repair instead of pretending the descriptor is self-contained.
- CCD pairs with the same basename IMG and optional SUB; MDS requires its MDF companion. These metadata pairs do not require loading their binary contents during indexing.
- `PSP/<title>/EBOOT.PBP` uses `<title>` for display and groups supporting files. Independent ISO/CSO/CHD/PBP/ZIP/7Z files in that directory remain separate selectable games.
- Wii U extracted games require exactly one `code/*.rpx`, with content and meta data present. The RPX is the launch entry and the complete package accompanies it. Incomplete/ambiguous packages stay visible for repair. A standalone RPX outside a code directory remains a recognizable executable.
- Arcade/NAOMI archives include CHDs under the same-basename child directory as companions. Archive support does not establish BIOS availability or the correct emulator's ROM-set version.
- ScummVM requires an explicit `.scummvm` marker. Arbitrary game-data folders are not guessed to be a recognized ScummVM game. Vita installed directories and unmarked DOS/game-engine folders are not guessed into game entries; recognized game packages and executables remain available.
- Disc filenames such as “Disc 1” and “Disc 2” alone are not enough to silently merge games; an explicit playlist establishes grouping.

## Bounds and recovery

`RomScanPlanner.plan(RomScanRequest)` returns planned entries, unresolved entries and issues. `needsDescriptorText(name)` and `MAX_DESCRIPTOR_BYTES = 262144` are exposed both at package level and through `RomScanPlanner`'s companion. The caller must provide complete descriptor text keyed by document ID, not a truncated prefix, and enforce its own total descriptor budget.

References are resolved relative to their descriptor. Parent references are accepted only while they stay in the enumerated root. Absolute paths, drive paths, content/network URLs, binary descriptors, ambiguous case-insensitive matches, duplicate provider identities/paths and missing/empty files are rejected. Exact-case matches take precedence; a fallback case-insensitive match must be unique.

Unassigned valid groups carry companions and `requiresRepair=false`, allowing console assignment. Invalid descriptors and packages carry `requiresRepair=true`, preventing a console override from bypassing absent files. Invalid groups never suppress unrelated members. Provider metadata conflicts are reported as issues; the storage layer must not interpret an incomplete/provider-failed enumeration as authoritative deletion.

Named BIOS filtering is conservative, not a firmware database: renamed firmware may still appear as an ambiguous candidate, and package decryption/install prerequisites remain emulator-specific. NSZ/XCZ, ECM, installable CIA/VPK/PKG, archive containers and compressed image formats are recognized without claiming automatic conversion or direct launch support.

## Evidence and primary references

The JVM tests cover populated console discovery, ambiguity and correction, archives with multiple extracted games, CUE/GDI/M3U grouping, missing and cyclic companions, traversal and URL rejection, case collisions, bounds, duplicate provider metadata, package handling, BIOS exclusion, zero-byte files, stable ordering and representative family coverage. Validation is run with the combined feature batch, not separately during implementation.

Primary references inspected on 2026-09-08:

- [Libretro's maintained core metadata](https://github.com/libretro/libretro-core-info): supported extensions and firmware names; including [FCEUmm](https://raw.githubusercontent.com/libretro/libretro-core-info/master/fceumm_libretro.info), [Snes9x](https://raw.githubusercontent.com/libretro/libretro-core-info/master/snes9x_libretro.info), [Genesis Plus GX](https://raw.githubusercontent.com/libretro/libretro-core-info/master/genesis_plus_gx_libretro.info), [PCSX ReARMed](https://raw.githubusercontent.com/libretro/libretro-core-info/master/pcsx_rearmed_libretro.info), [MAME](https://raw.githubusercontent.com/libretro/libretro-core-info/master/mame_libretro.info), [PUAE](https://raw.githubusercontent.com/libretro/libretro-core-info/master/puae_libretro.info), [Atari800](https://raw.githubusercontent.com/libretro/libretro-core-info/master/atari800_libretro.info), [Fuse](https://raw.githubusercontent.com/libretro/libretro-core-info/master/fuse_libretro.info), [blueMSX](https://raw.githubusercontent.com/libretro/libretro-core-info/master/bluemsx_libretro.info) and [Beetle NeoPop](https://raw.githubusercontent.com/libretro/libretro-core-info/master/mednafen_ngp_libretro.info).
- [Libretro disc swapping](https://docs.libretro.com/guides/disc-swapping/) and [Flycast](https://docs.libretro.com/library/flycast/): explicit M3U disc groups, CUE/GDI media and archive-plus-CHD layouts.
- [PPSSPP game formats](https://www.ppsspp.org/docs/getting-started/dumping-games/): ISO, CSO, CHD and EBOOT.PBP directories.
- [Dolphin's format documentation](https://github.com/dolphin-emu/dolphin/blob/master/docs/WiaAndRvz.md): WIA/RVZ containers, distinct from generic extraction.
- [Azahar's supported inputs](https://github.com/azahar-emu/azahar/wiki/Dumping-Games): decrypted CCI/3DS, installable CIA, CXI and 3DSX.
- [Cemu FAQ](https://cemu.info/faq.html): WUD/WUX/WUA and format-specific requirements.
- [DOSBox Pure](https://docs.libretro.com/library/dosbox_pure/): DOS media, DOSZ, executables and playlists.
- [ScummVM's own detection workflow](https://docs.scummvm.org/en/latest/use_scummvm/add_play_games.html): game-data directory detection belongs to the engine and cannot be inferred merely from a generic directory name.
