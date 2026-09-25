package dev.handheld.launcher.di

import androidx.lifecycle.SavedStateHandle
import dev.handheld.launcher.core.domain.model.*
import dev.handheld.launcher.core.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundColorSelectionTest {
    @Test fun `rapid colors preview the newest value while a slow older write completes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = Colors()
        try {
            val controller = object : ControllerPreferenceRepository {
                override val confirmBackMapping = flowOf(ConfirmBackMapping.Default)
                override val buttonLayout = flowOf(ControllerButtonLayout.Default)
                override suspend fun setConfirmBackMapping(mapping: ConfirmBackMapping) = Unit
                override suspend fun setButtonLayout(layout: ControllerButtonLayout) = Unit
            }
            val app = LauncherAppViewModel(controller, SavedStateHandle(), repository)
            runCurrent()
            app.setBackgroundCustomColorRgb(1)
            runCurrent()
            repeat(400) { app.setBackgroundCustomColorRgb(it + 2) }
            runCurrent()
            assertEquals(401, app.display.value.backgroundCustomColorRgb)
            assertEquals(listOf(1), repository.writes)
            repository.block.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(1, 401), repository.writes)
            assertEquals(401, app.display.value.backgroundCustomColorRgb)
            assertEquals(401, repository.preferences.value.backgroundCustomColorRgb)
            app.setBackgroundCustomColorRgb(0xFF33CC)
            app.setBackgroundTint(BackgroundTint.BLUE)
            advanceUntilIdle()
            assertEquals(BackgroundTint.BLUE, app.display.value.backgroundTint)
            assertNull(app.display.value.backgroundCustomColorRgb)
            assertEquals(app.display.value, repository.preferences.value)
        } finally { Dispatchers.resetMain() }
    }

    private class Colors : DisplayPreferenceRepository {
        override val preferences = MutableStateFlow(DisplayPreferences())
        val block = CompletableDeferred<Unit>()
        val writes = mutableListOf<Int>()
        override suspend fun setBackgroundCustomColorRgb(rgb: Int) {
            writes += rgb
            block.await()
            preferences.value = preferences.value.copy(backgroundCustomColorRgb = rgb)
        }
        override suspend fun setBackgroundTint(tint: BackgroundTint) {
            preferences.value = preferences.value.copy(backgroundTint = tint, backgroundCustomColorRgb = null)
        }
        override suspend fun setUiScalePercent(percent: Int) = Unit
        override suspend fun setGridSizePercent(percent: Int) = Unit
        override suspend fun setReduceMotion(enabled: Boolean) = Unit
        override suspend fun setHomeArtworkBackground(enabled: Boolean) = Unit
        override suspend fun setListArtworkBackground(enabled: Boolean) = Unit
        override suspend fun setBackgroundTintPercent(percent: Int) = Unit
        override suspend fun setBackgroundGrainPercent(percent: Int) = Unit
        override suspend fun setCollectionListMode(destination: LauncherDestination, isList: Boolean) = Unit
    }
}
