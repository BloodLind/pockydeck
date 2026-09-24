package dev.handheld.launcher.ui.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.*
import dev.handheld.launcher.ui.components.LibraryItemCard
import dev.handheld.launcher.ui.components.LibraryItemCardVariant
import org.junit.Rule
import org.junit.Test

class LastPlayedPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dotExplainsThePlatformAndAppearsOnlyOnHomeCardsWithHomeHistory() {
        val games = listOf(game("First PS2", "ps2"), game("Second PS2", "ps2"), game("GameCube game", "gamecube"))
        var history by mutableStateOf(listOf(SuccessfulOpenRecord(games[0].id, 1), SuccessfulOpenRecord(games[2].id, 2)))
        var variant by mutableStateOf(LibraryItemCardVariant.Home)
        var onHome by mutableStateOf(true)
        compose.setContent {
            LauncherTheme(reducedMotion = true) {
                CompositionLocalProvider(LocalLastPlayed provides if (onHome) lastPlayedByConsole(games, history) else emptySet()) {
                    Row { games.forEach { game ->
                        LibraryItemCard(game.toTileUiModel(), variant, selected = false,
                            modifier = Modifier.size(190.dp), onActivate = {})
                    } }
                }
            }
        }
        fun assertMarker(title: String, expected: Boolean) {
            val marker = hasContentDescription("Last played on", substring = true) and
                hasAnyAncestor(hasContentDescription(title))
            if (expected) compose.onNode(marker, useUnmergedTree = true).assertExists()
            else compose.onNode(marker, useUnmergedTree = true).assertDoesNotExist()
        }
        LibraryItemCardVariant.entries.forEach { value ->
            compose.runOnIdle { variant = value }
            assertMarker("First PS2", value == LibraryItemCardVariant.Home)
            assertMarker("Second PS2", false)
            assertMarker("GameCube game", value == LibraryItemCardVariant.Home)
        }
        compose.runOnIdle { variant = LibraryItemCardVariant.Home; history = history + SuccessfulOpenRecord(games[1].id, 3) }
        assertMarker("First PS2", false)
        assertMarker("Second PS2", true)
        assertMarker("GameCube game", true)
        compose.onNodeWithContentDescription("Last played on PS2", useUnmergedTree = true).assertExists()
        compose.runOnIdle { onHome = false }
        assertMarker("Second PS2", false)
        assertMarker("GameCube game", false)
    }

    private fun game(title: String, platform: String) = LibraryItem.RomGame(ItemId(title), title,
        CatalogSourceId("test"), Availability.Available, setOf(SupportedItemAction.OPEN), platformId = platform, format = "iso")
}
