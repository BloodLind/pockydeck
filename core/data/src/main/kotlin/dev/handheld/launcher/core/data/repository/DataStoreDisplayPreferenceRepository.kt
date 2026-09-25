package dev.handheld.launcher.core.data.repository

import androidx.datastore.preferences.core.edit
import dev.handheld.launcher.core.data.local.LauncherPreferenceKeys
import dev.handheld.launcher.core.data.local.LauncherPreferencesStore
import dev.handheld.launcher.core.domain.model.DisplayPreferences
import dev.handheld.launcher.core.domain.model.BackgroundTint
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.repository.DisplayPreferenceRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DataStoreDisplayPreferenceRepository(store: LauncherPreferencesStore) : DisplayPreferenceRepository {
    private val dataStore = store.dataStore
    override val preferences = dataStore.data.map { stored ->
        val values = stored.asMap()
        DisplayPreferences(
            uiScalePercent = (values[LauncherPreferenceKeys.uiScalePercent] as? Int)
                ?.takeIf { it in DisplayPreferences.supportedScales } ?: 100,
            reduceMotion = values[LauncherPreferenceKeys.reduceMotion] as? Boolean ?: false,
            homeArtworkBackground = values[LauncherPreferenceKeys.homeArtworkBackground] as? Boolean ?: false,
            listArtworkBackground = values[LauncherPreferenceKeys.listArtworkBackground] as? Boolean ?: false,
            backgroundTint = BackgroundTint.fromPersistedKey(values[LauncherPreferenceKeys.backgroundTint] as? String),
            backgroundCustomColorRgb = (values[LauncherPreferenceKeys.backgroundCustomColorRgb] as? Int)
                ?.takeIf { it in 0..0xFFFFFF },
            backgroundTintPercent = (values[LauncherPreferenceKeys.backgroundTintPercent] as? Int)
                ?.takeIf { it in DisplayPreferences.supportedBackgroundLevels } ?: 40,
            backgroundGrainPercent = (values[LauncherPreferenceKeys.backgroundGrainPercent] as? Int)
                ?.takeIf { it in DisplayPreferences.supportedBackgroundLevels } ?: 30,
            listDestinations = decodeListDestinations(values[LauncherPreferenceKeys.listDestinations]),
            gridSizePercent = (values[LauncherPreferenceKeys.gridSizePercent] as? Int)
                ?.takeIf { it in DisplayPreferences.supportedGridSizes } ?: 100,
        )
    }.distinctUntilChanged()

    override suspend fun setUiScalePercent(percent: Int) {
        require(percent in DisplayPreferences.supportedScales)
        dataStore.edit { it[LauncherPreferenceKeys.uiScalePercent] = percent }
    }

    override suspend fun setReduceMotion(enabled: Boolean) {
        dataStore.edit { it[LauncherPreferenceKeys.reduceMotion] = enabled }
    }

    override suspend fun setHomeArtworkBackground(enabled: Boolean) {
        dataStore.edit { it[LauncherPreferenceKeys.homeArtworkBackground] = enabled }
    }

    override suspend fun setListArtworkBackground(enabled: Boolean) {
        dataStore.edit { it[LauncherPreferenceKeys.listArtworkBackground] = enabled }
    }

    override suspend fun setGridSizePercent(percent: Int) {
        require(percent in DisplayPreferences.supportedGridSizes)
        dataStore.edit { it[LauncherPreferenceKeys.gridSizePercent] = percent }
    }

    override suspend fun setBackgroundTint(tint: BackgroundTint) {
        dataStore.edit {
            it[LauncherPreferenceKeys.backgroundTint] = tint.persistedKey
            it.remove(LauncherPreferenceKeys.backgroundCustomColorRgb)
        }
    }

    override suspend fun setBackgroundCustomColorRgb(rgb: Int) {
        require(rgb in 0..0xFFFFFF)
        dataStore.edit { it[LauncherPreferenceKeys.backgroundCustomColorRgb] = rgb }
    }

    override suspend fun setBackgroundTintPercent(percent: Int) {
        require(percent in DisplayPreferences.supportedBackgroundLevels)
        dataStore.edit { it[LauncherPreferenceKeys.backgroundTintPercent] = percent }
    }

    override suspend fun setBackgroundGrainPercent(percent: Int) {
        require(percent in DisplayPreferences.supportedBackgroundLevels)
        dataStore.edit { it[LauncherPreferenceKeys.backgroundGrainPercent] = percent }
    }

    override suspend fun setCollectionListMode(destination: LauncherDestination, isList: Boolean) {
        require(destination in DisplayPreferences.collectionDestinations)
        dataStore.edit { stored ->
            val current = decodeListDestinations(stored.asMap()[LauncherPreferenceKeys.listDestinations])
            val updated = if (isList) current + destination else current - destination
            stored[LauncherPreferenceKeys.listDestinations] = updated.mapTo(mutableSetOf()) { it.persistedKey }
        }
    }
}

private fun decodeListDestinations(value: Any?): Set<LauncherDestination> {
    val keys = value as? Set<*> ?: return emptySet()
    return DisplayPreferences.collectionDestinations.filterTo(mutableSetOf()) { it.persistedKey in keys }
}
