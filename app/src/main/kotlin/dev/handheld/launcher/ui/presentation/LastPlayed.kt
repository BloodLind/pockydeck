package dev.handheld.launcher.ui.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.controls.PlatformBadge
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.SuccessfulOpenRecord

/** Successful dispatch history survives restart; it makes no claim about process liveness. */
fun lastPlayedByConsole(items: List<LibraryItem>, history: List<SuccessfulOpenRecord>): Set<ItemId> {
    val consoles = items.filterIsInstance<LibraryItem.RomGame>()
        .mapNotNull { item -> item.platformId?.let { item.id to it } }.toMap()
    val seen = hashSetOf<String>()
    return history.sortedByDescending { it.openOrder }.mapNotNull { record ->
        consoles[record.itemId]?.takeIf(seen::add)?.let { record.itemId }
    }.toSet()
}

val LocalLastPlayed = staticCompositionLocalOf<Set<ItemId>> { emptySet() }
private val LastPlayedColor = Color(0xFF8FB5FF)

@Composable
fun LastPlayedIndicator(platform: String, modifier: Modifier = Modifier) {
    Box(modifier.size(16.dp).background(LastPlayedColor, CircleShape)
        .border(2.dp, Color(0xFF17191D), CircleShape)
        .semantics { contentDescription = "Last played on $platform" })
}

@Composable
fun LastPlayedTag(platform: String, modifier: Modifier = Modifier) {
    PlatformBadge("Last played on $platform", modifier,
        homeAccent = true, accentColor = LastPlayedColor)
}
