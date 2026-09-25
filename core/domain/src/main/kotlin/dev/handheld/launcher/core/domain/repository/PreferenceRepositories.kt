package dev.handheld.launcher.core.domain.repository

import dev.handheld.launcher.core.domain.model.ConfirmBackMapping
import dev.handheld.launcher.core.domain.model.ControllerButtonLayout
import dev.handheld.launcher.core.domain.model.BackgroundTint
import dev.handheld.launcher.core.domain.model.DestinationSnapshot
import dev.handheld.launcher.core.domain.model.DisplayPreferences
import dev.handheld.launcher.core.domain.model.LauncherDestination
import kotlinx.coroutines.flow.Flow

/** Missing or invalid fields fall back independently to the mapping and layout defaults. */
interface ControllerPreferenceRepository {
    val confirmBackMapping: Flow<ConfirmBackMapping>
    val buttonLayout: Flow<ControllerButtonLayout>

    suspend fun setConfirmBackMapping(mapping: ConfirmBackMapping)
    suspend fun setButtonLayout(layout: ControllerButtonLayout)
}

interface DisplayPreferenceRepository {
    val preferences: Flow<DisplayPreferences>
    suspend fun setUiScalePercent(percent: Int)
    suspend fun setGridSizePercent(percent: Int)
    suspend fun setReduceMotion(enabled: Boolean)
    suspend fun setHomeArtworkBackground(enabled: Boolean)
    suspend fun setListArtworkBackground(enabled: Boolean)
    suspend fun setBackgroundTint(tint: BackgroundTint)
    suspend fun setBackgroundCustomColorRgb(rgb: Int)
    suspend fun setBackgroundTintPercent(percent: Int)
    suspend fun setBackgroundGrainPercent(percent: Int)
    suspend fun setCollectionListMode(destination: LauncherDestination, isList: Boolean)
}

/** Each destination owns an independent compact snapshot. */
interface NavigationSnapshotRepository {
    fun observe(destination: LauncherDestination): Flow<DestinationSnapshot?>

    suspend fun save(snapshot: DestinationSnapshot)

    suspend fun clear(destination: LauncherDestination)
}
