package dev.handheld.launcher.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.contract.ControllerActionFooter
import dev.handheld.launcher.contract.LauncherActionDescriptor
import dev.handheld.launcher.contract.LauncherActionMeaning
import dev.handheld.launcher.contract.SemanticActionPort
import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.core.designsystem.foundation.ShellMetrics
import dev.handheld.launcher.core.designsystem.foundation.ShellMetricsInput
import dev.handheld.launcher.core.designsystem.controls.LauncherButton
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalComposeUiApi::class)
class LauncherShellPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun footerReplacementKeepsOnlyCurrentActionsDuringTransition() {
        val open = LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.ACTIVATE, "Open")
        val search = LauncherActionDescriptor(SemanticInputAction.SECONDARY, LauncherActionMeaning.OPEN_SEARCH, "Search")
        val apply = open.copy(label = "Apply")
        val cancel = LauncherActionDescriptor(SemanticInputAction.BACK, LauncherActionMeaning.GO_BACK, "Cancel")
        var footer by mutableStateOf(ControllerActionFooter(listOf(open, search)))
        val dispatched = mutableListOf<LauncherActionDescriptor>()
        val pageFocus = FocusRequester()
        lateinit var inputMode: InputModeManager
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ShellFixture(LauncherShellState(status = LauncherShellStatus(clock = "10:42"), footer = footer),
                SemanticActionPort { dispatched += it; true }, pageContent = { modifier ->
                    inputMode = LocalInputModeManager.current
                    Box(modifier) {
                        LauncherButton("Page action", {}, Modifier.focusRequester(pageFocus).testTag("focused-page-action"))
                    }
                })
        }
        compose.runOnIdle {
            inputMode.requestInputMode(InputMode.Keyboard)
            pageFocus.requestFocus()
        }
        val originalConfirmNodeId = compose.onNodeWithTag(LauncherShellTags.footerAction(SemanticInputAction.CONFIRM))
            .fetchSemanticsNode().id
        compose.runOnIdle { footer = ControllerActionFooter(listOf(cancel, apply)) }
        compose.mainClock.advanceTimeBy(48)

        compose.onAllNodesWithTag(LauncherShellTags.footerAction(SemanticInputAction.CONFIRM), useUnmergedTree = true)
            .assertCountEquals(1)
        assertEquals("A surviving input keeps its semantic identity after reordering", originalConfirmNodeId,
            compose.onNodeWithTag(LauncherShellTags.footerAction(SemanticInputAction.CONFIRM)).fetchSemanticsNode().id)
        compose.onNodeWithTag("focused-page-action").assertIsFocused()
        compose.onNodeWithTag(LauncherShellTags.footerAction(SemanticInputAction.SECONDARY)).assertDoesNotExist()
        compose.onNodeWithTag(LauncherShellTags.footerAction(SemanticInputAction.CONFIRM)).performClick()
        compose.onNodeWithTag(LauncherShellTags.footerAction(SemanticInputAction.BACK)).performClick()
        compose.runOnIdle { assertEquals(listOf(apply, cancel), dispatched) }
        compose.onNodeWithTag("focused-page-action").assertIsFocused()

        compose.mainClock.advanceTimeBy(160)
        compose.onNodeWithTag(LauncherShellTags.footerAction(SemanticInputAction.CONFIRM)).assertIsDisplayed()
    }

    @Test
    fun bluetoothAndNotificationPresenceIndicatorsDisappearWhenNotAvailable() {
        var status by mutableStateOf(LauncherShellStatus(clock = "10:42"))
        compose.setContent { ShellFixture(LauncherShellState(status = status), SemanticActionPort { false }, reducedMotion = true) }
        compose.onNodeWithContentDescription("Bluetooth enabled").assertDoesNotExist()
        compose.onNodeWithContentDescription("Notifications available").assertDoesNotExist()

        compose.runOnIdle { status = status.copy(bluetoothEnabled = true, notificationsPresent = true) }
        compose.onNodeWithContentDescription("Bluetooth enabled").assertIsDisplayed()
        compose.onNodeWithContentDescription("Notifications available").assertIsDisplayed()

        compose.runOnIdle { status = status.copy(bluetoothEnabled = false, notificationsPresent = false) }
        compose.onNodeWithContentDescription("Bluetooth enabled").assertDoesNotExist()
        compose.onNodeWithContentDescription("Notifications available").assertDoesNotExist()
    }

    @Composable
    private fun ShellFixture(
        state: LauncherShellState,
        actions: SemanticActionPort,
        reducedMotion: Boolean = false,
        pageContent: @Composable (Modifier) -> Unit = { Box(it) },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val metrics = with(density) {
                ShellMetrics.calculate(ShellMetricsInput(maxWidth.roundToPx(), maxHeight.roundToPx(), this.density, fontScale))
            }
            LauncherTheme(reducedMotion = reducedMotion, referenceScale = metrics.referenceScale) {
                LauncherShell(metrics, state, actions, onDestinationSelected = {}, content = pageContent)
            }
        }
    }
}
