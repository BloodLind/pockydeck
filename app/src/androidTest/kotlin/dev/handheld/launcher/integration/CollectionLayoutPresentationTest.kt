package dev.handheld.launcher.integration

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
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
import dev.handheld.launcher.feature.collection.collectionFilterOptions
import dev.handheld.launcher.input.LocalPageNavigation
import dev.handheld.launcher.input.PageNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionLayoutPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun scaledHeadersKeepWholeTargetsAndAlignFilterCenters() {
        val games = listOf("gba", "gb", "gbc", "nes", "snes", "n64", "psx", "psp").mapIndexed { index, platform ->
            game(index, platform)
        }
        var scale by mutableFloatStateOf(1f)
        var targetDensity = 1f
        compose.setContent {
            Box(Modifier.size(820.dp, 350.dp).testTag("collection-fixture")) {
                val native = LocalDensity.current
                targetDensity = native.density * scale
                CompositionLocalProvider(LocalDensity provides Density(targetDensity, native.fontScale)) {
                    LauncherTheme(reducedMotion = true, referenceScale = (2f / 3f) / scale, uiScaleFactor = scale) {
                        CollectionDestinationScreen("Library", CollectionUiState(LauncherDestination.LIBRARY,
                            items = games, allItems = games, loading = false), Modifier.fillMaxSize(),
                            callbacks(), restoreFocusRequest = 0)
                    }
                }
            }
        }
        for (percent in listOf(100, 110, 120)) {
            compose.runOnIdle { scale = percent / 100f }
            compose.waitForIdle()
            val page = actualBounds("collection-fixture")
            for (tag in listOf("collection-filter-all", "collection-all-filters", "collection-layout", "collection-sort")) {
                compose.onNodeWithTag(tag).assertIsDisplayed()
                val bounds = actualBounds(tag)
                assertTrue("$percent% $tag retains a complete 48dp touch allocation: $bounds",
                    bounds.width >= 48f * targetDensity - 1f && bounds.height >= 48f * targetDensity - 1f)
                assertTrue("$percent% $tag fits the page: $bounds inside $page", fits(bounds, page))
            }
            assertEquals("$percent% title and action centers align", actualBounds("collection-title").center.y,
                actualBounds("collection-sort").center.y, 1f)
            val strip = actualBounds("collection-filter-strip")
            val visibleChips = compose.onAllNodes(hasAnyAncestor(hasTestTag("collection-filter-strip")) and hasClickAction())
                .fetchSemanticsNodes().map { node ->
                    Rect(node.positionInRoot.x, node.positionInRoot.y,
                        node.positionInRoot.x + node.size.width, node.positionInRoot.y + node.size.height)
                }.filter { fits(it, strip) }
            assertTrue("$percent% leaves a whole category target in the strip", visibleChips.isNotEmpty())
            visibleChips.forEach {
                assertEquals("$percent% fixed All and category centers align", actualBounds("collection-filter-all").center.y, it.center.y, 1f)
            }
        }
    }

    @Test fun switchingToListKeepsSelectionAndControllerMovesByRowsWithoutOpening() {
        val games = (0..3).map { game(it, "gba") }
        var state by mutableStateOf(CollectionUiState(LauncherDestination.LIBRARY, items = games, allItems = games,
            selectedItemId = games[1].id, loading = false))
        var isList by mutableStateOf(false)
        var navigation: PageNavigation? = null
        var opened = 0
        compose.setContent {
            CompositionLocalProvider(LocalPageNavigation provides { navigation = it }) {
                LauncherTheme(reducedMotion = true, referenceScale = 2f / 3f) {
                    CollectionDestinationScreen("Library", state, Modifier.size(820.dp, 350.dp),
                        callbacks(onSelect = { state = state.copy(selectedItemId = it) }, onOpen = { opened++ }),
                        isList = isList, onLayoutChange = { isList = it })
                }
            }
        }
        compose.onNodeWithContentDescription(games[1].title).assertIsFocused()
        compose.onNodeWithTag("collection-layout").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("collection-layout").performClick()
        compose.onNodeWithContentDescription(games[1].title).assertIsFocused()
        val first = compose.onNodeWithContentDescription(games[0].title).fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithContentDescription(games[1].title).fetchSemanticsNode().boundsInRoot
        assertTrue("List items occupy separate rows", second.top >= first.bottom)
        assertEquals("List rows use the same full width", first.width, second.width, 1f)
        compose.runOnIdle { assertTrue(requireNotNull(navigation).move(FocusDirection.Down)) }
        compose.onNodeWithContentDescription(games[2].title).assertIsFocused()
        compose.runOnIdle {
            assertEquals(games[2].id, state.selectedItemId)
            assertEquals("Layout and navigation never activate a game", 0, opened)
        }
        compose.onNodeWithTag("collection-layout").performClick()
        compose.onNodeWithContentDescription(games[2].title).assertIsFocused()
        compose.runOnIdle { assertTrue(!isList) }
    }

    @Test fun laterUnequalFiltersAndTouchScrollingSettleOnWholeCellsAtLargeUiScales() {
        val games = buildList {
            listOf("gba" to 12, "psp" to 3, "gb" to 2, "psx" to 1,
                "mastersystem" to 542, "snes" to 25, "wii" to 4).forEach { (platform, count) ->
                repeat(count) { add(game(size, platform)) }
            }
        }
        var state by mutableStateOf(CollectionUiState(LauncherDestination.LIBRARY,
            items = games.filter { it.platformId == "psx" }, allItems = games, filter = "console:psx", loading = false))
        var scale by mutableFloatStateOf(1.1f)
        compose.setContent {
            Box(Modifier.size(820.dp, 350.dp)) {
                val native = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(native.density * scale, native.fontScale)) {
                    LauncherTheme(reducedMotion = true, referenceScale = (2f / 3f) / scale, uiScaleFactor = scale) {
                        CollectionDestinationScreen("Library", state, Modifier.fillMaxSize(), callbacks(),
                            restoreFocusRequest = 0, isList = true)
                    }
                }
            }
        }
        fun assertSelectedWhole(platform: String) {
            compose.waitForIdle()
            val visible = assertWholeSettledFilters(state)
            assertTrue("Selected $platform must be a complete visible cell", "collection-filter-console:$platform" in visible)
        }
        assertSelectedWhole("psx")
        for (platform in listOf("mastersystem", "wii")) {
            compose.runOnIdle { state = state.copy(filter = "console:$platform", items = games.filter { it.platformId == platform }) }
            assertSelectedWhole(platform)
        }
        // Resizing while the final category is selected must not reveal a clipped
        // predecessor when LazyRow clamps its scroll against the changed viewport.
        compose.runOnIdle { scale = 1.2f }
        assertSelectedWhole("wii")
        compose.runOnIdle { state = state.copy(filter = "console:psx", items = games.filter { it.platformId == "psx" }) }
        assertSelectedWhole("psx")
        val before = compose.onNodeWithTag("collection-filter-strip").fetchSemanticsNode()
            .config[SemanticsProperties.HorizontalScrollAxisRange].value()
        compose.onNodeWithTag("collection-filter-strip").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertWholeSettledFilters(state)
        val after = compose.onNodeWithTag("collection-filter-strip").fetchSemanticsNode()
            .config[SemanticsProperties.HorizontalScrollAxisRange].value()
        assertTrue("Touch can browse later categories", after > before)
        compose.runOnIdle { assertEquals("Touch browsing does not apply a filter", "console:psx", state.filter) }
    }

    private fun assertWholeSettledFilters(state: CollectionUiState): List<String> {
        val viewport = actualBounds("collection-filter-strip")
        val labels = collectionFilterOptions(state).associate { "collection-filter-${it.key}" to it.label }
        val visible = compose.onAllNodes(hasAnyAncestor(hasTestTag("collection-filter-strip")) and hasClickAction())
            .fetchSemanticsNodes().mapNotNull { node ->
                val bounds = Rect(node.positionInRoot.x, node.positionInRoot.y,
                    node.positionInRoot.x + node.size.width, node.positionInRoot.y + node.size.height)
                if (bounds.width <= 0f || bounds.height <= 0f || bounds.right <= viewport.left || bounds.left >= viewport.right) null
                else {
                    val tag = node.config[SemanticsProperties.TestTag]
                    // Include partially visible targets in this assertion: filtering
                    // them out would hide precisely the clipped-neighbor regression.
                    assertTrue("Every settled visible cell is complete: $tag $bounds inside $viewport", fits(bounds, viewport))
                    tag
                }
            }
        assertTrue("The settled strip has a complete category", visible.isNotEmpty())
        for (tag in visible) {
            val label = requireNotNull(labels[tag])
            val text = compose.onNodeWithText(label, useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertEquals("The complete count stays on one line: $label", 1, layout.lineCount)
            assertTrue("The settled label is not ellipsized: $label", !layout.isLineEllipsized(0))
            assertEquals(label.length, layout.getLineEnd(0, visibleEnd = true))
        }
        return visible
    }

    private fun actualBounds(tag: String): Rect {
        val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
        return Rect(node.positionInRoot.x, node.positionInRoot.y,
            node.positionInRoot.x + node.size.width, node.positionInRoot.y + node.size.height)
    }

    private fun fits(child: Rect, parent: Rect) = child.left >= parent.left - 1f && child.top >= parent.top - 1f &&
        child.right <= parent.right + 1f && child.bottom <= parent.bottom + 1f

    private fun game(index: Int, platform: String) = LibraryItem.RomGame(ItemId("layout:$index"), "Collection game $index",
        CatalogSourceId("fixture:layout"), Availability.Available, setOf(SupportedItemAction.OPEN), platform, platform)

    private fun callbacks(onSelect: (ItemId) -> Unit = {}, onOpen: (ItemId) -> Unit = {}) = CollectionScreenCallbacks(
        onSelect, onOpen, onOpenDetails = {}, onFavorite = { _, _ -> }, onFilter = {}, onToggleSort = {},
        onRememberAnchor = { _, _ -> }, onRetry = {}, onOpenSystemAction = {}, onOpenFilters = {},
    )
}
