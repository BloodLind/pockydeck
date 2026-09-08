package dev.handheld.launcher.rom

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import dev.handheld.launcher.feature.collection.CollectionDestinationScreen
import dev.handheld.launcher.feature.collection.CollectionScreenCallbacks
import dev.handheld.launcher.feature.collection.CollectionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RomCollectionPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun mixedCollectionReservesVisibleRomTitleAndConsoleAtLargerTextSize() {
        val app = LibraryItem.AndroidApp(
            CurrentUserAndroidComponentId("fixture.app", "fixture.app.Main"), "Sample app", LibraryCategory.OTHER,
            Availability.Available, setOf(SupportedItemAction.OPEN),
        )
        val game = LibraryItem.RomGame(
            ItemId("rom:visible-title"), "Sample GBA game", CatalogSourceId("fixture:roms"),
            Availability.Available, setOf(SupportedItemAction.OPEN), "gba", "gba",
        )
        var opened: ItemId? = null
        compose.setContent {
            val nativeDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(nativeDensity.density, 1.3f)) {
                LauncherTheme(referenceScale = 2f / 3f) {
                    CollectionDestinationScreen(
                        "Library",
                        CollectionUiState(LauncherDestination.LIBRARY, items = listOf(app, game), allItems = listOf(app, game), loading = false),
                        Modifier.size(820.dp, 350.dp),
                        CollectionScreenCallbacks(
                            onSelect = {}, onOpen = { opened = it }, onOpenDetails = {}, onFavorite = { _, _ -> },
                            onFilter = {}, onToggleSort = {}, onRememberAnchor = { _, _ -> }, onRetry = {}, onOpenSystemAction = {},
                        ),
                        restoreFocusRequest = 0,
                    )
                }
            }
        }
        compose.onNodeWithText("Sample app", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Sample GBA game", useUnmergedTree = true).assertIsDisplayed()
        // This subtitle belongs to the ROM card; the console filter remains a separate button.
        val subtitle = compose.onNode(
            hasText("GBA") and hasAnyAncestor(hasContentDescription("Sample GBA game")),
            useUnmergedTree = true,
        ).assertIsDisplayed().fetchSemanticsNode()
        compose.onNodeWithContentDescription("Sample GBA game").assertIsDisplayed().performClick()
        assertEquals(game.id, opened)
        val title = compose.onNodeWithText("Sample GBA game", useUnmergedTree = true).fetchSemanticsNode()
        val card = compose.onNodeWithContentDescription("Sample GBA game").fetchSemanticsNode()
        assertTrue(title.boundsInRoot.height > 0f && card.boundsInRoot.contains(title.boundsInRoot.center))
        assertTrue(subtitle.boundsInRoot.height > 0f && subtitle.boundsInRoot.bottom <= card.boundsInRoot.bottom)
    }
}
