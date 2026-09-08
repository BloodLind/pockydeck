package dev.handheld.launcher.ui.artwork.local

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.core.graphics.drawable.toBitmap
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.ui.artwork.ArtworkDecodePolicy
import dev.handheld.launcher.ui.artwork.ArtworkMemoryCache
import dev.handheld.launcher.ui.artwork.ArtworkMemoryOwner
import dev.handheld.launcher.ui.artwork.ReleasableArtworkPainter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

sealed interface AndroidIconResult {
    data class Loaded(val bitmap: ImageBitmap) : AndroidIconResult
    data object Missing : AndroidIconResult
}

/** Loads installed-app icons off the main thread and bounds decoded bitmap memory. */
class AndroidIconLoader(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    cacheBytes: Int = DEFAULT_CACHE_BYTES,
    private val maximumIconSizePx: Int = DEFAULT_MAX_ICON_SIZE_PX,
) {
    internal var generation by mutableIntStateOf(0)
        private set
    internal val memoryOwner = ArtworkMemoryOwner()
    internal val foreground get() = memoryOwner.foreground
    private val packageManager = context.applicationContext.packageManager
    private val cache = ArtworkMemoryCache<String, ImageBitmap>(cacheBytes.coerceAtLeast(1)) { it.width * it.height * 4 }
    internal val cachedBytes get() = cache.sizeBytes
    private val decoding = Semaphore(2)
    private val sameComponent = Array(16) { Mutex() }

    suspend fun load(componentId: CurrentUserAndroidComponentId, targetSizePx: Int = maximumIconSizePx): AndroidIconResult {
        currentCoroutineContext().ensureActive()
        val target = ArtworkDecodePolicy.target(targetSizePx).coerceAtMost(maximumIconSizePx).coerceAtLeast(1)
        val key = "${componentId.itemId.value}@$target"
        cache.get(key)?.let { return AndroidIconResult.Loaded(it) }
        val revision = cache.revision
        return withContext(dispatcher) {
          sameComponent[(key.hashCode() and Int.MAX_VALUE) % sameComponent.size].withLock {
            cache.get(key)?.let { return@withLock AndroidIconResult.Loaded(it) }
            decoding.withPermit {
              currentCoroutineContext().ensureActive()
              val loaded = try {
                val drawable = packageManager.getActivityIcon(
                    ComponentName(componentId.packageName, componentId.activityClassName),
                )
                currentCoroutineContext().ensureActive()
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: target
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: target
                val scale = minOf(
                    1f,
                    target.toFloat() / width,
                    target.toFloat() / height,
                )
                drawable.toBitmap(
                    width = (width * scale).toInt().coerceAtLeast(1),
                    height = (height * scale).toInt().coerceAtLeast(1),
                    config = Bitmap.Config.ARGB_8888,
                ).asImageBitmap()
              } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { return@withPermit AndroidIconResult.Missing }
              currentCoroutineContext().ensureActive()
              cache.put(key, loaded, revision)
              AndroidIconResult.Loaded(loaded)
            }
          }
        }
    }

    internal fun clear() {
        cache.trimTo(0)
        generation++
    }

    fun setForeground(value: Boolean) { memoryOwner.updateForeground(value); if (!value) trimMemory(clear = true) }
    fun trimMemory(clear: Boolean) = cache.trimTo(if (clear) 0 else cache.maximumBytes / 2)

    companion object {
        const val DEFAULT_CACHE_BYTES = 8 * 1024 * 1024
        const val DEFAULT_MAX_ICON_SIZE_PX = 192
    }
}

@Composable
fun rememberAndroidIconResult(
    loader: AndroidIconLoader,
    componentId: CurrentUserAndroidComponentId,
    active: Boolean = true,
    targetSizePx: Int = AndroidIconLoader.DEFAULT_MAX_ICON_SIZE_PX,
): State<AndroidIconResult?> {
    val artworkActive = active && loader.foreground
    val target = ArtworkDecodePolicy.target(targetSizePx)
    val result = remember(loader, componentId, artworkActive, target, loader.generation, loader.memoryOwner.generation) {
        mutableStateOf<AndroidIconResult?>(null)
    }
    DisposableEffect(result) {
        val unregister = if (artworkActive) loader.memoryOwner.onBackground { result.value = null } else ({})
        onDispose { unregister(); result.value = null }
    }
    LaunchedEffect(result) {
        if (!artworkActive) return@LaunchedEffect
        val job = currentCoroutineContext().job
        val unregister = loader.memoryOwner.onBackground { result.value = null; job.cancel() }
        try { result.value = loader.load(componentId, target) }
        finally { unregister() }
    }
    return result
}

@Composable
fun rememberAndroidIconPainter(
    loader: AndroidIconLoader,
    componentId: CurrentUserAndroidComponentId,
    active: Boolean = true,
    targetSizePx: Int = AndroidIconLoader.DEFAULT_MAX_ICON_SIZE_PX,
): Painter? {
    val artworkActive = active && loader.foreground
    val target = ArtworkDecodePolicy.target(targetSizePx)
    // Do not remember a Loaded(ImageBitmap) as a key: stopped compositions would retain
    // that key even after their observable result and painter delegate have been cleared.
    val painter = remember(loader, componentId, artworkActive, target, loader.generation, loader.memoryOwner.generation) {
        mutableStateOf<ReleasableArtworkPainter?>(null)
    }
    DisposableEffect(painter) {
        val unregister = if (artworkActive) loader.memoryOwner.onBackground { painter.value = null } else ({})
        onDispose { unregister(); painter.value?.release(); painter.value = null }
    }
    LaunchedEffect(painter) {
        if (!artworkActive) return@LaunchedEffect
        val job = currentCoroutineContext().job
        val unregister = loader.memoryOwner.onBackground { painter.value = null; job.cancel() }
        try {
            val loaded = loader.load(componentId, target) as? AndroidIconResult.Loaded
            currentCoroutineContext().ensureActive()
            painter.value = loaded?.bitmap?.let(loader.memoryOwner::painter)
        } finally { unregister() }
    }
    return painter.value
}
