package dev.handheld.launcher.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.contract.LocalControllerInput
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.model.ConfirmBackMapping
import dev.handheld.launcher.core.domain.rom.RomSource
import dev.handheld.launcher.core.domain.rom.RomSourceStatus
import dev.handheld.launcher.feature.settings.sources.RomSourcesScreenState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Scan fixtures only; never scans/removes user folders or changes preferences. */
@OptIn(ExperimentalComposeUiApi::class)
class RomSourcesScrollStabilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun wideTouchScrollKeepsItsRowThroughScanStatusAndFolderInsertions() = verify(780.dp, false)
    @Test fun compactSettingsKeepTheSameRomRowWhenNoticesAboveChange() = verify(500.dp, false)
    @Test fun controllerFocusStaysOnTheSameControlDuringScanUpdates() = verify(780.dp, true)

    private fun verify(width: Dp, controller: Boolean) {
        val originals = List(30) { source(it) }
        var sources by mutableStateOf(RomSourcesScreenState(sources = originals,
            storageAccessGranted = true, busy = true))
        compose.setContent {
            LauncherTheme(reducedMotion = true) {
                CompositionLocalProvider(LocalControllerInput provides controller) {
                    val inputMode = LocalInputModeManager.current
                    LaunchedEffect(Unit) { inputMode.requestInputMode(if (controller) InputMode.Keyboard else InputMode.Touch) }
                    Box(Modifier.size(width, 300.dp)) {
                        SettingsScreen(SettingsScreenState(ConfirmBackMapping.Default, "Fixture", romSources = sources),
                            Modifier, SettingsCallbacks({}, {}, {}, {}), emptyList(), initialSection = "ROM folders")
                    }
                }
            }
        }
        val list = compose.onNodeWithTag("settings-body-list")
        val anchor = "rom-source:fixture:15:console"
        list.performScrollToKey(anchor)
        val row = compose.onNode(hasContentDescription("Console for this folder") and hasAnyAncestor(hasTestTag(anchor)))
        if (controller) row.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitForIdle()
        val before = row.fetchSemanticsNode().boundsInRoot.top
        repeat(20) { step ->
            compose.runOnIdle {
                val busy = step % 2 == 0
                sources = sources.copy(
                    busy = busy, discoveryBusy = busy, discoveryFoldersVisited = step * 100,
                    message = if (busy) "A scan warning that wraps above the current folder. ".repeat(8) else null,
                    preparingText = if (step % 3 == 0) "Preparing fixture archive…" else null,
                    sources = (if (busy) listOf(source(-1)) else emptyList()) + originals.map { source ->
                        if (source.id.value.substringAfterLast(':').toInt() <= 15) source.copy(
                            status = if (busy) RomSourceStatus.SCANNING else RomSourceStatus.READY,
                            gameCount = step * 101, unidentifiedCount = if (busy) 40 else 0,
                            error = if (busy) "Scan warning with enough detail to wrap to several lines. ".repeat(4) else null,
                        ) else source
                    },
                )
            }
            compose.waitForIdle()
            row.assertIsDisplayed()
            assertEquals("Update $step must retain the same row and offset", before,
                row.fetchSemanticsNode().boundsInRoot.top, 1f)
            if (controller) row.assertIsFocused()
        }
        // Anchoring must not lock intentional navigation to another folder.
        list.performScrollToKey("rom-source:fixture:22:console")
        compose.onNodeWithTag("rom-source:fixture:22:console").assertIsDisplayed()
        if (controller) {
            val next = compose.onNode(hasContentDescription("Console for this folder") and
                hasAnyAncestor(hasTestTag("rom-source:fixture:22:console")))
            next.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            next.assertIsFocused()
        } else compose.onNodeWithTag(anchor).assertDoesNotExist()
    }

    private fun source(index: Int) = RomSource(CatalogSourceId("fixture:$index"),
        "content://fixture/$index", "$index", "Console $index", true, RomSourceStatus.READY,
        defaultPlatformId = "ps2", gameCount = 10, automaticallyDiscovered = true)
}
