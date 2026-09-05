package com.us.copilot.pattern

import com.us.copilot.ai.offline.HorsemanDetector
import com.us.copilot.core.model.CheckIn
import com.us.copilot.core.model.HorsemanCount
import com.us.copilot.core.model.InsightSummary
import com.us.copilot.core.model.Memory
import com.us.copilot.core.model.Profile
import com.us.copilot.core.model.TriggerCount
import com.us.copilot.core.util.TimeUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic, explainable analytics over the user's own logged moments.
 * Runs entirely on device and never needs a model.
 */
@Singleton
class PatternEngine @Inject constructor() {

    fun summarise(
        memories: List<Memory>,
        checkIns: List<CheckIn>,
        partner: Profile?,
        now: Long = System.currentTimeMillis(),
    ): InsightSummary {
        if (memories.isEmpty() && checkIns.isEmpty()) return InsightSummary()

        val window = TimeUtils.DAY_MS * 30
        val last30 = memories.filter { it.timestamp >= now - window }
        val prev30 = memories.filter { it.timestamp in (now - window * 2) until (now - window) }

        val positives = memories.count { !it.emotion.isNegative }
        val negatives = memories.count { it.emotion.isNegative }

        return InsightSummary(
            totalMemories = memories.size,
            unresolvedCount = memories.count { it.isUnresolved },
            conflictsLast30Days = last30.count { HorsemanDetector.isConflict(it.text) },
            conflictsPrevious30Days = prev30.count { HorsemanDetector.isConflict(it.text) },
            repairAttempts = memories.count { HorsemanDetector.isRepairAttempt(it.text) },
            positiveToNegativeRatio = if (negatives == 0) positives.toFloat() else positives / negatives.toFloat(),
            topTriggers = triggers(memories, partner),
            horsemen = horsemen(memories),
            connectionTrend = checkIns.sortedBy { it.epochDay }.takeLast(14).map { it.connection.toFloat() },
            streakDays = TimeUtils.streak(
                checkIns.map { it.epochDay }.sortedDescending(),
                TimeUtils.epochDay(now),
            ),
        )
    }

    /** Trigger frequency, matched against the partner profile's declared triggers plus tags. */
    fun triggers(memories: List<Memory>, partner: Profile?): List<TriggerCount> {
        val declared = partner?.triggers.orEmpty()
        val counts = mutableMapOf<String, MutableList<Long>>()

        memories.forEach { memory ->
            val lower = memory.text.lowercase()
            declared.forEach { trigger ->
                val keyword = trigger.lowercase().split(" ").maxByOrNull { it.length }.orEmpty()
                if (keyword.length >= 4 && lower.contains(keyword)) {
                    counts.getOrPut(trigger) { mutableListOf() }.add(memory.timestamp)
                }
            }
            memory.tags.forEach { tag ->
                if (memory.emotion.isNegative) counts.getOrPut(tag) { mutableListOf() }.add(memory.timestamp)
            }
        }

        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, List<Long>>> { it.value.size }.thenByDescending { it.value.max() })
            .take(5)
            .map { TriggerCount(it.key, it.value.size, it.value.max()) }
    }

    fun horsemen(memories: List<Memory>): List<HorsemanCount> {
        val grouped = memories.flatMap { memory ->
            HorsemanDetector.detect(memory.text).map { it.horseman to memory.text }
        }.groupBy({ it.first }, { it.second })

        return grouped.entries
            .sortedByDescending { it.value.size }
            .map { (horseman, examples) ->
                HorsemanCount(horseman, examples.size, examples.last().take(120))
            }
    }

    /** Conflict cadence: average days between conflict moments, and the busiest weekday. */
    fun cadence(memories: List<Memory>): Cadence {
        val conflicts = memories.filter { HorsemanDetector.isConflict(it.text) }.sortedBy { it.timestamp }
        if (conflicts.size < 2) return Cadence(0f, null, conflicts.size)

        val gaps = conflicts.zipWithNext { a, b -> (b.timestamp - a.timestamp).toFloat() / TimeUtils.DAY_MS }
        val busiestDay = conflicts
            .groupBy { java.time.Instant.ofEpochMilli(it.timestamp).atZone(java.time.ZoneId.systemDefault()).dayOfWeek }
            .maxByOrNull { it.value.size }?.key?.name?.lowercase()?.replaceFirstChar { it.uppercase() }

        return Cadence(gaps.average().toFloat(), busiestDay, conflicts.size)
    }

    data class Cadence(val averageDaysBetween: Float, val busiestWeekday: String?, val conflictCount: Int) {
        val hasSignal: Boolean get() = conflictCount >= 2
    }
}
