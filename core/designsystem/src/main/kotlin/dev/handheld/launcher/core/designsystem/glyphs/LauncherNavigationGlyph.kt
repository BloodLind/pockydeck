package dev.handheld.launcher.core.designsystem.glyphs

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import dev.handheld.launcher.core.designsystem.R
import dev.handheld.launcher.core.designsystem.foundation.LauncherIcon
import dev.handheld.launcher.core.designsystem.foundation.launcherContentColor

/** Original rounded console-style symbols for the destination dock. */
enum class LauncherNavigationGlyph(val label: String, internal val resourceId: Int) {
    Home("Home", R.drawable.ic_navigation_home),
    Library("Library", R.drawable.ic_navigation_cartridge),
    Apps("Apps", R.drawable.ic_navigation_apps),
    Favorites("Favorites", R.drawable.ic_navigation_star),
    Settings("Settings", R.drawable.ic_navigation_settings),
    Search("Search", R.drawable.ic_navigation_search),
}

@Composable
fun LauncherNavigationGlyphIcon(
    glyph: LauncherNavigationGlyph,
    modifier: Modifier = Modifier,
    contentDescription: String? = glyph.label,
    tint: Color = launcherContentColor(),
) = LauncherIcon(ImageVector.vectorResource(glyph.resourceId), contentDescription, modifier, tint)
