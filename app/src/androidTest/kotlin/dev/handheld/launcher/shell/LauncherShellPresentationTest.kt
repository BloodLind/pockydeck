package dev.handheld.launcher.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performTouchInput
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
import dev.handheld.launcher.core.designsystem.contract.LocalControllerInput
import dev.handheld.launcher.core.domain.model.LauncherDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalComposeUiApi::class)
class LauncherShellPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dockTargetStaysNeutralUntilTheMovingSelectionArrives() {
        var destination by mutableStateOf(LauncherDestination.HOME)
        var reducedMotion by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalControllerInput provides false) {
                ShellFixture(LauncherShellState(selectedDestination = destination,
                    status = LauncherShellStatus(clock = "10:42")), SemanticActionPort { false },
                    reducedMotion = reducedMotion,
                    onDestinationSelected = { destination = it })
            }
        }
        compose.mainClock.advanceTimeByFrame()
        fun surface(route: LauncherDestination): Color {
            val pixels = compose.onNodeWithTag(LauncherShellTags.destination(route)).captureToImage().toPixelMap()
            // Inside the circle, above the icon and away from the focus border.
            return pixels[pixels.width / 2, pixels.height / 8]
        }
        fun sameColor(message: String, expected: Color, actual: Color) {
            assertEquals(message, expected.red, actual.red, .01f)
            assertEquals(message, expected.green, actual.green, .01f)
            assertEquals(message, expected.blue, actual.blue, .01f)
        }
        val white = surface(LauncherDestination.HOME)
        val neutral = surface(LauncherDestination.SETTINGS)
        val settings = compose.onNodeWithTag(LauncherShellTags.destination(LauncherDestination.SETTINGS))
        settings.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(32)
        sameColor("Press must not light the destination ahead of the droplet", neutral, surface(LauncherDestination.SETTINGS))
        settings.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { assertEquals(LauncherDestination.SETTINGS, destination) }
        sameColor("Selection semantics must not paint a second target fill", neutral, surface(LauncherDestination.SETTINGS))

        compose.runOnIdle { destination = LauncherDestination.APPS }
        compose.mainClock.advanceTimeBy(256)
        sameColor("The interrupted transition must settle at its latest destination", white, surface(LauncherDestination.APPS))
        sameColor("An abandoned destination must never flash or retain a highlight", neutral, surface(LauncherDestination.SETTINGS))

        compose.runOnIdle { destination = LauncherDestination.SETTINGS }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { reducedMotion = true }
        compose.mainClock.advanceTimeByFrame()
        sameColor("Reduced motion snaps the same selection to the destination", white, surface(LauncherDestination.SETTINGS))
    }

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

    @Test fun compactSystemLegendsKeepFullTouchTargetsAndContentFollowsTheStatusStrip() {
        val actions = listOf(
            LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.ACTIVATE, "Play"),
            LauncherActionDescriptor(SemanticInputAction.ITEM_DETAILS, LauncherActionMeaning.OPEN_DETAILS, "Details"),
            LauncherActionDescriptor(SemanticInputAction.MENU, LauncherActionMeaning.OPEN_MENU, "Menu"),
        )
        var density = 1f
        val dispatched = mutableListOf<LauncherActionDescriptor>()
        compose.setContent {
            density = LocalDensity.current.density
            ShellFixture(LauncherShellState(status = LauncherShellStatus(clock = "10:42"),
                footer = ControllerActionFooter(actions)), SemanticActionPort { dispatched += it; true }, reducedMotion = true)
        }
        val status = compose.onNodeWithTag(LauncherShellTags.Status).fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag(LauncherShellTags.Content).fetchSemanticsNode().boundsInRoot
        assertEquals("No unused band below status", status.bottom, content.top, 1f)
        assertTrue("Status is a compact read-only strip", status.height / density <= 40f)
        actions.forEach { action ->
            val tag = LauncherShellTags.footerAction(action.input)
            val target = compose.onNodeWithTag(tag)
            val bounds = target.fetchSemanticsNode().boundsInRoot
            val glyph = compose.onNodeWithTag("$tag-glyph", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("Touch height survives smaller artwork", bounds.height / density >= 47.9f)
            assertTrue("The entire legend fits its action", glyph.top >= bounds.top && glyph.bottom <= bounds.bottom)
            assertTrue("Small glyphs leave breathing room", glyph.height < bounds.height * .7f)
            if (action.input != SemanticInputAction.CONFIRM)
                assertTrue("Start/Select use compact measured text", glyph.width / density < 54f)
            target.performClick()
        }
        compose.runOnIdle { assertEquals(actions, dispatched) }
    }

    @Composable
    private fun ShellFixture(
        state: LauncherShellState,
        actions: SemanticActionPort,
        reducedMotion: Boolean = false,
        pageContent: @Composable (Modifier) -> Unit = { Box(it) },
        onDestinationSelected: (LauncherDestination) -> Unit = {},
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val metrics = with(density) {
                ShellMetrics.calculate(ShellMetricsInput(maxWidth.roundToPx(), maxHeight.roundToPx(), this.density, fontScale))
            }
            LauncherTheme(reducedMotion = reducedMotion, referenceScale = metrics.referenceScale) {
                LauncherShell(metrics, state, actions, onDestinationSelected = onDestinationSelected, content = pageContent)
            }
        }
    }
}
