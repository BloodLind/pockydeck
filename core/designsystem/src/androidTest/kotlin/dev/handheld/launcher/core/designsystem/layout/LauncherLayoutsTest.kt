package dev.handheld.launcher.core.designsystem.layout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.controls.LauncherButton
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LauncherLayoutsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun filterSelectionAndRecoveryCallbacksFireOnceInVerticalLayout() {
        var selected = -1
        var recovered = 0
        compose.setContent {
            LauncherTheme {
                Column {
                    FilterStrip(listOf("All", "Games"), 0, { selected = it })
                    EmptyState("Empty", "Nothing here", "Retry", { recovered++ })
                }
            }
        }
        compose.onNodeWithText("Games").performClick()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle {
            assertEquals(1, selected)
            assertEquals(1, recovered)
        }
    }

    @Test
    fun filterStripScrollsLongChoiceSetAndHeadingBoundsCount() {
        compose.setContent {
            LauncherTheme {
                Column {
                    PageHeading("A very long collection heading that remains bounded", "123 items")
                    FilterStrip((1..12).map { "Platform $it" }, 0, {})
                }
            }
        }
        compose.onNodeWithText("123 items").assertIsDisplayed()
        compose.onNodeWithText("Platform 1").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Platform 12").assertIsDisplayed()
    }

    @Test
    fun keyedCollectionContentPreservesCallerStateAfterReorder() {
        compose.setContent { KeyedFixture() }
        compose.onNodeWithText("A: 0").performClick()
        compose.onNodeWithText("Reorder").performClick()
        compose.onNodeWithText("A: 1").assertIsDisplayed()
    }

    @Composable
    private fun KeyedFixture() {
        var values by remember { mutableStateOf(listOf("A", "B")) }
        val gridState = rememberLazyGridState()
        LauncherTheme {
            Column {
                LauncherButton("Reorder", { values = values.reversed() })
                CollectionGrid(values, 1, gridState, Modifier.height(240.dp), key = { it }) { value ->
                    var count by remember { mutableStateOf(0) }
                    LauncherButton("$value: $count", { count++ })
                }
                LauncherText("stable keys")
            }
        }
    }
}
