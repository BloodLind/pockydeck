package dev.handheld.launcher.core.designsystem.foundation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme

/** Replaces the previous transition on each change; repeated input never queues animations. */
@Composable
fun contentEntrance(key: Any): Modifier {
    val duration = LauncherTheme.motion.transitionDurationMillis
    val progress = remember(key, duration) { Animatable(if (duration == 0) 1f else 0f) }
    LaunchedEffect(progress) { progress.animateTo(1f, tween(duration, easing = FastOutSlowInEasing)) }
    return Modifier.graphicsLayer { alpha = progress.value }
}
