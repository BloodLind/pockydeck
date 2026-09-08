package dev.handheld.launcher.ui.artwork.enriched

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.data.metadata.ArtworkRecord
import dev.handheld.launcher.core.data.metadata.ArtworkRepository
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.ui.presentation.TileArtwork
import dev.handheld.launcher.ui.presentation.TileUiModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class EnrichedArtwork(val painter: BitmapPainter? = null, val pending: Boolean = false)
val LocalEnrichedArtworkLoader = staticCompositionLocalOf<EnrichedArtworkLoader?> { null }

class EnrichedArtworkLoader(context: Context, val repository: ArtworkRepository) {
    private val resolver = context.applicationContext.contentResolver
    private val decoding = Semaphore(2)
    private val cache = object : LruCache<String, ImageBitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }

    suspend fun load(reference: String, key: String): ImageBitmap? = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[key] }?.let { return@withContext it }
        decoding.withPermit {
            try {
                val uri = Uri.parse(reference)
                val input = when (uri.scheme) {
                    "content" -> resolver.openInputStream(uri)
                    "file" -> java.io.File(requireNotNull(uri.path)).inputStream()
                    null -> java.io.File(reference).inputStream()
                    else -> null
                } ?: return@withPermit null
                val bytes = input.use { it.readNBytes(16 * 1024 * 1024 + 1) }
                if (bytes.size > 16 * 1024 * 1024) return@withPermit null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth !in 1..8192 || bounds.outHeight !in 1..8192 || bounds.outWidth.toLong() * bounds.outHeight > 24_000_000) return@withPermit null
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 768) inSampleSize *= 2
                }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap() ?: return@withPermit null
                synchronized(cache) { cache.put(key, bitmap) }
                bitmap
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }
    }
}

@Composable
fun rememberEnrichedArtwork(model: TileUiModel): EnrichedArtwork {
    val loader = LocalEnrichedArtworkLoader.current ?: return EnrichedArtwork()
    val eligible = model.artwork is TileArtwork.Rom || model.artwork is TileArtwork.LocalReference
    var state by remember(model.itemId, model.artwork) { mutableStateOf(EnrichedArtwork(pending = eligible)) }
    LaunchedEffect(loader, model.itemId, model.artwork) {
        try {
        when (val art = model.artwork) {
            is TileArtwork.LocalReference -> {
                state = EnrichedArtwork(loader.load(art.reference.value, art.reference.value)?.let(::BitmapPainter))
            }
            is TileArtwork.Rom -> {
                loader.repository.request(model.itemId)
                combine(loader.repository.observe(model.itemId), loader.repository.paused) { record, paused -> record to paused }
                    .collectLatest { (record, paused) ->
                        val pending = !paused && (record == null || record.state in setOf(ArtworkRecord.QUEUED, ArtworkRecord.RETRY))
                        val file = record?.takeIf { it.state == ArtworkRecord.READY }?.let { withContext(Dispatchers.IO) { loader.repository.file(it) } }
                        val bitmap = file?.let { withContext(Dispatchers.IO) { loader.load(it.path, "${record.fileReference}:${it.lastModified()}") } }
                        state = EnrichedArtwork(bitmap?.let(::BitmapPainter), pending)
                        if (record?.state == ArtworkRecord.READY && bitmap == null) loader.repository.invalidFile(model.itemId, record.fileReference)
                    }
            }
            else -> state = EnrichedArtwork()
        }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { state = EnrichedArtwork() }
    }
    return state
}

/** Decorative loading stays within the existing artwork slot and never becomes a focus target. */
@Composable
fun ArtworkPendingHint(modifier: Modifier = Modifier) {
    val rotation = if (LauncherTheme.motion.reducedMotion) 0f else {
        val transition = rememberInfiniteTransition(label = "artwork")
        val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "artwork rotation")
        angle
    }
    val background = LauncherTheme.colors.surfaceControl
    val color = LauncherTheme.colors.textSecondary
    Box(modifier.size(16.dp).background(background.copy(alpha = .9f), CircleShape)
        .semantics { contentDescription = "Looking for artwork" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(9.dp)) {
            drawArc(color, rotation, 250f, false,
                style = Stroke(width = 1.25.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
