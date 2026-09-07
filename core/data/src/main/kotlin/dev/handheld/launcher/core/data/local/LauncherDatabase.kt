package dev.handheld.launcher.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        CatalogItemEntity::class,
        CatalogProvenanceEntity::class,
        CatalogItemActionEntity::class,
        InventoryStatusEntity::class,
        FavoriteReferenceEntity::class,
        ItemOverrideEntity::class,
        SuccessfulOpenReferenceEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LauncherDatabase : RoomDatabase() {
    internal abstract fun catalogDao(): CatalogDao
    internal abstract fun catalogReferenceDao(): CatalogReferenceDao

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

/** US-017 starts at version 1; later schema changes append reviewed explicit migrations here. */
internal object LauncherMigrations {
    val all: Array<Migration> = emptyArray()
}
