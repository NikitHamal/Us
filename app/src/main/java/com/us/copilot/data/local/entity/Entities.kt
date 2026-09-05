package com.us.copilot.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "profiles",
    indices = [Index(value = ["owner", "isActive"]), Index(value = ["owner", "version"])],
)
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val owner: String,
    val name: String,
    val attachmentStyle: String,
    val loveLanguages: String,
    val conflictStyle: String,
    val triggers: String,
    val soothers: String,
    val openness: Int,
    val conscientiousness: Int,
    val extraversion: Int,
    val agreeableness: Int,
    val neuroticism: Int,
    val stressPatterns: String,
    val commPreferences: String,
    val note: String,
    val version: Int,
    val isActive: Boolean,
    val updatedAt: Long,
)

@Entity(
    tableName = "memories",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["isUnresolved"]),
        Index(value = ["emotion"]),
    ],
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val text: String,
    val emotion: String,
    val intensity: Int,
    val timestamp: Long,
    val source: String,
    val speaker: String,
    val tags: String,
    val isUnresolved: Boolean,
    val resolvedAt: Long?,
    val embedding: String?,
    val appPackage: String?,
)

@Entity(tableName = "check_ins", indices = [Index(value = ["epochDay"], unique = true)])
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val epochDay: Long,
    val mood: Int,
    val energy: Int,
    val connection: Int,
    val note: String,
    val gratitude: String,
    val createdAt: Long,
)

@Entity(tableName = "analyses", indices = [Index(value = ["inputHash"], unique = true)])
data class AnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val memoryId: Long?,
    val inputHash: String,
    val toneJson: String,
    val rephraseJson: String?,
    val provider: String,
    val confidence: Float,
    val createdAt: Long,
)

/**
 * A notification captured from a watched app.
 *
 * Captured text is stored locally and encrypted at rest like everything else. Crucially,
 * [sharedWithAi] defaults to false: capturing a notification never means the AI has seen it.
 * The user promotes individual entries to AI context explicitly from the history screen.
 */
@Entity(
    tableName = "captured_notifications",
    indices = [
        Index(value = ["postedAt"]),
        Index(value = ["packageName"]),
        Index(value = ["fingerprint"], unique = true),
    ],
)
data class CapturedNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    /** SHA-256 of package + text, used to drop duplicate re-posts of the same notification. */
    val fingerprint: String,
    /** Set only when the user explicitly hands this entry to the AI as context. */
    val sharedWithAi: Boolean = false,
    /** Populated lazily if the user asks for an on-device tone read. */
    val riskLevel: String? = null,
)
