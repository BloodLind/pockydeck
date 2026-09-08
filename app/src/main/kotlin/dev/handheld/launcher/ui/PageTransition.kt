package dev.handheld.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** One live page: replaced controls disappear immediately while the new page fades/slides in. */
@Composable
internal fun pageTransition(key: Any, reducedMotion: Boolean): Modifier {
    val progress = remember(key, reducedMotion) { Animatable(if (reducedMotion) 1f else 0f) }
    val distance = with(LocalDensity.current) { 8.dp.toPx() }
    LaunchedEffect(progress) {
        if (!reducedMotion) progress.animateTo(1f, tween(160, easing = FastOutSlowInEasing))
    }
    return Modifier.graphicsLayer {
        alpha = progress.value
        translationY = distance * (1f - progress.value)
    }
}
