package dev.handheld.launcher.ui.presentation

import androidx.compose.ui.graphics.Color
import dev.handheld.launcher.core.domain.model.BackgroundTint

internal val BackgroundTint.label: String get() = when (this) {
    BackgroundTint.PURPLE -> "Purple"
    BackgroundTint.GRAPHITE -> "Graphite"
    BackgroundTint.BLUE -> "Blue"
    BackgroundTint.GREEN -> "Green"
    BackgroundTint.WARM -> "Warm"
}

/** Muted pigments preserve the matte finish and text contrast even at full strength. */
internal val BackgroundTint.color: Color get() = when (this) {
    BackgroundTint.PURPLE -> Color(0xFF4C425E)
    BackgroundTint.GRAPHITE -> Color(0xFF35373B)
    BackgroundTint.BLUE -> Color(0xFF394E68)
    BackgroundTint.GREEN -> Color(0xFF3C554D)
    BackgroundTint.WARM -> Color(0xFF5C4940)
}
