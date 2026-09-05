package com.us.copilot.ai

import com.us.copilot.ai.model.ToneAnalysis
import com.us.copilot.ai.model.ToneRequest
import com.us.copilot.core.model.Emotion
import com.us.copilot.core.model.ProviderId
import com.us.copilot.core.util.AppError
import com.us.copilot.core.util.Outcome
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies the explicit-selection-wins routing policy in [LlmRouter]. */
class LlmRouterTest {

    private val request = ToneRequest(text = "any message")

    private fun tone(confidence: Float, provider: ProviderId) = ToneAnalysis(
        sentiment = 0f,
        primaryEmotion = Emotion.NEUTRAL,
        harshnessScore = 10,
        summary = "",
        confidence = confidence,
        provider = provider,
    )

    private fun router(
        offlineConfidence: Float,
        cloudUsable: Boolean,
        cloudResult: Outcome<ToneAnalysis> = Outcome.Success(tone(0.95f, ProviderId.CLOUD)),
        nebiansUsable: Boolean = false,
        nebiansResult: Outcome<ToneAnalysis> = Outcome.Success(tone(0.9f, ProviderId.NEBIANS)),
    ): LlmRouter {
        val offline = mockk<LlmProvider>()
        val nebians = mockk<LlmProvider>()
        val cloud = mockk<LlmProvider>()
        coEvery { offline.analyzeTone(any()) } returns
            Outcome.Success(tone(offlineConfidence, ProviderId.OFFLINE))
        coEvery { nebians.analyzeTone(any()) } returns nebiansResult
        coEvery { cloud.analyzeTone(any()) } returns cloudResult
        val gate = object : CloudGate {
            override suspend fun isCloudUsable(): Boolean = cloudUsable
        }
        val nebiansGate = object : NebiansGate {
            override suspend fun isNebiansUsable(): Boolean = nebiansUsable
        }
        return LlmRouter(offline, nebians, cloud, gate, nebiansGate)
    }

    @Test
    fun `selected nebians model wins even when offline is confident`() = runTest {
        val result = router(offlineConfidence = 0.95f, cloudUsable = true, nebiansUsable = true)
            .analyzeTone(request)
        assertEquals(ProviderId.NEBIANS, result.valueOrNull?.provider)
    }

    @Test
    fun `everything disabled stays offline`() = runTest {
        val result = router(offlineConfidence = 0.4f, cloudUsable = false).analyzeTone(request)
        assertEquals(ProviderId.OFFLINE, result.valueOrNull?.provider)
    }

    @Test
    fun `legacy cloud answers when nebians is unusable`() = runTest {
        val result = router(offlineConfidence = 0.9f, cloudUsable = true).analyzeTone(request)
        assertEquals(ProviderId.CLOUD, result.valueOrNull?.provider)
    }

    @Test
    fun `cloud failure falls back to the offline answer`() = runTest {
        val result = router(
            offlineConfidence = 0.3f,
            cloudUsable = true,
            cloudResult = Outcome.Failure(AppError.NoNetwork),
        ).analyzeTone(request)
        assertEquals(ProviderId.OFFLINE, result.valueOrNull?.provider)
    }

    @Test
    fun `nebians failure falls back to offline when cloud is disabled`() = runTest {
        val result = router(
            offlineConfidence = 0.3f,
            cloudUsable = false,
            nebiansUsable = true,
            nebiansResult = Outcome.Failure(AppError.NoNetwork),
        ).analyzeTone(request)
        assertEquals(ProviderId.OFFLINE, result.valueOrNull?.provider)
    }

    @Test
    fun `nebians failure falls through to legacy cloud`() = runTest {
        val result = router(
            offlineConfidence = 0.4f,
            cloudUsable = true,
            nebiansUsable = true,
            nebiansResult = Outcome.Failure(AppError.NoNetwork),
        ).analyzeTone(request)
        assertEquals(ProviderId.CLOUD, result.valueOrNull?.provider)
    }
}
