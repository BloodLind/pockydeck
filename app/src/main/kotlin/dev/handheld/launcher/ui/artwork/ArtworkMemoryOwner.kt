package dev.handheld.launcher.ui.artwork

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import java.lang.ref.WeakReference

/** Main-thread lifecycle gate; cleanup must not wait for a stopped Compose frame clock. */
internal class ArtworkMemoryOwner {
    var foreground by mutableStateOf(true)
        private set
    var generation by mutableIntStateOf(0)
        private set
    private val requests = mutableSetOf<() -> Unit>()
    internal val activeRequestCount get() = requests.size
    private val painters = mutableListOf<WeakReference<ReleasableArtworkPainter>>()

    fun onBackground(cancelAndClear: () -> Unit): () -> Unit {
        if (foreground) requests += cancelAndClear else cancelAndClear()
        return { requests -= cancelAndClear }
    }

    fun painter(bitmap: ImageBitmap): ReleasableArtworkPainter {
        val painter = ReleasableArtworkPainter(bitmap)
        painters.removeAll { it.get() == null }
        if (foreground) {
            val reference = WeakReference(painter)
            painters += reference
            painter.onRelease = { painters.remove(reference) }
        } else painter.release()
        return painter
    }

    fun updateForeground(value: Boolean) {
        foreground = value
        if (!value) {
            // A background/resume pair can happen while Compose's frame clock is stopped.
            // The changed generation still restarts cleared requests on the next frame.
            generation++
            val callbacks = requests.toList()
            requests.clear()
            callbacks.forEach { it() }
            painters.toList().forEach { it.get()?.release() }
            painters.clear()
        }
    }
}

/** Crossfade can retain an outgoing Painter while stopped; this delegate can release its image. */
internal class ReleasableArtworkPainter(bitmap: ImageBitmap) : Painter() {
    internal var onRelease: (() -> Unit)? = null
    private var delegate by mutableStateOf<BitmapPainter?>(BitmapPainter(bitmap))
    internal val holdsBitmap get() = delegate != null
    override val intrinsicSize: Size = Size(bitmap.width.toFloat(), bitmap.height.toFloat())

    fun release() { delegate = null; onRelease?.invoke(); onRelease = null }

    override fun DrawScope.onDraw() { delegate?.let { with(it) { draw(size) } } }
}
