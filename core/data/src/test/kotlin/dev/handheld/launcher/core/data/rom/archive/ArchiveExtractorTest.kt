package dev.handheld.launcher.core.data.rom.archive

import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.zip.CRC32
import java.util.zip.GZIPOutputStream
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveExtractorTest {
    @get:Rule val temporary = TemporaryFolder()
    private val extractor = ArchiveExtractor()
    private val limits = ExtractionLimits(maxBytes = 16 * 1024 * 1024, minFreeBytes = 0)

    @Test fun zipPreservesRelativeLayoutAndReportsActualBytes() {
        val archive = zip(listOf("PSX/Game.cue" to bytes("FILE Track.bin BINARY"), "PSX/Track.bin" to ByteArray(70_000) { 9 }))
        val progress = mutableListOf<Long>()
        val output = temporary.newFolder()
        val result = extractor.extract(archive, "zip", output, limits, progress::add)
        assertEquals(listOf("PSX/Game.cue", "PSX/Track.bin"), result.files.map { relativeOutputPath(output, it) })
        assertEquals(70_021L, result.bytesWritten)
        assertEquals(result.bytesWritten, progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> b >= a })
        assertArrayEquals(ByteArray(70_000) { 9 }, File(output, "PSX/Track.bin").readBytes())
    }

    @Test fun sevenZExtractsFilesAndEmptyDirectoriesWithoutFlatteningNames() {
        val archive = temporary.newFile("games.7z")
        SevenZOutputFile(archive).use { output ->
            output.setContentCompression(SevenZMethod.COPY)
            val directory = SevenZArchiveEntry().apply { name = "GBA"; isDirectory = true }
            output.putArchiveEntry(directory)
            output.closeArchiveEntry()
            val entry = SevenZArchiveEntry().apply { name = "GBA/Game.gba" }
            output.putArchiveEntry(entry)
            output.write(bytes("game content"))
            output.closeArchiveEntry()
        }
        val result = extractor.extract(archive, "7z", temporary.newFolder(), limits)
        assertEquals("Game.gba", result.files.single().name)
        assertEquals("game content", result.files.single().readText())
        assertEquals(12L, result.bytesWritten)
    }

    @Test fun singleStreamCompressionUsesSourceBasenameAndChecksBounds() {
        val payload = ByteArray(80_000) { 1 }
        val compressors = listOf<Pair<String, (File) -> Unit>>(
            "gz" to { file -> GZIPOutputStream(file.outputStream()).use { it.write(payload) } },
            "xz" to { file -> XZCompressorOutputStream(file.outputStream()).use { it.write(payload) } },
            "bz2" to { file -> BZip2CompressorOutputStream(file.outputStream()).use { it.write(payload) } },
        )
        compressors.forEach { (format, write) ->
            val archive = temporary.newFile("Game.nes.$format")
            write(archive)
            val result = extractor.extract(archive, format, temporary.newFolder(), limits)
            assertEquals("Game.nes", result.files.single().name)
            assertArrayEquals(payload, result.files.single().readBytes())
            assertIoFailure { extractor.extract(archive, format, temporary.newFolder(), limits.copy(maxBytes = 100)) }
        }
    }

    @Test fun tarAndGzippedTarKeepOrdinaryUstarPaths() {
        val archive = tar("games.tar", listOf("folder/Game.nes" to bytes("NES game")))
        val compressed = temporary.newFile("games.tar.gz")
        GZIPOutputStream(compressed.outputStream()).use { it.write(archive.readBytes()) }
        for ((file, format) in listOf(archive to "tar", compressed to "tar.gz")) {
            val output = temporary.newFolder()
            val result = extractor.extract(file, format, output, limits)
            assertEquals("folder/Game.nes", relativeOutputPath(output, result.files.single()))
            assertEquals(8L, result.bytesWritten)
        }
    }

    @Test fun traversalAbsoluteDriveBackslashAndAlternateStreamPathsAreRejected() {
        for (name in listOf("../escape.nes", "folder/../../escape.nes", "/escape.nes", "C:/escape.nes", "folder\\..\\escape.nes", "Game.nes:stream", "folder/../Game.nes")) {
            val archive = zip(listOf(name to bytes("payload")))
            val output = temporary.newFolder()
            assertIoFailure(name) { extractor.extract(archive, "zip", output, limits) }
            assertFalse(File(temporary.root, "escape.nes").exists())
        }
    }

    @Test fun conflictingArchivePathsAndCaseCollisionsCannotOverwriteFiles() {
        for (entries in listOf(
            listOf("Game.nes" to bytes("one"), "Game.nes" to bytes("two")),
            listOf("Game.nes" to bytes("one"), "GAME.NES" to bytes("two")),
            listOf("folder" to bytes("one"), "folder/Game.nes" to bytes("two")),
            listOf("Folder/a.nes" to bytes("one"), "folder/b.nes" to bytes("two")),
        )) {
            val output = temporary.newFolder()
            assertIoFailure { extractor.extract(zip(entries), "zip", output, limits) }
            val first = File(output, entries.first().first)
            if (first.exists() && first.isFile) assertEquals("one", first.readText())
        }
    }

    @Test fun existingOutputFilesAndUnrelatedFilesSurviveFailure() {
        val output = temporary.newFolder()
        File(output, "Game.nes").writeText("original")
        File(output, "unrelated.txt").writeText("keep")
        assertIoFailure { extractor.extract(zip(listOf("Game.nes" to bytes("replacement"))), "zip", output, limits) }
        assertEquals("original", File(output, "Game.nes").readText())
        assertEquals("keep", File(output, "unrelated.txt").readText())
    }

    @Test fun zipSymlinkAndTarSymlinkOrHardlinkEntriesAreRejected() {
        val archive = temporary.newFile("symlink.zip")
        ZipArchiveOutputStream(archive).use { output ->
            val entry = ZipArchiveEntry("link").apply { unixMode = 0xa1ff }
            output.putArchiveEntry(entry)
            output.write(bytes("../outside"))
            output.closeArchiveEntry()
        }
        assertIoFailure { extractor.extract(archive, "zip", temporary.newFolder(), limits) }
        for (type in listOf(TarConstants.LF_SYMLINK, TarConstants.LF_LINK)) {
            val linkTar = temporary.newFile()
            TarArchiveOutputStream(linkTar.outputStream()).use { output ->
                output.putArchiveEntry(TarArchiveEntry("link", type).apply { linkName = "../outside" })
                output.closeArchiveEntry()
            }
            assertIoFailure { extractor.extract(linkTar, "tar", temporary.newFolder(), limits) }
        }
    }

    @Test fun sevenZDeletionRecordsAndReparsePointsAreRejected() {
        for (attributes in listOf(0, 0x400)) {
            val archive = temporary.newFile()
            SevenZOutputFile(archive).use { output ->
                val entry = SevenZArchiveEntry().apply {
                    name = "link"
                    if (attributes == 0) isAntiItem = true else {
                        hasWindowsAttributes = true
                        windowsAttributes = attributes
                    }
                }
                output.putArchiveEntry(entry)
                output.closeArchiveEntry()
            }
            assertIoFailure { extractor.extract(archive, "7z", temporary.newFolder(), limits) }
        }
    }

    @Test fun entryCountAndImplicitDirectoryCountsAreBounded() {
        val archive = zip(listOf("one.nes" to bytes("1"), "two.nes" to bytes("2")))
        assertIoFailure { extractor.extract(archive, "zip", temporary.newFolder(), limits.copy(maxEntries = 1)) }
        val nested = zip(listOf("a/b/c/game.nes" to bytes("game")))
        assertIoFailure { extractor.extract(nested, "zip", temporary.newFolder(), limits.copy(maxEntries = 3)) }
    }

    @Test fun freeSpaceReserveIsEnforcedBeforeWritingAnEntry() {
        val output = temporary.newFolder()
        assertIoFailure {
            extractor.extract(zip(listOf("Game.nes" to bytes("game"))), "zip", output, limits.copy(minFreeBytes = Long.MAX_VALUE))
        }
        assertTrue(output.listFiles().orEmpty().isEmpty())
    }

    @Test fun cancellationStopsDuringPayloadCopyAndLeavesOnlyCallerOwnedPartialFiles() {
        val archive = zip(listOf("Game.nes" to ByteArray(200_000) { 1 }))
        val output = temporary.newFolder()
        var cancelled = false
        try {
            extractor.extract(archive, "zip", output, limits, onProgress = { cancelled = true }, isCancelled = { cancelled })
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            assertTrue(File(output, "Game.nes").length() in 1 until 200_000)
            assertTrue(archive.exists())
        }
    }

    @Test fun corruptedZipPayloadFailsExplicitCrcValidation() {
        val archive = zip(listOf("Game.nes" to bytes("game content")), stored = true)
        val content = archive.readBytes()
        val nameSize = le16(content, 26)
        val extraSize = le16(content, 28)
        content[30 + nameSize + extraSize] = (content[30 + nameSize + extraSize].toInt() xor 1).toByte()
        archive.writeBytes(content)
        assertIoFailure { extractor.extract(archive, "zip", temporary.newFolder(), limits) }
    }

    @Test fun encryptedZipFlagIsRejectedWithoutAttemptingDecompression() {
        val archive = zip(listOf("Game.nes" to bytes("content")), stored = true)
        val content = archive.readBytes()
        content[6] = (content[6].toInt() or 1).toByte()
        val central = content.indices.first { index -> index + 4 <= content.size && content[index] == 0x50.toByte() &&
            content[index + 1] == 0x4b.toByte() && content[index + 2] == 1.toByte() && content[index + 3] == 2.toByte() }
        content[central + 8] = (content[central + 8].toInt() or 1).toByte()
        archive.writeBytes(content)
        val failure = assertIoFailure { extractor.extract(archive, "zip", temporary.newFolder(), limits) }
        assertTrue(failure.message.orEmpty().contains("Password"))
    }

    @Test fun dishonestZipDirectoryCountIsRejectedBeforeLibraryParsing() {
        val archive = zip(listOf("One.nes" to bytes("one"), "Two.nes" to bytes("two")))
        val content = archive.readBytes()
        val end = content.size - 22
        content[end + 8] = 1
        content[end + 10] = 1
        archive.writeBytes(content)
        assertIoFailure { extractor.extract(archive, "zip", temporary.newFolder(), limits) }
    }

    @Test fun sevenZAndXzDictionaryMemoryLimitsAreApplied() {
        val archive = temporary.newFile("dictionary.7z")
        SevenZOutputFile(archive).use { output ->
            output.putArchiveEntry(SevenZArchiveEntry().apply { name = "Game.nes" })
            output.write(ByteArray(2048))
            output.closeArchiveEntry()
        }
        assertIoFailure { extractor.extract(archive, "7z", temporary.newFolder(), limits.copy(maxMemoryKiB = 128)) }
        val xz = temporary.newFile("Game.nes.xz")
        XZCompressorOutputStream(xz.outputStream()).use { it.write(ByteArray(2048)) }
        assertIoFailure { extractor.extract(xz, "xz", temporary.newFolder(), limits.copy(maxMemoryKiB = 128)) }
    }

    @Test fun nativeEmulatorContainersAreNeverAccidentallyExpanded() {
        val archive = temporary.newFile("Game.chd").apply { writeText("not an archive") }
        for (format in listOf("chd", "cso", "rvz", "wia", "nsp")) {
            assertIoFailure { extractor.extract(archive, format, temporary.newFolder(), limits) }
        }
    }

    @Test fun truncatedTarCannotBecomeSuccessfulPartialExtraction() {
        val archive = tar("truncated.tar", listOf("Game.nes" to ByteArray(900) { 1 }))
        archive.writeBytes(archive.readBytes().copyOf(700))
        assertIoFailure { extractor.extract(archive, "tar", temporary.newFolder(), limits) }
    }

    private fun zip(entries: List<Pair<String, ByteArray>>, stored: Boolean = false): File = temporary.newFile().also { archive ->
        ZipArchiveOutputStream(archive).use { output ->
            entries.forEach { (name, data) ->
                val entry = ZipArchiveEntry(name)
                if (stored) {
                    entry.method = 0
                    entry.size = data.size.toLong()
                    entry.crc = CRC32().apply { update(data) }.value
                }
                output.putArchiveEntry(entry)
                output.write(data)
                output.closeArchiveEntry()
            }
        }
    }

    private fun tar(name: String, entries: List<Pair<String, ByteArray>>): File = temporary.newFile(name).also { archive ->
        TarArchiveOutputStream(archive.outputStream()).use { output ->
            entries.forEach { (path, data) ->
                output.putArchiveEntry(TarArchiveEntry(path).apply { size = data.size.toLong() })
                output.write(data)
                output.closeArchiveEntry()
            }
        }
    }

    private fun assertIoFailure(message: String = "Expected IOException", action: () -> Unit): IOException = try {
        action()
        throw AssertionError(message)
    } catch (failure: IOException) { failure }

    private fun bytes(value: String) = value.toByteArray(Charsets.UTF_8)
    private fun relativeOutputPath(directory: File, file: File): String {
        // Windows TEMP may use an 8.3 alias; the extractor deliberately returns canonical paths.
        val root = directory.toPath().toRealPath()
        val actual = file.toPath().toRealPath()
        assertTrue("Extracted file must remain inside the requested output directory", actual.startsWith(root))
        return root.relativize(actual).toString().replace(File.separatorChar, '/')
    }
    private fun le16(value: ByteArray, offset: Int) = (value[offset].toInt() and 255) + ((value[offset + 1].toInt() and 255) shl 8)
}
