package dev.handheld.launcher.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayPreferencesTest {
    @Test fun gridSizeIsIndependentOfUiScaleAndDestinationLayout() {
        val initial = DisplayPreferences(120, true, setOf(LauncherDestination.APPS))
        assertEquals(1f, initial.gridSizeFactor, 0f)
        assertEquals(listOf(70, 80, 90, 100, 110, 120, 130, 140), DisplayPreferences.supportedGridSizes)
        for (percent in DisplayPreferences.supportedGridSizes) {
            val resized = initial.copy(gridSizePercent = percent)
            assertEquals(percent / 100f, resized.gridSizeFactor, 0f)
            assertEquals(initial.uiScaleFactor, resized.uiScaleFactor, 0f)
            assertEquals(initial.listDestinations, resized.listDestinations)
            assertEquals(initial.reduceMotion, resized.reduceMotion)
        }
    }

    @Test fun gridSizeRejectsUnsupportedValuesRatherThanCreatingAnInvalidLayoutState() {
        for (percent in listOf(Int.MIN_VALUE, 0, 69, 75, 95, 101, 141, Int.MAX_VALUE)) {
            assertTrue("Unsupported grid size $percent", runCatching { DisplayPreferences(gridSizePercent = percent) }
                .exceptionOrNull() is IllegalArgumentException)
        }
    }
}
