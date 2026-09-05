package com.us.copilot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.us.copilot.data.local.entity.AnalysisEntity
import com.us.copilot.data.local.entity.CheckInEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {

    @Query("SELECT * FROM check_ins ORDER BY epochDay DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins WHERE epochDay = :epochDay LIMIT 1")
    fun observeDay(epochDay: Long): Flow<CheckInEntity?>

    @Query("SELECT * FROM check_ins WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay ASC")
    suspend fun range(from: Long, to: Long): List<CheckInEntity>

    @Query("SELECT epochDay FROM check_ins ORDER BY epochDay DESC")
    suspend fun allDays(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CheckInEntity)

    @Query("SELECT id FROM check_ins WHERE epochDay = :epochDay LIMIT 1")
    suspend fun idForDay(epochDay: Long): Long?

    @Query("DELETE FROM check_ins")
    suspend fun deleteAll()
}

@Dao
interface AnalysisDao {

    @Query("SELECT * FROM analyses WHERE inputHash = :hash LIMIT 1")
    suspend fun byHash(hash: String): AnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AnalysisEntity)

    @Query("DELETE FROM analyses WHERE createdAt < :before")
    suspend fun pruneOlderThan(before: Long)

    @Query("DELETE FROM analyses")
    suspend fun deleteAll()
}
