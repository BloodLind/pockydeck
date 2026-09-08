package dev.handheld.launcher.core.designsystem.glyphs

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import dev.handheld.launcher.core.designsystem.R
import dev.handheld.launcher.core.designsystem.foundation.LauncherIcon
import dev.handheld.launcher.core.designsystem.foundation.launcherContentColor
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme

/** The HTML preview's Material Symbols Outlined, FILL 0, weight 200. */
enum class LauncherGlyph(val label: String, internal val resourceId: Int) {
    Home("Home", R.drawable.ic_material_cottage),
    Library("Library", R.drawable.ic_material_grid_view),
    Apps("Apps", R.drawable.ic_material_apps),
    Favorites("Favorites", R.drawable.ic_material_grade),
    Settings("Settings", R.drawable.ic_material_tune),
    Search("Search", R.drawable.ic_material_search),
    ExpandMore("More", R.drawable.ic_material_expand_more),
}

/** Status symbols retain the HTML preview's fill variants. Labels belong to the status value. */
enum class LauncherStatusGlyph(internal val resourceId: Int) {
    Temperature(R.drawable.ic_material_device_thermostat),
    Memory(R.drawable.ic_material_memory),
    Storage(R.drawable.ic_material_sd_card),
    Wifi(R.drawable.ic_material_wifi),
    Battery(R.drawable.ic_material_battery_5_bar),
}

/** Physical legends only: the controller mapping supplies Confirm/Back meaning at the call site. */
enum class LauncherFaceButton(val legend: String) { A("A"), B("B"), X("X"), Y("Y"), Start("START") }

@Composable
fun LauncherGlyphIcon(
    glyph: LauncherGlyph,
    modifier: Modifier = Modifier,
    contentDescription: String? = glyph.label,
    tint: Color = launcherContentColor(),
) = LauncherIcon(ImageVector.vectorResource(glyph.resourceId), contentDescription, modifier, tint)

@Composable
fun LauncherStatusGlyphIcon(
    glyph: LauncherStatusGlyph,
    modifier: Modifier = Modifier,
    tint: Color = launcherContentColor(),
) = LauncherIcon(ImageVector.vectorResource(glyph.resourceId), null, modifier, tint)

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
