package dev.handheld.launcher.debug

import android.animation.ValueAnimator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.handheld.launcher.catalog.HomeGalleryFixture
import dev.handheld.launcher.catalog.LauncherControlGallery
import dev.handheld.launcher.core.designsystem.foundation.ShellMetrics
import dev.handheld.launcher.core.designsystem.foundation.ShellMetricsInput
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme

/** Debug-only controls/reference content; production routes never receive these fixtures. */
class ControlGalleryActivity : ComponentActivity() {
    private var reducedMotion by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        val section = intent.getStringExtra("section") ?: "controls"
        val compact = intent.getBooleanExtra("compact", false)
        val variant = intent.getStringExtra("variant") ?: "normal"
        setContent {
            BoxWithConstraints(
                Modifier.imePadding()
                    .then(if (compact) Modifier.widthIn(max = 600.dp).heightIn(max = 340.dp) else Modifier)
                    .fillMaxSize(),
            ) {
                val density = LocalDensity.current
                val metrics = remember(maxWidth, maxHeight, density.density, density.fontScale) {
                    with(density) {
                        ShellMetrics.calculate(ShellMetricsInput(
                            maxWidth.roundToPx(), maxHeight.roundToPx(), this.density, fontScale,
                        ))
                    }
                }
                LauncherTheme(reducedMotion, metrics.referenceScale) {
                    Box(Modifier.fillMaxSize().background(LauncherTheme.colors.backgroundGradient())) {
                        if (section == "home") {
                            HomeGalleryFixture(metrics, Modifier
                                .offset(metrics.contentBounds.left, metrics.contentBounds.top)
                                .size(metrics.contentBounds.width, metrics.contentBounds.height), variant)
                        } else {
                            LauncherControlGallery(metrics, section)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reducedMotion = !ValueAnimator.areAnimatorsEnabled()
    }
}
