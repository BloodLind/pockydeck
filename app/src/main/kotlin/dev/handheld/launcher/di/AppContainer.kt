package dev.handheld.launcher.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.handheld.launcher.feature.collection.CollectionViewModel
import dev.handheld.launcher.feature.home.HomeViewModelFactory
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.launch.LaunchCoordinator
import dev.handheld.launcher.platform.home.HomeRoleRequestCoordinator
import dev.handheld.launcher.platform.system.SystemActionRegistry
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader
import dev.handheld.launcher.core.data.android.status.AndroidDeviceStatusSource
import dev.handheld.launcher.contract.ActivityRequestPort
import dev.handheld.launcher.core.data.android.apps.AndroidComponentLaunchDispatcher
import dev.handheld.launcher.core.data.android.apps.BroadcastAndroidPackageChangeMonitor
import dev.handheld.launcher.core.data.discovery.AndroidCatalogRefreshCoordinator
import dev.handheld.launcher.core.data.discovery.PackageManagerAndroidAppDiscovery
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
import dev.handheld.launcher.core.domain.repository.LaunchDispatcher
import dev.handheld.launcher.core.domain.repository.NavigationSnapshotRepository
import dev.handheld.launcher.core.domain.repository.SuccessfulOpenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(
    context: Context,
    val activityRequestPort: ActivityRequestPort = InMemoryActivityRequestPort(),
) {
    private val applicationContext = context.applicationContext

    // One application-owned storage instance per file; discovery is not a startup prerequisite.
    private val database by lazy { LauncherDatabase.open(applicationContext) }
    private val preferences by lazy { LauncherPreferencesStore.open(applicationContext) }
    private val applicationScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

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
    val androidCatalog by lazy {
        AndroidCatalogRefreshCoordinator(
            catalogRepository,
            PackageManagerAndroidAppDiscovery(applicationContext),
            BroadcastAndroidPackageChangeMonitor(applicationContext),
            applicationScope,
        )
    }
    val launchDispatcher: LaunchDispatcher by lazy {
        AndroidComponentLaunchDispatcher(applicationContext)
    }
    val launchCoordinator by lazy {
        LaunchCoordinator(catalogRepository, navigationSnapshotRepository, launchDispatcher,
            successfulOpenRepository, applicationScope)
    }
    val homeRoleRequests by lazy { HomeRoleRequestCoordinator(activityRequestPort) }
    val iconLoader by lazy { AndroidIconLoader(applicationContext) }
    val systemActions by lazy { SystemActionRegistry(applicationContext) }
    val deviceStatus by lazy { AndroidDeviceStatusSource(applicationContext) }

    fun launcherViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer { LauncherAppViewModel(controllerPreferenceRepository, createSavedStateHandle()) }
    }

    fun homeViewModelFactory() = HomeViewModelFactory(catalogRepository, successfulOpenRepository,
        itemOverrideRepository, navigationSnapshotRepository, launchCoordinator,
        androidCatalog.refreshState, androidCatalog::refresh)

    fun collectionViewModelFactory(destination: LauncherDestination): ViewModelProvider.Factory = viewModelFactory {
        initializer { CollectionViewModel(destination, catalogRepository, favoriteRepository,
            itemOverrideRepository, successfulOpenRepository, navigationSnapshotRepository, createSavedStateHandle()) }
    }

    fun mainViewModelFactory(): MainViewModelFactory =
        MainViewModelFactory(activityRequestPort)
}
