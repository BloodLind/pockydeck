package dev.handheld.launcher.core.data.repository

import android.content.Context
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.data.local.LauncherPreferenceKeys
import dev.handheld.launcher.core.data.local.LauncherPreferencesStore
import dev.handheld.launcher.core.data.local.LauncherDatabase
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogInventory
import dev.handheld.launcher.core.domain.model.ConfirmBackMapping
import dev.handheld.launcher.core.domain.model.ControllerFaceButton
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.DestinationSnapshot
import dev.handheld.launcher.core.domain.model.InventoryScope
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LaunchOperationId
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.PageStateKey
import dev.handheld.launcher.core.domain.model.SuccessfulOpenCandidate
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStorePreferencesInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: LauncherPreferencesStore
    private lateinit var controllerPreferences: DataStoreControllerPreferenceRepository
    private lateinit var navigationSnapshots: DataStoreNavigationSnapshotRepository
    private lateinit var fileName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fileName = "us019-${System.nanoTime()}.preferences_pb"
        context.dataStoreFile(fileName).delete()
        openStore()
    }

    @After
    fun tearDown() {
        runBlocking { store.close() }
        context.dataStoreFile(fileName).delete()
    }

    @Test
    fun absentMappingDefaultsAndSwappedMappingSurvivesReopen() {
        runBlocking {
            assertEquals(ConfirmBackMapping.Default, controllerPreferences.confirmBackMapping.first())

            val swapped = ConfirmBackMapping(
                confirm = ControllerFaceButton.B,
                back = ControllerFaceButton.A,
            )
            controllerPreferences.setConfirmBackMapping(swapped)
            reopenStore()

            assertEquals(swapped, controllerPreferences.confirmBackMapping.first())
        }
    }

    @Test
    fun allSnapshotFieldsRemainIndependentForEveryDestinationAfterReopen() {
        runBlocking {
            val expected = LauncherDestination.entries.associateWith { destination ->
                val index = destination.ordinal
                DestinationSnapshot(
                    destination = destination,
                    selectedItemId = ItemId("selected:${destination.persistedKey}"),
                    firstVisibleItemId = ItemId("anchor:${destination.persistedKey}"),
                    firstVisibleOffsetPx = 10 + index,
                    query = "query ${destination.persistedKey}",
                    filterKey = PageStateKey("filter:${destination.persistedKey}"),
                    sortKey = PageStateKey("sort:${destination.persistedKey}"),
                )
            }
            expected.values.forEach { navigationSnapshots.save(it) }

            reopenStore()

            expected.forEach { (destination, snapshot) ->
                assertEquals(snapshot, navigationSnapshots.observe(destination).first())
            }
        }
    }

    @Test
    fun malformedAndUnknownVersionFieldsRecoverLocallyWithoutClearingValidData() {
        runBlocking {
            val retained = DestinationSnapshot(
                destination = LauncherDestination.SETTINGS,
                selectedItemId = ItemId("settings:selected"),
                firstVisibleItemId = ItemId("settings:anchor"),
                firstVisibleOffsetPx = 14,
                query = "retained query",
                filterKey = PageStateKey("settings:filter"),
                sortKey = PageStateKey("settings:sort"),
            )
            val swapped = ConfirmBackMapping(ControllerFaceButton.B, ControllerFaceButton.A)
            controllerPreferences.setConfirmBackMapping(swapped)
            navigationSnapshots.save(retained)

            val home = LauncherPreferenceKeys.snapshot(LauncherDestination.HOME)
            val apps = LauncherPreferenceKeys.snapshot(LauncherDestination.APPS)
            val library = LauncherPreferenceKeys.snapshot(LauncherDestination.LIBRARY)
            store.dataStore.edit { preferences ->
                preferences[home.version] = 99
                preferences[home.query] = "obsolete"
                preferences[stringPreferencesKey("navigation.apps.version")] = "1"
                preferences[library.version] = 1
                preferences[intPreferencesKey("navigation.library.selected_item_id")] = 3
                preferences[library.firstVisibleItemId] = ""
                preferences[library.firstVisibleOffsetPx] = -40
                preferences[intPreferencesKey("navigation.library.query")] = 12
                preferences[library.filterKey] = ""
                preferences[library.sortKey] = "  "
                preferences[stringPreferencesKey("navigation.unknown.extra")] = "ignored"
            }

            assertEquals(swapped, controllerPreferences.confirmBackMapping.first())
            assertNull(navigationSnapshots.observe(LauncherDestination.HOME).first())
            assertNull(navigationSnapshots.observe(LauncherDestination.APPS).first())
            assertEquals(
                DestinationSnapshot(
                    destination = LauncherDestination.LIBRARY,
                ),
                navigationSnapshots.observe(LauncherDestination.LIBRARY).first(),
            )
            assertEquals(retained, navigationSnapshots.observe(LauncherDestination.SETTINGS).first())

            store.dataStore.edit { preferences ->
                preferences[intPreferencesKey("controller.confirm_button")] = 7
                preferences[LauncherPreferenceKeys.backButton] = "a"
            }
            assertEquals(ConfirmBackMapping.Default, controllerPreferences.confirmBackMapping.first())
            assertEquals(retained, navigationSnapshots.observe(LauncherDestination.SETTINGS).first())
        }
    }

    @Test
    fun clearingOneDestinationPreservesOtherSnapshotsAndControllerMapping() {
        runBlocking {
            val swapped = ConfirmBackMapping(ControllerFaceButton.B, ControllerFaceButton.A)
            val home = DestinationSnapshot(LauncherDestination.HOME, query = "home")
            val apps = DestinationSnapshot(LauncherDestination.APPS, query = "apps")
            controllerPreferences.setConfirmBackMapping(swapped)
            navigationSnapshots.save(home)
            navigationSnapshots.save(apps)

            navigationSnapshots.clear(LauncherDestination.HOME)
            reopenStore()

            assertNull(navigationSnapshots.observe(LauncherDestination.HOME).first())
            assertEquals(apps, navigationSnapshots.observe(LauncherDestination.APPS).first())
            assertEquals(swapped, controllerPreferences.confirmBackMapping.first())
        }
    }

    @Test
    fun concurrentIndependentDestinationWritesAllSurvive() {
        runBlocking {
            val expected = LauncherDestination.entries.map { destination ->
                DestinationSnapshot(
                    destination = destination,
                    selectedItemId = ItemId("concurrent:${destination.persistedKey}"),
                    firstVisibleOffsetPx = destination.ordinal,
                    query = destination.persistedKey,
                )
            }

            coroutineScope {
                expected.map { snapshot ->
                    async(Dispatchers.Default) { navigationSnapshots.save(snapshot) }
                }.awaitAll()
            }

            reopenStore()
            expected.forEach { snapshot ->
                assertEquals(snapshot, navigationSnapshots.observe(snapshot.destination).first())
            }
        }
    }

    @Test
    fun unreadablePreferencesResetOnlyDataStoreAndPreserveRoomData() {
        runBlocking {
            val databaseName = "us019-room-${System.nanoTime()}.db"
            context.deleteDatabase(databaseName)
            val database = LauncherDatabase.open(context, databaseName)
            try {
                val item = androidApp()
                val catalog = RoomCatalogRepository(database)
                val favorites = RoomFavoriteRepository(database)
                val recency = RoomSuccessfulOpenRepository(database)
                catalog.applyInventory(
                    CatalogInventory.Complete(InventoryScope.CurrentUserAndroid, listOf(item)),
                )
                favorites.setFavorite(item.id, true)
                recency.recordOnce(
                    SuccessfulOpenCandidate(LaunchOperationId("corruption-open"), item.id),
                )
                controllerPreferences.setConfirmBackMapping(
                    ConfirmBackMapping(ControllerFaceButton.B, ControllerFaceButton.A),
                )
                navigationSnapshots.save(
                    DestinationSnapshot(LauncherDestination.FAVORITES, query = "before corruption"),
                )

                store.close()
                withContext(Dispatchers.IO) {
                    FileOutputStream(context.dataStoreFile(fileName)).use { output ->
                        output.write(byteArrayOf(0x0A, 0x7F))
                    }
                }
                openStore()

                assertEquals(ConfirmBackMapping.Default, controllerPreferences.confirmBackMapping.first())
                assertNull(navigationSnapshots.observe(LauncherDestination.FAVORITES).first())
                assertEquals(listOf(item), catalog.snapshot.first().items)
                assertEquals(setOf(item.id), favorites.favoriteItemIds.first())
                assertEquals(item.id, recency.records.first().single().itemId)
            } finally {
                database.close()
                context.deleteDatabase(databaseName)
            }
        }
    }

    @Test
    fun debugFakesImplementThePublishedRepositoryContracts() {
        runBlocking {
            val mapping = ConfirmBackMapping(ControllerFaceButton.B, ControllerFaceButton.A)
            val snapshot = DestinationSnapshot(LauncherDestination.SEARCH, query = "fake")
            val fakeController = FakeControllerPreferenceRepository()
            val fakeNavigation = FakeNavigationSnapshotRepository()

            fakeController.setConfirmBackMapping(mapping)
            fakeNavigation.save(snapshot)
            assertEquals(mapping, fakeController.confirmBackMapping.first())
            assertEquals(snapshot, fakeNavigation.observe(LauncherDestination.SEARCH).first())

            fakeNavigation.clear(LauncherDestination.SEARCH)
            assertNull(fakeNavigation.observe(LauncherDestination.SEARCH).first())
        }
    }

    private fun openStore() {
        store = LauncherPreferencesStore.open(context, fileName)
        controllerPreferences = DataStoreControllerPreferenceRepository(store)
        navigationSnapshots = DataStoreNavigationSnapshotRepository(store)
    }

    private suspend fun reopenStore() {
        store.close()
        openStore()
    }

    private fun androidApp(): LibraryItem.AndroidApp = LibraryItem.AndroidApp(
        componentId = CurrentUserAndroidComponentId("example.preferences", "example.preferences.Main"),
        title = "Preferences boundary",
        category = LibraryCategory.OTHER,
        availability = Availability.Available,
        supportedActions = setOf(SupportedItemAction.OPEN),
    )
}
