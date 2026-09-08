package dev.handheld.launcher.core.data.metadata

import dev.handheld.launcher.core.data.rom.source.shared.SharedStoragePaths
import dev.handheld.launcher.core.domain.rom.RomSource
import dev.handheld.launcher.core.domain.rom.scan.RomDocument
import dev.handheld.launcher.core.domain.rom.scan.RomPlatforms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

fun interface RomMetadataIdentifier {
    /** Identity evidence only. Several candidates explicitly represent a conflict. */
    suspend fun identify(source: RomSource, documents: List<RomDocument>): Map<String, Set<String>>
}

data class EsDeConsolePath(val platformId: String, val path: String)

/** Bounded ES-DE imports. Names/titles, emulator commands and artwork do not identify a ROM. */
object EsDeConsoleMetadata {
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_RECORDS = 30_000

    fun systems(xml: String): List<EsDeConsolePath> = parse(xml, "system").mapNotNull { fields ->
        val platform = fields["name"]?.let(RomPlatforms::matchingFolder)?.id ?: return@mapNotNull null
        fields["path"]?.let { EsDeConsolePath(platform, it) }
    }

    fun games(xml: String, platformId: String): List<EsDeConsolePath> {
        require(RomPlatforms.byId(platformId) != null)
        return parse(xml, "game").mapNotNull { fields -> fields["path"]?.let { EsDeConsolePath(platformId, it) } }
    }

    /** Absolute paths or source-relative paths without traversal; no URI or variable expansion. */
    fun file(reference: String, root: File? = null): File? {
        if (reference.isBlank() || reference.length > 4096 || '\u0000' in reference || '\\' in reference || '%' in reference) return null
        val value = reference.removePrefix("./")
        if (value.split('/').any { it == "." || it == ".." }) return null
        val file = File(value)
        // A native Windows drive is supported for JVM fixtures/import tools; Android does
        // not consider it absolute, so URI schemes and foreign drive paths remain rejected.
        if (':' in value && !(Regex("^[A-Za-z]:/").containsMatchIn(value) && file.isAbsolute)) return null
        return if (file.isAbsolute) file else root?.let { File(it, value) }
    }

    private fun parse(xml: String, record: String): List<Map<String, String>> {
        require(xml.length <= MAX_BYTES && xml.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Console metadata exceeds its import limit." }
        require(!xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true)) { "External XML declarations are unsupported." }
        val records = ArrayList<Map<String, String>>()
        val parser = SAXParserFactory.newInstance().newSAXParser().xmlReader
        parser.entityResolver = org.xml.sax.EntityResolver { _, _ -> throw SAXException("External entities are unsupported.") }
        parser.contentHandler = object : DefaultHandler() {
            var depth = 0
            var recordDepth = -1
            var field = ""
            var text = StringBuilder()
            var fields = linkedMapOf<String, String>()
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
                if (++depth > 32) throw SAXException("Console metadata is too deeply nested.")
                if (qName == record) {
                    if (recordDepth >= 0) throw SAXException("Nested metadata records are unsupported.")
                    recordDepth = depth; fields = linkedMapOf()
                }
                field = if (recordDepth >= 0 && depth == recordDepth + 1 && qName in setOf("name", "path")) qName else ""
                text = StringBuilder()
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (field.isEmpty()) return
                if (text.length + length > 4096) throw SAXException("Console metadata field exceeds its limit.")
                text.append(ch, start, length)
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                if (field == qName) {
                    if (fields.put(field, text.toString().trim()) != null) throw SAXException("Duplicate metadata fields are ambiguous.")
                    field = ""
                }
                if (qName == record && depth == recordDepth) {
                    if (records.size >= MAX_RECORDS) throw SAXException("Console metadata exceeds its record limit.")
                    records += fields
                    recordDepth = -1
                }
                depth--
            }
        }
        // ES-DE permits an alternativeEmulator sibling before gameList.
        val body = xml.removePrefix("\uFEFF").replace(Regex("<\\?xml[^?]*\\?>", RegexOption.IGNORE_CASE), "")
        parser.parse(InputSource(StringReader("<metadata>$body</metadata>")))
        return records
    }
}

/** Reads existing ES-DE metadata only; does not download databases, hash ROMs, or modify files. */
class EsDeRomMetadataIdentifier(private val storage: SharedStoragePaths) : RomMetadataIdentifier {
    private data class ParsedImport(val modified: Long, val bytes: Long, val records: List<EsDeConsolePath>)
    private val imports = LinkedHashMap<String, ParsedImport>(8, .75f, true)

    override suspend fun identify(source: RomSource, documents: List<RomDocument>): Map<String, Set<String>> = withContext(Dispatchers.IO) {
        val sourceKey = source.physicalRootKey ?: return@withContext emptyMap()
        if (!storage.hasAccess()) return@withContext emptyMap()
        val sourceRoot = runCatching { storage.resolve(sourceKey) }.getOrNull() ?: return@withContext emptyMap()
        val byPath = documents.filterNot { it.isDirectory }.mapNotNull { document ->
            EsDeConsoleMetadata.file(document.relativePath, sourceRoot)?.let { it.path to document.documentId }
        }.toMap()
        val result = linkedMapOf<String, MutableSet<String>>()
        var budget = 32 * 1024 * 1024
        var records = 0
        var truncated = false
        val volumes = storage.volumes()
        fun read(id: String, platform: String? = null): List<EsDeConsolePath>? = runCatching {
            val file = storage.resolve(id)
            if (!file.isFile || file.length() <= 0) return@runCatching null
            if (file.length() > minOf(budget, EsDeConsoleMetadata.MAX_BYTES)) { truncated = true; return@runCatching null }
            val key = "$id|${platform.orEmpty()}"
            val modified = file.lastModified()
            val size = file.length()
            synchronized(imports) { imports[key]?.takeIf { it.modified == modified && it.bytes == size } }?.let {
                budget -= size.toInt()
                return@runCatching it.records
            }
            val bytes = file.inputStream().use { it.readNBytes(minOf(budget, EsDeConsoleMetadata.MAX_BYTES) + 1) }
            budget -= bytes.size
            if (budget < 0 || bytes.size > EsDeConsoleMetadata.MAX_BYTES) { truncated = true; return@runCatching null }
            val xml = bytes.toString(Charsets.UTF_8)
            val parsed = if (platform == null) EsDeConsoleMetadata.systems(xml) else EsDeConsoleMetadata.games(xml,platform)
            synchronized(imports) {
                imports[key] = ParsedImport(modified,bytes.size.toLong(),parsed)
                while (imports.size > 8 || imports.values.sumOf { it.bytes } > 8 * 1024 * 1024 || imports.values.sumOf { it.records.size } > 50_000) {
                    imports.remove(imports.keys.first())
                }
            }
            parsed
        }.getOrNull()
        fun matched(file: File, platform: String) {
            val documentId = byPath[file.path] ?: return
            result.getOrPut(documentId) { linkedSetOf() }.add(platform)
        }
        fun verifiedRoot(file: File): File? = runCatching {
            val volume = volumes.firstOrNull { file.toPath().startsWith(it.directory.canonicalFile.toPath()) } ?: return@runCatching null
            val relative = volume.directory.canonicalFile.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
            storage.resolve("${volume.key}:$relative").takeIf { it.isDirectory && it.path == file.path }
        }.getOrNull()
        for (volume in volumes) for (folder in listOf("ES-DE", "ESDE", "es-de")) {
            currentCoroutineContext().ensureActive()
            if (budget <= 0 || records >= 60_000) { truncated = true; break }
            val base = "${volume.key}:$folder"
            val systems = read("$base/custom_systems/es_systems.xml").orEmpty()
            records += systems.size
            if (records > 60_000) { truncated = true; break }
            val roots = systems.mapNotNull { entry ->
                val file = EsDeConsoleMetadata.file(entry.path)?.let(::verifiedRoot) ?: return@mapNotNull null
                // A config's root must physically contain this registered source (or be inside it).
                val common = sourceRoot.toPath().startsWith(file.toPath()) || file.toPath().startsWith(sourceRoot.toPath())
                if (!common) null else entry.platformId to file
            }.groupBy({ it.first }, { it.second })
            roots.forEach { (platform, configuredRoots) ->
                byPath.forEach { (path, _) ->
                    if (configuredRoots.any { File(path).toPath().startsWith(it.toPath()) }) matched(File(path), platform)
                }
            }
            val directories = runCatching { storage.children("$base/gamelists", 256) }.getOrDefault(emptyList())
            for ((id, directory) in directories) {
                currentCoroutineContext().ensureActive()
                val platform = RomPlatforms.matchingFolder(directory.name)?.id ?: continue
                val games = read("$id/gamelist.xml",platform) ?: continue
                records += games.size
                if (records > 60_000) { truncated = true; break }
                for (game in games) {
                    val absolute = EsDeConsoleMetadata.file(game.path)
                    if (absolute != null) matched(absolute, platform)
                    else roots[platform].orEmpty().forEach { root -> EsDeConsoleMetadata.file(game.path, root)?.let { matched(it, platform) } }
                }
            }
        }
        if (truncated || budget <= 0 || records >= 60_000) emptyMap() else result.mapValues { it.value.toSet() }
    }
}
