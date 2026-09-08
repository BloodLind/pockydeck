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
import dev.handheld.launcher.core.data.rom.repository.RoomRomLibraryRepository
import dev.handheld.launcher.core.data.rom.source.SafRomSourceAccess
import dev.handheld.launcher.core.data.rom.source.shared.SharedStoragePaths
import dev.handheld.launcher.core.data.rom.source.shared.SharedStorageRomSourceAccess
import dev.handheld.launcher.core.data.rom.source.RoutingRomSourceAccess
import dev.handheld.launcher.core.data.rom.source.shared.AndroidSharedRomDiscovery
import dev.handheld.launcher.core.data.rom.scan.RomScanCoordinator
import dev.handheld.launcher.core.data.rom.archive.PreparedRomCache
import dev.handheld.launcher.core.data.rom.emulator.AndroidEmulatorResolver
import dev.handheld.launcher.core.data.rom.emulator.AndroidRomLauncher
import dev.handheld.launcher.core.domain.model.LaunchTarget
import dev.handheld.launcher.core.domain.model.LaunchRequest
import dev.handheld.launcher.core.domain.model.LaunchAcknowledgement
import dev.handheld.launcher.rom.RomFeatureController
import dev.handheld.launcher.core.data.repository.DataStoreControllerPreferenceRepository
import dev.handheld.launcher.core.data.repository.DataStoreDisplayPreferenceRepository
import dev.handheld.launcher.core.data.repository.DataStoreNavigationSnapshotRepository
import dev.handheld.launcher.core.data.repository.RoomCatalogRepository
import dev.handheld.launcher.core.data.repository.RoomFavoriteRepository
import dev.handheld.launcher.core.data.repository.RoomItemOverrideRepository
import dev.handheld.launcher.core.data.repository.RoomSuccessfulOpenRepository
import dev.handheld.launcher.core.domain.repository.CatalogRepository
import dev.handheld.launcher.core.domain.repository.ControllerPreferenceRepository
import dev.handheld.launcher.core.domain.repository.DisplayPreferenceRepository
import dev.handheld.launcher.core.domain.repository.FavoriteRepository
import dev.handheld.launcher.core.domain.repository.ItemOverrideRepository
import dev.handheld.launcher.core.domain.repository.LaunchDispatcher
import dev.handheld.launcher.core.domain.repository.NavigationSnapshotRepository
import dev.handheld.launcher.core.domain.repository.SuccessfulOpenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import dev.handheld.launcher.core.data.metadata.*
import dev.handheld.launcher.artwork.ArtworkWorker
import dev.handheld.launcher.ui.artwork.enriched.EnrichedArtworkLoader
import java.io.File

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
    val displayPreferenceRepository: DisplayPreferenceRepository by lazy {
        DataStoreDisplayPreferenceRepository(preferences)
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
        val android = AndroidComponentLaunchDispatcher(applicationContext)
        object : LaunchDispatcher {
            override suspend fun dispatch(request: LaunchRequest): LaunchAcknowledgement {
                return when(request.target) {
                    is LaunchTarget.ExternalContent -> romController.dispatch(request)
                    else -> android.dispatch(request)
                }
            }
        }
    }
    val romRepository by lazy { RoomRomLibraryRepository(database) }
    val sharedStoragePaths by lazy { SharedStoragePaths(applicationContext) }
    val sharedRomDiscovery by lazy { AndroidSharedRomDiscovery(romRepository,sharedStoragePaths) }
    val romSourceAccess by lazy { RoutingRomSourceAccess(SafRomSourceAccess(applicationContext),SharedStorageRomSourceAccess(sharedStoragePaths)) }
    val romScanner by lazy { RomScanCoordinator(romRepository,romSourceAccess,applicationScope,sharedRomDiscovery,
        metadataIdentifier = EsDeRomMetadataIdentifier(sharedStoragePaths)) }
    val romCache by lazy { PreparedRomCache(applicationContext) }
    val romController by lazy { RomFeatureController(romRepository,romSourceAccess,romScanner,
        AndroidEmulatorResolver(applicationContext),AndroidRomLauncher(applicationContext),romCache,applicationScope,
        sharedStoragePaths,sharedRomDiscovery, runningApps::recordEmulator) }
    val runningApps by lazy { dev.handheld.launcher.runtime.RunningAppMonitor(applicationContext,
        catalogRepository, romRepository, applicationScope) }
    val launchCoordinator by lazy {
        LaunchCoordinator(catalogRepository, navigationSnapshotRepository, launchDispatcher,
            successfulOpenRepository, applicationScope)
    }
    val homeRoleRequests by lazy { HomeRoleRequestCoordinator(activityRequestPort) }
    val iconLoader by lazy { AndroidIconLoader(applicationContext) }
    private val artworkDatabase by lazy { ArtworkDatabase.open(applicationContext) }
    val artworkRepository by lazy {
        val root = File(applicationContext.cacheDir, "artwork")
        ArtworkRepository(artworkDatabase, romRepository, EsDeArtworkResolver(sharedStoragePaths), File(root, "images"),
            LibretroArtworkProvider(File(root, "indexes"))) { ArtworkWorker.enqueue(applicationContext) }
    }
    val enrichedArtworkLoader by lazy { EnrichedArtworkLoader(applicationContext, artworkRepository) }
    fun startArtwork() { artworkRepository.start(applicationScope, itemOverrideRepository) }
    val systemActions by lazy { SystemActionRegistry(applicationContext) }
    val deviceStatus by lazy { AndroidDeviceStatusSource(applicationContext) }

    fun launcherViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer { LauncherAppViewModel(controllerPreferenceRepository, createSavedStateHandle(), displayPreferenceRepository) }
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
