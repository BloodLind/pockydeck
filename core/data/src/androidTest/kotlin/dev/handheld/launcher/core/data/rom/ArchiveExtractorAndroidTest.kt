package dev.handheld.launcher.core.data.rom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.data.rom.archive.ArchiveExtractor
import dev.handheld.launcher.core.data.rom.archive.ExtractionLimits
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.zip.GZIPOutputStream
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

/** Runs the real Commons Compress/XZ decoders and java.nio filesystem path on Android. */
@RunWith(AndroidJUnit4::class)
class ArchiveExtractorAndroidTest {
    private lateinit var context: Context
    private lateinit var root: File
    private val extractor = ArchiveExtractor()
    private val limits = ExtractionLimits(maxBytes = 4L * 1024 * 1024, minFreeBytes = 0)
    // Generated fixture data only; these bytes are not a commercial game or BIOS image.
    private val payload = ByteArray(96 * 1024) { index -> ((index * 31 + 7) % 251).toByte() }

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        root = File(context.cacheDir, "archive-runtime-${UUID.randomUUID()}")
        check(root.mkdir())
    }

    @After fun tearDown() {
        if (::root.isInitialized) {
            check(root.canonicalFile.parentFile == context.cacheDir.canonicalFile)
            check(root.name.startsWith("archive-runtime-"))
            check(root.deleteRecursively())
        }
    }

    @Test fun zipRoundTripPreservesLayoutBytesAndProgressOnAndroid() {
        val archive = zip("fixture.zip", "GBA/Fixture.gba", payload)
        val output = output("zip")
        val progress = mutableListOf<Long>()
        val result = extractor.extract(archive, "zip", output, limits, onProgress = { progress += it })

        assertEquals(payload.size.toLong(), result.bytesWritten)
        assertEquals("GBA/Fixture.gba", relative(output, result.files.single()))
        assertArrayEquals(payload, result.files.single().readBytes())
        assertEquals(result.bytesWritten, progress.last())
        assertTrue(progress.zipWithNext().all { (before, after) -> before <= after })
    }

    @Test fun sevenZLzma2RoundTripExercisesAndroidDecoderDependencies() {
        val archive = File(root, "fixture.7z")
        SevenZOutputFile(archive).use { writer ->
            val options = LZMA2Options().apply { dictSize = 1024 * 1024 }
            writer.setContentMethods(listOf(SevenZMethodConfiguration(SevenZMethod.LZMA2, options)))
            writer.putArchiveEntry(SevenZArchiveEntry().apply { name = "NES/Fixture.nes" })
            writer.write(payload)
            writer.closeArchiveEntry()
        }
        val output = output("7z")
        val result = extractor.extract(archive, "7z", output, limits)

        assertEquals("NES/Fixture.nes", relative(output, result.files.single()))
        assertEquals(payload.size.toLong(), result.bytesWritten)
        assertArrayEquals(payload, result.files.single().readBytes())
    }

    @Test fun gzipAndXzStreamsRoundTripThroughTheAndroidExtractor() {
        val gzip = File(root, "Fixture.gba.gz")
        GZIPOutputStream(gzip.outputStream()).use { it.write(payload) }
        val xz = File(root, "Fixture.gba.xz")
        XZOutputStream(xz.outputStream(), LZMA2Options(0)).use { it.write(payload) }

        for ((archive, format) in listOf(gzip to "gz", xz to "xz")) {
            val result = extractor.extract(archive, format, output(format), limits)
            assertEquals("Fixture.gba", result.files.single().name)
            assertEquals(payload.size.toLong(), result.bytesWritten)
            assertArrayEquals(payload, result.files.single().readBytes())
        }
    }

    @Test fun byteQuotaAndCancellationWorkWithAndroidFilesystemStreams() {
        val gzip = File(root, "quota.gba.gz")
        GZIPOutputStream(gzip.outputStream()).use { it.write(payload) }
        val quotaOutput = output("quota")
        assertIoFailure { extractor.extract(gzip, "gz", quotaOutput, limits.copy(maxBytes = 1024)) }
        assertTrue(File(quotaOutput, "quota.gba").length() <= 1024)

        val archive = zip("cancel.zip", "Fixture.gba", payload)
        val cancelOutput = output("cancel")
        var cancelled = false
        try {
            extractor.extract(archive, "zip", cancelOutput, limits,
                onProgress = { cancelled = true }, isCancelled = { cancelled })
            throw AssertionError("Expected extraction cancellation")
        } catch (_: CancellationException) {
            assertTrue(File(cancelOutput, "Fixture.gba").length() in 1 until payload.size.toLong())
            assertTrue(archive.isFile)
        }
    }

    @Test fun traversalEntryCannotWriteOutsideItsAndroidOutputDirectory() {
        val archive = zip("unsafe.zip", "../escaped.gba", payload)
        val output = output("unsafe")
        assertIoFailure { extractor.extract(archive, "zip", output, limits) }
        assertFalse(File(root, "escaped.gba").exists())
        assertTrue(output.listFiles().orEmpty().isEmpty())
    }

    private fun zip(name: String, entryName: String, content: ByteArray): File = File(root, name).also { archive ->
        ZipArchiveOutputStream(archive).use { writer ->
            writer.putArchiveEntry(ZipArchiveEntry(entryName))
            writer.write(content)
            writer.closeArchiveEntry()
        }
    }

    private fun output(name: String) = File(root, "output-$name").also { check(it.mkdir()) }

    private fun relative(directory: File, file: File): String {
        val rootPath = directory.toPath().toRealPath()
        val filePath = file.toPath().toRealPath()
        assertTrue(filePath.startsWith(rootPath))
        return rootPath.relativize(filePath).toString().replace(File.separatorChar, '/')
    }

    private fun assertIoFailure(action: () -> Unit) {
        try {
            action()
            throw AssertionError("Expected IOException")
        } catch (_: IOException) { }
    }
}
