package dev.handheld.launcher.core.designsystem.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.glyphs.LauncherFaceButton
import dev.handheld.launcher.core.designsystem.glyphs.LauncherFaceGlyph
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.foundation.launcherContentColor
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme

@Composable
fun PlatformBadge(
    label: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = label,
    homeAccent: Boolean = false,
) {
    val colors = LauncherTheme.colors
    val shape = RoundedCornerShape(LauncherTheme.shapes.smallControl)
    val scale = LauncherTheme.referenceScale
    Row(
        modifier.clearAndSetSemantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        }.background(if (homeAccent) colors.focus.copy(alpha = .14f) else colors.surfaceDock, shape)
            .then(if (homeAccent) Modifier.border(1.dp * scale, colors.focus.copy(alpha = .45f), shape) else Modifier)
            .padding(horizontal = LauncherTheme.spacing.xs + if (homeAccent) LauncherTheme.spacing.xxs / 2 else 0.dp,
                vertical = LauncherTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (homeAccent) {
            Box(Modifier.size(6.dp * scale).background(colors.focus, CircleShape))
            Spacer(Modifier.width(LauncherTheme.spacing.xxs * 1.5f))
        }
        LauncherText(label, style = if (homeAccent) LauncherTheme.typography.platformLabel else LauncherTheme.typography.badgeLabel,
            color = if (homeAccent) colors.focus else colors.textPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Presentation-only states; application/domain status is mapped by the shell. */
sealed interface StatusValue {
    data class Available(val value: String) : StatusValue
    data class Unavailable(val reason: String = "Temporarily unavailable") : StatusValue
    data object Unsupported : StatusValue
}

@Composable
fun StatusIndicator(value: StatusValue, modifier: Modifier = Modifier, label: String? = null) {
    if (value is StatusValue.Unsupported) return
    val text = if (value is StatusValue.Available) value.value else "—"
    val description = listOfNotNull(label, when (value) {
        is StatusValue.Available -> value.value
        is StatusValue.Unavailable -> value.reason
        StatusValue.Unsupported -> null
    }).joinToString(": ")
    LauncherText(text, modifier = modifier.clearAndSetSemantics {
        contentDescription = description
        if (value is StatusValue.Unavailable) stateDescription = value.reason
    }, style = LauncherTheme.typography.statusValue,
        color = if (value is StatusValue.Unavailable) LauncherTheme.colors.textSecondary else launcherContentColor(),
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Raw physical legends. The shell supplies their current meaning and activation target. */
@Composable
fun ControllerGlyph(glyph: String, semanticLabel: String?, modifier: Modifier = Modifier) {
    val button = LauncherFaceButton.entries.firstOrNull { it.legend.equals(glyph, ignoreCase = true) }
    if (button != null) {
        val colors = LauncherTheme.colors
        val scale = LauncherTheme.referenceScale
        val background = when (button) {
            LauncherFaceButton.A -> colors.confirm
            LauncherFaceButton.B -> colors.cancel
            else -> colors.destinationSelected
        }
        val shape = if (button == LauncherFaceButton.Start) {
            RoundedCornerShape(LauncherTheme.shapes.smallControl)
        } else CircleShape
        Box(modifier.clearAndSetSemantics {
            if (semanticLabel != null) contentDescription = semanticLabel
        }.then(if (button == LauncherFaceButton.Start) Modifier.size(48.dp * scale, 24.dp * scale)
            else Modifier.size(28.dp * scale)).background(background, shape),
            contentAlignment = Alignment.Center) {
            LauncherFaceGlyph(button, semanticLabel = null,
                color = if (button == LauncherFaceButton.B) colors.textPrimary else colors.destinationSelectedContent)
        }
    } else {
        Box(modifier.clearAndSetSemantics {
            if (semanticLabel != null) contentDescription = semanticLabel
        }.background(LauncherTheme.colors.destinationSelected, RoundedCornerShape(LauncherTheme.shapes.smallControl))
            .padding(horizontal = LauncherTheme.spacing.xs, vertical = LauncherTheme.spacing.xxs),
            contentAlignment = Alignment.Center) {
            LauncherText(glyph, style = LauncherTheme.typography.controlLabel,
                color = LauncherTheme.colors.destinationSelectedContent, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}
