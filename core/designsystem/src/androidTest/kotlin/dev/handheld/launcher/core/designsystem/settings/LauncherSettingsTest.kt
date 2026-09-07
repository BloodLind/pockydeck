package dev.handheld.launcher.core.designsystem.settings

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class LauncherSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun settingAndSwitchUseOneNativeTargetAndLongValuesStayWithinRow() {
        var actions = 0
        var toggles = 0
        var checked by mutableStateOf(false)
        var value by mutableStateOf("Default")
        val requester = FocusRequester()
        lateinit var inputMode: InputModeManager
        compose.setContent {
            LauncherTheme(referenceScale = 2f / 3f) {
                inputMode = LocalInputModeManager.current
                Column(Modifier.width(420.dp)) {
                    SettingRow("Open settings", modifier = Modifier.testTag("action")
                        .focusRequester(requester), onActivate = { actions++ })
                    ToggleRow("Use A to confirm", checked, { checked = it; toggles++ },
                        modifier = Modifier.testTag("toggle"))
                    ActionRow("Unavailable action", enabled = false,
                        modifier = Modifier.testTag("disabled"), onActivate = { actions++ })
                    ChoiceRow("Preferred launch behavior for supported applications", value, {},
                        modifier = Modifier.testTag("choice"), supportingText = "Choose how this item opens")
                }
            }
        }
        compose.onNodeWithTag("action").performTouchInput { click() }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard); requester.requestFocus() }
        compose.onNodeWithTag("action").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithTag("toggle").performTouchInput { click() }
        compose.onNodeWithTag("toggle").assertIsOn().assertIsNotSelected()
        compose.onNodeWithTag("disabled").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(2, actions); assertEquals(1, toggles) }
        val oldBounds = compose.onNodeWithTag("choice").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { value = "A very long selected choice value ".repeat(12) }
        val newBounds = compose.onNodeWithTag("choice").fetchSemanticsNode().boundsInRoot
        assertEquals(oldBounds.width, newBounds.width, .1f)
        assertTrue(newBounds.height <= oldBounds.height + 60f)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        File(dir, "us009-settings.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
