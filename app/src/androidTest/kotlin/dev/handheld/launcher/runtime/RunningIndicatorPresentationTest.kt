package dev.handheld.launcher.runtime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import dev.handheld.launcher.ui.components.LibraryItemCard
import dev.handheld.launcher.ui.components.LibraryItemCardVariant
import dev.handheld.launcher.ui.presentation.toTileUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunningIndicatorPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun appStatusUsesFullCardSquareAtEachUiScaleInsteadOfTheFittedIcon() {
        val app = LibraryItem.AndroidApp(CurrentUserAndroidComponentId("fixture.eden", "fixture.eden.Main"),
            "Eden", LibraryCategory.EMULATOR, Availability.Available, setOf(SupportedItemAction.OPEN))
        var scale by mutableFloatStateOf(1f)
        var variant by mutableStateOf(LibraryItemCardVariant.Home)
        var actualDensity = 1f
        compose.setContent {
            Box(Modifier.size(350.dp, 300.dp)) {
                actualDensity = LocalDensity.current.density * scale
                CompositionLocalProvider(
                    LocalDensity provides Density(actualDensity, 1.3f),
                    LocalRunningLabels provides mapOf(app.id to "Running"),
                ) {
                    LauncherTheme(reducedMotion = true, referenceScale = (2f / 3f) / scale, uiScaleFactor = scale) {
                        LibraryItemCard(app.toTileUiModel(), variant, selected = false,
                            modifier = (if (variant == LibraryItemCardVariant.Home) Modifier.size(160.dp) else Modifier.width(160.dp))
                                .testTag("running-app-card"), onActivate = {})
                    }
                }
            }
        }
        for (cardVariant in listOf(LibraryItemCardVariant.Home, LibraryItemCardVariant.Collection)) {
            for (percent in listOf(100, 110, 120)) {
                compose.runOnIdle { variant = cardVariant; scale = percent / 100f }
                compose.onNodeWithContentDescription("Live process detected: Running", useUnmergedTree = true).assertIsDisplayed()
                assertFullLabel("Running", "$cardVariant/$percent%")
                val status = compose.onNodeWithContentDescription("Live process detected: Running", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val card = compose.onNodeWithTag("running-app-card").fetchSemanticsNode().boundsInRoot
                val iconLimit = if (cardVariant == LibraryItemCardVariant.Home) 80f * 2f / 3f else 56f
                assertTrue("$cardVariant at $percent% lets the badge extend beyond the fitted icon's width",
                    status.width > iconLimit * actualDensity)
                assertTrue("$cardVariant at $percent% places status at the square's top edge", status.top < card.top + card.height * .22f)
                assertTrue("$cardVariant at $percent% keeps the whole badge inside the card", fits(status, card))
                compose.onAllNodes(hasClickAction()).assertCountEquals(1)
            }
        }
    }

    @Test fun listRomStatusKeepsFullEmulatorLabelBesideArtworkAndOneStableActivationTarget() {
        val rom = LibraryItem.RomGame(ItemId("fixture:running-rom"), "Sample GBA game", CatalogSourceId("fixture:roms"),
            Availability.Available, setOf(SupportedItemAction.OPEN), "gba", "gba")
        val label = "RetroArch (64-bit) running"
        var labels by mutableStateOf(mapOf(rom.id to label))
        var activations = 0
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 1.3f),
                LocalRunningLabels provides labels,
            ) {
                LauncherTheme(reducedMotion = true, referenceScale = 2f / 3f) {
                    LibraryItemCard(rom.toTileUiModel(), LibraryItemCardVariant.SearchResult, selected = false,
                        modifier = Modifier.width(320.dp).testTag("running-rom-row"), onActivate = { activations++ })
                }
            }
        }
        assertFullLabel(label)
        val status = compose.onNodeWithContentDescription("Live process detected: $label", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val console = compose.onNodeWithContentDescription("GBA", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag("running-rom-row").fetchSemanticsNode()
        assertTrue("The status uses caption width beside the image", status.left > console.right)
        assertTrue("The full status fits the row", fits(status, card.boundsInRoot))
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithTag("running-rom-row").performClick()
        compose.runOnIdle { assertEquals(1, activations); labels = emptyMap() }
        compose.onNodeWithContentDescription("Live process detected: $label", useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        assertEquals("Status updates preserve the card's activation target", card.id,
            compose.onNodeWithTag("running-rom-row").fetchSemanticsNode().id)
    }

    private fun assertFullLabel(label: String, context: String = "List") {
        val layouts = mutableListOf<TextLayoutResult>()
        val text = compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals(label, layout.layoutInput.text.text)
        val bounds = text.fetchSemanticsNode().boundsInRoot
        val lines = (0 until layout.lineCount).map {
            "${layout.getLineStart(it)}..${layout.getLineEnd(it)} bottom=${layout.getLineBottom(it)} ellipsis=${layout.isLineEllipsized(it)}"
        }
        val diagnostic = "$context/$label; size=${layout.size}, paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, " +
            "bounds=$bounds, lines=$lines"
        // Compose 1.7 reconstructs the semantics paragraph at its parent's maximum
        // width, even when compact text uses its intrinsic width. Check the rendered
        // characters instead of treating that unused paragraph width as clipping.
        for (line in 0 until layout.lineCount) {
            assertFalse("No status line may be ellipsized: $diagnostic", layout.isLineEllipsized(line))
        }
        assertEquals("The last visible line reaches the complete label: $diagnostic", label.length,
            layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        val visibleText = Rect(0f, 0f, bounds.width, bounds.height)
        for (offset in label.indices) {
            val character = layout.getBoundingBox(offset)
            assertTrue("Status character $offset must fit the visible allocation: $character; $diagnostic",
                fits(character, visibleText))
        }
        assertTrue("Every laid-out status line is visible", layout.getLineBottom(layout.lineCount - 1) <= bounds.height + 1f)
    }

    private fun fits(child: Rect, parent: Rect) = child.left >= parent.left - 1f && child.top >= parent.top - 1f &&
        child.right <= parent.right + 1f && child.bottom <= parent.bottom + 1f
}
