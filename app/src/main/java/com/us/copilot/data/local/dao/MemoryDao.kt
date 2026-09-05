package com.us.copilot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.us.copilot.data.local.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE isUnresolved = 1 ORDER BY timestamp ASC")
    fun observeUnresolved(): Flow<List<MemoryEntity>>

    @Query("SELECT tags FROM memories WHERE tags != ''")
    fun observeTagBlobs(): Flow<List<String>>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun byId(id: Long): MemoryEntity?

    @Query("SELECT * FROM memories WHERE timestamp >= :timestamp ORDER BY timestamp ASC")
    suspend fun since(timestamp: Long): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY timestamp ASC")
    suspend fun all(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE embedding IS NOT NULL")
    suspend fun withEmbeddings(): List<MemoryEntity>

    @Insert
    suspend fun insert(entity: MemoryEntity): Long

    @Update
    suspend fun update(entity: MemoryEntity)

    @Query("UPDATE memories SET isUnresolved = :unresolved, resolvedAt = :resolvedAt WHERE id = :id")
    suspend fun setResolved(id: Long, unresolved: Boolean, resolvedAt: Long?)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun count(): Int
}
