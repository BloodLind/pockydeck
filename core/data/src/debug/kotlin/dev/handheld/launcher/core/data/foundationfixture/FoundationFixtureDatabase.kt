package dev.handheld.launcher.core.data.foundationfixture

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "foundation_fixture")
internal data class FoundationFixtureEntity(
    @PrimaryKey val id: String,
    val note: String,
)

@Dao
internal interface FoundationFixtureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FoundationFixtureEntity)

    @Query("SELECT COUNT(*) FROM foundation_fixture")
    suspend fun count(): Int
}

@Database(
    entities = [FoundationFixtureEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class FoundationFixtureDatabase : RoomDatabase() {
    abstract fun fixtureDao(): FoundationFixtureDao
}
