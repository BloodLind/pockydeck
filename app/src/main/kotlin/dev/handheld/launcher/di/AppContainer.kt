package dev.handheld.launcher.di

import android.content.Context
import dev.handheld.launcher.contract.ActivityRequestPort
import dev.handheld.launcher.core.data.local.LauncherDatabase
import dev.handheld.launcher.core.data.local.LauncherPreferencesStore
import dev.handheld.launcher.core.data.repository.DataStoreControllerPreferenceRepository
import dev.handheld.launcher.core.data.repository.DataStoreNavigationSnapshotRepository
import dev.handheld.launcher.core.data.repository.RoomCatalogRepository
import dev.handheld.launcher.core.data.repository.RoomFavoriteRepository
import dev.handheld.launcher.core.data.repository.RoomItemOverrideRepository
import dev.handheld.launcher.core.data.repository.RoomSuccessfulOpenRepository
import dev.handheld.launcher.core.domain.repository.CatalogRepository
import dev.handheld.launcher.core.domain.repository.ControllerPreferenceRepository
import dev.handheld.launcher.core.domain.repository.FavoriteRepository
import dev.handheld.launcher.core.domain.repository.ItemOverrideRepository
import dev.handheld.launcher.core.domain.repository.NavigationSnapshotRepository
import dev.handheld.launcher.core.domain.repository.SuccessfulOpenRepository

class AppContainer(
    context: Context,
    val activityRequestPort: ActivityRequestPort = InMemoryActivityRequestPort(),
) {
    private val applicationContext = context.applicationContext

    // One application-owned storage instance per file; discovery is not a startup prerequisite.
    private val database by lazy { LauncherDatabase.open(applicationContext) }
    private val preferences by lazy { LauncherPreferencesStore.open(applicationContext) }

    val catalogRepository: CatalogRepository by lazy { RoomCatalogRepository(database) }
    val favoriteRepository: FavoriteRepository by lazy { RoomFavoriteRepository(database) }
    val itemOverrideRepository: ItemOverrideRepository by lazy { RoomItemOverrideRepository(database) }
    val successfulOpenRepository: SuccessfulOpenRepository by lazy {
        RoomSuccessfulOpenRepository(database)
    }
    val controllerPreferenceRepository: ControllerPreferenceRepository by lazy {
        DataStoreControllerPreferenceRepository(preferences)
    }
    val navigationSnapshotRepository: NavigationSnapshotRepository by lazy {
        DataStoreNavigationSnapshotRepository(preferences)
    }

    fun mainViewModelFactory(): MainViewModelFactory =
        MainViewModelFactory(activityRequestPort)
}
