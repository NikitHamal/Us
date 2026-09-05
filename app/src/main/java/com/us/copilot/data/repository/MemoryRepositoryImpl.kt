package com.us.copilot.data.repository

import com.us.copilot.core.model.Memory
import com.us.copilot.core.model.MemoryFilter
import com.us.copilot.data.local.dao.MemoryDao
import com.us.copilot.data.local.db.Converters
import com.us.copilot.core.util.TextUtils
import com.us.copilot.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepositoryImpl @Inject constructor(
    private val dao: MemoryDao,
) : MemoryRepository {

    override fun observe(filter: MemoryFilter): Flow<List<Memory>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() }.applyFilter(filter) }

    override fun observeRecent(limit: Int): Flow<List<Memory>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeUnresolved(): Flow<List<Memory>> =
        dao.observeUnresolved().map { list -> list.map { it.toDomain() } }

    override fun observeTags(): Flow<List<String>> = dao.observeTagBlobs().map { blobs ->
        blobs.flatMap { Converters.decodeList(it) }.distinct().sorted()
    }

    override suspend fun get(id: Long): Memory? = dao.byId(id)?.toDomain()

    override suspend fun add(memory: Memory): Long = dao.insert(
        memory.copy(
            timestamp = memory.timestamp.takeIf { it > 0 } ?: System.currentTimeMillis(),
        ).toEntity(),
    )

    override suspend fun update(memory: Memory) = dao.update(memory.toEntity())

    override suspend fun setResolved(id: Long, resolved: Boolean) =
        dao.setResolved(id, !resolved, if (resolved) System.currentTimeMillis() else null)

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun since(timestamp: Long): List<Memory> =
        dao.since(timestamp).map { it.toDomain() }

    override suspend fun all(): List<Memory> = dao.all().map { it.toDomain() }

    override suspend fun semanticSearch(queryEmbedding: FloatArray, limit: Int): List<Memory> =
        dao.withEmbeddings()
            .map { it.toDomain() }
            .mapNotNull { memory ->
                memory.embedding?.let { memory to TextUtils.cosineSimilarity(queryEmbedding, it) }
            }
            .filter { it.second > MIN_SIMILARITY }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }

    override suspend fun deleteAll() = dao.deleteAll()

    private fun List<Memory>.applyFilter(filter: MemoryFilter): List<Memory> {
        if (!filter.isActive) return this
        val query = filter.query.trim().lowercase()
        return filter { memory ->
            (query.isBlank() || memory.text.lowercase().contains(query) ||
                memory.tags.any { it.lowercase().contains(query) }) &&
                (filter.emotions.isEmpty() || memory.emotion in filter.emotions) &&
                (filter.sources.isEmpty() || memory.source in filter.sources) &&
                (filter.speakers.isEmpty() || memory.speaker in filter.speakers) &&
                (!filter.onlyUnresolved || memory.isUnresolved) &&
                (filter.tag == null || filter.tag in memory.tags)
        }
    }

    private companion object { const val MIN_SIMILARITY = 0.15f }
}
