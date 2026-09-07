package dev.handheld.launcher.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.R
import dev.handheld.launcher.core.designsystem.cards.AppIconTile
import dev.handheld.launcher.core.designsystem.cards.ArtworkFallback
import dev.handheld.launcher.core.designsystem.cards.CardVariant
import dev.handheld.launcher.core.designsystem.cards.CoverArtwork
import dev.handheld.launcher.core.designsystem.cards.CoverTile
import dev.handheld.launcher.core.designsystem.controls.LauncherButton
import dev.handheld.launcher.core.designsystem.controls.PlatformBadge
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.foundation.ShellMetrics
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyph
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyphIcon
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme

private data class HomeFixtureItem(val id: String, val title: String, val platform: String, val art: Int?)

private val homeFixtures = listOf(
    HomeFixtureItem("zelda", "The Legend of Zelda: The Wind Waker", "GAMECUBE", R.drawable.fixture_home_cover_1),
    HomeFixtureItem("dead-cells", "Dead Cells", "ANDROID", R.drawable.fixture_home_cover_2),
    HomeFixtureItem("persona", "Persona 3 Portable", "PSP", R.drawable.fixture_home_cover_3),
    HomeFixtureItem("colossus", "Shadow of the Colossus", "PS2", R.drawable.fixture_home_cover_4),
    HomeFixtureItem("dolphin", "Dolphin", "ANDROID APP", null),
)

/** Debug content only. Root owns backdrop/status/dock/footer and the content rectangle. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeGalleryFixture(metrics: ShellMetrics, modifier: Modifier = Modifier, variant: String = "normal") {
    var items by remember(variant) { mutableStateOf(
        if (variant == "reordered") listOf(homeFixtures[1], homeFixtures[0]) + homeFixtures.drop(2)
        else homeFixtures,
    ) }
    var selectedId by remember(variant) { mutableStateOf(items.first().id) }
    var requestedId by remember(variant) { mutableStateOf<String?>(selectedId) }
    val selected = items.first { it.id == selectedId }
    val requesters = remember { homeFixtures.associate { it.id to FocusRequester() } }
    val row = rememberLazyListState()
    val inputMode = LocalInputModeManager.current
    val density = LocalDensity.current
    val shadowOffset = with(density) { (2.dp * metrics.referenceScale).toPx() }
    val shadowBlur = with(density) { (3.dp * metrics.referenceScale).toPx() }
    Box(modifier) {
        Column(
            Modifier.offset(y = metrics.homeMetadataTop - metrics.contentBounds.top)
                .fillMaxWidth().height(metrics.metadataReservation),
            verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs),
        ) {
            PlatformBadge(selected.platform, homeAccent = true)
            LauncherText(
                if (variant == "long") "The exceptionally long localized title of a selected adventure across several worlds" else selected.title,
                style = LauncherTheme.typography.homeTitle.copy(
                    shadow = Shadow(Color.Black.copy(alpha = .4f), Offset(0f, shadowOffset), shadowBlur),
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (metrics.hasUsableHomeCard) {
            // Transparent lift reservation sits outside the gutter, aligning visible frames
            // with metadata. The trailing partial card intentionally reaches the screen edge.
            Box(Modifier.offset(x = -metrics.focusLiftReservation,
                y = metrics.homeCardAllocatedBounds.top - metrics.contentBounds.top)
                .wrapContentSize(Alignment.TopStart, unbounded = true)) {
                LazyRow(
                    Modifier.width(metrics.windowWidth - metrics.gutter + metrics.focusLiftReservation)
                        .height(metrics.homeCardAllocatedSize),
                    state = row,
                    horizontalArrangement = Arrangement.spacedBy(metrics.homeCardGap),
                ) {
                    items(items, key = { it.id }) { item ->
                        val tileModifier = Modifier.size(metrics.homeCardAllocatedSize)
                            .focusRequester(requesters.getValue(item.id))
                        val activate = {
                            selectedId = item.id
                            items = listOf(item) + items.filterNot { it.id == item.id }
                            requestedId = item.id
                        }
                        val focus: (Boolean) -> Unit = { if (it) selectedId = item.id }
                        if (item.art == null) {
                            AppIconTile(item.title, true, activate, focus, tileModifier, icon = {
                                LauncherGlyphIcon(LauncherGlyph.Apps, Modifier.size(30.dp), null,
                                    tint = LauncherTheme.colors.focus)
                            }, focusFrameWidth = metrics.focusFrameReservation,
                                focusLift = metrics.focusLiftReservation)
                        } else {
                            CoverTile(item.title, CardVariant.HomeCover, true, activate, focus,
                                tileModifier, artwork = {
                                    if (variant == "failed" && item.id == selectedId) ArtworkFallback()
                                    else CoverArtwork(painterResource(item.art))
                                }, badge = if (item.id == "zelda") null else ({ PlatformBadge(item.platform) }),
                                focusFrameWidth = metrics.focusFrameReservation,
                                focusLift = metrics.focusLiftReservation)
                        }
                    }
                }
            }
        } else {
            LauncherButton("Open Library", {}, Modifier.offset(y = metrics.metadataReservation))
        }
    }
    LaunchedEffect(requestedId, metrics.hasUsableHomeCard) {
        val target = requestedId ?: return@LaunchedEffect
        if (metrics.hasUsableHomeCard) {
            inputMode.requestInputMode(InputMode.Keyboard)
            row.scrollToItem(items.indexOfFirst { it.id == target }.coerceAtLeast(0))
            withFrameNanos { }
            requesters.getValue(target).requestFocus()
            requestedId = null
        }
    }
}
