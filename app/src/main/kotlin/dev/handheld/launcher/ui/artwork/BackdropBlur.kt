package dev.handheld.launcher.ui.artwork

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.roundToInt

internal const val BACKDROP_SIZE_PX = 128

/** Executed on IO once per source; drawing the backdrop needs no full-screen blur layer. */
internal fun blurredBackdrop(source: ImageBitmap): ImageBitmap {
    val original = source.asAndroidBitmap()
    val scale = minOf(1f, BACKDROP_SIZE_PX.toFloat() / maxOf(original.width, original.height))
    val small = Bitmap.createScaledBitmap(original, (original.width * scale).roundToInt().coerceAtLeast(1),
        (original.height * scale).roundToInt().coerceAtLeast(1), true)
    try {
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        val blurred = blurBackdropPixels(pixels, small.width, small.height)
        return Bitmap.createBitmap(blurred, small.width, small.height, Bitmap.Config.ARGB_8888)
            .apply { prepareToDraw() }.asImageBitmap()
    } finally {
        if (small !== original) small.recycle()
    }
}

/** Three separable box passes approximate a soft Gaussian blur in linear time. */
internal fun blurBackdropPixels(pixels: IntArray, width: Int, height: Int, radius: Int = 8): IntArray {
    require(width > 0 && height > 0 && pixels.size == width * height && radius > 0)
    var source = pixels.copyOf()
    var target = IntArray(source.size)
    val window = radius * 2 + 1
    repeat(3) {
        for (horizontal in listOf(true, false)) {
            val lines = if (horizontal) height else width
            val length = if (horizontal) width else height
            for (line in 0 until lines) {
                fun offset(position: Int): Int = if (horizontal) line * width + position.coerceIn(0, width - 1)
                    else position.coerceIn(0, height - 1) * width + line
                var a = 0; var r = 0; var g = 0; var b = 0
                fun add(pixel: Int, sign: Int) {
                    a += (pixel ushr 24) * sign
                    r += ((pixel ushr 16) and 255) * sign
                    g += ((pixel ushr 8) and 255) * sign
                    b += (pixel and 255) * sign
                }
                for (position in -radius..radius) add(source[offset(position)], 1)
                for (position in 0 until length) {
                    target[offset(position)] = ((a / window) shl 24) or ((r / window) shl 16) or
                        ((g / window) shl 8) or (b / window)
                    add(source[offset(position - radius)], -1)
                    add(source[offset(position + radius + 1)], 1)
                }
            }
            val previous = source; source = target; target = previous
        }
    }
    return source
}
