package dev.handheld.launcher.core.data.rom.archive

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.zip.CRC32
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.zip.AsiExtraField
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream

data class ExtractionLimits(
    val maxBytes: Long,
    val maxEntries: Int = 20_000,
    val minFreeBytes: Long = 512L * 1024 * 1024,
    val maxMemoryKiB: Int = 65_536,
) {
    init {
        require(maxBytes >= 0 && maxEntries > 0 && minFreeBytes >= 0 && maxMemoryKiB > 0)
    }
}

data class ExtractionResult(val files: List<File>, val bytesWritten: Long)

/**
 * Extracts only when explicitly requested by the caller. Does not recurse into nested archives,
 * decode emulator-native image containers, publish files, or manage the cache lifecycle.
 * On failure, the caller must discard its own partial directory. Existing files are never replaced.
 */
class ArchiveExtractor {
    fun extract(
        archive: File,
        format: String,
        outputDirectory: File,
        limits: ExtractionLimits,
        onProgress: (Long) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): ExtractionResult {
        if (!archive.isFile) throw IOException("Archive is unavailable.")
        val normalizedFormat = format.lowercase(Locale.ROOT).removePrefix(".")
        if (normalizedFormat !in supportedFormats) throw IOException("Archive extraction does not support .$normalizedFormat.")
        val state = ExtractionState(outputDirectory, limits, onProgress, isCancelled)
        state.checkCancellation()
        when (normalizedFormat) {
            "zip", "dosz" -> zip(archive, state)
            "7z" -> sevenZ(archive, state)
            else -> archive.inputStream().buffered().use { input ->
                when (normalizedFormat) {
                    "tar" -> tar(input, state)
                    "tar.gz", "tgz" -> GzipCompressorInputStream(input, true).use { tar(it, state) }
                    "tar.xz", "txz" -> XZCompressorInputStream(input, true, limits.maxMemoryKiB).use { tar(it, state) }
                    "tar.bz2", "tbz2", "tbz" -> BZip2CompressorInputStream(input, true).use { tar(it, state) }
                    "gz", "gzip" -> GzipCompressorInputStream(input, true).use { state.file(singleName(archive, normalizedFormat), -1, null, it) }
                    "xz" -> XZCompressorInputStream(input, true, limits.maxMemoryKiB).use { state.file(singleName(archive, normalizedFormat), -1, null, it) }
                    "bz2", "bzip2" -> BZip2CompressorInputStream(input, true).use { state.file(singleName(archive, normalizedFormat), -1, null, it) }
                }
            }
        }
        state.checkCancellation()
        return ExtractionResult(state.files.toList(), state.bytesWritten)
    }

    private fun zip(archive: File, state: ExtractionState) {
        preflightZip(archive, state.limits, state::checkCancellation)
        ZipFile.builder().setFile(archive).get().use { zip ->
            val entries = zip.entries
            while (entries.hasMoreElements()) {
                state.checkCancellation()
                val entry = entries.nextElement()
                val type = entry.unixMode and 0xf000
                if (entry.isUnixSymlink || entry.extraFields.any { it is AsiExtraField && it.isLink } ||
                    type !in setOf(0, 0x8000, 0x4000) || (entry.externalAttributes and 0x400L) != 0L) {
                    throw IOException("Archive links and special files are not supported.")
                }
                if (entry.generalPurposeBit.usesEncryption()) throw IOException("Password-protected archives are not supported.")
                // Restricts ZIP decoder memory to the ordinary stored/deflate implementations.
                if (entry.method !in setOf(0, 8) || !zip.canReadEntryData(entry)) throw IOException("ZIP entry uses an unsupported compression method.")
                if (entry.isDirectory) state.directory(entry.name)
                else zip.getInputStream(entry).use { state.file(entry.name, entry.size, entry.crc.takeIf { it >= 0 }, it) }
            }
        }
    }

    private fun sevenZ(archive: File, state: ExtractionState) {
        SevenZFile.builder().setFile(archive).setMaxMemoryLimitKiB(state.limits.maxMemoryKiB)
            .setTryToRecoverBrokenArchives(false).get().use { seven ->
                while (true) {
                    state.checkCancellation()
                    val entry = seven.nextEntry ?: break
                    if (entry.isAntiItem) throw IOException("Archive deletion records are not supported.")
                    if (entry.hasWindowsAttributes) {
                        val attributes = entry.windowsAttributes
                        val unixType = (attributes ushr 16) and 0xf000
                        if ((attributes and 0x400) != 0 || unixType !in setOf(0, 0x8000, 0x4000)) {
                            throw IOException("Archive links and special files are not supported.")
                        }
                    }
                    val name = entry.name ?: throw IOException("Archive entry has no filename.")
                    if (entry.isDirectory) state.directory(name)
                    else state.file(name, entry.size, entry.crcValue.takeIf { entry.hasCrc }, object : InputStream() {
                        override fun read(): Int = seven.read()
                        override fun read(bytes: ByteArray, offset: Int, length: Int): Int = seven.read(bytes, offset, length)
                    })
                }
            }
    }

    /**
     * Bounded ordinary POSIX/USTAR reader. Rejects PAX/GNU metadata, sparse files and links before
     * a generic parser could allocate memory for attacker-controlled extended metadata lengths.
     */
    private fun tar(input: InputStream, state: ExtractionState) {
        val header = ByteArray(512)
        var records = 0L
        val maxRecords = state.limits.maxEntries.toLong() * 4 + state.limits.maxBytes / 512 + 4
        while (true) {
            state.checkCancellation()
            readFully(input, header)
            if (++records > maxRecords) throw IOException("TAR metadata exceeds extraction limits.")
            if (header.all { it == 0.toByte() }) {
                // Consume the remainder to validate a wrapping gzip/xz/bzip2 checksum, with bounds.
                val tail = ByteArray(512)
                var trailing = 0
                while (true) {
                    state.checkCancellation()
                    val count = input.read(tail)
                    if (count < 0) return
                    trailing += count
                    if (trailing > 65_536 || (0 until count).any { tail[it] != 0.toByte() }) {
                        throw IOException("Unexpected data follows the end of the TAR archive.")
                    }
                }
            }
            val type = header[156].toInt()
            if (type !in setOf(0, '0'.code, '5'.code)) throw IOException("TAR links, special files and extended metadata are not supported.")
            val entry = TarArchiveEntry(header)
            if (!entry.isCheckSumOK || entry.size < 0) throw IOException("TAR entry header is corrupt.")
            if (entry.isDirectory) {
                if (entry.size != 0L) throw IOException("TAR directory contains unexpected data.")
                state.directory(entry.name)
            } else state.file(entry.name, entry.size, null, FixedLengthInput(input, entry.size))
            val padding = ((512 - entry.size % 512) % 512).toInt()
            if (padding > 0) readFully(input, ByteArray(padding))
            records += (entry.size + 511) / 512
        }
    }

    private fun singleName(archive: File, format: String): String =
        archive.name.takeIf { it.lowercase(Locale.ROOT).endsWith(".$format") }?.dropLast(format.length + 1)
            ?.takeIf { it.isNotBlank() } ?: "extracted-game"

    companion object {
        val supportedFormats: Set<String> = setOf("zip", "dosz", "7z", "tar", "gz", "gzip", "xz", "bz2", "bzip2", "tar.gz", "tgz", "tar.xz", "txz", "tar.bz2", "tbz2", "tbz")
    }
}

private class ExtractionState(
    directory: File,
    val limits: ExtractionLimits,
    private val onProgress: (Long) -> Unit,
    private val isCancelled: () -> Boolean,
) {
    private val root: Path
    private val names = mutableMapOf<String, PathRecord>()
    val files = mutableListOf<File>()
    var bytesWritten: Long = 0
        private set

    init {
        val supplied = directory.toPath().toAbsolutePath().normalize()
        if (Files.isSymbolicLink(supplied)) throw IOException("Extraction directory must not be a symbolic link.")
        Files.createDirectories(supplied)
        root = supplied.toRealPath()
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw IOException("Extraction destination is not a directory.")
        checkSpace(0)
    }

    fun checkCancellation() {
        if (isCancelled() || Thread.currentThread().isInterrupted) throw CancellationException("Archive extraction cancelled.")
    }

    fun directory(name: String) {
        val path = register(name, isDirectory = true)
        ensureDirectory(path)
    }

    fun file(name: String, expectedBytes: Long, expectedCrc: Long?, input: InputStream) {
        checkCancellation()
        if (expectedBytes > limits.maxBytes - bytesWritten) throw IOException("Archive exceeds the extraction size limit.")
        checkSpace(expectedBytes.coerceAtLeast(0))
        val path = register(name, isDirectory = false)
        ensureDirectory(path.parent)
        val crc = CRC32()
        var countForFile = 0L
        Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { output ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                checkCancellation()
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (count.toLong() > limits.maxBytes - bytesWritten ||
                    (expectedBytes >= 0 && count.toLong() > expectedBytes - countForFile)) {
                    throw IOException("Archive expands beyond its declared size or extraction limit.")
                }
                checkSpace(count.toLong())
                output.write(buffer, 0, count)
                crc.update(buffer, 0, count)
                countForFile += count
                bytesWritten += count
                onProgress(bytesWritten)
            }
        }
        if (expectedBytes >= 0 && countForFile != expectedBytes) throw IOException("Archive entry is truncated.")
        if (expectedCrc != null && crc.value != expectedCrc) throw IOException("Archive entry checksum does not match.")
        files += path.toFile()
    }

    private fun checkSpace(additionalBytes: Long) {
        val available = root.toFile().usableSpace
        if (available < limits.minFreeBytes || additionalBytes > available - limits.minFreeBytes) {
            throw IOException("Not enough free space to extract this archive and keep the storage reserve.")
        }
    }

    private fun register(name: String, isDirectory: Boolean): Path {
        if (name.isBlank() || name.length > 4096 || '\\' in name || name.startsWith('/') || name.any { it.code < 32 }) {
            throw IOException("Archive contains an unsafe path.")
        }
        val parts = name.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.size > 64 || parts.any { part ->
                part == ".." || part.endsWith('.') || part.endsWith(' ') || part.any { it in ":<>\"|?*" } ||
                    reservedName.matches(part.substringBefore('.'))
            }) throw IOException("Archive contains an unsafe path.")
        var relative = ""
        for ((index, part) in parts.withIndex()) {
            relative = if (relative.isEmpty()) part else "$relative/$part"
            val last = index == parts.lastIndex
            val directory = !last || isDirectory
            val key = relative.lowercase(Locale.ROOT)
            val existing = names[key]
            if (existing != null) {
                if (existing.name != relative || existing.isDirectory != directory || (last && existing.explicit)) {
                    throw IOException("Archive contains duplicate or conflicting paths.")
                }
                if (last) names[key] = existing.copy(explicit = true)
            } else {
                if (names.size >= limits.maxEntries) throw IOException("Archive contains too many files or directories.")
                names[key] = PathRecord(relative, directory, explicit = last)
            }
        }
        return root.resolve(relative).normalize().also {
            if (it == root || !it.startsWith(root)) throw IOException("Archive path escapes the extraction directory.")
        }
    }

    private fun ensureDirectory(path: Path) {
        if (path == root) return
        if (!path.startsWith(root)) throw IOException("Archive path escapes the extraction directory.")
        ensureDirectory(path.parent)
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
                throw IOException("Extraction directory contains a link or conflicting file.")
            }
        } else Files.createDirectory(path)
    }

    private data class PathRecord(val name: String, val isDirectory: Boolean, val explicit: Boolean)
    companion object {
        private val reservedName = Regex("(?i)(?:con|prn|aux|nul|com[1-9]|lpt[1-9])")
    }
}

private class FixedLengthInput(private val source: InputStream, private var remaining: Long) : InputStream() {
    override fun read(): Int {
        val byte = ByteArray(1)
        return if (read(byte, 0, 1) < 0) -1 else byte[0].toInt() and 0xff
    }
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (remaining == 0L) return -1
        val count = source.read(bytes, offset, minOf(length.toLong(), remaining).toInt())
        if (count < 0) throw IOException("TAR entry is truncated.")
        remaining -= count
        return count
    }
}

private fun readFully(input: InputStream, buffer: ByteArray) {
    var offset = 0
    while (offset < buffer.size) {
        val count = input.read(buffer, offset, buffer.size - offset)
        if (count < 0) throw IOException("Archive is truncated.")
        offset += count
    }
}

/** Bounds ZIP metadata before a central-directory parser can allocate its entry objects. */
private fun preflightZip(archive: File, limits: ExtractionLimits, checkCancellation: () -> Unit) {
    RandomAccessFile(archive, "r").use { input ->
        val length = input.length()
        if (length < 22) throw IOException("ZIP archive is truncated.")
        val tailLength = minOf(length, 65_557L).toInt()
        val tail = ByteArray(tailLength)
        input.seek(length - tailLength)
        input.readFully(tail)
        val eocd = (tail.size - 22 downTo 0).firstOrNull { index ->
            le32(tail, index) == 0x06054b50L && index + 22 + le16(tail, index + 20) == tail.size
        } ?: throw IOException("ZIP end-of-directory record is missing.")
        val endPosition = length - tailLength + eocd
        if (le16(tail, eocd + 4) != 0 || le16(tail, eocd + 6) != 0) throw IOException("Split ZIP archives are not supported.")
        var count = le16(tail, eocd + 10).toLong()
        var centralSize = le32(tail, eocd + 12)
        var centralOffset = le32(tail, eocd + 16)
        if (count == 0xffffL || centralSize == 0xffffffffL || centralOffset == 0xffffffffL) {
            if (endPosition < 20) throw IOException("ZIP64 locator is missing.")
            input.seek(endPosition - 20)
            val locator = ByteArray(20).also { input.readFully(it) }
            if (le32(locator, 0) != 0x07064b50L || le32(locator, 4) != 0L || le32(locator, 16) != 1L) {
                throw IOException("ZIP64 locator is invalid or spans multiple volumes.")
            }
            val position = le64(locator, 8)
            if (position < 0 || position > endPosition - 76) throw IOException("ZIP64 directory offset is invalid.")
            input.seek(position)
            val end64 = ByteArray(56).also { input.readFully(it) }
            if (le32(end64, 0) != 0x06064b50L || le64(end64, 4) !in 44L..65_536L ||
                le32(end64, 16) != 0L || le32(end64, 20) != 0L || le64(end64, 24) != le64(end64, 32)) {
                throw IOException("ZIP64 directory is invalid or spans multiple volumes.")
            }
            count = le64(end64, 32)
            centralSize = le64(end64, 40)
            centralOffset = le64(end64, 48)
        } else if (le16(tail, eocd + 8).toLong() != count) throw IOException("Split ZIP archives are not supported.")
        if (count < 0 || count > limits.maxEntries) throw IOException("ZIP archive contains too many entries.")
        if (centralSize < 0 || centralSize > limits.maxMemoryKiB.toLong() * 1024 / 4 ||
            centralOffset < 0 || centralOffset > endPosition || centralSize > endPosition - centralOffset) {
            throw IOException("ZIP directory exceeds metadata limits or lies outside the archive.")
        }
        var position = centralOffset
        var actualEntries = 0
        val header = ByteArray(46)
        val directoryEnd = centralOffset + centralSize
        while (position < directoryEnd) {
            checkCancellation()
            if (++actualEntries > limits.maxEntries || directoryEnd - position < header.size) {
                throw IOException("ZIP directory exceeds its entry limit or is truncated.")
            }
            input.seek(position)
            input.readFully(header)
            if (le32(header, 0) != 0x02014b50L || le16(header, 34) != 0) throw IOException("ZIP central-directory entry is invalid or spans multiple volumes.")
            position += 46L + le16(header, 28) + le16(header, 30) + le16(header, 32)
            if (position > directoryEnd) throw IOException("ZIP central-directory entry is truncated.")
        }
        if (actualEntries.toLong() != count) throw IOException("ZIP directory entry count does not match its contents.")
    }
}

private fun le16(bytes: ByteArray, offset: Int): Int = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
private fun le32(bytes: ByteArray, offset: Int): Long = (0..3).fold(0L) { value, i -> value or ((bytes[offset + i].toLong() and 0xff) shl (i * 8)) }
private fun le64(bytes: ByteArray, offset: Int): Long = (0..7).fold(0L) { value, i -> value or ((bytes[offset + i].toLong() and 0xff) shl (i * 8)) }
