package dev.handheld.launcher.core.data.metadata

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

class ArtworkRequestFailure(val retryAfterMillis: Long = 60_000, message: String = "Artwork download will retry") : IOException(message)

fun interface ArtworkHttp {
    /** Null is a definitive 404. Transient responses must not become a cached no-match. */
    suspend fun get(url: String, maximumBytes: Int): ByteArray?
}

class LibretroHttp : ArtworkHttp {
    private var lastRequestAt = 0L
    override suspend fun get(url: String, maximumBytes: Int): ByteArray? {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host == "thumbnails.libretro.com" && uri.userInfo == null && uri.port == -1)
        delay((500L - (System.currentTimeMillis() - lastRequestAt)).coerceAtLeast(0))
        currentCoroutineContext().ensureActive()
        lastRequestAt = System.currentTimeMillis()
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "HandheldLauncher/0.4 (on-demand artwork)")
        try {
            when (connection.responseCode) {
                404 -> return null
                200 -> Unit
                429, 503 -> throw ArtworkRequestFailure(
                    connection.getHeaderField("Retry-After")?.toLongOrNull()?.coerceIn(30, 86_400)?.times(1_000) ?: 300_000,
                    "Artwork service is busy; retry scheduled",
                )
                else -> throw ArtworkRequestFailure(message = "Artwork service is unavailable; retry scheduled")
            }
            if (connection.contentLengthLong > maximumBytes) throw IOException("Artwork response exceeds size limit")
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > maximumBytes) throw IOException("Artwork response exceeds size limit")
                    output.write(buffer, 0, count)
                }
            }
            return output.toByteArray()
        } finally { connection.disconnect() }
    }
}

data class ArtworkDownload(val bytes: ByteArray, val url: String, val title: String)

/** Public thumbnail indexes are cached; only a matched PNG is fetched for a game. */
class LibretroArtworkProvider(private val directory: File, private val http: ArtworkHttp = LibretroHttp()) {
    private val indexes = object : LinkedHashMap<String, ThumbnailNames.Index>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ThumbnailNames.Index>?) = size > 8
    }
    fun invalidateIndexes() {
        indexes.clear()
        directory.listFiles()?.filter { it.extension == "index" }?.forEach { it.delete() }
    }
    suspend fun find(platformId: String?, titles: List<String>): ArtworkDownload? {
        val system = systems[platformId] ?: return null
        for (kind in listOf("Named_Boxarts", "Named_Snaps")) {
            val base = "https://thumbnails.libretro.com/${segment(system)}/$kind/"
            val names = index(base)
            val match = names.match(titles) ?: continue
            val url = base + segment(match)
            val bytes = http.get(url, MAX_IMAGE_BYTES) ?: continue
            return ArtworkDownload(bytes, url, match.removeSuffix(".png"))
        }
        return null
    }

    private suspend fun index(url: String): ThumbnailNames.Index {
        indexes[url]?.let { return it }
        directory.mkdirs()
        val file = File(directory, artworkHash(url) + ".index")
        val fresh = file.isFile && file.length() <= MAX_INDEX_BYTES && System.currentTimeMillis() - file.lastModified() < 7 * 86_400_000L
        val bytes = if (fresh) file.readBytes() else http.get(url, MAX_INDEX_BYTES)?.also {
            val temporary = File(directory, file.name + ".tmp")
            temporary.writeBytes(it)
            java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } ?: return ThumbnailNames.Index(emptyList()).also { indexes[url] = it }
        val names = ThumbnailNames.Index(ThumbnailNames.parseIndex(bytes.toString(Charsets.UTF_8)))
        indexes[url] = names
        val files = directory.listFiles()?.filter { it.extension == "index" }?.sortedBy { it.lastModified() }.orEmpty()
        var totalBytes = files.sumOf { it.length() }
        for (old in files) {
            if (totalBytes <= 64L * 1024 * 1024) break
            if (old == file) continue
            totalBytes -= old.length()
            old.delete()
        }
        return names
    }

    companion object {
        const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
        const val MAX_INDEX_BYTES = 16 * 1024 * 1024
        fun segment(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        val systems = mapOf(
            "nes" to "Nintendo - Nintendo Entertainment System", "fds" to "Nintendo - Family Computer Disk System",
            "snes" to "Nintendo - Super Nintendo Entertainment System", "gb" to "Nintendo - Game Boy",
            "gbc" to "Nintendo - Game Boy Color", "gba" to "Nintendo - Game Boy Advance",
            "n64" to "Nintendo - Nintendo 64", "nds" to "Nintendo - Nintendo DS", "3ds" to "Nintendo - Nintendo 3DS",
            "gamecube" to "Nintendo - GameCube", "wii" to "Nintendo - Wii", "wiiu" to "Nintendo - Wii U",
            "virtualboy" to "Nintendo - Virtual Boy", "pokemini" to "Nintendo - Pokemon Mini",
            "psx" to "Sony - PlayStation", "ps2" to "Sony - PlayStation 2", "ps3" to "Sony - PlayStation 3",
            "psp" to "Sony - PlayStation Portable", "psvita" to "Sony - PlayStation Vita", "sg1000" to "Sega - SG-1000",
            "mastersystem" to "Sega - Master System - Mark III", "megadrive" to "Sega - Mega Drive - Genesis",
            "gamegear" to "Sega - Game Gear", "segacd" to "Sega - Mega-CD - Sega CD", "sega32x" to "Sega - 32X",
            "saturn" to "Sega - Saturn", "dreamcast" to "Sega - Dreamcast", "naomi" to "Sega - Naomi",
            "atomiswave" to "Atomiswave", "arcade" to "MAME", "neogeo" to "SNK - Neo Geo", "neogeocd" to "SNK - Neo Geo CD",
            "pce" to "NEC - PC Engine - TurboGrafx 16", "pcecd" to "NEC - PC Engine CD - TurboGrafx-CD",
            "supergrafx" to "NEC - PC Engine SuperGrafx", "atari2600" to "Atari - 2600", "atari5200" to "Atari - 5200",
            "atari7800" to "Atari - 7800", "atari800" to "Atari - 8-bit", "atarist" to "Atari - ST",
            "atarilynx" to "Atari - Lynx", "atarijaguar" to "Atari - Jaguar", "wonderswan" to "Bandai - WonderSwan",
            "wonderswancolor" to "Bandai - WonderSwan Color", "neogeopocket" to "SNK - Neo Geo Pocket",
            "neogeopocketcolor" to "SNK - Neo Geo Pocket Color", "colecovision" to "Coleco - ColecoVision",
            "intellivision" to "Mattel - Intellivision", "vectrex" to "GCE - Vectrex", "3do" to "The 3DO Company - 3DO",
            "amiga" to "Commodore - Amiga", "c64" to "Commodore - 64", "zxspectrum" to "Sinclair - ZX Spectrum",
            "amstradcpc" to "Amstrad - CPC", "msx" to "Microsoft - MSX", "msx2" to "Microsoft - MSX2",
            "dos" to "DOS", "scummvm" to "ScummVM",
        )
    }
}

object ThumbnailNames {
    private val links = Regex("href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val annotations = Regex("\\s*[\\[(][^\\])]*[\\])]")
    private val punctuation = Regex("[^\\p{L}\\p{N}]+")
    private val combiningMarks = Regex("\\p{M}")
    fun parseIndex(html: String): List<String> = links.findAll(html).mapNotNull { link ->
        runCatching { URLDecoder.decode(link.groupValues[1].replace("+", "%2B"), "UTF-8") }.getOrNull()
            ?.takeIf { it.endsWith(".png", true) && '/' !in it && '\\' !in it && it.length <= 512 && !it.startsWith('.') }
    }.take(100_000).distinct().toList()

    fun match(titles: List<String>, files: List<String>): String? = Index(files).match(titles)

    class Index(files: List<String>) {
        private val exact = files.groupBy { normalize(it.substringBeforeLast('.')) }
        private val base = files.groupBy { normalize(annotations.replace(it.substringBeforeLast('.'), "")) }
        fun match(titles: List<String>): String? {
        // Prefer the full filename, including its region. Never fuzzy-match a different sequel.
        titles.forEach { title -> exact[normalize(title)]?.singleOrNull()?.let { return it } }
        for (title in titles) {
            val key = normalize(annotations.replace(title, ""))
            if (key.isEmpty()) continue
            val candidates = base[key].orEmpty().filter { edition(it) == edition(title) }
            if (candidates.isNotEmpty()) return candidates.sortedWith(compareBy<String> {
                when { it.contains("(USA") -> 0; it.contains("(World") -> 1; it.contains("(Europe") -> 2; else -> 3 }
            }.thenBy { it }).first()
        }
        return null
        }
        // A demo, prototype, hack or beta must not quietly receive a retail edition's art.
        private fun edition(value: String) = listOf("demo", "proto", "beta", "sample", "hack").filter { marker ->
            annotations.findAll(value).any { it.value.contains(marker, true) }
        }
    }
    private fun normalize(value: String) = punctuation.replace(
        Normalizer.normalize(value, Normalizer.Form.NFKD).lowercase(Locale.ROOT).replace(combiningMarks, ""), "",
    )
}

fun artworkHash(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
