package dev.handheld.launcher.integration

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import dev.handheld.launcher.feature.collection.CollectionDestinationScreen
import dev.handheld.launcher.feature.collection.CollectionScreenCallbacks
import dev.handheld.launcher.feature.collection.CollectionUiState
import dev.handheld.launcher.feature.search.SearchScreen
import dev.handheld.launcher.feature.search.SearchEditorActions
import dev.handheld.launcher.feature.search.SearchScreenTags
import dev.handheld.launcher.platform.system.SupportedSystemAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Production page/card composables with local fixtures; no external activities are launched. */
@RunWith(AndroidJUnit4::class)
class PageFocusPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun applyAndCancelEditingReturnToResultsAndRestoreTheEntryQuery() {
        val game = game("edit", "Sample game")
        var state by mutableStateOf(CollectionUiState(
            LauncherDestination.SEARCH, allItems = listOf(game), loading = false,
        ))
        var editorActions: SearchEditorActions? = null
        compose.setContent {
            LauncherTheme(referenceScale = 2f / 3f) {
                SearchScreen(state, Modifier.size(820.dp, 400.dp),
                    callbacks(onSelect = { state = state.copy(selectedItemId = it) }),
                    onQuery = { state = state.copy(query = it, items = if (it == "Sample") listOf(game) else emptyList()) },
                    systemActions = emptyList(), onEditorActionsChanged = { editorActions = it })
            }
        }
        compose.onNode(hasSetTextAction(), useUnmergedTree = true).performTextReplacement("Sample")
        compose.runOnIdle {
            val capturedActions = requireNotNull(editorActions)
            capturedActions.apply()
            capturedActions.cancel() // A queued old callback must not undo the completed apply.
            assertEquals("Sample", state.query)
        }
        compose.onNodeWithContentDescription(game.title).assertIsFocused()
        compose.onNodeWithTag(SearchScreenTags.Edit).assertIsDisplayed().performClick()
        compose.onNode(hasSetTextAction(), useUnmergedTree = true).assertIsFocused().performTextReplacement("unmatched draft")
        compose.onNodeWithText("No matches").assertIsDisplayed()
        compose.runOnIdle { requireNotNull(editorActions).cancel() }
        compose.runOnIdle { assertEquals("Sample", state.query) }
        compose.onNodeWithContentDescription(game.title).assertIsFocused()
        compose.onNodeWithTag(SearchScreenTags.Edit).assertIsDisplayed()
    }

    @Test fun resultScrollingCollapsesSearchAndUpwardScrollingRevealsIt() {
        val games = (1..24).map { game("scroll-$it", "Sample game $it") }
        compose.setContent {
            LauncherTheme(referenceScale = 2f / 3f) {
                SearchScreen(CollectionUiState(LauncherDestination.SEARCH, items = games, allItems = games,
                    selectedItemId = games.first().id, query = "Sample", loading = false),
                    Modifier.size(820.dp, 400.dp), callbacks(), onQuery = {}, systemActions = emptyList())
            }
        }
        compose.onNodeWithContentDescription(games.first().title).assertIsFocused()
        compose.onNodeWithTag(SearchScreenTags.Results).performTouchInput { swipeUp() }
        compose.onNodeWithTag(SearchScreenTags.Edit).assertIsDisplayed()
        compose.onNode(hasSetTextAction(), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(SearchScreenTags.Results).performTouchInput { swipeDown() }
        compose.onNode(hasSetTextAction(), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(SearchScreenTags.Edit).assertDoesNotExist()
    }

    @Test fun pageReselectionRestoresCardFromFilterAndTouchSelectsBeforeOpening() {
        val first = game("first", "First game")
        val second = game("second", "Second game")
        var state by mutableStateOf(CollectionUiState(
            LauncherDestination.LIBRARY, items = listOf(first, second), allItems = listOf(first, second),
            selectedItemId = second.id, loading = false,
        ))
        var request by mutableStateOf(1)
        val opened = mutableListOf<ItemId>()
        compose.setContent {
            LauncherTheme(referenceScale = 2f / 3f) {
                CollectionDestinationScreen("Library", state, Modifier.size(820.dp, 400.dp),
                    callbacks(onSelect = { state = state.copy(selectedItemId = it) }, onOpen = {
                        assertEquals("The touched item is selected before its launch callback", it, state.selectedItemId)
                        opened += it
                    }), restoreFocusRequest = request)
            }
        }
        compose.onNodeWithContentDescription(second.title).assertIsFocused()
        compose.onNodeWithContentDescription("All").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithContentDescription("All").assertIsFocused()
        compose.runOnIdle { request++ }
        compose.onNodeWithContentDescription(second.title).assertIsFocused()
        compose.onNodeWithContentDescription(first.title).performTouchInput { click() }
        compose.onNodeWithContentDescription(first.title).assertIsFocused()
        compose.runOnIdle { assertEquals(listOf(first.id), opened) }
    }

    @Test fun blankAndUnmatchedSearchStayEmptyAndNewMatchesDoNotStealTypingFocus() {
        val game = game("search", "Sample game")
        // The UI also guards against a stale catalog emission during the first blank frame.
        var state by mutableStateOf(CollectionUiState(
            LauncherDestination.SEARCH, items = listOf(game), allItems = listOf(game), loading = false,
        ))
        val system = SupportedSystemAction("fixture", "Wireless setting", "Sample connection controls", "fixture")
        compose.setContent {
            LauncherTheme(referenceScale = 2f / 3f) {
                SearchScreen(state, Modifier.size(820.dp, 400.dp), callbacks(), onQuery = { query ->
                    state = state.copy(query = query, items = if (query.trim().equals("sample", true)) listOf(game) else emptyList())
                }, systemActions = listOf(system))
            }
        }
        compose.onNodeWithText("Search your library").assertIsDisplayed()
        compose.onNodeWithContentDescription(game.title).assertDoesNotExist()
        compose.onNodeWithContentDescription(system.title).assertDoesNotExist()
        val editor = compose.onNode(hasSetTextAction(), useUnmergedTree = true)
        editor.assertIsFocused()
        editor.performTextReplacement("sample")
        compose.onNodeWithContentDescription(game.title).assertIsDisplayed()
        compose.onNodeWithContentDescription(system.title).assertIsDisplayed()
        editor.assertIsFocused()
        editor.performTextReplacement("no such result")
        compose.onNodeWithText("No matches").assertIsDisplayed()
        compose.onNodeWithContentDescription(game.title).assertDoesNotExist()
        compose.onNodeWithContentDescription(system.title).assertDoesNotExist()
        editor.assertIsFocused()
        editor.performTextReplacement("   ")
        compose.onNodeWithText("Search your library").assertIsDisplayed()
        compose.onNodeWithContentDescription(game.title).assertDoesNotExist()
        compose.onNodeWithContentDescription(system.title).assertDoesNotExist()
    }

    @Test fun systemResultsUseCatalogCardGeometryAndReceivePageActivationFocus() {
        val game = game("geometry", "Sample game")
        val system = SupportedSystemAction("fixture", "Wireless setting", "Sample connection controls", "fixture")
        var state by mutableStateOf(CollectionUiState(
            LauncherDestination.SEARCH, items = listOf(game), allItems = listOf(game),
            selectedItemId = game.id, query = "Sample", loading = false,
        ))
        var request by mutableStateOf(1)
        compose.setContent {
            LauncherTheme(referenceScale = 2f / 3f) {
                SearchScreen(state, Modifier.size(820.dp, 400.dp),
                    callbacks(onSelect = { state = state.copy(selectedItemId = it) }),
                    onQuery = { state = state.copy(query = it, items = emptyList()) },
                    systemActions = listOf(system), restoreFocusRequest = request)
            }
        }
        compose.onNodeWithContentDescription(game.title).assertIsFocused()
        val catalogBounds = compose.onNodeWithContentDescription(game.title).fetchSemanticsNode().boundsInRoot
        val systemBounds = compose.onNodeWithContentDescription(system.title).fetchSemanticsNode().boundsInRoot
        assertEquals(catalogBounds.width, systemBounds.width, 1f)
        assertEquals(catalogBounds.height, systemBounds.height, 1f)
        val editor = compose.onNode(hasSetTextAction(), useUnmergedTree = true)
        editor.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        editor.performTextReplacement("Wireless")
        editor.assertIsFocused()
        compose.runOnIdle { request++ }
        compose.onNodeWithContentDescription(system.title).assertIsFocused()
        compose.onNodeWithContentDescription(game.title).assertDoesNotExist()
    }

    private fun callbacks(onSelect: (ItemId) -> Unit = {}, onOpen: (ItemId) -> Unit = {}) = CollectionScreenCallbacks(
        onSelect = onSelect, onOpen = onOpen, onOpenDetails = {}, onFavorite = { _, _ -> }, onFilter = {},
        onToggleSort = {}, onRememberAnchor = { _, _ -> }, onRetry = {}, onOpenSystemAction = {},
    )

    private fun game(key: String, title: String) = LibraryItem.RomGame(
        ItemId("fixture:$key"), title, CatalogSourceId("fixture:source"), Availability.Available,
        setOf(SupportedItemAction.OPEN), "gba", "gba",
    )
}
