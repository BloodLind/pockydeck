package dev.handheld.launcher.core.domain.repository

import dev.handheld.launcher.core.domain.model.ConfirmBackMapping
import dev.handheld.launcher.core.domain.model.ControllerButtonLayout
import dev.handheld.launcher.core.domain.model.DestinationSnapshot
import dev.handheld.launcher.core.domain.model.LauncherDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class InMemoryControllerPreferenceRepository : ControllerPreferenceRepository {
    private val mutableMapping = MutableStateFlow(ConfirmBackMapping.Default)
    override val confirmBackMapping: Flow<ConfirmBackMapping> = mutableMapping
    private val mutableLayout = MutableStateFlow(ControllerButtonLayout.Default)
    override val buttonLayout: Flow<ControllerButtonLayout> = mutableLayout

    override suspend fun setButtonLayout(layout: ControllerButtonLayout) {
        mutableLayout.value = layout
    }

    override suspend fun setConfirmBackMapping(mapping: ConfirmBackMapping) {
        mutableMapping.value = mapping
    }
}

internal class InMemoryNavigationSnapshotRepository : NavigationSnapshotRepository {
    private val snapshots = LauncherDestination.entries.associateWith {
        MutableStateFlow<DestinationSnapshot?>(null)
    }

    override fun observe(destination: LauncherDestination): Flow<DestinationSnapshot?> =
        snapshots.getValue(destination)

    override suspend fun save(snapshot: DestinationSnapshot) {
        snapshots.getValue(snapshot.destination).value = snapshot
    }

    override suspend fun clear(destination: LauncherDestination) {
        snapshots.getValue(destination).value = null
    }
}
