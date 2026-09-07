package dev.handheld.launcher.debug

import android.animation.ValueAnimator
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.handheld.launcher.catalog.HomeGalleryFixture
import dev.handheld.launcher.contract.ControllerActionFooter
import dev.handheld.launcher.contract.LauncherActionDescriptor
import dev.handheld.launcher.contract.LauncherActionMeaning
import dev.handheld.launcher.contract.SemanticActionPort
import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.contract.StatusPresentation
import dev.handheld.launcher.core.designsystem.controls.LauncherButton
import dev.handheld.launcher.core.designsystem.controls.SearchField
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.foundation.ShellMetrics
import dev.handheld.launcher.core.designsystem.foundation.ShellMetricsInput
import dev.handheld.launcher.core.designsystem.layout.PageHeading
import dev.handheld.launcher.core.designsystem.modal.LauncherDialog
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.model.StatusValue
import dev.handheld.launcher.shell.LauncherShell
import dev.handheld.launcher.shell.LauncherShellState
import dev.handheld.launcher.shell.LauncherShellStatus
import dev.handheld.launcher.shell.ShellStatusGlyph
import dev.handheld.launcher.shell.ShellStatusReading
import kotlinx.coroutines.delay
import java.util.Date

/** Native shared-shell calibration. Every destination below is explicitly a debug fixture. */
class ShellPreviewActivity : ComponentActivity() {
    private var reducedMotion by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        val initialDestination = LauncherDestination.fromPersistedKey(
            intent.getStringExtra("destination") ?: "home",
        ) ?: LauncherDestination.HOME
        val compact = intent.getBooleanExtra("compact", false)
        val variant = intent.getStringExtra("variant") ?: "normal"
        setContent {
            BoxWithConstraints(
                Modifier.imePadding()
                    .then(if (compact) Modifier.widthIn(max = 600.dp).heightIn(max = 340.dp) else Modifier)
                    .fillMaxSize(),
            ) {
                val density = LocalDensity.current
                val metrics = remember(maxWidth, maxHeight, density.density, density.fontScale) {
                    with(density) {
                        ShellMetrics.calculate(ShellMetricsInput(
                            maxWidth.roundToPx(), maxHeight.roundToPx(), this.density, fontScale,
                        ))
                    }
                }
                LauncherTheme(reducedMotion, metrics.referenceScale) {
                    PreviewShell(metrics, initialDestination, variant)
                }
            }
        }
    }

    @Composable
    private fun PreviewShell(metrics: ShellMetrics, initial: LauncherDestination, variant: String) {
        var destination by remember { mutableStateOf(initial) }
        var focusedDestination by remember { mutableStateOf<LauncherDestination?>(null) }
        var dialog by remember { mutableStateOf<String?>(null) }
        var query by remember { mutableStateOf("") }
        var clock by remember { mutableStateOf(DateFormat.getTimeFormat(this).format(Date())) }
        LaunchedEffect(Unit) {
            while (true) {
                clock = DateFormat.getTimeFormat(this@ShellPreviewActivity).format(Date())
                delay(1_000)
            }
        }
        val footer = ControllerActionFooter(listOf(
            LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.ACTIVATE, "Preview"),
            LauncherActionDescriptor(SemanticInputAction.BACK, LauncherActionMeaning.GO_BACK, "Back",
                enabled = destination != LauncherDestination.HOME || dialog != null),
            LauncherActionDescriptor(SemanticInputAction.SECONDARY, LauncherActionMeaning.OPEN_SEARCH, "Search"),
            LauncherActionDescriptor(SemanticInputAction.TERTIARY, LauncherActionMeaning.OPEN_DETAILS, "Details"),
            LauncherActionDescriptor(SemanticInputAction.MENU, LauncherActionMeaning.OPEN_MENU, "Menu"),
        ))
        val actionPort = SemanticActionPort { action ->
            if (!action.enabled) false else {
                when (action.meaning) {
                    LauncherActionMeaning.GO_BACK -> if (dialog != null) dialog = null else destination = LauncherDestination.HOME
                    LauncherActionMeaning.OPEN_SEARCH -> destination = LauncherDestination.SEARCH
                    LauncherActionMeaning.ACTIVATE -> dialog = "Fixture preview"
                    LauncherActionMeaning.OPEN_DETAILS -> dialog = "Fixture details"
                    LauncherActionMeaning.OPEN_MENU -> dialog = "Fixture menu"
                    else -> Unit
                }
                true
            }
        }
        LauncherShell(
            metrics = metrics,
            state = LauncherShellState(
                selectedDestination = destination,
                focusedDestination = focusedDestination,
                status = LauncherShellStatus(
                    clock = clock,
                    readings = listOf(
                        unavailable("Temperature", ShellStatusGlyph.Temperature),
                        unavailable("Memory", ShellStatusGlyph.Memory),
                        unavailable("Storage", ShellStatusGlyph.Storage),
                    ),
                    rightReadings = listOf(
                        unavailable("Wi-Fi", ShellStatusGlyph.Wifi),
                        unavailable("Battery", ShellStatusGlyph.Battery),
                    ),
                ),
                footer = footer,
            ),
            actionPort = actionPort,
            onDestinationSelected = { destination = it },
            onDestinationFocused = { focusedDestination = it },
            content = { bounds ->
                when (destination) {
                    LauncherDestination.HOME -> HomeGalleryFixture(metrics, bounds, variant)
                    LauncherDestination.SEARCH -> Column(bounds,
                        verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm)) {
                        PageHeading("Search fixture")
                        SearchField(query, { query = it }, Modifier.fillMaxWidth())
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs)) {
                            repeat(12) { index -> LauncherButton("Fixture result ${index + 1}",
                                { dialog = "Fixture result ${index + 1}" }, Modifier.fillMaxWidth()) }
                        }
                    }
                    else -> Column(bounds.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm)) {
                        PageHeading("${destination.persistedKey.replaceFirstChar { it.uppercase() }} fixture")
                        LauncherText("This debug page checks the shared content rectangle and controls.")
                        repeat(8) { index -> LauncherButton("Fixture action ${index + 1}",
                            { dialog = "Fixture action ${index + 1}" }, Modifier.fillMaxWidth()) }
                    }
                }
            },
            overlay = {
                dialog?.let { title ->
                    LauncherDialog(title, { dialog = null }) {
                        LauncherText("Debug gallery content. No app is launched and no library data is changed.")
                    }
                }
            },
        )
    }

    override fun onResume() {
        super.onResume()
        reducedMotion = !ValueAnimator.areAnimatorsEnabled()
    }
}

private fun unavailable(label: String, glyph: ShellStatusGlyph) = ShellStatusReading(
    StatusPresentation(label, StatusValue.Unavailable, "$label unavailable"), glyph,
)
