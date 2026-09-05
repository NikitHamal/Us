package com.us.copilot.domain.usecase

import com.us.copilot.ai.LlmRouter
import com.us.copilot.core.model.Memory
import com.us.copilot.core.model.MemoryFilter
import com.us.copilot.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveMemoriesUseCase @Inject constructor(
    private val repository: MemoryRepository,
) {
    operator fun invoke(filter: MemoryFilter): Flow<List<Memory>> = repository.observe(filter)
}

class ObserveRecentMemoriesUseCase @Inject constructor(
    private val repository: MemoryRepository,
) {
    operator fun invoke(limit: Int = 5): Flow<List<Memory>> = repository.observeRecent(limit)
}

class ObserveUnresolvedUseCase @Inject constructor(
    private val repository: MemoryRepository,
) {
    operator fun invoke(): Flow<List<Memory>> = repository.observeUnresolved()
}

/** Saves a memory and attaches an embedding so semantic search works later. */
class SaveMemoryUseCase @Inject constructor(
    private val repository: MemoryRepository,
    private val router: LlmRouter,
) {
    suspend operator fun invoke(memory: Memory): Long {
        val embedding = memory.embedding ?: router.embed(memory.text).valueOrNull
        return repository.add(memory.copy(embedding = embedding))
    }
}

class UpdateMemoryUseCase @Inject constructor(
    private val repository: MemoryRepository,
    private val router: LlmRouter,
) {
    suspend operator fun invoke(memory: Memory) {
        val embedding = router.embed(memory.text).valueOrNull ?: memory.embedding
        repository.update(memory.copy(embedding = embedding))
    }
}

class ToggleResolvedUseCase @Inject constructor(
    private val repository: MemoryRepository,
) {
    suspend operator fun invoke(id: Long, resolved: Boolean) = repository.setResolved(id, resolved)
}

class DeleteMemoryUseCase @Inject constructor(
    private val repository: MemoryRepository,
) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}

class SearchMemoriesUseCase @Inject constructor(
    private val repository: MemoryRepository,
    private val router: LlmRouter,
) {
    suspend operator fun invoke(query: String, limit: Int = 20): List<Memory> {
        if (query.isBlank()) return emptyList()
        val embedding = router.embed(query).valueOrNull ?: return emptyList()
        return repository.semanticSearch(embedding, limit)
    }
}

class ObserveTagsUseCase @Inject constructor(
    private val repository: MemoryRepository,
) {
    operator fun invoke(): Flow<List<String>> = repository.observeTags()
}
