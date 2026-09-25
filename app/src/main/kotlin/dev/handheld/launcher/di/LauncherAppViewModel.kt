package dev.handheld.launcher.di

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.handheld.launcher.core.domain.model.*
import dev.handheld.launcher.core.domain.repository.ControllerPreferenceRepository
import dev.handheld.launcher.core.domain.repository.DisplayPreferenceRepository
import dev.handheld.launcher.navigation.LauncherNavigationController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Small process-restorable navigation keys and launcher preferences; no UI focus objects. */
class LauncherAppViewModel(
    private val preferences: ControllerPreferenceRepository,
    saved: SavedStateHandle,
    private val displayPreferences: DisplayPreferenceRepository? = null,
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
    val buttonLayout = preferences.buttonLayout.stateIn(viewModelScope, SharingStarted.Eagerly, ControllerButtonLayout.Default)
    private val savedDisplay = (displayPreferences?.preferences ?: kotlinx.coroutines.flow.flowOf(DisplayPreferences()))
        .stateIn(viewModelScope, SharingStarted.Eagerly, DisplayPreferences())
    private data class BackgroundChoice(val preset: BackgroundTint? = null, val rgb: Int? = null)
    private val pendingBackground = MutableStateFlow<BackgroundChoice?>(null)
    private val backgroundWrites = Channel<BackgroundChoice>(Channel.CONFLATED)
    val display = combine(savedDisplay, pendingBackground) { saved, pending ->
        if (pending == null) saved else saved.copy(backgroundTint = pending.preset ?: saved.backgroundTint,
            backgroundCustomColorRgb = pending.rgb)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DisplayPreferences())
    val error = MutableStateFlow<String?>(null)

    init {
        // Immediate preview with one ordered writer. Drag events replace queued
        // colors, and stale disk emissions cannot roll the preview backward.
        viewModelScope.launch {
            for (choice in backgroundWrites) {
                try {
                    if (choice.preset != null) displayPreferences?.setBackgroundTint(choice.preset)
                    else choice.rgb?.let { displayPreferences?.setBackgroundCustomColorRgb(it) }
                    if (displayPreferences != null) savedDisplay.first {
                        it.backgroundCustomColorRgb == choice.rgb &&
                            (choice.preset == null || it.backgroundTint == choice.preset)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error.value = "Could not save background color. Try again." }
                finally { pendingBackground.compareAndSet(choice, null) }
            }
        }
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

    fun setButtonLayout(value: ControllerButtonLayout) {
        viewModelScope.launch {
            try { preferences.setButtonLayout(value) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error.value = "Could not save button layout. Try again." }
        }
    }

    fun setUiScalePercent(value: Int) = saveDisplay { displayPreferences?.setUiScalePercent(value) }
    fun setGridSizePercent(value: Int) = saveDisplay { displayPreferences?.setGridSizePercent(value) }
    fun setReduceMotion(value: Boolean) = saveDisplay { displayPreferences?.setReduceMotion(value) }
    fun setHomeArtworkBackground(value: Boolean) = saveDisplay { displayPreferences?.setHomeArtworkBackground(value) }
    fun setListArtworkBackground(value: Boolean) = saveDisplay { displayPreferences?.setListArtworkBackground(value) }
    fun setBackgroundTint(value: BackgroundTint) = selectBackground(BackgroundChoice(preset = value))
    fun setBackgroundCustomColorRgb(value: Int) {
        require(value in 0..0xFFFFFF)
        selectBackground(BackgroundChoice(rgb = value))
    }
    private fun selectBackground(choice: BackgroundChoice) {
        pendingBackground.value = choice
        backgroundWrites.trySend(choice)
    }
    fun setBackgroundTintPercent(value: Int) = saveDisplay { displayPreferences?.setBackgroundTintPercent(value) }
    fun setBackgroundGrainPercent(value: Int) = saveDisplay { displayPreferences?.setBackgroundGrainPercent(value) }
    fun setCollectionListMode(destination: LauncherDestination, isList: Boolean) =
        saveDisplay { displayPreferences?.setCollectionListMode(destination, isList) }

    private fun saveDisplay(write: suspend () -> Unit) {
        viewModelScope.launch {
            try { write() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error.value = "Could not save display settings. Try again." }
        }
    }
}
