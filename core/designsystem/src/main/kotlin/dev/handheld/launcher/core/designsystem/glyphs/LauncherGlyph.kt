package dev.handheld.launcher.core.designsystem.glyphs

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.foundation.LauncherIcon
import dev.handheld.launcher.core.designsystem.foundation.launcherContentColor
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme

enum class LauncherGlyph(val label: String) {
    Home("Home"), Library("Library"), Apps("Apps"), Favorites("Favorites"),
    Settings("Settings"), Search("Search"),
}

/** Physical legends only: the controller mapping supplies Confirm/Back meaning at the call site. */
enum class LauncherFaceButton(val legend: String) { A("A"), B("B"), X("X"), Y("Y"), Start("START") }

@Composable
fun LauncherGlyphIcon(
    glyph: LauncherGlyph,
    modifier: Modifier = Modifier,
    contentDescription: String? = glyph.label,
    tint: Color = launcherContentColor(),
) = LauncherIcon(glyph.vector, contentDescription, modifier, tint)

@Composable
fun LauncherFaceGlyph(
    button: LauncherFaceButton,
    modifier: Modifier = Modifier,
    semanticLabel: String? = button.legend,
    color: Color = launcherContentColor(),
) {
    BasicText(
        text = button.legend,
        modifier = modifier.clearAndSetSemantics {
            if (semanticLabel != null) contentDescription = semanticLabel
        },
        style = LauncherTheme.typography.controlLabel.copy(color = color, textAlign = TextAlign.Center),
    )
}

/** Original native vector paths; no external assets or platform symbol-font fallback. */
private fun glyphVector(name: String, draw: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            pathBuilder = draw,
        )
    }.build()

private val vectors = mapOf(
    LauncherGlyph.Home to glyphVector("Home") {
        moveTo(3f, 10.5f); lineTo(12f, 3f); lineTo(21f, 10.5f)
        moveTo(5f, 9f); lineTo(5f, 21f); lineTo(10f, 21f); lineTo(10f, 14f)
        lineTo(14f, 14f); lineTo(14f, 21f); lineTo(19f, 21f); lineTo(19f, 9f)
    },
    LauncherGlyph.Library to glyphVector("Library") {
        moveTo(3f, 3f); lineTo(9f, 3f); lineTo(9f, 9f); lineTo(3f, 9f); close()
        moveTo(15f, 3f); lineTo(21f, 3f); lineTo(21f, 9f); lineTo(15f, 9f); close()
        moveTo(3f, 15f); lineTo(9f, 15f); lineTo(9f, 21f); lineTo(3f, 21f); close()
        moveTo(15f, 15f); lineTo(21f, 15f); lineTo(21f, 21f); lineTo(15f, 21f); close()
    },
    LauncherGlyph.Apps to ImageVector.Builder("Apps", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black), stroke = null) {
            for (y in listOf(5f, 12f, 19f)) for (x in listOf(5f, 12f, 19f)) {
                moveTo(x + 1.5f, y)
                curveTo(x + 1.5f, y + .83f, x + .83f, y + 1.5f, x, y + 1.5f)
                curveTo(x - .83f, y + 1.5f, x - 1.5f, y + .83f, x - 1.5f, y)
                curveTo(x - 1.5f, y - .83f, x - .83f, y - 1.5f, x, y - 1.5f)
                curveTo(x + .83f, y - 1.5f, x + 1.5f, y - .83f, x + 1.5f, y)
                close()
            }
        }
    }.build(),
    LauncherGlyph.Favorites to glyphVector("Favorites") {
        moveTo(12f, 3f); lineTo(14.8f, 8.8f); lineTo(21f, 9.7f)
        lineTo(16.5f, 14.1f); lineTo(17.6f, 20.4f); lineTo(12f, 17.4f)
        lineTo(6.4f, 20.4f); lineTo(7.5f, 14.1f); lineTo(3f, 9.7f)
        lineTo(9.2f, 8.8f); close()
    },
    LauncherGlyph.Settings to glyphVector("Settings") {
        moveTo(3f, 6f); lineTo(7f, 6f); moveTo(11f, 6f); lineTo(21f, 6f)
        moveTo(7f, 4f); lineTo(11f, 4f); lineTo(11f, 8f); lineTo(7f, 8f); close()
        moveTo(3f, 12f); lineTo(14f, 12f); moveTo(18f, 12f); lineTo(21f, 12f)
        moveTo(14f, 10f); lineTo(18f, 10f); lineTo(18f, 14f); lineTo(14f, 14f); close()
        moveTo(3f, 18f); lineTo(6f, 18f); moveTo(10f, 18f); lineTo(21f, 18f)
        moveTo(6f, 16f); lineTo(10f, 16f); lineTo(10f, 20f); lineTo(6f, 20f); close()
    },
    LauncherGlyph.Search to glyphVector("Search") {
        moveTo(18f, 10.5f)
        curveTo(18f, 14.64f, 14.64f, 18f, 10.5f, 18f)
        curveTo(6.36f, 18f, 3f, 14.64f, 3f, 10.5f)
        curveTo(3f, 6.36f, 6.36f, 3f, 10.5f, 3f)
        curveTo(14.64f, 3f, 18f, 6.36f, 18f, 10.5f); close()
        moveTo(16f, 16f); lineTo(21f, 21f)
    },
)

private val LauncherGlyph.vector: ImageVector get() = vectors.getValue(this)
