package dev.handheld.launcher.core.domain.model

/** Launcher UI size is independent of Android's text accessibility setting. */
data class DisplayPreferences(
    val uiScalePercent: Int = 100,
    val reduceMotion: Boolean = false,
    val listDestinations: Set<LauncherDestination> = emptySet(),
    /** Library, Apps and Favorites artwork density; independent of fonts and overall UI scale. */
    val gridSizePercent: Int = 100,
) {
    init {
        require(uiScalePercent in supportedScales)
        require(listDestinations.all { it in collectionDestinations })
        require(gridSizePercent in supportedGridSizes)
    }

    val uiScaleFactor: Float get() = uiScalePercent / 100f
    val gridSizeFactor: Float get() = gridSizePercent / 100f

    companion object {
        val supportedScales = listOf(90, 100, 110, 120)
        val supportedGridSizes = (70..140 step 10).toList()
        val collectionDestinations = setOf(LauncherDestination.LIBRARY, LauncherDestination.APPS, LauncherDestination.FAVORITES)
    }
}
