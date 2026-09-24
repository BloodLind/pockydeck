package dev.handheld.launcher.ui.artwork.enriched

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import dev.handheld.launcher.core.data.metadata.ArtworkRecord
import dev.handheld.launcher.core.data.metadata.ArtworkRepository
import dev.handheld.launcher.ui.presentation.TileArtwork
import dev.handheld.launcher.ui.presentation.TileUiModel
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.ui.artwork.ArtworkBitmapDecoder
import dev.handheld.launcher.ui.artwork.ArtworkDecodePolicy
import dev.handheld.launcher.ui.artwork.ArtworkMemoryCache
import dev.handheld.launcher.ui.artwork.ArtworkMemoryOwner
import dev.handheld.launcher.ui.artwork.ReleasableArtworkPainter
import dev.handheld.launcher.ui.artwork.LocalArtworkLoadingAllowed
import dev.handheld.launcher.ui.artwork.blurredBackdrop
import dev.handheld.launcher.ui.artwork.BACKDROP_SIZE_PX
import kotlinx.coroutines.delay
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

data class EnrichedArtwork(val painter: Painter? = null, val pending: Boolean = false, val fromMemory: Boolean = false)
val LocalEnrichedArtworkLoader = staticCompositionLocalOf<EnrichedArtworkLoader?> { null }

class EnrichedArtworkLoader(context: Context, val repository: ArtworkRepository) {
    internal val memoryOwner = ArtworkMemoryOwner()
    internal val foreground get() = memoryOwner.foreground
    private val decoder = ArtworkBitmapDecoder(context.applicationContext.contentResolver)
    private val decoding = Semaphore(1)
    private val sameSource = Array(16) { Mutex() }
    private val backdropDecoding = Mutex()
    private val cache = ArtworkMemoryCache<String, ImageBitmap>(32 * 1024 * 1024) { it.width * it.height * 4 }
    // Keys only, never a second owner of bitmap memory. Allows a returning ROM card to
    // hit the image cache synchronously without waiting for a Room emission or scroll idle.
    private val romSources = object : LinkedHashMap<ItemId, String>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ItemId, String>?) = size > 256
    }
    internal val cachedBytes get() = cache.sizeBytes
    // One setting query for all currently displayed cards; no active Room subscription in
    // the background after the last card observer leaves composition.
    internal val paused = repository.paused.stateIn(CoroutineScope(SupervisorJob() + Dispatchers.IO),
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 0), false)

    internal fun cached(key: String, targetSizePx: Int, blurred: Boolean = false): ImageBitmap? =
        cache.get(cacheKey(key, targetSizePx, blurred))

    internal fun cached(model: TileUiModel, targetSizePx: Int, blurred: Boolean = false): ImageBitmap? =
        sourceKey(model)?.let { cached(it, targetSizePx, blurred) }

    private fun sourceKey(model: TileUiModel): String? = when (val art = model.artwork) {
        is TileArtwork.LocalReference -> art.reference.value
        is TileArtwork.Rom -> romSources[model.itemId]
        else -> null
    }

    private fun cachedCover(key: String): ImageBitmap? =
        sequenceOf(128, 192, 256, 384, 512, 768, 96, 64).mapNotNull { cached(key, it) }.firstOrNull()

    /** A loaded Home cover is enough: no repeat file read or Room request before showing its wash. */
    internal suspend fun backdropFromMemory(model: TileUiModel): ImageBitmap? {
        val key = sourceKey(model) ?: return null // Read the source index on the calling UI thread.
        cached(key, BACKDROP_SIZE_PX, blurred = true)?.let { return it }
        val cover = cachedCover(key) ?: return null
        return loadBackdrop(key) { cover }
    }

    private suspend fun loadBackdrop(key: String, source: suspend () -> ImageBitmap?): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val sizedKey = cacheKey(key, BACKDROP_SIZE_PX, blurred = true)
            cache.get(sizedKey)?.let { return@withContext it }
            val revision = cache.revision
            // Tiny blurred thumbnails never queue behind an entire strip of full-size covers.
            backdropDecoding.withLock {
                cache.get(sizedKey)?.let { return@withLock it }
                currentCoroutineContext().ensureActive()
                try {
                    val original = source() ?: return@withLock null
                    val bitmap = blurredBackdrop(original)
                    currentCoroutineContext().ensureActive()
                    cache.put(sizedKey, bitmap, revision)
                    bitmap
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            }
        }

    internal fun rememberSource(id: ItemId, source: String?) {
        if (source == null) romSources.remove(id) else romSources[id] = source
    }

    private fun cacheKey(key: String, targetSizePx: Int, blurred: Boolean) =
        "$key@${ArtworkDecodePolicy.target(targetSizePx)}:${if (blurred) "backdrop" else "cover"}"

    suspend fun load(reference: String, key: String, targetSizePx: Int = ArtworkDecodePolicy.DEFAULT_TARGET_PX,
        blurred: Boolean = false): ImageBitmap? = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        if (blurred) return@withContext loadBackdrop(key) {
            cachedCover(key) ?: decoder.decode(reference, BACKDROP_SIZE_PX)
        }
        val target = ArtworkDecodePolicy.target(targetSizePx)
        val sizedKey = cacheKey(key, target, blurred)
        cache.get(sizedKey)?.let { return@withContext it }
        val revision = cache.revision
        sameSource[(sizedKey.hashCode() and Int.MAX_VALUE) % sameSource.size].withLock {
          cache.get(sizedKey)?.let { return@withLock it }
          decoding.withPermit {
            // Let settled layout/focus draw before uploading another previously unseen cover.
            delay(16)
            currentCoroutineContext().ensureActive()
            try {
                val decoded = decoder.decode(reference, target) ?: return@withPermit null
                val bitmap = decoded
                currentCoroutineContext().ensureActive()
                cache.put(sizedKey, bitmap, revision)
                bitmap
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
          }
        }
    }

    fun setForeground(value: Boolean) { memoryOwner.updateForeground(value) }
    fun trimMemory(clear: Boolean) = cache.trimTo(if (clear) 0 else cache.maximumBytes / 2)
}

@Composable
fun rememberEnrichedArtwork(model: TileUiModel, active: Boolean = true,
    targetSizePx: Int = ArtworkDecodePolicy.DEFAULT_TARGET_PX, blurred: Boolean = false): EnrichedArtwork {
    val loader = LocalEnrichedArtworkLoader.current ?: return EnrichedArtwork()
    val artworkActive = active && loader.foreground
    val loadAllowed = LocalArtworkLoadingAllowed.current
    val target = ArtworkDecodePolicy.target(targetSizePx)
    val eligible = model.artwork is TileArtwork.Rom || model.artwork is TileArtwork.LocalReference
    if (!eligible || !artworkActive) return EnrichedArtwork()
    val generation = loader.memoryOwner.generation
    val state = remember(loader, model.itemId, model.artwork, artworkActive, target, generation, blurred) {
        val cached = loader.cached(model, target, blurred)
        mutableStateOf(EnrichedArtwork(cached?.let(loader.memoryOwner::painter),
            pending = cached == null, fromMemory = cached != null))
    }
    val loadedSource = remember(state) { arrayOfNulls<String>(1) }
    DisposableEffect(state) {
        val unregister = loader.memoryOwner.onBackground { state.value = EnrichedArtwork() }
        onDispose {
            unregister()
            (state.value.painter as? ReleasableArtworkPainter)?.release()
            state.value = EnrichedArtwork()
        }
    }
    LaunchedEffect(state, loadAllowed) {
        // Cancel decoding/Room observations while moving, but keep the displayed painter.
        if (!artworkActive || !loadAllowed) return@LaunchedEffect
        val requestJob = currentCoroutineContext().job
        val unregister = loader.memoryOwner.onBackground { state.value = EnrichedArtwork(); requestJob.cancel() }
        try {
        if (blurred && state.value.painter == null) {
            loader.backdropFromMemory(model)?.let { bitmap ->
                state.value = EnrichedArtwork(loader.memoryOwner.painter(bitmap), fromMemory = true)
            }
        }
        when (val art = model.artwork) {
            is TileArtwork.LocalReference -> {
                if (state.value.painter != null) return@LaunchedEffect
                val cached = loader.cached(art.reference.value, target, blurred)
                val bitmap = cached ?: loader.load(art.reference.value, art.reference.value, target, blurred)
                state.value = EnrichedArtwork(bitmap?.let(loader.memoryOwner::painter), fromMemory = cached != null)
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
                        val source = record?.takeIf { it.state == ArtworkRecord.READY }?.let {
                            withContext(Dispatchers.IO) {
                                loader.repository.file(it)?.let { file -> file.path to "${record.fileReference}:${file.lastModified()}" }
                            }
                        }
                        val key = source?.second
                        loader.rememberSource(model.itemId, key)
                        if (key != null && loadedSource[0] == key && state.value.painter != null) return@collectLatest
                        val cached = key?.let { loader.cached(it, target, blurred) }
                        val bitmap = cached ?: source?.let { loader.load(it.first, it.second, target, blurred) }
                        currentCoroutineContext().ensureActive()
                        (state.value.painter as? ReleasableArtworkPainter)?.release()
                        state.value = EnrichedArtwork(bitmap?.let(loader.memoryOwner::painter), pending, fromMemory = cached != null)
                        loadedSource[0] = key
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
