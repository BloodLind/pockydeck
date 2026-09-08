package dev.handheld.launcher.core.data.rom

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.handheld.launcher.core.data.local.LauncherDatabase
import dev.handheld.launcher.core.data.local.LauncherMigrations
import dev.handheld.launcher.core.data.repository.*
import dev.handheld.launcher.core.data.rom.repository.RoomRomLibraryRepository
import dev.handheld.launcher.core.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RomMigrationInstrumentedTest {
    private val databaseName = "rom-migration-${System.nanoTime()}.db"

    @get:Rule val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), LauncherDatabase::class.java,
    )

    @After fun tearDown() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
    }

    @Test fun versionTwoToThreePreservesCatalogReferencesOperationsAndNextRecencyOrder() = runBlocking {
        val appId = ItemId("android:example.alpha/example.alpha.Main")
        val romId = ItemId("rom:legacy")
        migrationHelper.createDatabase(databaseName, 2).apply {
            execSQL("INSERT INTO catalog_items VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf(appId.value, "android_app", "Alpha", "other", "available", null))
            execSQL("INSERT INTO catalog_items VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf(romId.value, "rom_game", "Legacy game", "game", "unavailable", "source_unavailable"))
            execSQL("INSERT INTO catalog_provenance VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf(appId.value, "android_discovery", null, "example.alpha", "example.alpha.Main", null))
            execSQL("INSERT INTO catalog_provenance VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf(romId.value, "user_source", "legacy-root", null, null, null))
            execSQL("INSERT INTO catalog_item_actions VALUES (?, ?)", arrayOf(appId.value, "open"))
            execSQL("INSERT INTO catalog_item_actions VALUES (?, ?)", arrayOf(romId.value, "view_details"))
            execSQL("INSERT INTO inventory_status VALUES (1, ?, ?, NULL, NULL)", arrayOf("complete", "current_user_android"))
            execSQL("INSERT INTO favorite_references VALUES (?)", arrayOf(appId.value))
            execSQL("INSERT INTO favorite_references VALUES (?)", arrayOf(romId.value))
            execSQL("INSERT INTO item_overrides VALUES (?, ?, ?)", arrayOf(appId.value, "game", "user-art:alpha"))
            execSQL("INSERT INTO item_overrides VALUES (?, ?, ?)", arrayOf(romId.value, null, "user-art:legacy"))
            execSQL("INSERT INTO successful_open_history VALUES (?, ?)", arrayOf<Any>(appId.value, 7L))
            execSQL("INSERT INTO successful_open_history VALUES (?, ?)", arrayOf<Any>(romId.value, 11L))
            execSQL("INSERT INTO successful_open_operations VALUES (?, ?, ?)", arrayOf<Any>("old-ack", appId.value, 7L))
            execSQL("INSERT INTO successful_open_order_state VALUES (1, 11)")
            close()
        }

        migrationHelper.runMigrationsAndValidate(databaseName, 3, true, LauncherMigrations.migration2To3).use { migrated ->
            migrated.query("SELECT platform_id, rom_format FROM catalog_items").use { cursor ->
                assertEquals(2, cursor.count)
                while (cursor.moveToNext()) { assertTrue(cursor.isNull(0)); assertTrue(cursor.isNull(1)) }
            }
            listOf("rom_sources", "rom_documents", "rom_preferences").forEach { table ->
                migrated.query("SELECT COUNT(*) FROM $table").use { cursor ->
                    assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0))
                }
            }
        }

        val database = LauncherDatabase.open(ApplicationProvider.getApplicationContext(), databaseName)
        try {
            val catalog = RoomCatalogRepository(database)
            val snapshot = catalog.snapshot.first()
            assertEquals(setOf(appId, romId), snapshot.items.map { it.id }.toSet())
            assertEquals(listOf(appId), snapshot.activeItems.map { it.id })
            assertEquals(InventoryStatus.Complete(InventoryScope.CurrentUserAndroid), snapshot.inventoryStatus)
            val retainedRom = catalog.findItem(romId) as LibraryItem.RomGame
            assertEquals(CatalogSourceId("legacy-root"), retainedRom.sourceId)
            assertNull(retainedRom.platformId)
            assertNull(retainedRom.format)
            assertEquals(setOf(SupportedItemAction.VIEW_DETAILS), retainedRom.supportedActions)
            assertEquals(setOf(appId, romId), RoomFavoriteRepository(database).favoriteItemIds.first())
            val overrides = RoomItemOverrideRepository(database).overridesByItemId.first()
            assertEquals(UserItemOverrides(LibraryCategory.GAME, UserArtworkReference("user-art:alpha")), overrides[appId])
            assertEquals(UserArtworkReference("user-art:legacy"), overrides[romId]?.artworkReference)
            val recent = RoomSuccessfulOpenRepository(database)
            assertEquals(SuccessfulOpenWriteResult.AlreadyRecorded, recent.recordOnce(SuccessfulOpenCandidate(LaunchOperationId("old-ack"), appId)))
            val next = recent.recordOnce(SuccessfulOpenCandidate(LaunchOperationId("new-ack"), appId))
            assertEquals(12L, (next as SuccessfulOpenWriteResult.Recorded).record.openOrder)
            assertEquals(11L, recent.records.first().single { it.itemId == romId }.openOrder)
            assertEquals(RoomRomLibraryRepository.DEFAULT_CACHE_LIMIT, RoomRomLibraryRepository(database).cacheLimitBytes.first())
        } finally { database.close() }
    }
}
