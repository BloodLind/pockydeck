package dev.handheld.launcher.shell

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.contract.ControllerActionFooter
import dev.handheld.launcher.contract.LauncherActionDescriptor
import dev.handheld.launcher.contract.SemanticActionPort
import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.contract.StatusPresentation
import dev.handheld.launcher.core.designsystem.controls.ControllerGlyph
import dev.handheld.launcher.core.designsystem.contract.LocalControllerInput
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.foundation.ShellBounds
import dev.handheld.launcher.core.designsystem.foundation.ShellMetrics
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyph
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyphIcon
import dev.handheld.launcher.core.designsystem.glyphs.LauncherStatusGlyph
import dev.handheld.launcher.core.designsystem.glyphs.LauncherStatusGlyphIcon
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.ConfirmBackMapping
import dev.handheld.launcher.core.domain.model.ControllerFaceButton
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.model.StatusValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** The stable, visual role of a status source. The caller never needs label parsing. */
enum class ShellStatusGlyph {
    Temperature,
    Memory,
    Storage,
    Wifi,
    Battery,
}

enum class ShellStatusTint { Neutral, Muted, Cold, Hot, Charging, Warning, Memory }

/** A status source and, when available, its stable visual role. */
@Immutable
data class ShellStatusReading(
    val presentation: StatusPresentation,
    val glyph: ShellStatusGlyph? = null,
    val symbol: LauncherStatusGlyph? = null,
    val tint: ShellStatusTint = ShellStatusTint.Neutral,
    val displayText: String? = null,
    val accessibilityDescription: String? = null,
)

/** Presentation inputs for the one persistent launcher status strip. */
@Immutable
data class LauncherShellStatus(
    /** A caller-supplied local clock. Null uses the device's local time. */
    val clock: String? = null,
    /** Left-aligned readings, such as storage or temperature, in caller-defined order. */
    val readings: List<ShellStatusReading> = emptyList(),
    /** Right-aligned readings, such as wireless or battery, in caller-defined order. */
    val rightReadings: List<ShellStatusReading> = emptyList(),
    /** The root maps only an explicitly available, enabled radio to true. */
    val bluetoothEnabled: Boolean = false,
    /** Presence only: the shell receives no notification text or app identity. */
    val notificationsPresent: Boolean = false,
)

/** State for shell chrome. Destination selection and actual controller focus remain separate. */
@Immutable
data class LauncherShellState(
    val selectedDestination: LauncherDestination = LauncherDestination.HOME,
    val focusedDestination: LauncherDestination? = null,
    val status: LauncherShellStatus = LauncherShellStatus(),
    val footer: ControllerActionFooter = ControllerActionFooter(emptyList()),
    val dockFocusEnabled: Boolean = true,
)

/** Stable tags for shell-level tests and debug inspection. */
object LauncherShellTags {
    const val Root = "launcher-shell-root"
    const val Status = "launcher-shell-status"
    const val Content = "launcher-shell-content"
    const val Dock = "launcher-shell-dock"
    const val Footer = "launcher-shell-footer"
    const val Overlay = "launcher-shell-overlay"

    fun destination(destination: LauncherDestination): String =
        "launcher-shell-destination-${destination.persistedKey}"

    fun footerAction(input: SemanticInputAction): String =
        "launcher-shell-footer-${input.name.lowercase(Locale.ROOT)}"
}

/**
 * The only launcher chrome root. Normal pages receive [ShellMetrics.contentBounds] directly;
 * overlay content is layered here so details and dialogs never create another shell.
 */
@Composable
fun LauncherShell(
    metrics: ShellMetrics,
    state: LauncherShellState,
    actionPort: SemanticActionPort,
    confirmBackMapping: ConfirmBackMapping = ConfirmBackMapping.Default,
    insets: LauncherShellInsets = LauncherShellInsets(),
    modifier: Modifier = Modifier,
    onDestinationSelected: (LauncherDestination) -> Unit,
    onDestinationFocused: (LauncherDestination?) -> Unit = {},
    content: @Composable (Modifier) -> Unit,
    overlay: @Composable () -> Unit = {},
) {
    val layout = LauncherShellLayoutPolicy.calculate(metrics, insets)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LauncherTheme.colors.backgroundGradient())
            .testTag(LauncherShellTags.Root),
    ) {
        if (layout.statusBounds.height > 0.dp) StatusStrip(
            status = state.status,
            bounds = layout.statusBounds,
            gutter = metrics.gutter,
            modifier = Modifier.testTag(LauncherShellTags.Status),
        )
        content(
            Modifier
                .offset(layout.contentBounds.left, layout.contentBounds.top)
                .size(layout.contentBounds.width, layout.contentBounds.height)
                .testTag(LauncherShellTags.Content),
        )
        if (layout.dockBounds.height >= 48.dp) NavigationDock(
            layout = layout,
            selectedDestination = state.selectedDestination,
            focusedDestination = state.focusedDestination,
            focusEnabled = state.dockFocusEnabled,
            onDestinationSelected = onDestinationSelected,
            onDestinationFocused = onDestinationFocused,
            modifier = Modifier.testTag(LauncherShellTags.Dock),
        )
        if (layout.footerBounds.height >= 48.dp) ShellFooter(
            layout = layout,
            gutter = metrics.gutter,
            footer = state.footer,
            actionPort = actionPort,
            confirmBackMapping = confirmBackMapping,
            modifier = Modifier.testTag(LauncherShellTags.Footer),
        )
        Box(Modifier.fillMaxSize().testTag(LauncherShellTags.Overlay)) { overlay() }
    }
}

@Composable
private fun StatusStrip(
    status: LauncherShellStatus,
    bounds: ShellBounds,
    gutter: Dp,
    modifier: Modifier = Modifier,
) {
    val clock = status.clock ?: rememberLocalClock()
    val scale = LauncherTheme.referenceScale
    Row(
        modifier = modifier
            .offset(bounds.left, bounds.top + 16.dp * scale)
            .width(bounds.width)
            .heightIn(min = 20.dp * scale * LauncherTheme.smallControlScale * LocalDensity.current.fontScale)
            .padding(horizontal = gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LauncherText(
            text = clock,
            style = LauncherTheme.typography.clock,
            color = LauncherTheme.colors.textPrimary,
            maxLines = 1,
        )
        if (status.notificationsPresent) {
            Spacer(Modifier.width(LauncherTheme.spacing.xs))
            Box(Modifier.size((5.dp * scale).coerceAtLeast(3.dp))
                .background(LauncherTheme.colors.textPrimary, CircleShape)
                .semantics { contentDescription = "Notifications available" })
        }
        if (status.readings.isNotEmpty()) Spacer(Modifier.width(LauncherTheme.spacing.md))
        StatusReadings(status.readings, Modifier.weight(1f), constrained = true)
        Spacer(Modifier.width(LauncherTheme.spacing.sm))
        if (status.bluetoothEnabled) {
            LauncherStatusGlyphIcon(LauncherStatusGlyph.Bluetooth,
                Modifier.size(16.dp * scale * LauncherTheme.smallControlScale),
                tint = LauncherTheme.colors.textPrimary, contentDescription = "Bluetooth enabled")
            if (status.rightReadings.any { it.presentation.shouldRender }) Spacer(Modifier.width(LauncherTheme.spacing.sm))
        }
        StatusReadings(
            readings = status.rightReadings,
        )
    }
}

@Composable
private fun StatusReadings(
    readings: List<ShellStatusReading>,
    modifier: Modifier = Modifier,
    constrained: Boolean = false,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        readings.filter { it.presentation.shouldRender }.let { visible ->
            visible.forEachIndexed { index, reading ->
                if (index > 0) Spacer(Modifier.width(LauncherTheme.spacing.sm))
                StatusReading(reading, if (constrained) Modifier.weight(
                    when (reading.glyph) { ShellStatusGlyph.Temperature -> 1f; ShellStatusGlyph.Memory -> 1.7f; else -> 2f },
                    fill = false,
                ) else Modifier, constrained = constrained)
            }
        }
    }
}

@Composable
private fun StatusReading(reading: ShellStatusReading, modifier: Modifier = Modifier, constrained: Boolean = false) {
    val description = reading.accessibilityDescription ?: "${reading.presentation.contentDescription}: ${
        (reading.presentation.value as? StatusValue.Available<String>)?.value ?: "Unavailable"}"
    Row(modifier.clearAndSetSemantics {
        contentDescription = description
        if (reading.presentation.value == StatusValue.Unavailable) stateDescription = "Unavailable"
    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xxs)) {
        reading.glyph?.let { glyph -> ShellStatusGlyphIcon(glyph, reading.symbol,
            reading.tint) }
        if (reading.glyph != ShellStatusGlyph.Wifi)
            LauncherText(reading.displayText ?: reading.presentation.displayedValue.orEmpty(),
                modifier = if (constrained) Modifier.weight(1f, fill = false) else Modifier,
                style = LauncherTheme.typography.statusValue,
                color = if (reading.presentation.value == StatusValue.Unavailable) LauncherTheme.colors.textSecondary else LauncherTheme.colors.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Symbols from the HTML design preview; the enclosing reading owns complete semantic text. */
@Composable
private fun ShellStatusGlyphIcon(glyph: ShellStatusGlyph, symbolOverride: LauncherStatusGlyph?, tint: ShellStatusTint) {
    val color = when (tint) {
        ShellStatusTint.Neutral -> LauncherTheme.colors.textPrimary
        ShellStatusTint.Muted -> LauncherTheme.colors.textMuted
        ShellStatusTint.Cold -> Color(0xFF67C7F5)
        ShellStatusTint.Hot -> LauncherTheme.colors.cancel
        ShellStatusTint.Charging -> LauncherTheme.colors.confirm
        ShellStatusTint.Warning -> Color(0xFFFACC15)
        ShellStatusTint.Memory -> Color(0xFFA5A5F3)
    }
    val symbol = symbolOverride ?: when (glyph) {
        ShellStatusGlyph.Temperature -> LauncherStatusGlyph.Temperature
        ShellStatusGlyph.Memory -> LauncherStatusGlyph.Memory
        ShellStatusGlyph.Storage -> LauncherStatusGlyph.Storage
        ShellStatusGlyph.Wifi -> LauncherStatusGlyph.Wifi
        ShellStatusGlyph.Battery -> LauncherStatusGlyph.Battery
    }
    val glyphSize = 16.dp * LauncherTheme.referenceScale * LauncherTheme.smallControlScale
    LauncherStatusGlyphIcon(symbol, Modifier.size(glyphSize), tint = color, contentDescription = null)
}

@Composable
private fun rememberLocalClock(): String {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun currentClock() = android.text.format.DateFormat.getTimeFormat(context).format(Date())
    var clock by remember { mutableStateOf(currentClock()) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                clock = currentClock()
                val now = System.currentTimeMillis()
                delay(60_000L - now % 60_000L)
            }
        }
    }
    return clock
}

@Composable
private fun NavigationDock(
    layout: LauncherShellLayout,
    selectedDestination: LauncherDestination,
    focusedDestination: LauncherDestination?,
    focusEnabled: Boolean,
    onDestinationSelected: (LauncherDestination) -> Unit,
    onDestinationFocused: (LauncherDestination?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var locallyFocusedDestination by remember { mutableStateOf<LauncherDestination?>(null) }
    val renderedFocus = (locallyFocusedDestination ?: focusedDestination).takeIf { focusEnabled }
    val slotSize = (52.8.dp * LauncherTheme.referenceScale * LauncherTheme.smallControlScale).coerceAtLeast(48.dp)
    Box(
        modifier = modifier
            .offset(layout.dockBounds.left, layout.dockBounds.top)
            .size(layout.dockBounds.width, layout.dockBounds.height),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier.offset(y = layout.dockCenterY - layout.dockBounds.top - slotSize / 2f),
            horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControllerGlyph("L1", "Previous destination")
            Row(
                Modifier.background(LauncherTheme.colors.surfaceDock, CircleShape)
                    .border(1.dp * LauncherTheme.referenceScale, LauncherTheme.colors.borderEmphasis, CircleShape),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              LauncherDestination.dockOrder.forEach { destination ->
                if (destination == LauncherDestination.SEARCH) {
                    Spacer(Modifier.width(LauncherTheme.spacing.xxs))
                    Spacer(
                        Modifier
                            .height(28.dp * LauncherTheme.referenceScale * LauncherTheme.smallControlScale)
                            .width(1.dp * LauncherTheme.referenceScale)
                            .background(LauncherTheme.colors.borderEmphasis),
                    )
                    Spacer(Modifier.width(LauncherTheme.spacing.xxs))
                }
                DockDestination(
                    destination = destination,
                    selected = destination == selectedDestination,
                    focused = destination == renderedFocus,
                    onSelected = { onDestinationSelected(destination) },
                    onFocused = { hasFocus ->
                        locallyFocusedDestination = when {
                            hasFocus -> destination
                            locallyFocusedDestination == destination -> null
                            else -> locallyFocusedDestination
                        }
                        onDestinationFocused(locallyFocusedDestination)
                    },
                )
              }
            }
            ControllerGlyph("R1", "Next destination")
        }
    }
}

@Composable
private fun DockDestination(
    destination: LauncherDestination,
    selected: Boolean,
    focused: Boolean,
    onSelected: () -> Unit,
    onFocused: (Boolean) -> Unit,
) {
    val visualSize = 52.8.dp * LauncherTheme.referenceScale * LauncherTheme.smallControlScale
    Box(
        modifier = Modifier
            .size(visualSize.coerceAtLeast(48.dp))
            .testTag(LauncherShellTags.destination(destination))
            .onFocusChanged { onFocused(it.isFocused) }
            .clickable(role = Role.Tab, onClick = onSelected)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(visualSize)
                .background(
                    color = if (selected) LauncherTheme.colors.destinationSelected else LauncherTheme.colors.dockInactive,
                    shape = CircleShape,
                )
                .then(
                    if (focused && LocalControllerInput.current) Modifier.border(2.dp * LauncherTheme.referenceScale, LauncherTheme.colors.focus, CircleShape)
                    else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            LauncherGlyphIcon(
                glyph = destination.glyph(),
                modifier = Modifier.size(28.6.dp * LauncherTheme.referenceScale * LauncherTheme.smallControlScale),
                contentDescription = destination.label(),
                tint = if (selected) LauncherTheme.colors.destinationSelectedContent else LauncherTheme.colors.textPrimary,
            )
        }
    }
}

@Composable
private fun ShellFooter(
    layout: LauncherShellLayout,
    gutter: Dp,
    footer: ControllerActionFooter,
    actionPort: SemanticActionPort,
    confirmBackMapping: ConfirmBackMapping,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LauncherTheme.motion.reducedMotion
    val reveal = remember { Animatable(1f) }
    val visibleActions = footer.actions.map { Triple(it.input, it.meaning, it.label) }
    LaunchedEffect(visibleActions, confirmBackMapping, reducedMotion) {
        if (reducedMotion) reveal.snapTo(1f)
        else {
            reveal.snapTo(.72f)
            reveal.animateTo(1f, tween(120))
        }
    }
    Box(
        modifier = modifier
            .offset(layout.footerBounds.left, layout.footerBounds.top)
            .size(layout.footerBounds.width, layout.footerBounds.height),
    ) {
        Box(
            Modifier
                .offset(y = layout.footerDividerY - layout.footerBounds.top)
                .fillMaxWidth()
                .height(1.dp)
                .background(LauncherTheme.colors.borderSubtle),
        )
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .then(if (reducedMotion) Modifier else Modifier.animateContentSize(
                    animationSpec = tween(120), alignment = Alignment.CenterEnd))
                .padding(horizontal = gutter)
                .graphicsLayer { alpha = reveal.value },
            horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            footer.actions.forEach { descriptor ->
                key(descriptor.input) {
                    FooterAction(descriptor, actionPort, confirmBackMapping,
                        visualOffset = (layout.footerDividerY - layout.footerBounds.top) / 2f - 8.dp * LauncherTheme.referenceScale)
                }
            }
        }
    }
}

@Composable
private fun FooterAction(
    descriptor: LauncherActionDescriptor,
    actionPort: SemanticActionPort,
    confirmBackMapping: ConfirmBackMapping,
    visualOffset: Dp,
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .height(48.dp)
            .testTag(LauncherShellTags.footerAction(descriptor.input))
            .semantics { if (!descriptor.enabled) disabled() }
            .focusProperties { canFocus = false }
            .clickable(enabled = descriptor.enabled) { actionPort.dispatch(descriptor) },
        contentAlignment = Alignment.Center,
    ) {
      Row(
        modifier = Modifier.offset(y = visualOffset),
        horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControllerGlyph(
            glyph = descriptor.input.footerLegend(confirmBackMapping),
            semanticLabel = descriptor.label,
        )
        LauncherText(
            text = descriptor.label,
            style = LauncherTheme.typography.actionLabel,
            color = if (descriptor.enabled) LauncherTheme.colors.textPrimary else LauncherTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
      }
    }
}

private fun LauncherDestination.glyph(): LauncherGlyph = when (this) {
    LauncherDestination.HOME -> LauncherGlyph.Home
    LauncherDestination.LIBRARY -> LauncherGlyph.Library
    LauncherDestination.APPS -> LauncherGlyph.Apps
    LauncherDestination.FAVORITES -> LauncherGlyph.Favorites
    LauncherDestination.SETTINGS -> LauncherGlyph.Settings
    LauncherDestination.SEARCH -> LauncherGlyph.Search
}

private fun LauncherDestination.label(): String = when (this) {
    LauncherDestination.HOME -> "Home"
    LauncherDestination.LIBRARY -> "Library"
    LauncherDestination.APPS -> "Apps"
    LauncherDestination.FAVORITES -> "Favorites"
    LauncherDestination.SETTINGS -> "Settings"
    LauncherDestination.SEARCH -> "Search"
}

private fun SemanticInputAction.footerLegend(mapping: ConfirmBackMapping): String = when (this) {
    SemanticInputAction.CONFIRM -> mapping.confirm.legend()
    SemanticInputAction.BACK -> mapping.back.legend()
    SemanticInputAction.SECONDARY -> "X"
    SemanticInputAction.TERTIARY -> "Y"
    SemanticInputAction.MENU -> "START"
    SemanticInputAction.PREVIOUS_DESTINATION -> "L"
    SemanticInputAction.NEXT_DESTINATION -> "R"
    SemanticInputAction.PREVIOUS_FILTER -> "LT"
    SemanticInputAction.NEXT_FILTER -> "RT"
    SemanticInputAction.NAVIGATE_UP,
    SemanticInputAction.NAVIGATE_DOWN,
    SemanticInputAction.NAVIGATE_LEFT,
    SemanticInputAction.NAVIGATE_RIGHT -> "D-PAD"
}

private fun ControllerFaceButton.legend(): String = when (this) {
    ControllerFaceButton.A -> "A"
    ControllerFaceButton.B -> "B"
}
