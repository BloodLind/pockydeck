package dev.handheld.launcher.core.domain.model

/** Launcher UI size is independent of Android's text accessibility setting. */
data class DisplayPreferences(
    val uiScalePercent: Int = 100,
    val reduceMotion: Boolean = false,
    val listDestinations: Set<LauncherDestination> = emptySet(),
) {
    init {
        require(uiScalePercent in supportedScales)
        require(listDestinations.all { it in collectionDestinations })
    }

    val uiScaleFactor: Float get() = uiScalePercent / 100f

    companion object {
        val supportedScales = listOf(90, 100, 110, 120)
        val collectionDestinations = setOf(LauncherDestination.LIBRARY, LauncherDestination.APPS, LauncherDestination.FAVORITES)
    }
}
