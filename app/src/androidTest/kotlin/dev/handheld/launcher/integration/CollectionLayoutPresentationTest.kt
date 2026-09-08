package dev.handheld.launcher.integration

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
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
        var focusedAction: FocusedControlAction? = null
        compose.setContent {
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
        compose.onNodeWithTag("collection-preview-open").assertIsFocused()
        compose.runOnIdle { assertTrue(requireNotNull(navigation).move(FocusDirection.Left)) }
        compose.onNodeWithContentDescription(games[2].title).assertIsFocused()
        compose.onNodeWithTag("collection-layout").performClick()
        compose.onNodeWithContentDescription(games[2].title).assertIsFocused()
        compose.runOnIdle { assertTrue(!isList) }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test fun focusedPreviewActionsFollowCatalogReplacementWithoutNeedingANewFocusEvent() {
        val games = (0..2).map { game(it, "gba") }
        var state by mutableStateOf(CollectionUiState(LauncherDestination.LIBRARY,
            items = listOf(games[0]), allItems = listOf(games[0]), selectedItemId = games[0].id, loading = false))
        val opened = mutableListOf<ItemId>()
        val details = mutableListOf<ItemId>()
        var focusedAction: FocusedControlAction? = null
        lateinit var inputMode: InputModeManager
        compose.setContent {
            inputMode = LocalInputModeManager.current
            LauncherTheme(reducedMotion = true, referenceScale = 2f / 3f) {
                CollectionDestinationScreen("Library", state, Modifier.size(820.dp, 350.dp),
                    callbacks(onSelect = { state = state.copy(selectedItemId = it) }, onOpen = { opened += it })
                        .copy(onOpenDetails = { details += it }, onFocusedAction = { focusedAction = it }),
                    isList = true, restoreFocusRequest = 0)
            }
        }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithTag("collection-preview-open").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("collection-preview-open").assertIsFocused()
        lateinit var capturedOpen: () -> Unit
        compose.runOnIdle {
            capturedOpen = requireNotNull(requireNotNull(focusedAction).onActivate)
            state = state.copy(items = listOf(games[1]), allItems = listOf(games[1]), selectedItemId = games[1].id)
        }
        compose.onNodeWithTag("collection-preview-open").assertIsFocused()
        compose.runOnIdle {
            assertEquals("Root Details/menu also follow the visible item", games[1].id, requireNotNull(focusedAction).itemId)
            capturedOpen()
            assertEquals(listOf(games[1].id), opened)
        }
        compose.onNodeWithTag("collection-preview-details").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("collection-preview-details").assertIsFocused()
        lateinit var capturedDetails: () -> Unit
        compose.runOnIdle {
            capturedDetails = requireNotNull(requireNotNull(focusedAction).onActivate)
            state = state.copy(items = listOf(games[2]), allItems = listOf(games[2]), selectedItemId = games[2].id)
        }
        compose.onNodeWithTag("collection-preview-details").assertIsFocused()
        compose.runOnIdle {
            assertEquals(games[2].id, requireNotNull(focusedAction).itemId)
            assertEquals("Details", requireNotNull(focusedAction).descriptor.label)
            capturedDetails()
            assertEquals(listOf(games[2].id), details)
        }
    }

    @Test fun listTouchOnlySelectsPreviewAndExplicitOpenUsesTheSelectedItemAtLargeUiScales() {
        val games = (0..3).map { game(it, "gba") }
        var state by mutableStateOf(CollectionUiState(LauncherDestination.LIBRARY, items = games, allItems = games,
            selectedItemId = games.first().id, favorites = setOf(games[1].id), loading = false))
        var scale by mutableFloatStateOf(1.1f)
        val opened = mutableListOf<ItemId>()
        val details = mutableListOf<ItemId>()
        compose.setContent {
            Box(Modifier.size(820.dp, 350.dp).testTag("collection-fixture")) {
                val native = LocalDensity.current
                CompositionLocalProvider(LocalControllerInput provides false,
                    LocalDensity provides Density(native.density * scale, native.fontScale)) {
                    LauncherTheme(reducedMotion = true, referenceScale = (2f / 3f) / scale, uiScaleFactor = scale) {
                        CollectionDestinationScreen("Library", state, Modifier.fillMaxSize(),
                            callbacks(onSelect = { state = state.copy(selectedItemId = it) }, onOpen = { opened += it })
                                .copy(onOpenDetails = { details += it }), isList = true)
                    }
                }
            }
        }
        for (percent in listOf(110, 120)) {
            compose.runOnIdle { scale = percent / 100f }
            // Even tapping the already-selected row is only a preview operation.
            compose.onNodeWithContentDescription(games.first().title).performTouchInput { click() }
            compose.onNodeWithContentDescription(games[1].title).performTouchInput { click() }
            compose.onNodeWithContentDescription(games[1].title).assertIsNotFocused()
            compose.onNode(hasText(games[1].title) and hasAnyAncestor(hasTestTag("collection-preview")),
                useUnmergedTree = true).assertIsDisplayed()
            compose.onNode(hasText("Favorite") and hasAnyAncestor(hasTestTag("collection-preview"))).assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(games[1].id, state.selectedItemId)
                assertEquals("$percent% row touch never launches", (percent - 110) / 10, opened.size)
            }
            val page = actualBounds("collection-fixture")
            val list = actualBounds("collection-grid")
            val preview = actualBounds("collection-preview")
            assertTrue("$percent% selectable list and preview are separate panes", list.right < preview.left)
            assertTrue("$percent% preview stays inside content", fits(preview, page))
            assertTrue("$percent% launch action stays visible", fits(actualBounds("collection-preview-open"), preview))
            compose.onNodeWithTag("collection-preview-open").performTouchInput { click() }
            compose.runOnIdle { assertEquals(games[1].id, opened.last()) }
            compose.onNode(hasText("Details") and hasAnyAncestor(hasTestTag("collection-preview"))).performClick()
            compose.runOnIdle { assertEquals(games[1].id, details.last()) }
        }
    }

    @Test fun narrowListKeepsAnExplicitOpenActionAndVeryShortListOpensDetailsOnTouch() {
        val games = (0..3).map { game(it, "gba") }
        var state by mutableStateOf(CollectionUiState(LauncherDestination.LIBRARY, items = games, allItems = games,
            selectedItemId = games.first().id, loading = false))
        var height by mutableStateOf(400.dp)
        val opened = mutableListOf<ItemId>()
        val details = mutableListOf<ItemId>()
        compose.setContent {
            CompositionLocalProvider(LocalControllerInput provides false) {
                LauncherTheme(reducedMotion = true, referenceScale = 2f / 3f) {
                    CollectionDestinationScreen("Library", state, Modifier.size(460.dp, height).testTag("collection-fixture"),
                        callbacks(onSelect = { state = state.copy(selectedItemId = it) }, onOpen = { opened += it })
                            .copy(onOpenDetails = { details += it }), isList = true)
                }
            }
        }
        compose.onNodeWithContentDescription(games[1].title).performTouchInput { click() }
        compose.onNodeWithTag("collection-preview-compact").assertIsDisplayed()
        compose.runOnIdle { assertTrue(opened.isEmpty()); assertTrue(details.isEmpty()) }
        assertTrue(fits(actualBounds("collection-preview-open"), actualBounds("collection-fixture")))
        compose.onNodeWithTag("collection-preview-open").performClick()
        compose.runOnIdle { assertEquals(listOf(games[1].id), opened); height = 240.dp }
        compose.onNodeWithTag("collection-preview-compact").assertDoesNotExist()
        compose.onNodeWithContentDescription(games.first().title).performTouchInput { click() }
        compose.runOnIdle {
            assertEquals("Tiny content keeps launch available through Details", listOf(games.first().id), details)
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
