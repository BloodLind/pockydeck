package dev.handheld.launcher.ui.artwork

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.IOException
import kotlin.coroutines.EmptyCoroutineContext

class ArtworkMemoryPolicyTest {
    @Test fun `LRU eviction honors bytes while the caller can retain an evicted displayed object`() {
        data class Image(val bytes: Int)
        val cache = ArtworkMemoryCache<String, Image>(12) { it.bytes }
        val first = Image(4)
        cache.put("first", first, cache.revision)
        cache.put("second", Image(5), cache.revision)
        assertSame(first, cache.get("first")) // first is the displayed, most recent entry.
        cache.put("third", Image(6), cache.revision)
        assertNull(cache.get("second"))
        assertSame(first, cache.get("first"))
        assertEquals(10, cache.sizeBytes)
        cache.trimTo(0)
        assertEquals(0, cache.sizeBytes)
        assertEquals("Eviction only drops ownership; displayed objects are not destroyed", 4, first.bytes)
    }

    @Test fun `trim rejects late pre-trim decodes but allows a fresh resumed request`() {
        val cache = ArtworkMemoryCache<String, Int>(100) { it }
        val decodingEpoch = cache.revision
        cache.put("visible", 60, decodingEpoch)
        cache.trimTo(0)
        assertFalse(cache.put("finished-after-background", 70, decodingEpoch))
        assertEquals(0, cache.sizeBytes)
        assertTrue(cache.put("resumed", 50, cache.revision))
        cache.trimTo(25)
        assertEquals(0, cache.sizeBytes)
        assertFalse(cache.put("oversized", 101, cache.revision))
        assertEquals(0, cache.sizeBytes)
    }

    @Test fun `decode samples fit the requested physical bucket including odd sized images`() {
        for (target in listOf(1, 64, 130, 192, 256, 384, 768, 4096)) {
            for ((width, height) in listOf(1025 to 513, 768 to 768, 4096 to 2048, 8192 to 1)) {
                val sample = requireNotNull(ArtworkDecodePolicy.sampleSize(width, height, target))
                val decodedEdge = (maxOf(width, height) + sample - 1) / sample
                assertTrue(decodedEdge <= ArtworkDecodePolicy.target(target))
                assertTrue(decodedEdge <= 768)
            }
        }
        assertEquals(192, ArtworkDecodePolicy.target(190))
        assertEquals(192, ArtworkDecodePolicy.target(191))
        assertTrue(requireNotNull(ArtworkDecodePolicy.sampleSize(1024, 1024, 128)) >
            requireNotNull(ArtworkDecodePolicy.sampleSize(1024, 1024, 768)))
        assertNull(ArtworkDecodePolicy.sampleSize(0, 256, 128))
        assertNull(ArtworkDecodePolicy.sampleSize(8193, 1, 128))
        assertNull(ArtworkDecodePolicy.sampleSize(8192, 8192, 128))
    }

    @Test fun `cancelled input rejects further reads and closes the underlying source`() {
        var closed = false
        val source = object : ByteArrayInputStream(ByteArray(64)) {
            override fun close() { closed = true; super.close() }
        }
        val job = Job()
        ArtworkInputStream(source, job).use { input ->
            assertEquals(8, input.read(ByteArray(8)))
            job.cancel()
            val failure = runCatching { input.read(ByteArray(8)) }.exceptionOrNull()
            assertTrue(failure is CancellationException)
        }
        assertTrue(closed)
    }

    @Test fun `unknown length stream cannot read beyond encoded limit but exact length reaches EOF`() {
        fun source(extra: Long) = object : InputStream() {
            var remaining = ArtworkDecodePolicy.MAX_ENCODED_BYTES + extra
            override fun read(): Int = if (remaining-- > 0) 0 else -1
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(remaining, length.toLong()).toInt()
                remaining -= count
                return count
            }
        }
        for (extra in listOf(0L, 1L)) ArtworkInputStream(source(extra), EmptyCoroutineContext).use { input ->
            val buffer = ByteArray(8192)
            repeat((ArtworkDecodePolicy.MAX_ENCODED_BYTES / buffer.size).toInt()) { assertEquals(buffer.size, input.read(buffer)) }
            if (extra == 0L) assertEquals(-1, input.read())
            else assertTrue(runCatching { input.read() }.exceptionOrNull() is IOException)
        }
    }
}
