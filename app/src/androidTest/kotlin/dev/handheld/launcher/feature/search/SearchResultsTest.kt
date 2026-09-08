package dev.handheld.launcher.feature.search

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Android Compose remember invalidation, including unchanged ROM result lists. */
@RunWith(AndroidJUnit4::class)
class SearchResultsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun publishedQueryCompletionRestoresTheSameRomRowsIncludingWhitespaceEdits() {
        val rom = LibraryItem.RomGame(ItemId("rom:mario"), "Super Mario Advance", CatalogSourceId("rom:source"),
            Availability.Available, setOf(SupportedItemAction.OPEN), "gba", "gba")
        val rows = listOf<LibraryItem>(rom)
        val editor = mutableStateOf("Mari")
        val published = mutableStateOf("Mari")
        var rendered = emptyList<SearchResult>()
        compose.setContent {
            val result = rememberSearchResults(editor.value, published.value, rows, "all", emptyList())
            SideEffect { rendered = result }
        }
        compose.runOnIdle {
            assertEquals(listOf(rom.id), rendered.filterIsInstance<SearchResult.Catalog>().map { it.item.id })
            editor.value = "Mario"
        }
        compose.runOnIdle {
            assertTrue("Pending publication must hide preceding-query rows", rendered.isEmpty())
            published.value = "Mario"
        }
        compose.runOnIdle {
            assertEquals("Publication itself must invalidate cached results", 1, rendered.size)
            editor.value = "Mario "
        }
        compose.runOnIdle {
            assertTrue(rendered.isEmpty())
            published.value = "Mario "
        }
        compose.runOnIdle {
            assertEquals("Trailing spaces must not leave matching ROMs hidden", rom,
                (rendered.single() as SearchResult.Catalog).item)
            editor.value = "   "
            published.value = "   "
        }
        compose.runOnIdle { assertTrue("Blank Search must hide the preceding ROM list", rendered.isEmpty()) }
    }
}
