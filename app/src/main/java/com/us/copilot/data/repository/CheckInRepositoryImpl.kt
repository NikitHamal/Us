package com.us.copilot.data.repository

import com.us.copilot.core.model.CheckIn
import com.us.copilot.data.local.dao.CheckInDao
import com.us.copilot.domain.repository.CheckInRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckInRepositoryImpl @Inject constructor(
    private val dao: CheckInDao,
) : CheckInRepository {

    override fun observeRecent(limit: Int): Flow<List<CheckIn>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeToday(epochDay: Long): Flow<CheckIn?> =
        dao.observeDay(epochDay).map { it?.toDomain() }

    override suspend fun upsert(checkIn: CheckIn) {
        val existingId = dao.idForDay(checkIn.epochDay) ?: 0L
        dao.upsert(
            checkIn.copy(
                createdAt = checkIn.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            ).toEntity(existingId),
        )
    }

    override suspend fun range(fromEpochDay: Long, toEpochDay: Long): List<CheckIn> =
        dao.range(fromEpochDay, toEpochDay).map { it.toDomain() }

    override suspend fun allDays(): List<Long> = dao.allDays()

    override suspend fun deleteAll() = dao.deleteAll()
}
