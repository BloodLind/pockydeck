package dev.handheld.launcher.ui.artwork.enriched

import android.content.Context
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.data.metadata.ArtworkRecord
import dev.handheld.launcher.core.data.metadata.ArtworkRepository
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.ui.presentation.TileArtwork
import dev.handheld.launcher.ui.presentation.TileUiModel
import dev.handheld.launcher.ui.artwork.ArtworkBitmapDecoder
import dev.handheld.launcher.ui.artwork.ArtworkDecodePolicy
import dev.handheld.launcher.ui.artwork.ArtworkMemoryCache
import dev.handheld.launcher.ui.artwork.ArtworkMemoryOwner
import dev.handheld.launcher.ui.artwork.ReleasableArtworkPainter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class EnrichedArtwork(val painter: Painter? = null, val pending: Boolean = false)
val LocalEnrichedArtworkLoader = staticCompositionLocalOf<EnrichedArtworkLoader?> { null }

class EnrichedArtworkLoader(context: Context, val repository: ArtworkRepository) {
    internal val memoryOwner = ArtworkMemoryOwner()
    internal val foreground get() = memoryOwner.foreground
    private val decoder = ArtworkBitmapDecoder(context.applicationContext.contentResolver)
    private val decoding = Semaphore(2)
    private val sameSource = Array(16) { Mutex() }
    private val cache = ArtworkMemoryCache<String, ImageBitmap>(16 * 1024 * 1024) { it.width * it.height * 4 }
    internal val cachedBytes get() = cache.sizeBytes
    // One setting query for all currently displayed cards; no active Room subscription in
    // the background after the last card observer leaves composition.
    internal val paused = repository.paused.stateIn(CoroutineScope(SupervisorJob() + Dispatchers.IO),
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 0), false)

    suspend fun load(reference: String, key: String, targetSizePx: Int = ArtworkDecodePolicy.DEFAULT_TARGET_PX): ImageBitmap? = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val target = ArtworkDecodePolicy.target(targetSizePx)
        val sizedKey = "$key@$target"
        cache.get(sizedKey)?.let { return@withContext it }
        val revision = cache.revision
        sameSource[(sizedKey.hashCode() and Int.MAX_VALUE) % sameSource.size].withLock {
          cache.get(sizedKey)?.let { return@withLock it }
          decoding.withPermit {
            currentCoroutineContext().ensureActive()
            try {
                val bitmap = decoder.decode(reference, target) ?: return@withPermit null
                currentCoroutineContext().ensureActive()
                cache.put(sizedKey, bitmap, revision)
                bitmap
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
          }
        }
    }

    fun setForeground(value: Boolean) { memoryOwner.updateForeground(value); if (!value) trimMemory(clear = true) }
    fun trimMemory(clear: Boolean) = cache.trimTo(if (clear) 0 else cache.maximumBytes / 2)
}

@Composable
fun rememberEnrichedArtwork(model: TileUiModel, active: Boolean = true,
    targetSizePx: Int = ArtworkDecodePolicy.DEFAULT_TARGET_PX): EnrichedArtwork {
    val loader = LocalEnrichedArtworkLoader.current ?: return EnrichedArtwork()
    val artworkActive = active && loader.foreground
    val target = ArtworkDecodePolicy.target(targetSizePx)
    val eligible = model.artwork is TileArtwork.Rom || model.artwork is TileArtwork.LocalReference
    if (!eligible || !artworkActive) return EnrichedArtwork()
    val generation = loader.memoryOwner.generation
    val state = remember(loader, model.itemId, model.artwork, artworkActive, target, generation) {
        mutableStateOf(EnrichedArtwork(pending = eligible && artworkActive))
    }
    DisposableEffect(state) {
        val unregister = loader.memoryOwner.onBackground { state.value = EnrichedArtwork() }
        onDispose {
            unregister()
            (state.value.painter as? ReleasableArtworkPainter)?.release()
            state.value = EnrichedArtwork()
        }
    }
    LaunchedEffect(state) {
        if (!artworkActive) return@LaunchedEffect
        val requestJob = currentCoroutineContext().job
        val unregister = loader.memoryOwner.onBackground { state.value = EnrichedArtwork(); requestJob.cancel() }
        try {
        when (val art = model.artwork) {
            is TileArtwork.LocalReference -> {
                state.value = EnrichedArtwork(loader.load(art.reference.value, art.reference.value, target)?.let(loader.memoryOwner::painter))
            }
            is TileArtwork.Rom -> {
                loader.repository.request(model.itemId)
                // Priority/access-time writes do not change what the card displays.
                val visualRecord = loader.repository.observe(model.itemId).map { record ->
                    record?.copy(priority = 0, lastAccessAt = 0, attempts = 0, nextAttemptAt = 0, message = null)
                }.distinctUntilChanged()
                combine(visualRecord, loader.paused) { record, paused -> record to paused }
                    .collectLatest { (record, paused) ->
                        val pending = !paused && (record == null || record.state in setOf(ArtworkRecord.QUEUED, ArtworkRecord.RETRY))
                        val file = record?.takeIf { it.state == ArtworkRecord.READY }?.let { withContext(Dispatchers.IO) { loader.repository.file(it) } }
                        val bitmap = file?.let { withContext(Dispatchers.IO) { loader.load(it.path, "${record.fileReference}:${it.lastModified()}", target) } }
                        currentCoroutineContext().ensureActive()
                        state.value = EnrichedArtwork(bitmap?.let(loader.memoryOwner::painter), pending)
                        if (record?.state == ArtworkRecord.READY && bitmap == null) loader.repository.invalidFile(model.itemId, record.fileReference)
                    }
            }
            else -> state.value = EnrichedArtwork()
        }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { state.value = EnrichedArtwork() }
        finally { unregister() }
    }
    return state.value
}

/** Decorative loading stays within the existing artwork slot and never becomes a focus target. */
@Composable
fun ArtworkPendingHint(modifier: Modifier = Modifier, animate: Boolean = true) {
    val rotation = if (!animate || LauncherTheme.motion.reducedMotion) 0f else {
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
