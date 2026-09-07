package dev.handheld.launcher.core.designsystem.layout

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import dev.handheld.launcher.core.designsystem.controls.FilterChip
import dev.handheld.launcher.core.designsystem.controls.LauncherButton
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme

@Composable
fun PageHeading(title: String, count: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(bottom = LauncherTheme.spacing.md)) {
        LauncherText(title, style = LauncherTheme.typography.pageTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (count != null) {
            LauncherText(count, style = LauncherTheme.typography.tileSubtitle, color = LauncherTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun FilterStrip(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs),
    ) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                label = label,
                selected = index == selectedIndex,
                onSelectedChange = { onSelected(index) },
                enabled = enabled,
            )
        }
    }
}

@Composable
fun <T> CollectionGrid(
    items: List<T>,
    columns: Int,
    state: LazyGridState,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    content: @Composable (T) -> Unit,
) {
    require(columns > 0) { "columns must be positive" }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.lg),
    ) {
        if (key == null) {
            items(items) { item -> content(item) }
        } else {
            items(items, key = key) { item -> content(item) }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(LauncherTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.md),
    ) {
        LauncherText(title, style = LauncherTheme.typography.pageTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        LauncherText(message, color = LauncherTheme.colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        LauncherButton(label = actionLabel, onActivate = onAction)
    }
}

@Composable
fun InlineNotice(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    color: Color = LauncherTheme.colors.textSecondary,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = LauncherTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm),
    ) {
        LauncherText(message, modifier = Modifier.weight(1f), color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (actionLabel != null && onAction != null) LauncherButton(label = actionLabel, onActivate = onAction)
    }
}
