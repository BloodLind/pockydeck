package dev.handheld.launcher.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.DisplayPreferences
import dev.handheld.launcher.feature.collection.FocusedControlAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GridSizeChoicesTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalComposeUiApi::class)
    @Test fun compactChoicesKeepControllerFocusAndApplyExactPercentages() {
        var percent by mutableIntStateOf(100)
        var focused: FocusedControlAction? = null
        lateinit var inputMode: InputModeManager
        compose.setContent {
            inputMode = LocalInputModeManager.current
            LauncherTheme(reducedMotion = true, referenceScale = 2f / 3f) {
                Box(Modifier.size(520.dp, 320.dp)) {
                    GridSizeChoices(percent, onSelect = { percent = it }, onFocusedAction = { focused = it })
                }
            }
        }
        DisplayPreferences.supportedGridSizes.forEach { choice ->
            compose.onNodeWithContentDescription("Grid card size $choice%").assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Grid card size 100%").assertIsSelected()
        val smaller = compose.onNodeWithContentDescription("Grid card size 70%")
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        smaller.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        smaller.assertIsFocused()
        compose.runOnIdle {
            assertEquals("Set grid card size to 70%", focused?.descriptor?.label)
            requireNotNull(focused?.onActivate).invoke()
        }
        smaller.assertIsSelected().assertIsFocused()
        compose.runOnIdle { assertEquals(70, percent) }
        compose.onNodeWithContentDescription("Grid card size 140%").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(140, percent) }
    }
}
