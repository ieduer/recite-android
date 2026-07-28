package net.bdfz.recite.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "piece_progress")
data class PieceProgressEntity(
    @PrimaryKey val pieceId: String,
    val stage: Int = 0,
    val readPercent: Int = 0,
    val peeks: Int = 0,
    val quizBest: Int = 0,
    val dictBest: Int = 0,
    val voiceBest: Int = 0,
    val examBest: Int = 0,
    val clozeAttempts: Int = 0,
    val quizAttempts: Int = 0,
    val dictationAttempts: Int = 0,
    val examAttempts: Int = 0,
    val firstStartedAt: String = "",
    val lastActivityAt: String = "",
    val lastReviewedAt: String = "",
) {
    val completed: Boolean
        get() = stage >= 5

    val progressPercent: Int
        get() = if (completed) 100 else maxOf(stage * 20, if (readPercent > 0) maxOf(1, readPercent / 10) else 0)
}

@Entity(
    tableName = "sync_outbox",
    indices = [Index(value = ["eventId"], unique = true)],
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: String,
    val pieceId: String,
    val payloadJson: String,
    val createdAtMs: Long,
    val attempts: Int = 0,
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM piece_progress ORDER BY pieceId")
    fun observeAll(): Flow<List<PieceProgressEntity>>

    @Query("SELECT * FROM piece_progress WHERE pieceId = :pieceId")
    suspend fun get(pieceId: String): PieceProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: PieceProgressEntity)

    @Query("SELECT * FROM sync_outbox ORDER BY id LIMIT :limit")
    suspend fun pending(limit: Int = 40): List<SyncOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(item: SyncOutboxEntity)

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun deleteOutbox(id: Long)

    @Query("UPDATE sync_outbox SET attempts = attempts + 1 WHERE id = :id")
    suspend fun recordAttempt(id: Long)

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observePendingCount(): Flow<Int>
}

@Database(
    entities = [PieceProgressEntity::class, SyncOutboxEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ReciteDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
}
