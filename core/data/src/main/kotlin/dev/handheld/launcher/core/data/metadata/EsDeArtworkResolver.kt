package dev.handheld.launcher.core.data.metadata

import dev.handheld.launcher.core.data.rom.source.shared.SharedStoragePaths
import dev.handheld.launcher.core.domain.rom.RomEntry
import dev.handheld.launcher.core.domain.rom.RomSource
import dev.handheld.launcher.core.domain.rom.scan.RomPlatforms
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.StringReader
import java.util.Locale
import javax.xml.parsers.SAXParserFactory

data class LocalArtwork(val file: File?, val title: String?)
data class EsDeGame(val path: String, val name: String?, val image: String?)

/** Also accepts ES-DE's optional alternativeEmulator sibling before gameList. */
object EsDeGamelist {
    /** Normalize once per gamelist, rather than walking every game for each visible card. */
    class Index(games: List<EsDeGame>) {
        private val paths = games.groupBy { stem(it.path).lowercase(Locale.ROOT) }
        private val leaves = games.groupBy { stem(it.path).substringAfterLast('/').lowercase(Locale.ROOT) }
        fun find(relative: String): EsDeGame? {
            val key = stem(relative).lowercase(Locale.ROOT)
            return paths[key]?.singleOrNull() ?: leaves[key.substringAfterLast('/')]?.singleOrNull()
        }
    }

    fun parse(xml: String): List<EsDeGame> {
        require(xml.length <= 8 * 1024 * 1024) { "Gamelist exceeds size limit" }
        require(!xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true)) { "External XML declarations are unsupported" }
        val content = "<esde>" + xml.replace(Regex("<\\?xml[^?]*\\?>", RegexOption.IGNORE_CASE), "") + "</esde>"
        val games = ArrayList<EsDeGame>()
        val factory = SAXParserFactory.newInstance()
        val parser = factory.newSAXParser().xmlReader
        parser.entityResolver = org.xml.sax.EntityResolver { _, _ -> InputSource(StringReader("")) }
        parser.contentHandler = object : DefaultHandler() {
            var inGame = false
            var field = ""
            var text = StringBuilder()
            var path: String? = null
            var name: String? = null
            var image: String? = null
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
                if (qName == "game") { inGame = true; path = null; name = null; image = null }
                field = if (inGame && qName in setOf("path", "name", "image", "thumbnail")) qName else ""
                text = StringBuilder()
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (field.isNotEmpty() && text.length + length <= 4096) text.append(ch, start, length)
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                val value = text.toString().trim().takeIf { it.isNotEmpty() }
                when (qName) {
                    "path" -> path = value
                    "name" -> name = value
                    "image" -> image = value ?: image
                    "thumbnail" -> if (image == null) image = value
                    "game" -> {
                        if (games.size < 30_000) path?.let { games += EsDeGame(it, name, image) }
                        inGame = false
                    }
                }
                field = ""
            }
        }
        parser.parse(InputSource(StringReader(content)))
        return games
    }
    fun stem(path: String): String {
        val normalized = path.replace('\\', '/').removePrefix("./")
        val format = RomPlatforms.formatOf(normalized)
        return if (format != null) normalized.dropLast(format.length + 1) else normalized.substringBeforeLast('.', normalized)
    }
}

/** Read-only lookup of existing media. No folder walk is performed per card. */
class EsDeArtworkResolver(private val storage: SharedStoragePaths) {
    private data class SystemDirectory(val root: File, val name: String, val platform: String?)
    private data class GamelistIndex(val modified: Long, val size: Long, val games: EsDeGamelist.Index)
    private var refreshedAt = 0L
    private var systems = emptyList<SystemDirectory>()
    private var volumes = emptyMap<String, File>()
    private val gamelists = object : LinkedHashMap<String, GamelistIndex>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GamelistIndex>?) = size > 20
    }

    @Synchronized fun invalidate() { refreshedAt = 0; gamelists.clear() }

    @Synchronized
    fun resolve(entry: RomEntry, source: RomSource, rejectedFile: String? = null): LocalArtwork {
        refreshRoots()
        if (volumes.isEmpty()) return LocalArtwork(null, null)
        val sourceFile = source.physicalRootKey?.let { runCatching { storage.resolve(it) }.getOrNull() }
        val ownVolume = source.physicalRootKey?.substringBefore(':')?.lowercase(Locale.ROOT)
        val candidates = systems.filter { it.platform == entry.platformId && entry.platformId != null }
            .sortedBy { if (volumes[ownVolume]?.let { volume -> it.root.toPath().startsWith(volume.toPath()) } == true) 0 else 1 }
        var matchTitle: String? = null
        for (system in candidates) {
            val gamelistFile = File(system.root, "gamelists/${system.name}/gamelist.xml")
            val match = game(gamelistFile, entry.relativePath)
            matchTitle = matchTitle ?: match?.name
            match?.image?.let { reference ->
                val base = sourceFile ?: gamelistFile.parentFile
                val file = if (reference.startsWith('/')) File(reference) else File(base, reference)
                safeImage(file, listOfNotNull(system.root, sourceFile), rejectedFile)?.let { return LocalArtwork(it, matchTitle) }
            }
            val names = listOfNotNull(match?.path?.let(EsDeGamelist::stem), EsDeGamelist.stem(entry.relativePath))
                .flatMap { listOf(it, it.substringAfterLast('/')) }.distinct()
            for (kind in listOf("covers", "miximages", "screenshots", "3dboxes", "titlescreens")) {
                for (name in names) for (extension in listOf("png", "jpg", "jpeg", "webp")) {
                    val file = File(system.root, "downloaded_media/${system.name}/$kind/$name.$extension")
                    safeImage(file, listOf(system.root), rejectedFile)?.let { return LocalArtwork(it, matchTitle) }
                }
            }
        }
        // A conventional gamelist beside a selected ROM folder remains usable without ES-DE.
        if (sourceFile != null) {
            val match = game(File(sourceFile, "gamelist.xml"), entry.relativePath)
            matchTitle = matchTitle ?: match?.name
            match?.image?.let { reference ->
                safeImage(if (reference.startsWith('/')) File(reference) else File(sourceFile, reference), listOf(sourceFile), rejectedFile)
                    ?.let { return LocalArtwork(it, matchTitle) }
            }
        }
        return LocalArtwork(null, matchTitle)
    }

    @Synchronized fun readable(file: File): Boolean {
        if (!storage.hasAccess()) return false
        refreshRoots()
        val volume = volumes.entries.firstOrNull { file.toPath().startsWith(it.value.toPath()) } ?: return false
        return runCatching {
            val relative = volume.value.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
            storage.resolve("${volume.key}:$relative").isFile
        }.getOrDefault(false)
    }

    private fun safeImage(file: File, roots: List<File>, rejectedFile: String?): File? = runCatching {
        if (!file.isFile || file.length() !in 1..16 * 1024 * 1024 || file.extension.lowercase() !in setOf("png", "jpg", "jpeg", "webp")) return null
        val canonical = file.canonicalFile
        if (canonical.path == rejectedFile) return null
        if (roots.none { canonical.toPath().startsWith(it.canonicalFile.toPath()) }) return null
        if (!readable(canonical)) return null
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(canonical.path, bounds)
        canonical.takeIf { bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192 && bounds.outWidth.toLong() * bounds.outHeight <= 24_000_000 }
    }.getOrNull()

    private fun refreshRoots() {
        if (System.currentTimeMillis() - refreshedAt < 30_000) return
        refreshedAt = System.currentTimeMillis()
        volumes = storage.volumes().associate { it.key to it.directory.canonicalFile }
        systems = volumes.values.flatMap { volume ->
            listOf("ES-DE", "ESDE", "es-de").map { File(volume, it) }.filter { it.isDirectory }.flatMap { root ->
                val names = listOf("gamelists", "downloaded_media").flatMap { folder ->
                    File(root, folder).listFiles()?.filter { it.isDirectory }?.take(256).orEmpty().map { it.name }
                }.distinct()
                names.map { SystemDirectory(root, it, RomPlatforms.matchingFolder(it)?.id) }
            }
        }
    }

    private fun game(file: File, relative: String): EsDeGame? {
        if (!file.isFile || file.length() > 8 * 1024 * 1024 || !readable(file)) return null
        val index = gamelists[file.path]?.takeIf { it.modified == file.lastModified() && it.size == file.length() }
            ?: GamelistIndex(file.lastModified(), file.length(), EsDeGamelist.Index(runCatching { EsDeGamelist.parse(file.readText()) }.getOrDefault(emptyList())))
                .also { gamelists[file.path] = it }
        return index.games.find(relative)
    }
}
