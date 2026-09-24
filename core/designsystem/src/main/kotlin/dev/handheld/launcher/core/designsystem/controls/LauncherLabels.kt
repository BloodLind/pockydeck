package dev.handheld.launcher.core.designsystem.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
    accentColor: Color? = null,
    compact: Boolean = false,
) {
    val colors = LauncherTheme.colors
    val accent = accentColor ?: colors.focus
    val shape = RoundedCornerShape(LauncherTheme.shapes.smallControl)
    val scale = LauncherTheme.referenceScale
    Row(
        modifier.clearAndSetSemantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        }.background(if (homeAccent) accent.copy(alpha = .14f) else colors.surfaceDock, shape)
            .then(if (homeAccent) Modifier.border(1.dp * scale, accent.copy(alpha = .45f), shape) else Modifier)
            .padding(horizontal = if (compact) LauncherTheme.spacing.xxs else
                LauncherTheme.spacing.xs + if (homeAccent) LauncherTheme.spacing.xxs / 2 else 0.dp,
                vertical = if (compact) LauncherTheme.spacing.xxs / 2 else LauncherTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (homeAccent) {
            Box(Modifier.size(6.dp * scale).background(accent, CircleShape))
            Spacer(Modifier.width(LauncherTheme.spacing.xxs * 1.5f))
        }
        LauncherText(label, style = if (homeAccent) LauncherTheme.typography.platformLabel else LauncherTheme.typography.badgeLabel,
            color = accentColor ?: if (homeAccent) accent else colors.textPrimary,
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
fun filterNavigationHintStyle() = LauncherTheme.typography.controlLabel.let {
    it.copy(fontSize = it.fontSize * .85f, lineHeight = it.lineHeight * .85f)
}

@Composable
fun filterNavigationHintGeometry(): FilterChipGeometry {
    val scale = LauncherTheme.referenceScale
    return FilterChipGeometry(6.dp * scale, 3.dp * scale, 10.dp * scale, 0.dp)
}

/** Quiet, non-interactive trigger hint; touch targets belong to the adjacent filters. */
@Composable
fun FilterNavigationHint(label: String, description: String, modifier: Modifier = Modifier) {
    val geometry = filterNavigationHintGeometry()
    Box(modifier.clearAndSetSemantics { contentDescription = description }
        .background(LauncherTheme.colors.surfaceControl, RoundedCornerShape(geometry.cornerRadius))
        .padding(horizontal = geometry.horizontalPadding, vertical = geometry.verticalPadding),
        contentAlignment = Alignment.Center) {
        LauncherText(label, style = filterNavigationHintStyle(), color = LauncherTheme.colors.textSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Raw physical legends. The shell supplies their current meaning and activation target. */
@Composable
fun ControllerGlyph(glyph: String, semanticLabel: String?, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (glyph.equals("SELECT", true)) {
        SelectButtonGlyph(semanticLabel, modifier, compact)
        return
    }
    if (glyph.equals("START", true)) {
        val type = LauncherTheme.typography.controlLabel
        Box(modifier.clearAndSetSemantics {
            if (semanticLabel != null) contentDescription = semanticLabel
        }.background(LauncherTheme.colors.destinationSelected, RoundedCornerShape(LauncherTheme.shapes.smallControl / 2))
            .padding(horizontal = LauncherTheme.spacing.xs * .75f, vertical = LauncherTheme.spacing.xxs * .5f),
            contentAlignment = Alignment.Center) {
            LauncherText(glyph.uppercase(java.util.Locale.ROOT),
                style = type.copy(fontSize = type.fontSize * .90f, lineHeight = type.lineHeight * .90f),
                color = LauncherTheme.colors.destinationSelectedContent, maxLines = 1)
        }
        return
    }
    val button = LauncherFaceButton.entries.firstOrNull { it.legend.equals(glyph, ignoreCase = true) }
    if (button != null) {
        val colors = LauncherTheme.colors
        val scale = LauncherTheme.referenceScale
        val background = when (button) {
            LauncherFaceButton.A -> colors.confirm
            LauncherFaceButton.B -> colors.cancel
            else -> colors.destinationSelected
        }
        Box(modifier.clearAndSetSemantics {
            if (semanticLabel != null) contentDescription = semanticLabel
        }.size((if (compact) 26.dp else 28.dp) * scale * LauncherTheme.smallControlScale)
            .background(background, CircleShape),
            contentAlignment = Alignment.Center) {
            val type = LauncherTheme.typography.controlLabel
            LauncherText(button.legend,
                style = if (compact) type.copy(fontSize = type.fontSize * .95f, lineHeight = type.lineHeight * .95f) else type,
                color = if (button == LauncherFaceButton.B) colors.textPrimary else colors.destinationSelectedContent,
                maxLines = 1)
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

/** Compact view/select symbol; its adjacent action label carries the current meaning. */
@Composable
private fun SelectButtonGlyph(semanticLabel: String?, modifier: Modifier, compact: Boolean) {
    val background = LauncherTheme.colors.destinationSelected
    val foreground = LauncherTheme.colors.destinationSelectedContent
    Canvas(modifier
        .size((if (compact) 26.dp else 28.dp) * LauncherTheme.referenceScale * LauncherTheme.smallControlScale)
        .clearAndSetSemantics {
            contentDescription = if (semanticLabel == null) "Select" else "Select: $semanticLabel"
        }
        .background(background, CircleShape)) {
        val unit = size.minDimension / 26f
        val corner = CornerRadius(1f * unit)
        val outline = Stroke(1.5f * unit)
        drawRoundRect(foreground, Offset(5.5f * unit, 6.5f * unit),
            Size(10f * unit, 9f * unit), corner, style = outline)
        // An opaque front panel separates the outlines at their overlap.
        drawRoundRect(background, Offset(10.5f * unit, 10.5f * unit),
            Size(10f * unit, 9f * unit), corner)
        drawRoundRect(foreground, Offset(10.5f * unit, 10.5f * unit),
            Size(10f * unit, 9f * unit), corner, style = outline)
    }
}
