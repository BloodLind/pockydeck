package dev.handheld.launcher.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    ],
    version = 2,
    exportSchema = true,
)
abstract class LauncherDatabase : RoomDatabase() {
    internal abstract fun catalogDao(): CatalogDao
    internal abstract fun catalogReferenceDao(): CatalogReferenceDao
    internal abstract fun successfulOpenDao(): SuccessfulOpenDao

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

    val all: Array<Migration> = arrayOf(migration1To2)
}
