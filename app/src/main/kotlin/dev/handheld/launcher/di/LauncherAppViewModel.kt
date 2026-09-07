package dev.handheld.launcher.di

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.handheld.launcher.core.domain.model.*
import dev.handheld.launcher.core.domain.repository.ControllerPreferenceRepository
import dev.handheld.launcher.navigation.LauncherNavigationController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Small process-restorable navigation keys and launcher preferences; no UI focus objects. */
class LauncherAppViewModel(
    private val preferences: ControllerPreferenceRepository,
    saved: SavedStateHandle,
) : ViewModel() {
    private val initialDestination = LauncherDestination.fromPersistedKey(saved["destination"] ?: "home") ?: LauncherDestination.HOME
    private val initialOrigin = if (saved.get<Boolean>("shortcutOrigin") == true) NavigationOrigin.ShortcutSearch(initialDestination)
        else NavigationOrigin.Destination(initialDestination)
    val navigation = LauncherNavigationController(when (saved.get<String>("locationKind")) {
        "shortcut" -> LauncherLocation.ShortcutSearch(initialDestination)
        "details" -> saved.get<String>("detailsId")?.takeIf { it.isNotBlank() }
            ?.let { LauncherLocation.ItemDetails(ItemId(it), initialOrigin) }
            ?: LauncherLocation.Destination(initialDestination)
        else -> LauncherLocation.Destination(initialDestination)
    })
    val mapping = preferences.confirmBackMapping.stateIn(viewModelScope, SharingStarted.Eagerly, ConfirmBackMapping.Default)
    val error = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            navigation.location.collect { location ->
                when (location) {
                    is LauncherLocation.Destination -> {
                        saved["locationKind"] = "destination"
                        saved["destination"] = location.destination.persistedKey
                    }
                    is LauncherLocation.ShortcutSearch -> {
                        saved["locationKind"] = "shortcut"
                        saved["destination"] = location.returnDestination.persistedKey
                    }
                    is LauncherLocation.ItemDetails -> {
                        saved["locationKind"] = "details"
                        saved["detailsId"] = location.itemId.value
                        saved["shortcutOrigin"] = location.origin is NavigationOrigin.ShortcutSearch
                        saved["destination"] = when (val origin = location.origin) {
                            is NavigationOrigin.Destination -> origin.destination.persistedKey
                            is NavigationOrigin.ShortcutSearch -> origin.returnDestination.persistedKey
                        }
                    }
                }
            }
        }
    }

    fun setMapping(value: ConfirmBackMapping) {
        viewModelScope.launch {
            try { preferences.setConfirmBackMapping(value) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error.value = "Could not save controller mapping. Try again." }
        }
    }
}
