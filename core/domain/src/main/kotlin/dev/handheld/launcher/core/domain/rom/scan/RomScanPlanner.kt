package dev.handheld.launcher.core.domain.rom.scan

import java.util.Locale

const val MAX_DESCRIPTOR_BYTES: Int = 262_144

fun needsDescriptorText(name: String): Boolean =
    RomPlatforms.formatOf(name) in setOf("cue", "gdi", "toc", "m3u", "m3u8")

/**
 * Plans one complete enumeration, including archive contents after the caller extracts them.
 * This class is stateless. It never makes assumptions about SAF IDs, URIs, durable IDs or access.
 */
class RomScanPlanner {
    fun plan(request: RomScanRequest): RomScanPlan = Planning(request).run()

    companion object {
        const val MAX_DESCRIPTOR_BYTES: Int = dev.handheld.launcher.core.domain.rom.scan.MAX_DESCRIPTOR_BYTES
        fun needsDescriptorText(name: String): Boolean = dev.handheld.launcher.core.domain.rom.scan.needsDescriptorText(name)
    }
}

private class Planning(private val request: RomScanRequest) {
    private val issues = mutableListOf<RomScanIssue>()
    private val nodes = linkedMapOf<String, Node>()
    private val candidates = linkedMapOf<String, Candidate>()
    private val completed = mutableMapOf<String, Resolution>()
    private val active = mutableSetOf<String>()
    private var foldedPaths = emptyMap<String, List<Node>>()
    private val overrides = request.folderPlatformOverrides.mapNotNull { (path, platform) ->
        normalizePath(path, allowEmpty = true)?.let { it to platform }
    }.toMap()

    fun run(): RomScanPlan {
        val normalized = request.documents.mapNotNull { document ->
            val path = normalizePath(document.relativePath)
            if (path == null || document.documentId.isBlank()) {
                issue(document.documentId, document.relativePath, RomScanIssueCode.INVALID_DOCUMENT_PATH, "Document path is not a safe relative path.")
                null
            } else Node(document, path)
        }
        val duplicateIds = normalized.groupBy { it.document.documentId }.filterValues { it.map(Node::path).distinct().size > 1 }.keys
        normalized.groupBy(Node::path).toSortedMap().forEach { (path, samePath) ->
            val distinct = samePath.distinctBy { it.document.documentId }
            if (distinct.size != 1 || distinct.any { it.document.documentId in duplicateIds }) {
                distinct.forEach { issue(it, RomScanIssueCode.DUPLICATE_DOCUMENT_PATH, "Provider returned conflicting document identities or paths.") }
            } else nodes[path] = distinct.single()
        }
        foldedPaths = nodes.values.groupBy { it.path.lowercase(Locale.ROOT) }
        nodes.values.filterNot { it.document.isDirectory || excluded(it.path) }.forEach { node ->
            val format = RomPlatforms.formatOf(node.name) ?: return@forEach
            if (node.document.sizeBytes != null && node.document.sizeBytes <= 0L) {
                issue(node, RomScanIssueCode.EMPTY_FILE, "Empty files are not games.")
                return@forEach
            }
            val platform = contextPlatform(node)
            val applicable = RomPlatforms.candidatesFor(format)
            val reason = when {
                platform != null && RomPlatforms.byId(platform) == null -> {
                    issue(node, RomScanIssueCode.UNKNOWN_PLATFORM, "Selected console is no longer recognized.")
                    "Choose a supported console for this game."
                }
                platform != null && !RomPlatforms.supports(platform, format) -> {
                    issue(node, RomScanIssueCode.PLATFORM_FORMAT_MISMATCH, "File format does not match the selected or named console folder.")
                    "File format does not match this console. Choose the correct console."
                }
                else -> null
            }
            val selected = if (reason == null) platform ?: applicable.singleOrNull()?.takeUnless {
                format in contextRequiredFormats
            } else null
            candidates[node.path] = Candidate(node, format, selected, applicable, reason)
        }
        val resolutions = candidates.values.map { resolve(it, 0) }
        val suppressed = resolutions.filter { it.valid }.flatMap { it.companions }.toSet()
        val entries = mutableListOf<PlannedRomEntry>()
        val unresolved = mutableListOf<UnresolvedRomEntry>()
        resolutions.filterNot { it.candidate.node.document.documentId in suppressed }.forEach { result ->
            val candidate = result.candidate
            val node = candidate.node
            if (result.valid && result.platformId != null) {
                entries += PlannedRomEntry(node.document.documentId, node.path, result.title, result.platformId,
                    candidate.format, result.kind, result.companions)
            } else {
                unresolved += UnresolvedRomEntry(node.document.documentId, node.path, result.title, candidate.format,
                    result.platforms, result.reason ?: "Choose a console to identify this game.", result.companions, result.requiresRepair)
            }
        }
        return RomScanPlan(entries, unresolved, issues.filterNot {
            it.documentId in suppressed && it.code !in setOf(RomScanIssueCode.INVALID_DOCUMENT_PATH, RomScanIssueCode.DUPLICATE_DOCUMENT_PATH)
        }.distinct())
    }

    private fun resolve(candidate: Candidate, depth: Int): Resolution {
        completed[candidate.node.path]?.let { return it }
        val base = Resolution(candidate, title = title(candidate.node.name, candidate.format), platformId = candidate.platformId,
            platforms = candidate.platformId?.let(::setOf) ?: candidate.platforms, reason = candidate.reason,
            valid = candidate.reason == null,
            kind = if (candidate.format in RomPlatforms.archiveExtensions || candidate.format == "dosz") RomEntryKind.ARCHIVE else RomEntryKind.SINGLE_FILE)
        if (candidate.reason != null) return base.also { completed[candidate.node.path] = it }
        if (depth > 32 || !active.add(candidate.node.path)) {
            return fail(base, RomScanIssueCode.CYCLIC_PLAYLIST, "Playlist references form a cycle or exceed the nesting limit.")
        }
        val result = when {
            needsDescriptorText(candidate.node.name) -> descriptor(base, depth)
            candidate.format == "ccd" -> pairedDescriptor(base, "img", listOf("sub"))
            candidate.format == "mds" -> pairedDescriptor(base, "mdf", emptyList())
            candidate.platformId == "psp" && candidate.node.name.equals("EBOOT.PBP", true) -> pspPackage(base)
            candidate.platformId == "wiiu" && candidate.format == "rpx" -> wiiUPackage(base)
            candidate.platformId in setOf("arcade", "naomi", "atomiswave", "neogeo") && candidate.format in RomPlatforms.archiveExtensions -> arcadeSet(base)
            else -> base
        }
        active.remove(candidate.node.path)
        completed[candidate.node.path] = result
        return result
    }

    private fun descriptor(base: Resolution, depth: Int): Resolution {
        val candidate = base.candidate
        val text = request.descriptorText[candidate.node.document.documentId]
            ?: return fail(base, RomScanIssueCode.MISSING_DESCRIPTOR_TEXT, "Descriptor could not be read completely. Rescan when the file is available.")
        val parsed = RomDescriptorParser.parse(candidate.format, text)
        parsed.error?.let { return fail(base, RomScanIssueCode.INVALID_DESCRIPTOR, it) }
        val companions = mutableListOf<Node>()
        for (reference in parsed.paths) {
            val path = resolveReference(candidate.node.parent, reference)
                ?: return fail(base, RomScanIssueCode.INVALID_DESCRIPTOR, "Descriptor references a path outside this source or a URL.")
            val match = find(path)
            if (match.ambiguous) return fail(base, RomScanIssueCode.AMBIGUOUS_COMPANION, "Descriptor reference has more than one case-insensitive match: $reference")
            val node = match.node
            if (node == null || node.document.isDirectory || excluded(node.path) || node.document.sizeBytes == 0L) {
                return fail(base, RomScanIssueCode.MISSING_COMPANION, "Descriptor companion is missing or unavailable: $reference")
            }
            if (node.path == candidate.node.path) return fail(base, RomScanIssueCode.CYCLIC_PLAYLIST, "Descriptor references itself.")
            companions += node
        }
        val ids = linkedSetOf<String>()
        if (candidate.format == "m3u" || candidate.format == "m3u8") {
            var platforms = base.platforms
            var hasKnownPlatform = base.platformId != null
            for (node in companions) {
                val child = candidates[node.path]
                    ?: return fail(base, RomScanIssueCode.INVALID_DESCRIPTOR, "Playlist references a file that is not a recognized game: ${node.name}")
                val childResult = resolve(child, depth + 1)
                if (!childResult.valid) return fail(base, RomScanIssueCode.INVALID_DESCRIPTOR, "Playlist contains an incomplete or invalid game: ${node.name}")
                platforms = platforms.intersect(childResult.platforms)
                hasKnownPlatform = hasKnownPlatform || childResult.platformId != null
                ids += node.document.documentId
                ids += childResult.companions
            }
            if (platforms.isEmpty()) return fail(base, RomScanIssueCode.CROSS_PLATFORM_PLAYLIST, "Playlist combines incompatible console formats or folder assignments.")
            return base.copy(platformId = platforms.singleOrNull().takeIf { hasKnownPlatform }, platforms = platforms, kind = RomEntryKind.PLAYLIST, companions = ids.toList())
        }
        companions.forEach { ids += it.document.documentId }
        return base.copy(kind = RomEntryKind.DISC_DESCRIPTOR, companions = ids.toList())
    }

    private fun pairedDescriptor(base: Resolution, requiredExtension: String, optionalExtensions: List<String>): Resolution {
        val stem = base.candidate.node.path.dropLast(base.candidate.format.length)
        val required = find(stem + requiredExtension)
        if (required.ambiguous) return fail(base, RomScanIssueCode.AMBIGUOUS_COMPANION, "Disc image has conflicting case-insensitive filenames.")
        val image = required.node
        if (image == null || image.document.isDirectory || image.document.sizeBytes == 0L) {
            return fail(base, RomScanIssueCode.MISSING_COMPANION, "Disc descriptor requires its .$requiredExtension companion.")
        }
        val companions = mutableListOf(image.document.documentId)
        for (extension in optionalExtensions) {
            val optional = find(stem + extension)
            if (optional.ambiguous) return fail(base, RomScanIssueCode.AMBIGUOUS_COMPANION, "Disc companion has conflicting case-insensitive filenames.")
            optional.node?.takeUnless { it.document.isDirectory || it.document.sizeBytes == 0L }?.let { companions += it.document.documentId }
        }
        return base.copy(kind = RomEntryKind.DISC_DESCRIPTOR, companions = companions)
    }

    private fun pspPackage(base: Resolution): Resolution {
        val parent = base.candidate.node.parent
        if (parent.isEmpty()) return base.copy(title = request.rootDisplayName.ifBlank { base.title })
        val companions = descendants(parent).filterNot {
            it.path == base.candidate.node.path || RomPlatforms.formatOf(it.name) in setOf("iso", "cso", "chd", "pbp", "zip", "7z")
        }.map { it.document.documentId }
        return base.copy(title = parent.substringAfterLast('/'), kind = RomEntryKind.FOLDER_PACKAGE, companions = companions)
    }

    private fun wiiUPackage(base: Resolution): Resolution {
        val code = base.candidate.node.parent
        if (!code.substringAfterLast('/').equals("code", true)) return base
        val parent = code.substringBeforeLast('/', "")
        val contents = descendants(parent)
        val prefix = if (parent.isEmpty()) "" else "$parent/"
        val packageNames = contents.map { it.path.removePrefix(prefix).substringBefore('/').lowercase(Locale.ROOT) }.toSet()
        val executables = contents.filter { it.parent.equals(code, true) && RomPlatforms.formatOf(it.name) == "rpx" }
        if (!packageNames.containsAll(setOf("content", "meta")) || executables.size != 1) {
            return fail(base, RomScanIssueCode.INCOMPLETE_PACKAGE, "Wii U game folder needs content, meta, and exactly one code/*.rpx entry.")
        }
        return base.copy(title = parent.substringAfterLast('/').ifBlank { request.rootDisplayName.ifBlank { base.title } },
            kind = RomEntryKind.FOLDER_PACKAGE,
            companions = contents.filterNot { it.path == base.candidate.node.path }.map { it.document.documentId })
    }

    private fun arcadeSet(base: Resolution): Resolution {
        val node = base.candidate.node
        val directory = node.path.dropLast(base.candidate.format.length + 1)
        val companions = descendants(directory).filter { RomPlatforms.formatOf(it.name) == "chd" }.map { it.document.documentId }
        return base.copy(companions = companions)
    }

    private fun descendants(directory: String): List<Node> = nodes.values.filter {
        !it.document.isDirectory && !excluded(it.path) && (directory.isEmpty() || it.path.startsWith("$directory/"))
    }

    private fun contextPlatform(node: Node): String? {
        val ancestors = generateSequence(node.parent) { it.takeIf(String::isNotEmpty)?.substringBeforeLast('/', "") }.toList()
        ancestors.firstNotNullOfOrNull { overrides[it] }?.let { return it }
        request.assignedPlatformId?.let { return it }
        ancestors.firstNotNullOfOrNull { RomPlatforms.matchingFolder(it.substringAfterLast('/'))?.id }?.let { return it }
        return RomPlatforms.matchingFolder(request.rootDisplayName)?.id
    }

    private fun find(path: String): Match = nodes[path]?.let { Match(it, false) } ?: run {
        val matches = foldedPaths[path.lowercase(Locale.ROOT)].orEmpty()
        Match(matches.singleOrNull(), matches.size > 1)
    }

    private fun fail(base: Resolution, code: RomScanIssueCode, reason: String): Resolution {
        issue(base.candidate.node, code, reason)
        return base.copy(valid = false, reason = reason, companions = emptyList(), requiresRepair = true)
    }

    private fun issue(node: Node, code: RomScanIssueCode, message: String) = issue(node.document.documentId, node.path, code, message)
    private fun issue(id: String?, path: String, code: RomScanIssueCode, message: String) {
        issues += RomScanIssue(id, path, code, message)
    }
}

private data class Node(val document: RomDocument, val path: String) {
    val name: String get() = path.substringAfterLast('/')
    val parent: String get() = path.substringBeforeLast('/', "")
}

private data class Candidate(val node: Node, val format: String, val platformId: String?, val platforms: Set<String>, val reason: String?)
private data class Match(val node: Node?, val ambiguous: Boolean)
private data class Resolution(
    val candidate: Candidate,
    val title: String,
    val platformId: String?,
    val platforms: Set<String>,
    val kind: RomEntryKind,
    val reason: String? = null,
    val valid: Boolean = true,
    val companions: List<String> = emptyList(),
    val requiresRepair: Boolean = false,
)

private fun title(name: String, format: String): String = name.dropLast(format.length + 1).ifBlank { name }

private fun normalizePath(path: String, allowEmpty: Boolean = false): String? {
    if ('\u0000' in path || path.startsWith('/') || path.startsWith('\\') || Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(path)) return null
    val parts = mutableListOf<String>()
    for (part in path.replace('\\', '/').split('/')) {
        when (part) {
            "", "." -> Unit
            ".." -> if (parts.isEmpty()) return null else parts.removeAt(parts.lastIndex)
            else -> parts += part
        }
    }
    return parts.joinToString("/").takeIf { it.isNotEmpty() || allowEmpty }
}

private fun resolveReference(parent: String, reference: String): String? {
    if (reference.startsWith('/') || reference.startsWith('\\') || Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(reference)) return null
    return normalizePath(if (parent.isEmpty()) reference else "$parent/$reference")
}

private val excludedDirectories = setOf(
    "bios", "firmware", "saves", "save", "savestates", "states", "screenshots", "snaps", "covers", "artwork",
    "manuals", "docs", "cheats", "textures", "cache", "caches", "thumbnails", "__macosx", "system", "nvram",
)
// These suffixes also describe unrelated software or platforms outside the current registry.
// A single registry candidate therefore does not make extension-only inference deterministic.
private val contextRequiredFormats = setOf("gz", "pkg", "conf", "cmd", "bat", "wad", "o", "abs", "cof", "prx")
private val knownFirmware = setOf(
    "disksys.rom", "gamegenie.nes", "gba_bios.bin", "gb_bios.bin", "gbc_bios.bin", "bios7.bin", "bios9.bin",
    "firmware.bin", "dc_boot.bin", "dc_flash.bin", "neogeo.zip", "neogeo.7z", "awbios.zip", "naomi.zip",
    "naomi2.zip", "airlbios.zip", "hod2bios.zip", "f355bios.zip", "f355dlx.zip", "psxonpsp660.bin", "ps1_rom.bin",
    "syscard3.pce", "syscard2.pce", "syscard1.pce", "supergrafx.bin", "2600.rom", "5200.rom", "7800.rom",
    "ataribas.rom", "atariosa.rom", "atariosb.rom", "atarixl.rom", "bb01r4_os.rom", "coleco.rom",
    "exec.bin", "grom.bin", "lynxboot.img", "stbios.bin", "bs-x.bin",
)
private val firmwarePattern = Regex("(?i)^(?:scph[0-9a-z_-]*|bios(?:_[a-z0-9_-]+)?|kick[0-9]+|amiga-os-[a-z0-9_-]+)\\.(?:bin|rom|sms|gg|a500|a600|a1200|a4000|cd32|cdtv)$")

private fun excluded(path: String): Boolean {
    val parts = path.lowercase(Locale.ROOT).split('/')
    if (parts.any { it.startsWith('.') } || parts.dropLast(1).any { it in excludedDirectories }) return true
    return parts.last() in knownFirmware || firmwarePattern.matches(parts.last())
}
