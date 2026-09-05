package com.us.copilot.core.model

/** One daily self check-in. `date` is an epoch-day value so a day has exactly one row. */
data class CheckIn(
    val id: Long = 0L,
    val epochDay: Long,
    val mood: Int = 3,
    val energy: Int = 3,
    val connection: Int = 3,
    val note: String = "",
    val gratitude: String = "",
    val createdAt: Long = 0L,
) {
    val average: Float get() = (mood + energy + connection) / 3f
}

/** Aggregated numbers shown on the insights dashboard. */
data class InsightSummary(
    val totalMemories: Int = 0,
    val unresolvedCount: Int = 0,
    val conflictsLast30Days: Int = 0,
    val conflictsPrevious30Days: Int = 0,
    val repairAttempts: Int = 0,
    val positiveToNegativeRatio: Float = 0f,
    val topTriggers: List<TriggerCount> = emptyList(),
    val horsemen: List<HorsemanCount> = emptyList(),
    val connectionTrend: List<Float> = emptyList(),
    val streakDays: Int = 0,
) {
    val hasData: Boolean get() = totalMemories > 0
    val conflictDelta: Int get() = conflictsLast30Days - conflictsPrevious30Days
    /** Gottman's magic ratio is 5:1 in stable relationships. */
    val meetsMagicRatio: Boolean get() = positiveToNegativeRatio >= 5f
}

data class TriggerCount(val trigger: String, val count: Int, val lastSeen: Long)

data class HorsemanCount(val horseman: Horseman, val count: Int, val lastExample: String)
