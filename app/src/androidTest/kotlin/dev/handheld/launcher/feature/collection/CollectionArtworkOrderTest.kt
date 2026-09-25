package dev.handheld.launcher.feature.collection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.ui.artwork.ArtworkLoadOrder
import dev.handheld.launcher.ui.artwork.LocalArtworkLoadOrder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CollectionArtworkOrderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun scrollingResequencesCoversWithoutRecomposingThePageOrGridHost() {
        var pageCompositions = 0
        var gridCompositions = 0
        var currentOrder: ArtworkLoadOrder? = null
        compose.setContent {
            val grid = rememberLazyGridState()
            SideEffect { pageCompositions++ }
            Column {
                CollectionArtworkOrder(grid, enabled = true) {
                    SideEffect { gridCompositions++ }
                    LazyVerticalGrid(GridCells.Fixed(2), Modifier.size(200.dp).testTag("grid"), state = grid) {
                        items(100, key = { "row:$it" }) {
                            val order = LocalArtworkLoadOrder.current
                            SideEffect { currentOrder = order }
                            Box(Modifier.size(100.dp))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val pageCount = pageCompositions
        val gridCount = gridCompositions
        val previousOrder = currentOrder
        for (index in listOf(20, 40, 10, 0)) {
            compose.onNodeWithTag("grid").performScrollToIndex(index)
            compose.runOnIdle {
                assertNotNull(currentOrder)
                assertTrue(currentOrder!!.allowed(ItemId("row:$index")))
                assertFalse("Later covers must still wait their turn", currentOrder!!.allowed(ItemId("row:${index + 1}")))
                assertEquals("Scrolling must not recompose page chrome", pageCount, pageCompositions)
                assertEquals("Only artwork consumers need the new queue", gridCount, gridCompositions)
            }
        }
        assertNotSame(previousOrder, currentOrder)
    }
}
