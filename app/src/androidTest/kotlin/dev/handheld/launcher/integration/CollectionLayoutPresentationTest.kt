package dev.handheld.launcher.integration

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.designsystem.contract.LocalControllerInput
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import dev.handheld.launcher.feature.collection.CollectionDestinationScreen
import dev.handheld.launcher.feature.collection.CatalogScrollIndicator
import dev.handheld.launcher.feature.collection.CollectionScreenCallbacks
import dev.handheld.launcher.feature.collection.CollectionUiState
import dev.handheld.launcher.feature.collection.FocusedControlAction
import dev.handheld.launcher.feature.collection.collectionFilterOptions
import dev.handheld.launcher.input.LocalPageNavigation
import dev.handheld.launcher.input.PageNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class CollectionLayoutPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun catalogRailStaysHiddenWhenAllRowsAreVisibleDespiteSmallScrollRange() {
        var columns by mutableIntStateOf(1)
        var count by mutableIntStateOf(2)
        lateinit var grid: LazyGridState
        var density = 1f
        compose.setContent {
            grid = rememberLazyGridState()
            density = LocalDensity.current.density
            LauncherTheme(reducedMotion = true) {
                Row(Modifier.size(340.dp, 206.dp)) {
                    CatalogScrollIndicator(grid, columns)
                    LazyVerticalGrid(GridCells.Fixed(columns), state = grid,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(count) { Box(Modifier.height(96.dp)) }
                    }
                }
            }
        }
        // Native list/grid measurements, including an incomplete final grid row.
        for ((columnCount, itemCount) in listOf(1 to 2, 3 to 5, 3 to 6)) {
            compose.runOnIdle { columns = columnCount; count = itemCount }
            compose.runOnIdle { runBlocking { grid.scrollToItem(0) } }
            compose.runOnIdle { assertTrue("Fixture must really have residual scrolling", grid.canScrollForward) }
            compose.onNodeWithTag("catalog-scroll-indicator").assertDoesNotExist()
            compose.runOnIdle { runBlocking { grid.scrollBy(8f * density) } }
            compose.runOnIdle {
                assertTrue(grid.canScrollBackward)
                assertTrue(grid.canScrollForward)
            }
            compose.onNodeWithTag("catalog-scroll-indicator").assertDoesNotExist()
            compose.runOnIdle { runBlocking { grid.scrollToItem(itemCount - 1) } }
            compose.onNodeWithTag("catalog-scroll-indicator").assertDoesNotExist()
            // A genuinely offscreen row brings the indicator back.
            compose.runOnIdle { count = itemCount + columnCount * 2 }
            compose.onNodeWithTag("catalog-scroll-indicator").assertIsDisplayed()
        }
    }

    @Test fun catalogPositionRailTracksListAndGridAndHidesForShortFilteredCatalogs() {
        val games = (0..1002).map { game(it, "gba") }
        var isList by mutableStateOf(false)
        var state by mutableStateOf(CollectionUiState(LauncherDestination.LIBRARY, items = games, allItems = games,
            selectedItemId = games.first().id, loading = false))
        compose.setContent {
            CompositionLocalProvider(LocalControllerInput provides false) {
                LauncherTheme(reducedMotion = true, referenceScale = 2f / 3f) {
                    CollectionDestinationScreen("Library", state, Modifier.size(820.dp, 350.dp), callbacks(),
                        restoreFocusRequest = 0, isList = isList)
                }
            }
        }
        for (list in listOf(false, true)) {
            compose.runOnIdle { isList = list }
            compose.onNodeWithTag("collection-grid").performScrollToIndex(0)
            fun position() = compose.onNodeWithTag("catalog-scroll-indicator").fetchSemanticsNode()
                .config[SemanticsProperties.ProgressBarRangeInfo].current
            compose.onNodeWithTag("catalog-scroll-indicator").assertIsDisplayed()
            assertTrue("The position rail sits at the left edge in both layouts",
                actualBounds("catalog-scroll-indicator").right < actualBounds("collection-grid").left)
            assertEquals(0f, position(), .001f)
            compose.onNodeWithTag("collection-grid").performScrollToIndex(500)
            assertTrue("The rail locates the middle in either layout", position() in .45f.. .6f)
            compose.onNodeWithTag("collection-grid").performScrollToIndex(games.lastIndex)
            assertEquals("The partial last row still reaches the rail end", 1f, position(), .001f)
            val rail = compose.onNodeWithTag("catalog-scroll-indicator").fetchSemanticsNode()
            assertTrue("Position is informational, not a controller/touch action", !rail.config.contains(SemanticsActions.OnClick))
            assertEquals(games.first().id, state.selectedItemId)
        }
        compose.runOnIdle { state = state.copy(items = games.take(1)) }
        compose.onNodeWithTag("catalog-scroll-indicator").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(items = emptyList()) }
        compose.onNodeWithTag("catalog-scroll-indicator").assertDoesNotExist()
    }

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
        for (percent in listOf(100, 110, 120, 130, 140, 150)) {
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
            val all = actualBounds("collection-filter-all")
            val group = actualBounds("collection-console-group")
            val more = actualBounds("collection-all-filters")
            val strip = actualBounds("collection-filter-strip")
            val previousHint = actualBounds("collection-filter-previous-hint")
            val nextHint = actualBounds("collection-filter-next-hint")
            assertTrue("$percent% fixed All stays before the bounded console group", all.right < group.left)
            assertTrue("$percent% All filters stays outside and after the group", group.right < more.left)
            assertEquals("$percent% fixed controls use equal compact gaps", group.left - all.right, more.left - group.right, 1f)
            for (part in listOf(group, strip, previousHint, nextHint, more)) {
                assertEquals("$percent% controls and hints share one baseline", all.center.y, part.center.y, 1f)
            }
            assertTrue(fits(strip, group) && fits(previousHint, group) && fits(nextHint, group))
            assertTrue(previousHint.right < strip.left && strip.right < nextHint.left)
            assertEquals("$percent% hint-to-cell spacing is symmetric", strip.left - previousHint.right,
                nextHint.left - strip.right, 1f)
            assertTrue("$percent% no stretched gap separates the hint and cells",
                strip.left - previousHint.right <= group.left - all.right + 1f)
            val visibleChips = compose.onAllNodes(hasAnyAncestor(hasTestTag("collection-filter-strip")) and hasClickAction())
                .fetchSemanticsNodes().map { node ->
                    Rect(node.positionInRoot.x, node.positionInRoot.y,
                        node.positionInRoot.x + node.size.width, node.positionInRoot.y + node.size.height)
                }.filter { fits(it, strip) }
            if (percent <= 120) assertEquals("$percent% shows three whole console categories", 3, visibleChips.size)
            else assertTrue("$percent% retains whole console choices within the narrower strip", visibleChips.size in 1..3)
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
        var focusedAction: FocusedControlAction? = null
        lateinit var focusManager: FocusManager
        compose.setContent {
            focusManager = LocalFocusManager.current
            CompositionLocalProvider(LocalPageNavigation provides { navigation = it }) {
                LauncherTheme(reducedMotion = true, referenceScale = 2f / 3f) {
                    CollectionDestinationScreen("Library", state, Modifier.size(820.dp, 350.dp),
                        callbacks(onSelect = { state = state.copy(selectedItemId = it) }, onOpen = { opened++ })
                            .copy(onFocusedAction = { focusedAction = it }),
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
            val action = requireNotNull(focusedAction)
            assertEquals("Controller Confirm keeps the row's launch target", games[2].id, action.itemId)
            requireNotNull(action.onActivate).invoke()
            assertEquals(1, opened)
        }
        compose.runOnIdle { assertTrue(requireNotNull(navigation).move(FocusDirection.Right)) }
        compose.onNodeWithContentDescription(games[2].title).assertIsFocused()
        compose.runOnIdle { assertTrue(requireNotNull(navigation).move(FocusDirection.Left)) }
        compose.onNodeWithContentDescription(games[2].title).assertIsFocused()
        compose.onNodeWithTag("collection-preview-actions").assertDoesNotExist()
        assertEquals(0, compose.onAllNodes(hasAnyAncestor(hasTestTag("collection-preview")) and hasClickAction()).fetchSemanticsNodes().size)
        // A fast touch can reach the click callback before controller-mode recomposition.
        compose.onNodeWithContentDescription(games[2].title).performTouchInput { click() }
        compose.runOnIdle { assertEquals("Native row clicks only select even with stale controller mode", 1, opened) }
        compose.onNodeWithTag("collection-layout").performClick()
        compose.onNodeWithContentDescription(games[2].title).assertIsFocused()
        compose.runOnIdle { assertTrue(!isList) }
    }

    @Test fun listPreviewAndRowActionFollowCatalogReplacement() {
        val games = (0..1).map { game(it, "gba") }
        var state by mutableStateOf(CollectionUiState(LauncherDestination.LIBRARY,
            items = listOf(games[0]), allItems = games, selectedItemId = games[0].id, loading = false))
        var focusedAction: FocusedControlAction? = null
        val opened = mutableListOf<ItemId>()
        compose.setContent {
            LauncherTheme(reducedMotion = true, referenceScale = 2f / 3f) {
                CollectionDestinationScreen("Library", state, Modifier.size(820.dp, 350.dp),
                    callbacks(onSelect = { state = state.copy(selectedItemId = it) }, onOpen = { opened += it })
                        .copy(onFocusedAction = { focusedAction = it }), isList = true)
            }
        }
        compose.onNodeWithContentDescription(games[0].title).assertIsFocused()
        compose.runOnIdle { state = state.copy(items = listOf(games[1]), selectedItemId = games[1].id) }
        compose.onNodeWithContentDescription(games[1].title).assertIsFocused()
        compose.onNode(hasText(games[1].title) and hasAnyAncestor(hasTestTag("collection-preview")), useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(games[1].id, requireNotNull(focusedAction).itemId)
            requireNotNull(focusedAction?.onActivate).invoke()
            assertEquals(listOf(games[1].id), opened)
        }
    }

    @Test fun listTouchSelectsReadOnlyPreviewAndPublishesFooterActionsAtLargeUiScales() {
        val games = (0..3).map { game(it, "gba") }
        var state by mutableStateOf(CollectionUiState(LauncherDestination.LIBRARY, items = games, allItems = games,
            selectedItemId = games.first().id, favorites = setOf(games[1].id), loading = false,
            emulatorLabels = games.associate { it.id to "RetroArch" }))
        var scale by mutableFloatStateOf(1.1f)
        var selectedAction: FocusedControlAction? = null
        val opened = mutableListOf<ItemId>()
        compose.setContent {
            Box(Modifier.size(820.dp, 350.dp).testTag("collection-fixture")) {
                val native = LocalDensity.current
                CompositionLocalProvider(LocalControllerInput provides false,
                    LocalDensity provides Density(native.density * scale, native.fontScale)) {
                    LauncherTheme(reducedMotion = true, referenceScale = (2f / 3f) / scale, uiScaleFactor = scale) {
                        CollectionDestinationScreen("Library", state, Modifier.fillMaxSize(),
                            callbacks(onSelect = { state = state.copy(selectedItemId = it) }, onOpen = { opened += it })
                                .copy(onFocusedAction = { selectedAction = it }), isList = true)
                    }
                }
            }
        }
        for (percent in listOf(110, 120)) {
            compose.runOnIdle { scale = percent / 100f }
            compose.onNodeWithContentDescription(games.first().title).performTouchInput { click() }
            compose.onNodeWithContentDescription("Not favorite").assertIsDisplayed()
            compose.onNodeWithContentDescription(games[1].title).performTouchInput { click() }
            compose.onNodeWithContentDescription(games[1].title).assertIsNotFocused().assertIsSelected()
            compose.onNode(hasText(games[1].title) and hasAnyAncestor(hasTestTag("collection-preview")), useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("Favorite").assertIsDisplayed()
            compose.onNodeWithTag("collection-preview-emulator").assertIsDisplayed()
            compose.onNode(hasText("RetroArch") and hasAnyAncestor(hasTestTag("collection-preview"))).assertIsDisplayed()
            compose.onNode(hasText("Platform") and hasAnyAncestor(hasTestTag("collection-preview"))).assertDoesNotExist()
            val preview = actualBounds("collection-preview")
            assertTrue("$percent% list and preview are separate panes", actualBounds("collection-grid").right < preview.left)
            assertTrue("$percent% preview fits", fits(preview, actualBounds("collection-fixture")))
            assertTrue("$percent% cover fits", fits(actualBounds("collection-preview-artwork"), preview))
            assertEquals("Preview has no actions", 0,
                compose.onAllNodes(hasAnyAncestor(hasTestTag("collection-preview")) and hasClickAction()).fetchSemanticsNodes().size)
            compose.runOnIdle {
                assertEquals("Touch only selects", (percent - 110) / 10, opened.size)
                assertEquals(games[1].id, requireNotNull(selectedAction).itemId)
                requireNotNull(selectedAction?.onActivate).invoke()
                assertEquals(games[1].id, opened.last())
            }
        }
    }

    @Test fun narrowListUsesFooterActionAndVeryShortListOpensDetailsOnTouch() {
        val games = (0..3).map { game(it, "gba") }
        var state by mutableStateOf(CollectionUiState(LauncherDestination.LIBRARY, items = games, allItems = games,
            selectedItemId = games.first().id, loading = false))
        var height by mutableStateOf(400.dp)
        var selectedAction: FocusedControlAction? = null
        val opened = mutableListOf<ItemId>()
        val details = mutableListOf<ItemId>()
        compose.setContent {
            CompositionLocalProvider(LocalControllerInput provides false) {
                LauncherTheme(reducedMotion = true, referenceScale = 2f / 3f) {
                    CollectionDestinationScreen("Library", state, Modifier.size(460.dp, height),
                        callbacks(onSelect = { state = state.copy(selectedItemId = it) }, onOpen = { opened += it })
                            .copy(onOpenDetails = { details += it }, onFocusedAction = { selectedAction = it }), isList = true)
                }
            }
        }
        compose.onNodeWithContentDescription(games[1].title).performTouchInput { click() }
        compose.onNodeWithTag("collection-preview-compact").assertIsDisplayed()
        compose.onNodeWithTag("collection-preview-open").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(opened.isEmpty()); assertTrue(details.isEmpty())
            requireNotNull(selectedAction?.onActivate).invoke()
            assertEquals(listOf(games[1].id), opened)
            height = 240.dp
        }
        compose.onNodeWithTag("collection-preview-compact").assertDoesNotExist()
        compose.onNodeWithContentDescription(games.first().title).performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf(games.first().id), details)
            assertEquals("The row never launches unexpectedly", listOf(games[1].id), opened)
        }
    }

    @Test fun gridSizeChangesArtworkAndColumnDensityWithoutChangingSelectionOrTypography() {
        val games = (0..23).map { game(it, "gba") }
        val selected = games[1].id
        var percent by mutableIntStateOf(100)
        var state by mutableStateOf(CollectionUiState(LauncherDestination.LIBRARY, items = games, allItems = games,
            selectedItemId = selected, loading = false))
        compose.setContent {
            CompositionLocalProvider(LocalControllerInput provides false) {
                LauncherTheme(reducedMotion = true, referenceScale = 2f / 3f) {
                    CollectionDestinationScreen("Library", state, Modifier.size(820.dp, 350.dp).testTag("collection-fixture"),
                        callbacks(onSelect = { state = state.copy(selectedItemId = it) }), gridSizePercent = percent)
                }
            }
        }
        data class Geometry(val columns: Int, val width: Float, val height: Float, val titleHeight: Int)
        fun geometry(): Geometry {
            compose.waitForIdle()
            val cards = compose.onAllNodes(hasAnyAncestor(hasTestTag("collection-grid")) and hasClickAction())
                .fetchSemanticsNodes()
            val first = cards.minBy { it.positionInRoot.y }
            val columns = cards.count { kotlin.math.abs(it.positionInRoot.y - first.positionInRoot.y) < 1f }
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(games.first().title, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Caption uses at most two text lines", layouts.single().lineCount in 1..2)
            compose.runOnIdle { assertEquals(selected, state.selectedItemId) }
            for (tag in listOf("collection-filter-all", "collection-all-filters", "collection-layout", "collection-sort")) {
                compose.onNodeWithTag(tag).assertIsDisplayed()
                assertTrue("Grid size does not clip header controls", fits(actualBounds(tag), actualBounds("collection-fixture")))
            }
            return Geometry(columns, first.size.width.toFloat(), first.size.height.toFloat(), layouts.single().size.height)
        }
        val normal = geometry()
        compose.runOnIdle { percent = 70 }
        val small = geometry()
        compose.runOnIdle { percent = 140 }
        val large = geometry()
        assertTrue("Smaller grid artwork fits more columns", small.columns > normal.columns && normal.columns > large.columns)
        assertTrue("Cell widths track grid density", small.width < normal.width && normal.width < large.width)
        assertTrue("Artwork grows while caption allocation stays fixed", small.height < normal.height && normal.height < large.height)
        assertEquals(normal.titleHeight, small.titleHeight)
        assertEquals(normal.titleHeight, large.titleHeight)
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
        assertEquals("The settled strip keeps three complete categories, including at the ends", 3, visible.size)
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
