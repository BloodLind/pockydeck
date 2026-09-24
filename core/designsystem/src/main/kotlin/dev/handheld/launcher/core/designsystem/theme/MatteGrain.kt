package dev.handheld.launcher.core.designsystem.theme

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.roundToInt

/** Periodic 3x3 binomial smoothing removes isolated speckles without a runtime blur. */
private val softenedNoise: FloatArray by lazy {
    var seed = 0x504F434B
    val noise = FloatArray(128 * 128) {
        seed = seed xor (seed shl 13)
        seed = seed xor (seed ushr 17)
        seed = seed xor (seed shl 5)
        (seed ushr 24) / 127.5f - 1f
    }
    FloatArray(noise.size) { index ->
        val x = index % 128
        val y = index / 128
        var sum = 0f
        for (dy in -1..1) for (dx in -1..1) {
            val weight = (if (dx == 0) 2 else 1) * (if (dy == 0) 2 else 1)
            sum += noise[((y + dy) and 127) * 128 + ((x + dx) and 127)] * weight
        }
        sum / 16f
    }
}

// At most ten 64 KiB tiles. Dragging a slider reuses them; no random work per frame.
private val grainTiles = arrayOfNulls<Bitmap>(11)

internal fun plasticGrain(percent: Int): Bitmap = synchronized(grainTiles) {
    val level = (percent.coerceIn(0, 100) / 10f).roundToInt()
    grainTiles[level] ?: run {
        val intensity = level / 10f
        val pixels = IntArray(128 * 128) { index ->
            val sample = softenedNoise[index]
            val white = sample >= 0f
            // Match light/dark contrast on charcoal, with very low default amplitude.
            val alpha = (abs(sample) * (if (white) 18 else 72) * intensity).roundToInt()
            (alpha shl 24) or if (white) 0x00FFFFFF else 0
        }
        Bitmap.createBitmap(pixels, 128, 128, Bitmap.Config.ARGB_8888).apply { prepareToDraw() }
            .also { grainTiles[level] = it }
    }
}
