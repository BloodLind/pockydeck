package dev.handheld.launcher.ui.artwork

/** Reusable physical-pixel buckets avoid separate cache entries for tiny layout changes. */
internal object ArtworkDecodePolicy {
    const val MAX_ENCODED_BYTES = 16L * 1024 * 1024
    const val DEFAULT_TARGET_PX = 384
    private val sizes = intArrayOf(64, 96, 128, 192, 256, 384, 512, 768)

    fun target(requestedPx: Int): Int = sizes.firstOrNull { it >= requestedPx } ?: sizes.last()

    fun sampleSize(width: Int, height: Int, targetPx: Int): Int? {
        if (width !in 1..8192 || height !in 1..8192 || width.toLong() * height > 24_000_000) return null
        val target = target(targetPx)
        var sample = 1
        // BitmapFactory rounds sampled dimensions up for some formats.
        while ((maxOf(width, height) + sample - 1) / sample > target) sample *= 2
        return sample
    }
}
