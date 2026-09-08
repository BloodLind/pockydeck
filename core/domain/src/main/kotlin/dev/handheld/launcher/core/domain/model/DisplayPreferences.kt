package dev.handheld.launcher.core.domain.model

/** Launcher UI size is independent of Android's text accessibility setting. */
data class DisplayPreferences(
    val uiScalePercent: Int = 100,
    val reduceMotion: Boolean = false,
) {
    init { require(uiScalePercent in supportedScales) }

    val uiScaleFactor: Float get() = uiScalePercent / 100f

    companion object {
        val supportedScales = listOf(90, 100, 110, 120)
    }
}
