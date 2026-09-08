package dev.handheld.launcher.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.handheld.launcher.feature.collection.CollectionDestinationScreen
import dev.handheld.launcher.feature.collection.CollectionScreenCallbacks
import dev.handheld.launcher.feature.collection.CollectionUiState
import dev.handheld.launcher.platform.system.SupportedSystemAction
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader

@Composable
fun LibraryScreen(
    state: CollectionUiState,
    modifier: Modifier,
    callbacks: CollectionScreenCallbacks,
    systemActions: List<SupportedSystemAction>,
    iconLoader: AndroidIconLoader? = null,
    restoreFocusRequest: Int = 1,
    allowFocusRequest: Boolean = true,
) = CollectionDestinationScreen("Library", state, modifier, callbacks, systemActions, iconLoader, restoreFocusRequest, allowFocusRequest)
