package dev.handheld.launcher.core.data.repository

import dev.handheld.launcher.core.domain.model.ConfirmBackMapping
import dev.handheld.launcher.core.domain.model.DestinationSnapshot
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.repository.ControllerPreferenceRepository
import dev.handheld.launcher.core.domain.repository.NavigationSnapshotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeControllerPreferenceRepository(
    initialMapping: ConfirmBackMapping = ConfirmBackMapping.Default,
) : ControllerPreferenceRepository {
    private val mutableMapping = MutableStateFlow(initialMapping)

    override val confirmBackMapping: Flow<ConfirmBackMapping> = mutableMapping

    override suspend fun setConfirmBackMapping(mapping: ConfirmBackMapping) {
        mutableMapping.value = mapping
    }
}

class FakeNavigationSnapshotRepository(
    initialSnapshots: Collection<DestinationSnapshot> = emptyList(),
) : NavigationSnapshotRepository {
    private val snapshots = MutableStateFlow(
        initialSnapshots.associateBy(DestinationSnapshot::destination),
    )

    override fun observe(destination: LauncherDestination): Flow<DestinationSnapshot?> =
        snapshots.map { it[destination] }

    override suspend fun save(snapshot: DestinationSnapshot) {
        snapshots.update { it + (snapshot.destination to snapshot) }
    }

    override suspend fun clear(destination: LauncherDestination) {
        snapshots.update { it - destination }
    }
}
