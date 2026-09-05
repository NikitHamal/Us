package com.us.copilot.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** All time maths in one place so tests can pin the clock. */
object TimeUtils {

    val DAY_MS: Long = TimeUnit.DAYS.toMillis(1)

    private val dayFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")
    private val shortFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun epochDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    fun startOfDayMillis(epochDay: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()

    fun formatDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(dayFormatter)

    fun formatShort(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(shortFormatter)

    fun formatTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(timeFormatter)

    /** "just now", "3h ago", "yesterday", "12 Aug 2026". */
    fun relative(millis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - millis
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "just now"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
            diff < DAY_MS -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
            diff < DAY_MS * 2 -> "yesterday"
            diff < DAY_MS * 7 -> "${TimeUnit.MILLISECONDS.toDays(diff)} days ago"
            else -> formatShort(millis)
        }
    }

    /** Consecutive-day streak from a descending list of epoch days. */
    fun streak(epochDaysDesc: List<Long>, today: Long): Int {
        if (epochDaysDesc.isEmpty()) return 0
        val unique = epochDaysDesc.distinct().sortedDescending()
        if (unique.first() != today && unique.first() != today - 1) return 0
        var streak = 1
        for (i in 1 until unique.size) {
            if (unique[i - 1] - unique[i] == 1L) streak++ else break
        }
        return streak
    }
}
