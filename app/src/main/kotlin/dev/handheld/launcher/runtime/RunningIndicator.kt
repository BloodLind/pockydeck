package dev.handheld.launcher.runtime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.ItemId

/** Only Home supplies labels; other routes stay empty even when reusing Home card styling. */
val LocalRunningLabels = staticCompositionLocalOf<Map<ItemId, String>> { emptyMap() }

@Composable
fun RunningIndicator(label: String, modifier: Modifier = Modifier) {
    Row(modifier.background(LauncherTheme.colors.surfaceControl.copy(alpha = .92f), RoundedCornerShape(6.dp))
        .semantics { contentDescription = "Live process detected: $label" }.padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(LauncherTheme.colors.confirm, CircleShape))
        LauncherText(label, modifier = Modifier.weight(1f, fill = false), style = LauncherTheme.typography.tileSubtitle,
            color = LauncherTheme.colors.confirm, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
