package dev.handheld.launcher.ui.artwork

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.CoroutineContext

/** Two small streaming reads replace a full encoded-image byte array for every card. */
internal class ArtworkBitmapDecoder(private val resolver: ContentResolver) {
    suspend fun decode(reference: String, targetSizePx: Int): ImageBitmap? {
        val coroutine = currentCoroutineContext()
        coroutine.ensureActive()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsInput = open(reference, coroutine) ?: return null
        boundsInput.use { BitmapFactory.decodeStream(it, null, bounds) }
        coroutine.ensureActive()
        val sample = ArtworkDecodePolicy.sampleSize(bounds.outWidth, bounds.outHeight, targetSizePx) ?: return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inScaled = false
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        val bitmap = open(reference, coroutine)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        // decodeStream itself is native/non-suspending. A cancelled result must never enter
        // a painter/cache; it is safe to release this still-private, unpublished allocation.
        try { coroutine.ensureActive() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { bitmap.recycle(); throw cancelled }
        return bitmap.asImageBitmap()
    }

    private fun open(reference: String, coroutine: CoroutineContext): InputStream? {
        coroutine.ensureActive()
        val uri = Uri.parse(reference)
        val input = when (uri.scheme) {
            "content" -> resolver.openAssetFileDescriptor(uri, "r")?.let { descriptor ->
                if (descriptor.length > ArtworkDecodePolicy.MAX_ENCODED_BYTES) {
                    descriptor.close()
                    return null
                }
                try { descriptor.createInputStream() }
                catch (error: Exception) { descriptor.close(); throw error }
            }
            "file", null -> {
                val file = File(if (uri.scheme == "file") uri.path ?: return null else reference)
                if (file.length() > ArtworkDecodePolicy.MAX_ENCODED_BYTES) return null
                file.inputStream()
            }
            else -> null
        } ?: return null
        return ArtworkInputStream(input, coroutine)
    }
}

/** Reads remain bounded even when a DocumentsProvider does not report its stream length. */
internal class ArtworkInputStream(input: InputStream, private val coroutine: CoroutineContext) : FilterInputStream(input) {
    private var consumed = 0L

    override fun read(): Int {
        checkReadable()
        if (consumed == ArtworkDecodePolicy.MAX_ENCODED_BYTES) return endAtLimit()
        return `in`.read().also { if (it >= 0) consumed++ }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        checkReadable()
        if (consumed == ArtworkDecodePolicy.MAX_ENCODED_BYTES) return endAtLimit()
        val remaining = (ArtworkDecodePolicy.MAX_ENCODED_BYTES - consumed).coerceAtMost(length.toLong()).toInt()
        return `in`.read(buffer, offset, remaining).also { if (it > 0) consumed += it }
    }

    override fun skip(count: Long): Long {
        if (count <= 0) return 0
        checkReadable()
        return `in`.skip(minOf(count, ArtworkDecodePolicy.MAX_ENCODED_BYTES - consumed)).also { consumed += it }
    }

    override fun markSupported(): Boolean = false

    private fun checkReadable() {
        coroutine.ensureActive()
    }

    private fun endAtLimit(): Int = if (`in`.read() == -1) -1
        else throw IOException("Artwork stream exceeds the encoded size limit")
}
