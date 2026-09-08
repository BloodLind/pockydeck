package dev.handheld.launcher.integration

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.handheld.launcher.MainActivity
import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.core.domain.model.ConfirmBackMapping
import dev.handheld.launcher.core.domain.model.ControllerFaceButton
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.di.LauncherApplication
import dev.handheld.launcher.feature.collection.CollectionViewModel
import dev.handheld.launcher.feature.collection.collectionFilterKeys
import dev.handheld.launcher.feature.search.SearchScreenTags
import dev.handheld.launcher.shell.LauncherShellTags
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in real-app coverage: run this class explicitly against an installed, populated launcher.
 * No test fixtures replace repositories, no app data is reset, and no card is activated.
 * Only normal UI navigation, text editing and its usual saved snapshots are changed.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityInputDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val container get() = (compose.activity.application as LauncherApplication).appContainer
    private lateinit var library: CollectionViewModel
    private lateinit var search: CollectionViewModel
    private lateinit var mapping: ConfirmBackMapping
    private var originalFilter: String? = null
    private var originalSearch: String? = null
    private var searchEdited = false
    private val heldKeys = mutableMapOf<Int, Long>()
    private var joystickUsed = false
    private var originalWindowCallback: Window.Callback? = null
    private val inputRecords = CopyOnWriteArrayList<String>()
    private val deviceId by lazy {
        InputDevice.getDeviceIds().firstOrNull { id ->
            InputDevice.getDevice(id)?.let {
                it.supportsSource(InputDevice.SOURCE_GAMEPAD) || it.supportsSource(InputDevice.SOURCE_JOYSTICK)
            } == true
        } ?: KeyCharacterMap.VIRTUAL_KEYBOARD
    }

    @Before fun openExistingLibrary() {
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodes(hasTestTag(LauncherShellTags.Root)).fetchSemanticsNodes().isNotEmpty()
        }
        // Fetch the Activity's already-created models; observe only, never mutate them directly.
        compose.runOnIdle {
            val original = compose.activity.window.callback
            originalWindowCallback = original
            // Transparent receipt diagnostics: every event still reaches the original Activity.
            compose.activity.window.callback = object : Window.Callback by original {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    val consumed = original.dispatchKeyEvent(event)
                    recordInput("received key=${event.keyCode} action=${event.action} device=${event.deviceId} source=${event.source} repeat=${event.repeatCount} handled=$consumed")
                    return consumed
                }

                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    val consumed = original.dispatchTouchEvent(event)
                    if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP) {
                        recordInput("received touch action=${event.actionMasked} x=${event.x} y=${event.y} handled=$consumed")
                    }
                    return consumed
                }

                override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
                    val consumed = original.dispatchGenericMotionEvent(event)
                    if (event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
                        recordInput("received joystick device=${event.deviceId} L=${event.getAxisValue(MotionEvent.AXIS_LTRIGGER)} R=${event.getAxisValue(MotionEvent.AXIS_RTRIGGER)} handled=$consumed")
                    }
                    return consumed
                }
            }
            library = ViewModelProvider(compose.activity, container.collectionViewModelFactory(LauncherDestination.LIBRARY))
                .get("collection.library", CollectionViewModel::class.java)
            search = ViewModelProvider(compose.activity, container.collectionViewModelFactory(LauncherDestination.SEARCH))
                .get("collection.search", CollectionViewModel::class.java)
        }
        compose.waitUntil(TIMEOUT_MS) { !library.state.value.loading && !search.state.value.loading }
        mapping = runBlocking { container.controllerPreferenceRepository.confirmBackMapping.first() }
        originalFilter = library.state.value.filter
        originalSearch = search.state.value.query
        tapTag(LauncherShellTags.destination(LauncherDestination.LIBRARY))
        waitForImeHidden()
        cycleToFilter("all")
        compose.waitUntil(TIMEOUT_MS) { !library.state.value.searching && library.state.value.items.size >= 12 }
        compose.onNodeWithTag(GRID).assertExists()
    }

    @After fun releaseInputAndReturnToLibrary() {
      try {
        try {
            heldKeys.keys.toList().forEach(::keyUp)
            if (joystickUsed) triggers()
            if (searchEdited && originalSearch != null) {
                openSearchEditorByTouch()
                compose.onNode(hasSetTextAction(), useUnmergedTree = true).performTextReplacement(originalSearch!!)
                applySearch()
            }
        } finally {
            tapTag(LauncherShellTags.destination(LauncherDestination.LIBRARY))
            waitForImeHidden()
            originalFilter?.let { if (it in filterKeys()) cycleToFilter(it) }
        }
      } finally {
          originalWindowCallback?.let { original ->
              instrumentation.runOnMainSync { compose.activity.window.callback = original }
          }
      }
    }

    @Test fun rightContinuesIntoNextRowAndLeftReturnsToPreviousRow() {
        press(KeyEvent.KEYCODE_DPAD_RIGHT) // The first controller input restores the selected card.
        waitForFocusedCard()
        // Release the old card's focus before changing the viewport. Otherwise its
        // bring-into-view request can move the grid back to its previous distant row.
        val sortLabel = if (library.state.value.sort == "recent") "Recent" else "Title"
        compose.onNode(hasContentDescription("Sort: $sortLabel"))
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNode(hasContentDescription("Sort: $sortLabel")).assertIsFocused()
        compose.onNodeWithTag(GRID).performScrollToIndex(0)
        compose.waitForIdle()
        val visible = gridCards().sortedWith(compareBy({ it.boundsInRoot.top }, { it.boundsInRoot.left }))
        assertTrue("A populated grid must expose at least two cards", visible.size >= 2)
        val top = visible.first().boundsInRoot.top
        val firstRow = visible.filter { kotlin.math.abs(it.boundsInRoot.top - top) < 4f }
            .sortedBy { it.boundsInRoot.left }
        assertTrue("The test requires a multicolumn Library grid", firstRow.size >= 2)
        val first = firstRow.first()
        compose.onNode(SemanticsMatcher("first card ${first.id}") { it.id == first.id })
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        val before = library.state.value
        waitWithDiagnostics("The first catalog card must receive initial grid focus") {
            library.state.value.selectedItemId == before.items.first().id && focusedGridCards().size == 1
        }
        val edgeIndex = firstRow.size - 1
        // Actual Android key events traverse the row before crossing its boundary.
        for (index in 1..edgeIndex) {
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            waitWithDiagnostics("Right should reach first-row column $index") {
                library.state.value.selectedItemId == before.items[index].id && focusedGridCards().size == 1
            }
        }
        val edgeId = before.items[edgeIndex].id
        val nextId = before.items[edgeIndex + 1].id

        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.waitUntil(TIMEOUT_MS) { library.state.value.selectedItemId == nextId && focusedGridCards().size == 1 }
        assertTrue("The next row's first card must be selected and focused", focusedGridCards().single().config[SemanticsProperties.Selected])

        press(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.waitUntil(TIMEOUT_MS) { library.state.value.selectedItemId == edgeId && focusedGridCards().size == 1 }
        assertTrue("Left must return to the previous row's last card", focusedGridCards().single().config[SemanticsProperties.Selected])
    }

    @Test fun touchingTheDockClearsCardFocusAndControllerInputRestoresItsHighlight() {
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        waitForFocusedCard()
        val selected = library.state.value.selectedItemId

        // A dock tap exercises MainActivity.dispatchTouchEvent without ever opening a game.
        tapTag(LauncherShellTags.destination(LauncherDestination.LIBRARY))
        compose.waitUntil(TIMEOUT_MS) { focusedGridCards().isEmpty() }
        assertEquals(selected, library.state.value.selectedItemId)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        waitForFocusedCard()
        assertEquals("The first controller input reacquires the selected card", selected, library.state.value.selectedItemId)
        assertTrue(focusedGridCards().single().config[SemanticsProperties.Selected])
    }

    @Test fun searchSupportsMappedApplyCancelAndTouchCancelWithoutOpeningResults() {
        val query = library.state.value.items.first().title.take(18)
        searchEdited = true
        press(KeyEvent.KEYCODE_BUTTON_X)
        waitForEditor()
        compose.onNode(hasSetTextAction(), useUnmergedTree = true).performTextReplacement(query)
        compose.waitUntil(TIMEOUT_MS) { search.state.value.query == query }
        applySearch()
        assertEquals(query, search.state.value.query)
        compose.onNodeWithTag(LauncherShellTags.destination(LauncherDestination.SEARCH)).assertIsSelected()

        openSearchEditorByTouch()
        compose.onNode(hasSetTextAction(), useUnmergedTree = true).performTextReplacement("$query unmatched draft 42")
        compose.onNode(hasSetTextAction(), useUnmergedTree = true).assertIsFocused()
        press(faceKey(mapping.back))
        waitForClosedEditor()
        compose.waitUntil(TIMEOUT_MS) { search.state.value.query == query }

        openSearchEditorByTouch()
        compose.onNode(hasSetTextAction(), useUnmergedTree = true).performTextReplacement("$query second draft 73")
        tapTag(LauncherShellTags.footerAction(SemanticInputAction.BACK), expectedImeVisible = true)
        waitForClosedEditor()
        compose.waitUntil(TIMEOUT_MS) { search.state.value.query == query }
        compose.onNodeWithTag(LauncherShellTags.destination(LauncherDestination.SEARCH)).assertIsSelected()
    }

    @Test fun slowTriggerReportsMergeAndAHeldTriggerAcceleratesThenStopsOnRelease() {
        assertTrue("Trigger cycling requires at least two detected console filters", filterKeys().size >= 2)
        val changes = CopyOnWriteArrayList<FilterChange>()
        val observer = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        compose.runOnIdle {
            observer.launch {
                library.state.map { it.filter }.distinctUntilChanged().drop(1).collect {
                    changes += FilterChange(it, SystemClock.uptimeMillis())
                }
            }
        }
        try {
            // Both report orders exceed the old 65ms dedup window but finish before hold repeat.
            checkSlowSqueeze(changes, keyFirst = true, right = true)
            checkSlowSqueeze(changes, keyFirst = false, right = false)
            changes.clear()
            val began = SystemClock.uptimeMillis()
            try {
                keyDown(KeyEvent.KEYCODE_BUTTON_R2)
                SystemClock.sleep(3_400)
            } finally { keyUp(KeyEvent.KEYCODE_BUTTON_R2) }
            compose.waitForIdle()
            val times = changes.map { it.at - began }
            val early = gaps(times.filter { it in 450L..1_400L })
            val late = gaps(times.filter { it in 2_600L..3_350L })
            assertTrue("Need enough observed early hold steps: $times", early.size >= 4)
            assertTrue("Need enough observed accelerated hold steps: $times", late.size >= 5)
            assertTrue("Hold should accelerate; early=$early late=$late", median(late) < median(early) * .82)
            SystemClock.sleep(100)
            val releasedCount = changes.size
            SystemClock.sleep(500)
            assertEquals("No repeat may survive trigger release", releasedCount, changes.size)
        } finally {
            try {
                keyUp(KeyEvent.KEYCODE_BUTTON_L2)
                keyUp(KeyEvent.KEYCODE_BUTTON_R2)
                triggers()
            } finally { observer.cancel() }
        }
    }

    private fun checkSlowSqueeze(changes: CopyOnWriteArrayList<FilterChange>, keyFirst: Boolean, right: Boolean) {
        compose.waitForIdle()
        changes.clear()
        val keys = filterKeys()
        val before = keys.indexOf(library.state.value.filter)
        val expected = keys[Math.floorMod(before + if (right) 1 else -1, keys.size)]
        val key = if (right) KeyEvent.KEYCODE_BUTTON_R2 else KeyEvent.KEYCODE_BUTTON_L2
        val began = SystemClock.uptimeMillis()
        try {
            if (keyFirst) keyDown(key) else triggers(left = if (right) 0f else .8f, right = if (right) .8f else 0f)
            SystemClock.sleep(180)
            if (keyFirst) triggers(left = if (right) 0f else .8f, right = if (right) .8f else 0f) else keyDown(key)
            SystemClock.sleep(30)
        } finally {
            keyUp(key)
            triggers()
        }
        val elapsed = SystemClock.uptimeMillis() - began
        assertTrue("Injection took ${elapsed}ms; cannot isolate engagement from the 360ms hold threshold", elapsed < 340)
        compose.waitUntil(TIMEOUT_MS) { changes.isNotEmpty() }
        compose.waitForIdle()
        assertEquals("One physical squeeze must change the filter once: $changes", 1, changes.size)
        assertEquals(expected, changes.single().filter)
    }

    private fun filterKeys(): List<String> = library.state.value.let {
        collectionFilterKeys(LauncherDestination.LIBRARY, it.allItems, it.overrides, it.favorites)
    }

    private fun cycleToFilter(target: String) {
        repeat(filterKeys().size + 1) {
            val before = library.state.value.filter
            if (before == target) return
            press(KeyEvent.KEYCODE_BUTTON_R2)
            compose.waitUntil(TIMEOUT_MS) { library.state.value.filter != before }
        }
        assertEquals("Could not restore Library filter through controller input", target, library.state.value.filter)
    }

    private fun gridCards(): List<SemanticsNode> {
        val viewport = compose.onAllNodes(hasTestTag(GRID)).fetchSemanticsNodes().singleOrNull()?.boundsInRoot
            ?: return emptyList()
        return compose.onAllNodes(
            hasAnyAncestor(hasTestTag(GRID)) and hasClickAction() and
                SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
        ).fetchSemanticsNodes().filter { node ->
            val bounds = node.boundsInRoot
            bounds.width > 0f && bounds.height > 0f &&
                bounds.left < viewport.right && bounds.right > viewport.left &&
                bounds.top < viewport.bottom && bounds.bottom > viewport.top
        }
    }

    private fun focusedGridCards(): List<SemanticsNode> = gridCards().filter { it.config[SemanticsProperties.Focused] }

    private fun waitForFocusedCard() = waitWithDiagnostics("Expected exactly one focused Library card") { focusedGridCards().size == 1 }

    private fun waitForEditor() {
        waitWithDiagnostics("Expected the Search editor to hold focus") {
            compose.onAllNodes(hasSetTextAction() and isFocused(), useUnmergedTree = true).fetchSemanticsNodes().size == 1
        }
    }

    private fun waitForClosedEditor() {
        waitWithDiagnostics("Expected Search to finish editing and show Edit search") {
            compose.onAllNodes(hasTestTag(SearchScreenTags.Edit)).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        waitForImeHidden()
        waitWithDiagnostics("Expected the dock to return after the keyboard closes") {
            compose.onAllNodes(hasTestTag(LauncherShellTags.destination(LauncherDestination.SEARCH)))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForImeHidden() = compose.waitUntil(TIMEOUT_MS) {
        ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) != true
    }

    private fun openSearchEditorByTouch() {
        tapTag(LauncherShellTags.footerAction(SemanticInputAction.SECONDARY))
        waitForEditor()
    }

    private fun applySearch() {
        // Never inject Confirm unless the actual editor is focused: a result must not launch.
        compose.onNode(hasSetTextAction(), useUnmergedTree = true).assertIsFocused()
        press(faceKey(mapping.confirm))
        waitForClosedEditor()
    }

    private fun faceKey(button: ControllerFaceButton) = if (button == ControllerFaceButton.A) KeyEvent.KEYCODE_BUTTON_A else KeyEvent.KEYCODE_BUTTON_B

    private fun press(key: Int) {
        keyDown(key)
        try { SystemClock.sleep(20) } finally { keyUp(key) }
        compose.waitForIdle()
    }

    private fun keyDown(key: Int) {
        val now = SystemClock.uptimeMillis()
        heldKeys[key] = now
        recordInput("inject key=$key DOWN device=$deviceId")
        assertTrue("Android rejected gamepad key down $key", instrumentation.uiAutomation.injectInputEvent(
            KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0, 0, deviceId, 0, 0, InputDevice.SOURCE_GAMEPAD), true,
        ))
    }

    private fun keyUp(key: Int) {
        val down = heldKeys[key] ?: return
        recordInput("inject key=$key UP device=$deviceId")
        val accepted = instrumentation.uiAutomation.injectInputEvent(
            KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, key, 0, 0, deviceId, 0, 0, InputDevice.SOURCE_GAMEPAD), true,
        )
        if (accepted) heldKeys.remove(key)
        assertTrue("Android rejected gamepad key up $key", accepted)
    }

    private fun triggers(left: Float = 0f, right: Float = 0f) {
        joystickUsed = true
        val now = SystemClock.uptimeMillis()
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_UNKNOWN }
        val coordinates = MotionEvent.PointerCoords().apply {
            setAxisValue(MotionEvent.AXIS_LTRIGGER, left)
            setAxisValue(MotionEvent.AXIS_RTRIGGER, right)
        }
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, 1, arrayOf(properties), arrayOf(coordinates),
            0, 0, 1f, 1f, deviceId, 0, InputDevice.SOURCE_JOYSTICK, 0)
        try {
            assertTrue("Android rejected synthetic joystick reports on this device", instrumentation.uiAutomation.injectInputEvent(event, true))
        } finally { event.recycle() }
    }

    private fun tapTag(tag: String, expectedImeVisible: Boolean? = null) {
        val bounds = settledTapBounds(tag, expectedImeVisible)
        compose.onNodeWithTag(tag).assertIsDisplayed()
        val screenOffset = IntArray(2)
        compose.runOnIdle { compose.activity.window.decorView.getLocationOnScreen(screenOffset) }
        val x = bounds.center.x + screenOffset[0]
        val y = bounds.center.y + screenOffset[1]
        recordInput("tap tag=$tag bounds=$bounds decorOffset=${screenOffset.toList()} screen=($x,$y)")
        val down = SystemClock.uptimeMillis()
        fun inject(action: Int) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            try { assertTrue("Android rejected touch $action for $tag", instrumentation.uiAutomation.injectInputEvent(event, true)) }
            finally { event.recycle() }
        }
        inject(MotionEvent.ACTION_DOWN)
        try { SystemClock.sleep(30) } finally { inject(MotionEvent.ACTION_UP) }
        compose.waitForIdle()
    }

    private fun settledTapBounds(tag: String, expectedImeVisible: Boolean?): Rect {
        var previous: TapLayout? = null
        var changedAt = SystemClock.uptimeMillis()
        var settled: Rect? = null
        waitWithDiagnostics("Expected stable native keyboard and touch bounds for $tag") {
            val decor = compose.activity.window.decorView
            val insets = ViewCompat.getRootWindowInsets(decor)
            val sample = TapLayout(
                imeVisible = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true,
                imeBottom = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0,
                width = decor.width,
                height = decor.height,
                bounds = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().singleOrNull()?.boundsInRoot,
            )
            val now = SystemClock.uptimeMillis()
            if (sample != previous) { previous = sample; changedAt = now }
            val bounds = sample.bounds
            val ready = (expectedImeVisible == null || sample.imeVisible == expectedImeVisible) &&
                bounds != null && bounds.width > 0f && bounds.height > 0f && now - changedAt >= 250L
            if (ready) settled = bounds
            ready
        }
        recordInput("settled target=$tag layout=$previous")
        return requireNotNull(settled)
    }

    private fun recordInput(message: String) {
        inputRecords += "${SystemClock.uptimeMillis()} $message"
        while (inputRecords.size > 100) inputRecords.removeAt(0)
    }

    private fun waitWithDiagnostics(label: String, condition: () -> Boolean) {
        try { compose.waitUntil(TIMEOUT_MS, condition) }
        catch (failed: Throwable) {
            val details = runCatching { focusDiagnostics() }.getOrElse { "Diagnostics unavailable: $it" }
            throw AssertionError("$label\n$details\nInput receipts:\n${inputRecords.joinToString("\n")}", failed)
        }
    }

    private fun focusDiagnostics(): String {
        fun describe(node: SemanticsNode): String {
            fun <T> value(key: SemanticsPropertyKey<T>): T? = if (node.config.contains(key)) node.config[key] else null
            return "id=${node.id} tag=${value(SemanticsProperties.TestTag)} " +
                "description=${value(SemanticsProperties.ContentDescription)} text=${value(SemanticsProperties.Text)} " +
                "selected=${value(SemanticsProperties.Selected)} focused=${value(SemanticsProperties.Focused)} bounds=${node.boundsInRoot}"
        }
        val mergedFocused = compose.onAllNodes(isFocused()).fetchSemanticsNodes().map(::describe)
        val rawFocused = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().map(::describe)
        val cards = gridCards()
        val selectedCards = cards.filter { it.config[SemanticsProperties.Selected] }.map(::describe)
        val editors = compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().map(::describe)
        val footer = compose.onAllNodes(hasAnyAncestor(hasTestTag(LauncherShellTags.Footer))).fetchSemanticsNodes().map(::describe)
        var window = ""
        instrumentation.runOnMainSync {
            val decor = compose.activity.window.decorView
            window = "windowFocused=${decor.hasWindowFocus()} inTouchMode=${decor.isInTouchMode} size=${decor.width}x${decor.height} " +
                "ime=${ViewCompat.getRootWindowInsets(decor)?.isVisible(WindowInsetsCompat.Type.ime())}"
        }
        return "$window\nLibrary selected=${library.state.value.selectedItemId} filter=${library.state.value.filter} " +
            "cards=${cards.size} focusedGrid=${focusedGridCards().size}\n" +
            "Merged focus=$mergedFocused\nUnmerged focus=$rawFocused\nSelected cards=$selectedCards\n" +
            "Search query=${search.state.value.query} editors=$editors\nFooter=$footer"
    }

    private data class FilterChange(val filter: String, val at: Long)
    private data class TapLayout(val imeVisible: Boolean, val imeBottom: Int, val width: Int, val height: Int, val bounds: Rect?)
    private fun gaps(times: List<Long>) = times.zipWithNext { first, second -> second - first }
    private fun median(values: List<Long>) = values.sorted()[values.size / 2].toDouble()

    private companion object {
        const val GRID = "collection-grid"
        const val TIMEOUT_MS = 30_000L
    }
}
