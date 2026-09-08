# Missing artwork — 8 September 2026

Version **0.4.0 (code 7)** fills missing ROM artwork in the existing card slots. It first reads available ES-DE media, then downloads matched images from Libretro. Covers arrive progressively, with a small pending hint and the existing fallback while waiting. Card dimensions, collection order, focus and console filters remain owned by the existing presentation. Settings → Artwork provides automatic/paused mode, counts and retry.

## Provider approval and request contract

The user requested scraping missing artwork and explicitly confirmed **“Allow Libretro downloads and install.”** This resolved the installation approval block. The only online provider is `https://thumbnails.libretro.com`, using the public collections documented by [Libretro](https://docs.libretro.com/guides/roms-playlists-thumbnails/) and listed in its [thumbnail repository](https://github.com/libretro-thumbnails/libretro-thumbnails).

- Only HTTPS GETs are allowed, to the exact thumbnail host with no user information, custom port or redirects. Requests fetch console directory indexes and then a matching public PNG filename. There are no request bodies, credentials, API keys or account setup. ROM contents, saves, local paths and gamelist files are not uploaded. The selected console/image name and normal connection metadata are visible to the provider.
- Matching happens on the device. Full region/name matches take precedence; normalized title matches retain sequel numbers and demo/prototype/beta/sample/hack distinctions. Region fallbacks prefer USA, World, then Europe. Box art is preferred, with screenshots as a fallback. Unsupported collections or unmatched titles retain the local fallback; there is no arbitrary fuzzy match.
- One serialized worker uses a minimum 500ms request interval, 10-second connect/read timeouts, a 16MiB index cap and a 4MiB image response cap. Network failures use bounded retries and backoff; 429/503 honors a numeric Retry-After. These are application limits, not a provider service-level guarantee.
- Settings credit Libretro and the original artwork rights holders. Images are downloaded to the user's device cache; they are not bundled as application assets. No paid subscription or paid API integration is configured.

## Storage, local reuse and recovery

Artwork uses a separate Room **`artwork.db`, version 1**, storing per-item state, provider, matching title, file/source references, priority, last access and retries. The existing catalog database/schema and favorite, override and recent-history tables are unchanged. Manual artwork references take precedence and exclude those entries from the automatic queue.

The ES-DE resolver reads default ES-DE directories on approved mounted shared volumes, matching gamelist paths across archive extensions and nested folders. It considers covers, miximages, screenshots, 3D boxes and title screens. Gamelists with an ES-DE alternative-emulator sibling are supported; external XML entities are rejected. XML/file sizes and decoded dimensions are bounded. Local originals are never changed. Indexed gamelist lookup avoids repeatedly comparing every filename in a large collection. Rejected local images can fall back to a downloaded cover without rediscovering the rejected file in a loop.

The durable queue runs outside the UI, prioritizes requested cards, and resumes through WorkManager. Pause is persisted; an already running request may finish before the next pause check. Retry refreshes local lookup/index state and retries missing or failed rows while preserving ready covers. A 15-minute recovery worker also re-enqueues interrupted work. Android scheduling and network/provider latency can delay completion.

Private downloaded images are limited to 192MiB and index files to 64MiB; parsed indexes retain eight collections. Old downloaded images are evicted by last access and wait until requested again before downloading, avoiding a background eviction loop. Decoded artwork has a 16MiB memory cache, two decode slots and a maximum target dimension near 768px. Images update inside fixed card bounds; no artwork operation launches an item or moves selection.

## Verification

The [verification record](artwork-040-verification.txt) contains the commands, physical checks and final APK checksum. **205 unique tests passed**, with one Windows symlink fixture skipped; lint has zero errors and debug/release builds passed. Android tests used the connected physical Retroid Pocket Flip 2 only. The main app instrumentation suite was not run because its install/uninstall lifecycle can remove user app data; isolated data-module tests used disposable fixtures.

Fresh screenshots show:

- [Existing ES-DE covers on Home](artwork-040-home.png).
- [Jaguar cards waiting for artwork](artwork-040-pending.png) and [the same cards after actual Libretro downloads](artwork-040-loaded.png), with the first card selected in both captures.
- [Persisted pause after restart](artwork-040-paused.png).
- [PS2 local cover reuse](artwork-040-ps2.png).
- [Downloaded covers after a cold start with Wi-Fi off](artwork-040-offline.png); Wi-Fi was restored afterward.
- [Retry preserves 3,148 ready covers](artwork-040-retry.png) and [automatic scraping resumed](artwork-040-settings.png).

The physical catalog retained 3,773 ROMs. Inspection of the app's own artwork database confirmed public Libretro image URLs and private cached PNG references for all 22 Jaguar games, plus Lynx/arcade downloads. These were real network downloads, separate from the fake-HTTP fixtures in native tests. Source ROM/save/ES-DE files were not altered.

Gamepad-source A/D-pad injections verified pause, retry, resume and consecutive toggles without leaving the row. The latter exposed a stale focused action; the final build refreshes both its footer label and callback when the mode changes. These are injected controller events on a physical device, not certification of every physical button or stick.

## Remaining coverage and scope

This is artwork enrichment, not full descriptive metadata import or a manual match/artwork picker. Custom ES-DE media directories outside the supported conventional locations may need explicit source configuration later. Unknown platforms, abbreviations without useful gamelist names, unsupported collections and unmatched releases can keep fallback cards. The complete cancellation/rate-limit/cache-pressure matrix, every emulator's offline game boot, and physical button/analog-axis acceptance remain open. GameNative/PC shortcut import was pending at this checkpoint and is subsequently implemented in [0.5.0](../F16/pc-integration-050.md). Batched ROM discovery/search work remains open.
