package dev.handheld.launcher.core.data.repository

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.handheld.launcher.core.data.local.LauncherDatabase
import dev.handheld.launcher.core.data.local.LauncherMigrations
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LaunchOperationId
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.SuccessfulOpenCandidate
import dev.handheld.launcher.core.domain.model.UserArtworkReference
import dev.handheld.launcher.core.domain.model.UserItemOverrides
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherDatabaseSchemaInstrumentedTest {
    private val databaseName = "us018-version-two.db"

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LauncherDatabase::class.java,
    )

    @After
    fun tearDown() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
    }

    @Test
    fun migrationOneToTwoRetainsAllRowsAndSeedsCounterFromHistoryMaximum() = runBlocking {
        val versionOne = migrationHelper.createDatabase(databaseName, 1)
        val alphaId = "android:example.alpha/example.alpha.Main"
        versionOne.execSQL(
            "INSERT INTO catalog_items VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf(alphaId, "android_app", "Alpha", "other", "available", null),
        )
        versionOne.execSQL(
            "INSERT INTO catalog_provenance VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf(alphaId, "android_discovery", null, "example.alpha", "example.alpha.Main", null),
        )
        versionOne.execSQL(
            "INSERT INTO catalog_item_actions VALUES (?, ?)",
            arrayOf(alphaId, "open"),
        )
        versionOne.execSQL(
            "INSERT INTO inventory_status VALUES (1, ?, ?, NULL, NULL)",
            arrayOf("complete", "current_user_android"),
        )
        versionOne.execSQL("INSERT INTO favorite_references VALUES (?)", arrayOf(alphaId))
        versionOne.execSQL(
            "INSERT INTO item_overrides VALUES (?, ?, ?)",
            arrayOf(alphaId, "game", "user-art:alpha"),
        )
        versionOne.execSQL(
            "INSERT INTO successful_open_history VALUES (?, ?)",
            arrayOf<Any?>(alphaId, 7L),
        )
        versionOne.close()

        val versionTwo = migrationHelper.runMigrationsAndValidate(
            databaseName,
            2,
            true,
            LauncherMigrations.migration1To2,
        )
        val tableNames = linkedSetOf<String>()
        versionTwo.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) tableNames += cursor.getString(nameIndex)
        }
        versionTwo.query(
            "SELECT last_open_order FROM successful_open_order_state WHERE singleton_id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7L, cursor.getLong(0))
        }
        versionTwo.close()

        val expectedTables = setOf(
            "catalog_items",
            "catalog_provenance",
            "catalog_item_actions",
            "inventory_status",
            "favorite_references",
            "item_overrides",
            "successful_open_history",
            "successful_open_operations",
            "successful_open_order_state",
        )
        assertTrue(tableNames.containsAll(expectedTables))
        assertFalse(tableNames.any { "rom" in it.lowercase() || "provider" in it.lowercase() })

        val productionDatabase = LauncherDatabase.open(
            ApplicationProvider.getApplicationContext(),
            databaseName,
        )
        try {
            val snapshot = RoomCatalogRepository(productionDatabase).snapshot.first()
            assertEquals(listOf(ItemId(alphaId)), snapshot.items.map { it.id })
            assertEquals(setOf(ItemId(alphaId)), RoomFavoriteRepository(productionDatabase).favoriteItemIds.first())
            assertEquals(
                UserItemOverrides(
                    category = LibraryCategory.GAME,
                    artworkReference = UserArtworkReference("user-art:alpha"),
                ),
                RoomItemOverrideRepository(productionDatabase).overridesByItemId.first()[ItemId(alphaId)],
            )
            assertEquals(7L, RoomSuccessfulOpenRepository(productionDatabase).records.first().single().openOrder)

            val next = RoomSuccessfulOpenRepository(productionDatabase).recordOnce(
                SuccessfulOpenCandidate(
                    LaunchOperationId("post-migration-open"),
                    ItemId("android:example.beta/example.beta.Main"),
                ),
            )
            assertEquals(8L, (next as dev.handheld.launcher.core.domain.model.SuccessfulOpenWriteResult.Recorded).record.openOrder)
        } finally {
            productionDatabase.close()
        }
    }
}
