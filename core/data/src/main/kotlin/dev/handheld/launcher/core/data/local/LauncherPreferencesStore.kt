package dev.handheld.launcher.core.data.local

import android.content.Context
import androidx.datastore.dataStoreFile
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.handheld.launcher.core.domain.model.LauncherDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin

/**
 * Owns the single application-scoped preferences DataStore used by launcher repositories.
 * Call [close] only when the owning application/test scope is being torn down.
 */
class LauncherPreferencesStore private constructor(
    internal val dataStore: DataStore<Preferences>,
    private val ownerJob: kotlinx.coroutines.CompletableJob,
) {
    suspend fun close() {
        ownerJob.cancelAndJoin()
    }

    companion object {
        const val DEFAULT_FILE_NAME = "handheld-launcher.preferences_pb"

        fun open(
            context: Context,
            fileName: String = DEFAULT_FILE_NAME,
        ): LauncherPreferencesStore {
            require(fileName.isNotBlank()) { "Preferences file name must not be blank" }
            require('/' !in fileName && '\\' !in fileName) {
                "Preferences file name must not contain path separators"
            }
            require(fileName.endsWith(PREFERENCES_FILE_SUFFIX)) {
                "Preferences file name must end with $PREFERENCES_FILE_SUFFIX"
            }

            val applicationContext = context.applicationContext
            val ownerJob = SupervisorJob()
            val dataStore = PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = CoroutineScope(ownerJob + Dispatchers.IO),
                produceFile = { applicationContext.dataStoreFile(fileName) },
            )
            return LauncherPreferencesStore(dataStore, ownerJob)
        }

        private const val PREFERENCES_FILE_SUFFIX = ".preferences_pb"
    }
}

internal object LauncherPreferenceKeys {
    val uiScalePercent = intPreferencesKey("display.ui_scale_percent")
    val gridSizePercent = intPreferencesKey("display.grid_size_percent")
    val reduceMotion = booleanPreferencesKey("display.reduce_motion")
    val listDestinations = stringSetPreferencesKey("display.list_destinations")
    val confirmButton = stringPreferencesKey("controller.confirm_button")
    val backButton = stringPreferencesKey("controller.back_button")

    fun snapshot(destination: LauncherDestination): SnapshotKeys {
        val prefix = "navigation.${destination.persistedKey}"
        return SnapshotKeys(
            version = intPreferencesKey("$prefix.version"),
            selectedItemId = stringPreferencesKey("$prefix.selected_item_id"),
            firstVisibleItemId = stringPreferencesKey("$prefix.first_visible_item_id"),
            firstVisibleOffsetPx = intPreferencesKey("$prefix.first_visible_offset_px"),
            query = stringPreferencesKey("$prefix.query"),
            filterKey = stringPreferencesKey("$prefix.filter_key"),
            sortKey = stringPreferencesKey("$prefix.sort_key"),
        )
    }

    data class SnapshotKeys(
        val version: Preferences.Key<Int>,
        val selectedItemId: Preferences.Key<String>,
        val firstVisibleItemId: Preferences.Key<String>,
        val firstVisibleOffsetPx: Preferences.Key<Int>,
        val query: Preferences.Key<String>,
        val filterKey: Preferences.Key<String>,
        val sortKey: Preferences.Key<String>,
    ) {
    }
}
