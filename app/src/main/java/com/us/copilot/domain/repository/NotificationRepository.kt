package com.us.copilot.domain.repository

import com.us.copilot.core.model.CapturedNotification
import kotlinx.coroutines.flow.Flow

/**
 * Storage for notifications captured from watched apps.
 *
 * The split between [observeAll] and [contextForAi] is the whole privacy model in miniature:
 * everything captured is visible to the user, but only entries they explicitly shared are ever
 * readable by the AI layer.
 */
interface NotificationRepository {

    fun observeAll(): Flow<List<CapturedNotification>>

    fun observeCount(): Flow<Int>

    fun observeSharedWithAi(): Flow<List<CapturedNotification>>

    /** Returns only user-shared entries. The AI layer must use this and never [observeAll]. */
    suspend fun contextForAi(limit: Int = 20): List<CapturedNotification>

    /** Inserts a capture, ignoring duplicates, then trims to the retention limit. */
    suspend fun capture(notification: CapturedNotification)

    suspend fun setSharedWithAi(id: Long, shared: Boolean)

    /** Revokes AI access from every entry at once, without deleting the history. */
    suspend fun stopSharingAll()

    suspend fun delete(id: Long)

    suspend fun clearAll()
}
