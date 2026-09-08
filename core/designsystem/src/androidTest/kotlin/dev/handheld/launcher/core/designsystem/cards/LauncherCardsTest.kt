package dev.handheld.launcher.core.designsystem.cards

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.handheld.launcher.core.designsystem.controls.ControllerGlyph
import dev.handheld.launcher.core.designsystem.controls.PlatformBadge
import dev.handheld.launcher.core.designsystem.controls.StatusIndicator
import dev.handheld.launcher.core.designsystem.controls.StatusValue
import dev.handheld.launcher.core.designsystem.foundation.ShellMetrics
import dev.handheld.launcher.core.designsystem.foundation.ShellMetricsInput
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class LauncherCardsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun homeArtworkChangesInsideTheSameSquareAndFocusDoesNotActivate() {
        var artworkState by mutableStateOf(0)
        var activations = 0
        val requester = FocusRequester()
        val metrics = ShellMetrics.calculate(ShellMetricsInput(1920, 1080, 2.25f))
        lateinit var inputMode: androidx.compose.ui.input.InputModeManager
        var actualDensity = 1f
        compose.setContent {
            LauncherTheme(referenceScale = metrics.referenceScale) {
                inputMode = LocalInputModeManager.current
                actualDensity = LocalDensity.current.density
                CoverTile("Long Home title", CardVariant.HomeCover, true, { activations++ }, {},
                    Modifier.size(metrics.homeCardAllocatedSize).focusRequester(requester).testTag("home"),
                    artwork = {
                        Box(Modifier.fillMaxSize().testTag("image")) {
                            when (artworkState) {
                                0 -> ArtworkFallback(label = "Loading artwork")
                                1 -> ArtworkFallback()
                                else -> CoverArtwork(StripedPainter())
                            }
                        }
                    }, focusFrameWidth = metrics.focusFrameReservation,
                    focusLift = metrics.focusLiftReservation)
            }
        }
        val allocation = compose.onNodeWithTag("home").fetchSemanticsNode().boundsInRoot
        val imageBounds = compose.onNodeWithTag("image", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(imageBounds.width, imageBounds.height, 1f)
        assertEquals(metrics.homeCardArtworkSize.value * actualDensity, imageBounds.width, 1.5f)
        compose.onNodeWithText("Long Home title").assertDoesNotExist() // metadata belongs above Home's row
        compose.runOnIdle {
            artworkState = 1
            inputMode.requestInputMode(InputMode.Keyboard)
            requester.requestFocus()
        }
        compose.onNodeWithTag("home").assertIsFocused().assertHasClickAction()
        compose.runOnIdle { assertEquals(0, activations) }
        compose.onNodeWithText("Artwork unavailable").assertExists()
        assertEquals(allocation, compose.onNodeWithTag("home").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { artworkState = 2 }
        assertEquals(allocation, compose.onNodeWithTag("home").fetchSemanticsNode().boundsInRoot)
        val loaded = compose.onNodeWithTag("image", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(imageBounds.width, loaded.width, 1f)
        assertEquals(imageBounds.height, loaded.height, 1f)
        compose.onNodeWithTag("home").performClick()
        compose.runOnIdle { assertEquals(1, activations) }
    }

    @Test
    fun collectionCaptionKeepsItsHeightAndNativeAppAndSearchVariantsRender() {
        var title by mutableStateOf("Short")
        var subtitle by mutableStateOf<String?>(null)
        compose.setContent {
            LauncherTheme(referenceScale = 2f / 3f) {
                Column(Modifier.fillMaxSize().background(LauncherTheme.colors.backgroundGradient()).padding(20.dp)) {
                    PlatformBadge("ANDROID", homeAccent = true)
                    Row {
                        CoverTile(title, CardVariant.CollectionCover, true, {}, {},
                            Modifier.width(160.dp).testTag("collection"),
                            artwork = { CoverArtwork(StripedPainter(), Modifier.testTag("collection-image")) },
                            subtitle = subtitle)
                        AppIconTile("Dolphin Emulator", true, {}, {}, Modifier.size(165.dp).testTag("app"),
                            icon = { AppIconArtwork(StripedPainter(), Modifier.testTag("app-image")) })
                        SearchResultCard("A local search result", "Android application", true, {}, {},
                            Modifier.width(280.dp).testTag("search"), artwork = { AppIconArtwork(StripedPainter()) })
                    }
                    StatusIndicator(StatusValue.Available("10:42"), label = "Clock")
                    StatusIndicator(StatusValue.Unavailable("Sensor unavailable"), label = "Temperature")
                    StatusIndicator(StatusValue.Unsupported, label = "Unsupported status")
                    ControllerGlyph("L1", "Previous destination")
                }
            }
        }
        val before = compose.onNodeWithTag("collection").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            title = "A long collection title that needs two bounded lines and stays within this card"
            subtitle = "A long secondary description that remains on a single line"
        }
        assertEquals(before, compose.onNodeWithTag("collection").fetchSemanticsNode().boundsInRoot)
        val artwork = compose.onNodeWithTag("collection-image", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(artwork.width, artwork.height, 1f)
        val caption = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Collection captions are below the artwork", caption.top >= artwork.bottom)
        val icon = compose.onNodeWithTag("app-image", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(icon.width, icon.height, 1f)
        val app = compose.onNodeWithTag("app").fetchSemanticsNode().boundsInRoot
        assertTrue(icon.width < app.width * .5f)
        compose.onNodeWithTag("app").assertHasClickAction()
        compose.onNodeWithTag("search").assertHasClickAction()
        compose.onNodeWithContentDescription("Clock: 10:42").assertExists()
        compose.onNodeWithContentDescription("Temperature: Sensor unavailable")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Sensor unavailable"))
        compose.onNodeWithContentDescription("Unsupported status").assertDoesNotExist()
        compose.onNodeWithContentDescription("Previous destination").assertExists()
        compose.onNodeWithText("L1").assertDoesNotExist()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val output = File(context.getExternalFilesDir(null), "us008-cards.png")
        output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun appAndRomCollectionCardsShareSquareArtworkAndCaptionAllocation() {
        compose.setContent {
            LauncherTheme(referenceScale = 2f / 3f) {
                Row {
                    CoverTile("Collection game", CardVariant.CollectionCover, true, {}, {},
                        Modifier.width(160.dp).testTag("collection-rom"), artwork = { ArtworkFallback() })
                    AppIconTile("Collection application", true, {}, {}, Modifier.width(160.dp).testTag("collection-app"),
                        icon = { AppIconArtwork(StripedPainter()) }, showCaption = true)
                }
            }
        }
        val rom = compose.onNodeWithTag("collection-rom").fetchSemanticsNode().boundsInRoot
        val app = compose.onNodeWithTag("collection-app").fetchSemanticsNode().boundsInRoot
        assertEquals("App and ROM collections reserve the same width", rom.width, app.width, 1f)
        assertEquals("App and ROM collections reserve the same square and caption height", rom.height, app.height, 1f)
        val romCaption = compose.onNodeWithText("Collection game", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val appCaption = compose.onNodeWithText("Collection application", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(romCaption.top >= rom.top + rom.width)
        assertTrue(appCaption.top >= app.top + app.width)
    }

    @Test
    fun completedTouchFocusesBeforeActivationAndScrollingDoesNotActivate() {
        var focused = false
        var focusedAtActivation = false
        var activations = 0
        lateinit var scroll: ScrollState
        compose.setContent {
            LauncherTheme {
                scroll = rememberScrollState()
                Row(Modifier.width(240.dp).horizontalScroll(scroll)) {
                    CoverTile("Touch target", CardVariant.HomeCover, true,
                        { focusedAtActivation = focused; activations++ }, { focused = it },
                        Modifier.size(160.dp).testTag("touch-card"), artwork = { ArtworkFallback() })
                    CoverTile("Another target", CardVariant.HomeCover, true, { activations++ }, {},
                        Modifier.size(160.dp), artwork = { ArtworkFallback() })
                }
            }
        }
        compose.onNodeWithTag("touch-card").performTouchInput { click() }
        compose.onNodeWithTag("touch-card").assertIsFocused()
        compose.runOnIdle {
            assertTrue("The launch callback sees the newly focused item", focusedAtActivation)
            assertEquals(1, activations)
        }
        compose.onNodeWithTag("touch-card").performTouchInput { swipeLeft() }
        compose.runOnIdle {
            assertTrue("The gesture scrolls the row", scroll.value > 0)
            assertEquals("A scroll gesture does not launch a card", 1, activations)
        }
    }

    @Test
    fun coverCropsAndNativeIconFitsANonSquarePainterWithoutStretching() {
        compose.setContent {
            LauncherTheme {
                Row {
                    Box(Modifier.size(100.dp).background(Color.Black).testTag("fit")) { AppIconArtwork(StripedPainter()) }
                    Box(Modifier.size(100.dp).background(Color.Black).testTag("crop")) { CoverArtwork(StripedPainter()) }
                }
            }
        }
        val fit = compose.onNodeWithTag("fit").captureToImage().toPixelMap()
        val crop = compose.onNodeWithTag("crop").captureToImage().toPixelMap()
        assertEquals(Color.Black, fit[fit.width / 2, fit.height / 10])
        assertEquals(Color.Red, fit[fit.width / 10, fit.height / 2])
        assertEquals(Color.Green, crop[crop.width / 10, crop.height / 2])
        assertEquals(Color.Green, crop[crop.width / 2, crop.height / 10])
    }

    private class StripedPainter : Painter() {
        override val intrinsicSize = Size(200f, 100f)
        override fun DrawScope.onDraw() {
            drawRect(Color.Green)
            drawRect(Color.Red, size = Size(size.width / 4f, size.height))
            drawRect(Color.Blue, topLeft = Offset(size.width * .75f, 0f), size = Size(size.width / 4f, size.height))
        }
    }
}
