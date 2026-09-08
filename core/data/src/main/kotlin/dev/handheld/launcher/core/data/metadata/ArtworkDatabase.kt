package dev.handheld.launcher.core.data.metadata

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Rebuildable enrichment lives separately from the catalog and user-owned records. */
@Entity(tableName = "artwork", indices = [Index(value = ["state", "nextAttemptAt", "priority"])])
data class ArtworkRecord(
    @PrimaryKey val itemId: String,
    val title: String,
    val platformId: String?,
    val state: String = QUEUED,
    val provider: String? = null,
    val fileReference: String? = null,
    val sourceReference: String? = null,
    val matchTitle: String? = null,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0,
    val priority: Long = 0,
    val lastAccessAt: Long = 0,
    val active: Boolean = true,
    val message: String? = null,
) {
    companion object {
        const val QUEUED = "queued"
        const val READY = "ready"
        const val RETRY = "retry"
        const val MISSING = "missing"
        const val UNSUPPORTED = "unsupported"
        const val FAILED = "failed"
    }
}

@Entity(tableName = "artwork_settings")
data class ArtworkSetting(@PrimaryKey val name: String, val value: String)

data class ArtworkCount(val state: String, val count: Int)

@Dao
interface ArtworkDao {
    @Query("SELECT * FROM artwork WHERE itemId = :id") fun observe(id: String): Flow<ArtworkRecord?>
    @Query("SELECT * FROM artwork WHERE itemId = :id") suspend fun find(id: String): ArtworkRecord?
    @Query("SELECT * FROM artwork") suspend fun all(): List<ArtworkRecord>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(records: List<ArtworkRecord>)
    @Upsert suspend fun put(record: ArtworkRecord)
    @Query("UPDATE artwork SET active = 0") suspend fun deactivateAll()
    @Query("UPDATE artwork SET active = 1 WHERE itemId IN (:ids)") suspend fun activate(ids: List<String>)
    @Query("UPDATE artwork SET priority = :time, lastAccessAt = :time WHERE itemId = :id")
    suspend fun prioritize(id: String, time: Long)
    @Query("SELECT * FROM artwork WHERE active = 1 AND state IN ('queued','retry') AND nextAttemptAt <= :now ORDER BY priority DESC, platformId, itemId LIMIT :limit")
    suspend fun pending(now: Long, limit: Int = 1): List<ArtworkRecord>
    @Query("SELECT COUNT(*) FROM artwork WHERE active = 1 AND state IN ('queued','retry')") suspend fun pendingCount(): Int
    @Query("SELECT MIN(nextAttemptAt) FROM artwork WHERE active = 1 AND state IN ('queued','retry')") suspend fun nextAttempt(): Long?
    @Query("SELECT state, COUNT(*) AS count FROM artwork WHERE active = 1 GROUP BY state") fun counts(): Flow<List<ArtworkCount>>
    @Query("SELECT * FROM artwork WHERE provider = 'Libretro' AND fileReference IS NOT NULL ORDER BY lastAccessAt ASC")
    suspend fun downloaded(): List<ArtworkRecord>
    @Query("UPDATE artwork SET state = 'queued', attempts = 0, nextAttemptAt = 0, message = NULL, provider = NULL, fileReference = NULL, sourceReference = NULL WHERE active = 1 AND state IN ('missing','failed','unsupported','retry')")
    suspend fun retryMissing()
    @Query("SELECT value FROM artwork_settings WHERE name = :name") suspend fun setting(name: String): String?
    @Query("SELECT value FROM artwork_settings WHERE name = :name") fun observeSetting(name: String): Flow<String?>
    @Upsert suspend fun putSetting(setting: ArtworkSetting)
}

@Database(entities = [ArtworkRecord::class, ArtworkSetting::class], version = 1, exportSchema = true)
abstract class ArtworkDatabase : RoomDatabase() {
    abstract fun artworkDao(): ArtworkDao
    companion object {
        fun open(context: Context) = Room.databaseBuilder(context.applicationContext, ArtworkDatabase::class.java, "artwork.db").build()
    }
}
