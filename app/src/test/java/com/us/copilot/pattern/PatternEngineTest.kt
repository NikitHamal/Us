package com.us.copilot.pattern

import com.us.copilot.core.model.CheckIn
import com.us.copilot.core.model.Emotion
import com.us.copilot.core.model.Horseman
import com.us.copilot.core.model.Memory
import com.us.copilot.core.model.Profile
import com.us.copilot.core.model.ProfileOwner
import com.us.copilot.core.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternEngineTest {

    private val engine = PatternEngine()
    private val now = 1_760_000_000_000L
    private fun daysAgo(days: Int) = now - TimeUtils.DAY_MS * days

    private fun memory(text: String, days: Int, emotion: Emotion = Emotion.NEUTRAL, unresolved: Boolean = false) =
        Memory(
            id = days.toLong() + text.hashCode(),
            text = text,
            emotion = emotion,
            timestamp = daysAgo(days),
            isUnresolved = unresolved,
        )

    @Test
    fun `empty input produces an empty summary`() {
        val summary = engine.summarise(emptyList(), emptyList(), null, now)
        assertFalse(summary.hasData)
    }

    @Test
    fun `counts conflicts in the correct thirty day windows`() {
        val memories = listOf(
            memory("you always do this", 5),
            memory("whatever, forget it", 12),
            memory("you never listen", 40),
        )
        val summary = engine.summarise(memories, emptyList(), null, now)
        assertEquals(2, summary.conflictsLast30Days)
        assertEquals(1, summary.conflictsPrevious30Days)
        assertEquals(1, summary.conflictDelta)
    }

    @Test
    fun `repair attempts are counted`() {
        val memories = listOf(
            memory("I'm sorry, that came out wrong", 2),
            memory("you're right, my bad", 3),
            memory("we went for a walk", 4),
        )
        val summary = engine.summarise(memories, emptyList(), null, now)
        assertEquals(2, summary.repairAttempts)
    }

    @Test
    fun `magic ratio is computed from emotion valence`() {
        val positives = List(10) { memory("good day $it", it, Emotion.JOY) }
        val negatives = listOf(memory("bad day", 11, Emotion.ANGER))
        val summary = engine.summarise(positives + negatives, emptyList(), null, now)
        assertEquals(10f, summary.positiveToNegativeRatio, 0.01f)
        assertTrue(summary.meetsMagicRatio)
    }

    @Test
    fun `horsemen are grouped and ranked`() {
        val memories = listOf(
            memory("you always ignore me", 1),
            memory("you never help", 2),
            memory("that's pathetic", 3),
        )
        val horsemen = engine.horsemen(memories)
        assertEquals(Horseman.CRITICISM, horsemen.first().horseman)
        assertEquals(2, horsemen.first().count)
    }

    @Test
    fun `declared partner triggers are matched in memory text`() {
        val partner = Profile.empty(ProfileOwner.PARTNER).copy(
            triggers = listOf("Being interrupted", "Money talk"),
        )
        val memories = listOf(
            memory("she felt interrupted again", 1, Emotion.HURT),
            memory("another fight about money", 2, Emotion.ANGER),
            memory("interrupted her mid sentence", 3, Emotion.SHAME),
        )
        val triggers = engine.triggers(memories, partner)
        assertEquals("Being interrupted", triggers.first().trigger)
        assertEquals(2, triggers.first().count)
    }

    @Test
    fun `cadence needs at least two conflicts`() {
        assertFalse(engine.cadence(listOf(memory("whatever", 1))).hasSignal)
        val cadence = engine.cadence(
            listOf(memory("whatever", 1), memory("you always", 8), memory("forget it", 15)),
        )
        assertTrue(cadence.hasSignal)
        assertEquals(7f, cadence.averageDaysBetween, 0.5f)
    }

    @Test
    fun `check in streak feeds the summary`() {
        val today = TimeUtils.epochDay(now)
        val checkIns = (0..4).map { CheckIn(epochDay = today - it, connection = 4) }
        val summary = engine.summarise(listOf(memory("hi", 1)), checkIns, null, now)
        assertEquals(5, summary.streakDays)
        assertEquals(5, summary.connectionTrend.size)
    }
}
