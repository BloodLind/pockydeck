package dev.handheld.launcher.core.designsystem.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme

/** Recognizable D-pad with only the two adjustment directions highlighted. */
@Composable
fun HorizontalDpadGlyph(modifier: Modifier = Modifier) {
    val bright = LauncherTheme.colors.textPrimary
    val dim = LauncherTheme.colors.textMuted
    Canvas(modifier.size(28.dp * LauncherTheme.referenceScale).semantics { contentDescription = "D-pad Left and Right" }) {
        val u = size.minDimension / 5f
        drawRect(dim, Offset(2 * u, 0f), Size(u, 5 * u))
        drawRect(bright, Offset(0f, 2 * u), Size(5 * u, u))
        fun arrow(x: Float, direction: Float) = Path().apply {
            moveTo(x + direction * u * .7f, 1.5f * u)
            lineTo(x, 2.5f * u)
            lineTo(x + direction * u * .7f, 3.5f * u)
            close()
        }
        drawPath(arrow(0f, 1f), bright)
        drawPath(arrow(5 * u, -1f), bright)
    }
}
