package dev.handheld.launcher.integration

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.MainActivity
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.shell.LauncherShellTags
import org.junit.Rule
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
}
