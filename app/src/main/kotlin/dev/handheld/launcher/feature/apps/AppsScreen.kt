package dev.handheld.launcher.feature.apps

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.handheld.launcher.feature.collection.CollectionDestinationScreen
import dev.handheld.launcher.feature.collection.CollectionScreenCallbacks
import dev.handheld.launcher.feature.collection.CollectionUiState
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader

@Composable
fun AppsScreen(
    state: CollectionUiState,
    modifier: Modifier,
    callbacks: CollectionScreenCallbacks,
    iconLoader: AndroidIconLoader? = null,
    restoreFocusRequest: Int = 1,
    allowFocusRequest: Boolean = true,
) = CollectionDestinationScreen("Apps", state, modifier, callbacks, iconLoader = iconLoader,
    restoreFocusRequest = restoreFocusRequest, allowFocusRequest = allowFocusRequest)
