package dev.handheld.launcher.feature.settings

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.feature.collection.FocusedControlAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GridSizeChoicesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sliderSupportsAccessibleValueAndHeldControllerAdjustmentsWithoutLosingFocus() {
        var percent by mutableIntStateOf(100)
        var focused: FocusedControlAction? = null
        compose.setContent {
            LauncherTheme(reducedMotion = true) { GridSizeChoices(percent, { percent = it }, { focused = it }) }
        }
        val slider = compose.onNodeWithContentDescription("Grid card size")
        slider.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        slider.assertIsFocused()
        compose.runOnIdle {
            org.junit.Assert.assertNull(focused?.onAdjust)
            requireNotNull(focused?.onActivate).invoke()
        }
        compose.runOnIdle { repeat(6) { requireNotNull(focused?.onAdjust).invoke(-1) } }
        compose.runOnIdle { assertEquals(70, percent) }
        slider.assertIsFocused()
        compose.runOnIdle { repeat(12) { requireNotNull(focused?.onAdjust).invoke(1) } }
        compose.runOnIdle { assertEquals(140, percent) }
        slider.assertIsFocused()
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(110f) }
        compose.runOnIdle { assertEquals(110, percent) }
        compose.runOnIdle { requireNotNull(focused?.onBack).invoke() }
        slider.assertIsFocused()
        compose.runOnIdle {
            org.junit.Assert.assertNull(focused?.onAdjust)
            org.junit.Assert.assertNull(focused?.onBack)
            assertEquals(110, percent)
        }
    }

    @Test fun touchDragChangesValueAcrossTheTrack() {
        var percent by mutableIntStateOf(100)
        compose.setContent { LauncherTheme(reducedMotion = true) { GridSizeChoices(percent, { percent = it }) } }
        compose.onNodeWithContentDescription("Grid card size").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * .1f, height - 30f),
                androidx.compose.ui.geometry.Offset(width * .9f, height - 30f))
        }
        compose.runOnIdle { assertTrue(percent > 100) }
    }
}
