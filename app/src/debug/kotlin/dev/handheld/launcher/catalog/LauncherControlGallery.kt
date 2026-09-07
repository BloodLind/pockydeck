package dev.handheld.launcher.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.R
import dev.handheld.launcher.core.designsystem.cards.AppIconTile
import dev.handheld.launcher.core.designsystem.cards.ArtworkFallback
import dev.handheld.launcher.core.designsystem.cards.CardVariant
import dev.handheld.launcher.core.designsystem.cards.CoverArtwork
import dev.handheld.launcher.core.designsystem.cards.CoverTile
import dev.handheld.launcher.core.designsystem.cards.SearchResultCard
import dev.handheld.launcher.core.designsystem.controls.ControllerGlyph
import dev.handheld.launcher.core.designsystem.controls.FilterChip
import dev.handheld.launcher.core.designsystem.controls.LauncherButton
import dev.handheld.launcher.core.designsystem.controls.LauncherIconButton
import dev.handheld.launcher.core.designsystem.controls.SearchField
import dev.handheld.launcher.core.designsystem.controls.SortSelector
import dev.handheld.launcher.core.designsystem.controls.StatusIndicator
import dev.handheld.launcher.core.designsystem.controls.StatusValue
import dev.handheld.launcher.core.designsystem.foundation.LauncherSurface
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.foundation.ShellMetrics
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyph
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyphIcon
import dev.handheld.launcher.core.designsystem.layout.CollectionGrid
import dev.handheld.launcher.core.designsystem.layout.EmptyState
import dev.handheld.launcher.core.designsystem.layout.FilterStrip
import dev.handheld.launcher.core.designsystem.layout.InlineNotice
import dev.handheld.launcher.core.designsystem.layout.PageHeading
import dev.handheld.launcher.core.designsystem.modal.ItemAction
import dev.handheld.launcher.core.designsystem.modal.ItemActionList
import dev.handheld.launcher.core.designsystem.modal.LauncherDialog
import dev.handheld.launcher.core.designsystem.settings.ActionRow
import dev.handheld.launcher.core.designsystem.settings.ChoiceRow
import dev.handheld.launcher.core.designsystem.settings.SettingRow
import dev.handheld.launcher.core.designsystem.settings.ToggleRow
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme

private val sections = listOf("controls", "cards", "layouts", "settings", "modal")

@Composable
fun LauncherControlGallery(metrics: ShellMetrics, initialSection: String = "controls") {
    var section by remember { mutableStateOf(initialSection.takeIf { it in sections } ?: "controls") }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(LauncherTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs)) {
                sections.forEach { id ->
                    FilterChip(id.replaceFirstChar { it.uppercase() }, section == id, { section = id })
                }
            }
            // Scroll ordinary specimens; a modal needs the bounded overlay host below.
            if (section != "modal") {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(LauncherTheme.spacing.lg)) {
                    when (section) {
                        "controls" -> ControlsGallery()
                        "cards" -> CardsGallery(metrics)
                        "layouts" -> LayoutGallery()
                        "settings" -> SettingsGallery()
                    }
                }
            }
        }
        if (section == "modal") ModalGallery()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FocusFixture(requester: FocusRequester) {
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(requester) {
        inputMode.requestInputMode(InputMode.Keyboard)
        requester.requestFocus()
    }
}

@Composable
private fun ControlsGallery() {
    var query by remember { mutableStateOf("") }
    var activations by remember { mutableStateOf(0) }
    var searches by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf(true) }
    var sort by remember { mutableStateOf("Recent") }
    val focus = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.md)) {
        PageHeading("Production controls", "Activations: $activations · IME searches: $searches")
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm)) {
            LauncherButton("Activate", { activations++ }, Modifier.width(110.dp))
            LauncherIconButton("Focused search action", { activations++ }, Modifier.focusRequester(focus)) {
                LauncherGlyphIcon(LauncherGlyph.Search, Modifier.size(24.dp), null)
            }
            FilterChip("Selected filter", selected, { selected = it })
            LauncherSurface(pressed = true, modifier = Modifier.width(130.dp)) {
                LauncherText("Pressed specimen", style = LauncherTheme.typography.controlLabel)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.md)) {
            LauncherButton("Disabled", { activations++ }, enabled = false)
            LauncherButton("Unavailable details", { activations++ }, unavailable = true,
                unavailableReason = "Artwork could not be loaded")
            SortSelector(sort, listOf("Recent", "Title"), { sort = if (sort == "Recent") "Title" else "Recent" })
        }
        SearchField(query, { query = it }, placeholder = "Type a query with the Android keyboard",
            onSearch = { searches++ })
        Row(horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.md)) {
            listOf("A", "B", "X", "Y", "START", "L1", "R1", "L2", "R2").forEach {
                ControllerGlyph(it, "Physical $it button")
            }
        }
        LauncherText("Body text and unavailable status remain readable.", style = LauncherTheme.typography.body)
        Row(horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.md)) {
            StatusIndicator(StatusValue.Available("Fixture value"), label = "Gallery only")
            StatusIndicator(StatusValue.Unavailable("No reading"), label = "Unavailable example")
            StatusIndicator(StatusValue.Unsupported, label = "Omitted unsupported example")
        }
    }
    FocusFixture(focus)
}

@Composable
private fun CardsGallery(metrics: ShellMetrics) {
    var phase by remember { mutableStateOf(0) }
    val phases = listOf("Loading artwork", "Artwork unavailable", "Loaded artwork")
    Column(verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.md)) {
        PageHeading("Cards and artwork", "Cycle states to inspect stable bounds")
        LauncherButton("${phases[phase]} · change state", { phase = (phase + 1) % phases.size })
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.lg)) {
            CoverTile("Stateful artwork", CardVariant.HomeCover, true, {}, {},
                Modifier.size(metrics.homeCardAllocatedSize.coerceAtLeast(140.dp)), artwork = {
                    if (phase == 2) CoverArtwork(painterResource(R.drawable.fixture_home_cover_1))
                    else ArtworkFallback(label = phases[phase])
                })
            CoverTile("A very long collection title that stays within its reserved caption", CardVariant.CollectionCover,
                true, {}, {}, Modifier.width(140.dp),
                artwork = { CoverArtwork(painterResource(R.drawable.fixture_home_cover_3)) },
                subtitle = "A long subtitle remains one line")
            AppIconTile("Round native icon", true, {}, {}, Modifier.size(160.dp), icon = {
                Box(Modifier.size(42.dp).background(LauncherTheme.colors.confirm, CircleShape))
            })
            AppIconTile("Missing icon", true, {}, {}, Modifier.size(160.dp), icon = {
                ArtworkFallback(label = "?")
            }, unavailable = true, unavailableReason = "Package icon unavailable")
        }
        SearchResultCard("A long horizontal search result with a useful bounded subtitle", "Installed Android app",
            true, {}, {}, Modifier.fillMaxWidth(), artwork = {
                LauncherGlyphIcon(LauncherGlyph.Apps, Modifier.size(36.dp), null)
            })
    }
}

@Composable
private fun LayoutGallery() {
    var filter by remember { mutableStateOf(0) }
    var recovered by remember { mutableStateOf(0) }
    val grid = rememberLazyGridState()
    Column(verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.md)) {
        PageHeading("Collection layout with a deliberately long heading for wrapping", "123,456 items · fixture count")
        FilterStrip(listOf("All", "Games", "Apps", "System", "Additional long category"), filter, { filter = it })
        InlineNotice("Refresh failed; saved content remains available.", "Retry", { recovered++ })
        CollectionGrid(listOf("A", "B", "C"), 3, grid, Modifier.height(90.dp), key = { it }) {
            LauncherButton("Saved item $it", {})
        }
        EmptyState("No matching results", "Try another filter or refresh the catalog. Retry count: $recovered",
            "Refresh", { recovered++ })
    }
}

@Composable
private fun SettingsGallery() {
    var checked by remember { mutableStateOf(true) }
    var choice by remember { mutableStateOf("A confirms, B goes back") }
    var actions by remember { mutableStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs)) {
        PageHeading("Settings", "Actions: $actions")
        SettingRow("Appearance", "Readable native typography", onActivate = { actions++ })
        ToggleRow("Reduced motion", checked, { checked = it })
        ChoiceRow("Controller mapping", choice,
            { choice = if (choice.startsWith("A")) "B confirms, A goes back" else "A confirms, B goes back" })
        ChoiceRow("A long translated setting label that retains a readable text region",
            "A very long option value that ends with an ellipsis instead of taking over the entire row", {})
        ActionRow("Recover source", "A useful enabled action", onActivate = { actions++ })
        ActionRow("Unavailable operation", enabled = false, onActivate = { actions++ })
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ModalGallery() {
    var visible by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("None") }
    val inputMode = LocalInputModeManager.current
    Box(Modifier.fillMaxSize()) {
        LauncherButton("Open dialog · chosen: $selected", { visible = true },
            Modifier.padding(top = 70.dp, start = LauncherTheme.spacing.lg))
        LauncherDialog("Item actions", { visible = false }, visible = visible) {
            ItemActionList(listOf(
                ItemAction("Open", onActivate = { selected = "Open"; visible = false }),
                ItemAction("Unavailable operation", false) {},
                ItemAction("View details", onActivate = { selected = "Details"; visible = false }),
            ))
        }
    }
    // Establish fixture input mode before composing the modal's initial focus request.
    LaunchedEffect(Unit) {
        inputMode.requestInputMode(InputMode.Keyboard)
        visible = true
    }
}
