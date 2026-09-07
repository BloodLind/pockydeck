package dev.handheld.launcher.ui.artwork.local

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.core.graphics.drawable.toBitmap
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
    private val packageManager = context.applicationContext.packageManager
    private val cache = object : LruCache<String, ImageBitmap>((cacheBytes / 1024).coerceAtLeast(1)) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            ((value.width.toLong() * value.height.toLong() * 4L) / 1024L)
                .coerceAtLeast(1L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
    }

    suspend fun load(componentId: CurrentUserAndroidComponentId): AndroidIconResult {
        val key = componentId.itemId.value
        synchronized(cache) { cache.get(key) }?.let { return AndroidIconResult.Loaded(it) }
        return withContext(dispatcher) {
            val loaded = runCatching {
                val drawable = packageManager.getActivityIcon(
                    ComponentName(componentId.packageName, componentId.activityClassName),
                )
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: maximumIconSizePx
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: maximumIconSizePx
                val scale = minOf(
                    1f,
                    maximumIconSizePx.toFloat() / width,
                    maximumIconSizePx.toFloat() / height,
                )
                drawable.toBitmap(
                    width = (width * scale).toInt().coerceAtLeast(1),
                    height = (height * scale).toInt().coerceAtLeast(1),
                    config = Bitmap.Config.ARGB_8888,
                ).asImageBitmap()
            }.getOrNull() ?: return@withContext AndroidIconResult.Missing
            synchronized(cache) { cache.put(key, loaded) }
            AndroidIconResult.Loaded(loaded)
        }
    }

    internal fun clear() {
        synchronized(cache) { cache.evictAll() }
        generation++
    }

    companion object {
        const val DEFAULT_CACHE_BYTES = 8 * 1024 * 1024
        const val DEFAULT_MAX_ICON_SIZE_PX = 192
    }
}

@Composable
fun rememberAndroidIconResult(
    loader: AndroidIconLoader,
    componentId: CurrentUserAndroidComponentId,
): State<AndroidIconResult?> {
    val result = remember(loader, componentId) { mutableStateOf<AndroidIconResult?>(null) }
    LaunchedEffect(loader, componentId, loader.generation) { result.value = loader.load(componentId) }
    return result
}

@Composable
fun rememberAndroidIconPainter(
    loader: AndroidIconLoader,
    componentId: CurrentUserAndroidComponentId,
): Painter? {
    val result = rememberAndroidIconResult(loader, componentId).value
    return (result as? AndroidIconResult.Loaded)?.bitmap?.let(::BitmapPainter)
}
