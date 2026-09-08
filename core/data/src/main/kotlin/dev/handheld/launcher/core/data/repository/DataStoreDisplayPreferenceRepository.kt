package dev.handheld.launcher.core.data.repository

import androidx.datastore.preferences.core.edit
import dev.handheld.launcher.core.data.local.LauncherPreferenceKeys
import dev.handheld.launcher.core.data.local.LauncherPreferencesStore
import dev.handheld.launcher.core.domain.model.DisplayPreferences
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
        )
    }.distinctUntilChanged()

    override suspend fun setUiScalePercent(percent: Int) {
        require(percent in DisplayPreferences.supportedScales)
        dataStore.edit { it[LauncherPreferenceKeys.uiScalePercent] = percent }
    }

    override suspend fun setReduceMotion(enabled: Boolean) {
        dataStore.edit { it[LauncherPreferenceKeys.reduceMotion] = enabled }
    }
}
