package dev.handheld.launcher.rom

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.feature.settings.sources.RomSourcesCallbacks
import dev.handheld.launcher.feature.settings.sources.RomSourcesScreen
import dev.handheld.launcher.feature.settings.sources.RomSourcesScreenState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Setup is explicit; this test never launches Android settings or grants a permission. */
class RomStorageSetupTest {
    @get:Rule val compose = createComposeRule()

    @Test fun missingAccessKeepsManualChoiceAndGrantEnablesDiscoveryControls() {
        var state by mutableStateOf(RomSourcesScreenState())
        var setupRequests = 0
        var manualRequests = 0
        var discoveryChanges = mutableListOf<Boolean>()
        compose.setContent {
            LauncherTheme {
                RomSourcesScreen(state, RomSourcesCallbacks(
                    onAddSource = { manualRequests++ },
                    onSetupStorageAccess = { setupRequests++ },
                    onSetAutomaticDiscovery = { discoveryChanges.add(it) },
                ))
            }
        }
        compose.onNodeWithContentDescription("Set up automatic discovery").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Add ROM folder").performClick()
        compose.onNodeWithContentDescription("Discover new console folders").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, setupRequests)
            assertEquals(1, manualRequests)
            state = state.copy(storageAccessGranted = true)
        }
        compose.onNodeWithContentDescription("Storage access enabled").assertIsDisplayed()
        compose.onNodeWithContentDescription("Discover new console folders").performClick()
        compose.runOnIdle { assertEquals(listOf(false), discoveryChanges) }
    }
}
