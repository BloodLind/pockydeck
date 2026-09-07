package dev.handheld.launcher.core.data.repository

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.handheld.launcher.core.data.local.LauncherPreferenceKeys
import dev.handheld.launcher.core.data.local.LauncherPreferencesStore
import dev.handheld.launcher.core.domain.model.ConfirmBackMapping
import dev.handheld.launcher.core.domain.model.ControllerFaceButton
import dev.handheld.launcher.core.domain.model.DestinationSnapshot
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.model.PageStateKey
import dev.handheld.launcher.core.domain.repository.ControllerPreferenceRepository
import dev.handheld.launcher.core.domain.repository.NavigationSnapshotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DataStoreControllerPreferenceRepository(
    store: LauncherPreferencesStore,
) : ControllerPreferenceRepository {
    private val dataStore = store.dataStore

    override val confirmBackMapping: Flow<ConfirmBackMapping> = dataStore.data
        .map(::decodeMapping)
        .distinctUntilChanged()

    override suspend fun setConfirmBackMapping(mapping: ConfirmBackMapping) {
        dataStore.edit { preferences ->
            preferences[LauncherPreferenceKeys.confirmButton] = mapping.confirm.persistedValue
            preferences[LauncherPreferenceKeys.backButton] = mapping.back.persistedValue
        }
    }

    private fun decodeMapping(preferences: Preferences): ConfirmBackMapping {
        val confirm = preferences.safeString(LauncherPreferenceKeys.confirmButton).toControllerButton()
        val back = preferences.safeString(LauncherPreferenceKeys.backButton).toControllerButton()
        return if (confirm != null && back != null && confirm != back) {
            ConfirmBackMapping(confirm, back)
        } else {
            ConfirmBackMapping.Default
        }
    }

    private val ControllerFaceButton.persistedValue: String
        get() = when (this) {
            ControllerFaceButton.A -> "a"
            ControllerFaceButton.B -> "b"
        }

    private fun String?.toControllerButton(): ControllerFaceButton? = when (this) {
        "a" -> ControllerFaceButton.A
        "b" -> ControllerFaceButton.B
        else -> null
    }
}

class DataStoreNavigationSnapshotRepository(
    store: LauncherPreferencesStore,
) : NavigationSnapshotRepository {
    private val dataStore = store.dataStore

    override fun observe(destination: LauncherDestination): Flow<DestinationSnapshot?> {
        val keys = LauncherPreferenceKeys.snapshot(destination)
        return dataStore.data
            .map { preferences -> preferences.decodeSnapshot(destination, keys) }
            .distinctUntilChanged()
    }

    override suspend fun save(snapshot: DestinationSnapshot) {
        val keys = LauncherPreferenceKeys.snapshot(snapshot.destination)
        dataStore.edit { preferences ->
            preferences[keys.version] = SNAPSHOT_VERSION
            preferences.setOrRemove(keys.selectedItemId, snapshot.selectedItemId?.value)
            preferences.setOrRemove(keys.firstVisibleItemId, snapshot.firstVisibleItemId?.value)
            preferences[keys.firstVisibleOffsetPx] = snapshot.firstVisibleOffsetPx
            preferences[keys.query] = snapshot.query
            preferences.setOrRemove(keys.filterKey, snapshot.filterKey?.value)
            preferences.setOrRemove(keys.sortKey, snapshot.sortKey?.value)
        }
    }

    override suspend fun clear(destination: LauncherDestination) {
        val keys = LauncherPreferenceKeys.snapshot(destination)
        dataStore.edit { preferences ->
            preferences.remove(keys.version)
            preferences.remove(keys.selectedItemId)
            preferences.remove(keys.firstVisibleItemId)
            preferences.remove(keys.firstVisibleOffsetPx)
            preferences.remove(keys.query)
            preferences.remove(keys.filterKey)
            preferences.remove(keys.sortKey)
        }
    }

    private fun Preferences.decodeSnapshot(
        destination: LauncherDestination,
        keys: LauncherPreferenceKeys.SnapshotKeys,
    ): DestinationSnapshot? {
        if (safeInt(keys.version) != SNAPSHOT_VERSION) return null
        return DestinationSnapshot(
            destination = destination,
            selectedItemId = safeString(keys.selectedItemId).toItemIdOrNull(),
            firstVisibleItemId = safeString(keys.firstVisibleItemId).toItemIdOrNull(),
            firstVisibleOffsetPx = safeInt(keys.firstVisibleOffsetPx)?.coerceAtLeast(0) ?: 0,
            query = safeString(keys.query).orEmpty(),
            filterKey = safeString(keys.filterKey).toPageStateKeyOrNull(),
            sortKey = safeString(keys.sortKey).toPageStateKeyOrNull(),
        )
    }

    private fun String?.toItemIdOrNull(): ItemId? =
        this?.let { value -> runCatching { ItemId(value) }.getOrNull() }

    private fun String?.toPageStateKeyOrNull(): PageStateKey? =
        this?.let { value -> runCatching { PageStateKey(value) }.getOrNull() }

    private fun MutablePreferences.setOrRemove(
        key: Preferences.Key<String>,
        value: String?,
    ) {
        if (value == null) remove(key) else this[key] = value
    }

    private companion object {
        const val SNAPSHOT_VERSION = 1
    }
}

private fun Preferences.safeString(key: Preferences.Key<String>): String? =
    asMap()[key] as? String

private fun Preferences.safeInt(key: Preferences.Key<Int>): Int? =
    asMap()[key] as? Int
