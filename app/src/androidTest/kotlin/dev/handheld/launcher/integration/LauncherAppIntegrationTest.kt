package dev.handheld.launcher.integration

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.MainActivity
import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.model.ControllerFaceButton
import dev.handheld.launcher.di.LauncherApplication
import dev.handheld.launcher.feature.search.SearchScreenTags
import dev.handheld.launcher.shell.LauncherShellTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator-safe integration coverage: no role picker, external launch, or catalog mutations. */
@RunWith(AndroidJUnit4::class)
class LauncherAppIntegrationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun dockDestinationsReuseOneShellRootAndExposeSelectedDestination() {
        LauncherDestination.dockOrder.forEach { destination ->
            compose.onNodeWithTag(LauncherShellTags.destination(destination)).performClick()
            compose.waitForIdle()
            compose.onAllNodesWithTag(LauncherShellTags.Root).assertCountEquals(1)
            compose.onNodeWithTag(LauncherShellTags.destination(destination)).assertIsSelected()
        }
    }

    @Test
    fun touchPageSelectionAndReselectionPutFocusOnTheActiveHomeCardAndBackKeepsItsActions() {
        val selectedCard = isFocused() and hasAnyAncestor(hasTestTag(LauncherShellTags.Content)) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button) and
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
        compose.onNodeWithTag(LauncherShellTags.destination(LauncherDestination.SETTINGS)).performTouchInput { click() }
        val home = compose.onNodeWithTag(LauncherShellTags.destination(LauncherDestination.HOME))
        home.performTouchInput { click() }
        compose.runOnIdle { pressGamepad(KeyEvent.KEYCODE_DPAD_RIGHT) }
        compose.waitUntil(5_000) { compose.onAllNodes(selectedCard).fetchSemanticsNodes().size == 1 }
        compose.onNode(selectedCard).assertIsFocused()

        home.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        home.assertIsFocused()
        home.performTouchInput { click() }
        compose.runOnIdle { pressGamepad(KeyEvent.KEYCODE_DPAD_RIGHT) }
        compose.waitUntil(5_000) { compose.onAllNodes(selectedCard).fetchSemanticsNodes().size == 1 }
        compose.onNode(selectedCard).assertIsFocused()
        home.assertIsSelected()

        val confirm = compose.onNodeWithTag(LauncherShellTags.footerAction(SemanticInputAction.CONFIRM))
        val confirmLabel = confirm.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        compose.onNodeWithTag(LauncherShellTags.footerAction(SemanticInputAction.TERTIARY)).assertExists()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(selectedCard).assertIsFocused()
        confirm.assertContentDescriptionEquals(*confirmLabel.toTypedArray())
        compose.onNodeWithTag(LauncherShellTags.footerAction(SemanticInputAction.TERTIARY)).assertExists()
    }

    @Test
    fun searchEditsKeepNativeFieldFocusAndQuerySurvivesFilterChange() {
        compose.onNodeWithTag(LauncherShellTags.destination(LauncherDestination.SEARCH)).performClick()
        compose.waitForIdle()

        val query = compose.onNode(hasSetTextAction(), useUnmergedTree = true)
        query.performClick()
        query.performTextReplacement("query")
        query.assertTextContains("query")
        query.assertIsFocused()

        // The production shell condenses Search while the native IME owns the lower window.
        // Hide it before touching a filter, without sending Back through launcher navigation.
        compose.runOnIdle {
            val inputMethodManager = compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        // Android's IME dismissal is asynchronous and is not part of Compose's idle clock.
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("System", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("System", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction(), useUnmergedTree = true).assertTextContains("query")
        compose.onAllNodesWithTag(LauncherShellTags.Root).assertCountEquals(1)
    }

    @Test
    fun mappedGamepadButtonsApplyAndCancelSearchWithoutOpeningAResult() {
        val container = (compose.activity.application as LauncherApplication).appContainer
        val mapping = runBlocking { container.controllerPreferenceRepository.confirmBackMapping.first() }
        compose.onNodeWithTag(LauncherShellTags.destination(LauncherDestination.SEARCH)).performClick()
        compose.onNodeWithText("System", useUnmergedTree = true).performClick()
        val editor = compose.onNode(hasSetTextAction(), useUnmergedTree = true)
        editor.performClick().performTextReplacement("ROM foldersx")
        compose.runOnIdle { pressGamepad(KeyEvent.KEYCODE_DEL) }
        editor.assertTextContains("ROM folders")
        editor.assertIsFocused()
        compose.runOnIdle { pressGamepad(if (mapping.confirm == ControllerFaceButton.A) KeyEvent.KEYCODE_BUTTON_A else KeyEvent.KEYCODE_BUTTON_B) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(SearchScreenTags.Edit).fetchSemanticsNodes().isNotEmpty() }
        val result = compose.onNode(hasContentDescription("ROM folders") and hasAnyAncestor(hasTestTag(LauncherShellTags.Content)))
        result.assertIsFocused()
        compose.onNodeWithTag(SearchScreenTags.Edit).performClick()
        editor.assertIsFocused().performTextReplacement("unmatched controller draft")
        compose.runOnIdle { pressGamepad(if (mapping.back == ControllerFaceButton.A) KeyEvent.KEYCODE_BUTTON_A else KeyEvent.KEYCODE_BUTTON_B) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(SearchScreenTags.Edit).fetchSemanticsNodes().isNotEmpty() }
        result.assertIsFocused()
        compose.onNodeWithTag(SearchScreenTags.Edit).performClick()
        editor.assertTextContains("ROM folders")
        compose.onNodeWithTag(LauncherShellTags.destination(LauncherDestination.SEARCH)).assertIsSelected()
    }

    @Test
    fun rapidRightShoulderRouteSwitchesKeepOneShellAndReachSettings() {
        compose.onNodeWithTag(LauncherShellTags.destination(LauncherDestination.HOME)).performClick()
        compose.runOnIdle {
            repeat(4) {
                val downTime = SystemClock.uptimeMillis()
                compose.activity.dispatchKeyEvent(
                    KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_R1, 0),
                )
                compose.activity.dispatchKeyEvent(
                    KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_R1, 0),
                )
            }
        }
        compose.waitForIdle()
        compose.onAllNodesWithTag(LauncherShellTags.Root).assertCountEquals(1)
        compose.onNodeWithTag(LauncherShellTags.destination(LauncherDestination.SETTINGS)).assertIsSelected()
    }

    @Test fun touchedSliderRestoresExactlyAndBackExitsAdjustmentBeforeLeavingSettings() {
        val container = (compose.activity.application as LauncherApplication).appContainer
        val mapping = runBlocking { container.controllerPreferenceRepository.confirmBackMapping.first() }
        val original = runBlocking { container.displayPreferenceRepository.preferences.first().gridSizePercent }
        fun button(value: ControllerFaceButton) = if (value == ControllerFaceButton.A) KeyEvent.KEYCODE_BUTTON_A else KeyEvent.KEYCODE_BUTTON_B
        fun press(key: Int) { compose.runOnIdle { pressGamepad(key) }; compose.waitForIdle() }
        try {
            compose.onNodeWithTag(LauncherShellTags.destination(LauncherDestination.SETTINGS)).performClick()
            compose.onNodeWithContentDescription("Display").performClick()
            val slider = compose.onNodeWithContentDescription("Grid card size")
            slider.performScrollTo()
            // Real Activity touch dispatch also switches the root's input mode.
            val bounds = slider.fetchSemanticsNode().boundsInRoot
            compose.runOnIdle {
                val time = SystemClock.uptimeMillis()
                val offset = IntArray(2)
                compose.activity.window.decorView.getLocationOnScreen(offset)
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(time, time + 20, action,
                        bounds.center.x + offset[0], bounds.bottom - 25 + offset[1], 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                    try { compose.activity.dispatchTouchEvent(event) } finally { event.recycle() }
                }
            }
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            slider.assertIsFocused()
            press(button(mapping.confirm))
            compose.onNodeWithContentDescription("D-pad Left and Right", useUnmergedTree = true).assertExists()
            val before = runBlocking { container.displayPreferenceRepository.preferences.first().gridSizePercent }
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            compose.waitUntil(5_000) {
                runBlocking { container.displayPreferenceRepository.preferences.first().gridSizePercent } > before
            }
            press(button(mapping.back))
            slider.assertIsFocused()
            compose.onNodeWithContentDescription("D-pad Left and Right", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag(LauncherShellTags.destination(LauncherDestination.SETTINGS)).assertIsSelected()
            press(KeyEvent.KEYCODE_DPAD_UP)
            compose.onNodeWithContentDescription("UI scale").assertIsFocused()
            press(button(mapping.confirm))
            compose.onNodeWithContentDescription("D-pad Left and Right", useUnmergedTree = true).assertExists()
            press(button(mapping.back))

            // Returning from controller use to a touched non-first slider must preserve that target.
            slider.performScrollTo()
            val secondBounds = slider.fetchSemanticsNode().boundsInRoot
            compose.runOnIdle {
                val time = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(time, time + 20, action,
                        secondBounds.center.x, secondBounds.bottom - 25, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                    try { compose.activity.dispatchTouchEvent(event) } finally { event.recycle() }
                }
            }
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            slider.assertIsFocused()
            press(KeyEvent.KEYCODE_DPAD_LEFT)
            assertTrue("A slider outside adjustment mode must allow leaving to the settings sidebar",
                compose.onAllNodes(hasContentDescription("Grid card size") and isFocused()).fetchSemanticsNodes().isEmpty())
        } finally {
            runBlocking { container.displayPreferenceRepository.setGridSizePercent(original) }
        }
    }

    private fun pressGamepad(keyCode: Int) {
        val time = SystemClock.uptimeMillis()
        val source = if (keyCode == KeyEvent.KEYCODE_DEL) InputDevice.SOURCE_KEYBOARD else InputDevice.SOURCE_GAMEPAD
        compose.activity.dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode, 0,
            0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, source))
        compose.activity.dispatchKeyEvent(KeyEvent(time, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0,
            0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, source))
    }
}
