package dev.handheld.launcher.core.designsystem.foundation

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.glyphs.LauncherFaceButton
import dev.handheld.launcher.core.designsystem.glyphs.LauncherFaceGlyph
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyph
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyphIcon
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme

/** Isolated native fixture. Pressed state is a visual sample; focus is requested for real. */
@Composable
fun LauncherPrimitivesPreviewContent(reducedMotion: Boolean = false) {
    LauncherTheme(reducedMotion = reducedMotion) {
        val requester = remember { FocusRequester() }
        var focused by remember { mutableStateOf(false) }
        Column(
            Modifier.padding(LauncherTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.lg),
        ) {
            LauncherText("Visual primitives", style = LauncherTheme.typography.pageTitle)
            LauncherText(
                "A long title stays readable with native font scaling and wraps within its allocated width.",
                Modifier.width(420.dp),
            )
            LauncherText("—", unavailable = true, unavailableReason = "Value temporarily unavailable")
            Row(horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.lg)) {
                Column(verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm)) {
                    LauncherSurface(selected = true, modifier = Modifier.size(88.dp), contentDescription = "Home") {
                        LauncherGlyphIcon(LauncherGlyph.Home, Modifier.size(32.dp), contentDescription = null)
                    }
                    LauncherText("Selected", style = LauncherTheme.typography.controlLabel)
                }
                Column(verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm)) {
                    LauncherSurface(
                        focused = focused,
                        modifier = Modifier.size(88.dp).onFocusChanged { focused = it.isFocused }
                            .focusRequester(requester).focusable(),
                        contentDescription = "Search",
                    ) { LauncherGlyphIcon(LauncherGlyph.Search, Modifier.size(32.dp), contentDescription = null) }
                    LauncherText("Actual focus", style = LauncherTheme.typography.controlLabel)
                }
                Column(verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm)) {
                    LauncherSurface(pressed = true, modifier = Modifier.size(88.dp), contentDescription = "Apps") {
                        LauncherGlyphIcon(LauncherGlyph.Apps, Modifier.size(32.dp), contentDescription = null)
                    }
                    LauncherText("Pressed sample", style = LauncherTheme.typography.controlLabel)
                }
                Column(verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm)) {
                    LauncherSurface(
                        unavailable = true, enabled = false, modifier = Modifier.size(88.dp),
                        contentDescription = "Library", unavailableReason = "Source unavailable",
                    ) { LauncherGlyphIcon(LauncherGlyph.Library, Modifier.size(32.dp), contentDescription = null) }
                    LauncherText("Unavailable", style = LauncherTheme.typography.controlLabel)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.lg)) {
                LauncherGlyph.entries.forEach { glyph -> LauncherGlyphIcon(glyph, Modifier.size(32.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.lg)) {
                LauncherFaceButton.entries.forEach { button -> LauncherFaceGlyph(button) }
            }
            LauncherText(
                "Reduced motion: $reducedMotion · Physical Flip 2 calibration pending",
                style = LauncherTheme.typography.actionLabel,
                color = LauncherTheme.colors.textSecondary,
            )
        }
        LaunchedEffect(Unit) { requester.requestFocus() }
    }
}
