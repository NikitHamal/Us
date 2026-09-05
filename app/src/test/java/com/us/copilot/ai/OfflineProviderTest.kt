package com.us.copilot.ai

import com.us.copilot.ai.model.ProfileContext
import com.us.copilot.ai.model.RiskLevel
import com.us.copilot.ai.model.ToneRequest
import com.us.copilot.ai.offline.CactModelLoader
import com.us.copilot.ai.offline.HorsemanDetector
import com.us.copilot.ai.offline.Lexicon
import com.us.copilot.ai.offline.OfflineProvider
import com.us.copilot.core.model.Horseman
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfflineProviderTest {

    private lateinit var provider: OfflineProvider

    @Before
    fun setUp() {
        val loader = mockk<CactModelLoader>()
        coEvery { loader.confidenceBonus() } returns 0f
        coEvery { loader.isReady() } returns false
        provider = OfflineProvider(loader)
    }

    @Test
    fun `warm message is low risk`() = runTest {
        val result = provider.analyzeTone(
            ToneRequest(text = "Thank you for today, I really appreciate how patient you were."),
        )
        val tone = result.valueOrNull
        assertNotNull(tone)
        assertEquals(RiskLevel.LOW, tone!!.riskLevel)
        assertTrue(tone.sentiment > 0f)
    }

    @Test
    fun `contempt is flagged as high risk`() = runTest {
        val tone = provider.analyzeTone(
            ToneRequest(text = "That is pathetic, grow up. You always do this."),
        ).valueOrNull
        assertNotNull(tone)
        assertEquals(RiskLevel.HIGH, tone!!.riskLevel)
        assertTrue(tone.detectedHorsemen.any { it.horseman == Horseman.CONTEMPT })
    }

    @Test
    fun `partner triggers raise the risk`() = runTest {
        val tone = provider.analyzeTone(
            ToneRequest(
                text = "I am going to raise my voice about this if it happens again",
                partner = ProfileContext(triggers = listOf("raise my voice")),
            ),
        ).valueOrNull
        assertNotNull(tone)
        assertTrue(tone!!.triggerHits.isNotEmpty())
        assertTrue(tone.riskLevel != RiskLevel.LOW)
    }

    @Test
    fun `rephrase always returns three voices with an nvc skeleton`() = runTest {
        val set = provider.rephrase(
            com.us.copilot.ai.model.RephraseRequest(text = "You never listen to me!"),
        ).valueOrNull
        assertNotNull(set)
        assertEquals(3, set!!.options.size)
        assertNotNull(set.nvc)
        // The blaming phrase must be gone from every rewrite.
        assertTrue(set.options.none { it.text.lowercase().contains("you never listen") })
    }

    @Test
    fun `embeddings are normalised and comparable`() = runTest {
        val a = provider.embed("we argued about money again last night").valueOrNull!!
        val b = provider.embed("another argument about money last night").valueOrNull!!
        val c = provider.embed("she loved the flowers I brought home").valueOrNull!!
        val similar = com.us.copilot.core.util.TextUtils.cosineSimilarity(a, b)
        val different = com.us.copilot.core.util.TextUtils.cosineSimilarity(a, c)
        assertTrue("similar pair should score higher", similar > different)
    }

    @Test
    fun `repair attempts and conflict markers are detected`() {
        assertTrue(HorsemanDetector.isRepairAttempt("I'm sorry, that came out wrong"))
        assertTrue(HorsemanDetector.isConflict("whatever, forget it"))
        assertTrue(Lexicon.sentimentScore("I hate this, you are so selfish") < 0f)
    }
}
