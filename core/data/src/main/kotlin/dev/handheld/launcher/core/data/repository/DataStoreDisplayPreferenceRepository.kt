package dev.handheld.launcher.core.data.repository

import androidx.datastore.preferences.core.edit
import dev.handheld.launcher.core.data.local.LauncherPreferenceKeys
import dev.handheld.launcher.core.data.local.LauncherPreferencesStore
import dev.handheld.launcher.core.domain.model.DisplayPreferences
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

    override suspend fun setGridSizePercent(percent: Int) {
        require(percent in DisplayPreferences.supportedGridSizes)
        dataStore.edit { it[LauncherPreferenceKeys.gridSizePercent] = percent }
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
