package dev.handheld.launcher.rom

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.MainActivity
import dev.handheld.launcher.core.data.rom.repository.RoomRomLibraryRepository
import dev.handheld.launcher.core.domain.model.ConfirmBackMapping
import dev.handheld.launcher.core.domain.model.ControllerFaceButton
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.di.LauncherApplication
import dev.handheld.launcher.shell.LauncherShellTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual application controller and shell; no ROM grants, external launches or user-file mutations. */
@RunWith(AndroidJUnit4::class)
class RomInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val container get() = (compose.activity.application as LauncherApplication).appContainer
    private lateinit var originalMapping: ConfirmBackMapping
    private var originalLimit = RoomRomLibraryRepository.DEFAULT_CACHE_LIMIT

    @Before fun setUp() {
        runBlocking {
            originalLimit = container.romRepository.cacheLimitBytes.first()
            originalMapping = container.controllerPreferenceRepository.confirmBackMapping.first()
        }
        compose.onNodeWithTag(LauncherShellTags.destination(LauncherDestination.SETTINGS)).performClick()
        compose.onNodeWithContentDescription("ROM folders").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            container.romController.sourcesState.value.cacheLimitLabel == "${originalLimit / RoomRomLibraryRepository.GIB} GiB"
        }
    }

    @After fun tearDown() {
        container.romController.dismissChoice()
        compose.waitUntil(5_000) { container.romController.choice.value == null }
        runBlocking { container.romRepository.setCacheLimit(originalLimit) }
    }

    @Test fun cacheChoiceCommitsOnlySelectedBudgetAndReopensWithPersistedSelection() {
        val changed = if (originalLimit == 2 * RoomRomLibraryRepository.GIB) 4 * RoomRomLibraryRepository.GIB else 2 * RoomRomLibraryRepository.GIB
        openCacheChoice()
        assertEquals(originalLimit.toString(), container.romController.choice.value?.selectedId)
        compose.onNodeWithContentDescription("${changed / RoomRomLibraryRepository.GIB} GiB").performClick()
        compose.waitUntil(5_000) {
            container.romController.choice.value == null &&
                container.romController.sourcesState.value.cacheLimitLabel == "${changed / RoomRomLibraryRepository.GIB} GiB"
        }
        assertEquals(changed, runBlocking { container.romRepository.cacheLimitBytes.first() })
        assertEquals(originalMapping, runBlocking { container.controllerPreferenceRepository.confirmBackMapping.first() })

        openCacheChoice()
        assertEquals(changed.toString(), container.romController.choice.value?.selectedId)
        compose.onNodeWithText("Close").performClick()
        compose.waitUntil(5_000) { container.romController.choice.value == null }
        assertEquals(changed, runBlocking { container.romRepository.cacheLimitBytes.first() })
        compose.onAllNodesWithTag(LauncherShellTags.Root).assertCountEquals(1)
        compose.onNodeWithTag(LauncherShellTags.destination(LauncherDestination.SETTINGS)).assertIsSelected()
    }

    @Test fun mappedBackCancelsChoiceAndModalBlocksBackgroundNavigationWithoutChangingPreferences() {
        val defaults = runBlocking { container.romRepository.consoleEmulatorDefaults.first() }
        openCacheChoice()
        compose.runOnIdle {
            press(KeyEvent.KEYCODE_BUTTON_R1)
            press(KeyEvent.KEYCODE_BUTTON_X)
        }
        compose.waitForIdle()
        assertEquals("Game cache limit", container.romController.choice.value?.title)
        compose.onNodeWithTag(LauncherShellTags.destination(LauncherDestination.SETTINGS)).assertIsSelected()

        compose.runOnIdle { press(if (originalMapping.back == ControllerFaceButton.A) KeyEvent.KEYCODE_BUTTON_A else KeyEvent.KEYCODE_BUTTON_B) }
        compose.waitUntil(5_000) { container.romController.choice.value == null }
        compose.waitForIdle()
        assertEquals(originalLimit, runBlocking { container.romRepository.cacheLimitBytes.first() })
        assertEquals(defaults, runBlocking { container.romRepository.consoleEmulatorDefaults.first() })
        assertEquals(originalMapping, runBlocking { container.controllerPreferenceRepository.confirmBackMapping.first() })
        assertNull(container.romController.message.value)
        compose.onNodeWithContentDescription("Cache limit").assertIsFocused()
        compose.onAllNodesWithTag(LauncherShellTags.Root).assertCountEquals(1)
    }

    @Test fun touchOpenedChoiceRestoresTouchedRowInsteadOfPreviousKeyboardControl() {
        val previousControl = compose.onNode(
            hasContentDescription("Add ROM folder") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        )
        previousControl.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        previousControl.assertIsFocused()
        compose.onNodeWithContentDescription("Cache limit").performScrollTo().performTouchInput { click() }
        compose.waitUntil(5_000) { container.romController.choice.value?.title == "Game cache limit" }
        compose.onNodeWithText("Close").performTouchInput { click() }
        compose.waitUntil(5_000) { container.romController.choice.value == null }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Cache limit").assertIsFocused()
        assertEquals(originalLimit, runBlocking { container.romRepository.cacheLimitBytes.first() })
    }

    private fun openCacheChoice() {
        val control = compose.onNodeWithContentDescription("Cache limit").performScrollTo()
        control.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        control.assertIsFocused()
        control.performClick()
        compose.waitUntil(5_000) { container.romController.choice.value?.title == "Game cache limit" }
        compose.waitForIdle()
    }

    private fun press(keyCode: Int) {
        val start = SystemClock.uptimeMillis()
        compose.activity.dispatchKeyEvent(KeyEvent(start, start, KeyEvent.ACTION_DOWN, keyCode, 0))
        compose.activity.dispatchKeyEvent(KeyEvent(start, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0))
    }
}
