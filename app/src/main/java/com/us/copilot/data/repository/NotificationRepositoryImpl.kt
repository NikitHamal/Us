package com.us.copilot.data.repository

import com.us.copilot.core.model.CapturedNotification
import com.us.copilot.data.local.dao.CapturedNotificationDao
import com.us.copilot.data.local.entity.CapturedNotificationEntity
import com.us.copilot.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val dao: CapturedNotificationDao,
) : NotificationRepository {

    override fun observeAll(): Flow<List<CapturedNotification>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeCount(): Flow<Int> = dao.observeCount()

    override fun observeSharedWithAi(): Flow<List<CapturedNotification>> =
        dao.observeSharedWithAi().map { list -> list.map { it.toDomain() } }

    override suspend fun contextForAi(limit: Int): List<CapturedNotification> =
        dao.sharedForContext(limit).map { it.toDomain() }

    override suspend fun capture(notification: CapturedNotification) {
        dao.insert(notification.toEntity())
        dao.trimTo(CapturedNotification.RETENTION_LIMIT)
    }

    override suspend fun setSharedWithAi(id: Long, shared: Boolean) =
        dao.setSharedWithAi(id, shared)

    override suspend fun stopSharingAll() = dao.clearAllSharing()

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun clearAll() = dao.clearAll()
}

private fun CapturedNotificationEntity.toDomain() = CapturedNotification(
    id = id,
    packageName = packageName,
    appLabel = appLabel,
    title = title,
    text = text,
    postedAt = postedAt,
    fingerprint = fingerprint,
    sharedWithAi = sharedWithAi,
    riskLevel = riskLevel,
)

private fun CapturedNotification.toEntity() = CapturedNotificationEntity(
    id = id,
    packageName = packageName,
    appLabel = appLabel,
    title = title,
    text = text,
    postedAt = postedAt,
    fingerprint = fingerprint,
    sharedWithAi = sharedWithAi,
    riskLevel = riskLevel,
)
