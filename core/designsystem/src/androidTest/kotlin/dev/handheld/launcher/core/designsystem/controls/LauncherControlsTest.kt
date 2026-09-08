package dev.handheld.launcher.core.designsystem.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class LauncherControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun buttonActivatesOnceAndFocusMovementDoesNotActivate() {
        var activations = 0
        var focused = false
        val first = FocusRequester()
        val second = FocusRequester()
        lateinit var inputModeManager: androidx.compose.ui.input.InputModeManager
        compose.setContent {
            LauncherTheme {
                inputModeManager = LocalInputModeManager.current
                Row {
                    LauncherButton("Open", { activations++ }, Modifier.focusRequester(first), onFocusChanged = { focused = it })
                    LauncherButton("Other", {}, Modifier.focusRequester(second))
                }
            }
        }
        compose.onNodeWithText("Open").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, activations)
            inputModeManager.requestInputMode(InputMode.Keyboard)
            first.requestFocus()
        }
        compose.onNodeWithText("Open").assertIsFocused()
        compose.runOnIdle { assertTrue(focused) }
        compose.onNodeWithText("Open").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(2, activations) }
        compose.onNodeWithText("Open").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(3, activations) }
        compose.onNodeWithText("Open").performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(3, activations) }
        compose.onNodeWithText("Other").assertIsFocused()
    }

    @Test
    fun disabledButtonCannotActivateAndSelectedChipIsIndependentOfFocus() {
        var activations = 0
        var selected by mutableStateOf(true)
        val apps = FocusRequester()
        lateinit var inputModeManager: androidx.compose.ui.input.InputModeManager
        compose.setContent {
            LauncherTheme {
                inputModeManager = LocalInputModeManager.current
                Column {
                    LauncherButton("Unavailable", { activations++ }, enabled = false,
                        unavailable = true, unavailableReason = "Needs recovery")
                    FilterChip("Games", selected, { selected = it })
                    FilterChip("Apps", false, {}, Modifier.focusRequester(apps))
                }
            }
        }
        compose.onNodeWithText("Unavailable")
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Needs recovery"))
            .performClick()
        compose.runOnIdle {
            inputModeManager.requestInputMode(InputMode.Keyboard)
            apps.requestFocus()
        }
        compose.runOnIdle {
            assertEquals(0, activations)
            assertTrue(selected)
        }
        compose.onNodeWithText("Games").assertIsSelected()
        compose.onNodeWithText("Apps").assertIsFocused()
    }

    @Test
    fun searchFieldUsesNativeTextInputAndReportsQueryChanges() {
        var query by mutableStateOf("")
        var searches = 0
        val queryFocus = FocusRequester()
        compose.setContent {
            LauncherTheme { SearchField(query, { query = it }, Modifier.focusRequester(queryFocus), onSearch = { searches++ }) }
        }
        val field = compose.onNode(hasSetTextAction())
        compose.runOnIdle { queryFocus.requestFocus() }
        field.assertIsFocused()
        field.performTextInput("zelda")
        field.performImeAction()
        compose.runOnIdle {
            assertEquals("zelda", query)
            assertEquals(1, searches)
        }
    }

    @Test
    fun filterPillsAreVisuallyCompactWithSeparate48DpTouchTargets() {
        compose.setContent {
            LauncherTheme(referenceScale = 2f / 3f) {
                Row(Modifier.background(Color.Black)) {
                    FilterChip("Games", true, {}, Modifier.testTag("games"))
                    FilterChip("Apps", false, {}, Modifier.testTag("apps"))
                }
            }
        }
        listOf("games", "apps").forEach { tag ->
            compose.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
        val games = compose.onNodeWithTag("games").fetchSemanticsNode().boundsInRoot
        val apps = compose.onNodeWithTag("apps").fetchSemanticsNode().boundsInRoot
        assertTrue("Adjacent touch targets never overlap", games.right <= apps.left)
        val pixels = compose.onNodeWithTag("games").captureToImage().toPixelMap()
        val paintedRows = (0 until pixels.height).count { y ->
            (0 until pixels.width).any { x -> pixels[x, y].red > .2f }
        }
        assertTrue("The pill is shorter than its accessible touch target", paintedRows < pixels.height * .85f)
        assertTrue("The reference-sized pill remains visible", paintedRows > pixels.height * .25f)
    }
}
