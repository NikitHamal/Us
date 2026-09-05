package com.us.copilot.core

import com.us.copilot.core.util.TextUtils
import com.us.copilot.core.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeUtilsTest {

    @Test
    fun `streak counts consecutive days ending today`() {
        val today = 20_000L
        assertEquals(3, TimeUtils.streak(listOf(today, today - 1, today - 2), today))
    }

    @Test
    fun `streak tolerates yesterday as the latest entry`() {
        val today = 20_000L
        assertEquals(2, TimeUtils.streak(listOf(today - 1, today - 2), today))
    }

    @Test
    fun `streak breaks on a gap`() {
        val today = 20_000L
        assertEquals(1, TimeUtils.streak(listOf(today, today - 3), today))
    }

    @Test
    fun `streak is zero when nothing is recent`() {
        assertEquals(0, TimeUtils.streak(listOf(19_000L), 20_000L))
        assertEquals(0, TimeUtils.streak(emptyList(), 20_000L))
    }

    @Test
    fun `relative formatting covers the common ranges`() {
        val now = 1_760_000_000_000L
        assertEquals("just now", TimeUtils.relative(now - 10_000, now))
        assertEquals("5m ago", TimeUtils.relative(now - 5 * 60_000, now))
        assertEquals("yesterday", TimeUtils.relative(now - TimeUtils.DAY_MS - 1000, now))
    }
}

class TextUtilsTest {

    @Test
    fun `stop words are removed from content tokens`() {
        val tokens = TextUtils.contentTokens("I am so tired of the constant arguing")
        assertTrue(tokens.contains("tired"))
        assertTrue(tokens.contains("arguing"))
        assertTrue(!tokens.contains("the"))
    }

    @Test
    fun `shared text noise is stripped`() {
        val cleaned = TextUtils.cleanSharedText("Sent from Instagram\nI miss you\n")
        assertEquals("I miss you", cleaned)
    }

    @Test
    fun `cosine similarity is bounded and self similarity is one`() {
        val vector = floatArrayOf(0.5f, 0.5f, 0.7071f)
        assertEquals(1f, TextUtils.cosineSimilarity(vector, vector), 0.001f)
        assertEquals(0f, TextUtils.cosineSimilarity(vector, FloatArray(0)), 0.001f)
    }

    @Test
    fun `sha256 is stable`() {
        assertEquals(TextUtils.sha256("us"), TextUtils.sha256("us"))
        assertEquals(64, TextUtils.sha256("us").length)
    }
}
