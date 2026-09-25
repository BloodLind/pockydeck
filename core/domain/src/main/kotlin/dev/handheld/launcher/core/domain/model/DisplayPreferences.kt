package dev.handheld.launcher.core.domain.model

enum class BackgroundTint(val persistedKey: String) {
    PURPLE("purple"), GRAPHITE("graphite"), BLUE("blue"), GREEN("green"), WARM("warm");

    companion object {
        fun fromPersistedKey(value: String?): BackgroundTint = entries.firstOrNull { it.persistedKey == value } ?: PURPLE
    }
}

/** Launcher UI size is independent of Android's text accessibility setting. */
data class DisplayPreferences(
    val uiScalePercent: Int = 100,
    val reduceMotion: Boolean = false,
    val listDestinations: Set<LauncherDestination> = emptySet(),
    /** Library, Apps and Favorites artwork density; independent of fonts and overall UI scale. */
    val gridSizePercent: Int = 100,
    val homeArtworkBackground: Boolean = false,
    val listArtworkBackground: Boolean = false,
    val backgroundTint: BackgroundTint = BackgroundTint.PURPLE,
    val backgroundTintPercent: Int = 40,
    val backgroundGrainPercent: Int = 30,
    /** An opaque RGB custom tint; null uses the existing named preset. */
    val backgroundCustomColorRgb: Int? = null,
) {
    init {
        require(uiScalePercent in supportedScales)
        require(listDestinations.all { it in collectionDestinations })
        require(gridSizePercent in supportedGridSizes)
        require(backgroundTintPercent in supportedBackgroundLevels)
        require(backgroundGrainPercent in supportedBackgroundLevels)
        require(backgroundCustomColorRgb == null || backgroundCustomColorRgb in 0..0xFFFFFF)
    }

    val uiScaleFactor: Float get() = uiScalePercent / 100f
    val gridSizeFactor: Float get() = gridSizePercent / 100f

    companion object {
        val supportedScales = (90..150 step 10).toList()
        val supportedGridSizes = (70..140 step 10).toList()
        val supportedBackgroundLevels = (0..100 step 10).toList()
        val collectionDestinations = setOf(LauncherDestination.LIBRARY, LauncherDestination.APPS, LauncherDestination.FAVORITES)
    }
}
