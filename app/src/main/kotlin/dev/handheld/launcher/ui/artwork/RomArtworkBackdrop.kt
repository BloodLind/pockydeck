package dev.handheld.launcher.ui.artwork

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.ui.artwork.BACKDROP_SIZE_PX
import dev.handheld.launcher.ui.artwork.LocalArtworkLoadingAllowed
import dev.handheld.launcher.ui.artwork.enriched.rememberEnrichedArtwork
import dev.handheld.launcher.ui.presentation.TileArtwork
import dev.handheld.launcher.ui.presentation.TileUiModel

internal fun romBackdropEligible(model: TileUiModel?): Boolean = model?.isRom == true &&
    (model.artwork is TileArtwork.Rom || model.artwork is TileArtwork.LocalReference)

/** Decorative artwork shared by Home and lists. Navigation gates image work, not the reveal. */
@Composable
internal fun RomArtworkBackdrop(selected: TileUiModel?, enabled: Boolean, loadingAllowed: Boolean, testTag: String) {
    if (!enabled || !romBackdropEligible(selected)) return
    var settled by remember { mutableStateOf<TileUiModel?>(null) }
    LaunchedEffect(selected?.itemId, selected?.artwork, loadingAllowed) {
        // The page already waits for navigation to settle. Do not add a second debounce here.
        if (loadingAllowed) settled = selected
    }
    var displayed by remember { mutableStateOf<TileUiModel?>(null) }
    CompositionLocalProvider(LocalArtworkLoadingAllowed provides loadingAllowed) {
        val model = settled
        val artwork = model?.let { rememberEnrichedArtwork(it, targetSizePx = BACKDROP_SIZE_PX, blurred = true) }
        LaunchedEffect(model, artwork?.painter, artwork?.pending) {
            if (artwork?.painter != null) displayed = model
            else if (artwork != null && !artwork.pending) displayed = null
        }
        val duration = if (LauncherTheme.motion.reducedMotion) 0 else 120
        // Keep the old layer composed (and its painter alive) until the new image is ready.
        // Navigation gates image work, never snaps or cancels this short reveal.
        Crossfade(displayed, Modifier.fillMaxSize().clipToBounds(),
            animationSpec = tween(duration, easing = FastOutSlowInEasing), label = "ROM backdrop") { ready ->
            if (ready != null) {
                val painter = rememberEnrichedArtwork(ready, targetSizePx = BACKDROP_SIZE_PX, blurred = true).painter
                if (painter != null) Box(Modifier.fillMaxSize().testTag(testTag).clearAndSetSemantics { }) {
                    Image(painter, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = .7f; scaleX = 1.08f; scaleY = 1.08f })
                    // Stronger shading beneath the title and footer keeps contrast predictable.
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                        Color(0xB31C1D2A), Color(0x751C1D2A), Color(0xCC1C1D2A)))))
                }
            }
        }
    }
}
