package dev.handheld.launcher.feature.home

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.designsystem.contract.LocalControllerInput
import dev.handheld.launcher.core.designsystem.foundation.ShellMetrics
import dev.handheld.launcher.core.designsystem.foundation.ShellMetricsInput
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader
import dev.handheld.launcher.ui.presentation.toTileUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Composable-only fixture: no catalog, launch, preferences, or artwork repositories are touched. */
@RunWith(AndroidJUnit4::class)
class HomeScreenResetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun touchModeReturnsTheScrolledRowToItsFirstCardAndFollowsTheNewRecentFirstCard() {
        val items = (1..12).map { index ->
            LibraryItem.RomGame(
                ItemId("home-reset:$index"), "Home game $index", CatalogSourceId("fixture"),
                Availability.Available, setOf(SupportedItemAction.OPEN), "gba", "gba",
            ).toTileUiModel()
        }
        var state by mutableStateOf(HomeUiState(
            items = items, selectedItemId = items[6].itemId, firstVisibleItemId = items[6].itemId,
            firstVisibleOffsetPx = 17, loading = false,
        ))
        var reportedViewport: Pair<ItemId?, Int>? = null
        val metrics = ShellMetrics.calculate(ShellMetricsInput(800, 450, 1f))
        val icons = AndroidIconLoader(ApplicationProvider.getApplicationContext())
        compose.setContent {
            CompositionLocalProvider(LocalControllerInput provides false) {
                LauncherTheme(reducedMotion = true, referenceScale = metrics.referenceScale) {
                    HomeScreen(state, metrics, icons, Modifier.size(800.dp, 450.dp),
                        onSelect = { state = state.copy(selectedItemId = it) }, onActivate = {},
                        onOpenLibrary = {}, onOpenDetails = {}, onRefresh = {}, allowFocusRequest = false,
                        onViewportChanged = { id, offset ->
                            reportedViewport = id to offset
                            state = state.copy(firstVisibleItemId = id, firstVisibleOffsetPx = offset,
                                followingStart = state.followingStart && id == state.items.first().itemId && offset == 0)
                        })
                }
            }
        }
        compose.runOnIdle { assertEquals(items[6].itemId to 17, reportedViewport) }
        compose.onNodeWithTag("home-row").performScrollToIndex(7)
        compose.runOnIdle {
            assertEquals(items[7].itemId, reportedViewport?.first)
            state = state.copy(selectedItemId = items.first().itemId, firstVisibleItemId = items.first().itemId,
                firstVisibleOffsetPx = 0, returnToStartSequence = 1, followingStart = true)
        }
        compose.onNodeWithContentDescription(items.first().title).assertIsDisplayed().assertIsSelected()
        compose.runOnIdle {
            assertEquals(items.first().itemId to 0, reportedViewport)
            // Successful-open publication can finish after the Home return request.
            val reordered = listOf(items.last()) + items.dropLast(1)
            state = state.copy(items = reordered, selectedItemId = reordered.first().itemId,
                firstVisibleItemId = reordered.first().itemId)
        }
        compose.onNodeWithContentDescription(items.last().title).assertIsDisplayed().assertIsSelected()
        compose.runOnIdle { assertEquals(items.last().itemId to 0, reportedViewport) }
        assertTrue("A touch-mode reset must not move keyboard focus into a card",
            compose.onAllNodes(hasClickAction() and isFocused()).fetchSemanticsNodes().isEmpty())
    }
}
