package com.us.copilot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.us.copilot.data.local.entity.CapturedNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedNotificationDao {

    @Query("SELECT * FROM captured_notifications ORDER BY postedAt DESC")
    fun observeAll(): Flow<List<CapturedNotificationEntity>>

    @Query("SELECT * FROM captured_notifications WHERE sharedWithAi = 1 ORDER BY postedAt DESC")
    fun observeSharedWithAi(): Flow<List<CapturedNotificationEntity>>

    @Query("SELECT * FROM captured_notifications WHERE sharedWithAi = 1 ORDER BY postedAt DESC LIMIT :limit")
    suspend fun sharedForContext(limit: Int): List<CapturedNotificationEntity>

    @Query("SELECT COUNT(*) FROM captured_notifications")
    fun observeCount(): Flow<Int>

    /** IGNORE on conflict: the unique fingerprint index makes re-posts a no-op. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: CapturedNotificationEntity): Long

    @Query("UPDATE captured_notifications SET sharedWithAi = :shared WHERE id = :id")
    suspend fun setSharedWithAi(id: Long, shared: Boolean)

    @Query("UPDATE captured_notifications SET sharedWithAi = 0")
    suspend fun clearAllSharing()

    @Query("DELETE FROM captured_notifications WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM captured_notifications")
    suspend fun clearAll()

    /** Retention trim, run after each insert so history cannot grow without bound. */
    @Query(
        """
        DELETE FROM captured_notifications
        WHERE id NOT IN (
            SELECT id FROM captured_notifications ORDER BY postedAt DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimTo(keep: Int)
}
