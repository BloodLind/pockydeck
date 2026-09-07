package dev.handheld.launcher.core.data.repository

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.handheld.launcher.core.data.local.LauncherDatabase
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
    private val databaseName = "us017-version-one.db"

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
    fun exportedVersionOneSchemaOpensWithProductionDatabaseWithoutDestructiveFallback() = runBlocking {
        val schemaDatabase = migrationHelper.createDatabase(databaseName, 1)
        val tableNames = linkedSetOf<String>()
        schemaDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) tableNames += cursor.getString(nameIndex)
        }
        schemaDatabase.close()

        val expectedTables = setOf(
            "catalog_items",
            "catalog_provenance",
            "catalog_item_actions",
            "inventory_status",
            "favorite_references",
            "item_overrides",
            "successful_open_history",
        )
        assertTrue(tableNames.containsAll(expectedTables))
        assertFalse(tableNames.any { "rom" in it.lowercase() || "provider" in it.lowercase() })

        val productionDatabase = LauncherDatabase.open(
            ApplicationProvider.getApplicationContext(),
            databaseName,
        )
        try {
            val snapshot = RoomCatalogRepository(productionDatabase).snapshot.first()
            assertTrue(snapshot.items.isEmpty())
            assertEquals(0, productionDatabase.catalogReferenceDao().readHistoryReferences().size)
        } finally {
            productionDatabase.close()
        }
    }
}
