package dev.handheld.launcher.feature.search

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.designsystem.contract.LocalControllerInput
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import dev.handheld.launcher.feature.collection.CollectionScreenCallbacks
import dev.handheld.launcher.feature.collection.CollectionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Page-only native focus coverage; no launcher repositories or existing user data are changed. */
@OptIn(ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
class SearchFocusRestorationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyFilterRestoresItsVisibleChipWithoutStartingAnEditor() = verifyEmptyFilter(400.dp, compact = false)

    @Test fun emptyFilterInACompactWindowRestoresEditWithoutOpeningIt() = verifyEmptyFilter(200.dp, compact = true)

    private fun verifyEmptyFilter(height: Dp, compact: Boolean) {
        val rom = LibraryItem.RomGame(ItemId("rom:sample"), "Sample game", CatalogSourceId("rom:source"),
            Availability.Available, setOf(SupportedItemAction.OPEN), "gba", "gba")
        var state by mutableStateOf(CollectionUiState(LauncherDestination.SEARCH, items = listOf(rom),
            allItems = listOf(rom), selectedItemId = rom.id, query = "Sample", loading = false))
        var activation by mutableStateOf(1)
        var queryRequest by mutableStateOf(0)
        var editor: SearchEditorActions? = null
        val keyboard = RecordingSearchKeyboard()
        val callbacks = CollectionScreenCallbacks(
            onSelect = { state = state.copy(selectedItemId = it) }, onOpen = {}, onOpenDetails = {},
            onFavorite = { _, _ -> }, onFilter = {}, onToggleSort = {}, onRememberAnchor = { _, _ -> },
            onRetry = {}, onOpenSystemAction = {},
        )
        compose.setContent {
            CompositionLocalProvider(LocalControllerInput provides true, LocalSoftwareKeyboardController provides keyboard) {
                LauncherTheme(reducedMotion = true, referenceScale = 2f / 3f) {
                    SearchScreen(state, Modifier.size(820.dp, height), callbacks, onQuery = {}, systemActions = emptyList(),
                        queryFocusRequest = queryRequest, restoreFocusRequest = activation,
                        onEditorActionsChanged = { editor = it })
                }
            }
        }
        compose.onNodeWithContentDescription(rom.title).assertIsFocused()
        compose.runOnIdle {
            state = state.copy(filter = "apps", items = emptyList(), selectedItemId = null)
            activation++
        }
        compose.onNodeWithTag(if (compact) SearchScreenTags.Edit else SearchScreenTags.filter("apps")).assertIsFocused()
        assertTrue(compose.onAllNodes(hasSetTextAction() and isFocused(), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        compose.runOnIdle {
            assertNull("Passive category changes must not open an edit session", editor)
            assertEquals("Passive focus must not request the keyboard", 0, keyboard.shows)
            queryRequest++ // The explicit X/Search action still starts editing.
        }
        compose.onNode(hasSetTextAction(), useUnmergedTree = true).assertIsFocused()
        compose.runOnIdle {
            assertNotNull(editor)
            assertTrue("Explicit editing requests the keyboard", keyboard.shows > 0)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private class RecordingSearchKeyboard : SoftwareKeyboardController {
    var shows = 0
    override fun show() { shows++ }
    override fun hide() = Unit
}
