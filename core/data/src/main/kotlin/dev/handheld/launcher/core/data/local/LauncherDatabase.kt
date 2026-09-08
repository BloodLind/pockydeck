package dev.handheld.launcher.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.handheld.launcher.core.data.rom.repository.*

@Database(
    entities = [
        CatalogItemEntity::class,
        CatalogProvenanceEntity::class,
        CatalogItemActionEntity::class,
        InventoryStatusEntity::class,
        FavoriteReferenceEntity::class,
        ItemOverrideEntity::class,
        SuccessfulOpenReferenceEntity::class,
        SuccessfulOpenOperationEntity::class,
        SuccessfulOpenOrderStateEntity::class,
        RomSourceEntity::class,
        RomDocumentEntity::class,
        RomPreferenceEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class LauncherDatabase : RoomDatabase() {
    internal abstract fun catalogDao(): CatalogDao
    internal abstract fun catalogReferenceDao(): CatalogReferenceDao
    internal abstract fun successfulOpenDao(): SuccessfulOpenDao
    internal abstract fun romDao(): RomDao

    companion object {
        const val DEFAULT_DATABASE_NAME = "handheld-launcher.db"

        /** Builds the production database with explicit migrations and no destructive fallback. */
        fun open(
            context: Context,
            databaseName: String = DEFAULT_DATABASE_NAME,
        ): LauncherDatabase = Room.databaseBuilder(
            context.applicationContext,
            LauncherDatabase::class.java,
            databaseName,
        ).addMigrations(*LauncherMigrations.all).build()
    }
}

/** Reviewed explicit migrations only; the production builder never enables destructive fallback. */
internal object LauncherMigrations {
    val migration1To2: Migration = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `successful_open_operations` (
                    `operation_id` TEXT NOT NULL,
                    `item_id` TEXT NOT NULL,
                    `open_order` INTEGER NOT NULL,
                    PRIMARY KEY(`operation_id`)
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS
                `index_successful_open_operations_open_order`
                ON `successful_open_operations` (`open_order`)
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `successful_open_order_state` (
                    `singleton_id` INTEGER NOT NULL,
                    `last_open_order` INTEGER NOT NULL,
                    PRIMARY KEY(`singleton_id`)
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT OR REPLACE INTO `successful_open_order_state`
                    (`singleton_id`, `last_open_order`)
                SELECT 1, COALESCE(MAX(`open_order`), 0)
                FROM `successful_open_history`
                """.trimIndent(),
            )
        }
    }

    val migration2To3: Migration = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE catalog_items ADD COLUMN platform_id TEXT")
            database.execSQL("ALTER TABLE catalog_items ADD COLUMN rom_format TEXT")
            database.execSQL("CREATE TABLE IF NOT EXISTS rom_sources (source_id TEXT NOT NULL PRIMARY KEY, tree_uri TEXT NOT NULL, root_document_id TEXT NOT NULL, name TEXT NOT NULL, enabled INTEGER NOT NULL, status TEXT NOT NULL, default_platform_id TEXT, last_scan_at INTEGER, error TEXT, revision INTEGER NOT NULL)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_rom_sources_tree_uri ON rom_sources(tree_uri)")
            database.execSQL("CREATE TABLE IF NOT EXISTS rom_documents (item_id TEXT NOT NULL PRIMARY KEY, source_id TEXT NOT NULL, document_id TEXT NOT NULL, document_uri TEXT NOT NULL, relative_path TEXT NOT NULL, title TEXT NOT NULL, platform_id TEXT, platform_override TEXT, format TEXT NOT NULL, present INTEGER NOT NULL, issue TEXT, requires_repair INTEGER NOT NULL, companions TEXT NOT NULL, size_bytes INTEGER, scan_token TEXT NOT NULL)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_rom_documents_source_id_document_id ON rom_documents(source_id, document_id)")
            database.execSQL("CREATE TABLE IF NOT EXISTS rom_preferences (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
        }
    }

    val all: Array<Migration> = arrayOf(migration1To2, migration2To3)
}
