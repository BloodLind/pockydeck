package dev.handheld.launcher.debug

import android.animation.ValueAnimator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme

/** Debug-only visual evidence surface, never registered by the release manifest. */
class ThemePreviewActivity : ComponentActivity() {
    private var reducedMotion by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme(reducedMotion = reducedMotion) {
                ThemeSamples()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reducedMotion = !ValueAnimator.areAnimatorsEnabled()
    }
}

@Composable
private fun ThemeSamples() {
    val colors = LauncherTheme.colors
    val type = LauncherTheme.typography
    val spacing = LauncherTheme.spacing
    val motion = LauncherTheme.motion
    Column(
        Modifier.fillMaxSize()
            .background(colors.backgroundGradient())
            .verticalScroll(rememberScrollState())
            .padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        SampleText("Home theme", type.homeTitle, colors.textPrimary)
        SampleText(
            "Bundled Plus Jakarta Sans · Android font scale ${LocalDensity.current.fontScale}",
            type.body,
            colors.textSecondary,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            SamplePanel("Card surface", colors.surfaceCard, Modifier.weight(1f)) {
                SampleText("ExtraBold 800", type.tileTitleLarge, colors.textPrimary)
                SampleText("Primary text", type.body, colors.textPrimary)
                SampleText("Secondary text", type.body, colors.textSecondary)
                SampleText("Action label", type.actionLabel, colors.textSecondary)
            }
            SamplePanel("Dock surface", colors.surfaceDock, Modifier.weight(1f)) {
                SampleText("Page title", type.pageTitle, colors.textPrimary)
                SampleText("Setting label", type.settingLabel, colors.textSecondary)
                SampleText("Status value", type.statusValue, colors.textPrimary)
                SampleText("Control label", type.controlLabel, colors.textPrimary)
            }
            SamplePanel("Selected surface", colors.destinationSelected, Modifier.weight(1f), colors.destinationSelectedContent) {
                SampleText("Selected", type.tileTitleLarge, colors.destinationSelectedContent)
                SampleText("Text stays readable", type.body, colors.destinationSelectedContent)
            }
        }
        SampleText(
            "Reduced motion: ${motion.reducedMotion} · Focus ${motion.focusDurationMillis} ms · Press ${motion.pressedDurationMillis} ms",
            type.body,
            colors.textSecondary,
        )
        SampleText(
            "Visual test surface · Native size calibration is pending · No catalog or device status fixtures",
            type.actionLabel,
            colors.textSecondary,
        )
    }
}

@Composable
private fun SamplePanel(
    title: String,
    background: Color,
    modifier: Modifier,
    foreground: Color = LauncherTheme.colors.textPrimary,
    content: @Composable () -> Unit,
) {
    Column(
        modifier.background(background, RoundedCornerShape(LauncherTheme.shapes.smallControl))
            .padding(LauncherTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm),
    ) {
        SampleText(title, LauncherTheme.typography.controlLabel, foreground)
        content()
    }
}

@Composable
private fun SampleText(text: String, style: TextStyle, color: Color) {
    BasicText(text, style = style.copy(color = color))
}
