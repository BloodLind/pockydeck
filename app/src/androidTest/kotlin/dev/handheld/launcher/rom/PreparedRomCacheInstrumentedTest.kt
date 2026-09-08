package dev.handheld.launcher.rom

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.data.rom.archive.PreparedArchive
import dev.handheld.launcher.core.data.rom.archive.PreparedRomCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Random
import java.util.UUID
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real provider input and real cache operations, isolated from every other prepared copy. */
@RunWith(AndroidJUnit4::class)
class PreparedRomCacheInstrumentedTest {
    private lateinit var context: Context
    private lateinit var inputRoot: File
    private lateinit var isolatedNoBackup: File
    private lateinit var outputRoot: File
    private lateinit var cacheContext: Context
    private lateinit var cache: PreparedRomCache
    private val inputFolders = mutableListOf<File>()

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        inputRoot = File(context.noBackupFilesDir, "rom-prepared").canonicalFile
        inputRoot.mkdirs()
        isolatedNoBackup = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "rom-cache-test-").toFile().canonicalFile
        outputRoot = File(isolatedNoBackup, "rom-prepared").canonicalFile
        cacheContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = isolatedNoBackup
        }
        cache = PreparedRomCache(cacheContext)
    }

    @After fun tearDown() {
        inputFolders.forEach { folder ->
            require(folder.canonicalFile.parentFile == inputRoot)
            assertTrue("Could not remove source fixture", !folder.exists() || folder.deleteRecursively())
        }
        if (::isolatedNoBackup.isInitialized) {
            require(isolatedNoBackup.canonicalFile.parentFile == context.noBackupFilesDir.canonicalFile)
            assertTrue("Could not remove isolated test cache", !isolatedNoBackup.exists() || isolatedNoBackup.deleteRecursively())
        }
    }

    @Test fun cacheHitFitsBudgetThatCannotAllocateAnotherCompressedCopy() = runBlocking {
        val payload = fixtureBytes()
        val archive = zip("Game.nes", payload)
        val source = inputFixture("Game.zip", archive)
        val prepared = prepare(source, "zip", "Game.zip")
        assertArrayEquals(payload, gameFile(prepared, "Game.nes").readBytes())
        val usageBefore = cache.usageBytes()
        // Hashing the source is possible, but storing source + existing result is not.
        val tightLimit = maxOf(usageBefore, archive.size.toLong())
        assertTrue(tightLimit < usageBefore + archive.size)
        val progress = mutableListOf<String>()

        val hit = cache.prepare(source.uri.toString(), "zip", tightLimit, { progress += it }, "Game.zip")

        assertEquals(prepared.key, hit.key)
        assertEquals(usageBefore, cache.usageBytes())
        assertArrayEquals(payload, gameFile(hit, "Game.nes").readBytes())
        assertFalse(progress.any { it.startsWith("Extracting") })
        assertEquals(setOf(prepared.key), outputRoot.listFiles().orEmpty().map { it.name }.toSet())
        assertArrayEquals(archive, context.contentResolver.openInputStream(source.uri)!!.use { it.readBytes() })
    }

    @Test fun identicalGzipBytesRespectChangedSourceNameAfterRestart() = runBlocking {
        val payload = fixtureBytes()
        val source = inputFixture("same.gz", gzip(payload))
        val original = prepare(source, "gz", "Game.gz")
        assertTrue(cache.reserveForLaunch(original.key))
        assertArrayEquals(payload, gameFile(original, "Game").readBytes())

        // A fresh cache object simulates losing all in-memory state before the corrected filename.
        val reopened = PreparedRomCache(cacheContext)
        val renamed = reopened.prepare(source.uri.toString(), "gz", CACHE_BUDGET, {}, "Game.nes.gz")

        assertNotEquals(original.key, renamed.key)
        assertArrayEquals(payload, gameFile(renamed, "Game.nes").readBytes())
        assertTrue(gameFile(original, "Game").isFile)
        assertEquals("Game.nes", renamed.documents.single { !it.isDirectory }.relativePath)
        assertFalse(outputRoot.listFiles().orEmpty().any { it.name.startsWith('.') })
    }

    @Test fun failedAttemptReleasesOnlyReservationCreatedByThatAttempt() = runBlocking {
        val source = inputFixture("Game.zip", zip("Game.nes", fixtureBytes()))
        val prepared = prepare(source, "zip", "Game.zip")
        val marker = File(File(outputRoot, prepared.key), ".reserved")

        val newReservation = cache.reserveForLaunch(prepared.key)
        assertTrue(newReservation)
        assertTrue(marker.isFile)
        cache.releaseFailedReservation(prepared.key, newReservation)
        assertFalse(marker.exists())

        assertTrue(cache.reserveForLaunch(prepared.key))
        val reopened = PreparedRomCache(cacheContext)
        val existingReservation = reopened.reserveForLaunch(prepared.key)
        assertFalse(existingReservation)
        reopened.releaseFailedReservation(prepared.key, existingReservation)
        assertTrue("An earlier emulator launch still owns this reservation", marker.isFile)
    }

    @Test fun clearUnusedRemovesOnlyUnreservedOutputAndKeepsOriginalArchives() = runBlocking {
        val firstSource = inputFixture("First.zip", zip("First.nes", fixtureBytes()))
        val first = prepare(firstSource, "zip", "First.zip")
        assertTrue(cache.reserveForLaunch(first.key))
        val secondSource = inputFixture("Second.zip", zip("Second.nes", fixtureBytes()))
        val second = prepare(secondSource, "zip", "Second.zip")
        val before = cache.usageBytes()

        val reclaimed = cache.clear(includeReserved = false)

        assertTrue(reclaimed > 0)
        assertEquals(before - reclaimed, cache.usageBytes())
        assertTrue(gameFile(first, "First.nes").isFile)
        assertTrue(File(File(outputRoot, first.key), ".reserved").isFile)
        assertFalse(File(outputRoot, second.key).exists())
        assertTrue(firstSource.file.isFile)
        assertTrue(secondSource.file.isFile)
        assertEquals(setOf(first.key), outputRoot.listFiles().orEmpty().map { it.name }.toSet())
    }

    private suspend fun prepare(source: Source, format: String, sourceName: String): PreparedArchive =
        cache.prepare(source.uri.toString(), format, CACHE_BUDGET, {}, sourceName)

    private fun gameFile(prepared: PreparedArchive, relative: String): File {
        require(prepared.key.matches(Regex("[a-f0-9]{64}")))
        val published = File(outputRoot, prepared.key).canonicalFile
        require(published.parentFile == outputRoot)
        val result = File(File(published, "content"), relative).canonicalFile
        require(result.toPath().startsWith(File(published, "content").canonicalFile.toPath()))
        return result
    }

    private fun inputFixture(name: String, bytes: ByteArray): Source {
        val key = UUID.randomUUID().toString().replace("-", "").repeat(2)
        val folder = File(inputRoot, key).canonicalFile
        require(folder.parentFile == inputRoot)
        inputFolders += folder
        val content = File(folder, "content").apply { mkdirs() }
        val file = File(content, name).apply { writeBytes(bytes) }
        File(folder, ".complete").writeText("1")
        File(folder, ".reserved").writeText("1")
        val treeId = "cache:$key"
        val tree = DocumentsContract.buildTreeDocumentUri("${context.packageName}.romcache", treeId)
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, "$treeId/$name")
        return Source(uri, file)
    }

    private fun fixtureBytes(): ByteArray = ByteArray(64 * 1024).also {
        Random(UUID.randomUUID().mostSignificantBits).nextBytes(it)
    }

    private fun zip(name: String, bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
        output.toByteArray()
    }

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
        output.toByteArray()
    }

    private data class Source(val uri: Uri, val file: File)

    companion object { private const val CACHE_BUDGET = 4L * 1024 * 1024 }
}
