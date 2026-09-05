package com.us.copilot.domain.repository

import com.us.copilot.core.model.CheckIn
import com.us.copilot.core.model.Memory
import com.us.copilot.core.model.MemoryFilter
import com.us.copilot.core.model.Profile
import com.us.copilot.core.model.ProfileOwner
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    /** Active version of each profile. */
    fun observeProfile(owner: ProfileOwner): Flow<Profile?>
    fun observeAll(): Flow<List<Profile>>
    suspend fun get(owner: ProfileOwner): Profile?
    /** Saves a NEW version and deactivates the previous one. Returns the new row id. */
    suspend fun saveNewVersion(profile: Profile): Long
    fun observeHistory(owner: ProfileOwner): Flow<List<Profile>>
    suspend fun restoreVersion(id: Long)
    suspend fun deleteAll()
}

interface MemoryRepository {
    fun observe(filter: MemoryFilter): Flow<List<Memory>>
    fun observeRecent(limit: Int): Flow<List<Memory>>
    fun observeUnresolved(): Flow<List<Memory>>
    fun observeTags(): Flow<List<String>>
    suspend fun get(id: Long): Memory?
    suspend fun add(memory: Memory): Long
    suspend fun update(memory: Memory)
    suspend fun setResolved(id: Long, resolved: Boolean)
    suspend fun delete(id: Long)
    suspend fun since(timestamp: Long): List<Memory>
    suspend fun all(): List<Memory>
    /** Semantic search using stored embeddings; falls back to text match when absent. */
    suspend fun semanticSearch(queryEmbedding: FloatArray, limit: Int): List<Memory>
    suspend fun deleteAll()
}

interface CheckInRepository {
    fun observeRecent(limit: Int): Flow<List<CheckIn>>
    fun observeToday(epochDay: Long): Flow<CheckIn?>
    suspend fun upsert(checkIn: CheckIn)
    suspend fun range(fromEpochDay: Long, toEpochDay: Long): List<CheckIn>
    suspend fun allDays(): List<Long>
    suspend fun deleteAll()
}
