package dev.handheld.launcher.core.designsystem.foundation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.glyphs.LauncherFaceButton
import dev.handheld.launcher.core.designsystem.glyphs.LauncherFaceGlyph
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyph
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyphIcon
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LauncherPrimitivesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun immediateFocusIsSeparateFromSelectionAndStaysInsideAllocation() = verifyFocus(false)
    @Test fun reducedMotionKeepsImmediateFrameWithoutDecorativeLift() = verifyFocus(true)

    private fun verifyFocus(reducedMotion: Boolean) {
        val firstRequester = FocusRequester()
        val secondRequester = FocusRequester()
        lateinit var focusManager: FocusManager
        lateinit var contentBounds: Rect
        var focusColor = Color.Unspecified
        var lowerEdgeColor = Color.Unspecified
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LauncherTheme(reducedMotion = reducedMotion) {
                focusManager = LocalFocusManager.current
                focusColor = LauncherTheme.colors.focus
                lowerEdgeColor = LauncherTheme.colors.focusLowerEdge
                var firstFocused by remember { mutableStateOf(false) }
                var secondFocused by remember { mutableStateOf(false) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusFrame(
                        modifier = Modifier.size(80.dp).testTag("first")
                            .onFocusChanged { firstFocused = it.isFocused }
                            .focusRequester(firstRequester).focusable(),
                        selected = true, focused = firstFocused,
                        contentDescription = "Home",
                    ) {
                        Box(Modifier.fillMaxSize().onGloballyPositioned { contentBounds = it.boundsInRoot() })
                    }
                    FocusFrame(
                        modifier = Modifier.size(80.dp).testTag("second")
                            .onFocusChanged { secondFocused = it.isFocused }
                            .focusRequester(secondRequester).focusable(),
                        focused = secondFocused, contentDescription = "Search",
                    ) {}
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { secondRequester.requestFocus() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("first").assertIsSelected().assertIsNotFocused()
        assertEquals(0, focusPixels("first", focusColor))
        val allocation = compose.onNodeWithTag("first").fetchSemanticsNode().boundsInRoot
        val neutralContent = compose.runOnIdle { contentBounds }

        compose.runOnIdle { firstRequester.requestFocus() }
        // One frame: an animated 200ms focus outline would not meet this immediate-color check.
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("first").assertIsFocused().assertIsSelected()
        assertTrue(focusPixels("first", focusColor) > 0)
        assertTrue("The focused lower edge remains inside the allocation", focusPixels("first", lowerEdgeColor) > 0)
        assertEquals(allocation, compose.onNodeWithTag("first").fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(80)
        val intermediateContent = compose.runOnIdle { contentBounds }
        compose.mainClock.advanceTimeBy(160)
        val focusedContent = compose.runOnIdle { contentBounds }
        assertContained(allocation, neutralContent)
        assertContained(allocation, focusedContent)
        assertEquals(focusedContent.width, focusedContent.height, .1f)
        if (reducedMotion) {
            assertEquals(neutralContent, intermediateContent)
            assertEquals(neutralContent, focusedContent)
        } else {
            assertTrue(intermediateContent.top < neutralContent.top)
            assertTrue(intermediateContent.top > focusedContent.top)
        }

        compose.runOnIdle { assertTrue(focusManager.moveFocus(FocusDirection.Right)) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("second").assertIsFocused()
        compose.onNodeWithTag("first").assertIsNotFocused().assertIsSelected()
        assertEquals(0, focusPixels("first", focusColor))
    }

    @Test
    fun anUnfocusedCardRetainsItsOutline() {
        var borderColor = Color.Unspecified
        compose.setContent {
            LauncherTheme {
                borderColor = LauncherTheme.colors.borderEmphasis.compositeOver(LauncherTheme.colors.surfaceCard)
                FocusFrame(Modifier.size(100.dp).testTag("outlined")) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        assertTrue("An unfocused card keeps a visible border", focusPixels("outlined", borderColor) > 20)
    }

    @Test
    fun decorationPreservesParentActionAndMeaningfulGlyphsAvoidDuplicateText() {
        compose.setContent {
            LauncherTheme {
                Column {
                    LauncherSurface(
                        modifier = Modifier.testTag("parent").clickable {},
                        contentDescription = "Favorite action",
                    ) {
                        LauncherGlyphIcon(
                            LauncherGlyph.Favorites, Modifier.size(24.dp).testTag("decoration"),
                            contentDescription = null,
                        )
                    }
                    LauncherGlyphIcon(LauncherGlyph.Home, contentDescription = "Home destination")
                    LauncherFaceGlyph(LauncherFaceButton.B, semanticLabel = "Confirm")
                }
            }
        }
        compose.onNodeWithTag("parent").assertIsDisplayed().assertHasClickAction()
            .assertContentDescriptionEquals("Favorite action")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.InvisibleToUser))
        compose.onNodeWithTag("decoration", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        compose.onNodeWithContentDescription("Favorites").assertDoesNotExist()
        compose.onNodeWithContentDescription("Home destination").assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        compose.onNodeWithContentDescription("Confirm").assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        compose.onNodeWithText("B").assertDoesNotExist()
    }

    @Test
    fun largeFontWrapsWithoutOverflowAndAdjacentTargetsKeep48DpBounds() {
        val label = "A long native title remains readable when the user increases the system font size."
        compose.setContent {
            LauncherTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LauncherSurface(Modifier.size(48.dp).testTag("left").clickable {}) {
                                LauncherGlyphIcon(LauncherGlyph.Home, Modifier.size(24.dp), null)
                            }
                            LauncherSurface(Modifier.size(24.dp).testTag("right").clickable {}) {
                                LauncherGlyphIcon(LauncherGlyph.Apps, Modifier.size(24.dp), null)
                            }
                        }
                        LauncherText(label, Modifier.width(240.dp))
                        LauncherSurface(
                            modifier = Modifier.testTag("unavailable"), enabled = false,
                            unavailable = true, contentDescription = "Missing item",
                        ) { LauncherText("Still readable") }
                    }
                }
            }
        }
        listOf("left", "right").forEach { tag ->
            compose.onNodeWithTag(tag).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        val left = compose.onNodeWithTag("left").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("right").fetchSemanticsNode().boundsInRoot
        assertTrue(left.right <= right.left)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().lineCount > 1)
        assertFalse(layouts.single().hasVisualOverflow)
        compose.onNodeWithTag("unavailable").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Unavailable"))
    }

    private fun focusPixels(tag: String, expected: Color): Int {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return (0 until pixels.height).sumOf { y ->
            (0 until pixels.width).count { x ->
                val pixel = pixels[x, y]
                kotlin.math.abs(pixel.red - expected.red) < .01f &&
                    kotlin.math.abs(pixel.green - expected.green) < .01f &&
                    kotlin.math.abs(pixel.blue - expected.blue) < .01f
            }
        }
    }

    private fun assertContained(outer: Rect, inner: Rect) {
        assertTrue(inner.left >= outer.left && inner.top >= outer.top)
        assertTrue(inner.right <= outer.right && inner.bottom <= outer.bottom)
    }
}
