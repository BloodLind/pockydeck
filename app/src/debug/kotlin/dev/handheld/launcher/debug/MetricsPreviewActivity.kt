package dev.handheld.launcher.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.foundation.ShellBounds
import dev.handheld.launcher.core.designsystem.foundation.ShellMetrics
import dev.handheld.launcher.core.designsystem.foundation.ShellMetricsInput
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme

/** Isolated metrics fixture; it is not the launcher shell or its navigation implementation. */
class MetricsPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val compactFixture = intent.getBooleanExtra("compact", false)
        val destination = intent.getStringExtra("destination") ?: "Home"
        setContent {
            LauncherTheme {
                BoxWithConstraints(
                    Modifier.fillMaxSize().background(LauncherTheme.colors.backgroundGradient()),
                    contentAlignment = Alignment.Center,
                ) {
                    val fixtureWidth = if (compactFixture) minOf(maxWidth, 800.dp) else maxWidth
                    val fixtureHeight = if (compactFixture) minOf(maxHeight, 480.dp) else maxHeight
                    val density = LocalDensity.current
                    val metrics = with(density) {
                        ShellMetrics.calculate(ShellMetricsInput(fixtureWidth.roundToPx(), fixtureHeight.roundToPx(), this.density, fontScale))
                    }
                    LauncherTheme(referenceScale = metrics.referenceScale) {
                        MetricsFixture(metrics, destination, Modifier.size(fixtureWidth, fixtureHeight))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricsFixture(metrics: ShellMetrics, destination: String, modifier: Modifier) {
    val colors = LauncherTheme.colors
    val type = LauncherTheme.typography
    Box(modifier.border(1.dp, colors.borderEmphasis)) {
        FixtureBand(metrics.statusBounds, colors.surfaceApp) {
            BasicText(
                "$destination fixture · ${metrics.windowWidth.value.toInt()} × ${metrics.windowHeight.value.toInt()} dp · font ${metrics.fontScale}",
                style = type.body.copy(color = colors.textSecondary),
            )
        }
        Column(
            Modifier.offset(metrics.contentBounds.left, metrics.homeMetadataTop)
                .size(metrics.contentBounds.width, metrics.metadataReservation),
        ) {
            BasicText("SHARED NATIVE METRICS", style = type.platformLabel.copy(color = colors.focus))
            BasicText(
                "Readable titles and one shared geometry for $destination",
                modifier = Modifier.padding(top = LauncherTheme.spacing.sm),
                style = type.homeTitle.copy(color = colors.textPrimary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (metrics.hasUsableHomeCard) {
            val slot = metrics.homeCardAllocatedBounds
            Box(
                Modifier.offset(slot.left, slot.top).size(slot.width, slot.height)
                    .border(1.dp, colors.borderEmphasis),
            ) {
                val outer = metrics.homeCardArtworkSize + metrics.focusFrameReservation * 2f
                Box(
                    Modifier.offset(x = metrics.focusLiftReservation)
                        .size(outer)
                        .border(metrics.focusFrameReservation, colors.focus, RoundedCornerShape(LauncherTheme.shapes.homeOuter))
                        .padding(metrics.focusFrameReservation)
                        .background(colors.surfaceArtwork, RoundedCornerShape(LauncherTheme.shapes.homeInner)),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("Artwork", style = type.controlLabel.copy(color = colors.textPrimary), maxLines = 1)
                }
            }
        }
        Box(Modifier.offset(y = metrics.dockCenterY - 24.dp).fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xxs)) {
                repeat(6) { index ->
                    Box(
                        Modifier.size(metrics.minimumTouchTarget),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(48.dp * metrics.referenceScale)
                            .background(if (index == 0) colors.destinationSelected else colors.dockInactive, CircleShape),
                            contentAlignment = Alignment.Center) {
                            BasicText("${index + 1}", style = type.controlLabel.copy(
                                color = if (index == 0) colors.destinationSelectedContent else colors.textPrimary,
                            ))
                        }
                    }
                }
            }
        }
        Box(Modifier.offset(y = metrics.footerDividerY).fillMaxWidth().height(1.dp).background(colors.borderEmphasis))
        Box(Modifier.offset(y = metrics.footerDividerY).fillMaxWidth().height(metrics.windowHeight - metrics.footerDividerY), contentAlignment = Alignment.Center) {
            BasicText(
                "48dp targets · Native reference scale · Separate visual and interaction anchors",
                style = type.actionLabel.copy(color = colors.textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FixtureBand(bounds: ShellBounds, color: Color, content: @Composable () -> Unit) {
    Box(
        Modifier.offset(bounds.left, bounds.top).size(bounds.width, bounds.height)
            .background(color).border(1.dp, LauncherTheme.colors.borderEmphasis),
        contentAlignment = Alignment.Center,
    ) { content() }
}
