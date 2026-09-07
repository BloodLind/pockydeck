package dev.handheld.launcher.core.domain.repository

import dev.handheld.launcher.core.domain.model.ConfirmBackMapping
import dev.handheld.launcher.core.domain.model.DestinationSnapshot
import dev.handheld.launcher.core.domain.model.LauncherDestination
import kotlinx.coroutines.flow.Flow

/** Absence or invalid optional storage must emit [ConfirmBackMapping.Default]. */
interface ControllerPreferenceRepository {
    val confirmBackMapping: Flow<ConfirmBackMapping>

    suspend fun setConfirmBackMapping(mapping: ConfirmBackMapping)
}

/** Each destination owns an independent compact snapshot. */
interface NavigationSnapshotRepository {
    fun observe(destination: LauncherDestination): Flow<DestinationSnapshot?>

    suspend fun save(snapshot: DestinationSnapshot)

    suspend fun clear(destination: LauncherDestination)
}
