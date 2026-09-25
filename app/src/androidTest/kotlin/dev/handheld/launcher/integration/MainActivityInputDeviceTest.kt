package dev.handheld.launcher.integration

import android.os.SystemClock
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import java.util.concurrent.ConcurrentLinkedQueue
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.handheld.launcher.MainActivity
import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.core.domain.model.ConfirmBackMapping
import dev.handheld.launcher.core.domain.model.BackgroundTint
import dev.handheld.launcher.core.domain.model.ControllerFaceButton
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.model.LauncherLocation
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.di.LauncherAppViewModel
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
 * Only normal UI navigation, text editing, display settings and their saved preferences change.
 * The grid tests restore the user's original layout; the display test restores its original scale.
 * Direct preference access is an emergency
 * restoration only if a failed test makes the normal Settings UI inaccessible.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityInputDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val container get() = (compose.activity.application as LauncherApplication).appContainer
    private lateinit var library: CollectionViewModel
    private lateinit var search: CollectionViewModel
    private lateinit var app: LauncherAppViewModel
    private lateinit var mapping: ConfirmBackMapping
    private var originalFilter: String? = null
    private var originalListMode: Boolean? = null
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
            app = ViewModelProvider(compose.activity, container.launcherViewModelFactory())
                .get(LauncherAppViewModel::class.java)
        }
        compose.waitUntil(TIMEOUT_MS) { !library.state.value.loading && !search.state.value.loading }
        mapping = runBlocking { container.controllerPreferenceRepository.confirmBackMapping.first() }
        originalFilter = library.state.value.filter
        originalListMode = runBlocking {
            LauncherDestination.LIBRARY in container.displayPreferenceRepository.preferences.first().listDestinations
        }
        tapTag(LauncherShellTags.destination(LauncherDestination.LIBRARY))
        waitForImeHidden()
        if (originalListMode == true) {
            tapTag("collection-layout")
            compose.waitUntil(TIMEOUT_MS) { LauncherDestination.LIBRARY !in app.display.value.listDestinations }
        }
        cycleToFilter("all")
        // On a fresh process, repository publication can finish before the Activity's
        // lifecycle collector has rendered that snapshot. Wait for the actual page too.
        waitWithDiagnostics("The populated Library grid must be rendered before input testing") {
            app.navigation.location.value == LauncherLocation.Destination(LauncherDestination.LIBRARY) &&
                !library.state.value.loading && !library.state.value.searching && library.state.value.items.size >= 12 &&
                compose.onAllNodes(hasTestTag(GRID)).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag(GRID).assertExists()
    }

    @After fun releaseInputAndReturnToLibrary() {
      try {
        try {
            heldKeys.keys.toList().forEach(::keyUp)
            if (joystickUsed) triggers()
            if (compose.onAllNodes(hasSetTextAction() and isFocused(), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()) {
                press(faceKey(mapping.back))
                waitForImeHidden()
            }
        } finally {
            tapTag(LauncherShellTags.destination(LauncherDestination.LIBRARY))
            waitForImeHidden()
            originalFilter?.let { if (it in filterKeys()) cycleToFilter(it) }
            originalListMode?.let { wasList ->
                if ((LauncherDestination.LIBRARY in app.display.value.listDestinations) != wasList) {
                    tapTag("collection-layout")
                    compose.waitUntil(TIMEOUT_MS) { (LauncherDestination.LIBRARY in app.display.value.listDestinations) == wasList }
                }
            }
            // Position saving is debounced. Wait for restoration to reach storage before
            // the Activity rule closes the ViewModel and cancels its pending save job.
            originalFilter?.takeIf { it in filterKeys() }?.let { restoredFilter ->
                waitWithDiagnostics("Restored Library filter $restoredFilter must reach storage before teardown") {
                    // Continue pumping Compose so pending focus/anchor callbacks can settle.
                    runBlocking {
                        container.navigationSnapshotRepository.observe(LauncherDestination.LIBRARY).first()
                            ?.filterKey?.value == restoredFilter
                    }
                }
            }
        }
      } finally {
          originalWindowCallback?.let { original ->
              instrumentation.runOnMainSync { compose.activity.window.callback = original }
          }
      }
    }

    @Test fun listKeepsFocusOnRowsAndYSelectShortcutsFollowTheSelectedGame() {
        tapTag("collection-layout")
        compose.waitUntil(TIMEOUT_MS) { LauncherDestination.LIBRARY in app.display.value.listDestinations }
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        waitForFocusedCard()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        waitForFocusedCard()
        val selected = requireNotNull(library.state.value.selectedItemId)
        val wasFavorite = selected in library.state.value.favorites
        try {
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            press(KeyEvent.KEYCODE_DPAD_LEFT)
            assertEquals(selected, library.state.value.selectedItemId)
            waitForFocusedCard()
            compose.onNodeWithTag("collection-preview-actions").assertDoesNotExist()
            assertEquals(0, compose.onAllNodes(hasAnyAncestor(hasTestTag("collection-preview")) and hasClickAction()).fetchSemanticsNodes().size)
            compose.onNodeWithTag(LauncherShellTags.footerAction(SemanticInputAction.TERTIARY)).assertIsDisplayed()
            press(KeyEvent.KEYCODE_BUTTON_Y)
            compose.waitUntil(TIMEOUT_MS) { (selected in library.state.value.favorites) != wasFavorite }
            assertEquals(selected, library.state.value.selectedItemId)
            waitForFocusedCard()
            press(KeyEvent.KEYCODE_BUTTON_Y)
            compose.waitUntil(TIMEOUT_MS) { (selected in library.state.value.favorites) == wasFavorite }
            compose.onNodeWithTag(LauncherShellTags.footerAction(SemanticInputAction.ITEM_DETAILS)).assertIsDisplayed()
            compose.onNodeWithTag(LauncherShellTags.footerAction(SemanticInputAction.MENU)).assertDoesNotExist()
            press(KeyEvent.KEYCODE_BUTTON_SELECT)
            compose.waitUntil(TIMEOUT_MS) { (app.navigation.location.value as? LauncherLocation.ItemDetails)?.itemId == selected }
            press(faceKey(mapping.back))
            compose.waitUntil(TIMEOUT_MS) { app.navigation.location.value == LauncherLocation.Destination(LauncherDestination.LIBRARY) }
            waitForFocusedCard()
            assertEquals(selected, library.state.value.selectedItemId)

            // Use the actual touch event path, then use the footer without moving to another pane.
            fun fastTapSelectedRow() {
                val cardBounds = focusedGridCards().single().boundsInRoot
                val touchOffset = IntArray(2)
                compose.runOnIdle { compose.activity.window.decorView.getLocationOnScreen(touchOffset) }
                val touchDown = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(touchDown, SystemClock.uptimeMillis(), action,
                        cardBounds.center.x + touchOffset[0], cardBounds.center.y + touchOffset[1], 0).apply {
                        source = InputDevice.SOURCE_TOUCHSCREEN
                    }
                    try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
                }
                compose.waitForIdle()
            }
            fastTapSelectedRow()
            press(KeyEvent.KEYCODE_BUTTON_Y)
            compose.waitUntil(TIMEOUT_MS) { (selected in library.state.value.favorites) != wasFavorite }
            waitForFocusedCard()
            assertEquals("Y after touch must keep the touched row", selected, library.state.value.selectedItemId)
            // Re-enter touch mode before using the shared footer actions.
            fastTapSelectedRow()
            tapTag(LauncherShellTags.footerAction(SemanticInputAction.TERTIARY))
            compose.waitUntil(TIMEOUT_MS) { (selected in library.state.value.favorites) == wasFavorite }
            tapTag(LauncherShellTags.footerAction(SemanticInputAction.ITEM_DETAILS))
            compose.waitUntil(TIMEOUT_MS) { (app.navigation.location.value as? LauncherLocation.ItemDetails)?.itemId == selected }
            press(faceKey(mapping.back))
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            waitForFocusedCard()
            assertEquals(selected, library.state.value.selectedItemId)
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            java.io.File(compose.activity.cacheDir, "list-redesign.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        } finally {
            runBlocking { container.favoriteRepository.setFavorite(selected, wasFavorite) }
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
        // Focus changes publish the selected item through an asynchronous StateFlow.
        // Capture the baseline only after the native card and rendered model agree.
        waitWithDiagnostics("The initial focused card and saved selection must agree") {
            val card = focusedGridCards().singleOrNull()
            val selectedTitle = library.state.value.selectedItem?.title
            card != null && card.config[SemanticsProperties.Selected] && selectedTitle != null &&
                selectedTitle in card.config[SemanticsProperties.ContentDescription]
        }
        compose.waitForIdle()
        val selected = library.state.value.selectedItemId

        // A dock tap exercises MainActivity.dispatchTouchEvent without ever opening a game.
        tapTag(LauncherShellTags.destination(LauncherDestination.LIBRARY))
        compose.waitUntil(TIMEOUT_MS) { focusedGridCards().isEmpty() }
        assertEquals(selected, library.state.value.selectedItemId)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        // Android can assign fallback focus before the shell's two-frame restoration.
        // Any focused card is not evidence that the selected card has been restored.
        waitWithDiagnostics("The first controller input must reacquire the selected card") {
            library.state.value.selectedItemId == selected && focusedGridCards().singleOrNull()
                ?.config?.get(SemanticsProperties.Selected) == true
        }
        compose.waitForIdle()
        assertEquals("The first controller input reacquires the selected card", selected, library.state.value.selectedItemId)
        assertTrue(focusedGridCards().single().config[SemanticsProperties.Selected])
    }

    @Test fun searchSupportsMappedApplyCancelAndTouchCancelWithoutOpeningResults() {
        val query = library.state.value.items.first().title.take(18)
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

    @Test fun realRomSearchUpdatesClearAndCloseDoNotResurrectAQueryOrRestrictiveFilter() {
        val items = library.state.value.items
        val rom = requireNotNull(items.filterIsInstance<LibraryItem.RomGame>().firstOrNull { candidate ->
            candidate.title.length >= 12 && items.count { it.title.contains(candidate.title.dropLast(1), ignoreCase = true) } == 1
        }) { "This real-device regression requires a ROM with a unique title prefix" }
        val query = rom.title
        fun replaceAndFindRom(value: String) {
            compose.onNode(hasSetTextAction(), useUnmergedTree = true).performTextReplacement(value)
            waitWithDiagnostics("Search must publish the existing ROM for '$value'") {
                search.state.value.let { it.query == value && !it.searching && it.items.any { item -> item.id == rom.id } }
            }
            compose.onNode(hasAnyAncestor(hasTestTag(SearchScreenTags.Results)) and
                hasContentDescription(rom.title) and hasClickAction()).assertIsDisplayed()
        }
        fun chooseAppsScope() {
            assertEquals("all", search.state.value.filter)
            press(KeyEvent.KEYCODE_BUTTON_R2)
            compose.waitUntil(TIMEOUT_MS) { search.state.value.filter == "games" }
            press(KeyEvent.KEYCODE_BUTTON_R2)
            compose.waitUntil(TIMEOUT_MS) { search.state.value.filter == "apps" }
            waitForImeHidden()
        }
        fun assertFreshSearch() {
            waitWithDiagnostics("Closed Search must reopen empty with the All scope") {
                search.state.value.let { it.query.isEmpty() && it.filter == "all" && !it.searching && it.items.isEmpty() }
            }
            val field = compose.onNode(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNode()
            assertEquals("The saved editor buffer must also be empty", "", field.config[SemanticsProperties.EditableText].text)
        }

        press(KeyEvent.KEYCODE_BUTTON_X)
        waitForEditor()
        replaceAndFindRom(query.dropLast(1))
        val prefixMatches = search.state.value.items.map { it.id }
        replaceAndFindRom(query)
        assertEquals("This edit must retain the same catalog matches", prefixMatches, search.state.value.items.map { it.id })
        replaceAndFindRom("$query ")
        assertEquals(prefixMatches, search.state.value.items.map { it.id })
        applySearch()
        chooseAppsScope()
        press(KeyEvent.KEYCODE_BUTTON_X)
        waitForEditor()
        compose.waitUntil(TIMEOUT_MS) { imeIsVisible() }
        compose.onNodeWithTag(LauncherShellTags.footerAction(SemanticInputAction.TERTIARY)).assertIsDisplayed()
        press(KeyEvent.KEYCODE_BUTTON_Y)
        compose.waitUntil(TIMEOUT_MS) { search.state.value.query.isEmpty() && search.state.value.filter == "all" }
        waitForEditor()
        assertTrue("Controller Clear keeps the native keyboard open", imeIsVisible())
        press(faceKey(mapping.back))
        waitForClosedEditor()
        assertEquals("Cancel after Clear must not restore the previous query", "", search.state.value.query)
        press(faceKey(mapping.back))
        assertEquals("Shortcut Search must return to its Library origin",
            LauncherLocation.Destination(LauncherDestination.LIBRARY), app.navigation.location.value)

        tapTag(LauncherShellTags.destination(LauncherDestination.SEARCH))
        assertFreshSearch()
        press(KeyEvent.KEYCODE_BUTTON_X)
        waitForEditor()
        replaceAndFindRom(query)
        applySearch()
        chooseAppsScope()
        assertTrue("The close check starts with a nonempty query", search.state.value.query.isNotEmpty())
        tapTag(LauncherShellTags.destination(LauncherDestination.LIBRARY))
        compose.waitUntil(TIMEOUT_MS) { search.state.value.query.isEmpty() && search.state.value.filter == "all" }
        tapTag(LauncherShellTags.destination(LauncherDestination.SEARCH))
        assertFreshSearch()
        press(faceKey(mapping.back))
        assertEquals("Dock Search Back goes Home", LauncherLocation.Destination(LauncherDestination.HOME), app.navigation.location.value)
    }

    @Test fun customBackgroundPaletteSupportsTouchControllerExitAndSavedSelection() {
        val original = runBlocking { container.displayPreferenceRepository.preferences.first() }
        fun rgb() = requireNotNull(app.display.value.backgroundCustomColorRgb)
        fun hsv() = FloatArray(3).also { android.graphics.Color.colorToHSV(rgb() or 0xFF000000.toInt(), it) }
        try {
            tapTag(LauncherShellTags.destination(LauncherDestination.SETTINGS))
            val section = hasContentDescription("Display") and hasClickAction()
            if (compose.onAllNodes(section).fetchSemanticsNodes().isNotEmpty())
                compose.onNode(section).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            compose.onNodeWithTag("background-color-hue").performScrollTo()
            tapTag("background-color-hue")
            compose.onNodeWithTag("background-color-field").performScrollTo()
            tapTag("background-color-field")
            compose.waitUntil(TIMEOUT_MS) { app.display.value.backgroundCustomColorRgb != null }
            val touched = rgb()
            press(KeyEvent.KEYCODE_DPAD_RIGHT) // First input restores the precise touched control.
            compose.onNodeWithTag("background-color-field").assertIsFocused()
            press(faceKey(mapping.confirm))
            compose.onNodeWithTag(LauncherShellTags.footerAction(SemanticInputAction.NAVIGATE_UP)).assertIsDisplayed()
            val before = hsv()
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            press(KeyEvent.KEYCODE_DPAD_UP)
            val after = hsv()
            assertTrue(after[1] > before[1] && after[2] > before[2])
            assertTrue(rgb() != touched)
            press(faceKey(mapping.back))
            compose.onNodeWithTag("background-color-field").assertIsFocused()
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.onNodeWithTag("background-color-hue").assertIsFocused()
            press(faceKey(mapping.confirm))
            compose.onNodeWithTag(LauncherShellTags.footerAction(SemanticInputAction.NAVIGATE_RIGHT)).assertIsDisplayed()
            press(KeyEvent.KEYCODE_DPAD_LEFT)
            assertTrue(hsv()[0] < after[0])
            press(faceKey(mapping.back))
            // A fast drag must settle on its final point without an older save snapping it back.
            compose.onNodeWithTag("background-color-field").performScrollTo().performTouchInput {
                swipe(Offset(width * .2f, height * .2f), Offset(width * .8f, height * .7f), 300)
            }
            compose.waitForIdle()
            val dragged = hsv()
            assertEquals(.8f, dragged[1], .04f)
            assertEquals(.3f, dragged[2], .04f)
            val selected = rgb()
            val saved = runBlocking { kotlinx.coroutines.withTimeout(TIMEOUT_MS) {
                container.displayPreferenceRepository.preferences.first { it.backgroundCustomColorRgb == selected }
            } }
            assertEquals(original.uiScalePercent, saved.uiScalePercent)
            assertEquals(original.backgroundGrainPercent, saved.backgroundGrainPercent)
            tapTag(LauncherShellTags.destination(LauncherDestination.HOME))
            tapTag(LauncherShellTags.destination(LauncherDestination.SETTINGS))
            compose.onNodeWithTag("background-tint-choice").performScrollTo()
            assertEquals(selected, rgb())
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try { java.io.File(compose.activity.cacheDir, "background-color-palette.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            } } finally { bitmap.recycle() }
            compose.onNodeWithTag("background-preset-purple").performScrollTo()
            tapTag("background-preset-purple")
            compose.waitUntil(TIMEOUT_MS) { app.display.value.backgroundCustomColorRgb == null && app.display.value.backgroundTint == BackgroundTint.PURPLE }
            compose.onNodeWithTag("background-preset-purple").assertIsSelected()
        } finally {
            // Let the ordered writer finish before restoring the user's preference.
            val selected = app.display.value
            runBlocking {
                kotlinx.coroutines.withTimeout(TIMEOUT_MS) { container.displayPreferenceRepository.preferences.first {
                    it.backgroundCustomColorRgb == selected.backgroundCustomColorRgb && it.backgroundTint == selected.backgroundTint
                } }
                container.displayPreferenceRepository.setBackgroundTint(original.backgroundTint)
                original.backgroundCustomColorRgb?.let { container.displayPreferenceRepository.setBackgroundCustomColorRgb(it) }
            }
            compose.waitUntil(TIMEOUT_MS) { app.display.value.backgroundCustomColorRgb == original.backgroundCustomColorRgb &&
                app.display.value.backgroundTint == original.backgroundTint }
        }
    }

    @Test fun backgroundColorAndGrainSettingsRenderLiveAndPreserveOtherPreferences() {
        val original = runBlocking { container.displayPreferenceRepository.preferences.first() }
        tapTag(LauncherShellTags.destination(LauncherDestination.SETTINGS))
        val section = hasContentDescription("Display") and hasClickAction()
        if (compose.onAllNodes(section).fetchSemanticsNodes().isNotEmpty())
            compose.onNode(section).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        fun tint(value: BackgroundTint) {
            compose.onNodeWithTag("background-preset-${value.persistedKey}").performScrollTo()
                .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            compose.waitUntil(TIMEOUT_MS) { app.display.value.backgroundTint == value && app.display.value.backgroundCustomColorRgb == null }
            assertEquals(value, app.display.value.backgroundTint)
        }
        fun level(tag: String, value: Int) {
            compose.onNodeWithTag(tag).performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(value.toFloat()) }
            compose.waitUntil(TIMEOUT_MS) {
                (if (tag == "background-tint-strength") app.display.value.backgroundTintPercent
                else app.display.value.backgroundGrainPercent) == value
            }
            compose.waitForIdle()
        }
        fun sample(fileName: String? = null): Pair<Double, Double> {
            compose.waitForIdle()
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                fileName?.let { java.io.File(compose.activity.cacheDir, it).outputStream().use { output ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                } }
                // An empty section of the status strip contains only the shell background.
                val pixels = (0 until 24).flatMap { y -> (0 until 24).map { x ->
                    bitmap.getPixel(bitmap.width / 2 + x, 24 + y)
                } }
                val blue = pixels.map { android.graphics.Color.blue(it).toDouble() }
                val red = pixels.map { android.graphics.Color.red(it).toDouble() }
                val mean = blue.average()
                return (mean - red.average()) to blue.map { (it - mean) * (it - mean) }.average()
            } finally { bitmap.recycle() }
        }
        try {
            level("background-tint-strength", 100)
            level("background-grain-intensity", 0)
            tint(BackgroundTint.PURPLE)
            val purple = sample()
            tint(BackgroundTint.GRAPHITE)
            val graphite = sample()
            tint(BackgroundTint.BLUE)
            val blue = sample()
            assertTrue("The live shell shows purple, not only the saved value", purple.first > graphite.first + 5)
            assertTrue("The blue choice visibly changes the shell pigment", blue.first > purple.first + 5)
            tint(BackgroundTint.PURPLE)
            level("background-tint-strength", 0)
            val neutral = sample()
            assertTrue("Zero strength returns neutral charcoal", kotlin.math.abs(neutral.first - graphite.first) < 2)
            level("background-grain-intensity", 100)
            val textured = sample()
            assertTrue("Grain zero removes the visible texture", textured.second > neutral.second + .3)
            // Capture the intended softer default, then restore the user's actual settings.
            level("background-tint-strength", 40)
            level("background-grain-intensity", 30)
            compose.onNodeWithTag("background-tint-choice").performScrollTo()
            sample("background-tint-settings.png")
            val saved = runBlocking { container.displayPreferenceRepository.preferences.first() }
            assertEquals(BackgroundTint.PURPLE, saved.backgroundTint)
            assertEquals(40, saved.backgroundTintPercent)
            assertEquals(30, saved.backgroundGrainPercent)
            assertEquals(original.homeArtworkBackground, saved.homeArtworkBackground)
            assertEquals(original.listArtworkBackground, saved.listArtworkBackground)
            assertEquals(original.uiScalePercent, saved.uiScalePercent)
        } finally {
            runBlocking {
                container.displayPreferenceRepository.setBackgroundTint(original.backgroundTint)
                original.backgroundCustomColorRgb?.let { container.displayPreferenceRepository.setBackgroundCustomColorRgb(it) }
                container.displayPreferenceRepository.setBackgroundTintPercent(original.backgroundTintPercent)
                container.displayPreferenceRepository.setBackgroundGrainPercent(original.backgroundGrainPercent)
            }
            compose.waitUntil(TIMEOUT_MS) { app.display.value.backgroundTint == original.backgroundTint &&
                app.display.value.backgroundCustomColorRgb == original.backgroundCustomColorRgb &&
                app.display.value.backgroundTintPercent == original.backgroundTintPercent &&
                app.display.value.backgroundGrainPercent == original.backgroundGrainPercent }
        }
    }

    @Test fun homeArtworkBackgroundSettingIsOptionalHomeOnlyAndRestoresOriginalPreference() {
        val original = runBlocking { container.displayPreferenceRepository.preferences.first().homeArtworkBackground }
        fun openDisplay() {
            tapTag(LauncherShellTags.destination(LauncherDestination.SETTINGS))
            val section = hasContentDescription("Display") and hasClickAction()
            if (compose.onAllNodes(section).fetchSemanticsNodes().isNotEmpty()) {
                compose.onNode(section).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            }
        }
        fun choose(value: Boolean) {
            openDisplay()
            val toggle = compose.onNodeWithTag("home-artwork-background-toggle").performScrollTo()
            if (app.display.value.homeArtworkBackground != value)
                toggle.performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            compose.waitUntil(TIMEOUT_MS) { app.display.value.homeArtworkBackground == value }
            assertEquals(value, runBlocking { container.displayPreferenceRepository.preferences.first().homeArtworkBackground })
        }
        try {
            choose(true)
            tapTag(LauncherShellTags.destination(LauncherDestination.HOME))
            compose.waitUntil(TIMEOUT_MS) {
                compose.onAllNodesWithTag("home-rom-backdrop").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("home-rom-backdrop").assertIsDisplayed()
            compose.waitForIdle()
            // Save the real-device preview without changing catalog entries or opening a game.
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                try { java.io.File(compose.activity.cacheDir, "home-backdrop-preview.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                } } finally { bitmap.recycle() }
            }
            tapTag(LauncherShellTags.destination(LauncherDestination.LIBRARY))
            compose.onNodeWithTag("home-rom-backdrop").assertDoesNotExist()
            choose(false)
            tapTag(LauncherShellTags.destination(LauncherDestination.HOME))
            compose.onNodeWithTag("home-rom-backdrop").assertDoesNotExist()
        } finally {
            try { choose(original) }
            catch (restoreFailure: Throwable) {
                runBlocking { container.displayPreferenceRepository.setHomeArtworkBackground(original) }
                throw restoreFailure
            }
        }
    }

    @Test fun listArtworkBackgroundSettingIsIndependentAndOnlyAppearsInLists() {
        val original = runBlocking { container.displayPreferenceRepository.preferences.first() }
        fun choose(value: Boolean) {
            tapTag(LauncherShellTags.destination(LauncherDestination.SETTINGS))
            val section = hasContentDescription("Display") and hasClickAction()
            if (compose.onAllNodes(section).fetchSemanticsNodes().isNotEmpty()) {
                compose.onNode(section).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            }
            val toggle = compose.onNodeWithTag("list-artwork-background-toggle").performScrollTo()
            if (app.display.value.listArtworkBackground != value)
                toggle.performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            compose.waitUntil(TIMEOUT_MS) { app.display.value.listArtworkBackground == value }
            val stored = runBlocking { container.displayPreferenceRepository.preferences.first() }
            assertEquals(value, stored.listArtworkBackground)
            assertEquals("Home keeps its independent setting", original.homeArtworkBackground, stored.homeArtworkBackground)
        }
        fun awaitBackdrop() {
            compose.waitUntil(TIMEOUT_MS) {
                compose.onAllNodesWithTag("list-rom-backdrop").fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithTag("list-rom-backdrop").assertIsDisplayed()
        }
        try {
            cycleToFilter("console:ps2")
            tapTag("collection-layout")
            compose.waitUntil(TIMEOUT_MS) { LauncherDestination.LIBRARY in app.display.value.listDestinations }
            choose(false)
            tapTag(LauncherShellTags.destination(LauncherDestination.LIBRARY))
            compose.onNodeWithTag("list-rom-backdrop").assertDoesNotExist()
            choose(true)
            tapTag(LauncherShellTags.destination(LauncherDestination.LIBRARY))
            awaitBackdrop()
            compose.waitForIdle()
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                try { java.io.File(compose.activity.cacheDir, "compact-list-backdrop.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                } } finally { bitmap.recycle() }
            }
            tapTag("collection-layout")
            compose.waitUntil(TIMEOUT_MS) { LauncherDestination.LIBRARY !in app.display.value.listDestinations }
            compose.onNodeWithTag("list-rom-backdrop").assertDoesNotExist()
            tapTag("collection-layout")
            awaitBackdrop()
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitForFocusedCard()
            press(KeyEvent.KEYCODE_BUTTON_SELECT)
            compose.waitUntil(TIMEOUT_MS) { app.navigation.location.value is LauncherLocation.ItemDetails }
            compose.onNodeWithTag("list-rom-backdrop").assertDoesNotExist()
            press(faceKey(mapping.back))
            awaitBackdrop()
            tapTag(LauncherShellTags.destination(LauncherDestination.HOME))
            compose.onNodeWithTag("list-rom-backdrop").assertDoesNotExist()
            choose(false)
            tapTag(LauncherShellTags.destination(LauncherDestination.LIBRARY))
            compose.onNodeWithTag("list-rom-backdrop").assertDoesNotExist()
        } finally {
            try { choose(original.listArtworkBackground) }
            catch (restoreFailure: Throwable) {
                runBlocking { container.displayPreferenceRepository.setListArtworkBackground(original.listArtworkBackground) }
                throw restoreFailure
            }
        }
    }

    @Test fun displayScaleKeepsDockAndFooterInsideTheNativeWindowAndRestoresOriginalSetting() {
        val originalScale = runBlocking { container.displayPreferenceRepository.preferences.first().uiScalePercent }
        tapTag(LauncherShellTags.destination(LauncherDestination.SETTINGS))
        val displaySection = hasContentDescription("Display") and hasClickAction()
        if (compose.onAllNodes(displaySection).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNode(displaySection).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        }

        fun chooseScale(percent: Int) {
            val choice = hasContentDescription("UI scale")
            compose.onNode(choice).performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(percent.toFloat())) }
            compose.waitUntil(TIMEOUT_MS) { app.display.value.uiScalePercent == percent }
            compose.onNode(choice).assertIsDisplayed()
            assertEquals("Settings must persist the chosen scale", percent,
                runBlocking { container.displayPreferenceRepository.preferences.first().uiScalePercent })
        }

        fun capturePreview(page: String) {
            if (InstrumentationRegistry.getArguments().getString("captureScalePreviews") != "true") return
            compose.waitForIdle()
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                try {
                    java.io.File(instrumentation.context.getExternalFilesDir(null), "scale-150-$page.png")
                        .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                } finally { bitmap.recycle() }
            }
        }

        var testFailure: Throwable? = null
        try {
            for (percent in listOf(90, 110, 120, 130, 140, 150, 100)) {
                chooseScale(percent)
                compose.waitForIdle()
                assertChromeInsideNativeWindow(percent)
                if (percent == 150) capturePreview("settings")
                tapTag(LauncherShellTags.destination(LauncherDestination.LIBRARY))
                compose.onNodeWithTag(GRID).performScrollToIndex(0)
                compose.waitForIdle()
                val viewport = compose.onNodeWithTag(GRID).fetchSemanticsNode().boundsInRoot
                val firstCard = gridCards().minWith(compareBy({ it.positionInRoot.y }, { it.positionInRoot.x }))
                val cardBottom = firstCard.positionInRoot.y + firstCard.size.height
                assertTrue("At $percent%, the full first card and both caption lines must fit above the dock: $cardBottom > ${viewport.bottom}",
                    cardBottom <= viewport.bottom + 1f)
                val artwork = compose.onAllNodes(hasTestTag("collection-card-artwork"), useUnmergedTree = true)
                    .fetchSemanticsNodes().filter { it.boundsInRoot.overlaps(viewport) }
                    .minWith(compareBy({ it.positionInRoot.y }, { it.positionInRoot.x }))
                val minimumThumbnailPx = 48f * compose.activity.resources.displayMetrics.density
                assertTrue("At $percent%, grid covers must remain recognizable, not collapse to a sliver: ${artwork.size.height}px",
                    artwork.size.height >= minimumThumbnailPx)
                if (percent == 150) {
                    assertChromeInsideNativeWindow(percent)
                    capturePreview("grid")
                    tapTag("collection-layout")
                    compose.waitUntil(TIMEOUT_MS) { LauncherDestination.LIBRARY in app.display.value.listDestinations }
                    assertChromeInsideNativeWindow(percent)
                    capturePreview("list")
                    tapTag("collection-layout")
                    compose.waitUntil(TIMEOUT_MS) { LauncherDestination.LIBRARY !in app.display.value.listDestinations }
                    tapTag(LauncherShellTags.destination(LauncherDestination.HOME))
                    assertChromeInsideNativeWindow(percent)
                    capturePreview("home")
                }
                tapTag(LauncherShellTags.destination(LauncherDestination.SETTINGS))
            }
        } catch (failure: Throwable) {
            testFailure = failure
            throw failure
        } finally {
            try {
                tapTag(LauncherShellTags.destination(LauncherDestination.SETTINGS))
                chooseScale(originalScale)
            } catch (restoreFailure: Throwable) {
                // A cropping regression may hide the Settings controls. Restore this one
                // preference without touching the catalog, and retain the test failure.
                recordInput("Settings UI could not restore $originalScale%; restoring the display preference after failure")
                try {
                    runBlocking { container.displayPreferenceRepository.setUiScalePercent(originalScale) }
                    compose.waitUntil(TIMEOUT_MS) { app.display.value.uiScalePercent == originalScale }
                } catch (fallbackFailure: Throwable) { restoreFailure.addSuppressed(fallbackFailure) }
                testFailure?.addSuppressed(restoreFailure) ?: throw restoreFailure
            }
        }
    }

    @Test fun sustainedDpadSearchReachesMaximumSpeedReversesAndStopsWithoutLosingCardFocus() {
        // One physical-style DOWN/UP pair owns every repeat. Short press loops cannot
        // exercise the 2.5-second acceleration ramp or reveal queued scroll/focus work.
        val query = listOf("a", "e", "i").maxBy { needle ->
            library.state.value.items.count { it.title.contains(needle, ignoreCase = true) }
        }
        press(KeyEvent.KEYCODE_BUTTON_X)
        waitForEditor()
        compose.onNode(hasSetTextAction(), useUnmergedTree = true).performTextReplacement(query)
        waitWithDiagnostics("A broad real-ROM Search must supply a long acceleration runway") {
            search.state.value.let { it.query == query && !it.searching && it.items.size >= 300 }
        }
        applySearch()
        val items = search.state.value.items
        val indices = items.mapIndexed { index, item -> item.id to index }.toMap()
        val changes = CopyOnWriteArrayList<Pair<Int, Long>>()
        val observer = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        compose.runOnIdle {
            observer.launch {
                search.state.map { it.selectedItemId }.distinctUntilChanged().collect { id ->
                    indices[id]?.let { changes += it to SystemClock.uptimeMillis() }
                }
            }
        }
        fun focusedResults() = compose.onAllNodes(
            hasAnyAncestor(hasTestTag(SearchScreenTags.Results)) and isFocused() and hasClickAction(),
        ).fetchSemanticsNodes()
        fun renderFor(durationMillis: Long) {
            val deadline = SystemClock.uptimeMillis() + durationMillis
            // Android repeats use uptime, but Compose's test clock needs pumping.
            // A thread sleep would freeze rendering for the entire hold and test a
            // synthetic backlog instead of the live screen on the physical device.
            compose.waitUntil(durationMillis + 5_000) { SystemClock.uptimeMillis() >= deadline }
        }
        fun assertSettled() {
            waitWithDiagnostics("A single real result must own focus after accelerated movement") {
                focusedResults().size == 1
            }
            val afterRelease = search.state.value.selectedItemId
            renderFor(450)
            compose.waitForIdle()
            assertEquals("No deferred repeat may move selection after release", afterRelease, search.state.value.selectedItemId)
            assertEquals("The focused result must match the footer's selected item", items.first { it.id == afterRelease }.title,
                focusedResults().single().config[SemanticsProperties.ContentDescription].first())
            assertTrue("Scrolling must not reopen Search editing", !imeIsVisible())
        }
        val frameTimes = ConcurrentLinkedQueue<Long>()
        val frameThread = HandlerThread("ScrollFrameMetrics").apply { start() }
        val frameListener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 0L) frameTimes.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
        }
        compose.runOnIdle {
            container.enrichedArtworkLoader.trimMemory(clear = true)
            compose.activity.window.addOnFrameMetricsAvailableListener(frameListener, Handler(frameThread.looper))
        }
        try {
            waitWithDiagnostics("Applied Search must focus its first real result") { focusedResults().size == 1 }
            changes.clear()
            val began = SystemClock.uptimeMillis()
            try {
                keyDown(KeyEvent.KEYCODE_DPAD_DOWN)
                renderFor(7_000)
            } finally { keyUp(KeyEvent.KEYCODE_DPAD_DOWN) }
            compose.waitForIdle()
            assertSettled()
            val forward = changes.toList()
            assertTrue("Held Down must keep updating the selected card while scrolling: $forward", forward.size >= 40)
            assertTrue("Held Down must move forward without focus jumping back", forward.zipWithNext().all { (a, b) -> b.first > a.first })
            val early = forward.count { it.second - began in 500L..2_000L }
            val fast = forward.count { it.second - began in 4_500L..6_000L }
            assertTrue("Maximum-speed movement must visibly accelerate; early=$early fast=$fast", fast >= early + 3)
            android.util.Log.i("HeldSearchTest", "query=$query results=${items.size} heldMs=7000 changes=${forward.size} early=$early fast=$fast final=${forward.lastOrNull()?.first}")

            changes.clear()
            val reversalStart = indices.getValue(requireNotNull(search.state.value.selectedItemId))
            try {
                keyDown(KeyEvent.KEYCODE_DPAD_UP)
                renderFor(3_500)
            } finally { keyUp(KeyEvent.KEYCODE_DPAD_UP) }
            compose.waitForIdle()
            assertSettled()
            assertTrue("Immediate reversal must move only toward earlier results: $changes",
                changes.size >= 20 && changes.first().first < reversalStart && changes.zipWithNext().all { (a, b) -> b.first < a.first })

            // Traverse horizontal row boundaries at maximum cadence as well.
            changes.clear()
            try {
                keyDown(KeyEvent.KEYCODE_DPAD_RIGHT)
                renderFor(4_000)
            } finally { keyUp(KeyEvent.KEYCODE_DPAD_RIGHT) }
            compose.waitForIdle()
            assertSettled()
            assertTrue("Horizontal held navigation must progress through multiple rows", changes.size >= 25)
            assertTrue(changes.zipWithNext().all { (a, b) -> b.first > a.first })
            assertEquals("Navigation must retain the original query", query, search.state.value.query)
        } finally {
            observer.cancel()
            compose.runOnUiThread { compose.activity.window.removeOnFrameMetricsAvailableListener(frameListener) }
            frameThread.quitSafely()
            val frames = frameTimes.toList().sorted()
            if (frames.isNotEmpty()) android.util.Log.i("HeldSearchTest",
                "cold artwork scrolling: ${frames.size} frames, p95=${frames[frames.size * 95 / 100] / 1_000_000}ms, over50ms=${frames.count { it > 50_000_000L }}, max=${frames.last() / 1_000_000}ms")
        }
    }

    @Test fun slowTriggerReportsMergeAndAHeldTriggerAcceleratesThenStopsOnRelease() {
        assertTrue("Trigger hold coverage requires at least two detected console filters", filterKeys().size >= 3)
        val changes = CopyOnWriteArrayList<FilterChange>()
        val emptyCollections = CopyOnWriteArrayList<String>()
        val observer = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        compose.runOnIdle {
            observer.launch {
                library.state.map { it.filter }.distinctUntilChanged().drop(1).collect {
                    changes += FilterChange(it, SystemClock.uptimeMillis())
                }
            }
            observer.launch {
                library.state.collect { if (it.items.isEmpty()) emptyCollections += it.filter }
            }
        }
        try {
            // Both report orders merge, and a deliberate short squeeze exceeds the old
            // 360ms repeat delay while remaining below the trigger's new 650ms threshold.
            checkSlowSqueeze(changes, keyFirst = true, right = true)
            checkSlowSqueeze(changes, keyFirst = false, right = false)
            val keys = filterKeys()
            changes.clear()
            val began = SystemClock.uptimeMillis()
            try {
                keyDown(KeyEvent.KEYCODE_BUTTON_R2)
                SystemClock.sleep(3_700)
            } finally { keyUp(KeyEvent.KEYCODE_BUTTON_R2) }
            compose.waitForIdle()
            val times = changes.map { it.at - began }
            assertTrue("No trigger repeat before its deliberate 650ms threshold: $times", times.size >= 2 && times[1] >= 640L)
            val indices = changes.map { keys.indexOf(it.filter) }
            assertTrue("A hold must advance through the finite filter sequence without wrapping: $changes",
                indices.all { it > 0 } && indices.zipWithNext().all { (first, second) -> second > first })
            val early = gaps(times.filter { it in 750L..1_700L })
            val late = gaps(times.filter { it in 2_900L..3_650L })
            if (early.size >= 4 && late.size >= 5) {
                assertTrue("Hold should accelerate; early=$early late=$late", median(late) < median(early) * .82)
            } else {
                // A finite catalog can run out of categories before the late timing window.
                // Engine JVM coverage still checks the full acceleration curve unconditionally.
                assertEquals("A short filter sequence must stop at its last category", keys.last(), library.state.value.filter)
                recordInput("Finite boundary ended observable cadence after ${changes.size} steps: $times")
            }
            SystemClock.sleep(100)
            val releasedCount = changes.size
            SystemClock.sleep(500)
            assertEquals("No repeat may survive trigger release", releasedCount, changes.size)
            assertTrue("Populated category changes must not briefly empty the collection: $emptyCollections", emptyCollections.isEmpty())
        } finally {
            try {
                keyUp(KeyEvent.KEYCODE_BUTTON_L2)
                keyUp(KeyEvent.KEYCODE_BUTTON_R2)
                triggers()
            } finally { observer.cancel() }
        }
    }

    @Test fun filterDirectionsTraverseVisibleChipsWithoutScrollingAndTriggersStopAtFiniteEnds() {
        val keys = filterKeys()
        assertTrue("Strip navigation requires at least one detected console filter", keys.size >= 2)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        waitForFocusedCard()
        cycleToFilter(keys[1])
        waitForFocusedCard()
        val selectedFilter = library.state.value.filter
        compose.onNodeWithTag(FILTER_STRIP).performScrollToIndex(0)
        val visible = fullyVisibleFilterTags()
        assertTrue("At least one complete category must fit in the native header", visible.isNotEmpty())
        val stripScroll = filterStripScroll()
        compose.onNodeWithTag(visible.first()).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag(visible.first()).assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNodeWithTag(FILTER_ALL).assertIsFocused()
        assertEquals("Left exits to All without applying it", selectedFilter, library.state.value.filter)

        for (tag in visible) {
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            compose.onNodeWithTag(tag).assertIsFocused()
            assertEquals("D-pad traversal must not change the strip's scroll", stripScroll, filterStripScroll(), .001f)
            assertEquals("D-pad focus must not apply a category", selectedFilter, library.state.value.filter)
        }
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(hasContentDescription("Show all console filters in a grid")).assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag("collection-layout").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        val sortLabel = if (library.state.value.sort == "recent") "Recent" else "Title"
        compose.onNode(hasContentDescription("Sort: $sortLabel")).assertIsFocused()
        assertEquals(stripScroll, filterStripScroll(), .001f)
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNodeWithTag("collection-layout").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNode(hasContentDescription("Show all console filters in a grid")).assertIsFocused()
        for (tag in visible.asReversed()) {
            press(KeyEvent.KEYCODE_DPAD_LEFT)
            compose.onNodeWithTag(tag).assertIsFocused()
        }
        assertEquals(stripScroll, filterStripScroll(), .001f)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        waitForFocusedCard()
        assertEquals("Down returns to the grid without applying a category", selectedFilter, library.state.value.filter)

        cycleToFilter(keys.last())
        repeat(3) {
            press(KeyEvent.KEYCODE_BUTTON_R2)
            assertEquals("R2 at the final category must not wrap to All", keys.last(), library.state.value.filter)
        }
        try {
            keyDown(KeyEvent.KEYCODE_BUTTON_R2)
            SystemClock.sleep(900)
        } finally { keyUp(KeyEvent.KEYCODE_BUTTON_R2) }
        compose.waitForIdle()
        assertEquals("Held R2 repeats must also stop at the final category", keys.last(), library.state.value.filter)

        cycleToFilter("all")
        repeat(3) {
            press(KeyEvent.KEYCODE_BUTTON_L2)
            assertEquals("L2 at All must not wrap to the final category", "all", library.state.value.filter)
        }
    }

    @Test fun backReachesLibraryControlsFromDeepGridAndListAndReturnsToTheSameGame() {
        for (list in listOf(false, true)) {
            if ((LauncherDestination.LIBRARY in app.display.value.listDestinations) != list) {
                tapTag("collection-layout")
                compose.waitUntil(TIMEOUT_MS) { (LauncherDestination.LIBRARY in app.display.value.listDestinations) == list }
            }
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitForFocusedCard()
            try { keyDown(KeyEvent.KEYCODE_DPAD_DOWN); SystemClock.sleep(1_300) }
            finally { keyUp(KeyEvent.KEYCODE_DPAD_DOWN) }
            compose.waitForIdle()
            waitForFocusedCard()
            val selected = requireNotNull(library.state.value.selectedItemId)
            assertTrue("The shortcut must work well below the first row",
                library.state.value.items.indexOfFirst { it.id == selected } >= 12)
            val anchor = library.state.value.firstVisibleItemId to library.state.value.firstVisibleOffsetPx
            val beforeFilter = library.state.value.filter
            val beforeSort = library.state.value.sort
            fun unchanged() {
                assertEquals(selected, library.state.value.selectedItemId)
                assertEquals(anchor, library.state.value.firstVisibleItemId to library.state.value.firstVisibleOffsetPx)
                assertEquals(beforeFilter, library.state.value.filter)
                assertEquals(beforeSort, library.state.value.sort)
            }
            val backHint = hasAnyAncestor(hasTestTag(LauncherShellTags.footerAction(SemanticInputAction.BACK))) and hasText("Controls")
            compose.onNode(backHint, useUnmergedTree = true).assertIsDisplayed()
            press(faceKey(mapping.back))
            compose.onNodeWithTag("collection-all-filters").assertIsFocused()
            unchanged()
            press(faceKey(mapping.confirm)) // Only activate an asserted header control, never a game.
            val filtersDialog = hasAnyAncestor(hasTestTag(LauncherShellTags.Overlay)) and hasText("All filters")
            compose.onNode(filtersDialog).assertIsDisplayed()
            press(faceKey(mapping.back))
            compose.onNode(filtersDialog).assertDoesNotExist()
            compose.onNodeWithTag("collection-all-filters").assertIsFocused()
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            compose.onNodeWithTag("collection-layout").assertIsFocused()
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            compose.onNodeWithTag("collection-sort").assertIsFocused()
            press(faceKey(mapping.confirm))
            val sortDialog = hasAnyAncestor(hasTestTag(LauncherShellTags.Overlay)) and hasText("Sort by")
            compose.onNode(sortDialog).assertIsDisplayed()
            press(faceKey(mapping.back))
            compose.onNodeWithTag("collection-sort").assertIsFocused()
            unchanged()
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitForFocusedCard()
            unchanged()
            press(faceKey(mapping.back))
            compose.onNodeWithTag("collection-all-filters").assertIsFocused()
            press(faceKey(mapping.back))
            waitForFocusedCard()
            unchanged()
        }
    }

    @Test fun controlsShortcutCancelsAnInFlightGridMoveWithoutStealingFocusBack() {
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        waitForFocusedCard()
        try {
            keyDown(KeyEvent.KEYCODE_DPAD_DOWN)
            SystemClock.sleep(25)
            press(faceKey(mapping.back))
        } finally { keyUp(KeyEvent.KEYCODE_DPAD_DOWN) }
        compose.onNodeWithTag("collection-all-filters").assertIsFocused()
        val selected = library.state.value.selectedItemId
        SystemClock.sleep(250)
        compose.waitForIdle()
        compose.onNodeWithTag("collection-all-filters").assertIsFocused()
        assertEquals(selected, library.state.value.selectedItemId)
        press(faceKey(mapping.back))
        waitForFocusedCard()
        assertEquals(selected, library.state.value.selectedItemId)
    }

    @Test fun backUsesPageRootsAndDismissesMenuWithoutNavigatingOrCyclingFilters() {
        for (destination in LauncherDestination.dockOrder) {
            tapTag(LauncherShellTags.destination(destination))
            compose.onNodeWithTag(LauncherShellTags.destination(destination)).assertIsSelected()
            press(faceKey(mapping.back))
            val expected = if (destination in setOf(LauncherDestination.SEARCH, LauncherDestination.SETTINGS))
                LauncherDestination.HOME else destination
            assertEquals("Back from ${destination.persistedKey}",
                LauncherLocation.Destination(expected), app.navigation.location.value)
            compose.onNodeWithTag(LauncherShellTags.destination(expected)).assertIsSelected()
        }

        tapTag(LauncherShellTags.destination(LauncherDestination.LIBRARY))
        waitForImeHidden()
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        waitForFocusedCard()
        val before = library.state.value
        val menu = hasAnyAncestor(hasTestTag(LauncherShellTags.Overlay)) and hasText("Menu")
        press(KeyEvent.KEYCODE_BUTTON_START)
        waitWithDiagnostics("Menu must open above the Library") { compose.onAllNodes(menu).fetchSemanticsNodes().isNotEmpty() }
        press(KeyEvent.KEYCODE_BUTTON_R2)
        press(KeyEvent.KEYCODE_BUTTON_R1)
        assertEquals("A modal must block underlying filter cycling", before.filter, library.state.value.filter)
        assertEquals(LauncherLocation.Destination(LauncherDestination.LIBRARY), app.navigation.location.value)
        press(faceKey(mapping.back))
        waitWithDiagnostics("Back must dismiss Menu") { compose.onAllNodes(menu).fetchSemanticsNodes().isEmpty() }
        assertEquals(before.selectedItemId, library.state.value.selectedItemId)
        assertEquals(LauncherLocation.Destination(LauncherDestination.LIBRARY), app.navigation.location.value)
        press(faceKey(mapping.back))
        assertEquals("A second Back must remain at the Library root",
            LauncherLocation.Destination(LauncherDestination.LIBRARY), app.navigation.location.value)
    }

    private fun checkSlowSqueeze(changes: CopyOnWriteArrayList<FilterChange>, keyFirst: Boolean, right: Boolean) {
        compose.waitForIdle()
        changes.clear()
        val keys = filterKeys()
        val before = keys.indexOf(library.state.value.filter)
        val next = (before + if (right) 1 else -1).coerceIn(0, keys.lastIndex)
        assertTrue("Slow-squeeze coverage must start away from the tested boundary", next != before)
        val expected = keys[next]
        val key = if (right) KeyEvent.KEYCODE_BUTTON_R2 else KeyEvent.KEYCODE_BUTTON_L2
        val began = SystemClock.uptimeMillis()
        try {
            if (keyFirst) keyDown(key) else triggers(left = if (right) 0f else .8f, right = if (right) .8f else 0f)
            SystemClock.sleep(180)
            if (keyFirst) triggers(left = if (right) 0f else .8f, right = if (right) .8f else 0f) else keyDown(key)
            SystemClock.sleep(260)
        } finally {
            keyUp(key)
            triggers()
        }
        val elapsed = SystemClock.uptimeMillis() - began
        assertTrue("Injection took ${elapsed}ms; cannot isolate engagement from the 650ms hold threshold", elapsed < 620)
        compose.waitUntil(TIMEOUT_MS) { changes.isNotEmpty() }
        compose.waitForIdle()
        assertEquals("One physical squeeze must change the filter once: $changes", 1, changes.size)
        assertEquals(expected, changes.single().filter)
    }

    private fun filterKeys(): List<String> = library.state.value.let {
        collectionFilterKeys(LauncherDestination.LIBRARY, it.allItems, it.overrides, it.favorites)
    }

    private fun fullyVisibleFilterTags(): List<String> {
        val viewport = compose.onNodeWithTag(FILTER_STRIP).fetchSemanticsNode().boundsInRoot
        return compose.onAllNodes(hasAnyAncestor(hasTestTag(FILTER_STRIP)) and hasClickAction())
            .fetchSemanticsNodes().mapNotNull { node ->
                val tag = if (node.config.contains(SemanticsProperties.TestTag)) node.config[SemanticsProperties.TestTag] else ""
                val position = node.positionInRoot
                val size = node.size
                if (tag.startsWith("collection-filter-") && size.width > 0 &&
                    position.x >= viewport.left && position.x + size.width <= viewport.right &&
                    position.y >= viewport.top && position.y + size.height <= viewport.bottom) tag to position.x else null
            }.sortedBy { it.second }.map { it.first }
    }

    private fun filterStripScroll(): Float = compose.onNodeWithTag(FILTER_STRIP).fetchSemanticsNode()
        .config[SemanticsProperties.HorizontalScrollAxisRange].value()

    private fun assertChromeInsideNativeWindow(percent: Int) {
        val nativeSize = IntArray(2)
        compose.runOnIdle {
            nativeSize[0] = compose.activity.window.decorView.width
            nativeSize[1] = compose.activity.window.decorView.height
        }
        assertTrue("The real Activity window must be laid out", nativeSize.all { it > 0 })
        val tags = listOf(LauncherShellTags.Root, LauncherShellTags.Dock, LauncherShellTags.Footer) +
            LauncherDestination.dockOrder.map(LauncherShellTags::destination) +
            listOf(SemanticInputAction.CONFIRM, SemanticInputAction.SECONDARY)
                .map(LauncherShellTags::footerAction)
        for (tag in tags) {
            val target = compose.onNodeWithTag(tag)
            target.assertIsDisplayed()
            val node = target.fetchSemanticsNode()
            // boundsInRoot is clipped by ancestors and can hide an oversized layout. Use
            // its actual placed size/position to catch chrome extending past the window.
            val position = node.positionInRoot
            val size = node.size
            val bounds = Rect(position.x, position.y, position.x + size.width, position.y + size.height)
            assertTrue("$percent% $tag must fit the native ${nativeSize[0]}x${nativeSize[1]} window: $bounds",
                bounds.width > 0f && bounds.height > 0f && bounds.left >= -1f && bounds.top >= -1f &&
                    bounds.right <= nativeSize[0] + 1f && bounds.bottom <= nativeSize[1] + 1f)
        }
    }

    private fun cycleToFilter(target: String) {
        repeat(filterKeys().size + 1) {
            val before = library.state.value.filter
            if (before == target) return
            val keys = filterKeys()
            val targetIndex = keys.indexOf(target)
            val currentIndex = keys.indexOf(before)
            assertTrue("Both current and target filters must remain available: $before -> $target", currentIndex >= 0 && targetIndex >= 0)
            press(if (targetIndex > currentIndex) KeyEvent.KEYCODE_BUTTON_R2 else KeyEvent.KEYCODE_BUTTON_L2)
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
        !imeIsVisible()
    }

    private fun imeIsVisible(): Boolean = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
        ?.isVisible(WindowInsetsCompat.Type.ime()) == true

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
        const val FILTER_ALL = "collection-filter-all"
        const val FILTER_STRIP = "collection-filter-strip"
        const val TIMEOUT_MS = 30_000L
    }
}
